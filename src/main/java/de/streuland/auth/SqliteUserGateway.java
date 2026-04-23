package de.streuland.auth;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import java.nio.file.Path;
import java.nio.file.Files;

public class SqliteUserGateway implements UserGateway {
    private final String jdbcUrl;

    public SqliteUserGateway(Path dbPath) {
        try {
            Files.createDirectories(dbPath.getParent());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create directory for user DB", e);
        }
        this.jdbcUrl = "jdbc:sqlite:" + dbPath.toString();
        initialize();
    }

    private void initialize() {
        try (Connection c = connection(); Statement st = c.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "id TEXT PRIMARY KEY, " +
                    "username TEXT UNIQUE, " +
                    "email TEXT UNIQUE, " +
                    "password_hash TEXT, " +
                    "password_salt TEXT, " +
                    "created_at TEXT, " +
                    "invite_code_id TEXT, " +
                    "server_id TEXT" +
                    ")");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize users schema", e);
        };
    }

    @Override
    public User createUser(String username, String email, String passwordHash, String passwordSalt, String inviteCodeId, String serverId) {
        String id = UUID.randomUUID().toString();
        String createdAt = Instant.now().toString();
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO users (id, username, email, password_hash, password_salt, created_at, invite_code_id, server_id) VALUES (?,?,?,?,?,?,?,?)")) {
            ps.setString(1, id);
            ps.setString(2, username);
            ps.setString(3, email);
            ps.setString(4, passwordHash);
            ps.setString(5, passwordSalt);
            ps.setString(6, createdAt);
            ps.setString(7, inviteCodeId);
            ps.setString(8, serverId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create user", e);
        }
        User u = User.create(id, username, email, serverId, inviteCodeId);
        u.salt = passwordSalt;
        return u;
    }

    private Connection connection() {
        try {
            return DriverManager.getConnection(jdbcUrl);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to open user DB connection", e);
        }
    }
}
