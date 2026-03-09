package de.streuland.transaction;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import de.streuland.schematic.SchematicPaster;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchematicPasterTransactionTest {
    private ServerMock server;
    private JavaPlugin plugin;
    private World world;

    @BeforeEach
    void setUp() {
        deleteRecursively(new File("plugins/Streuland/transactions"));
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
        deleteRecursively(new File("plugins/Streuland/transactions"));
    }

    @Test
    void shouldRollbackOnPlacementFailureAndWriteLog() {
        TransactionManager manager = new TransactionManager(plugin);
        SchematicPaster paster = new SchematicPaster(manager);
        paster.setFailAfterPlacements(1);

        Location origin = new Location(world, 0, 64, 0);
        List<SchematicPaster.BlockPlacement> placements = new ArrayList<>();
        placements.add(placement(new Location(world, 0, 64, 0), Material.STONE));
        placements.add(placement(new Location(world, 1, 64, 0), Material.GOLD_BLOCK));
        placements.add(placement(new Location(world, 2, 64, 0), Material.DIAMOND_BLOCK));

        AtomicBoolean metadataPersisted = new AtomicBoolean(false);
        boolean success = paster.paste("tx_fail", origin, placements, () -> metadataPersisted.set(true));

        assertFalse(success);
        assertFalse(metadataPersisted.get());
        assertTrue(world.getBlockAt(0, 64, 0).getType() == Material.AIR);
        assertTrue(world.getBlockAt(1, 64, 0).getType() == Material.AIR);
        assertTrue(world.getBlockAt(2, 64, 0).getType() == Material.AIR);
        assertTrue(new File("plugins/Streuland/transactions/tx_fail.log").exists());
    }

    private static SchematicPaster.BlockPlacement placement(Location location, Material material) {
        Block block = location.getBlock();
        BlockState state = block.getState();
        state.setType(material);
        return new SchematicPaster.BlockPlacement(location, state);
    }

    private static void deleteRecursively(File file) {
        if (!file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}
