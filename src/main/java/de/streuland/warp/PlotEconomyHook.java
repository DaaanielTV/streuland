package de.streuland.warp;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public class PlotEconomyHook {
    private final Economy economy;

    public PlotEconomyHook(Economy economy) {
        this.economy = economy;
    }

    public boolean isAvailable() {
        return economy != null;
    }

    public boolean charge(Player player, double amount) {
        if (amount <= 0 || economy == null) {
            return true;
        }
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response.transactionSuccess();
    }

    public void payout(OfflinePlayer target, double amount) {
        if (amount <= 0 || economy == null || target == null) {
            return;
        }
        economy.depositPlayer(target, amount);
    }
}
