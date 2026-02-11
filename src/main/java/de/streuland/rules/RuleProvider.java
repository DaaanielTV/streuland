package de.streuland.rules;

import java.util.Collection;

public interface RuleProvider {
    Collection<Rule> loadRules(RuleEngine engine);
}
