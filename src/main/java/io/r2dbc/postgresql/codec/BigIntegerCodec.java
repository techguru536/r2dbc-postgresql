/*
 * Copyright 2020-2020 the original author or authors.
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
import io.r2dbc.postgresql.message.Format;
import io.r2dbc.postgresql.type.PostgresqlObjectId;
import io.r2dbc.postgresql.util.Assert;
import reactor.util.annotation.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;

import static io.r2dbc.postgresql.type.PostgresqlObjectId.NUMERIC;

final class BigIntegerCodec extends AbstractNumericCodec<BigInteger> {

    public BigIntegerCodec(ByteBufAllocator byteBufAllocator) {
        super(BigInteger.class, byteBufAllocator);
    }

    @Override
    BigInteger doDecode(ByteBuf buffer, PostgresqlObjectId dataType, @Nullable Format format, @Nullable Class<? extends BigInteger> type) {
        Assert.requireNonNull(buffer, "byteBuf must not be null");

        return this.decodeNumber(buffer, dataType, format, BigInteger.class, it -> {
            return it instanceof BigDecimal ? ((BigDecimal) it).toBigInteger() : BigInteger.valueOf(it.longValue());
        });
    }

    @Override
    PostgresqlObjectId getDefaultType() {
        return NUMERIC;
    }

}
