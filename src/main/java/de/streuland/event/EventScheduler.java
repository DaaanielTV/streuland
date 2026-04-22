package de.streuland.event;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class EventScheduler {
    public enum TriggerType {
        TRIGGER_ONCE,
        TRIGGER_RECURRING
    }

    private final JavaPlugin plugin;
    private final File configFile;
    private final ConcurrentMap<UUID, ScheduledEvent> events = new ConcurrentHashMap<>();
    private BukkitTask schedulerTask;

    public EventScheduler(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "scheduled-events.yml");
        loadEvents();
    }

    public void start() {
        stop();
        schedulerTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1200L, 1200L);
    }

    public void stop() {
        if (schedulerTask != null) {
            schedulerTask.cancel();
            schedulerTask = null;
        }
        saveEvents();
    }

    private void tick() {
        int currentMonth = LocalDateTime.now().getMonthValue();
        int currentDay = LocalDateTime.now().getDayOfMonth();
        int currentHour = LocalDateTime.now().getHour();
        int currentMinute = LocalDateTime.now().getMinute();

        for (ScheduledEvent event : events.values()) {
            if (!event.isActive()) {
                continue;
            }

            if (event.getTriggerType() == TriggerType.TRIGGER_ONCE && event.wasTriggeredToday()) {
                continue;
            }

            ScheduledEvent.CalendarSchedule schedule = event.getCalendarSchedule();
            if (schedule != null) {
                if (schedule.getMonth() == currentMonth
                        && schedule.getDay() == currentDay
                        && schedule.getHour() == currentHour
                        && schedule.getMinute() == currentMinute) {
                    triggerEvent(event);
                }
            }
        }
    }

    private void triggerEvent(ScheduledEvent event) {
        Bukkit.broadcastMessage(event.getType().getPrefix() + "[Event] " + event.getType().getDisplayName() + " hat begonnen!");

        if (event.getType().isDangerous()) {
            Bukkit.broadcastMessage("§c§lWarnung:§c§l Dies ist ein gefährliches Event!");
        }

        if (event.getDuration() != null) {
            long ticks = event.getDuration().toMillis() / 50L;
            if (ticks > 0) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> endEvent(event), ticks);
            }
        }

        if (event.getTriggerType() == TriggerType.TRIGGER_ONCE) {
            event.setTriggeredToday(true);
        }
    }

    private void endEvent(ScheduledEvent event) {
        Bukkit.broadcastMessage("§7[Event] " + event.getType().getDisplayName() + " ist beendet.");
    }

    public ScheduledEvent addEvent(EventType type, TriggerType triggerType,
            ScheduledEvent.CalendarSchedule calendarSchedule, ScheduledEvent.SeasonDaySchedule seasonDaySchedule,
            org.bukkit.configuration.ConfigurationSection statEffects) {
        ScheduledEvent event = new ScheduledEvent(type, triggerType, calendarSchedule, seasonDaySchedule, statEffects);
        events.put(event.getId(), event);
        saveEvents();
        return event;
    }

    public void removeEvent(UUID id) {
        events.remove(id);
        saveEvents();
    }

    public List<ScheduledEvent> getActiveEvents() {
        List<ScheduledEvent> active = new ArrayList<>();
        for (ScheduledEvent event : events.values()) {
            if (event.isActive()) {
                active.add(event);
            }
        }
        return active;
    }

    public List<ScheduledEvent> getAllEvents() {
        return new ArrayList<>(events.values());
    }

    public boolean isEventActive(EventType type) {
        for (ScheduledEvent event : events.values()) {
            if (event.getType() == type && event.isActive()) {
                return true;
            }
        }
        return false;
    }

    public void setEventActive(UUID id, boolean active) {
        ScheduledEvent event = events.get(id);
        if (event != null) {
            event.setActive(active);
            saveEvents();
        }
    }

    private void loadEvents() {
        if (!configFile.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
        for (String key : yaml.getKeys(false)) {
            ScheduledEvent event = ScheduledEvent.fromYaml(yaml.getConfigurationSection(key));
            if (event != null) {
                events.put(event.getId(), event);
            }
        }
    }

    private void saveEvents() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (ScheduledEvent event : events.values()) {
            event.toYaml(yaml.createSection(event.getId().toString()));
        }
        try {
            yaml.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save scheduled events: " + e.getMessage());
        }
    }
}