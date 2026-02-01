package de.streuland.plot;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a single plot in the Streuland world.
 * 
 * Data is immutable after creation; modifications should go through PlotManager.
 */
public class Plot {
    private final String plotId;
    private final int centerX;
    private final int centerZ;
    private final int size;
    private final UUID owner;
    private final long createdAt;
    private final int spawnY;  // Safe Y coordinate for spawning
    private final Set<UUID> trustedPlayers;
    
    public Plot(String plotId, int centerX, int centerZ, int size, UUID owner, long createdAt, int spawnY) {
        this.plotId = plotId;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.size = size;
        this.owner = owner;
        this.createdAt = createdAt;
        this.spawnY = spawnY;
        this.trustedPlayers = new HashSet<>();
    }
    
    /**
     * Gets the plot ID (unique identifier)
     */
    public String getPlotId() {
        return plotId;
    }
    
    /**
     * Gets the center X coordinate of the plot
     */
    public int getCenterX() {
        return centerX;
    }
    
    /**
     * Gets the center Z coordinate of the plot
     */
    public int getCenterZ() {
        return centerZ;
    }
    
    /**
     * Gets the plot size (all plots are square: size x size)
     */
    public int getSize() {
        return size;
    }
    
    /**
     * Gets the owner UUID
     */
    public UUID getOwner() {
        return owner;
    }
    
    /**
     * Gets creation timestamp
     */
    public long getCreatedAt() {
        return createdAt;
    }
    
    /**
     * Gets the safe Y coordinate for spawning in this plot
     */
    public int getSpawnY() {
        return spawnY;
    }
    
    /**
     * Adds a trusted player to the plot
     */
    public void addTrusted(UUID player) {
        trustedPlayers.add(player);
    }
    
    /**
     * Removes a trusted player from the plot
     */
    public void removeTrusted(UUID player) {
        trustedPlayers.remove(player);
    }
    
    /**
     * Checks if a player is the owner or trusted
     */
    public boolean isAllowed(UUID player) {
        if (owner == null) return false;  // Unclaimed plots cannot be built in
        return owner.equals(player) || trustedPlayers.contains(player);
    }
    
    /**
     * Gets set of trusted players (copy to prevent external modification)
     */
    public Set<UUID> getTrustedPlayers() {
        return new HashSet<>(trustedPlayers);
    }
    
    /**
     * Checks if a block at given coordinates is within this plot's boundary
     * 
     * @param x block X coordinate
     * @param z block Z coordinate
     * @return true if block is within plot boundary
     */
    public boolean contains(int x, int z) {
        int minX = centerX - (size / 2);
        int maxX = centerX + (size / 2);
        int minZ = centerZ - (size / 2);
        int maxZ = centerZ + (size / 2);
        
        return x >= minX && x < maxX && z >= minZ && z < maxZ;
    }
    
    /**
     * Gets the minimum X coordinate (inclusive)
     */
    public int getMinX() {
        return centerX - (size / 2);
    }
    
    /**
     * Gets the maximum X coordinate (exclusive)
     */
    public int getMaxX() {
        return centerX + (size / 2);
    }
    
    /**
     * Gets the minimum Z coordinate (inclusive)
     */
    public int getMinZ() {
        return centerZ - (size / 2);
    }
    
    /**
     * Gets the maximum Z coordinate (exclusive)
     */
    public int getMaxZ() {
        return centerZ + (size / 2);
    }
    
    /**
     * Calculates distance to another plot (center to center)
     */
    public double distance(Plot other) {
        double dx = this.centerX - other.centerX;
        double dz = this.centerZ - other.centerZ;
        return Math.sqrt(dx * dx + dz * dz);
    }
    
    @Override
    public String toString() {
        return "Plot{" +
                "id='" + plotId + '\'' +
                ", center=(" + centerX + "," + centerZ + ")" +
                ", size=" + size +
                ", owner=" + owner +
                '}';
    }
}
