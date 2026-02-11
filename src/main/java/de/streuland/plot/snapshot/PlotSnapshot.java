package de.streuland.plot.snapshot;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class PlotSnapshot {
    private final String id;
    private final String plotId;
    private final UUID creator;
    private final long createdAt;
    private final List<BlockSnapshot> blocks;

    public PlotSnapshot(String id, String plotId, UUID creator, long createdAt, List<BlockSnapshot> blocks) {
        this.id = id;
        this.plotId = plotId;
        this.creator = creator;
        this.createdAt = createdAt;
        this.blocks = blocks;
    }

    public String getId() {
        return id;
    }

    public String getPlotId() {
        return plotId;
    }

    public UUID getCreator() {
        return creator;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public List<BlockSnapshot> getBlocks() {
        return Collections.unmodifiableList(blocks);
    }
}
