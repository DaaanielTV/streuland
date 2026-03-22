package de.streuland.plot.snapshot;

import java.util.UUID;

public class SnapshotMeta {
    private final String id;
    private final long createdAt;
    private final UUID creator;
    private final String authorName;
    private final String note;

    public SnapshotMeta(String id, long createdAt, UUID creator) {
        this(id, createdAt, creator, null, null);
    }

    public SnapshotMeta(String id, long createdAt, UUID creator, String authorName, String note) {
        this.id = id;
        this.createdAt = createdAt;
        this.creator = creator;
        this.authorName = authorName;
        this.note = note;
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

    public String getAuthorName() {
        return authorName;
    }

    public String getNote() {
        return note;
    }
}
