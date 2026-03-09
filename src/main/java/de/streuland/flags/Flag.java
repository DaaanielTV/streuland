package de.streuland.flags;

import de.streuland.plot.AreaType;

import java.util.EnumMap;
import java.util.Map;

public enum Flag {
    PVP(defaults(false, true, false, false)),
    MOB_SPAWN(defaults(false, true, false, true)),
    EXPLOSIONS(defaults(false, true, false, true)),
    FIRE_SPREAD(defaults(false, true, false, true)),
    KEEP_INVENTORY_OVERWRITE(defaults(false, false, false, false)),
    REDSTONE(defaults(false, true, false, true));

    private final Map<AreaType, Boolean> defaults;

    Flag(Map<AreaType, Boolean> defaults) {
        this.defaults = defaults;
    }

    public boolean isEnabledByDefault(AreaType areaType) {
        return defaults.getOrDefault(areaType, false);
    }

    private static Map<AreaType, Boolean> defaults(boolean path, boolean plotClaimed, boolean plotUnclaimed, boolean wilderness) {
        Map<AreaType, Boolean> map = new EnumMap<>(AreaType.class);
        map.put(AreaType.PATH, path);
        map.put(AreaType.PLOT_CLAIMED, plotClaimed);
        map.put(AreaType.PLOT_UNCLAIMED, plotUnclaimed);
        map.put(AreaType.WILDERNESS, wilderness);
        return map;
    }
}
