package de.streuland.plot.distribution;

/**
 * Represents a candidate plot location with precomputed metrics for scoring.
 */
public class CandidatePlotLocation {
    private final int x;
    private final int z;
    private final String biomeKey;
    private final double terrainVariance;
    private final double infrastructureDistance;
    private final double playerDensity;

    public CandidatePlotLocation(int x, int z, String biomeKey, double terrainVariance, double infrastructureDistance, double playerDensity) {
        this.x = x;
        this.z = z;
        this.biomeKey = biomeKey;
        this.terrainVariance = terrainVariance;
        this.infrastructureDistance = infrastructureDistance;
        this.playerDensity = playerDensity;
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    public String getBiomeKey() {
        return biomeKey;
    }

    public double getTerrainVariance() {
        return terrainVariance;
    }

    public double getInfrastructureDistance() {
        return infrastructureDistance;
    }

    public double getPlayerDensity() {
        return playerDensity;
    }
}
