package de.streuland.clan;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
                handleCreate(player, args);
                break;

            case "join":
                handleJoin(player, args);
                break;

            case "leave":
                handleLeave(player);
                break;

            case "info":
                handleInfo(player, args);
                break;

            case "list":
                handleList(player);
                break;

            case "ally":
                handleAlly(player, args);
                break;

            case "war":
                handleWar(player, args);
                break;

            case "peace":
                handlePeace(player, args);
                break;

            case "proposals":
                handleProposals(player);
                break;

            case "accept":
                handleAccept(player, args);
                break;

            case "reject":
                handleReject(player, args);
                break;

            case "raid":
                handleRaid(player, args);
                break;

            case "setmotto":
                handleSetMotto(player, args);
                break;

            case "setcolor":
                handleSetColor(player, args);
                break;

            case "members":
                handleMembers(player, args);
                break;

            default:
                sendHelp(player);
                break;
        }

        return true;
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cNutze: /clan create <Name>");
            return;
        }
        Clan clan = clanManager.createClan(args[1], player.getUniqueId());
        if (clan != null) {
            player.sendMessage("§aClan " + args[1] + " wurde erfolgreich erstellt!");
        } else {
            player.sendMessage("§cDu bist bereits in einem Clan.");
        }
    }

    private void handleJoin(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cNutze: /clan join <ClanID>");
            return;
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
    }

    private void handleLeave(Player player) {
        clanManager.leaveClan(player.getUniqueId());
        player.sendMessage("§aDu hast den Clan verlassen.");
    }

    private void handleInfo(Player player, String[] args) {
        Clan clan = null;

        if (args.length >= 2) {
            try {
                UUID clanId = UUID.fromString(args[1]);
                clan = clanManager.getClanById(clanId);
            } catch (IllegalArgumentException e) {
                for (Clan c : clanManager.getAllClans()) {
                    if (c.getName().equalsIgnoreCase(args[1])) {
                        clan = c;
                        break;
                    }
                }
            }
        } else {
            clan = clanManager.getClanByPlayer(player.getUniqueId());
        }

        if (clan == null) {
            player.sendMessage("§cClan nicht gefunden.");
            return;
        }

        sendClanInfo(player, clan);
    }

    private void sendClanInfo(Player player, Clan clan) {
        player.sendMessage("§6========== Clan Info ==========");
        player.sendMessage("§eName: §f" + clan.getDisplayName());
        if (!clan.getMotto().isEmpty()) {
            player.sendMessage("§eMotto: §f" + clan.getMotto());
        }
        player.sendMessage("§eMitglieder: §f" + clan.getMemberCount());
        player.sendMessage("§eParzellen: §f" + clan.getPlotCount());
        player.sendMessage("§eKills: §f" + clan.getKills());

        int allyCount = clanManager.getAllies(clan).size();
        int enemyCount = clanManager.getEnemies(clan).size();
        player.sendMessage("§eAllianzen: §a" + allyCount);
        player.sendMessage("§eFeinde: §c" + enemyCount);

        player.sendMessage("§eErstellt: §f" + formatDuration(clan.getAge()));
    }

    private void handleList(Player player) {
        Collection<Clan> clans = clanManager.getAllClans();

        if (clans.isEmpty()) {
            player.sendMessage("§cKeine Clans vorhanden.");
            return;
        }

        player.sendMessage("§6========== Clans (" + clans.size() + ") ==========");
        for (Clan clan : clans) {
            String status = "";
            Clan playerClan = clanManager.getClanByPlayer(player.getUniqueId());
            if (playerClan != null && !playerClan.getClanId().equals(clan.getClanId())) {
                DiplomacyStatus rel = playerClan.getRelationship(clan.getClanId());
                status = " §7(" + rel.getDisplayName() + ")";
            }
            player.sendMessage(clan.getDisplayName() + " §7- " + clan.getMemberCount() + " Mitglieder" + status);
        }
    }

    private void handleAlly(Player player, String[] args) {
        Clan playerClan = clanManager.getClanByPlayer(player.getUniqueId());
        if (playerClan == null) {
            player.sendMessage("§cDu bist in keinem Clan.");
            return;
        }

        if (!playerClan.getLeader().equals(player.getUniqueId())) {
            player.sendMessage("§cNur der Clan-Leader kann Allianzen vorschlagen.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§cNutze: /clan ally <ClanName>");
            return;
        }

        Clan targetClan = findClanByName(args[1]);
        if (targetClan == null) {
            player.sendMessage("§cClan nicht gefunden.");
            return;
        }

        if (targetClan.getClanId().equals(playerClan.getClanId())) {
            player.sendMessage("§cDu kannst keine Allianz mit dir selbst vorschlagen.");
            return;
        }

        if (clanManager.proposeAlly(playerClan.getClanId(), targetClan.getClanId())) {
            player.sendMessage("§aAllianz-Vorschlag an §e" + targetClan.getName() + " §agesendet!");
            if ( Bukkit.getPlayer(targetClan.getLeader()) != null) {
                Bukkit.getPlayer(targetClan.getLeader()).sendMessage(
                        "§6[Clan] §e" + playerClan.getName() + " §6schlägt eine Allianz vor! Nutze §e/clan proposals §6um anzunehmen.");
            }
        } else {
            player.sendMessage("§cAllianz-Vorschlag fehlgeschlagen.");
        }
    }

    private void handleWar(Player player, String[] args) {
        Clan playerClan = clanManager.getClanByPlayer(player.getUniqueId());
        if (playerClan == null) {
            player.sendMessage("§cDu bist in keinem Clan.");
            return;
        }

        if (!playerClan.getLeader().equals(player.getUniqueId())) {
            player.sendMessage("§cNur der Clan-Leader kann einen Krieg erklären.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§cNutze: /clan war <ClanName>");
            return;
        }

        Clan targetClan = findClanByName(args[1]);
        if (targetClan == null) {
            player.sendMessage("§cClan nicht gefunden.");
            return;
        }

        if (targetClan.getClanId().equals(playerClan.getClanId())) {
            player.sendMessage("§cDu kannst keinen Krieg gegen dich selbst erklären.");
            return;
        }

        if (clanManager.declareWar(playerClan.getClanId(), targetClan.getClanId())) {
            player.sendMessage("§4Kriegserklärung an §c" + targetClan.getName() + " §4gesendet!");
        } else {
            player.sendMessage("§cKriegserklärung fehlgeschlagen. Dein Clan braucht mindestens 3 Mitglieder.");
        }
    }

    private void handlePeace(Player player, String[] args) {
        Clan playerClan = clanManager.getClanByPlayer(player.getUniqueId());
        if (playerClan == null) {
            player.sendMessage("§cDu bist in keinem Clan.");
            return;
        }

        if (!playerClan.getLeader().equals(player.getUniqueId())) {
            player.sendMessage("§cNur der Clan-Leader kann Frieden vorschlagen.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§cNutze: /clan peace <ClanName>");
            return;
        }

        Clan targetClan = findClanByName(args[1]);
        if (targetClan == null) {
            player.sendMessage("§cClan nicht gefunden.");
            return;
        }

        if (clanManager.proposePeace(playerClan.getClanId(), targetClan.getClanId())) {
            player.sendMessage("§aFriedens-Vorschlag an §e" + targetClan.getName() + " §agesendet!");
        } else {
            player.sendMessage("§cFriedens-Vorschlag fehlgeschlagen.");
        }
    }

    private void handleProposals(Player player) {
        Clan playerClan = clanManager.getClanByPlayer(player.getUniqueId());
        if (playerClan == null) {
            player.sendMessage("§cDu bist in keinem Clan.");
            return;
        }

        Collection<ClanDiplomacyManager.DiplomaticProposal> proposals =
                clanManager.getPendingProposals(playerClan.getClanId());

        if (proposals.isEmpty()) {
            player.sendMessage("§cKeine offenen Vorschläge.");
            return;
        }

        player.sendMessage("§6========== Offene Vorschläge ==========");
        for (ClanDiplomacyManager.DiplomaticProposal proposal : proposals) {
            Clan otherClan = clanManager.getClanById(proposal.getRequestingClanId());
            if (otherClan == null) continue;

            String typeStr = "";
            switch (proposal.getType()) {
                case ALLY_REQUEST:
                    typeStr = "§aAllianz";
                    break;
                case WAR_DECLARATION:
                    typeStr = "§cKrieg";
                    break;
                case PEACE_TREATY:
                    typeStr = "§eFrieden";
                    break;
            }

            player.sendMessage("§eID: §f" + proposal.getProposalId());
            player.sendMessage("§eVon: §f" + otherClan.getDisplayName());
            player.sendMessage("§eTyp: " + typeStr);
            player.sendMessage("§eAnnehmen: §a/clan accept " + proposal.getProposalId());
            player.sendMessage("§eAblehnen: §c/clan reject " + proposal.getProposalId());
            player.sendMessage("§7--------------------------------");
        }
    }

    private void handleAccept(Player player, String[] args) {
        Clan playerClan = clanManager.getClanByPlayer(player.getUniqueId());
        if (playerClan == null) {
            player.sendMessage("§cDu bist in keinem Clan.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§cNutze: /clan accept <ProposalID>");
            return;
        }

        try {
            UUID proposalId = UUID.fromString(args[1]);
            if (clanManager.acceptDiplomacyProposal(proposalId, playerClan.getClanId())) {
                player.sendMessage("§aVorschlag angenommen!");
            } else {
                player.sendMessage("§cVorschlag nicht gefunden oder bereits beantwortet.");
            }
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cUngültige Vorschlags-ID.");
        }
    }

    private void handleReject(Player player, String[] args) {
        Clan playerClan = clanManager.getClanByPlayer(player.getUniqueId());
        if (playerClan == null) {
            player.sendMessage("§cDu bist in keinem Clan.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§cNutze: /clan reject <ProposalID>");
            return;
        }

        try {
            UUID proposalId = UUID.fromString(args[1]);
            if (clanManager.rejectDiplomacyProposal(proposalId, playerClan.getClanId())) {
                player.sendMessage("§cVorschlag abgelehnt.");
            } else {
                player.sendMessage("§cVorschlag nicht gefunden.");
            }
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cUngültige Vorschlags-ID.");
        }
    }

    private void handleRaid(Player player, String[] args) {
        Clan playerClan = clanManager.getClanByPlayer(player.getUniqueId());
        if (playerClan == null) {
            player.sendMessage("§cDu bist in keinem Clan.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§cNutze: /clan raid <ClanID>");
            return;
        }

        try {
            UUID targetId = UUID.fromString(args[1]);
            clanManager.startRaid(player.getUniqueId(), targetId);
            player.sendMessage("§aDeine Stimme für den Raid wurde gezählt!");
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cUngültige Clan-ID.");
        }
    }

    private void handleSetMotto(Player player, String[] args) {
        Clan playerClan = clanManager.getClanByPlayer(player.getUniqueId());
        if (playerClan == null) {
            player.sendMessage("§cDu bist in keinem Clan.");
            return;
        }

        if (!playerClan.getLeader().equals(player.getUniqueId())) {
            player.sendMessage("§cNur der Clan-Leader kann das Motto ändern.");
            return;
        }

        if (args.length < 2) {
            playerClan.setMotto("");
            player.sendMessage("§aMotto entfernt.");
            return;
        }

        String motto = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        if (motto.length() > 50) {
            player.sendMessage("§cMotto darf maximal 50 Zeichen lang sein.");
            return;
        }

        playerClan.setMotto(motto);
        player.sendMessage("§aMotto gesetzt: §f" + motto);
    }

    private void handleSetColor(Player player, String[] args) {
        Clan playerClan = clanManager.getClanByPlayer(player.getUniqueId());
        if (playerClan == null) {
            player.sendMessage("§cDu bist in keinem Clan.");
            return;
        }

        if (!playerClan.getLeader().equals(player.getUniqueId())) {
            player.sendMessage("§cNur der Clan-Leader kann die Farbe ändern.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§cNutze: /clan setcolor <farbe>");
            player.sendMessage("§7Verfügbare Farben: §eaqua§7, §cred§7, §2green§7, §egold§7, §dlightpurple§7, §9blue");
            return;
        }

        ChatColor color = parseChatColor(args[1]);
        if (color == null) {
            player.sendMessage("§cUngültige Farbe.");
            return;
        }

        playerClan.setColor(color);
        player.sendMessage("§aFarbe gesetzt auf " + color.name() + ".");
    }

    private void handleMembers(Player player, String[] args) {
        Clan clan = null;

        if (args.length >= 2) {
            clan = findClanByName(args[1]);
        } else {
            clan = clanManager.getClanByPlayer(player.getUniqueId());
        }

        if (clan == null) {
            player.sendMessage("§cClan nicht gefunden.");
            return;
        }

        player.sendMessage("��6========== Mitglieder von " + clan.getDisplayName() + " ==========");
        for (UUID memberId : clan.getMembers()) {
            org.bukkit.OfflinePlayer bukkitPlayer = Bukkit.getOfflinePlayer(memberId);
            String name = bukkitPlayer.getName() != null ? bukkitPlayer.getName() : "Unbekannt";
            if (memberId.equals(clan.getLeader())) {
                player.sendMessage("§e" + name + " §7(Leader)");
            } else {
                player.sendMessage("§f" + name);
            }
        }
    }

    private Clan findClanByName(String name) {
        for (Clan clan : clanManager.getAllClans()) {
            if (clan.getName().equalsIgnoreCase(name)) {
                return clan;
            }
        }
        return null;
    }

    private ChatColor parseChatColor(String name) {
        name = name.toLowerCase();
        switch (name) {
            case "aqua": return ChatColor.AQUA;
            case "red": return ChatColor.RED;
            case "green": return ChatColor.GREEN;
            case "gold": return ChatColor.GOLD;
            case "lightpurple": return ChatColor.LIGHT_PURPLE;
            case "blue": return ChatColor.BLUE;
            case "darkgray": return ChatColor.DARK_GRAY;
            case "gray": return ChatColor.GRAY;
            case "darkgreen": return ChatColor.DARK_GREEN;
            default: return null;
        }
    }

    private String formatDuration(long millis) {
        long days = millis / (24 * 60 * 60 * 1000);
        if (days > 0) return days + " Tage";
        long hours = millis / (60 * 60 * 1000);
        if (hours > 0) return hours + " Stunden";
        long minutes = millis / (60 * 1000);
        return minutes + " Minuten";
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6========== Clan System ==========");
        player.sendMessage("§e/clan create <Name> §7- Erstelle einen Clan");
        player.sendMessage("§e/clan join <ID> §7- Tritt einem Clan bei");
        player.sendMessage("§e/clan leave §7- Verlasse den Clan");
        player.sendMessage("§e/clan info [Name] §7- Clan-Info anzeigen");
        player.sendMessage("§e/clan list §7- Alle Clans auflisten");
        player.sendMessage("§e/clan members [Name] §7- Mitglieder anzeigen");
        player.sendMessage("§6--- Diplomatie ---");
        player.sendMessage("§e/clan ally <Name> §7- Allianz vorschlagen");
        player.sendMessage("§e/clan war <Name> §7- Krieg erklären");
        player.sendMessage("§e/clan peace <Name> §7- Frieden vorschlagen");
        player.sendMessage("§e/clan proposals §7- Offene Vorschläge");
        player.sendMessage("§e/clan accept <ID> §7- Vorschlag annehmen");
        player.sendMessage("§e/clan reject <ID> §7- Vorschlag ablehnen");
        player.sendMessage("§6--- Verwaltung ---");
        player.sendMessage("§e/clan raid <ID> §7- Für Raid stimmen");
        player.sendMessage("§e/clan setmotto <Text> §7- Setze Motto");
        player.sendMessage("§e/clan setcolor <Farbe> §7- Setze Clan-Farbe");
    }
}