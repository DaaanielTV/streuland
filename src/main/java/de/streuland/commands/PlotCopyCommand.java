package de.streuland.commands;

import de.streuland.plot.PlotManager;
import de.streuland.plot.template.PlotTemplate;
import de.streuland.plot.template.PlotTemplateRegistry;
import de.streuland.schematic.Schematic;
import de.streuland.schematic.SchematicPaster;
import de.streuland.schematic.SchematicPreview;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlotCopyCommand {
    private final PlotTemplateRegistry templateRegistry;
    private final SchematicPaster paster;
    private final SchematicPreview preview;
    private final PlotManager plotManager;
    private final Map<UUID, PendingCopy> pendingCopies = new ConcurrentHashMap<>();

    public PlotCopyCommand(PlotTemplateRegistry templateRegistry, SchematicPaster paster, SchematicPreview preview, PlotManager plotManager) {
        this.templateRegistry = templateRegistry;
        this.paster = paster;
        this.preview = preview;
        this.plotManager = plotManager;
    }

    public boolean handle(Player player, String[] args) {
        if (args.length >= 1 && "templates".equalsIgnoreCase(args[0])) {
            showTemplateGui(player);
            return true;
        }
        if (args.length < 1 || !"copy".equalsIgnoreCase(args[0])) {
            return false;
        }

        if (args.length < 2) {
            showUsage(player);
            return true;
        }

        String action = args[1].toLowerCase();
        if ("list".equals(action)) {
            showList(player);
            return true;
        }

        if ("preview".equals(action)) {
            handlePreview(player, args);
            return true;
        }

        if ("apply".equals(action)) {
            handleApply(player, args);
            return true;
        }

        if (templateRegistry.exists(action)) {
            handleQuickCopy(player, action);
            return true;
        }

        showUsage(player);
        return true;
    }

    private void showUsage(Player player) {
        player.sendMessage("§6=== Schnellbau Templates ===");
        player.sendMessage("§e/plot copy list§f - Verfügbare Templates anzeigen");
        player.sendMessage("§e/plot copy <name>§f - Template quick anwenden");
        player.sendMessage("§e/plot copy preview <name>§f - Vorschau anzeigen");
        player.sendMessage("§e/plot copy apply <name>§f - Template bestätigen");
    }

    private void showList(Player player) {
        List<PlotTemplate> templates = templateRegistry.list();
        if (templates.isEmpty()) {
            player.sendMessage("§cKeine Templates verfügbar.");
            return;
        }

        player.sendMessage("§6=== Verfügbare Schnellbau Designs ===");
        for (PlotTemplate template : templates) {
            String costText = template.hasCost() ? " §e" + template.getCost() + "§f§l$" : " §aKOSTENLOS";
            player.sendMessage("§e" + template.getName() + "§f - " + template.getDescription() + costText);
            player.sendMessage("§7  §o/plot copy " + template.getId());
        }
        player.sendMessage("§7Verwende §e/plot copy <name>§7 zum schnellen Kopieren.");
    }

    private void handleQuickCopy(Player player, String templateId) {
        PlotTemplate template = templateRegistry.get(templateId);
        if (template == null) {
            player.sendMessage("§cTemplate nicht gefunden.");
            return;
        }

        if (!plotManager.isOnOwnPlot(player)) {
            player.sendMessage("§cDu musst auf deinem eigenen Plot stehen!");
            return;
        }

        Schematic schematic = templateRegistry.loadSchematic(template);
        Location center = player.getLocation();

        if (schematic != null) {
            preview.showPreview(player, center, schematic);
            pendingCopies.put(player.getUniqueId(), new PendingCopy(template, center.clone()));
            player.sendMessage("§eTemplate '" + template.getName() + "' als Vorschau.");
            player.sendMessage("§7Bestätigen mit §e/plot copy confirm§7 oder §e/plot cancel§7 zum Abbrechen.");
        } else {
            player.sendMessage("§cTemplate konnte nicht geladen werden.");
        }
    }

    private void handlePreview(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cVerwendung: /plot copy preview <name>");
            return;
        }

        String templateId = args[2];
        PlotTemplate template = templateRegistry.get(templateId);
        if (template == null) {
            player.sendMessage("§cTemplate nicht gefunden: " + templateId);
            return;
        }

        Schematic schematic = templateRegistry.loadSchematic(template);
        if (schematic == null) {
            player.sendMessage("§cSchematic nicht gefunden: " + template.getSchematicName());
            return;
        }

        preview.showPreview(player, player.getLocation(), schematic);
        pendingCopies.put(player.getUniqueId(), new PendingCopy(template, player.getLocation().clone()));
        player.sendMessage("§aVorschau aktiv für §e" + template.getName() + "§a.");
        player.sendMessage("§7Bestätigen mit §e/plot copy apply§7.");
    }

    private void handleApply(Player player, String[] args) {
        PendingCopy pending = pendingCopies.get(player.getUniqueId());
        if (pending == null) {
            player.sendMessage("§cKeine Vorschau aktiv. Verwende §e/plot copy preview <name>§c.");
            return;
        }

        preview.hidePreview(player);
        PlotTemplate template = pending.template;

        Schematic schematic = templateRegistry.loadSchematic(template);
        if (schematic == null) {
            player.sendMessage("§cTemplate konnte nicht geladen werden.");
            pendingCopies.remove(player.getUniqueId());
            return;
        }

        paster.pasteAsync(schematic, pending.center, player.getUniqueId())
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage("§aSchnellbau '" + template.getName() + "' erfolgreich eingefügt!");
                    } else {
                        player.sendMessage("§cEinfügen fehlgeschlagen.");
                    }
                });

        pendingCopies.remove(player.getUniqueId());
    }

    public boolean confirm(Player player) {
        PendingCopy pending = pendingCopies.get(player.getUniqueId());
        if (pending == null) {
            return false;
        }

        preview.hidePreview(player);
        PlotTemplate template = pending.template;

        Schematic schematic = templateRegistry.loadSchematic(template);
        if (schematic == null) {
            player.sendMessage("§cTemplate konnte nicht geladen werden.");
            pendingCopies.remove(player.getUniqueId());
            return true;
        }

        paster.pasteAsync(schematic, pending.center, player.getUniqueId())
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage("§aSchnellbau '" + template.getName() + "' erfolgreich eingefügt!");
                    } else {
                        player.sendMessage("§cEinfügen fehlgeschlagen.");
                    }
                });

        pendingCopies.remove(player.getUniqueId());
        return true;
    }

    public boolean cancel(Player player) {
        PendingCopy pending = pendingCopies.remove(player.getUniqueId());
        if (pending == null) {
            return false;
        }
        preview.hidePreview(player);
        player.sendMessage("§cVorschau abgebrochen.");
        return true;
    }

    public void hidePreview(Player player) {
        preview.hidePreview(player);
        pendingCopies.remove(player.getUniqueId());
    }

    private void showTemplateGui(Player player) {
        List<PlotTemplate> templates = templateRegistry.list();
        if (templates.isEmpty()) {
            player.sendMessage("§cKeine Templates verfügbar.");
            return;
        }

        player.sendMessage("§6=== Schnellbau Templates Shop ===");
        int index = 1;
        for (PlotTemplate template : templates) {
            String costText = template.hasCost() ? " §e" + template.getCost() + "$" : " §aKOSTENLOS";
            String item = template.getIcon() != null ? template.getIcon().name() : "BLOCK";
            player.sendMessage("§e[" + index + "] §f" + template.getName() + " §7(" + item + ") " + costText);
            index++;
        }
        player.sendMessage("§7Klicke auf eine Nummer oder nutze §e/plot copy <name>§7.");
    }

    public List<String> getTabCompletions(Player player, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 2) {
            completions.add("list");
            completions.add("preview");
            completions.add("apply");
            for (PlotTemplate template : templateRegistry.list()) {
                completions.add(template.getId());
            }
        } else if (args.length == 3 && "preview".equalsIgnoreCase(args[1])) {
            for (PlotTemplate template : templateRegistry.list()) {
                completions.add(template.getId());
            }
        }
        return completions;
    }

    private static class PendingCopy {
        private final PlotTemplate template;
        private final Location center;

        private PendingCopy(PlotTemplate template, Location center) {
            this.template = template;
            this.center = center;
        }
    }
}