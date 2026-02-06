package de.streuland.rules;

import de.streuland.rules.actions.CancelAction;
import de.streuland.rules.actions.CooldownAction;
import de.streuland.rules.actions.ModifyBlockAction;
import de.streuland.rules.conditions.BiomeCondition;
import de.streuland.rules.conditions.CooldownActiveCondition;
import de.streuland.rules.conditions.NotCondition;
import de.streuland.rules.conditions.PlayerRankCondition;
import de.streuland.rules.conditions.PlotLevelCondition;
import org.bukkit.Material;
import org.bukkit.block.Biome;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class ExampleRules implements RuleProvider {
    @Override
    public Collection<Rule> loadRules(RuleEngine engine) {
        Rule blockPlaceCooldown = new DefaultRule(
                "block-place-cooldown",
                RuleTrigger.BLOCK_PLACE,
                Arrays.asList(new CooldownActiveCondition("block-place")),
                Arrays.asList(new CancelAction())
        );

        Rule blockPlaceSetCooldown = new DefaultRule(
                "block-place-set-cooldown",
                RuleTrigger.BLOCK_PLACE,
                Arrays.asList(),
                Arrays.asList(new CooldownAction("block-place", 1500L))
        );

        Rule restrictDesertBreak = new DefaultRule(
                "desert-break-restrict",
                RuleTrigger.BLOCK_BREAK,
                Arrays.asList(
                        new BiomeCondition(new HashSet<>(Arrays.asList(Biome.DESERT, Biome.DESERT_HILLS))),
                        new NotCondition(new PlayerRankCondition("streuland.rules.rank.builder"))
                ),
                Arrays.asList(new CancelAction())
        );

        Rule lowLevelPlace = new DefaultRule(
                "low-level-place",
                RuleTrigger.BLOCK_PLACE,
                Arrays.asList(new NotCondition(new PlotLevelCondition(2))),
                Arrays.asList(new ModifyBlockAction(Material.DIRT, Material.DIRT.createBlockData().getAsString()))
        );

        return Arrays.asList(blockPlaceCooldown, blockPlaceSetCooldown, restrictDesertBreak, lowLevelPlace);
    }
}
