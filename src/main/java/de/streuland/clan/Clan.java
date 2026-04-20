package de.streuland.clan;

import org.bukkit.ChatColor;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Clan {
    private final UUID clanId;
    private String name;
    private String motto;
    private ChatColor color;
    private UUID leader;
    private final Set<UUID> members;
    private final Set<String> plotIds;
    private final Map<UUID, DiplomacyStatus> relationships;
    private final long createdAt;
    private int kills;
    private long warDeclarationTime;

    public Clan(UUID clanId, String name, UUID leader) {
        this(clanId, name, leader, ChatColor.AQUA, "");
    }

    public Clan(UUID clanId, String name, UUID leader, ChatColor color, String motto) {
        this.clanId = clanId;
        this.name = name;
        this.leader = leader;
        this.color = color;
        this.motto = motto;
        this.members = new HashSet<>();
        this.members.add(leader);
        this.plotIds = new HashSet<>();
        this.relationships = new ConcurrentHashMap<>();
        this.createdAt = System.currentTimeMillis();
        this.kills = 0;
        this.warDeclarationTime = 0;
    }

    public UUID getClanId() {
        return clanId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMotto() {
        return motto;
    }

    public void setMotto(String motto) {
        this.motto = motto;
    }

    public ChatColor getColor() {
        return color;
    }

    public void setColor(ChatColor color) {
        this.color = color;
    }

    public UUID getLeader() {
        return leader;
    }

    public void setLeader(UUID leader) {
        this.leader = leader;
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public void addMember(UUID member) {
        members.add(member);
    }

    public void removeMember(UUID member) {
        members.remove(member);
    }

    public Set<String> getPlotIds() {
        return plotIds;
    }

    public void addPlot(String plotId) {
        plotIds.add(plotId);
    }

    public void removePlot(String plotId) {
        plotIds.remove(plotId);
    }

    public Map<UUID, DiplomacyStatus> getRelationships() {
        return relationships;
    }

    public DiplomacyStatus getRelationship(UUID otherClanId) {
        return relationships.getOrDefault(otherClanId, DiplomacyStatus.NEUTRAL);
    }

    public void setRelationship(UUID otherClanId, DiplomacyStatus status) {
        if (status == null) {
            relationships.remove(otherClanId);
        } else {
            relationships.put(otherClanId, status);
        }
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public int getKills() {
        return kills;
    }

    public void addKill() {
        this.kills++;
    }

    public void setKills(int kills) {
        this.kills = kills;
    }

    public long getWarDeclarationTime() {
        return warDeclarationTime;
    }

    public void setWarDeclarationTime(long time) {
        this.warDeclarationTime = time;
    }

    public int getMemberCount() {
        return members.size();
    }

    public int getPlotCount() {
        return plotIds.size();
    }

    public String getDisplayName() {
        return color + name;
    }

    public long getAge() {
        return System.currentTimeMillis() - createdAt;
    }
}