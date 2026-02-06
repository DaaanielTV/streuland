package de.streuland.rules;

public interface RuleCondition {
    boolean test(RuleContext context);
}
