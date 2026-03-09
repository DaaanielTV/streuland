package de.streuland.commands;

import de.streuland.schematic.Schematic;
import de.streuland.schematic.SchematicLoader;
import de.streuland.schematic.SchematicPaster;
import de.streuland.schematic.SchematicPreview;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlotSchematicCommand {
    private final SchematicLoader loader;
    private final SchematicPreview preview;
    private final SchematicPaster paster;
    private final Map<UUID, PendingPaste> pendingPastes = new HashMap<>();

    public PlotSchematicCommand(SchematicLoader loader, SchematicPreview preview, SchematicPaster paster) {
        this.loader = loader;
        this.preview = preview;
        this.paster = paster;
    }

    public boolean handle(Player player, String[] args) {
        if (args.length < 1 || !"template".equalsIgnoreCase(args[0])) {
            return false;
        }

        if (args.length < 2) {
            player.sendMessage("§cVerwendung: /plot template <list|preview|paste>");
            return true;
        }

        String action = args[1].toLowerCase();
        if ("list".equals(action)) {
            List<String> templates = loader.listTemplates();
            player.sendMessage("§6Templates: §f" + (templates.isEmpty() ? "keine" : String.join(", ", templates)));
            return true;
        }

        if (args.length < 3) {
            player.sendMessage("§cVerwendung: /plot template " + action + " <name>");
            return true;
        }

        String templateName = args[2];
        Schematic schematic;
        try {
            schematic = loader.load(templateName);
        } catch (Exception e) {
            player.sendMessage("§cTemplate konnte nicht geladen werden: " + e.getMessage());
            return true;
        }

        Location center = player.getLocation();
        if ("preview".equals(action)) {
            preview.showPreview(player, center, schematic);
            player.sendMessage("§aPreview aktiv für " + schematic.getName() + ".");
            return true;
        }

        if ("paste".equals(action)) {
            pendingPastes.put(player.getUniqueId(), new PendingPaste(schematic, center));
            player.sendMessage("§ePaste bestätigen: /plot confirm");
            return true;
        }

        player.sendMessage("§cUnbekannte template Aktion.");
        return true;
    }

    public boolean confirm(Player player) {
        PendingPaste pending = pendingPastes.remove(player.getUniqueId());
        if (pending == null) {
            return false;
        }
        preview.hidePreview(player);
        paster.pasteAsync(pending.schematic, pending.center, player.getUniqueId())
                .thenAccept(success -> player.sendMessage(success ? "§aTemplate erfolgreich eingefügt." : "§cTemplate Paste fehlgeschlagen."));
        return true;
    }

    public void hidePreview(Player player) {
        preview.hidePreview(player);
    }

    private static class PendingPaste {
        private final Schematic schematic;
        private final Location center;

        private PendingPaste(Schematic schematic, Location center) {
            this.schematic = schematic;
            this.center = center.clone();
        }
    }
}
