package de.streuland.district;

/**
 * Development levels for districts.
 */
public enum DistrictLevel {
    DORF(0, 0, 0),
    SIEDLUNG(500, 5, 1),
    STADT(2000, 15, 3),
    METROPOLE(10000, 30, 5);

    private final int requiredBuiltBlocks;
    private final int requiredActivePlayers;
    private final int requiredGoals;

    DistrictLevel(int requiredBuiltBlocks, int requiredActivePlayers, int requiredGoals) {
        this.requiredBuiltBlocks = requiredBuiltBlocks;
        this.requiredActivePlayers = requiredActivePlayers;
        this.requiredGoals = requiredGoals;
    }

    public int getRequiredBuiltBlocks() {
        return requiredBuiltBlocks;
    }

    public int getRequiredActivePlayers() {
        return requiredActivePlayers;
    }

    public int getRequiredGoals() {
        return requiredGoals;
    }

    public DistrictLevel next() {
        int ordinal = ordinal();
        DistrictLevel[] values = values();
        if (ordinal + 1 >= values.length) {
            return this;
        }
        return values[ordinal + 1];
    }
}
