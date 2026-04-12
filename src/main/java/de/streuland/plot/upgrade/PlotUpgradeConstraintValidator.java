package de.streuland.plot.upgrade;

/**
 * Validates upgrade side effects against world/plot protection constraints.
 */
public interface PlotUpgradeConstraintValidator {
    String validate(String plotId, PlotUpgradeDefinition definition, PlotProgressionState state);

    static PlotUpgradeConstraintValidator allowAll() {
        return (plotId, definition, state) -> null;
    }
}
