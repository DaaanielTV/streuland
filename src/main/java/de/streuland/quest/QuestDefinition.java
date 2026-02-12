package de.streuland.quest;

public class QuestDefinition {
    private final String id;
    private final String title;
    private final String description;
    private final QuestType type;
    private final String objectiveKey;
    private final int target;
    private final long expiresAt;
    private final QuestReward reward;

    public QuestDefinition(String id, String title, String description, QuestType type, String objectiveKey, int target, long expiresAt, QuestReward reward) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.type = type;
        this.objectiveKey = objectiveKey;
        this.target = target;
        this.expiresAt = expiresAt;
        this.reward = reward;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public QuestType getType() {
        return type;
    }

    public String getObjectiveKey() {
        return objectiveKey;
    }

    public int getTarget() {
        return target;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public QuestReward getReward() {
        return reward;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }
}
