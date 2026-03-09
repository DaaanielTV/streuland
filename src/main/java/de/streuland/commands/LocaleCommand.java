package de.streuland.commands;

import de.streuland.i18n.MessageProvider;
import org.bukkit.entity.Player;

import java.util.Set;

public class LocaleCommand {
    private final MessageProvider messageProvider;

    public LocaleCommand(MessageProvider messageProvider) {
        this.messageProvider = messageProvider;
    }

    public boolean handleLang(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageProvider.t(player, "plot.lang.usage"));
            return true;
        }
        if (!player.hasPermission("streuland.plot.lang")) {
            player.sendMessage(messageProvider.t(player, "error.no_permission"));
            return true;
        }
        String locale = args[1].toLowerCase();
        Set<String> available = messageProvider.getAvailableLocales();
        if (!available.contains(locale)) {
            player.sendMessage(messageProvider.t(player, "plot.lang.invalid", locale, String.join(", ", available)));
            return true;
        }
        messageProvider.setPlayerLocale(player.getUniqueId(), locale);
        player.sendMessage(messageProvider.t(player, "plot.lang.set", locale));
        return true;
    }

    public boolean handleServerLang(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageProvider.t(player, "plot.serverlang.usage"));
            return true;
        }
        if (!player.hasPermission("streuland.plot.serverlang")) {
            player.sendMessage(messageProvider.t(player, "error.no_permission"));
            return true;
        }
        String locale = args[1].toLowerCase();
        Set<String> available = messageProvider.getAvailableLocales();
        if (!available.contains(locale)) {
            player.sendMessage(messageProvider.t(player, "plot.serverlang.invalid", locale, String.join(", ", available)));
            return true;
        }
        messageProvider.setServerLocale(locale);
        player.sendMessage(messageProvider.t(player, "plot.serverlang.set", locale));
        return true;
    }
}
