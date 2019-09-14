/*
 * Copyright 2017-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.r2dbc.postgresql.client;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.r2dbc.postgresql.message.backend.BackendKeyData;
import io.r2dbc.postgresql.message.backend.BackendMessage;
import io.r2dbc.postgresql.message.backend.BackendMessageDecoder;
import io.r2dbc.postgresql.message.backend.BackendMessageEnvelopeDecoder;
import io.r2dbc.postgresql.message.backend.ErrorResponse;
import io.r2dbc.postgresql.message.backend.Field;
import io.r2dbc.postgresql.message.backend.NoticeResponse;
import io.r2dbc.postgresql.message.backend.NotificationResponse;
import io.r2dbc.postgresql.message.backend.ParameterStatus;
import io.r2dbc.postgresql.message.backend.ReadyForQuery;
import io.r2dbc.postgresql.message.frontend.FrontendMessage;
import io.r2dbc.postgresql.message.frontend.Terminate;
import io.r2dbc.postgresql.util.Assert;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.DirectProcessor;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.core.publisher.SynchronousSink;
import reactor.netty.Connection;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.tcp.TcpClient;
import reactor.util.annotation.Nullable;
import reactor.util.concurrent.Queues;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import static io.r2dbc.postgresql.client.TransactionStatus.IDLE;
import static io.r2dbc.postgresql.util.PredicateUtils.not;

/**
 * An implementation of client based on the Reactor Netty project.
 *
 * @see TcpClient
 */
public final class ReactorNettyClient implements Client {

    private static final Logger logger = LoggerFactory.getLogger(ReactorNettyClient.class);

    private static final boolean DEBUG_ENABLED = logger.isDebugEnabled();

    private final ByteBufAllocator byteBufAllocator;

    private final Connection connection;

    private final EmitterProcessor<FrontendMessage> requestProcessor = EmitterProcessor.create(false);

    private final FluxSink<FrontendMessage> requests = this.requestProcessor.sink();

    private final Queue<MonoSink<Flux<BackendMessage>>> responseReceivers = Queues.<MonoSink<Flux<BackendMessage>>>unbounded().get();

    private final DirectProcessor<NotificationResponse> notificationProcessor = DirectProcessor.create();

    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    private volatile Integer processId;

    private volatile Integer secretKey;

    private volatile TransactionStatus transactionStatus = IDLE;

    private volatile Version version = new Version("", 0);

    /**
     * Creates a new frame processor connected to a given TCP connection.
     *
     * @param connection the TCP connection
     * @param sslConfig  ssl configuration
     * @throws IllegalArgumentException if {@code connection} is {@code null}
     */
    private ReactorNettyClient(Connection connection, SSLConfig sslConfig) {
        Assert.requireNonNull(connection, "Connection must not be null");
        Assert.requireNonNull(sslConfig, "SSLConfig must not be null");

        Mono<Void> sslHandshake;
        if (sslConfig.getSslMode().startSsl()) {
            SSLSessionHandlerAdapter sslSessionHandlerAdapter = new SSLSessionHandlerAdapter(connection.outbound().alloc(), sslConfig);
            connection.addHandlerFirst(sslSessionHandlerAdapter);
            sslHandshake = sslSessionHandlerAdapter.getHandshake();
        } else {
            sslHandshake = Mono.empty();
        }

        connection.addHandler(new EnsureSubscribersCompleteChannelHandler(this.requestProcessor, this.responseReceivers));
        this.connection = connection;
        this.byteBufAllocator = connection.outbound().alloc();

        BackendMessageEnvelopeDecoder envelopeDecoder = new BackendMessageEnvelopeDecoder(byteBufAllocator);

        Mono<Void> receive = connection.inbound().receive()
            .retain()
            .concatMap(envelopeDecoder)
            .map(BackendMessageDecoder::decode)
            .handle(this::handleResponse)
            .windowWhile(not(ReadyForQuery.class::isInstance))
            .doOnNext(fluxOfMessages -> {
                MonoSink<Flux<BackendMessage>> receiver = this.responseReceivers.poll();
                if (receiver != null) {
                    receiver.success(fluxOfMessages);
                }
            })
            .doOnComplete(() -> {
                MonoSink<Flux<BackendMessage>> receiver = this.responseReceivers.poll();
                if (receiver != null) {
                    receiver.success(Flux.empty());
                }
            })
            .then();

        Mono<Void> request = sslHandshake
            .thenMany(this.requestProcessor)
            .doOnNext(message -> logger.debug("Request:  {}", message))
            .concatMap(message -> connection.outbound().send(message.encode(connection.outbound().alloc())))
            .then();

        connection.onDispose()
            .doFinally(s -> envelopeDecoder.dispose())
            .subscribe();

        receive
            .onErrorResume(throwable -> {
                MonoSink<Flux<BackendMessage>> receiver = this.responseReceivers.poll();
                if (receiver != null) {
                    receiver.error(throwable);
                }
                this.requestProcessor.onComplete();
                logger.error("Connection Error", throwable);
                return close();
            })
            .subscribe();

        request
            .onErrorResume(throwable -> {
                logger.error("Connection Error", throwable);
                return close();
            })
            .subscribe();
    }

