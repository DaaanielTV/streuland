package de.streuland.rules.conditions;

import de.streuland.rules.RuleCondition;
import de.streuland.rules.RuleContext;

public class NotCondition implements RuleCondition {
    private final RuleCondition condition;

    public NotCondition(RuleCondition condition) {
        this.condition = condition;
    }

    @Override
    public boolean test(RuleContext context) {
        return !condition.test(context);
    }
}
