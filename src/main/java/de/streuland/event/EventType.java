package de.streuland.event;

import org.bukkit.ChatColor;

public enum EventType {
    COMBAT_BONUS("COMBAT_BONUS", "Kampf-Bonus", "§c§l", ChatColor.RED, true, false),
    HARVEST_FESTIVAL("HARVEST_FESTIVAL", "Erntefest", "§e§l", ChatColor.YELLOW, true, false),
    MOB_INVASION("MOB_INVASION", "Monster-Invasion", "§4§l", ChatColor.DARK_RED, false, true),
    LUCK_BOOST("LUCK_BOOST", "Glücks-Boost", "§d§l", ChatColor.LIGHT_PURPLE, true, false),
    DOUBLE_DROPS("DOUBLE_DROPS", "Doppelte Drops", "§a§l", ChatColor.GREEN, true, false),
    XP_BOOST("XP_BOOST", "XP-Boost", "§b§l", ChatColor.AQUA, true, false),
    RAIN("RAIN", "Regen", "§9§l", ChatColor.BLUE, false, false),
    SUN("SUN", "Sonnenschein", "§e§l", ChatColor.YELLOW, false, false),
    STORM("STORM", "Sturm", "§8§l", ChatColor.DARK_GRAY, false, true);

    private final String id;
    private final String displayName;
    private final String prefix;
    private final ChatColor chatColor;
    private final boolean appliesToPlayer;
    private final boolean isDangerous;

    EventType(String id, String displayName, String prefix, ChatColor chatColor, boolean appliesToPlayer, boolean isDangerous) {
        this.id = id;
        this.displayName = displayName;
        this.prefix = prefix;
        this.chatColor = chatColor;
        this.appliesToPlayer = appliesToPlayer;
        this.isDangerous = isDangerous;
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

    public boolean isAppliesToPlayer() {
        return appliesToPlayer;
    }

    public boolean isDangerous() {
        return isDangerous;
    }

    public static EventType fromId(String id) {
        if (id == null) return null;
        for (EventType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return type;
            }
        }
        return null;
    }
}