    private void handleResponse(BackendMessage message, SynchronousSink<BackendMessage> sink) {

        if (DEBUG_ENABLED) {
            logger.debug("Response: {}", message);
        }

        if (message.getClass() == NoticeResponse.class) {
            logger.warn("Notice: {}", toString(((NoticeResponse) message).getFields()));
            return;
        }

        if (message.getClass() == BackendKeyData.class) {

            BackendKeyData backendKeyData = (BackendKeyData) message;

            this.processId = backendKeyData.getProcessId();
            this.secretKey = backendKeyData.getSecretKey();
            return;
        }

        if (message.getClass() == ErrorResponse.class) {
            logger.warn("Error: {}", toString(((ErrorResponse) message).getFields()));
        }

        if (message.getClass() == ParameterStatus.class) {
            handleParameterStatus((ParameterStatus) message);
        }

        if (message.getClass() == ReadyForQuery.class) {
            this.transactionStatus = TransactionStatus.valueOf(((ReadyForQuery) message).getTransactionStatus());
        }

        if (message.getClass() == NotificationResponse.class) {
            this.notificationProcessor.onNext((NotificationResponse) message);
            return;
        }

        sink.next(message);
    }

    private void handleParameterStatus(ParameterStatus message) {

        Version existingVersion = this.version;

        String versionString = existingVersion.getVersion();
        int versionNum = existingVersion.getVersionNumber();

        if (message.getName().equals("server_version_num")) {
            versionNum = Integer.parseInt(message.getValue());
        }

        if (message.getName().equals("server_version")) {
            versionString = message.getValue();

            if (versionNum == 0) {
                versionNum = Version.parseServerVersionStr(versionString);
            }
        }

        version = new Version(versionString, versionNum);
    }

    /**
     * Creates a new frame processor connected to a given host.
     *
     * @param host the host to connect to
     * @param port the port to connect to
     * @throws IllegalArgumentException if {@code host} is {@code null}
     */
    public static Mono<ReactorNettyClient> connect(String host, int port) {
        Assert.requireNonNull(host, "host must not be null");

        return connect(host, port, null, new SSLConfig(SSLMode.DISABLE, null, null));
    }

    /**
     * Creates a new frame processor connected to a given host.
     *
     * @param host           the host to connect to
     * @param port           the port to connect to
     * @param connectTimeout connect timeout
     * @param sslConfig      SSL configuration
     * @throws IllegalArgumentException if {@code host} is {@code null}
     */
    public static Mono<ReactorNettyClient> connect(String host, int port, @Nullable Duration connectTimeout, SSLConfig sslConfig) {
        return connect(ConnectionProvider.newConnection(), host, port, connectTimeout, sslConfig);
    }

    /**
     * Creates a new frame processor connected to a given host.
     *
     * @param connectionProvider the connection provider resources
     * @param host               the host to connect to
     * @param port               the port to connect to
     * @param connectTimeout     connect timeout
     * @param sslConfig          SSL configuration
     * @throws IllegalArgumentException if {@code host} is {@code null}
     */
    public static Mono<ReactorNettyClient> connect(ConnectionProvider connectionProvider, String host, int port, @Nullable Duration connectTimeout, SSLConfig sslConfig) {
        Assert.requireNonNull(connectionProvider, "connectionProvider must not be null");
        Assert.requireNonNull(host, "host must not be null");

        TcpClient tcpClient = TcpClient.create(connectionProvider)
            .host(host).port(port);

        if (connectTimeout != null) {
            tcpClient = tcpClient.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(connectTimeout.toMillis()));
        }

