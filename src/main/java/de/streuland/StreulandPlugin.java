package de.streuland;

import de.streuland.command.PlotCommandExecutor;
import de.streuland.command.DistrictCommandExecutor;
import de.streuland.district.DistrictManager;
import de.streuland.district.DistrictProgressService;
import de.streuland.listener.ProtectionListener;
import de.streuland.path.PathGenerator;
import de.streuland.plot.PlotManager;
import de.streuland.plot.PlotStorage;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Streuland Main Plugin Class
 * 
 * Initializes all components and manages the plugin lifecycle.
 */
public class StreulandPlugin extends JavaPlugin {
    private PlotManager plotManager;
    private PlotStorage plotStorage;
    private PathGenerator pathGenerator;
    private ProtectionListener protectionListener;
    private DistrictManager districtManager;
    private DistrictProgressService districtProgressService;
    
    @Override
    public void onEnable() {
        getLogger().info("===============================================");
        getLogger().info("Streuland Plot Plugin v1.0.0 - Enabling");
        getLogger().info("===============================================");
        
        // Load configuration
        saveDefaultConfig();
        
        try {
            // Initialize components in dependency order
            plotStorage = new PlotStorage(this);
            getLogger().info("✓ PlotStorage initialized");
            
            plotManager = new PlotManager(this, plotStorage);
            getLogger().info("✓ PlotManager initialized");
            
            pathGenerator = new PathGenerator(this, plotManager);
            getLogger().info("✓ PathGenerator initialized");
            
            protectionListener = new ProtectionListener(this, plotManager);
            getLogger().info("✓ ProtectionListener registered");

            districtManager = new DistrictManager(this, plotManager);
            districtProgressService = new DistrictProgressService(this, plotManager, districtManager);
            getServer().getPluginManager().registerEvents(districtManager, this);
            getServer().getPluginManager().registerEvents(districtProgressService, this);
            getLogger().info("✓ District system initialized");
            
            // Register command
            PlotCommandExecutor commandExecutor = new PlotCommandExecutor(this, plotManager, pathGenerator);
            if (getCommand("plot") == null) {
                throw new IllegalStateException("Command 'plot' is not defined in plugin.yml");
            }
            getCommand("plot").setExecutor(commandExecutor);
            getCommand("district").setExecutor(new DistrictCommandExecutor(plotManager, districtManager));
            getLogger().info("✓ Commands registered");
            
            getLogger().info("===============================================");
            getLogger().info("Streuland enabled successfully!");
            getLogger().info("Loaded " + plotStorage.getAllPlots().size() + " plots");
            getLogger().info("===============================================");
            
        } catch (Exception e) {
            getLogger().severe("Failed to initialize Streuland!");
            e.printStackTrace();
            setEnabled(false);
        }
    }
    
    @Override
    public void onDisable() {
        getLogger().info("Streuland disabled");
    }
    
    public PlotManager getPlotManager() {
        return plotManager;
    }
    
    public PlotStorage getPlotStorage() {
        return plotStorage;
    }
    
    public PathGenerator getPathGenerator() {
        return pathGenerator;
    }
}
