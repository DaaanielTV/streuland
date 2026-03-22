package de.streuland.storage;

import de.streuland.plot.Plot;
import de.streuland.plot.PlotData;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class SqlitePlotStorage implements PlotStorage {
    private static final String KEY_PUBLIC = "public";
    private static final String KEY_TITLE = "title";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_TAGS = "tags";
    private static final String KEY_SPAWN = "spawn";
    private static final String KEY_TRUSTED = "trusted";

    private final String jdbcUrl;

    public SqlitePlotStorage(Path dbFile) {
        try {
            Files.createDirectories(dbFile.getParent());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create SQLite parent directory", e);
        }
        this.jdbcUrl = "jdbc:sqlite:" + dbFile;
        initialize();
    }

    private void initialize() {
        try (Connection connection = connection(); Statement st = connection.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("CREATE TABLE IF NOT EXISTS plots (" +
                    "id TEXT PRIMARY KEY," +
                    "world TEXT NOT NULL," +
                    "center_x INTEGER NOT NULL," +
                    "center_z INTEGER NOT NULL," +
                    "size INTEGER NOT NULL," +
                    "spawn_y INTEGER NOT NULL," +
                    "owner_uuid TEXT," +
                    "created_at INTEGER," +
                    "area_type TEXT," +
                    "metadata TEXT" +
                    ")");
            st.execute("CREATE VIRTUAL TABLE IF NOT EXISTS plots_rtree USING rtree(id, minX, maxX, minZ, maxZ)");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize SQLite schema", e);
        }
    }

    @Override
    public synchronized void save(Plot plot) {
        String world = YamlPlotStorage.resolveWorld(plot.getPlotId());
        String metadata = encodeMetadata(plot);
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO plots(id, world, center_x, center_z, size, spawn_y, owner_uuid, created_at, area_type, metadata) VALUES(?,?,?,?,?,?,?,?,?,?) " +
                    "ON CONFLICT(id) DO UPDATE SET world=excluded.world, center_x=excluded.center_x, center_z=excluded.center_z, size=excluded.size, spawn_y=excluded.spawn_y, owner_uuid=excluded.owner_uuid, created_at=excluded.created_at, area_type=excluded.area_type, metadata=excluded.metadata");
                 PreparedStatement deleteRtree = c.prepareStatement("DELETE FROM plots_rtree WHERE id = ?");
                 PreparedStatement insertRtree = c.prepareStatement("INSERT INTO plots_rtree(id, minX, maxX, minZ, maxZ) VALUES(?,?,?,?,?)")) {
                ps.setString(1, plot.getPlotId());
                ps.setString(2, world);
                ps.setInt(3, plot.getCenterX());
                ps.setInt(4, plot.getCenterZ());
                ps.setInt(5, plot.getSize());
                ps.setInt(6, plot.getSpawnY());
                ps.setString(7, plot.getOwner() == null ? null : plot.getOwner().toString());
                ps.setLong(8, plot.getCreatedAt());
                ps.setString(9, plot.getAreaType().name());
                ps.setString(10, metadata);
                ps.executeUpdate();

                deleteRtree.setString(1, plot.getPlotId());
                deleteRtree.executeUpdate();

                insertRtree.setString(1, plot.getPlotId());
                insertRtree.setInt(2, plot.getMinX());
                insertRtree.setInt(3, plot.getMaxX());
                insertRtree.setInt(4, plot.getMinZ());
                insertRtree.setInt(5, plot.getMaxZ());
                insertRtree.executeUpdate();
            }
            c.commit();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save plot into SQLite: " + plot.getPlotId(), e);
        }
    }

    @Override
    public synchronized void delete(String id) {
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM plots WHERE id = ?");
                 PreparedStatement rt = c.prepareStatement("DELETE FROM plots_rtree WHERE id = ?")) {
                ps.setString(1, id);
                ps.executeUpdate();
                rt.setString(1, id);
                rt.executeUpdate();
            }
            c.commit();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete plot from SQLite: " + id, e);
        }
    }

    @Override
    public Optional<Plot> findById(String id) {
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM plots WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(toPlot(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to query plot by id", e);
        }
    }

    @Override
    public Optional<Plot> findNearest(String world, int x, int z, int radius) {
        String sql = "SELECT p.* FROM plots_rtree r " +
                "JOIN plots p ON p.id = r.id " +
                "WHERE p.world = ? " +
                "AND r.minX <= ? AND r.maxX >= ? " +
                "AND r.minZ <= ? AND r.maxZ >= ?";
        Plot nearest = null;
        long best = Long.MAX_VALUE;
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, world);
            ps.setInt(2, x + radius);
            ps.setInt(3, x - radius);
            ps.setInt(4, z + radius);
            ps.setInt(5, z - radius);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Plot candidate = toPlot(rs);
                    long dx = candidate.getCenterX() - x;
                    long dz = candidate.getCenterZ() - z;
                    long dist2 = dx * dx + dz * dz;
                    if (dist2 <= ((long) radius * radius) && dist2 < best) {
                        best = dist2;
                        nearest = candidate;
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find nearest plot", e);
        }
        return Optional.ofNullable(nearest);
    }

    @Override
    public Collection<Plot> listAll() {
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM plots");
             ResultSet rs = ps.executeQuery()) {
            List<Plot> plots = new ArrayList<>();
            while (rs.next()) {
                plots.add(toPlot(rs));
            }
            return plots;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list plots", e);
        }
    }

    public int migrateFromYaml(Path yamlDir) {
        YamlPlotStorage yamlStorage = new YamlPlotStorage(yamlDir);
        int migrated = 0;
        for (Plot plot : yamlStorage.listAll()) {
            save(plot);
            Path source = yamlDir.resolve(plot.getPlotId() + ".yml");
            Path backup = yamlDir.resolve(plot.getPlotId() + ".yml.bak");
            try {
                Files.copy(source, backup, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to backup YAML file during migration: " + source, e);
            }
            migrated++;
        }
        return migrated;
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private static Plot toPlot(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        int centerX = rs.getInt("center_x");
        int centerZ = rs.getInt("center_z");
        int size = rs.getInt("size");
        int spawnY = rs.getInt("spawn_y");
        String ownerRaw = rs.getString("owner_uuid");
        UUID owner = ownerRaw == null ? null : UUID.fromString(ownerRaw);
        long createdAt = rs.getLong("created_at");
        String areaType = rs.getString("area_type");
        Plot.PlotState state = "PLOT_CLAIMED".equals(areaType) || owner != null ? Plot.PlotState.CLAIMED : Plot.PlotState.UNCLAIMED;
        Plot plot = new Plot(id, centerX, centerZ, size, owner, createdAt, spawnY, state);
        for (UUID trusted : decodeMetadata(rs.getString("metadata")).trusted) {
            plot.addTrusted(trusted);
        }
        return plot;
    }

    private static String encodeMetadata(Plot plot) {
        Properties properties = new Properties();
        properties.setProperty(KEY_TRUSTED, plot.getTrustedPlayers().stream().map(UUID::toString).collect(Collectors.joining(",")));
        return propertiesToString(properties);
    }

    private static Metadata decodeMetadata(String raw) {
        Properties properties = stringToProperties(raw);
        Metadata metadata = new Metadata();
        String trusted = properties.getProperty(KEY_TRUSTED, "");
        if (!trusted.trim().isEmpty()) {
            for (String token : trusted.split(",")) {
                if (!token.trim().isEmpty()) {
                    metadata.trusted.add(UUID.fromString(token.trim()));
                }
            }
        }
        return metadata;
    }

    private static String propertiesToString(Properties properties) {
        return properties.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(";"));
    }

    private static Properties stringToProperties(String raw) {
        Properties properties = new Properties();
        if (raw == null || raw.trim().isEmpty()) {
            return properties;
        }
        for (String entry : raw.split(";")) {
            int index = entry.indexOf('=');
            if (index <= 0) {
                continue;
            }
            properties.setProperty(entry.substring(0, index), entry.substring(index + 1));
        }
        return properties;
    }

    private static final class Metadata {
        private final Set<UUID> trusted = new LinkedHashSet<>();
    }
}
