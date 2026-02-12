package de.streuland.district;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.streuland.analytics.PlotAnalyticsRecord;
import de.streuland.analytics.PlotAnalyticsService;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages biome-specific district traders (spawn, dialogue, stock, pricing).
 */
public class TraderNpcService implements Listener {
    private static final double NEAREST_SEARCH_RADIUS = 256.0D;

    private final JavaPlugin plugin;
    private final PlotManager plotManager;
    private final DistrictManager districtManager;
    private final PlotAnalyticsService analyticsService;
    private final Economy economy;
    private final Gson gson;
    private final java.io.File storageFile;
    private final Map<String, TraderNpc> tradersByDistrict = new HashMap<>();

    public TraderNpcService(JavaPlugin plugin,
                            PlotManager plotManager,
                            DistrictManager districtManager,
                            PlotAnalyticsService analyticsService,
                            Economy economy) {
        this.plugin = plugin;
        this.plotManager = plotManager;
        this.districtManager = districtManager;
        this.analyticsService = analyticsService;
        this.economy = economy;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        java.io.File districtFolder = new java.io.File(plugin.getDataFolder(), "districts");
        if (!districtFolder.exists()) {
            districtFolder.mkdirs();
        }
        this.storageFile = new java.io.File(districtFolder, "traders.json");
        load();
    }

    public void start() {
        reconcileTraders();
        Bukkit.getScheduler().runTaskTimer(plugin, this::reconcileTraders, 20L * 120L, 20L * 120L);
    }

