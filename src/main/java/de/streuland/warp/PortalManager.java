package de.streuland.warp;

import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PortalManager implements Listener {
    public static class PlotPortal {
        private final String portalId;
        private final String plotId;
        private final Location edge;
        private final double cost;
        private final long cooldownMillis;

        public PlotPortal(String portalId, String plotId, Location edge, double cost, long cooldownMillis) {
            this.portalId = portalId;
            this.plotId = plotId;
            this.edge = edge;
            this.cost = cost;
            this.cooldownMillis = cooldownMillis;
        }

        public String getPortalId() { return portalId; }
        public String getPlotId() { return plotId; }
        public Location getEdge() { return edge.clone(); }
        public double getCost() { return cost; }
        public long getCooldownMillis() { return cooldownMillis; }
    }

    private final JavaPlugin plugin;
    private final PlotManager plotManager;
    private final PlotEconomyHook economyHook;
    private final CooldownManager cooldownManager;
    private final Map<String, PlotPortal> portals = new ConcurrentHashMap<>();
    private final Map<String, Integer> plotCounters = new HashMap<>();
    private final File file;
    private final FileConfiguration config;

    public PortalManager(JavaPlugin plugin, PlotManager plotManager, PlotEconomyHook economyHook, CooldownManager cooldownManager) {
        this.plugin = plugin;
        this.plotManager = plotManager;
        this.economyHook = economyHook;
        this.cooldownManager = cooldownManager;

        this.file = new File(plugin.getDataFolder(), "portals.yml");
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                throw new IllegalStateException("Could not create portals.yml", e);
            }
        }
        this.config = YamlConfiguration.loadConfiguration(file);
        load();

        if (Bukkit.getServer() != null) {
            Bukkit.getScheduler().runTaskTimer(plugin, this::spawnParticles, 20L, 20L);
        }
    }

    public String createPortal(String plotId, Location edge) {
        return createPortal(plotId, edge, 0D, 10_000L);
    }

    public String createPortal(String plotId, Location edge, double cost, long cooldownMillis) {
        int next = plotCounters.getOrDefault(plotId, 0) + 1;
        plotCounters.put(plotId, next);
        String portalId = plotId + "-" + next;
        PlotPortal portal = new PlotPortal(portalId, plotId, edge.clone(), cost, cooldownMillis);
        portals.put(portalId.toLowerCase(Locale.ROOT), portal);
        persistPortal(portal);
        spawnMarker(portal);
        return portalId;
    }

    public boolean unregisterPortal(String portalId) {
        PlotPortal removed = portals.remove(portalId.toLowerCase(Locale.ROOT));
        if (removed == null) {
            return false;
        }
        config.set("portals." + removed.getPortalId(), null);
        save();
        return true;
    }

    public PlotPortal getPortal(String portalId) {
        return portals.get(portalId.toLowerCase(Locale.ROOT));
    }

    public Collection<PlotPortal> listPortals() {
        List<PlotPortal> list = new ArrayList<PlotPortal>(portals.values());
        Collections.sort(list, Comparator.comparing(PlotPortal::getPortalId));
        return list;
    }

    public boolean teleport(Player player, String portalId) {
        PlotPortal destination = getPortal(portalId);
        if (destination == null) {
            player.sendMessage("§cPortal nicht gefunden: " + portalId);
            return false;
        }

        UUID playerId = player.getUniqueId();
        if (cooldownManager.isOnCooldown(playerId)) {
            long remaining = cooldownManager.getRemainingMillis(playerId) / 1000L;
            player.sendMessage("§cPortal-Cooldown aktiv: " + remaining + "s");
            return false;
        }

        if (!economyHook.charge(player, destination.getCost())) {
            player.sendMessage("§cNicht genug Geld für dieses Portal (§e" + destination.getCost() + "§c).");
            return false;
        }

        Plot targetPlot = plotManager.getStorage(destination.getEdge().getWorld()).getPlot(destination.getPlotId());
        if (targetPlot != null && targetPlot.getOwner() != null && !targetPlot.getOwner().equals(playerId)) {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(targetPlot.getOwner());
            economyHook.payout(owner, destination.getCost());
        }

        Location destinationLoc = destination.getEdge().clone().add(0.5, 1.0, 0.5);
        player.teleport(destinationLoc);
        cooldownManager.setCooldown(playerId, destination.getCooldownMillis());
        player.sendMessage("§aTeleportiert zu Portal §e" + destination.getPortalId());
        return true;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }
        Location clicked = event.getClickedBlock().getLocation();
        for (PlotPortal portal : portals.values()) {
            Location pLoc = portal.getEdge();
            if (pLoc.getWorld() != null
                    && clicked.getWorld() != null
                    && pLoc.getWorld().getName().equals(clicked.getWorld().getName())
                    && pLoc.getBlockX() == clicked.getBlockX()
                    && pLoc.getBlockY() == clicked.getBlockY()
                    && pLoc.getBlockZ() == clicked.getBlockZ()) {
                event.setCancelled(true);
                teleport(event.getPlayer(), portal.getPortalId());
                return;
            }
        }
    }

    private void load() {
        ConfigurationSection section = config.getConfigurationSection("portals");
        if (section == null) {
            return;
        }
        for (String portalId : section.getKeys(false)) {
            String base = "portals." + portalId;
            String plotId = config.getString(base + ".plotId");
            String worldName = config.getString(base + ".world");
            if (plotId == null || worldName == null || Bukkit.getWorld(worldName) == null) {
                continue;
            }
            Location edge = new Location(Bukkit.getWorld(worldName),
                    config.getDouble(base + ".x"),
                    config.getDouble(base + ".y"),
                    config.getDouble(base + ".z"));
            double cost = config.getDouble(base + ".cost", 0D);
            long cooldownMillis = config.getLong(base + ".cooldownMillis", 10_000L);
            PlotPortal portal = new PlotPortal(portalId, plotId, edge, cost, cooldownMillis);
            portals.put(portalId.toLowerCase(Locale.ROOT), portal);
            updateCounter(plotId, portalId);
        }
    }

    private void updateCounter(String plotId, String portalId) {
        int index = portalId.lastIndexOf('-');
        if (index < 0) {
            return;
        }
        try {
            int current = Integer.parseInt(portalId.substring(index + 1));
            plotCounters.put(plotId, Math.max(plotCounters.getOrDefault(plotId, 0), current));
        } catch (NumberFormatException ignored) {
        }
    }

    private void persistPortal(PlotPortal portal) {
        String base = "portals." + portal.getPortalId();
        config.set(base + ".plotId", portal.getPlotId());
        config.set(base + ".world", portal.getEdge().getWorld().getName());
        config.set(base + ".x", portal.getEdge().getX());
        config.set(base + ".y", portal.getEdge().getY());
        config.set(base + ".z", portal.getEdge().getZ());
        config.set(base + ".cost", portal.getCost());
        config.set(base + ".cooldownMillis", portal.getCooldownMillis());
        save();
    }

    private void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save portals.yml: " + e.getMessage());
        }
    }

    private void spawnParticles() {
        for (PlotPortal portal : portals.values()) {
            Location location = portal.getEdge().clone().add(0.5, 1.1, 0.5);
            if (location.getWorld() != null) {
                location.getWorld().spawnParticle(Particle.PORTAL, location, 20, 0.4, 0.2, 0.4, 0.01);
            }
        }
    }

    private void spawnMarker(PlotPortal portal) {
        Location markerLoc = portal.getEdge().clone().add(0.5, 1.3, 0.5);
        if (markerLoc.getWorld() == null) {
            return;
        }
        ArmorStand stand = (ArmorStand) markerLoc.getWorld().spawnEntity(markerLoc, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setCustomNameVisible(true);
        stand.setCustomName("§dPortal " + portal.getPortalId());
        stand.setMarker(true);
        stand.setSmall(true);
    }
}
