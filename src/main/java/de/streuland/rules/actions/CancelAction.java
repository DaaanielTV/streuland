package de.streuland.rules.actions;

import de.streuland.rules.RuleAction;
import de.streuland.rules.RuleContext;
import org.bukkit.event.Cancellable;

public class CancelAction implements RuleAction {
    @Override
    public void execute(RuleContext context) {
        if (context.getEvent() instanceof Cancellable) {
            ((Cancellable) context.getEvent()).setCancelled(true);
        }
    }
}
