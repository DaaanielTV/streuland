package de.streuland.transaction;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for active world transactions.
 */
public class TransactionManager {
    private final JavaPlugin plugin;
    private final Map<String, WorldTransaction> activeTransactions = new ConcurrentHashMap<>();

    public TransactionManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public WorldTransaction begin(String id, Location origin) {
        WorldTransaction tx = new WorldTransaction(id, origin);
        WorldTransaction existing = activeTransactions.putIfAbsent(id, tx);
        if (existing != null) {
            throw new IllegalArgumentException("Transaction already exists: " + id);
        }
        return tx;
    }

    public Optional<WorldTransaction> get(String id) {
        return Optional.ofNullable(activeTransactions.get(id));
    }

    public boolean rollback(String id) {
        WorldTransaction tx = activeTransactions.remove(id);
        if (tx == null) {
            return false;
        }
        runOnMainThread(tx::rollback);
        return true;
    }

    public void close(String id) {
        activeTransactions.remove(id);
    }

    private void runOnMainThread(Runnable action) {
        if (Bukkit.isPrimaryThread()) {
            action.run();
            return;
        }
        try {
            Bukkit.getScheduler().callSyncMethod(plugin, new Callable<Void>() {
                @Override
                public Void call() {
                    action.run();
                    return null;
                }
            }).get();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to execute transaction mutation on main thread", e);
        }
    }
}
