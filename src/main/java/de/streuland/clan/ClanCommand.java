package de.streuland.clan;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class ClanCommand implements CommandExecutor {
    private final ClanManager clanManager;

    public ClanCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Nur Spieler können diesen Befehl nutzen.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create":
                if (args.length < 2) {
                    player.sendMessage("§cNutze: /clan create <Name>");
                    return true;
                }
                Clan clan = clanManager.createClan(args[1], player.getUniqueId());
                if (clan != null) {
                    player.sendMessage("§aClan " + args[1] + " wurde erfolgreich erstellt!");
                } else {
                    player.sendMessage("§cDu bist bereits in einem Clan.");
                }
                break;

            case "join":
                if (args.length < 2) {
                    player.sendMessage("§cNutze: /clan join <ClanID>");
                    return true;
                }
                try {
                    UUID clanId = UUID.fromString(args[1]);
                    if (clanManager.joinClan(player.getUniqueId(), clanId)) {
                        player.sendMessage("§aDu bist dem Clan beigetreten!");
                    } else {
                        player.sendMessage("§cBeitritt fehlgeschlagen.");
                    }
                } catch (IllegalArgumentException e) {
                    player.sendMessage("§cUngültige Clan-ID.");
                }
                break;

            case "raid":
                if (args.length < 2) {
                    player.sendMessage("§cNutze: /clan raid <ClanID>");
                    return true;
                }
                try {
                    UUID targetId = UUID.fromString(args[1]);
                    clanManager.startRaid(player.getUniqueId(), targetId);
                    player.sendMessage("§aDeine Stimme für den Raid wurde gezählt!");
                } catch (IllegalArgumentException e) {
                    player.sendMessage("§cUngültige Clan-ID.");
                }
                break;

            default:
                sendHelp(player);
                break;
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6--- Clan System Hilfe ---");
        player.sendMessage("§e/clan create <Name> §7- Erstelle einen Clan");
        player.sendMessage("§e/clan join <ID> §7- Tritt einem Clan bei");
        player.sendMessage("§e/clan raid <ID> §7- Starte/Stimme für einen Raid");
    }
}
