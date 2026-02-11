package de.streuland.rules.conditions;

import de.streuland.rules.RuleCondition;
import de.streuland.rules.RuleContext;
import org.bukkit.block.Biome;

import java.util.Set;

public class BiomeCondition implements RuleCondition {
    private final Set<Biome> allowedBiomes;

    public BiomeCondition(Set<Biome> allowedBiomes) {
        this.allowedBiomes = allowedBiomes;
    }

    @Override
    public boolean test(RuleContext context) {
        if (context.getBiome() == null) {
            return false;
        }
        return allowedBiomes.contains(context.getBiome());
    }
}
