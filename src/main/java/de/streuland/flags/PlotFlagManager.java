package de.streuland.flags;

import de.streuland.plot.AreaType;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotData;
import de.streuland.plot.PlotManager;
import de.streuland.plot.PlotStorage;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlotFlagManager {
    private static final Pattern NUMERIC_SUFFIX = Pattern.compile(".*_(\\d+)$");

    public interface FlagSyncHook {
        void onFlagsUpdated(Plot plot, Map<Flag, Boolean> resolvedFlags);
    }

    private final PlotManager plotManager;
    private final List<FlagSyncHook> hooks = new ArrayList<>();

    public PlotFlagManager(PlotManager plotManager) {
        this.plotManager = plotManager;
    }

    public boolean isFlagEnabled(int plotId, Flag flag) {
        Plot plot = findPlotByNumericId(plotId);
        if (plot == null) {
            return flag.isEnabledByDefault(AreaType.WILDERNESS);
        }
        return isFlagEnabled(plot, flag);
    }

    public boolean isFlagEnabled(Plot plot, Flag flag) {
        if (plot == null || flag == null) {
            return false;
        }
        PlotData data = storageFor(plot).getPlotData(plot.getPlotId());
        Boolean override = data.getFlagOverrides().get(flag.name());
        if (override != null) {
            return override;
        }
        return flag.isEnabledByDefault(plot.getAreaType());
    }

    public void setFlag(Plot plot, Flag flag, boolean enabled) {
        if (plot == null || flag == null) {
            return;
        }
        PlotStorage storage = storageFor(plot);
        PlotData data = storage.getPlotData(plot.getPlotId());
        data.getFlagOverrides().put(flag.name(), enabled);
        storage.savePlotData(plot.getPlotId(), data);
        notifyHooks(plot);
    }

    public void clearOverride(Plot plot, Flag flag) {
        if (plot == null || flag == null) {
            return;
        }
        PlotStorage storage = storageFor(plot);
        PlotData data = storage.getPlotData(plot.getPlotId());
        data.getFlagOverrides().remove(flag.name());
        storage.savePlotData(plot.getPlotId(), data);
        notifyHooks(plot);
    }

    public void registerHook(FlagSyncHook hook) {
        if (hook != null) {
            hooks.add(hook);
        }
    }

    public Map<Flag, Boolean> resolveFlags(Plot plot) {
        Map<Flag, Boolean> resolved = new java.util.EnumMap<>(Flag.class);
        for (Flag flag : Flag.values()) {
            resolved.put(flag, isFlagEnabled(plot, flag));
        }
        return resolved;
    }

    public OptionalInt parseNumericPlotId(String plotId) {
        if (plotId == null) {
            return OptionalInt.empty();
        }
        Matcher matcher = NUMERIC_SUFFIX.matcher(plotId);
        if (!matcher.matches()) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(matcher.group(1)));
        } catch (NumberFormatException ignored) {
            return OptionalInt.empty();
        }
    }

    private Plot findPlotByNumericId(int numericId) {
        for (Plot plot : plotManager.getAllPlots()) {
            OptionalInt id = parseNumericPlotId(plot.getPlotId());
            if (id.isPresent() && id.getAsInt() == numericId) {
                return plot;
            }
        }
        return null;
    }

    private PlotStorage storageFor(Plot plot) {
        World world = plotManager.getWorldForPlot(plot.getPlotId());
        return plotManager.getStorage(world);
    }

    private void notifyHooks(Plot plot) {
        Map<Flag, Boolean> resolved = resolveFlags(plot);
        for (FlagSyncHook hook : hooks) {
            hook.onFlagsUpdated(plot, resolved);
        }
    }

    public static Flag parseFlag(String rawFlag) {
        if (rawFlag == null) {
            return null;
        }
        try {
            return Flag.valueOf(rawFlag.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
