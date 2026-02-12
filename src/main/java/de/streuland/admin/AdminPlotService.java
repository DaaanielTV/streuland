package de.streuland.admin;

import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import de.streuland.plot.snapshot.SnapshotManager;
import de.streuland.plot.snapshot.SnapshotMeta;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class AdminPlotService {
    public static final String PERMISSION_INSPECT = "streuland.plot.admin.inspect";
    public static final String PERMISSION_LOG = "streuland.plot.admin.log";
    public static final String PERMISSION_ROLLBACK = "streuland.plot.admin.rollback";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final PlotManager plotManager;
    private final SnapshotManager snapshotManager;
    private final BlockChangeLogger blockChangeLogger;
    private final Map<UUID, PendingRollback> pendingRollbacks = new HashMap<>();

    public AdminPlotService(PlotManager plotManager, SnapshotManager snapshotManager, BlockChangeLogger blockChangeLogger) {
        this.plotManager = plotManager;
        this.snapshotManager = snapshotManager;
        this.blockChangeLogger = blockChangeLogger;
    }

    public boolean handleInspect(Player player, String[] args) {
        if (!player.hasPermission(PERMISSION_INSPECT)) {
            player.sendMessage("§cKeine Berechtigung für Plot-Inspektion.");
            return true;
        }
        if (args.length < 3) {
            player.sendMessage("§cVerwendung: /plot inspect <x> <z>");
            return true;
        }
        int x;
        int z;
        try {
            x = Integer.parseInt(args[1]);
            z = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage("§cKoordinaten müssen Zahlen sein.");
            return true;
        }

        Plot plot = plotManager.getPlotAt(player.getWorld(), x, z);
        if (plot == null) {
            player.sendMessage("§cKein Plot an diesen Koordinaten gefunden.");
            return true;
        }

        List<BlockChangeEntry> changes = blockChangeLogger.getChangesForCoordinate(plot.getPlotId(), x, z, 20);
        player.sendMessage("§6=== Block-Inspektion " + plot.getPlotId() + " @ " + x + "," + z + " ===");
        if (changes.isEmpty()) {
            player.sendMessage("§7Keine Änderungen protokolliert.");
            return true;
        }
        for (BlockChangeEntry change : changes) {
            String playerName = resolveName(change.getPlayerId());
            player.sendMessage("§e" + DATE_FORMATTER.format(change.getTimestampInstant())
                    + " §7[" + change.getAction().name() + "] §f"
                    + playerName + " §8(" + change.getX() + "," + change.getY() + "," + change.getZ() + ")"
                    + " §7" + change.getFromBlock() + " §f→ §a" + change.getToBlock());
        }
        return true;
    }

    public boolean handleAdmin(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cVerwendung: /plot admin <rollback|log>");
            return true;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        if ("rollback".equals(sub)) {
            return handleRollback(player, args);
        }
        if ("log".equals(sub)) {
            return handlePlayerLog(player, args);
        }
        player.sendMessage("§cVerwendung: /plot admin <rollback|log>");
        return true;
    }

    private boolean handlePlayerLog(Player player, String[] args) {
        if (!player.hasPermission(PERMISSION_LOG)) {
            player.sendMessage("§cKeine Berechtigung für Spieler-Logs.");
            return true;
        }
        if (args.length < 3) {
            player.sendMessage("§cVerwendung: /plot admin log <player>");
            return true;
        }
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(args[2]);
        if (offlinePlayer == null || offlinePlayer.getUniqueId() == null) {
            player.sendMessage("§cSpieler nicht gefunden.");
            return true;
        }
        List<BlockChangeEntry> changes = blockChangeLogger.getChangesByPlayer(offlinePlayer.getUniqueId(), 50);
        player.sendMessage("§6=== Blockchanges von " + resolveName(offlinePlayer.getUniqueId()) + " ===");
        if (changes.isEmpty()) {
            player.sendMessage("§7Keine Blockänderungen vorhanden.");
            return true;
        }
        for (BlockChangeEntry change : changes) {
            player.sendMessage("§e" + DATE_FORMATTER.format(change.getTimestampInstant())
                    + " §7[" + change.getAction().name() + "] §fPlot " + change.getPlotId()
                    + " §8(" + change.getX() + "," + change.getY() + "," + change.getZ() + ")"
                    + " §7" + change.getFromBlock() + " §f→ §a" + change.getToBlock());
        }
        return true;
    }

    private boolean handleRollback(Player player, String[] args) {
        if (!player.hasPermission(PERMISSION_ROLLBACK)) {
            player.sendMessage("§cKeine Berechtigung für Rollback.");
            return true;
        }
        if (args.length >= 3 && "confirm".equalsIgnoreCase(args[2])) {
            return confirmRollback(player);
        }
        if (args.length < 3) {
            player.sendMessage("§cVerwendung: /plot admin rollback <plot> [days]");
            player.sendMessage("§7Bestätigen mit /plot admin rollback confirm");
            return true;
        }

        String plotId = args[2];
        Plot plot = plotManager.getStorage().getPlot(plotId);
        if (plot == null) {
            player.sendMessage("§cPlot nicht gefunden: " + plotId);
            return true;
        }
        int days = 1;
        if (args.length >= 4) {
            try {
                days = Math.max(1, Integer.parseInt(args[3]));
            } catch (NumberFormatException e) {
                player.sendMessage("§cTage müssen numerisch sein.");
                return true;
            }
        }

        long threshold = System.currentTimeMillis() - Duration.ofDays(days).toMillis();
        List<SnapshotMeta> snapshots = snapshotManager.listSnapshots(plotId);
        SnapshotMeta candidate = null;
        for (SnapshotMeta meta : snapshots) {
            if (meta.getCreatedAt() <= threshold) {
                candidate = meta;
            }
        }
        if (candidate == null && !snapshots.isEmpty()) {
            candidate = snapshots.get(snapshots.size() - 1);
        }
        if (candidate == null) {
            player.sendMessage("§cKeine Backups für Plot " + plotId + " gefunden.");
            return true;
        }

        pendingRollbacks.put(player.getUniqueId(), new PendingRollback(plotId, candidate.getId(), days, System.currentTimeMillis() + 30_000L));
        player.sendMessage("§eRollback vorbereitet: Plot §f" + plotId + "§e -> Snapshot §f" + candidate.getId());
        player.sendMessage("§eVerwende §f/plot admin rollback confirm §ezur Bestätigung (30s). ");
        return true;
    }

    private boolean confirmRollback(Player player) {
        PendingRollback pendingRollback = pendingRollbacks.get(player.getUniqueId());
        if (pendingRollback == null) {
            player.sendMessage("§cKein Rollback ausstehend.");
            return true;
        }
        if (System.currentTimeMillis() > pendingRollback.expiresAt) {
            pendingRollbacks.remove(player.getUniqueId());
            player.sendMessage("§cRollback-Bestätigung abgelaufen.");
            return true;
        }

        player.sendMessage("§eRollback läuft... ");
        snapshotManager.restoreSnapshot(pendingRollback.plotId, pendingRollback.snapshotId, false).thenRun(() -> {
            blockChangeLogger.deleteChangesForPlotSince(pendingRollback.plotId, Duration.ofDays(pendingRollback.days));
            player.sendMessage("§aRollback abgeschlossen für Plot " + pendingRollback.plotId + ".");
        });
        pendingRollbacks.remove(player.getUniqueId());
        return true;
    }

    private String resolveName(UUID playerId) {
        if (playerId == null || BlockChangeLogger.SYSTEM_UUID.equals(playerId)) {
            return "SYSTEM";
        }
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
        return offlinePlayer.getName() == null ? playerId.toString() : offlinePlayer.getName();
    }

    private static class PendingRollback {
        private final String plotId;
        private final String snapshotId;
        private final int days;
        private final long expiresAt;

        private PendingRollback(String plotId, String snapshotId, int days, long expiresAt) {
            this.plotId = plotId;
            this.snapshotId = snapshotId;
            this.days = days;
            this.expiresAt = expiresAt;
        }
    }
}
