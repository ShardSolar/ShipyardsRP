package net.shard.seconddawnrp.database;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DatabaseManager implements AutoCloseable {

    private final DatabaseConfig config;
    private Connection connection;

    public DatabaseManager(DatabaseConfig config) {
        this.config = config;
    }

    public void init() throws SQLException, IOException, ClassNotFoundException {
        Files.createDirectories(config.getDatabaseFile().getParent());
        Class.forName("org.sqlite.JDBC");
        this.connection = openConnection();
    }

    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            try {
                connection = openConnection();
            } catch (Exception e) {
                throw new SQLException("Failed to reconnect to database", e);
            }
        }
        return connection;
    }

    private Connection openConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(config.getJdbcUrl());
        conn.setAutoCommit(true);
        // Enable WAL mode for better concurrent read performance
        try (var stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA foreign_keys=ON");
        }
        return conn;
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}