package com.knowledgeos.tenant;

import com.pgvector.PGvector;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Ad ogni prelievo di connessione dal pool, imposta (o azzera) la GUC di sessione
 * "app.current_tenant_id" in base al {@link TenantContext} del thread corrente,
 * cosi' che le policy di Row Level Security in Postgres (V1__init_schema.sql)
 * filtrino sempre per il tenant della richiesta in corso — indipendentemente
 * da eventuali bug nel filtro applicativo esplicito (difesa in profondita',
 * 06_SECURITY_MODEL.md §3.2/§4).
 */
public class TenantAwareDataSource extends DelegatingDataSource {

    public TenantAwareDataSource(DataSource targetDataSource) {
        super(targetDataSource);
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection connection = super.getConnection();
        PGvector.addVectorType(connection);
        applyTenant(connection);
        return connection;
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Connection connection = super.getConnection(username, password);
        PGvector.addVectorType(connection);
        applyTenant(connection);
        return connection;
    }

    private void applyTenant(Connection connection) throws SQLException {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            try (PreparedStatement statement =
                         connection.prepareStatement("SELECT set_config('app.current_tenant_id', ?, false)")) {
                statement.setString(1, tenantId.toString());
                statement.execute();
            }
        } else {
            try (Statement statement = connection.createStatement()) {
                statement.execute("RESET app.current_tenant_id");
            }
        }
    }
}
