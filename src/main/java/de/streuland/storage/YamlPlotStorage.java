package de.streuland.storage;

import de.streuland.plot.Plot;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class YamlPlotStorage implements PlotStorage {
    private final Path yamlDir;
    private final Yaml yaml;

    public YamlPlotStorage(Path yamlDir) {
        this.yamlDir = yamlDir;
        LoaderOptions loaderOptions = new LoaderOptions();
        this.yaml = new Yaml(new SafeConstructor(loaderOptions), createDumperOptions());
        try {
            Files.createDirectories(yamlDir);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create YAML plot directory: " + yamlDir, e);
        }
    }

    @Override
    public synchronized void save(Plot plot) {
        Path file = fileForId(plot.getPlotId());
        Map<String, Object> map = toMap(plot);
        Path tempFile = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(tempFile)) {
            yaml.dump(map, writer);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write temp plot YAML: " + plot.getPlotId(), e);
        }
        try {
            Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicMoveFailure) {
            try {
                Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to commit plot YAML: " + plot.getPlotId(), e);
            }
        }
    }

    @Override
    public synchronized void delete(String id) {
        Path file = fileForId(id);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete plot YAML: " + id, e);
        }
    }

    @Override
    public synchronized Optional<Plot> findById(String id) {
        Path file = fileForId(id);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        return Optional.ofNullable(readPlot(file));
    }

    @Override
    public synchronized Optional<Plot> findNearest(String world, int x, int z, int radius) {
        int squared = radius * radius;
        Plot nearest = null;
        int best = Integer.MAX_VALUE;
        for (Plot plot : listAll()) {
            if (!world.equals(resolveWorld(plot.getPlotId()))) {
                continue;
            }
            int dx = plot.getCenterX() - x;
            int dz = plot.getCenterZ() - z;
            int dist2 = dx * dx + dz * dz;
            if (dist2 <= squared && dist2 < best) {
                best = dist2;
                nearest = plot;
            }
        }
        return Optional.ofNullable(nearest);
    }

    @Override
    public synchronized Collection<Plot> listAll() {
        try (Stream<Path> files = Files.list(yamlDir)) {
            List<Plot> result = new ArrayList<>();
            files.filter(path -> path.getFileName().toString().endsWith(".yml"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(path -> {
                        Plot plot = readPlot(path);
                        if (plot != null) {
                            result.add(plot);
                        }
                    });
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list YAML plots", e);
        }
    }

    Path fileForId(String id) {
        return yamlDir.resolve(id + ".yml");
    }

    private Plot readPlot(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            Object loaded = yaml.load(in);
            if (!(loaded instanceof Map)) {
                return null;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) loaded;
            String id = String.valueOf(map.getOrDefault("id", trimExtension(file.getFileName().toString())));
            int centerX = toInt(map.get("centerX"));
            int centerZ = toInt(map.get("centerZ"));
            int size = toIntWithDefault(map.get("size"), 64);
            int spawnY = toIntWithDefault(map.get("spawnY"), 64);
            long createdAt = toLongWithDefault(map.get("createdAt"), System.currentTimeMillis());
            UUID owner = map.get("owner") == null ? null : UUID.fromString(String.valueOf(map.get("owner")));
            String stateRaw = String.valueOf(map.getOrDefault("state", owner == null ? "UNCLAIMED" : "CLAIMED"));
            Plot.PlotState state = Plot.PlotState.valueOf(stateRaw);
            Plot plot = new Plot(id, centerX, centerZ, size, owner, createdAt, spawnY, state);
            Object trusted = map.get("trusted");
            if (trusted instanceof List) {
                for (Object value : (List<?>) trusted) {
                    if (value != null) {
                        plot.addTrusted(UUID.fromString(String.valueOf(value)));
                    }
                }
            }
            return plot;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read plot YAML: " + file, e);
        }
    }

    private Map<String, Object> toMap(Plot plot) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", plot.getPlotId());
        map.put("centerX", plot.getCenterX());
        map.put("centerZ", plot.getCenterZ());
        map.put("size", plot.getSize());
        map.put("owner", plot.getOwner() == null ? null : plot.getOwner().toString());
        map.put("state", plot.getState().name());
        map.put("createdAt", plot.getCreatedAt());
        map.put("spawnY", plot.getSpawnY());
        List<String> trusted = new ArrayList<>();
        for (UUID uuid : plot.getTrustedPlayers()) {
            trusted.add(uuid.toString());
        }
        map.put("trusted", trusted);
        return map;
    }

    static String resolveWorld(String plotId) {
        if (plotId.startsWith("unclaimed_")) {
            String remainder = plotId.substring("unclaimed_".length());
            int idx = remainder.lastIndexOf('_');
            return idx > 0 ? remainder.substring(0, idx) : remainder;
        }
        int idx = plotId.indexOf("_plot_");
        return idx > 0 ? plotId.substring(0, idx) : "world";
    }

    private static String trimExtension(String filename) {
        int i = filename.lastIndexOf('.');
        return i > 0 ? filename.substring(0, i) : filename;
    }

    private static DumperOptions createDumperOptions() {
        DumperOptions options = new DumperOptions();
        options.setPrettyFlow(true);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        return options;
    }

    private static int toInt(Object value) {
        return Integer.parseInt(String.valueOf(value));
    }

    private static int toIntWithDefault(Object value, int defaultValue) {
        return value == null ? defaultValue : toInt(value);
    }

    private static long toLongWithDefault(Object value, long defaultValue) {
        return value == null ? defaultValue : Long.parseLong(String.valueOf(value));
    }
}
