package de.streuland.clan;

import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class ClanStorage {
    private final JavaPlugin plugin;
    private final Path clansFile;
    private Map<UUID, ClanData> clans;

    public ClanStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.clansFile = plugin.getDataFolder().toPath().resolve("clans.yml");
        this.clans = new HashMap<>();
        load();
    }

    public void save(Clan clan) {
        if (clan == null) return;

        ClanData data = new ClanData();
        data.clanId = clan.getClanId().toString();
        data.name = clan.getName();
        data.leader = clan.getLeader().toString();
        data.members = clan.getMembers().stream()
                .map(UUID::toString)
                .collect(Collectors.toList());
        data.plotIds = new ArrayList<>(clan.getPlotIds());
        data.motto = clan.getMotto();
        data.color = clan.getColor() != null ? clan.getColor().name() : "AQUA";
        data.kills = clan.getKills();
        data.createdAt = clan.getCreatedAt();

        Map<String, String> relationships = new HashMap<>();
        for (Map.Entry<UUID, DiplomacyStatus> entry : clan.getRelationships().entrySet()) {
            relationships.put(entry.getKey().toString(), entry.getValue().getId());
        }
        data.relationships = relationships;

        clans.put(clan.getClanId(), data);
    }

    public void delete(UUID clanId) {
        clans.remove(clanId);
    }

    public Collection<Clan> loadAll() {
        List<Clan> loadedClans = new ArrayList<>();

        for (ClanData data : clans.values()) {
            try {
                Clan clan = deserialize(data);
                if (clan != null) {
                    loadedClans.add(clan);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Fehler beim Laden des Clans: " + data.name);
            }
        }

        return loadedClans;
    }

    public void saveToDisk() {
        try {
            Files.createDirectories(clansFile.getParent());

            Map<String, Object> root = new LinkedHashMap<>();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> clanList = new ArrayList<>();

            for (ClanData data : clans.values()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", data.clanId);
                map.put("name", data.name);
                map.put("leader", data.leader);
                map.put("members", data.members);
                map.put("plotIds", data.plotIds);
                map.put("motto", data.motto);
                map.put("color", data.color);
                map.put("kills", data.kills);
                map.put("createdAt", data.createdAt);
                map.put("relationships", data.relationships);
                clanList.add(map);
            }

            root.put("clans", clanList);

            Yaml yaml = new Yaml();
            try (Writer writer = Files.newBufferedWriter(clansFile)) {
                yaml.dump(root, writer);
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Fehler beim Speichern der Clans: " + e.getMessage());
        }
    }

    private void load() {
        try {
            if (!Files.exists(clansFile)) {
                return;
            }

            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(Files.newBufferedReader(clansFile));

            if (root == null) return;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> clanList = (List<Map<String, Object>>) root.get("clans");
            if (clanList == null) return;

            for (Map<String, Object> map : clanList) {
                ClanData data = new ClanData();
                data.clanId = (String) map.get("id");
                data.name = (String) map.get("name");
data.leader = (String) map.get("leader");

                @SuppressWarnings("unchecked")
                List<String> members = (List<String>) map.get("members");
            data.members = members != null ? members : new ArrayList<>();

            @SuppressWarnings("unchecked")
            List<String> plotIds = (List<String>) map.get("plotIds");
            data.plotIds = plotIds != null ? plotIds : new ArrayList<>();

            data.motto = (String) map.getOrDefault("motto", "");
            data.color = (String) map.getOrDefault("color", "AQUA");
            data.kills = map.get("kills") != null ? ((Number) map.get("kills")).intValue() : 0;
            data.createdAt = map.get("createdAt") != null ? ((Number) map.get("createdAt")).longValue() : System.currentTimeMillis();

            @SuppressWarnings("unchecked")
            Map<String, String> relationships = (Map<String, String>) map.get("relationships");
            data.relationships = relationships != null ? relationships : new HashMap<>();

                clans.put(UUID.fromString(data.clanId), data);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Fehler beim Laden der Clans: " + e.getMessage());
        }
    }

    private Clan deserialize(ClanData data) {
        try {
            UUID clanId = UUID.fromString(data.clanId);
            UUID leader = UUID.fromString(data.leader);
            ChatColor color = ChatColor.valueOf(data.color != null ? data.color : "AQUA");

            Clan clan = new Clan(clanId, data.name, leader, color, data.motto != null ? data.motto : "");
            clan.setKills(data.kills);

            for (String memberStr : data.members) {
                clan.addMember(UUID.fromString(memberStr));
            }

            for (String plotId : data.plotIds) {
                clan.addPlot(plotId);
            }

            if (data.relationships != null) {
                for (Map.Entry<String, String> entry : data.relationships.entrySet()) {
                    UUID otherClanId = UUID.fromString(entry.getKey());
                    DiplomacyStatus status = DiplomacyStatus.fromId(entry.getValue());
                    clan.setRelationship(otherClanId, status);
                }
            }

            return clan;
        } catch (Exception e) {
            return null;
        }
    }

    private static class ClanData {
        String clanId;
        String name;
        String leader;
        List<String> members = new ArrayList<>();
        List<String> plotIds = new ArrayList<>();
        String motto = "";
        String color = "AQUA";
        int kills = 0;
        long createdAt;
        Map<String, String> relationships = new HashMap<>();
    }
}