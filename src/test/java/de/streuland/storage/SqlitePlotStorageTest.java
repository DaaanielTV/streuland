package de.streuland.storage;

import de.streuland.plot.Plot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SqlitePlotStorageTest {

    @Test
    void saveFindNearestAndListAll(@TempDir Path tempDir) {
        SqlitePlotStorage storage = new SqlitePlotStorage(tempDir.resolve("db.sqlite"));

        Plot first = new Plot("world_main_plot_1", 100, 100, 64, UUID.randomUUID(), 10L, 64, Plot.PlotState.CLAIMED);
        Plot second = new Plot("world_main_plot_2", 250, 250, 64, null, 20L, 64, Plot.PlotState.UNCLAIMED);
        storage.save(first);
        storage.save(second);

        Optional<Plot> loaded = storage.findById("world_main_plot_1");
        assertTrue(loaded.isPresent());
        assertEquals(100, loaded.get().getCenterX());

        Optional<Plot> nearest = storage.findNearest("world_main", 120, 110, 80);
        assertTrue(nearest.isPresent());
        assertEquals("world_main_plot_1", nearest.get().getPlotId());

        Collection<Plot> all = storage.listAll();
        assertEquals(2, all.size());
    }

    @Test
    void migrateFromYamlCreatesBackups(@TempDir Path tempDir) throws Exception {
        Path yamlDir = tempDir.resolve("yaml");
        Files.createDirectories(yamlDir);

        String yaml = "id: world_main_plot_99\n" +
                "centerX: 400\n" +
                "centerZ: 500\n" +
                "size: 64\n" +
                "owner: null\n" +
                "state: UNCLAIMED\n" +
                "createdAt: 12345\n" +
                "spawnY: 70\n" +
                "trusted: []\n";
        Files.write(yamlDir.resolve("world_main_plot_99.yml"), yaml.getBytes());

        SqlitePlotStorage storage = new SqlitePlotStorage(tempDir.resolve("db.sqlite"));
        int migrated = storage.migrateFromYaml(yamlDir);

        assertEquals(1, migrated);
        assertTrue(storage.findById("world_main_plot_99").isPresent());
        assertTrue(Files.exists(yamlDir.resolve("world_main_plot_99.yml.bak")));
    }
}
