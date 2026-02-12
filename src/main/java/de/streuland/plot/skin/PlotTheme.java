package de.streuland.plot.skin;

import org.bukkit.Particle;

import java.util.Locale;

public enum PlotTheme {
    MODERN("Modern", Particle.END_ROD),
    MEDIEVAL("Medieval", Particle.CRIT),
    NATURE("Nature", Particle.VILLAGER_HAPPY),
    STEAMPUNK("Steampunk", Particle.SMOKE_NORMAL);

    private final String displayName;
    private final Particle borderParticle;

    PlotTheme(String displayName, Particle borderParticle) {
        this.displayName = displayName;
        this.borderParticle = borderParticle;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Particle getBorderParticle() {
        return borderParticle;
    }

    public static PlotTheme fromInput(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        for (PlotTheme value : values()) {
            if (value.name().equals(normalized)) {
                return value;
            }
        }
        return null;
    }
}
