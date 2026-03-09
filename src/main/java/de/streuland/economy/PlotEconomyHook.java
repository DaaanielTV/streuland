package de.streuland.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class PlotEconomyHook {
    private final JavaPlugin plugin;
    private Economy economy;

    public PlotEconomyHook(JavaPlugin plugin) {
        this.plugin = plugin;
        init();
    }

    public PlotEconomyHook(Economy economy) {
        this.plugin = null;
        this.economy = economy;
    }

    private void init() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            economy = null;
            return;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        economy = rsp == null ? null : rsp.getProvider();
    }

    public boolean hasEconomy() {
        return economy != null;
    }

    public double getBalance(UUID uuid) {
        if (!hasEconomy()) {
            return 0D;
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        return economy.getBalance(player);
    }

    public boolean withdraw(UUID uuid, double amount) {
        if (!hasEconomy()) {
            return false;
        }
        EconomyResponse response = economy.withdrawPlayer(Bukkit.getOfflinePlayer(uuid), amount);
        return response.transactionSuccess();
    }

    public boolean deposit(UUID uuid, double amount) {
        if (!hasEconomy()) {
            return false;
        }
        EconomyResponse response = economy.depositPlayer(Bukkit.getOfflinePlayer(uuid), amount);
        return response.transactionSuccess();
    }
}
