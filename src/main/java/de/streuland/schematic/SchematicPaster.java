package de.streuland.schematic;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SchematicPaster {
    private final JavaPlugin plugin;
    private int failureAfterBlocks = -1;

    public SchematicPaster(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Boolean> pasteAsync(Schematic schematic, Location center, UUID actor) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            World world = center.getWorld();
            if (world == null) {
                future.complete(false);
                return;
            }

            SchematicTransaction transaction = new SchematicTransaction(plugin, actor, schematic.getName());
            try {
                int placed = 0;
                for (Schematic.SchematicBlock schematicBlock : schematic.getBlocks()) {
                    Location target = center.clone().add(schematicBlock.getX(), schematicBlock.getY(), schematicBlock.getZ());
                    transaction.capture(target.getBlock());
                    target.getBlock().setType(schematicBlock.getMaterial(), false);
                    placed++;
                    if (failureAfterBlocks >= 0 && placed >= failureAfterBlocks) {
                        throw new IllegalStateException("Simulierter Fehler nach " + failureAfterBlocks + " Blöcken");
                    }
                }
                transaction.writeLog(true, "Paste erfolgreich");
                Player player = Bukkit.getPlayer(actor);
                if (player != null) {
                    player.teleport(center);
                }
                future.complete(true);
            } catch (Exception e) {
                transaction.rollback();
                transaction.writeLog(false, e.getMessage());
                Player player = Bukkit.getPlayer(actor);
                if (player != null) {
                    player.sendMessage("§cTemplate-Paste fehlgeschlagen. Änderungen wurden zurückgerollt.");
                }
                future.complete(false);
            }
        });
        return future;
    }

    void setFailureAfterBlocksForTest(int failureAfterBlocks) {
        this.failureAfterBlocks = failureAfterBlocks;
    }
}
