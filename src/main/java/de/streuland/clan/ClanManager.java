package de.streuland.clan;

import de.streuland.path.PathGenerator;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClanManager {
    private final JavaPlugin plugin;
    private final PlotManager plotManager;
    private final PathGenerator pathGenerator;
    private final Map<UUID, Clan> clans;
    private final Map<UUID, UUID> playerClanMap;
    private final Map<UUID, Raid> activeRaids;
    private final ClanDiplomacyManager diplomacyManager;

    public ClanManager(JavaPlugin plugin, PlotManager plotManager, PathGenerator pathGenerator) {
        this.plugin = plugin;
        this.plotManager = plotManager;
        this.pathGenerator = pathGenerator;
        this.clans = new ConcurrentHashMap<>();
        this.playerClanMap = new ConcurrentHashMap<>();
        this.activeRaids = new ConcurrentHashMap<>();
        this.diplomacyManager = new ClanDiplomacyManager(plugin, clans);
    }

    public ClanDiplomacyManager getDiplomacyManager() {
        return diplomacyManager;
    }

    public Clan createClan(String name, UUID leader) {
        if (playerClanMap.containsKey(leader)) {
            return null;
        }
        Clan clan = new Clan(UUID.randomUUID(), name, leader);
        clans.put(clan.getClanId(), clan);
        playerClanMap.put(leader, clan.getClanId());
        
        // Add leader's plots to clan
        for (Plot plot : plotManager.getPlotsByOwner(leader)) {
            clan.addPlot(plot.getPlotId());
        }
        
        updateClanPaths(clan);
        return clan;
    }

    public boolean joinClan(UUID player, UUID clanId) {
        Clan clan = clans.get(clanId);
        if (clan == null || playerClanMap.containsKey(player)) {
            return false;
        }
        clan.addMember(player);
        playerClanMap.put(player, clanId);
        
        // Add player's plots to clan
        for (Plot plot : plotManager.getPlotsByOwner(player)) {
            clan.addPlot(plot.getPlotId());
        }
        
        updateClanPaths(clan);
        return true;
    }

    public void updateClanPaths(Clan clan) {
        List<String> plotIds = new ArrayList<>(clan.getPlotIds());
        if (plotIds.size() < 2) return;

        List<PathGenerator.BlockPosition> allPathBlocks = new ArrayList<>();
        for (int i = 0; i < plotIds.size() - 1; i++) {
            Plot p1 = plotManager.getStorage().getPlot(plotIds.get(i));
            Plot p2 = plotManager.getStorage().getPlot(plotIds.get(i + 1));
            if (p1 != null && p2 != null) {
                allPathBlocks.addAll(pathGenerator.generatePathBetween(
                    p1.getCenterX(), p1.getCenterZ(),
                    p2.getCenterX(), p2.getCenterZ()
                ));
            }
        }
        pathGenerator.buildPathBlocks(allPathBlocks, true);
    }

    public void startRaid(UUID initiatorId, UUID targetClanId) {
        Clan initiatorClan = getClanByPlayer(initiatorId);
        Clan targetClan = clans.get(targetClanId);
        
        if (initiatorClan == null || targetClan == null || initiatorClan.equals(targetClan)) return;
        
        Raid raid = activeRaids.computeIfAbsent(targetClanId, k -> new Raid(initiatorClan.getClanId(), targetClanId));
        raid.addVote(initiatorId);
        
        double approval = (double) raid.getVotes().size() / initiatorClan.getMembers().size();
        if (approval > 0.5) {
            executeRaid(raid);
        }
    }

    private void executeRaid(Raid raid) {
        Clan winner = clans.get(raid.getAttackerClanId());
        Clan loser = clans.get(raid.getVictimClanId());
        
        if (winner == null || loser == null) return;
        
        // Transfer all plots from loser to winner
        for (String plotId : new ArrayList<>(loser.getPlotIds())) {
            Plot plot = plotManager.getStorage().getPlot(plotId);
            if (plot != null) {
                plotManager.transferPlotOwnership(plotId, plot.getOwner(), winner.getLeader());
                winner.addPlot(plotId);
            }
        }
        
        loser.getPlotIds().clear();
        updateClanPaths(winner);
        activeRaids.remove(raid.getVictimClanId());
        
        Bukkit.broadcastMessage("§6[Clan] Der Clan " + winner.getName() + " hat den Clan " + loser.getName() + " erfolgreich überfallen!");
    }

    private static class Raid {
        private final UUID attackerClanId;
        private final UUID victimClanId;
        private final Set<UUID> votes = new HashSet<>();

        public Raid(UUID attackerClanId, UUID victimClanId) {
            this.attackerClanId = attackerClanId;
            this.victimClanId = victimClanId;
        }

        public UUID getAttackerClanId() { return attackerClanId; }
        public UUID getVictimClanId() { return victimClanId; }
        public Set<UUID> getVotes() { return votes; }
        public void addVote(UUID player) { votes.add(player); }
    }

    public void leaveClan(UUID player) {
        UUID clanId = playerClanMap.remove(player);
        if (clanId != null) {
            Clan clan = clans.get(clanId);
            if (clan != null) {
                clan.removeMember(player);
                // If leader leaves, assign new leader or disband
                if (clan.getLeader().equals(player)) {
                    if (clan.getMembers().isEmpty()) {
                        clans.remove(clanId);
                    } else {
                        clan.setLeader(clan.getMembers().iterator().next());
                    }
                }
                
                // Remove player's plots from clan
                for (Plot plot : plotManager.getPlotsByOwner(player)) {
                    clan.removePlot(plot.getPlotId());
                }
            }
        }
    }

    public Clan getClanByPlayer(UUID player) {
        UUID clanId = playerClanMap.get(player);
        return clanId != null ? clans.get(clanId) : null;
    }

    public Clan getClanById(UUID clanId) {
        return clans.get(clanId);
    }

    public Collection<Clan> getAllClans() {
        return clans.values();
    }

    public boolean proposeAlly(UUID requesterId, UUID targetId) {
        return diplomacyManager.proposeAlly(requesterId, targetId);
    }

    public boolean declareWar(UUID attackerId, UUID targetId) {
        return diplomacyManager.declareWar(attackerId, targetId);
    }

    public boolean proposePeace(UUID requesterId, UUID targetId) {
        return diplomacyManager.proposePeace(requesterId, targetId);
    }

    public boolean acceptDiplomacyProposal(UUID proposalId, UUID acceptingClanId) {
        return diplomacyManager.acceptProposal(proposalId, acceptingClanId);
    }

    public boolean rejectDiplomacyProposal(UUID proposalId, UUID rejectingClanId) {
        return diplomacyManager.rejectProposal(proposalId, rejectingClanId);
    }

    public boolean isAtWar(UUID clanIdA, UUID clanIdB) {
        return diplomacyManager.isAtWar(clanIdA, clanIdB);
    }

    public boolean isAlly(UUID clanIdA, UUID clanIdB) {
        return diplomacyManager.isAlly(clanIdA, clanIdB);
    }

    public List<Clan> getAllies(Clan clan) {
        return diplomacyManager.getAllies(clan);
    }

    public List<Clan> getEnemies(Clan clan) {
        return diplomacyManager.getEnemies(clan);
    }

    public Collection<ClanDiplomacyManager.DiplomaticProposal> getPendingProposals(UUID clanId) {
        return diplomacyManager.getPendingProposals(clanId);
    }

    public void update() {
        diplomacyManager.checkWarExpiry();
        diplomacyManager.cleanupExpiredProposals();
    }

    public int getTotalClanCount() {
        return clans.size();
    }

    public int getActiveWarCount() {
        long count = clans.keySet().stream()
                .filter(clanId -> diplomacyManager.getActiveWar(clanId) != null)
                .count();
        return (int) count;
    }
}
