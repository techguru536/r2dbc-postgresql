/*
 * Copyright 2019 the original author or authors.
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

import io.r2dbc.postgresql.client.Version;

/**
 * Connection metadata for a connection connected to a PostgreSQL database.
 */
final class PostgresqlConnectionMetadata implements io.r2dbc.postgresql.api.PostgresqlConnectionMetadata {

    private final Version version;

    PostgresqlConnectionMetadata(Version version) {
        this.version = version;
    }

    @Override
    public String getDatabaseProductName() {
        return "PostgreSQL";
    }

    @Override
    public String getDatabaseVersion() {
        return this.version.getVersion();
    }

}
