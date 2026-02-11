package de.streuland.plot.snapshot;

import java.util.Map;

public class BlockSnapshot {
    private final int x;
    private final int y;
    private final int z;
    private final String type;
    private final String blockData;
    private final Map<String, Object> tileData;

    public BlockSnapshot(int x, int y, int z, String type, String blockData, Map<String, Object> tileData) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.type = type;
        this.blockData = blockData;
        this.tileData = tileData;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public String getType() {
        return type;
    }

    public String getBlockData() {
        return blockData;
    }

    public Map<String, Object> getTileData() {
        return tileData;
    }
}
