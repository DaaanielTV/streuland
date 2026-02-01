package de.streuland.path;

import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * Generates paths between plots using a simple line-of-sight algorithm.
 * 
 * Strategy:
 * - Connect new plot to nearest existing plot
 * - Follow terrain (no floating paths)
 * - Flatten to reasonable width
 * - Replace grass/trees but preserve water/lava
 */
public class PathGenerator {
    private final JavaPlugin plugin;
    private final PlotManager plotManager;
    private final int pathWidth;
    private final boolean useCurves;
    private final Set<Material> pathBlockReplacements;
    private final List<Material> pathStoneBlocks;
    private final int PATH_DEPTH_OFFSET = 60;  // Place paths 60 blocks deeper
    
    public PathGenerator(JavaPlugin plugin, PlotManager plotManager) {
        this.plugin = plugin;
        this.plotManager = plotManager;
        
        FileConfiguration config = plugin.getConfig();
        this.pathWidth = config.getInt("path.width", 4);
        this.useCurves = config.getBoolean("path.use-curves", true);
        this.pathBlockReplacements = new HashSet<>(Arrays.asList(
            Material.GRASS_BLOCK,
            Material.DIRT,
            Material.OAK_LOG,
            Material.BIRCH_LOG,
            Material.SPRUCE_LOG,
            Material.JUNGLE_LOG,
            Material.ACACIA_LOG,
            Material.DARK_OAK_LOG,
            Material.OAK_LEAVES,
            Material.BIRCH_LEAVES,
            Material.SPRUCE_LEAVES,
            Material.JUNGLE_LEAVES,
            Material.ACACIA_LEAVES,
            Material.DARK_OAK_LEAVES,
            Material.TALL_GRASS,
            Material.SEAGRASS,
            Material.TALL_SEAGRASS
        ));
        
        // Stone blocks for paths
        this.pathStoneBlocks = Arrays.asList(
            Material.STONE,
            Material.GRANITE,
            Material.DIORITE,
            Material.ANDESITE,
            Material.MOSSY_COBBLESTONE,
            Material.COBBLESTONE
        );
    }
    
    /**
     * Generates a path from a new plot to the nearest existing plot, or to spawn if it's the first plot.
     * All paths are at fixed Y=63 level.
     * Returns list of block positions to modify.
     */
    public List<BlockPosition> generatePath(Plot newPlot) {
        List<BlockPosition> pathBlocks = new ArrayList<>();
        final int FIXED_PATH_Y = 63;
        
        int targetX, targetZ;
        Plot nearest = plotManager.getNearestPlot(newPlot.getCenterX(), newPlot.getCenterZ());
        
        if (nearest == null) {
            // First plot - connect to spawn at (0, 0)
            targetX = 0;
            targetZ = 0;
            plugin.getLogger().info("First plot detected - creating path to spawn (0, 0)");
        } else {
            // Subsequent plots - connect to nearest existing plot
            targetX = nearest.getCenterX();
            targetZ = nearest.getCenterZ();
            plugin.getLogger().info("Creating path to nearest plot at (" + targetX + ", " + targetZ + ")");
        }
        
        // Calculate entry point (edge of the plot closest to target)
        int plotRadius = newPlot.getSize() / 2;
        int dx = targetX - newPlot.getCenterX();
        int dz = targetZ - newPlot.getCenterZ();
        
        // Determine which edge of the plot is closest to target
        int entryX = newPlot.getCenterX();
        int entryZ = newPlot.getCenterZ();
        
        if (Math.abs(dx) > Math.abs(dz)) {
            // Target is more to the left or right
            entryX = newPlot.getCenterX() + (dx > 0 ? plotRadius : -plotRadius);
        } else {
            // Target is more to the front or back
            entryZ = newPlot.getCenterZ() + (dz > 0 ? plotRadius : -plotRadius);
        }
        
        // Generate line from entry point to target
        List<BlockPosition> line = bresenhamLine(entryX, entryZ, targetX, targetZ);
        
        plugin.getLogger().info("Path height fixed at Y=" + FIXED_PATH_Y);
        
        // Expand line to path width (5x5 area), all at Y=63
        for (BlockPosition pos : line) {
            // Expand around center - 5x5 area (±2 blocks)
            for (int dx2 = -2; dx2 <= 2; dx2++) {
                for (int dz2 = -2; dz2 <= 2; dz2++) {
                    int x = pos.x + dx2;
                    int z = pos.z + dz2;
                    
                    pathBlocks.add(new BlockPosition(x, FIXED_PATH_Y, z));
                }
            }
        }
        
        return pathBlocks;
    }
    
