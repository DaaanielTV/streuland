package de.streuland.rules.conditions;

import de.streuland.rules.RuleCondition;
import de.streuland.rules.RuleContext;

public class PlotLevelCondition implements RuleCondition {
    private final int minimumLevel;

    public PlotLevelCondition(int minimumLevel) {
        this.minimumLevel = minimumLevel;
    }

    @Override
    public boolean test(RuleContext context) {
        return context.getPlotLevel() >= minimumLevel;
    }
}
