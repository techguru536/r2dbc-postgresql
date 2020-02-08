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

package io.r2dbc.postgresql;

import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.postgresql.client.Client;
import io.r2dbc.postgresql.client.PortalNameSupplier;
import io.r2dbc.postgresql.codec.Codecs;
import io.r2dbc.postgresql.util.Assert;

/**
 * Value object capturing contextual connection resources such as {@link Client}, {@link Codecs} and the {@link PostgresqlConnection connection facade}.
 */
final class ConnectionContext {

    private final Client client;

    private final Codecs codecs;

    private final PostgresqlConnection connection;

    private final StatementCache statementCache;

    private final boolean forceBinary;

    private final PortalNameSupplier portalNameSupplier;

    ConnectionContext(Client client, Codecs codecs, PostgresqlConnection connection, StatementCache statementCache, boolean forceBinary, PortalNameSupplier portalNameSupplier) {
        this.client = client;
        this.codecs = codecs;
        this.connection = connection;
        this.portalNameSupplier = Assert.requireNonNull(portalNameSupplier, "portalNameSupplier must not be null");
        this.statementCache = Assert.requireNonNull(statementCache, "statementCache must not be null");
        this.forceBinary = forceBinary;
    }

    public Client getClient() {
        return this.client;
    }

    public Codecs getCodecs() {
        return this.codecs;
    }

    public PostgresqlConnection getConnection() {
        return this.connection;
    }

    public StatementCache getStatementCache() {
        return this.statementCache;
    }

    public boolean isForceBinary() {
        return this.forceBinary;
    }

    public PortalNameSupplier getPortalNameSupplier() {
        return this.portalNameSupplier;
    }

    @Override
    public String toString() {
        return "ConnectionContext{" +
            "client=" + this.client +
            ", codecs=" + this.codecs +
            ", connection=" + this.connection +
            ", statementCache=" + this.statementCache +
            ", forceBinary=" + this.forceBinary +
            ", portalNameSupplier=" + this.portalNameSupplier +
            '}';
    }
}
