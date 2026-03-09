package de.streuland.schematic;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

class SchematicPasterIntegrationTest {
    private ServerMock server;
    private JavaPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void pasteFailureRollsBackWorldState() {
        World world = server.addSimpleWorld("world");
        Player player = server.addPlayer();
        Location center = new Location(world, 10, 64, 10);
        player.teleport(center);

        world.getBlockAt(10, 64, 10).setType(Material.DIRT);
        world.getBlockAt(11, 64, 10).setType(Material.DIRT);
        world.getBlockAt(12, 64, 10).setType(Material.DIRT);

        Schematic schematic = new Schematic("small", Arrays.asList(
                new Schematic.SchematicBlock(0, 0, 0, Material.STONE),
                new Schematic.SchematicBlock(1, 0, 0, Material.OAK_PLANKS),
                new Schematic.SchematicBlock(2, 0, 0, Material.GLASS)
        ));

        SchematicPaster paster = new SchematicPaster(plugin);
        paster.setFailureAfterBlocksForTest(2);

        CompletableFuture<Boolean> result = paster.pasteAsync(schematic, center, player.getUniqueId());
        for (int i = 0; i < 10 && !result.isDone(); i++) {
            server.getScheduler().performOneTick();
        }

        Assertions.assertTrue(result.isDone(), "Paste future should complete");
        Assertions.assertFalse(result.join(), "Paste should fail due to simulated exception");

        Assertions.assertEquals(Material.DIRT, world.getBlockAt(10, 64, 10).getType());
        Assertions.assertEquals(Material.DIRT, world.getBlockAt(11, 64, 10).getType());
        Assertions.assertEquals(Material.DIRT, world.getBlockAt(12, 64, 10).getType());
    }
}
