package de.streuland.plot.snapshot;

import java.util.Objects;

public class ChunkKey {
    private final int x;
    private final int z;

    public ChunkKey(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ChunkKey chunkKey = (ChunkKey) o;
        return x == chunkKey.x && z == chunkKey.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, z);
    }

    @Override
    public String toString() {
        return x + "," + z;
    }

    public static ChunkKey fromString(String value) {
        String[] parts = value.split(",");
        return new ChunkKey(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }
}
