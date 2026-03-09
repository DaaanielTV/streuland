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
    }
}
