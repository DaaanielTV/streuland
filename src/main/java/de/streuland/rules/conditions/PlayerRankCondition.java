package de.streuland.rules.conditions;

import de.streuland.rules.RuleCondition;
import de.streuland.rules.RuleContext;
import org.bukkit.entity.Player;

public class PlayerRankCondition implements RuleCondition {
    private final String permission;

    public PlayerRankCondition(String permission) {
        this.permission = permission;
    }

    @Override
    public boolean test(RuleContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return false;
        }
        return player.hasPermission(permission);
    }
}
