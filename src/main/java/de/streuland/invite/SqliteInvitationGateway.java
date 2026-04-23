package de.streuland.invite;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite-backed implementation of InvitationGateway.
 * Persistents InvitationCode entities.
 */
public class SqliteInvitationGateway implements InvitationGateway {
    private final String jdbcUrl;
    private final Gson gson = new Gson();
    private static final Type ROLES_TYPE = new TypeToken<List<String>>(){}.getType();

    public SqliteInvitationGateway(Path dbPath) {
        try {
            Files.createDirectories(dbPath.getParent());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create directory for invitation DB", e);
        }
        this.jdbcUrl = "jdbc:sqlite:" + dbPath.toString();
        initialize();
    }

    private void initialize() {
        try (Connection c = connection(); Statement st = c.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("CREATE TABLE IF NOT EXISTS invitations (" +
                    "id TEXT PRIMARY KEY, " +
                    "code TEXT UNIQUE, " +
                    "issuer_user_id TEXT, " +
                    "created_at TEXT, " +
                    "expires_at TEXT, " +
                    "max_uses INTEGER, " +
                    "uses INTEGER DEFAULT 0, " +
                    "is_revoked BOOLEAN, " +
                    "allowed_roles TEXT, " +
                    "server_id TEXT, " +
                    "target_server TEXT" +
                    ")");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize invitations schema", e);
        }
    }

    @Override
    public InvitationCode create(InvitationCode code) {
        if (code == null) return null;
        if (code.createdAt == null) code.createdAt = Instant.now().toString();
        if (code.id == null) code.id = java.util.UUID.randomUUID().toString();
        if (code.code == null || code.code.isEmpty()) {
            code.code = generateCode();
        }
        String rolesJson = gson.toJson(code.allowedRoles == null ? new ArrayList<String>() : code.allowedRoles);
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO invitations (id, code, issuer_user_id, created_at, expires_at, max_uses, uses, is_revoked," +
                        " allowed_roles, server_id, target_server) VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, code.id);
            ps.setString(2, code.code);
            ps.setString(3, code.issuerUserId);
            ps.setString(4, code.createdAt);
            ps.setString(5, code.expiresAt);
            ps.setObject(6, code.maxUses);
            ps.setInt(7, code.uses);
            ps.setBoolean(8, code.isRevoked);
            ps.setString(9, rolesJson);
            ps.setString(10, code.serverId);
            ps.setString(11, code.targetServer);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to insert invitation", e);
        }
        return code;
    }

    @Override
    public List<InvitationCode> listAll() {
        List<InvitationCode> list = new ArrayList<>();
        String sql = "SELECT * FROM invitations";
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(toInvitation(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list invitations", e);
        }
        return list;
    }

    @Override
    public InvitationCode getById(String id) {
        String sql = "SELECT * FROM invitations WHERE id = ?";
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return toInvitation(rs);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to get invitation by id", e);
        }
        return null;
    }

    @Override
    public InvitationCode getByCode(String code) {
        String sql = "SELECT * FROM invitations WHERE code = ?";
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return toInvitation(rs);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to get invitation by code", e);
        }
        return null;
    }

    @Override
    public InvitationCode revoke(String id) {
        String sql = "UPDATE invitations SET is_revoked = 1 WHERE id = ?";
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to revoke invitation", e);
        }
        return getById(id);
    }

    @Override
    public void incrementUses(String id) {
        String sql = "UPDATE invitations SET uses = uses + 1 WHERE id = ?";
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to increment invitation uses", e);
        }
    }

    @Override
    public InvitationCode update(String id, String expiresAt, Integer maxUses, List<String> allowedRoles, Boolean isRevoked) {
        String sql = "UPDATE invitations SET expires_at = ?, max_uses = ?, allowed_roles = ?, is_revoked = ? WHERE id = ?";
        String rolesJson = gson.toJson(allowedRoles == null ? new ArrayList<String>() : allowedRoles);
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, expiresAt);
            if (maxUses == null) ps.setObject(2, null);
            else ps.setInt(2, maxUses);
            ps.setString(3, rolesJson);
            ps.setBoolean(4, isRevoked != null ? isRevoked : false);
            ps.setString(5, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update invitation", e);
        }
        return getById(id);
    }

    private InvitationCode toInvitation(ResultSet rs) throws SQLException {
        InvitationCode c = new InvitationCode();
        c.id = rs.getString("id");
        c.code = rs.getString("code");
        c.issuerUserId = rs.getString("issuer_user_id");
        c.createdAt = rs.getString("created_at");
        c.expiresAt = rs.getString("expires_at");
        int mu = rs.getInt("max_uses");
        if (rs.wasNull()) c.maxUses = null; else c.maxUses = mu;
        c.uses = rs.getInt("uses");
        c.isRevoked = rs.getBoolean("is_revoked");
        String rolesJson = rs.getString("allowed_roles");
        c.allowedRoles = gson.fromJson(rolesJson, ROLES_TYPE);
        c.serverId = rs.getString("server_id");
        c.targetServer = rs.getString("target_server");
        return c;
    }

    private Connection connection() {
        try {
            return DriverManager.getConnection(jdbcUrl);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to open invite DB connection", e);
        }
    }

    private String generateCode() {
        // simple 32-char alphanumeric-like code, URL-safe
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < 32; i++) {
            int idx = (int) (Math.random() * chars.length());
            sb.append(chars.charAt(idx));
        }
        return sb.toString();
    }
}
