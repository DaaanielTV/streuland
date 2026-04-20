package de.streuland.clan;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Clan {
    private final UUID clanId;
    private String name;
    private UUID leader;
    private final Set<UUID> members;
    private final Set<String> plotIds;

    public Clan(UUID clanId, String name, UUID leader) {
        this.clanId = clanId;
        this.name = name;
        this.leader = leader;
        this.members = new HashSet<>();
        this.members.add(leader);
        this.plotIds = new HashSet<>();
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
}
