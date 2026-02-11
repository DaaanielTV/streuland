package de.streuland.rules;

public interface Rule {
    String getName();
    RuleTrigger getTrigger();
    boolean matches(RuleContext context);
    void execute(RuleContext context);
}
