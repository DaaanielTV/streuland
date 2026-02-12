package de.streuland.quest;

public class QuestProgress {
    private int value;
    private boolean completed;
    private long completedAt;

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = Math.max(0, value);
    }

    public void increment(int delta) {
        setValue(value + delta);
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
        if (completed && completedAt == 0L) {
            this.completedAt = System.currentTimeMillis();
        }
    }

    public long getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(long completedAt) {
        this.completedAt = completedAt;
    }
}
