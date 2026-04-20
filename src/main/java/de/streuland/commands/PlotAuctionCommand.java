package de.streuland.commands;

import de.streuland.market.AuctionListing;
import de.streuland.market.PlotAuctionService;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import org.bukkit.entity.Player;

import java.util.Locale;

public class PlotAuctionCommand {
    private final PlotAuctionService auctionService;
    private final PlotManager plotManager;

    public PlotAuctionCommand(PlotAuctionService auctionService, PlotManager plotManager) {
        this.auctionService = auctionService;
        this.plotManager = plotManager;
    }

    public boolean handle(Player player, String[] args) {
        if (args.length < 2) {
            sendHelp(player);
            return true;
        }

        String subcommand = args[1].toLowerCase(Locale.ROOT);

        return switch (subcommand) {
            case "create" -> handleCreate(player, args);
            case "cancel" -> handleCancel(player, args);
            case "buy", "accept" -> handleAccept(player, args);
            case "list" -> handleList(player, args);
            case "info" -> handleInfo(player, args);
            default -> {
                player.sendMessage("§cUnbekannter Befehl. Nutze /plot auction help");
                yield true;
            }
        };
    }

    private boolean handleCreate(Player player, String[] args) {
        if (args.length < 6) {
            player.sendMessage("§cVerwendung: /plot auction create <plotId> <startPrice> <floorPrice> <stunden>");
            return true;
        }

        Plot plot = plotManager.getPlotById(args[2]);
        if (plot == null) {
            player.sendMessage("§cPlot nicht gefunden: " + args[2]);
            return true;
        }

        double startPrice;
        double floorPrice;
        long hours;

        try {
            startPrice = Double.parseDouble(args[3]);
            floorPrice = Double.parseDouble(args[4]);
            hours = Long.parseLong(args[5]);
        } catch (NumberFormatException e) {
            player.sendMessage("§cUngültige Zahlen. usage: startPrice floorPrice stunden");
            return true;
        }

        PlotAuctionService.CreateResult result = auctionService.createAuction(
                plot, player.getUniqueId(), startPrice, floorPrice, hours
        );

        player.sendMessage(switch (result) {
            case OK -> "§aAuktion erstellt! Start: " + fmt(startPrice) + ", Mindest: " + fmt(floorPrice) + ", " + hours + "h";
            case NOT_OWNER -> "§cDu bist nicht der Besitzer.";
            case INVALID_PRICE -> "§cUngültiger Preis.";
            case FLOOR_HIGHER_THAN_START -> "§cMindestpreis muss niedriger als Startpreis sein.";
            case ALREADY_LISTED -> "§cPlot bereits in einer Auktion.";
            case PLOT_PROTECTED -> "§cDieser Plot ist geschützt.";
            case INVALID_DURATION -> "§cDauer muss zwischen 1-168 Stunden liegen.";
        });
        return true;
    }

    private boolean handleCancel(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cVerwendung: /plot auction cancel <plotId>");
            return true;
        }

        Plot plot = plotManager.getPlotById(args[2]);
        if (plot == null) {
            player.sendMessage("§cPlot nicht gefunden: " + args[2]);
            return true;
        }

        PlotAuctionService.CancelResult result = auctionService.cancelAuction(
                plot.getPlotId(), player.getUniqueId()
        );

        player.sendMessage(switch (result) {
            case OK -> "§aAuktion abgebrochen.";
            case NOT_FOUND -> "§cKeine aktive Auktion für diesen Plot.";
            case NOT_OWNER -> "§cDu bist nicht der Verkäufer.";
            case ALREADY_SOLD -> "§cPlot wurde bereits verkauft.";
        });
        return true;
    }

    private boolean handleAccept(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cVerwendung: /plot auction buy <plotId>");
            return true;
        }

        Plot plot = plotManager.getPlotById(args[2]);
        if (plot == null) {
            AuctionListing auction = auctionService.getAuction(args[2]);
            if (auction == null) {
                player.sendMessage("§cPlot/Auktion nicht gefunden: " + args[2]);
                return true;
            }
            plot = plotManager.getPlotById(auction.getPlotId());
        }

        if (plot == null) {
            player.sendMessage("§cPlot nicht gefunden.");
            return true;
        }

        PlotAuctionService.AcceptOutcome outcome = auctionService.acceptAuction(
                player.getUniqueId(), plot.getPlotId()
        );

        player.sendMessage(switch (outcome.result()) {
            case OK -> "§aGekauft für " + fmt(outcome.chargedAmount()) + "! §7(Steuer: " + fmt(outcome.taxAmount()) + ")";
            case NOT_FOUND -> "§cKeine aktive Auktion.";
            case OWN_AUCTION -> "§cDas ist deine eigene Auktion.";
            case SELLER_SAME_AS_BUYER -> "§cDu kannst nicht dein eigenes Plot kaufen.";
            case INSUFFICIENT_FUNDS -> "§cNicht genug Geld. Benötigt: " + fmt(outcome.chargedAmount());
            case PAYMENT_FAILED -> "§cZahlung fehlgeschlagen.";
            case TRANSFER_FAILED -> "§cPlot-Transfer fehlgeschlagen.";
            case AUCTION_ENDED -> "§cAuktion ist beendet.";
        });
        return true;
    }

    private boolean handleList(Player player, String[] args) {
        var auctions = auctionService.getActiveAuctions();

        if (auctions.isEmpty()) {
            player.sendMessage("§7Keine aktiven Auktionen.");
            return true;
        }

        player.sendMessage("§6=== Aktive Auktionen (" + auctions.size() + ") ===");
        for (AuctionListing auction : auctions) {
            double current = auction.calculatePrice();
            long remaining = Math.max(0, (auction.getEndsAt() - System.currentTimeMillis()) / 60000);
            player.sendMessage("§e" + auction.getPlotId() + " §7| §a" + fmt(current) +
                    " §7(Start: " + fmt(auction.getStartingPrice()) + ") §7| §e" + remaining + "min");
        }
        return true;
    }

    private boolean handleInfo(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cVerwendung: /plot auction info <plotId>");
            return true;
        }

        AuctionListing auction = auctionService.getAuction(args[2]);
        if (auction == null) {
            player.sendMessage("§cKeine Auktion für diesen Plot.");
            return true;
        }

        double current = auction.calculatePrice();
        long remaining = (auction.getEndsAt() - System.currentTimeMillis()) / 60000;

        player.sendMessage("§6=== Auktion: " + auction.getPlotId() + " ===");
        player.sendMessage("§eStatus: §f" + auction.getStatus());
        player.sendMessage("§eAktueller Preis: §a" + fmt(current));
        player.sendMessage("§eStartpreis: §f" + fmt(auction.getStartingPrice()));
        player.sendMessage("§eMindestpreis: §f" + fmt(auction.getFloorPrice()));
        player.sendMessage("§eVerbleibend: §f" + remaining + " Minuten");
        player.sendMessage("§eTyp: §f" + auction.getDecayType());
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6=== Plot Auktion ===");
        player.sendMessage("§e/plot auction create <plot> <start> <floor> <h> §7- Auktion starten");
        player.sendMessage("§e/plot auction cancel <plot> §7- Auktion abbrechen");
        player.sendMessage("§e/plot auction buy <plot> §7- Zum aktuellen Preis kaufen");
        player.sendMessage("§e/plot auction list §7- Aktive Auktionen");
        player.sendMessage("§e/plot auction info <plot> §7- Auktions-Details");
    }

    private String fmt(double value) {
        return String.format(Locale.US, "%,.2f", value);
    }
}