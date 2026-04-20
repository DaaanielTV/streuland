package de.streuland.clan;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ClanDiplomacyManager {
    private final JavaPlugin plugin;
    private final Map<UUID, Clan> clans;
    private final Map<UUID, DiplomaticProposal> pendingProposals;
    private final Map<UUID, ActiveWar> activeWars;

    private static final long WAR_DURATION_MS = 24 * 60 * 60 * 1000L;
    private static final long PEACE_TREATY_DURATION_MS = 12 * 60 * 60 * 1000L;
    private static final int WAR_VOTE_THRESHOLD = 3;
    private static final long PROPOSAL_TIMEOUT_MS = 5 * 60 * 1000L;

    public ClanDiplomacyManager(JavaPlugin plugin, Map<UUID, Clan> clans) {
        this.plugin = plugin;
        this.clans = clans;
        this.pendingProposals = new ConcurrentHashMap<>();
        this.activeWars = new ConcurrentHashMap<>();
    }

    public enum ProposalType {
        ALLY_REQUEST,
        WAR_DECLARATION,
        PEACE_TREATY
    }

    public DiplomaticProposal createProposal(UUID fromClanId, UUID toClanId, ProposalType type) {
        Clan fromClan = clans.get(fromClanId);
        Clan toClan = clans.get(toClanId);

        if (fromClan == null || toClan == null) {
            return null;
        }

        if (fromClanId.equals(toClanId)) {
            return null;
        }

        if (type == ProposalType.ALLY_REQUEST) {
            DiplomacyStatus current = fromClan.getRelationship(toClanId);
            if (current == DiplomacyStatus.ALLY) {
                return null;
            }
        }

        if (type == ProposalType.WAR_DECLARATION) {
            if (!canDeclareWar(fromClan)) {
                return null;
            }
        }

        DiplomaticProposal proposal = new DiplomaticProposal(
                UUID.randomUUID(),
                fromClanId,
                toClanId,
                type,
                System.currentTimeMillis()
        );

        pendingProposals.put(proposal.getProposalId(), proposal);

        if (type == ProposalType.WAR_DECLARATION) {
            Clan targetClan = clans.get(toClanId);
            if (targetClan != null) {
                targetClan.setWarDeclarationTime(System.currentTimeMillis());
            }
        }

        return proposal;
    }

    public boolean acceptProposal(UUID proposalId, UUID acceptingClanId) {
        DiplomaticProposal proposal = pendingProposals.get(proposalId);
        if (proposal == null) {
            return false;
        }

        if (!proposal.isPending()) {
            return false;
        }

        UUID targetClanId = proposal.getTargetClanId();
        if (!targetClanId.equals(acceptingClanId)) {
            return false;
        }

        Clan requestingClan = clans.get(proposal.getRequestingClanId());
        Clan targetClan = clans.get(targetClanId);

        if (requestingClan == null || targetClan == null) {
            return false;
        }

        ProposalType type = proposal.getType();
        boolean success = false;

        switch (type) {
            case ALLY_REQUEST:
                success = createAlliance(requestingClan, targetClan);
                break;
            case WAR_DECLARATION:
                success = startWar(requestingClan, targetClan);
                break;
            case PEACE_TREATY:
                success = signPeaceTreaty(requestingClan, targetClan);
                break;
        }

        pendingProposals.remove(proposalId);
        return success;
    }

    public boolean rejectProposal(UUID proposalId, UUID rejectingClanId) {
        DiplomaticProposal proposal = pendingProposals.get(proposalId);
        if (proposal == null) {
            return false;
        }

        if (!proposal.isPending()) {
            return false;
        }

        Clan targetClan = clans.get(proposal.getTargetClanId());
        Clan requestingClan = clans.get(proposal.getRequestingClanId());

        if (targetClan == null || rejectingClanId.equals(targetClan.getLeader())) {
            pendingProposals.remove(proposalId);
            return true;
        }

        if (requestingClan != null && rejectingClanId.equals(requestingClan.getLeader())) {
            pendingProposals.remove(proposalId);
            return true;
        }

        return false;
    }

    private boolean createAlliance(Clan clanA, Clan clanB) {
        clanA.setRelationship(clanB.getClanId(), DiplomacyStatus.ALLY);
        clanB.setRelationship(clanA.getClanId(), DiplomacyStatus.ALLY);

        broadcastAllianceMessage(clanA, clanB, true);
        return true;
    }

    private boolean startWar(Clan attacker, Clan defender) {
        attacker.setRelationship(defender.getClanId(), DiplomacyStatus.WAR);
        defender.setRelationship(attacker.getClanId(), DiplomacyStatus.WAR);

        ActiveWar war = new ActiveWar(
                UUID.randomUUID(),
                attacker.getClanId(),
                defender.getClanId(),
                System.currentTimeMillis(),
                WAR_DURATION_MS
        );

        activeWars.put(war.getWarId(), war);

        broadcastWarMessage(attacker, defender);
        return true;
    }

    private boolean signPeaceTreaty(Clan proposer, Clan acceptor) {
        proposer.setRelationship(acceptor.getClanId(), DiplomacyStatus.NEUTRAL);
        acceptor.setRelationship(proposer.getClanId(), DiplomacyStatus.NEUTRAL);

        ActiveWar warToRemove = null;
        for (ActiveWar war : activeWars.values()) {
            if ((war.getAttackerClanId().equals(proposer.getClanId()) &&
                    war.getDefenderClanId().equals(acceptor.getClanId())) ||
                    (war.getAttackerClanId().equals(acceptor.getClanId()) &&
                            war.getDefenderClanId().equals(proposer.getClanId())))) {
                warToRemove = war;
                break;
            }
        }

        if (warToRemove != null) {
            activeWars.remove(warToRemove.getWarId());
        }

        broadcastPeaceMessage(proposer, acceptor);
        return true;
    }

    public boolean declareWar(UUID attackerId, UUID targetId) {
        Clan attacker = clans.get(attackerId);
        Clan target = clans.get(targetId);

        if (attacker == null || target == null) {
            return false;
        }

        if (!canDeclareWar(attacker)) {
            return false;
        }

        DiplomaticProposal proposal = createProposal(attackerId, targetId, ProposalType.WAR_DECLARATION);
        return proposal != null;
    }

    public boolean proposeAlly(UUID requesterId, UUID targetId) {
        Clan requester = clans.get(requesterId);
        Clan target = clans.get(targetId);

        if (requester == null || target == null) {
            return false;
        }

        if (requester.getRelationship(targetId) == DiplomacyStatus.ALLY) {
            return false;
        }

        DiplomaticProposal proposal = createProposal(requesterId, targetId, ProposalType.ALLY_REQUEST);
        return proposal != null;
    }

    public boolean proposePeace(UUID requesterId, UUID targetId) {
        Clan requester = clans.get(requesterId);
        Clan target = clans.get(targetId);

        if (requester == null || target == null) {
            return false;
        }

        DiplomaticProposal proposal = createProposal(requesterId, targetId, ProposalType.PEACE_TREATY);
        return proposal != null;
    }

    private boolean canDeclareWar(Clan clan) {
        if (clan.getMemberCount() < 3) {
            return false;
        }

        for (DiplomacyStatus status : clan.getRelationships().values()) {
            if (status == DiplomacyStatus.WAR) {
                return false;
            }
        }

        return true;
    }

    public boolean isAtWar(UUID clanIdA, UUID clanIdB) {
        Clan clanA = clans.get(clanIdA);
        if (clanA == null) {
            return false;
        }
        return clanA.getRelationship(clanIdB) == DiplomacyStatus.WAR;
    }

    public boolean isAlly(UUID clanIdA, UUID clanIdB) {
        Clan clanA = clans.get(clanIdA);
        if (clanA == null) {
            return false;
        }
        return clanA.getRelationship(clanIdB) == DiplomacyStatus.ALLY;
    }

    public List<Clan> getAllies(Clan clan) {
        List<Clan> allies = new ArrayList<>();
        for (Map.Entry<UUID, DiplomacyStatus> entry : clan.getRelationships().entrySet()) {
            if (entry.getValue() == DiplomacyStatus.ALLY) {
                Clan ally = clans.get(entry.getKey());
                if (ally != null) {
                    allies.add(ally);
                }
            }
        }
        return allies;
    }

    public List<Clan> getEnemies(Clan clan) {
        List<Clan> enemies = new ArrayList<>();
        for (Map.Entry<UUID, DiplomacyStatus> entry : clan.getRelationships().entrySet()) {
            if (entry.getValue().isHostile()) {
                Clan enemy = clans.get(entry.getKey());
                if (enemy != null) {
                    enemies.add(enemy);
                }
            }
        }
        return enemies;
    }

    public ActiveWar getActiveWar(UUID clanId) {
        for (ActiveWar war : activeWars.values()) {
            if (war.getAttackerClanId().equals(clanId) || war.getDefenderClanId().equals(clanId)) {
                return war;
            }
        }
        return null;
    }

    public Collection<DiplomaticProposal> getPendingProposals(UUID clanId) {
        Collection<DiplomaticProposal> result = new ArrayList<>();
        for (DiplomaticProposal proposal : pendingProposals.values()) {
            if (proposal.isPending() && proposal.getTargetClanId().equals(clanId)) {
                result.add(proposal);
            }
        }
        return result;
    }

    public void checkWarExpiry() {
        long now = System.currentTimeMillis();
        List<ActiveWar> expiredWars = new ArrayList<>();

        for (ActiveWar war : activeWars.values()) {
            if (war.isExpired(now)) {
                expiredWars.add(war);
            }
        }

        for (ActiveWar war : expiredWars) {
            Clan attacker = clans.get(war.getAttackerClanId());
            Clan defender = clans.get(war.getDefenderClanId());

            if (attacker != null) {
                attacker.setRelationship(defender.getClanId(), DiplomacyStatus.NEUTRAL);
            }
            if (defender != null) {
                defender.setRelationship(attacker.getClanId(), DiplomacyStatus.NEUTRAL);
            }

            if (attacker != null && defender != null) {
                Bukkit.broadcastMessage("§6[Clan] Der Krieg zwischen §c" + attacker.getName() + " §6und §c" + defender.getName() + " §6ist beendet!");
            }

            activeWars.remove(war.getWarId());
        }
    }

    public void cleanupExpiredProposals() {
        long now = System.currentTimeMillis();
        List<UUID> toRemove = new ArrayList<>();

        for (DiplomaticProposal proposal : pendingProposals.values()) {
            if (now - proposal.getCreatedAt() > PROPOSAL_TIMEOUT_MS) {
                toRemove.add(proposal.getProposalId());
            }
        }

        for (UUID id : toRemove) {
            pendingProposals.remove(id);
        }
    }

    private void broadcastAllianceMessage(Clan clanA, Clan clanB, boolean formed) {
        String message = formed ?
                "§6[Clan] Der Clan §a" + clanA.getName() + " §6hat eine Allianz mit §a" + clanB.getName() + " §6geschlossen!" :
                "§6[Clan] Die Allianz zwischen §a" + clanA.getName() + " §6und §a" + clanB.getName() + " §6wurde aufgelöst!";
        Bukkit.broadcastMessage(message);
    }

    private void broadcastWarMessage(Clan attacker, Clan defender) {
        Bukkit.broadcastMessage("§4[Clan-Krieg] §c" + attacker.getName() + " §4hat den Krieg gegen §c" + defender.getName() + " §4erklärt!");
        Bukkit.broadcastMessage("§4[Clan-Krieg] Der Krieg dauert §c" + (WAR_DURATION_MS / (60 * 60 * 1000)) + " Stunden!");
    }

    private void broadcastPeaceMessage(Clan clanA, Clan clanB) {
        Bukkit.broadcastMessage("§6[Clan] §a" + clanA.getName() + " §6und §a" + clanB.getName() + " §6haben einen Friedensvertrag unterzeichnet!");
    }

    public static class DiplomaticProposal {
        private final UUID proposalId;
        private final UUID requestingClanId;
        private final UUID targetClanId;
        private final ProposalType type;
        private final long createdAt;
        private boolean accepted;
        private boolean rejected;

        public DiplomaticProposal(UUID proposalId, UUID requestingClanId, UUID targetClanId, ProposalType type, long createdAt) {
            this.proposalId = proposalId;
            this.requestingClanId = requestingClanId;
            this.targetClanId = targetClanId;
            this.type = type;
            this.createdAt = createdAt;
            this.accepted = false;
            this.rejected = false;
        }

        public UUID getProposalId() {
            return proposalId;
        }

        public UUID getRequestingClanId() {
            return requestingClanId;
        }

        public UUID getTargetClanId() {
            return targetClanId;
        }

        public ProposalType getType() {
            return type;
        }

        public long getCreatedAt() {
            return createdAt;
        }

        public boolean isPending() {
            return !accepted && !rejected;
        }

        public boolean isAccepted() {
            return accepted;
        }

        public boolean isRejected() {
            return rejected;
        }

        public void accept() {
            this.accepted = true;
        }

        public void reject() {
            this.rejected = true;
        }
    }

    public static class ActiveWar {
        private final UUID warId;
        private final UUID attackerClanId;
        private final UUID defenderClanId;
        private final long startTime;
        private final long duration;
        private final Map<UUID, Integer> attackerKills;
        private final Map<UUID, Integer> defenderKills;

        public ActiveWar(UUID warId, UUID attackerClanId, UUID defenderClanId, long startTime, long duration) {
            this.warId = warId;
            this.attackerClanId = attackerClanId;
            this.defenderClanId = defenderClanId;
            this.startTime = startTime;
            this.duration = duration;
            this.attackerKills = new ConcurrentHashMap<>();
            this.defenderKills = new ConcurrentHashMap<>();
        }

        public UUID getWarId() {
            return warId;
        }

        public UUID getAttackerClanId() {
            return attackerClanId;
        }

        public UUID getDefenderClanId() {
            return defenderClanId;
        }

        public long getStartTime() {
            return startTime;
        }

        public long getDuration() {
            return duration;
        }

        public boolean isExpired(long currentTime) {
            return currentTime - startTime > duration;
        }

        public long getRemainingTime() {
            long elapsed = System.currentTimeMillis() - startTime;
            return Math.max(0, duration - elapsed);
        }

        public Map<UUID, Integer> getAttackerKills() {
            return attackerKills;
        }

        public Map<UUID, Integer> getDefenderKills() {
            return defenderKills;
        }

        public void addKill(UUID playerId, boolean isAttacker) {
            if (isAttacker) {
                attackerKills.merge(playerId, 1, Integer::sum);
            } else {
                defenderKills.merge(playerId, 1, Integer::sum);
            }
        }

        public int getTotalAttackerKills() {
            return attackerKills.values().stream().mapToInt(Integer::intValue).sum();
        }

        public int getTotalDefenderKills() {
            return defenderKills.values().stream().mapToInt(Integer::intValue).sum();
        }
    }
}