package de.streuland.admin;

import java.time.Instant;
import java.util.UUID;

public class BlockChangeEntry {
    private final String plotId;
    private final UUID playerId;
    private final long timestamp;
    private final BlockChangeAction action;
    private final int x;
    private final int y;
    private final int z;
    private final String fromBlock;
    private final String toBlock;

    public BlockChangeEntry(String plotId, UUID playerId, long timestamp, BlockChangeAction action,
                            int x, int y, int z, String fromBlock, String toBlock) {
        this.plotId = plotId;
        this.playerId = playerId;
        this.timestamp = timestamp;
        this.action = action;
        this.x = x;
        this.y = y;
        this.z = z;
        this.fromBlock = fromBlock;
        this.toBlock = toBlock;
    }

    public String getPlotId() {
        return plotId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Instant getTimestampInstant() {
        return Instant.ofEpochMilli(timestamp);
    }

    public BlockChangeAction getAction() {
        return action;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public String getFromBlock() {
        return fromBlock;
    }

    public String getToBlock() {
        return toBlock;
    }
}