        Mono<? extends Connection> connection = tcpClient.connect();

        return connection.map(c -> new ReactorNettyClient(c, sslConfig));
    }

    @Override
    public Mono<Void> close() {
        return Mono.defer(() -> {

            if (this.isClosed.compareAndSet(false, true)) {

                if (!connection.channel().isOpen()) {
                    this.isClosed.set(true);
                    return connection.onDispose();
                }
                return Flux.just(Terminate.INSTANCE)
                    .doOnNext(message -> logger.debug("Request:  {}", message))
                    .concatMap(message -> connection.outbound().send(message.encode(connection.outbound().alloc())))
                    .then()
                    .doOnSuccess(v -> connection.dispose())
                    .then(connection.onDispose())
                    .doOnSuccess(v -> this.isClosed.set(true));
            }

            return Mono.empty();
        });
    }

    @Override
    public Flux<BackendMessage> exchange(Publisher<FrontendMessage> requests) {
        Assert.requireNonNull(requests, "requests must not be null");

        return Mono
            .<Flux<BackendMessage>>create(sink -> {
                if (this.isClosed.get()) {
                    sink.error(new IllegalStateException("Cannot exchange messages because the connection is closed"));
                }

                final AtomicInteger once = new AtomicInteger();

                Flux.from(requests)
                    .subscribe(message -> {
                        if (once.get() == 0 && once.compareAndSet(0, 1)) {
                            synchronized (this) {
                                this.responseReceivers.add(sink);
                                this.requests.next(message);
                            }
                            return;
                        }

                        this.requests.next(message);
                    }, this.requests::error);

            })
            .flatMapMany(Function.identity());
    }

    @Override
    public ByteBufAllocator getByteBufAllocator() {
        return this.byteBufAllocator;
    }

    @Override
    public Optional<Integer> getProcessId() {
        return Optional.ofNullable(this.processId);
    }

    @Override
    public Optional<Integer> getSecretKey() {
        return Optional.ofNullable(this.secretKey);
    }

    @Override
    public TransactionStatus getTransactionStatus() {
        return this.transactionStatus;
    }

    @Override
    public Version getVersion() {
        return this.version;
    }

    @Override
    public boolean isConnected() {
        if (this.isClosed.get()) {
            return false;
        }

        Channel channel = this.connection.channel();
        return channel.isOpen();
    }

    @Override
    public Disposable addNotificationListener(Consumer<NotificationResponse> consumer) {
        return this.notificationProcessor.subscribe(consumer);
    }

    @SuppressWarnings("unchecked")
    private static <T extends BackendMessage> BiConsumer<BackendMessage, SynchronousSink<BackendMessage>> handleBackendMessage(Class<T> type, BiConsumer<T, SynchronousSink<BackendMessage>> consumer) {
        return (message, sink) -> {
            if (type.isInstance(message)) {
                consumer.accept((T) message, sink);
            } else {
                sink.next(message);
            }
        };
    }

    private static String toString(List<Field> fields) {

        StringJoiner joiner = new StringJoiner(", ");
        for (Field field : fields) {
            joiner.add(field.getType().name() + "=" + field.getValue());
        }

        return joiner.toString();
    }

    private static final class EnsureSubscribersCompleteChannelHandler extends ChannelDuplexHandler {

        private final EmitterProcessor<FrontendMessage> requestProcessor;

        private final Queue<MonoSink<Flux<BackendMessage>>> responseReceivers;

        private EnsureSubscribersCompleteChannelHandler(EmitterProcessor<FrontendMessage> requestProcessor, Queue<MonoSink<Flux<BackendMessage>>> responseReceivers) {
            this.requestProcessor = requestProcessor;
            this.responseReceivers = responseReceivers;
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
            super.channelUnregistered(ctx);

            this.requestProcessor.onComplete();

            for (MonoSink<Flux<BackendMessage>> responseReceiver = this.responseReceivers.poll(); responseReceiver != null; responseReceiver = this.responseReceivers.poll()) {
                responseReceiver.success(Flux.empty());
            }
        }
    }

}
