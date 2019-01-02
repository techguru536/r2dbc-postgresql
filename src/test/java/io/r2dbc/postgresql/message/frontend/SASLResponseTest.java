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

package io.r2dbc.postgresql.message.frontend;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static io.r2dbc.postgresql.message.frontend.FrontendMessageAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

final class SASLResponseTest {

    @Test
    void constructorNoData() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SASLResponse(null))
            .withMessage("data must not be null");
    }

    @Test
    void encode() {
        assertThat(new SASLResponse(ByteBuffer.allocate(4).putInt(100))).encoded()
            .isDeferred()
            .isEncodedAs(buffer -> buffer
                .writeByte('p')
                .writeInt(8)
                .writeInt(100));
    }

}
