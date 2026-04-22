package de.streuland.commands;

import de.streuland.event.EventScheduler;
import de.streuland.event.EventType;
import de.streuland.event.ScheduledEvent;
import org.bukkit.entity.Player;

import java.util.Locale;

public class AdminEventCommand {
    private final EventScheduler eventScheduler;

    public AdminEventCommand(EventScheduler eventScheduler) {
        this.eventScheduler = eventScheduler;
    }

    public boolean handle(Player player, String[] args) {
        if (args.length < 2) {
            sendHelp(player);
            return true;
        }

        String subcommand = args[1].toLowerCase(Locale.ROOT);

        return switch (subcommand) {
            case "list" -> handleList(player, args);
            case "add" -> handleAdd(player, args);
            case "remove" -> handleRemove(player, args);
            case "enable" -> handleEnable(player, args);
            case "disable" -> handleDisable(player, args);
            case "info" -> handleInfo(player, args);
            default -> {
                player.sendMessage("§cUnbekannter Befehl. Nutze /event help");
                yield true;
            }
        };
    }

    private boolean handleList(Player player, String[] args) {
        var events = eventScheduler.getAllEvents();
        if (events.isEmpty()) {
            player.sendMessage("§7Keine geplanten Events definiert.");
            return true;
        }

        player.sendMessage("§eGeplante Events (§b" + events.size() + "§e):");
        for (ScheduledEvent event : events) {
            String status = event.isActive() ? "§aAktiv" : "§cInaktiv";
            String typeName = event.getType().getDisplayName();
            String schedule = formatSchedule(event);
            player.sendMessage(" §7- " + typeName + " §8(" + status + "§8) " + schedule);
        }
        return true;
    }

    private String formatSchedule(ScheduledEvent event) {
        var cal = event.getCalendarSchedule();
        if (cal != null) {
            return String.format("§7jeden %d.%d um %02d:%02d", cal.getMonth(), cal.getDay(), cal.getHour(), cal.getMinute());
        }
        var season = event.getSeasonDaySchedule();
        if (season != null) {
            return String.format("§7Tag %d um %02d:00", season.getDayInSeason(), season.getHour());
        }
        return "§7Manuell";
    }

    private boolean handleAdd(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage("§cVerwendung: /event add <type> <once|recurring> <month> <day> <hour> <minute>");
            return true;
        }

        EventType type = EventType.fromId(args[2].toUpperCase(Locale.ROOT));
        if (type == null) {
            player.sendMessage("§cUnbekannter Event-Typ: " + args[2]);
            showEventTypes(player);
            return true;
        }

        EventScheduler.TriggerType triggerType;
        try {
            triggerType = EventScheduler.TriggerType.valueOf(args[3].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cUngültiger Trigger-Typ. Nutze §eonce§c oder §erecurring§c.");
            return true;
        }

        int month = 1, day = 1, hour = 12, minute = 0;
        if (args.length >= 8) {
            try {
                month = Integer.parseInt(args[4]);
                day = Integer.parseInt(args[5]);
                hour = Integer.parseInt(args[6]);
                minute = Integer.parseInt(args[7]);
            } catch (NumberFormatException e) {
                player.sendMessage("��cUngültige Zeitangabe.");
                return true;
            }
        }

        var schedule = new ScheduledEvent.CalendarSchedule(month, day, hour, minute);
        var event = eventScheduler.addEvent(type, triggerType, schedule, null, null);

        player.sendMessage("§aEvent §e" + type.getDisplayName() + " §ahinzugefügt!");
        return true;
    }

    private boolean handleRemove(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cVerwendung: /event remove <id>");
            return true;
        }

        try {
            java.util.UUID id = java.util.UUID.fromString(args[2]);
            eventScheduler.removeEvent(id);
            player.sendMessage("§aEvent entfernt.");
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cUngültige Event-ID.");
        }
        return true;
    }

    private boolean handleEnable(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cVerwendung: /event enable <id>");
            return true;
        }

        try {
            java.util.UUID id = java.util.UUID.fromString(args[2]);
            eventScheduler.setEventActive(id, true);
            player.sendMessage("§aEvent aktiviert.");
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cUngültige Event-ID.");
        }
        return true;
    }

    private boolean handleDisable(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cVerwendung: /event disable <id>");
            return true;
        }

        try {
            java.util.UUID id = java.util.UUID.fromString(args[2]);
            eventScheduler.setEventActive(id, false);
            player.sendMessage("§aEvent deaktiviert.");
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cUngültige Event-ID.");
        }
        return true;
    }

    private boolean handleInfo(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cVerwendung: /event info <id>");
            return true;
        }

        var events = eventScheduler.getAllEvents();
        try {
            var id = java.util.UUID.fromString(args[2]);
            for (ScheduledEvent event : events) {
                if (event.getId().equals(id)) {
                    player.sendMessage("§eEvent: §b" + event.getType().getDisplayName());
                    player.sendMessage(" §7Status: " + (event.isActive() ? "§aAktiv" : "§cInaktiv"));
                    player.sendMessage(" §7Trigger: " + event.getTriggerType());
                    player.sendMessage(" §7ID: " + event.getId());
                    return true;
                }
            }
            player.sendMessage("§cEvent nicht gefunden.");
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cUngültige Event-ID.");
        }
        return true;
    }

    private void showEventTypes(Player player) {
        player.sendMessage("§eVerfügbare Event-Typen:");
        for (EventType type : EventType.values()) {
            player.sendMessage(" §7- " + type.getId() + ": " + type.getDisplayName());
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage("§e/event admin§f - Geplante Events verwalten");
        player.sendMessage(" §b/event list§f - Alle Events anzeigen");
        player.sendMessage(" §b/event add <type> <once|recurring> [zeit]§f - Event hinzufügen");
        player.sendMessage(" §b/event remove <id>§f - Event entfernen");
        player.sendMessage(" §b/event enable <id>§f - Event aktivieren");
        player.sendMessage(" §b/event disable <id>§f - Event deaktivieren");
        player.sendMessage(" §b/event info <id>§f - Event-Details anzeigen");
    }
}