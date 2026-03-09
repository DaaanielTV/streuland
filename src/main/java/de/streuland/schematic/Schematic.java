package de.streuland.schematic;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Schematic {
    private final String name;
    private final List<SchematicBlock> blocks;

    public Schematic(String name, List<SchematicBlock> blocks) {
        this.name = name;
        this.blocks = Collections.unmodifiableList(new ArrayList<>(blocks));
    }

    public String getName() {
        return name;
    }

    public List<SchematicBlock> getBlocks() {
        return blocks;
    }

    public static class SchematicBlock {
        private final int x;
        private final int y;
        private final int z;
        private final Material material;

        public SchematicBlock(int x, int y, int z, Material material) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.material = material;
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

        public Material getMaterial() {
            return material;
        }
    }
}
