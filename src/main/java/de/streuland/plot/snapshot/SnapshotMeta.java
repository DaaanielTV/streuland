package de.streuland.plot.snapshot;

import java.util.UUID;

public class SnapshotMeta {
    private final String id;
    private final long createdAt;
    private final UUID creator;

    public SnapshotMeta(String id, long createdAt, UUID creator) {
        this.id = id;
        this.createdAt = createdAt;
        this.creator = creator;
    }

    public String getId() {
        return id;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public UUID getCreator() {
        return creator;
    }
}
