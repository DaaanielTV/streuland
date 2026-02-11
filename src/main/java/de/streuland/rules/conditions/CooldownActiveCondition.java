package de.streuland.rules.conditions;

import de.streuland.rules.RuleCondition;
import de.streuland.rules.RuleContext;

public class CooldownActiveCondition implements RuleCondition {
    private final String key;

    public CooldownActiveCondition(String key) {
        this.key = key;
    }

    @Override
    public boolean test(RuleContext context) {
        return context.getEngine().isOnCooldown(context.getPlayer(), key);
    }
}
