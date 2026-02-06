package de.streuland.rules.actions;

import de.streuland.rules.RuleAction;
import de.streuland.rules.RuleContext;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockPlaceEvent;

public class ModifyBlockAction implements RuleAction {
    private final Material material;
    private final String blockData;

    public ModifyBlockAction(Material material, String blockData) {
        this.material = material;
        this.blockData = blockData;
    }

    @Override
    public void execute(RuleContext context) {
        if (context.getEvent() instanceof BlockPlaceEvent) {
            BlockPlaceEvent event = (BlockPlaceEvent) context.getEvent();
            Block block = event.getBlockPlaced();
            block.setType(material, false);
            if (blockData != null) {
                try {
                    block.setBlockData(Bukkit.createBlockData(blockData), false);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }
}
