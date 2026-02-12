package de.streuland.admin;

import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BlockChangeLogger {
    public static final UUID SYSTEM_UUID = new UUID(0L, 0L);

    private final JavaPlugin plugin;
    private final PlotManager plotManager;
    private final String jdbcUrl;

    public BlockChangeLogger(JavaPlugin plugin, PlotManager plotManager) {
        this.plugin = plugin;
        this.plotManager = plotManager;
        File dbFile = new File(plugin.getDataFolder(), "blockchanges.db");
        this.jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        initialize();
    }

    private void initialize() {
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS block_changes ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "plot_id TEXT NOT NULL,"
                    + "player_uuid TEXT NOT NULL,"
                    + "action_type TEXT NOT NULL,"
                    + "changed_at INTEGER NOT NULL,"
                    + "x INTEGER NOT NULL,"
                    + "y INTEGER NOT NULL,"
                    + "z INTEGER NOT NULL,"
                    + "from_block TEXT NOT NULL,"
                    + "to_block TEXT NOT NULL"
                    + ")");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_block_changes_plot_xyz_time ON block_changes(plot_id, x, z, changed_at DESC)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_block_changes_player_time ON block_changes(player_uuid, changed_at DESC)");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize blockchanges.db: " + e.getMessage());
        }
    }

    public void logChange(UUID playerId, BlockChangeAction action, int x, int y, int z, String fromBlock, String toBlock) {
        Plot plot = plotManager.getPlotAt(x, z);
        if (plot == null) {
            return;
        }
        UUID safePlayerId = playerId == null ? SYSTEM_UUID : playerId;
        String sql = "INSERT INTO block_changes(plot_id, player_uuid, action_type, changed_at, x, y, z, from_block, to_block) VALUES(?,?,?,?,?,?,?,?,?)";
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, plot.getPlotId());
            statement.setString(2, safePlayerId.toString());
            statement.setString(3, action.name());
            statement.setLong(4, System.currentTimeMillis());
            statement.setInt(5, x);
            statement.setInt(6, y);
            statement.setInt(7, z);
            statement.setString(8, fromBlock == null ? "UNKNOWN" : fromBlock);
            statement.setString(9, toBlock == null ? "UNKNOWN" : toBlock);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to log block change: " + e.getMessage());
        }
    }

    public List<BlockChangeEntry> getChangesForCoordinate(String plotId, int x, int z, int limit) {
        String sql = "SELECT plot_id, player_uuid, changed_at, action_type, x, y, z, from_block, to_block FROM block_changes "
                + "WHERE plot_id = ? AND x = ? AND z = ? ORDER BY changed_at DESC LIMIT ?";
        return queryEntries(sql, statement -> {
            statement.setString(1, plotId);
            statement.setInt(2, x);
            statement.setInt(3, z);
            statement.setInt(4, limit);
        });
    }

    public List<BlockChangeEntry> getChangesByPlayer(UUID playerId, int limit) {
        String sql = "SELECT plot_id, player_uuid, changed_at, action_type, x, y, z, from_block, to_block FROM block_changes "
                + "WHERE player_uuid = ? ORDER BY changed_at DESC LIMIT ?";
        return queryEntries(sql, statement -> {
            statement.setString(1, playerId.toString());
            statement.setInt(2, limit);
        });
    }

    public int deleteChangesForPlotSince(String plotId, Duration window) {
        long threshold = System.currentTimeMillis() - window.toMillis();
        String sql = "DELETE FROM block_changes WHERE plot_id = ? AND changed_at >= ?";
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, plotId);
            statement.setLong(2, threshold);
            return statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to prune block change log: " + e.getMessage());
            return 0;
        }
    }

    private interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    private List<BlockChangeEntry> queryEntries(String sql, StatementBinder binder) {
        List<BlockChangeEntry> results = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String actionRaw = resultSet.getString("action_type");
                    BlockChangeAction action;
                    try {
                        action = BlockChangeAction.valueOf(actionRaw);
                    } catch (IllegalArgumentException e) {
                        action = BlockChangeAction.BREAK;
                    }
                    UUID playerId;
                    try {
                        playerId = UUID.fromString(resultSet.getString("player_uuid"));
                    } catch (Exception ignored) {
                        playerId = SYSTEM_UUID;
                    }
                    results.add(new BlockChangeEntry(
                            resultSet.getString("plot_id"),
                            playerId,
                            resultSet.getLong("changed_at"),
                            action,
                            resultSet.getInt("x"),
                            resultSet.getInt("y"),
                            resultSet.getInt("z"),
                            resultSet.getString("from_block"),
                            resultSet.getString("to_block")
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to query block changes: " + e.getMessage());
        }
        return results;
    }
}