    /**
     * Applies path blocks to the world (should be called on main thread).
     * Uses random stone blocks for paths and fills support blocks below if needed.
     * Spreads block placement over multiple ticks to avoid lag.
     */
    public void buildPathBlocks(List<BlockPosition> pathBlocks) {
        if (pathBlocks.isEmpty()) {
            return;
        }
        
        World world = plotManager.getWorld();
        java.util.Random random = new java.util.Random();
        final int FIXED_PATH_Y = 63;
        final int CLEARANCE_ABOVE = 5;
        int batchSize = 500;
        int totalBlocks = pathBlocks.size();
        final int[] blocksPlaced = {0};  // Track total blocks placed
        final int[] batchesTotal = {(totalBlocks + batchSize - 1) / batchSize};  // Total batches
        final int[] batchesCompleted = {0};  // Track completed batches
        
        // Split pathBlocks into batches and schedule them over time
        for (int i = 0; i < pathBlocks.size(); i += batchSize) {
            final int batchStart = i;
            final int batchEnd = Math.min(i + batchSize, pathBlocks.size());
            
            // Schedule each batch on the main thread with a small delay
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                for (int j = batchStart; j < batchEnd; j++) {
                    BlockPosition pos = pathBlocks.get(j);
                    
                    // Place cobblestone or mossy cobblestone at fixed Y=63
                    Block pathBlock = world.getBlockAt(pos.x, FIXED_PATH_Y, pos.z);
                    Material stoneType = random.nextBoolean() ? Material.COBBLESTONE : Material.MOSSY_COBBLESTONE;
                    pathBlock.setType(stoneType);
                    
                    blocksPlaced[0]++;
                    
                    // Clear 4 blocks above for path clearance (Y=64-67)
                    for (int clearY = 1; clearY <= 4; clearY++) {
                        Block above = world.getBlockAt(pos.x, FIXED_PATH_Y + clearY, pos.z);
                        above.setType(Material.AIR);
                    }
                }
                
                batchesCompleted[0]++;
                
                // Only log when all batches are done
                if (batchesCompleted[0] == batchesTotal[0]) {
                    plugin.getLogger().info("Path completed! Built " + blocksPlaced[0] + " path blocks.");
                }
            }, (long)(i / batchSize));  // Delay each batch by tick number
        }
    }
    
    /**
     * Bresenham line algorithm for integer-only 2D pathfinding.
     */
    private List<BlockPosition> bresenhamLine(int x0, int z0, int x1, int z1) {
        List<BlockPosition> line = new ArrayList<>();
        
        int dx = Math.abs(x1 - x0);
        int dz = Math.abs(z1 - z0);
        int sx = x0 < x1 ? 1 : -1;
        int sz = z0 < z1 ? 1 : -1;
        int err = dx - dz;
        
        int x = x0;
        int z = z0;
        
        while (true) {
            line.add(new BlockPosition(x, 0, z));  // Y will be filled in later
            
            if (x == x1 && z == z1) break;
            
            int e2 = 2 * err;
            if (e2 > -dz) {
                err -= dz;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                z += sz;
            }
        }
        
        return line;
    }
    
    /**
     * Checks if a material can be removed (part of path-building)
     */
    private boolean isRemovable(Material mat) {
        return pathBlockReplacements.contains(mat);
    }
    
    /**
     * Simple 3D coordinate holder
     */
    public static class BlockPosition {
        public int x;
        public int y;
        public int z;
        
        public BlockPosition(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
        
        @Override
        public String toString() {
            return "(" + x + "," + y + "," + z + ")";
        }
    }
}
