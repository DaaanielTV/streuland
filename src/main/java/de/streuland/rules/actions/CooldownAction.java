package de.streuland.rules.actions;

import de.streuland.rules.RuleAction;
import de.streuland.rules.RuleContext;

public class CooldownAction implements RuleAction {
    private final String key;
    private final long durationMillis;

    public CooldownAction(String key, long durationMillis) {
        this.key = key;
        this.durationMillis = durationMillis;
    }

    @Override
    public void execute(RuleContext context) {
        context.getEngine().setCooldown(context.getPlayer(), key, durationMillis);
    }
}
