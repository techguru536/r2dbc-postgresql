/*
 * Copyright 2019-2020 the original author or authors.
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

package io.r2dbc.postgresql;

import io.r2dbc.postgresql.client.Binding;
import io.r2dbc.postgresql.client.Client;
import io.r2dbc.postgresql.client.Parameter;
import io.r2dbc.postgresql.client.TestClient;
import io.r2dbc.postgresql.message.backend.ErrorResponse;
import io.r2dbc.postgresql.message.backend.ParseComplete;
import io.r2dbc.postgresql.message.frontend.Flush;
import io.r2dbc.postgresql.message.frontend.Parse;
import io.r2dbc.spi.R2dbcNonTransientResourceException;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.Collections;

import static io.r2dbc.postgresql.client.TestClient.NO_OP;
import static io.r2dbc.postgresql.message.Format.FORMAT_BINARY;
import static io.r2dbc.postgresql.util.TestByteBufAllocator.TEST;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DisabledStatementCacheTest {

    @Test
    void constructorNoClient() {
        assertThatIllegalArgumentException().isThrownBy(() -> new DisabledStatementCache(null))
            .withMessage("client must not be null");
    }

    @Test
    void getName() {
        // @formatter:off
        Client client = TestClient.builder()
            .expectRequest(new Parse("", Collections.singletonList(100), "test-query"),  Flush.INSTANCE)
            .thenRespond(ParseComplete.INSTANCE)
            .expectRequest(new Parse("", Collections.singletonList(100), "test-query"),  Flush.INSTANCE)
            .thenRespond(ParseComplete.INSTANCE)
            .expectRequest(new Parse("", Collections.singletonList(200), "test-query"),  Flush.INSTANCE)
            .thenRespond(ParseComplete.INSTANCE)
            .expectRequest(new Parse("", Collections.singletonList(200), "test-query-2"), Flush.INSTANCE)
            .thenRespond(ParseComplete.INSTANCE)
            .build();
        // @formatter:on

        DisabledStatementCache statementCache = new DisabledStatementCache(client);

        statementCache.getName(new Binding(1).add(0, new Parameter(FORMAT_BINARY, 100, Flux.just(TEST.buffer(4).writeInt(100)))), "test-query")
            .as(StepVerifier::create)
            .expectNext("")
            .verifyComplete();

        statementCache.getName(new Binding(1).add(0, new Parameter(FORMAT_BINARY, 100, Flux.just(TEST.buffer(4).writeInt(200)))), "test-query")
            .as(StepVerifier::create)
            .expectNext("")
            .verifyComplete();

        statementCache.getName(new Binding(1).add(0, new Parameter(FORMAT_BINARY, 200, Flux.just(TEST.buffer(2).writeShort(300)))), "test-query")
            .as(StepVerifier::create)
            .expectNext("")
            .verifyComplete();

        statementCache.getName(new Binding(1).add(0, new Parameter(FORMAT_BINARY, 200, Flux.just(TEST.buffer(4).writeShort(300)))), "test-query-2")
            .as(StepVerifier::create)
            .expectNext("")
            .verifyComplete();
    }

    @Test
    void getNameErrorResponse() {
        // @formatter:off
        Client client = TestClient.builder()
            .expectRequest(new Parse("", Collections.singletonList(100), "test-query"), Flush.INSTANCE)
            .thenRespond(new ErrorResponse(Collections.emptyList()))
            .build();
        // @formatter:on

        DisabledStatementCache statementCache = new DisabledStatementCache(client);

        statementCache.getName(new Binding(1).add(0, new Parameter(FORMAT_BINARY, 100, Flux.just(TEST.buffer(4).writeInt(200)))), "test-query")
            .as(StepVerifier::create)
            .verifyError(R2dbcNonTransientResourceException.class);
    }

    @Test
    void getNameNoBinding() {
        assertThatIllegalArgumentException().isThrownBy(() -> new DisabledStatementCache(NO_OP).getName(null, "test-query"))
            .withMessage("binding must not be null");
    }

    @Test
    void getNameNoSql() {
        assertThatIllegalArgumentException().isThrownBy(() -> new DisabledStatementCache(NO_OP).getName(new Binding(0), null))
            .withMessage("sql must not be null");
    }

}
