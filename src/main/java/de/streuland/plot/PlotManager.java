package de.streuland.plot;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class PlotManager {
    private static class WorldContext {
        private final World world;
        private final PlotStorage storage;
        private final SpatialGrid spatialGrid;
        private final WorldConfig.WorldSettings settings;
        private int plotCounter;

        private WorldContext(JavaPlugin plugin, World world, PlotStorage storage, WorldConfig.WorldSettings settings) {
            this.world = world;
            this.storage = storage;
            this.spatialGrid = new SpatialGrid(plugin);
            this.settings = settings;
            this.plotCounter = storage.getNextPlotNumber();
            this.spatialGrid.rebuild(storage.getAllPlots());
        }
    }

    private final JavaPlugin plugin;
    private final WorldConfig worldConfig;
    private final Map<String, WorldContext> contexts = new HashMap<>();
    private final String primaryWorldName;

    public PlotManager(JavaPlugin plugin) {
        this.plugin = plugin;
        Plot.setRolePermissions(loadRolePermissions(plugin.getConfig()));
        this.worldConfig = new WorldConfig(plugin);
        PlotStoragePartitioner partitioner = new PlotStoragePartitioner(plugin);

        for (WorldConfig.WorldSettings settings : worldConfig.getAllWorlds()) {
            World world = Bukkit.getWorld(settings.getWorldName());
            if (world == null) {
                plugin.getLogger().warning("Configured plot world not loaded: " + settings.getWorldName());
                continue;
            }
            PlotStorage storage = new PlotStorage(plugin, settings.getWorldName(), partitioner);
            contexts.put(settings.getWorldName(), new WorldContext(plugin, world, storage, settings));
        }

        FileConfiguration config = plugin.getConfig();
        this.primaryWorldName = config.getString("world.name", "world_main");
        if (!contexts.containsKey(primaryWorldName) && !contexts.isEmpty()) {
            plugin.getLogger().warning("Primary world " + primaryWorldName + " unavailable, using fallback world");
        }
    }

    private WorldContext contextFor(World world) {
        if (world != null && contexts.containsKey(world.getName())) {
            return contexts.get(world.getName());
        }
        if (contexts.containsKey(primaryWorldName)) {
            return contexts.get(primaryWorldName);
        }
        return contexts.values().stream().findFirst().orElseThrow(() -> new IllegalStateException("No plot world available"));
    }

    public CompletableFuture<Plot> createPlotAsync(UUID playerUUID, World world) {
        WorldContext ctx = contextFor(world);
        return CompletableFuture.supplyAsync(() -> {
            for (Plot plot : ctx.storage.getAllPlots()) {
                if (plot.getAreaType() == AreaType.PLOT_UNCLAIMED) {
                    return claimPlotForPlayer(ctx, plot, playerUUID);
                }
            }

            for (int attempts = 0; attempts < 10; attempts++) {
                int x = random(-ctx.settings.getMaxSearchRadius(), ctx.settings.getMaxSearchRadius());
                int z = random(-ctx.settings.getMaxSearchRadius(), ctx.settings.getMaxSearchRadius());
                if (isValidPlotLocation(ctx, x, z)) {
                    return createPlotAtLocation(ctx, playerUUID, x, z);
                }
            }
            return null;
        });
    }

    public CompletableFuture<Plot> createPlotAsync(UUID playerUUID) {
        return createPlotAsync(playerUUID, getWorld());
    }

    private Plot createPlotAtLocation(WorldContext ctx, UUID playerUUID, int centerX, int centerZ) {
        String plotId = ctx.world.getName() + "_plot_" + ctx.plotCounter++;
        int spawnY = findSafeSpawnY(ctx, centerX, centerZ);
        Plot plot = new Plot(plotId, centerX, centerZ, ctx.settings.getPlotSize(), playerUUID, System.currentTimeMillis(), spawnY, Plot.PlotState.CLAIMED);
        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            ctx.storage.savePlot(plot);
            ctx.spatialGrid.addPlot(plot);
            Bukkit.getPluginManager().callEvent(new de.streuland.event.PlotCreatedEvent(plot));
        });
        return plot;
    }

    private int findSafeSpawnY(WorldContext ctx, int centerX, int centerZ) {
        Block highestBlock = ctx.world.getHighestBlockAt(centerX, centerZ);
        int y = highestBlock.getY() + 1;
        Block below = ctx.world.getBlockAt(centerX, y - 1, centerZ);
        if (below.isLiquid()) {
            for (int i = y; i < 256; i++) {
                Block current = ctx.world.getBlockAt(centerX, i, centerZ);
                Block belowCurrent = ctx.world.getBlockAt(centerX, i - 1, centerZ);
                if (!current.isLiquid() && !belowCurrent.isLiquid() && !belowCurrent.getType().name().contains("AIR")) {
                    return i;
                }
            }
        }
        return y;
    }

    private boolean isValidPlotLocation(WorldContext ctx, int x, int z) {
        if (!ctx.spatialGrid.isLocationAvailable(x, z, ctx.settings.getPlotSize())) {
            return false;
        }
        for (Plot plot : ctx.storage.getAllPlots()) {
            double distance = Math.sqrt(Math.pow(x - plot.getCenterX(), 2) + Math.pow(z - plot.getCenterZ(), 2));
            if (distance < ctx.settings.getMinDistance()) {
                return false;
            }
        }
        return isValidTerrain(ctx, x, z);
    }

    private boolean isValidTerrain(WorldContext ctx, int x, int z) {
        FileConfiguration config = plugin.getConfig();
        boolean rejectWater = config.getBoolean("terrain.water-rejection", true);
        boolean rejectLava = config.getBoolean("terrain.lava-rejection", true);
        int waterThreshold = config.getInt("terrain.adjacent-water-threshold", 3);

        Block block = ctx.world.getHighestBlockAt(x, z);
        int y = block.getY();

        if (block.isLiquid()) {
            return !rejectWater;
        }
        if (rejectLava) {
            Block belowBlock = ctx.world.getBlockAt(x, y - 1, z);
            if (belowBlock.getType().toString().contains("LAVA")) {
                return false;
            }
        }
        if (rejectWater && waterThreshold > 0) {
            int waterCount = 0;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    Block adjacent = ctx.world.getBlockAt(x + dx, y, z + dz);
                    if (adjacent.isLiquid()) {
                        waterCount++;
                    }
                }
            }
            if (waterCount >= waterThreshold) {
                return false;
            }
        }
        return true;
    }

    public Plot getPlotAt(World world, int x, int z) {
        return contextFor(world).spatialGrid.getPlotAt(x, z);
    }

    public Plot getPlotAt(int x, int z) {
        return getPlotAt(getWorld(), x, z);
    }

    public Plot getNearestPlot(World world, int x, int z) {
        double minDist = Double.MAX_VALUE;
        Plot nearest = null;
        for (Plot plot : contextFor(world).storage.getAllPlots()) {
            double dx = x - plot.getCenterX();
            double dz = z - plot.getCenterZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < minDist) {
                minDist = dist;
                nearest = plot;
            }
        }
        return nearest;
    }

    public Plot getNearestPlot(int x, int z) {
        return getNearestPlot(getWorld(), x, z);
    }

    private Plot claimPlotForPlayer(WorldContext ctx, Plot plot, UUID player) {
        Plot claimedPlot = ctx.storage.claimPlot(plot.getPlotId(), player);
        if (claimedPlot != null && claimedPlot != plot) {
            ctx.spatialGrid.removePlot(plot);
            ctx.spatialGrid.addPlot(claimedPlot);
        }
        return claimedPlot;
    }

    public Plot claimPlotForPlayer(Plot plot, UUID player) {
        return claimPlotForPlayer(contextFor(getWorldForPlot(plot.getPlotId())), plot, player);
    }

    public boolean createRole(String plotId, UUID actor, String roleId, Set<Permission> permissions) {
        PlotStorage storage = storageForPlot(plotId);
        Plot plot = storage.getPlot(plotId);
        if (plot == null || !canManageRoles(plot, actor)) {
            return false;
        }
        boolean created = plot.createRole(roleId, permissions);
        if (created) {
            storage.savePlot(plot);
        }
        return created;
    }

    public boolean updateRole(String plotId, UUID actor, String roleId, Set<Permission> permissions) {
        PlotStorage storage = storageForPlot(plotId);
        Plot plot = storage.getPlot(plotId);
        if (plot == null || !canManageRoles(plot, actor)) {
            return false;
        }
        boolean updated = plot.updateRole(roleId, permissions);
        if (updated) {
            storage.savePlot(plot);
        }
        return updated;
    }

    public boolean removeRoleDefinition(String plotId, UUID actor, String roleId) {
        PlotStorage storage = storageForPlot(plotId);
        Plot plot = storage.getPlot(plotId);
        if (plot == null || !canManageRoles(plot, actor)) {
            return false;
        }
        boolean removed = plot.removeRoleDefinition(roleId);
        if (removed) {
            storage.savePlot(plot);
        }
        return removed;
    }

    public boolean assignRole(String plotId, UUID actor, UUID target, Role role) {
        return role != null && assignRole(plotId, actor, target, role.getId());
    }

    public boolean assignRole(String plotId, UUID actor, UUID target, String roleId) {
        PlotStorage storage = storageForPlot(plotId);
        Plot plot = storage.getPlot(plotId);
        if (plot == null || !canManageRoles(plot, actor) || target == null || target.equals(plot.getOwner())) {
            return false;
        }
        plot.assignRole(target, roleId);
        storage.savePlot(plot);
        return true;
    }

    public boolean removeRole(String plotId, UUID actor, UUID target) {
        PlotStorage storage = storageForPlot(plotId);
        Plot plot = storage.getPlot(plotId);
        if (plot == null || !canManageRoles(plot, actor) || target == null || target.equals(plot.getOwner())) {
            return false;
        }
        plot.removeRole(target);
        storage.savePlot(plot);
        return true;
    }

    public boolean unassignRole(String plotId, UUID actor, UUID target, String roleId) {
        PlotStorage storage = storageForPlot(plotId);
        Plot plot = storage.getPlot(plotId);
        if (plot == null || !canManageRoles(plot, actor) || target == null || target.equals(plot.getOwner())) {
            return false;
        }
        plot.removeRole(target, roleId);
        storage.savePlot(plot);
        return true;
    }

    public boolean hasPermission(Plot plot, UUID actor, Permission permission) {
        return plot != null && permission != null && plot.isAllowed(actor, permission);
    }

    public boolean hasPermission(String plotId, UUID actor, Permission permission) {
        Plot plot = storageForPlot(plotId).getPlot(plotId);
        return hasPermission(plot, actor, permission);
    }

    private boolean canManageRoles(Plot plot, UUID actor) {
        if (plot == null || actor == null) {
            return false;
        }
        return plot.isAllowed(actor, Permission.ROLE_MANAGE) || Objects.equals(plot.getOwner(), actor);
    }

    private Map<Role, Set<Permission>> loadRolePermissions(FileConfiguration config) {
        Map<Role, Set<Permission>> mappings = new EnumMap<>(Role.class);
        for (Role role : Role.values()) {
            List<String> permissionNames = config.getStringList("roles." + role.name());
            Set<Permission> permissions = EnumSet.noneOf(Permission.class);
            for (String raw : permissionNames) {
                try {
                    permissions.add(Permission.valueOf(raw.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Unknown permission '" + raw + "' in roles." + role.name());
                }
            }
            mappings.put(role, permissions);
        }
        return mappings;
    }

    public Plot claimPlotAt(UUID player, World world, int x, int z) {
        Plot plot = getPlotAt(world, x, z);
        if (plot == null || plot.getAreaType() != AreaType.PLOT_UNCLAIMED) {
            return null;
        }
        return claimPlotForPlayer(contextFor(world), plot, player);
    }

    public Plot claimPlotAt(UUID player, int x, int z) { return claimPlotAt(player, getWorld(), x, z); }

    public boolean unclaimPlot(String plotId, UUID requester, boolean force) {
        PlotStorage storage = storageForPlot(plotId);
        Plot plot = storage.getPlot(plotId);
        if (plot == null || plot.getAreaType() != AreaType.PLOT_CLAIMED) return false;
        if (!force && (plot.getOwner() == null || !plot.getOwner().equals(requester))) return false;
        return storage.unclaimPlot(plotId) != null;
    }

    public boolean transferPlotOwnership(String plotId, UUID currentOwner, UUID newOwner) {
        PlotStorage storage = storageForPlot(plotId);
        Plot plot = storage.getPlot(plotId);
        if (plot == null || plot.getState() != Plot.PlotState.CLAIMED || plot.getOwner() == null) return false;
        if (!plot.getOwner().equals(currentOwner)) return false;
        return storage.transferOwnership(plotId, currentOwner, newOwner) != null;
    }

    public boolean deletePlot(String plotId, UUID requester, boolean force) {
        WorldContext ctx = contextFor(getWorldForPlot(plotId));
        Plot plot = ctx.storage.getPlot(plotId);
        if (plot == null) return false;
        if (!force && plot.getOwner() != null && !plot.getOwner().equals(requester)) return false;
        Plot removed = ctx.storage.deletePlot(plotId);
        if (removed != null) {
            ctx.spatialGrid.removePlot(removed);
            return true;
        }
        return false;
    }

    public AreaType resolveAreaTypeAt(World world, int x, int y, int z) {
        if (isPathCoordinate(world, x, y, z)) {
            return AreaType.PATH;
        }
        Plot plot = getPlotAt(world, x, z);
        return plot == null ? AreaType.WILDERNESS : plot.getAreaType();
    }

    public AreaType resolveAreaTypeAt(int x, int y, int z) { return resolveAreaTypeAt(getWorld(), x, y, z); }

    private boolean isPathCoordinate(World world, int x, int y, int z) {
        if (y < 63 || y > 67) return false;
        if (y > 63) return true;
        return getPlotAt(world, x, z) == null;
    }

    public List<Plot> getAllPlots(World world) { return new ArrayList<>(contextFor(world).storage.getAllPlots()); }

    public List<Plot> getAllPlots() {
        List<Plot> all = new ArrayList<>();
        for (WorldContext c : contexts.values()) {
            all.addAll(c.storage.getAllPlots());
        }
        return all;
    }

    public World getWorld() { return contextFor(null).world; }

    public int getPlotSize() { return contextFor(null).settings.getPlotSize(); }

    public int getMaxPlotsPerPlayer(World world) { return contextFor(world).settings.getMaxPlotsPerPlayer(); }
    public int getMaxPlotsPerPlayer() { return getMaxPlotsPerPlayer(getWorld()); }

    public PlotStorage getStorage(World world) { return contextFor(world).storage; }
    public PlotStorage getStorage() { return getStorage(getWorld()); }

    public SpatialGrid getSpatialGrid(World world) { return contextFor(world).spatialGrid; }
    public SpatialGrid getSpatialGrid() { return getSpatialGrid(getWorld()); }

    public Collection<String> getManagedWorlds() { return new ArrayList<>(contexts.keySet()); }

    public void generateUnclaimedPlots(World world, int gridSize, int spacing) {
        WorldContext ctx = contextFor(world);
        int generated = 0;
        for (int i = -gridSize; i <= gridSize; i++) {
            for (int j = -gridSize; j <= gridSize; j++) {
                int centerX = i * spacing;
                int centerZ = j * spacing;
                if (!isValidPlotLocation(ctx, centerX, centerZ)) continue;
                String plotId = "unclaimed_" + ctx.world.getName() + "_" + ctx.plotCounter++;
                int spawnY = findSafeSpawnY(ctx, centerX, centerZ);
                Plot unclaimedPlot = new Plot(plotId, centerX, centerZ, ctx.settings.getPlotSize(), null, System.currentTimeMillis(), spawnY, Plot.PlotState.UNCLAIMED);
                ctx.storage.savePlot(unclaimedPlot);
                ctx.spatialGrid.addPlot(unclaimedPlot);
                generated++;
            }
        }

        Map<String, Integer> biomeDist = new HashMap<>();
        for (Plot plot : ctx.storage.getAllPlots()) {
            String biome = ctx.world.getBlockAt(plot.getCenterX(), plot.getSpawnY(), plot.getCenterZ()).getBiome().name();
            biomeDist.merge(biome, 1, Integer::sum);
        }
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("plot-count", ctx.storage.getAllPlots().size());
        metadata.put("plot-count-cap", ctx.settings.getPlotCountCap());
        metadata.put("biome-distribution", biomeDist);
        worldConfig.saveMetadata(ctx.world.getName(), metadata);

        plugin.getLogger().info("Generated " + generated + " unclaimed plots in world " + ctx.world.getName());
    }

    public void generateUnclaimedPlots(int gridSize, int spacing) { generateUnclaimedPlots(getWorld(), gridSize, spacing); }

    public World getWorldForPlot(String plotId) {
        for (WorldContext ctx : contexts.values()) {
            if (ctx.storage.exists(plotId)) {
                return ctx.world;
            }
        }
        return getWorld();
    }

    private PlotStorage storageForPlot(String plotId) {
        return contextFor(getWorldForPlot(plotId)).storage;
    }

    private int random(int min, int max) { return min + (int) (Math.random() * (max - min + 1)); }
}
