package de.streuland.rules.listener;

import de.streuland.rules.RuleEngine;
import de.streuland.rules.RuleTrigger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class RuleListener implements Listener {
    private final RuleEngine engine;

    public RuleListener(JavaPlugin plugin, RuleEngine engine) {
        this.engine = engine;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Location location = event.getBlockPlaced().getLocation();
        engine.handle(RuleTrigger.BLOCK_PLACE, event, player, location);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Location location = event.getBlock().getLocation();
        engine.handle(RuleTrigger.BLOCK_BREAK, event, player, location);
    }

    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent event) {
        Location location = event.getLocation();
        engine.handle(RuleTrigger.ENTITY_SPAWN, event, null, location);
    }

    @EventHandler
    public void onRedstoneUpdate(BlockRedstoneEvent event) {
        Location location = event.getBlock().getLocation();
        engine.handle(RuleTrigger.REDSTONE_UPDATE, event, null, location);
    }
}
