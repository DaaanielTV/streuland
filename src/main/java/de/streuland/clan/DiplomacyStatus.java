package de.streuland.clan;

import org.bukkit.ChatColor;

public enum DiplomacyStatus {
    ALLY("ALLY", "Allianz", "§a§l", ChatColor.GREEN),
    NEUTRAL("NEUTRAL", "Neutral", "§7§l", ChatColor.GRAY),
    HOSTILE("HOSTILE", "Feindlich", "§c§l", ChatColor.RED),
    WAR("WAR", "Krieg", "§4§l", ChatColor.DARK_RED);

    private final String id;
    private final String displayName;
    private final String prefix;
    private final ChatColor chatColor;

    DiplomacyStatus(String id, String displayName, String prefix, ChatColor chatColor) {
        this.id = id;
        this.displayName = displayName;
        this.prefix = prefix;
        this.chatColor = chatColor;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPrefix() {
        return prefix;
    }

    public ChatColor getChatColor() {
        return chatColor;
    }

    public boolean isHostile() {
        return this == HOSTILE || this == WAR;
    }

    public boolean canBeAttacked() {
        return this == NEUTRAL || this == HOSTILE;
    }

    public static DiplomacyStatus fromId(String id) {
        if (id == null) return NEUTRAL;
        for (DiplomacyStatus status : values()) {
            if (status.id.equalsIgnoreCase(id)) {
                return status;
            }
        }
        return NEUTRAL;
    }
}