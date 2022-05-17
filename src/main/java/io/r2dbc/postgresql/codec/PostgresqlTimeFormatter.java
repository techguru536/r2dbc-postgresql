/*
 * Copyright 2017 the original author or authors.
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

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalQuery;

import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.NANO_OF_SECOND;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;

class PostgresqlTimeFormatter {

    private static final DateTimeFormatter SHORT_OFFSET =
        new DateTimeFormatterBuilder()
            .appendValue(HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(MINUTE_OF_HOUR, 2)
            .optionalStart()
            .appendLiteral(':')
            .appendValue(SECOND_OF_MINUTE, 2)
            .optionalStart()
            .appendFraction(NANO_OF_SECOND, 0, 9, true)
            .optionalEnd()
            .optionalStart()
            .appendOffset("+HH:mm", "+00")
            .optionalEnd()
            .toFormatter();

    private static final DateTimeFormatter FULL_OFFSET =
        new DateTimeFormatterBuilder()
            .appendValue(HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(MINUTE_OF_HOUR, 2)
            .optionalStart()
            .appendLiteral(':')
            .appendValue(SECOND_OF_MINUTE, 2)
            .optionalStart()
            .appendFraction(NANO_OF_SECOND, 0, 9, true)
            .optionalEnd()
            .optionalStart()
            .appendOffset("+HH:MM:ss", "+00:00")
            .optionalEnd()
            .toFormatter();

    /**
     * Fully parses the text producing an object of the specified type.
     * Attempts to parse the text using {@link #SHORT_OFFSET} first, then {@link #FULL_OFFSET}.
     * <p>
     * Most applications should use this method for parsing.
     * It parses the entire text to produce the required date-time.
     * For example:
     * <pre>
     *  OffsetTime dt = parse(str, OffsetTime::from);
     * </pre>
     * If the parse completes without reading the entire length of the text,
     * or a problem occurs during parsing or merging, then an exception is thrown.
     *
     * @param <T>   the type of the parsed date-time
     * @param text  the text to parse, not null
     * @param query the query defining the type to parse to, not null
     * @return the parsed date-time, not null
     * @throws DateTimeParseException if unable to parse the requested result
     */
    static <T> T parse(CharSequence text, TemporalQuery<T> query) {

        try {
            return SHORT_OFFSET.parse(text, query);
        } catch (DateTimeParseException e) {
            return FULL_OFFSET.parse(text, query);
        }
    }

}
