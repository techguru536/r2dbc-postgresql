/*
 * Copyright 2017-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.r2dbc.postgresql.codec;

import io.r2dbc.postgresql.client.Parameter;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static io.r2dbc.postgresql.message.Format.BINARY;
import static io.r2dbc.postgresql.message.Format.TEXT;
import static io.r2dbc.postgresql.type.PostgresqlObjectId.MONEY;
import static io.r2dbc.postgresql.type.PostgresqlObjectId.TIMESTAMPTZ;
import static io.r2dbc.postgresql.type.PostgresqlObjectId.VARCHAR;
import static io.r2dbc.postgresql.util.ByteBufUtils.encode;
import static io.r2dbc.postgresql.util.TestByteBufAllocator.TEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

final class OffsetDateTimeCodecTest {

    @Test
    void constructorNoByteBufAllocator() {
        assertThatNullPointerException().isThrownBy(() -> new OffsetDateTimeCodec(null))
            .withMessage("byteBufAllocator must not be null");
    }

    @Test
    void decode() {
        OffsetDateTime offsetDateTime = OffsetDateTime.now();

        assertThat(new OffsetDateTimeCodec(TEST).decode(encode(TEST, offsetDateTime.toString()), TEXT, OffsetDateTime.class))
            .isEqualTo(offsetDateTime);
    }

    @Test
    void decodeNoByteBuf() {
        assertThat(new OffsetDateTimeCodec(TEST).decode(null, TEXT, OffsetDateTime.class)).isNull();
    }

    @Test
    void doCanDecode() {
        OffsetDateTimeCodec codec = new OffsetDateTimeCodec(TEST);

        assertThat(codec.doCanDecode(BINARY, TIMESTAMPTZ)).isFalse();
        assertThat(codec.doCanDecode(TEXT, MONEY)).isFalse();
        assertThat(codec.doCanDecode(TEXT, TIMESTAMPTZ)).isTrue();
    }

    @Test
    void doCanDecodeNoFormat() {
        assertThatNullPointerException().isThrownBy(() -> new OffsetDateTimeCodec(TEST).doCanDecode(null, VARCHAR))
            .withMessage("format must not be null");
    }

    @Test
    void doCanDecodeNoType() {
        assertThatNullPointerException().isThrownBy(() -> new OffsetDateTimeCodec(TEST).doCanDecode(TEXT, null))
            .withMessage("type must not be null");
    }

    @Test
    void doEncode() {
        OffsetDateTime offsetDateTime = OffsetDateTime.now();

        assertThat(new OffsetDateTimeCodec(TEST).doEncode(offsetDateTime))
            .isEqualTo(new Parameter(TEXT, TIMESTAMPTZ.getObjectId(), encode(TEST, offsetDateTime.toString())));
    }

    @Test
    void doEncodeNoValue() {
        assertThatNullPointerException().isThrownBy(() -> new OffsetDateTimeCodec(TEST).doEncode(null))
            .withMessage("value must not be null");
    }

    @Test
    void encodeNull() {
        assertThat(new OffsetDateTimeCodec(TEST).encodeNull())
            .isEqualTo(new Parameter(TEXT, TIMESTAMPTZ.getObjectId(), null));
    }

}
