package de.streuland.commands;

import de.streuland.market.MarketManager;
import de.streuland.market.PlotSale;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.UUID;

public class PlotMarketCommand {
    private final PlotManager plotManager;
    private final MarketManager marketManager;

    public PlotMarketCommand(PlotManager plotManager, MarketManager marketManager) {
        this.plotManager = plotManager;
        this.marketManager = marketManager;
    }

    public boolean handle(Player player, String[] args, boolean economyEnabled) {
        if (!economyEnabled) {
            player.sendMessage("§cEconomy disabled (Vault not available).");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage("§cUsage: /plot <sell|buy|auction|bid>");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "sell":
                if (!player.hasPermission("streuland.market.sell") && !player.hasPermission("streuland.market.*")) { player.sendMessage("§cKeine Rechte."); return true; }
                return sell(player, args);
            case "buy":
                if (!player.hasPermission("streuland.market.buy") && !player.hasPermission("streuland.market.*")) { player.sendMessage("§cKeine Rechte."); return true; }
                return buy(player, args);
            case "auction":
                if (!player.hasPermission("streuland.market.auction") && !player.hasPermission("streuland.market.*")) { player.sendMessage("§cKeine Rechte."); return true; }
                return auction(player, args);
            case "bid":
                if (!player.hasPermission("streuland.market.bid") && !player.hasPermission("streuland.market.*")) { player.sendMessage("§cKeine Rechte."); return true; }
                return bid(player, args);
            default:
                return false;
        }
    }

    private boolean sell(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /plot sell <price>");
            return true;
        }
        double price = parseDouble(args[1]);
        if (price <= 0D) {
            player.sendMessage("§cInvalid price.");
            return true;
        }
        Plot plot = currentOwnedPlot(player);
        if (plot == null) {
            player.sendMessage("§cYou must stand on your own plot.");
            return true;
        }
        Integer plotId = parsePlotId(plot.getPlotId());
        if (plotId == null) {
            player.sendMessage("§cPlot ID is not market-compatible.");
            return true;
        }
        marketManager.createSale(new PlotSale(plotId, player.getUniqueId(), price, PlotSale.SaleType.FIXED, 0L, 0D, null));
        player.sendMessage("§aPlot listed for sale: #" + plotId + " @ " + price);
        return true;
    }

    private boolean buy(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /plot buy <plotId>");
            return true;
        }
        int plotId = Integer.parseInt(args[1]);
        boolean success = marketManager.buyPlot(player.getUniqueId(), plotId);
        player.sendMessage(success ? "§aPurchase completed." : "§cPurchase failed.");
        return true;
    }

    private boolean auction(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: /plot auction <price> <durationMinutes>");
            return true;
        }
        double price = parseDouble(args[1]);
        long minutes = Long.parseLong(args[2]);
        if (price <= 0D || minutes <= 0) {
            player.sendMessage("§cInvalid auction arguments.");
            return true;
        }
        Plot plot = currentOwnedPlot(player);
        if (plot == null) {
            player.sendMessage("§cYou must stand on your own plot.");
            return true;
        }
        Integer plotId = parsePlotId(plot.getPlotId());
        if (plotId == null) {
            player.sendMessage("§cPlot ID is not market-compatible.");
            return true;
        }
        long ends = System.currentTimeMillis() + (minutes * 60_000L);
        marketManager.createSale(new PlotSale(plotId, player.getUniqueId(), price, PlotSale.SaleType.AUCTION, ends, 0D, null));
        player.sendMessage("§aAuction started for plot #" + plotId + ".");
        return true;
    }

    private boolean bid(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: /plot bid <plotId> <amount>");
            return true;
        }
        int plotId = Integer.parseInt(args[1]);
        double amount = parseDouble(args[2]);
        boolean success = marketManager.placeBid(player.getUniqueId(), plotId, amount);
        player.sendMessage(success ? "§aBid placed." : "§cBid rejected.");
        return true;
    }

    private Plot currentOwnedPlot(Player player) {
        Plot plot = plotManager.getPlotAt(player.getWorld(), player.getLocation().getBlockX(), player.getLocation().getBlockZ());
        UUID owner = plot == null ? null : plot.getOwner();
        return owner != null && owner.equals(player.getUniqueId()) ? plot : null;
    }

    private Integer parsePlotId(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("^.*_(\\d+)$", "$1");
        if (!digits.matches("\\d+")) {
            return null;
        }
        return Integer.parseInt(digits);
    }

    private double parseDouble(String raw) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return -1D;
        }
    }
}
