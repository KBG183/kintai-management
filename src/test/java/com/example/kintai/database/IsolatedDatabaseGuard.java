package com.example.kintai.database;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.regex.Pattern;

/** Fail closed before integration tests mutate database state. */
public final class IsolatedDatabaseGuard {

    private IsolatedDatabaseGuard() {}

    public static String requireAllowedDatabase(DataSource dataSource, String allowedNamePattern) throws Exception {
        Pattern allowed = Pattern.compile(allowedNamePattern);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT DATABASE()")) {
            if (!result.next()) {
                throw new IllegalStateException("Unable to determine the integration-test database; refusing to continue.");
            }
            String database = result.getString(1);
            if (database == null || !allowed.matcher(database).matches()) {
                throw new IllegalStateException("Integration test requires an allowlisted isolated database; refusing database: " + database);
            }
            return database;
        }
    }
}
