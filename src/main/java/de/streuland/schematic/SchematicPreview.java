package de.streuland.schematic;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Client-side schematic preview using sendBlockChange marker blocks.
 *
 * Limitations:
 * - Preview is local to one player and disappears on relog/chunk refresh.
 * - No tile-entity/BlockData fidelity is rendered; only simple marker material.
 * - This preview does not spawn entities to keep compatibility with lightweight servers.
 */
public class SchematicPreview {
    private final Map<UUID, List<Location>> activePreviewBlocks = new HashMap<>();

    public void showPreview(Player player, Location center, Schematic schematic) {
        hidePreview(player);

        List<Location> locations = new ArrayList<>();
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        for (Schematic.SchematicBlock block : schematic.getBlocks()) {
            Location target = center.clone().add(block.getX(), block.getY(), block.getZ());
            player.sendBlockChange(target, Material.GLASS, (byte) 0);
            locations.add(target);
        }
        activePreviewBlocks.put(player.getUniqueId(), locations);
    }

    public void hidePreview(Player player) {
        List<Location> locations = activePreviewBlocks.remove(player.getUniqueId());
        if (locations == null) {
            return;
        }
        for (Location location : locations) {
            Block realBlock = location.getBlock();
            player.sendBlockChange(location, realBlock.getType(), realBlock.getData());
        }
    }
}
