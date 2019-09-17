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

package io.r2dbc.postgresql.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.r2dbc.postgresql.client.Parameter;
import io.r2dbc.postgresql.message.Format;
import io.r2dbc.postgresql.type.PostgresqlObjectId;
import io.r2dbc.postgresql.util.Assert;
import reactor.core.publisher.Flux;
import reactor.util.annotation.Nullable;

import static io.r2dbc.postgresql.message.Format.FORMAT_BINARY;
import static io.r2dbc.postgresql.type.PostgresqlObjectId.FLOAT4;

final class FloatCodec extends AbstractNumericCodec<Float> {

    private final ByteBufAllocator byteBufAllocator;

    FloatCodec(ByteBufAllocator byteBufAllocator) {
        super(Float.class);
        this.byteBufAllocator = Assert.requireNonNull(byteBufAllocator, "byteBufAllocator must not be null");
    }

    @Override
    public Parameter encodeNull() {
        return createNull(FLOAT4, FORMAT_BINARY);
    }

    @Override
    Float doDecode(ByteBuf buffer, PostgresqlObjectId dataType, Format format, @Nullable Class<? extends Float> type) {
        Assert.requireNonNull(buffer, "byteBuf must not be null");
        Assert.requireNonNull(format, "format must not be null");

        return decodeNumber(buffer, dataType, format, Float.class, Number::floatValue);
    }

    @Override
    Parameter doEncode(Float value) {
        Assert.requireNonNull(value, "value must not be null");

        ByteBuf encoded = this.byteBufAllocator.buffer(4).writeFloat(value);
        return create(FLOAT4, FORMAT_BINARY, Flux.just(encoded));
    }

    @Override
    PostgresqlObjectId getDefaultType() {
        return FLOAT4;
    }
}
