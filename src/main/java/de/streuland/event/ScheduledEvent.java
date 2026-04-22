package de.streuland.event;

import org.bukkit.configuration.ConfigurationSection;

import java.time.Duration;
import java.util.UUID;

public class ScheduledEvent {
    private final UUID id;
    private final String name;
    private final String description;
    private final EventType type;
    private final EventScheduler.TriggerType triggerType;
    private final CalendarSchedule calendarSchedule;
    private final SeasonDaySchedule seasonDaySchedule;
    private final Duration duration;
    private final ConfigurationSection statEffects;
    private boolean active;
    private boolean triggeredToday;

    public ScheduledEvent(EventType type, EventScheduler.TriggerType triggerType,
            CalendarSchedule calendarSchedule, SeasonDaySchedule seasonDaySchedule,
            ConfigurationSection statEffects) {
        this.id = UUID.randomUUID();
        this.name = "event." + type.getId().toLowerCase();
        this.description = "event." + type.getId().toLowerCase() + ".description";
        this.type = type;
        this.triggerType = triggerType;
        this.calendarSchedule = calendarSchedule;
        this.seasonDaySchedule = seasonDaySchedule;
        this.duration = Duration.ofHours(2);
        this.statEffects = statEffects;
        this.active = true;
        this.triggeredToday = false;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public EventType getType() {
        return type;
    }

    public EventScheduler.TriggerType getTriggerType() {
        return triggerType;
    }

    public CalendarSchedule getCalendarSchedule() {
        return calendarSchedule;
    }

    public SeasonDaySchedule getSeasonDaySchedule() {
        return seasonDaySchedule;
    }

    public Duration getDuration() {
        return duration;
    }

    public ConfigurationSection getStatEffects() {
        return statEffects;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean wasTriggeredToday() {
        return triggeredToday;
    }

    public void setTriggeredToday(boolean triggeredToday) {
        this.triggeredToday = triggeredToday;
    }

    public static class CalendarSchedule {
        private final int month;
        private final int day;
        private final int hour;
        private final int minute;

        public CalendarSchedule(int month, int day, int hour, int minute) {
            this.month = month;
            this.day = day;
            this.hour = hour;
            this.minute = minute;
        }

        public int getMonth() {
            return month;
        }

        public int getDay() {
            return day;
        }

        public int getHour() {
            return hour;
        }

        public int getMinute() {
            return minute;
        }

        public static CalendarSchedule fromYaml(ConfigurationSection section) {
            if (section == null) return null;
            int month = section.getInt("month", -1);
            int day = section.getInt("day", -1);
            int hour = section.getInt("hour", -1);
            int minute = section.getInt("minute", -1);
            if (month < 1 || month > 12 || day < 1 || day > 31 || hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return null;
            }
            return new CalendarSchedule(month, day, hour, minute);
        }

        public void toYaml(ConfigurationSection section) {
            section.set("month", month);
            section.set("day", day);
            section.set("hour", hour);
            section.set("minute", minute);
        }
    }

    public static class SeasonDaySchedule {
        private final int dayInSeason;
        private final int hour;

        public SeasonDaySchedule(int dayInSeason, int hour) {
            this.dayInSeason = dayInSeason;
            this.hour = hour;
        }

        public int getDayInSeason() {
            return dayInSeason;
        }

        public int getHour() {
            return hour;
        }

        public static SeasonDaySchedule fromYaml(ConfigurationSection section) {
            if (section == null) return null;
            int dayInSeason = section.getInt("dayInSeason", -1);
            int hour = section.getInt("hour", -1);
            if (dayInSeason < 1 || hour < 0 || hour > 23) {
                return null;
            }
            return new SeasonDaySchedule(dayInSeason, hour);
        }

        public void toYaml(ConfigurationSection section) {
            section.set("dayInSeason", dayInSeason);
            section.set("hour", hour);
        }
    }

    public static ScheduledEvent fromYaml(ConfigurationSection section) {
        if (section == null) return null;
        String typeStr = section.getString("type");
        String triggerStr = section.getString("triggerType");
        EventType type = EventType.fromId(typeStr);
        EventScheduler.TriggerType triggerType = EventScheduler.TriggerType.valueOf(triggerStr);
        if (type == null || triggerType == null) return null;
        CalendarSchedule calendar = CalendarSchedule.fromYaml(section.getConfigurationSection("calendarSchedule"));
        SeasonDaySchedule season = SeasonDaySchedule.fromYaml(section.getConfigurationSection("seasonDaySchedule"));
        ScheduledEvent event = new ScheduledEvent(type, triggerType, calendar, season, section.getConfigurationSection("statEffects"));
        event.setActive(section.getBoolean("active", true));
        return event;
    }

    public void toYaml(ConfigurationSection section) {
        section.set("name", name);
        section.set("description", description);
        section.set("type", type.getId());
        section.set("triggerType", triggerType.name());
        if (calendarSchedule != null) {
            calendarSchedule.toYaml(section.createSection("calendarSchedule"));
        }
        if (seasonDaySchedule != null) {
            seasonDaySchedule.toYaml(section.createSection("seasonDaySchedule"));
        }
        section.set("active", active);
    }
}