package com.irvin.inventory.config;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

class MigrationLockTimeoutTest {

    private static final String SHOW = "SELECT current_setting('lock_timeout')";
    private static final String SET = "SELECT set_config('lock_timeout', ?, false)";

    private final Connection connection = mock(Connection.class);
    private final PreparedStatement show = mock(PreparedStatement.class);
    private final PreparedStatement set = mock(PreparedStatement.class);
    private final Context context = mock(Context.class, RETURNS_DEEP_STUBS);
    private final MigrationLockTimeout callback = new MigrationLockTimeout();

    @BeforeEach
    void connection() throws SQLException {
        when(context.getConnection()).thenReturn(connection);
        when(context.getMigrationInfo().getScript()).thenReturn("V18__add-index.sql");
        when(connection.prepareStatement(SHOW)).thenReturn(show);
        when(connection.prepareStatement(SET)).thenReturn(set);
        ResultSet current = mock(ResultSet.class);
        when(current.getString(1)).thenReturn("5s");
        when(show.executeQuery()).thenReturn(current);
        when(set.executeQuery()).thenReturn(mock(ResultSet.class));
    }

    @Test
    void nonTransactionalMigrationsWaitLongerAndPutThePoolValueBack() throws SQLException {
        when(connection.getAutoCommit()).thenReturn(true);

        callback.handle(Event.BEFORE_EACH_MIGRATE, context);
        callback.handle(Event.AFTER_EACH_MIGRATE, context);

        InOrder order = Mockito.inOrder(set);
        order.verify(set).setString(1, MigrationLockTimeout.NON_TRANSACTIONAL_LOCK_TIMEOUT);
        order.verify(set).setString(1, "5s");
    }

    @Test
    void theValueIsPutBackWhenTheMigrationFails() throws SQLException {
        when(connection.getAutoCommit()).thenReturn(true);

        callback.handle(Event.BEFORE_EACH_MIGRATE, context);
        callback.handle(Event.AFTER_EACH_MIGRATE_ERROR, context);

        verify(set).setString(1, "5s");
    }

    @Test
    void transactionalMigrationsKeepThePoolTimeout() throws SQLException {
        when(connection.getAutoCommit()).thenReturn(false);

        callback.handle(Event.BEFORE_EACH_MIGRATE, context);
        callback.handle(Event.AFTER_EACH_MIGRATE, context);

        verify(connection, never()).prepareStatement(anyString());
    }
}