    public boolean handleTraderCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cVerwendung: /plot trader <nearest|buy|stock>");
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "nearest":
                return handleNearest(player);
            case "buy":
                return handleBuy(player, args);
            case "stock":
                return handleStock(player, args);
            default:
                player.sendMessage("§cVerwendung: /plot trader <nearest|buy|stock>");
                return true;
        }
    }

    @EventHandler
    public void onNpcInteract(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof Villager)) {
            return;
        }
        TraderNpc trader = findByEntity(clicked.getUniqueId());
        if (trader == null) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        player.sendMessage("§6[Händler] §fWillkommen! Ich handle mit " + trader.biome.name() + "-Waren.");
        player.sendMessage("§7Nutze §e/plot trader buy <item> [anzahl]§7 zum Kaufen.");
        player.sendMessage("§7Nutze §e/plot trader nearest§7 für mein aktuelles Angebot.");
    }

    private boolean handleNearest(Player player) {
        TraderNpc trader = findNearestTrader(player.getLocation());
        if (trader == null) {
            player.sendMessage("§cKein Händler in der Nähe gefunden.");
            return true;
        }

        District district = districtManager.getDistrictById(trader.districtId);
        int districtLevel = district == null ? 2 : district.getLevel().ordinal() + 1;
        double distance = player.getLocation().distance(new Location(Bukkit.getWorld(trader.world), trader.x, trader.y, trader.z));

        player.sendMessage("§6=== Nächster Händler ===");
        player.sendMessage("§eDistrikt: §f" + trader.districtId + " §7| §eBiome: §f" + trader.biome.name());
        player.sendMessage("§eDistanz: §f" + String.format(Locale.US, "%.1f", distance) + " Blöcke");
        player.sendMessage("§eSchematic-Variante: §f" + trader.schematicVariant);
        player.sendMessage("§eInventar:");
        for (TraderItem item : trader.inventory.values()) {
            double dynamicPrice = round(item.basePrice * (1.0D + (districtLevel - 2) * 0.1D));
            player.sendMessage(" §7- §f" + item.material + " §7x" + item.stock + " §8| §a" + dynamicPrice);
        }
        return true;
    }

    private boolean handleBuy(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cVerwendung: /plot trader buy <item> [anzahl]");
            return true;
        }
        TraderNpc trader = findNearestTrader(player.getLocation());
        if (trader == null) {
            player.sendMessage("§cKein Händler in Reichweite.");
            return true;
        }
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Integer.parseInt(args[3]));
            } catch (NumberFormatException e) {
                player.sendMessage("§cAnzahl muss numerisch sein.");
                return true;
            }
        }

        String key = args[2].toUpperCase(Locale.ROOT);
        TraderItem item = trader.inventory.get(key);
        if (item == null) {
            player.sendMessage("§cDieser Händler führt den Artikel nicht.");
            return true;
        }
        if (item.stock < amount) {
            player.sendMessage("§cNicht genug Lagerbestand. Verfügbar: " + item.stock);
            return true;
        }

        District district = districtManager.getDistrictById(trader.districtId);
        int districtLevel = district == null ? 2 : district.getLevel().ordinal() + 1;
        double unitPrice = round(item.basePrice * (1.0D + (districtLevel - 2) * 0.1D));
        double total = round(unitPrice * amount);

        if (economy == null) {
            player.sendMessage("§cKein Economy-Provider aktiv. Händlerkauf deaktiviert.");
            return true;
        }

        EconomyResponse withdraw = economy.withdrawPlayer(player, total);
        if (!withdraw.transactionSuccess()) {
            player.sendMessage("§cZu wenig Guthaben. Benötigt: " + total);
            return true;
        }

        Material material = Material.matchMaterial(item.material);
        if (material == null) {
            economy.depositPlayer(player, total);
            player.sendMessage("§cArtikel ist technisch ungültig. Kauf abgebrochen.");
            return true;
        }

        Map<Integer, ItemStack> overflow = player.getInventory().addItem(new ItemStack(material, amount));
        if (!overflow.isEmpty()) {
            economy.depositPlayer(player, total);
            player.sendMessage("§cDein Inventar ist voll.");
            return true;
        }

        item.stock -= amount;
        save();

        analyticsService.record(new PlotAnalyticsRecord(resolveAnyPlotIdForDistrict(trader.districtId), player.getUniqueId(),
                "TRADER_PURCHASE", Instant.now(), total));
        player.sendMessage("§aGekauft: " + item.material + " x" + amount + " für " + total);
        return true;
    }

    private boolean handleStock(Player player, String[] args) {
        if (args.length < 5) {
            player.sendMessage("§cVerwendung: /plot trader stock <item> <anzahl> <base_price>");
            return true;
        }

        TraderNpc trader = findNearestTrader(player.getLocation());
        if (trader == null) {
            player.sendMessage("§cKein Händler in Reichweite.");
            return true;
        }

        if (!ownsAnyPlotInDistrict(player.getUniqueId(), trader.districtId)) {
            player.sendMessage("§cNur Plot-Besitzer dieses Distrikts dürfen Händlerbestand ändern.");
            return true;
        }

        String materialKey = args[2].toUpperCase(Locale.ROOT);
        Material material = Material.matchMaterial(materialKey);
        if (material == null) {
            player.sendMessage("§cUnbekanntes Material.");
            return true;
        }

        int amount;
        double basePrice;
        try {
            amount = Integer.parseInt(args[3]);
            basePrice = Double.parseDouble(args[4]);
        } catch (NumberFormatException e) {
            player.sendMessage("§cAnzahl/Preis ungültig.");
            return true;
        }

        if (amount <= 0 || basePrice <= 0D) {
            player.sendMessage("§cAnzahl und Preis müssen größer als 0 sein.");
            return true;
        }

        TraderItem existing = trader.inventory.get(materialKey);
        if (existing == null) {
            trader.inventory.put(materialKey, new TraderItem(materialKey, amount, basePrice));
        } else {
            existing.stock += amount;
            existing.basePrice = basePrice;
        }
        save();

        player.sendMessage("§aHändlerbestand aktualisiert: " + materialKey + " x" + amount + " (Basispreis " + basePrice + ")");
        return true;
    }

    private boolean ownsAnyPlotInDistrict(UUID playerId, String districtId) {
        District district = districtManager.getDistrictById(districtId);
        if (district == null) {
            return false;
        }
        for (String plotId : district.getPlotIds()) {
            Plot plot = plotManager.getStorage(plotManager.getWorldForPlot(plotId)).getPlot(plotId);
            if (plot != null && playerId.equals(plot.getOwner())) {
                return true;
            }
        }
        return false;
    }

    private TraderNpc findNearestTrader(Location location) {
        String worldName = location.getWorld().getName();
        Optional<TraderNpc> nearest = tradersByDistrict.values().stream()
                .filter(trader -> worldName.equalsIgnoreCase(trader.world))
                .min(Comparator.comparingDouble(trader -> distanceSquared(location, trader)));
        if (!nearest.isPresent()) {
            return null;
        }
        TraderNpc trader = nearest.get();
        if (Math.sqrt(distanceSquared(location, trader)) > NEAREST_SEARCH_RADIUS) {
            return null;
        }
        return trader;
    }

    private double distanceSquared(Location location, TraderNpc trader) {
        double dx = location.getX() - trader.x;
        double dy = location.getY() - trader.y;
        double dz = location.getZ() - trader.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private TraderNpc findByEntity(UUID entityId) {
        for (TraderNpc trader : tradersByDistrict.values()) {
            if (entityId.equals(trader.entityId)) {
                return trader;
            }
        }
        return null;
    }

    public void reconcileTraders() {
        for (District district : districtManager.getAllDistricts()) {
            int districtLevel = district.getLevel().ordinal() + 1;
            if (districtLevel < 2) {
                continue;
            }

            TraderBiome biome = resolveDistrictBiome(district);
            TraderNpc trader = tradersByDistrict.computeIfAbsent(district.getId(), id -> createDefaultTrader(district, biome));
            trader.biome = biome;
            if (trader.inventory.isEmpty()) {
                trader.inventory.putAll(defaultInventoryFor(biome));
            }
            if (trader.schematicVariant == null || trader.schematicVariant.isEmpty()) {
                trader.schematicVariant = selectVariant(district.getId(), biome);
            }

            Location spawn = resolveSpawnLocation(district);
            if (spawn == null) {
                continue;
            }
            trader.world = spawn.getWorld().getName();
            trader.x = spawn.getX();
            trader.y = spawn.getY();
            trader.z = spawn.getZ();
            spawnOrUpdateEntity(trader, spawn);
        }
        save();
    }

    private TraderNpc createDefaultTrader(District district, TraderBiome biome) {
        TraderNpc trader = new TraderNpc();
        trader.districtId = district.getId();
        trader.biome = biome;
        trader.inventory = defaultInventoryFor(biome);
        trader.schematicVariant = selectVariant(district.getId(), biome);
        return trader;
    }

    private void spawnOrUpdateEntity(TraderNpc trader, Location location) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }

        Entity existing = trader.entityId == null ? null : world.getEntity(trader.entityId);
        if (existing instanceof Villager) {
            Villager villager = (Villager) existing;
            villager.teleport(location);
            applyVisuals(villager, trader);
            return;
        }

        Villager villager = (Villager) world.spawnEntity(location, EntityType.VILLAGER);
        trader.entityId = villager.getUniqueId();
        applyVisuals(villager, trader);
    }

    private void applyVisuals(Villager villager, TraderNpc trader) {
        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setCustomNameVisible(true);
        villager.setCustomName("§6" + trader.biome.name() + " Händler §7(" + trader.schematicVariant + ")");

        switch (trader.biome) {
            case DESERT:
                villager.setVillagerType(Villager.Type.DESERT);
                villager.setProfession(Villager.Profession.CARTOGRAPHER);
                break;
            case JUNGLE:
                villager.setVillagerType(Villager.Type.JUNGLE);
                villager.setProfession(Villager.Profession.FLETCHER);
                break;
            case SNOW:
            default:
                villager.setVillagerType(Villager.Type.SNOW);
                villager.setProfession(Villager.Profession.LIBRARIAN);
                break;
        }
    }

    private TraderBiome resolveDistrictBiome(District district) {
        Map<TraderBiome, Integer> counts = new EnumMap<>(TraderBiome.class);
        counts.put(TraderBiome.DESERT, 0);
        counts.put(TraderBiome.JUNGLE, 0);
        counts.put(TraderBiome.SNOW, 0);

        for (String plotId : district.getPlotIds()) {
            World world = plotManager.getWorldForPlot(plotId);
            Plot plot = plotManager.getStorage(world).getPlot(plotId);
            if (plot == null) {
                continue;
            }
            Biome biome = world.getBlockAt(plot.getCenterX(), plot.getSpawnY(), plot.getCenterZ()).getBiome();
            TraderBiome resolved = TraderBiome.fromBiome(biome);
            counts.put(resolved, counts.get(resolved) + 1);
        }

        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(TraderBiome.DESERT);
    }

    private Location resolveSpawnLocation(District district) {
        List<Plot> plots = new ArrayList<>();
        for (String plotId : district.getPlotIds()) {
            World world = plotManager.getWorldForPlot(plotId);
            Plot plot = plotManager.getStorage(world).getPlot(plotId);
            if (plot != null) {
                plots.add(plot);
            }
        }
        if (plots.isEmpty()) {
            return null;
        }

        Plot pivot = plots.get(0);
        World world = plotManager.getWorldForPlot(pivot.getPlotId());
        double x = plots.stream().mapToDouble(Plot::getCenterX).average().orElse(pivot.getCenterX());
        double z = plots.stream().mapToDouble(Plot::getCenterZ).average().orElse(pivot.getCenterZ());
        int y = world.getHighestBlockYAt((int) Math.round(x), (int) Math.round(z)) + 1;
        return new Location(world, x + 0.5D, y, z + 0.5D);
    }

    private String resolveAnyPlotIdForDistrict(String districtId) {
        District district = districtManager.getDistrictById(districtId);
        if (district == null || district.getPlotIds().isEmpty()) {
            return "unknown";
        }
        return district.getPlotIds().iterator().next();
    }

    private Map<String, TraderItem> defaultInventoryFor(TraderBiome biome) {
        Map<String, TraderItem> inventory = new HashMap<>();
        switch (biome) {
            case DESERT:
                inventory.put("SAND", new TraderItem("SAND", 64, 4.0D));
                inventory.put("SANDSTONE", new TraderItem("SANDSTONE", 32, 7.0D));
                inventory.put("DEAD_BUSH", new TraderItem("DEAD_BUSH", 16, 10.0D));
                break;
            case JUNGLE:
                inventory.put("JUNGLE_LOG", new TraderItem("JUNGLE_LOG", 48, 6.0D));
                inventory.put("COCOA_BEANS", new TraderItem("COCOA_BEANS", 24, 9.0D));
                inventory.put("VINE", new TraderItem("VINE", 32, 8.0D));
                break;
            case SNOW:
            default:
                inventory.put("PACKED_ICE", new TraderItem("PACKED_ICE", 32, 12.0D));
                inventory.put("BLUE_ICE", new TraderItem("BLUE_ICE", 24, 18.0D));
                inventory.put("SNOW_BLOCK", new TraderItem("SNOW_BLOCK", 64, 5.0D));
                break;
        }
        return inventory;
    }

    private String selectVariant(String districtId, TraderBiome biome) {
        String[] variants;
        switch (biome) {
            case DESERT:
                variants = new String[]{"desert_tent", "desert_arch", "desert_stalls"};
                break;
            case JUNGLE:
                variants = new String[]{"jungle_hut", "jungle_totem", "jungle_canopy"};
                break;
            case SNOW:
            default:
                variants = new String[]{"snow_igloo", "snow_kiosk", "snow_pavilion"};
                break;
        }
        int idx = Math.abs(districtId.hashCode()) % variants.length;
        return variants[idx];
    }

    private double round(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    private void load() {
        tradersByDistrict.clear();
        if (!storageFile.exists()) {
            return;
        }
        try {
            String json = Files.readString(storageFile.toPath(), StandardCharsets.UTF_8);
            Type type = new TypeToken<Map<String, TraderNpc>>() { }.getType();
            Map<String, TraderNpc> loaded = gson.fromJson(json, type);
            if (loaded != null) {
                tradersByDistrict.putAll(loaded);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load traders.json: " + e.getMessage());
        }
    }

    private void save() {
        try {
            Files.writeString(storageFile.toPath(), gson.toJson(tradersByDistrict), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save traders.json: " + e.getMessage());
        }
    }

    public enum TraderBiome {
        DESERT,
        JUNGLE,
        SNOW;

        static TraderBiome fromBiome(Biome biome) {
            String key = biome.name();
            if (key.contains("DESERT") || key.contains("BADLANDS")) {
                return DESERT;
            }
            if (key.contains("JUNGLE") || key.contains("BAMBOO")) {
                return JUNGLE;
            }
            if (key.contains("SNOW") || key.contains("ICE") || key.contains("FROZEN")) {
                return SNOW;
            }
            return DESERT;
        }
    }

    public static class TraderNpc {
        public String districtId;
        public String world;
        public double x;
        public double y;
        public double z;
        public UUID entityId;
        public TraderBiome biome;
        public String schematicVariant;
        public Map<String, TraderItem> inventory = new HashMap<>();
    }

    public static class TraderItem {
        public String material;
        public int stock;
        public double basePrice;

        public TraderItem() {
        }

        public TraderItem(String material, int stock, double basePrice) {
            this.material = material;
            this.stock = stock;
            this.basePrice = basePrice;
        }
    }
}
