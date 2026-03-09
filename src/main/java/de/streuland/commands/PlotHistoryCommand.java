package de.streuland.commands;

import de.streuland.history.JournalManager;
import de.streuland.history.PlotChangeJournal.JournalEntry;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class PlotHistoryCommand {
    private final JournalManager journalManager;

    public PlotHistoryCommand(JournalManager journalManager) {
        this.journalManager = journalManager;
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Nur Spieler können /plot history nutzen.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.isOp()) {
            player.sendMessage("§cNur Admins dürfen den Plot-Verlauf verwalten.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§cVerwendung: /plot history <plotId> [since|to|list]");
            player.sendMessage("§cOder: /plot history rollback <plotId> <timestamp> [--dry-run]");
            player.sendMessage("§cOder: /plot history replay <plotId> <from> <to>");
            return true;
        }
        if ("rollback".equalsIgnoreCase(args[1])) {
            return handleRollback(player, args);
        }
        if ("replay".equalsIgnoreCase(args[1])) {
            return handleReplay(player, args);
        }
        return handleList(player, args);
    }

    private boolean handleList(Player player, String[] args) {
        String plotId = args[1];
        long since = 0L;
        long to = Long.MAX_VALUE;
        if (args.length >= 4 && "since".equalsIgnoreCase(args[2])) {
            since = parseLong(args[3], -1L);
        }
        if (args.length >= 4 && "to".equalsIgnoreCase(args[2])) {
            to = parseLong(args[3], -1L);
        }
        if (since < 0 || to < 0) {
            player.sendMessage("§cZeitstempel ungültig.");
            return true;
        }
        List<JournalEntry> entries = journalManager.between(plotId, since, to);
        player.sendMessage("§6Plot-History " + plotId + " §7(" + entries.size() + " Einträge)");
        int limit = Math.min(entries.size(), 10);
        for (int i = 0; i < limit; i++) {
            JournalEntry e = entries.get(i);
            player.sendMessage("§7- ts=" + e.timestamp + " @ " + e.x + "," + e.y + "," + e.z + " "
                    + (e.before == null ? "?" : e.before.type) + " -> " + (e.after == null ? "?" : e.after.type));
        }
        return true;
    }

    private boolean handleRollback(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage("§cVerwendung: /plot history rollback <plotId> <timestamp> [--dry-run]");
            return true;
        }
        String plotId = args[2];
        long timestamp = parseLong(args[3], -1L);
        if (timestamp < 0) {
            player.sendMessage("§cZeitstempel ungültig.");
            return true;
        }
        boolean dryRun = args.length >= 5 && "--dry-run".equalsIgnoreCase(args[4]);
        List<JournalEntry> affected = journalManager.rollbackTo(plotId, timestamp, dryRun);
        if (dryRun) {
            player.sendMessage("§eDry-run: " + affected.size() + " Änderungen würden zurückgesetzt.");
        } else {
            player.sendMessage("§aRollback abgeschlossen: " + affected.size() + " Änderungen zurückgesetzt.");
        }
        return true;
    }

    private boolean handleReplay(Player player, String[] args) {
        if (args.length < 5) {
            player.sendMessage("§cVerwendung: /plot history replay <plotId> <from> <to>");
            return true;
        }
        String plotId = args[2];
        long from = parseLong(args[3], -1L);
        long to = parseLong(args[4], -1L);
        if (from < 0 || to < 0 || to < from) {
            player.sendMessage("§cUngültiger Zeitbereich.");
            return true;
        }
        journalManager.replay(plotId, from, to, player);
        player.sendMessage("§aReplay gestartet.");
        return true;
    }

    private long parseLong(String raw, long fallback) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
