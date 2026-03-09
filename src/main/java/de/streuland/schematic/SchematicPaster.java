package de.streuland.schematic;

import de.streuland.transaction.TransactionManager;
import de.streuland.transaction.WorldTransaction;
import org.bukkit.Location;
import org.bukkit.block.BlockState;

import java.util.List;
import java.util.UUID;

/**
 * Applies schematic-like block placements with transactional rollback support.
 */
public class SchematicPaster {
    private final TransactionManager transactionManager;
    private volatile int failAfterPlacements = -1;

    public SchematicPaster(TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    public boolean paste(String transactionId,
                         Location origin,
                         List<BlockPlacement> placements,
                         Runnable persistMetadata) {
        String id = transactionId == null || transactionId.isEmpty()
            ? "schematic_" + UUID.randomUUID()
            : transactionId;
        WorldTransaction tx = transactionManager.begin(id, origin);
        int placed = 0;
        try {
            for (BlockPlacement placement : placements) {
                Location location = placement.getLocation();
                tx.recordBlock(location, location.getBlock().getState());
                tx.applyChange(location, placement.getAfterState());
                placed++;
                if (failAfterPlacements >= 0 && placed > failAfterPlacements) {
                    throw new IllegalStateException("Simulated schematic placement failure at index " + placed);
                }
            }
            persistMetadata.run();
            tx.commit();
            return true;
        } catch (Exception ex) {
            tx.rollback();
            return false;
        } finally {
            transactionManager.close(id);
        }
    }

    public void setFailAfterPlacements(int failAfterPlacements) {
        this.failAfterPlacements = failAfterPlacements;
    }

    public static class BlockPlacement {
        private final Location location;
        private final BlockState afterState;

        public BlockPlacement(Location location, BlockState afterState) {
            this.location = location;
            this.afterState = afterState;
        }

        public Location getLocation() {
            return location;
        }

        public BlockState getAfterState() {
            return afterState;
        }
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
