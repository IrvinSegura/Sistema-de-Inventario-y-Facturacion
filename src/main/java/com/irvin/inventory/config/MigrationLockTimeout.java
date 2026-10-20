package com.irvin.inventory.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.stereotype.Component;

// Non-transactional migrations (CREATE/DROP INDEX CONCURRENTLY) wait for every transaction already
// running to end, reports included (up to their 10 s timeout), so the pool's 5 s lock_timeout could
// fail a deploy. They don't block sales while they wait, so they get a longer timeout, and the
// connection's own value is put back afterwards, since it returns to the pool. Transactional
// migrations keep the pool's timeout on purpose: their queued ACCESS EXCLUSIVE locks would hold the
// checkout for as long as they wait.
@Slf4j
@Component
class MigrationLockTimeout implements Callback {

    static final String NON_TRANSACTIONAL_LOCK_TIMEOUT = "2min";

    // The connection's value while a non-transactional migration runs; null otherwise.
    private String previous;

    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.BEFORE_EACH_MIGRATE || event == Event.AFTER_EACH_MIGRATE
                || event == Event.AFTER_EACH_MIGRATE_ERROR;
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return true;
    }

    @Override
    public void handle(Event event, Context context) {
        Connection connection = context.getConnection();
        try {
            if (event == Event.BEFORE_EACH_MIGRATE) {
                // Flyway wraps a migration in a transaction unless it can't run in one: then
                // auto-commit stays on.
                previous = connection.getAutoCommit() ? lockTimeout(connection) : null;
                if (previous != null) {
                    setLockTimeout(connection, NON_TRANSACTIONAL_LOCK_TIMEOUT);
                    log.info("lock_timeout {} for non-transactional migration {}", NON_TRANSACTIONAL_LOCK_TIMEOUT,
                            context.getMigrationInfo().getScript());
                }
            } else if (previous != null) {
                setLockTimeout(connection, previous);
                previous = null;
            }
        } catch (SQLException e) {
            throw new FlywayException("Could not set lock_timeout for migration "
                    + context.getMigrationInfo().getScript(), e);
        }
    }

    @Override
    public String getCallbackName() {
        return "migration-lock-timeout";
    }

    private static String lockTimeout(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT current_setting('lock_timeout')");
                ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getString(1);
        }
    }

    // set_config instead of SET, so the value goes as a parameter; false = for the whole session.
    private static void setLockTimeout(Connection connection, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT set_config('lock_timeout', ?, false)")) {
            statement.setString(1, value);
            statement.executeQuery().close();
        }
    }
}
