package de.streuland;

import de.streuland.admin.AdminPlotService;
import de.streuland.admin.BlockChangeLogger;
import de.streuland.admin.DailyPlotBackupService;
import de.streuland.command.PlotCommandExecutor;
import de.streuland.command.DistrictCommandExecutor;
import de.streuland.analytics.InMemoryPlotAnalyticsService;
import de.streuland.district.DistrictClusterService;
import de.streuland.district.DistrictManager;
import de.streuland.district.DistrictProgressService;
import de.streuland.dashboard.PlotAnalyticsExporter;
import de.streuland.quest.QuestService;
import de.streuland.quest.QuestTracker;
import de.streuland.dashboard.RestApiController;
import de.streuland.listener.BlockChangeListener;
import de.streuland.listener.ProtectionListener;
import de.streuland.neighborhood.NeighborhoodService;
import de.streuland.neighborhood.ResourceSyncScheduler;
import de.streuland.path.PathGenerator;
import de.streuland.plot.PlotManager;
import de.streuland.plot.PlotStorage;
import de.streuland.plot.snapshot.SnapshotManager;
import de.streuland.plot.snapshot.SnapshotStorage;
import de.streuland.plot.skin.PlotSkinService;
import de.streuland.plot.biome.BiomeEffectScheduler;
import de.streuland.plot.biome.BiomeBonusService;
import de.streuland.plot.market.PlotMarketService;
import de.streuland.rules.DefaultPlotLevelProvider;
import de.streuland.rules.ExampleRules;
import de.streuland.rules.RuleEngine;
import de.streuland.rules.listener.RuleListener;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import net.milkbowl.vault.economy.Economy;

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
    private BlockChangeListener blockChangeListener;
    private SnapshotStorage snapshotStorage;
    private SnapshotManager snapshotManager;
    private RuleEngine ruleEngine;
    private RuleListener ruleListener;
    private PlotSkinService plotSkinService;
    private BiomeBonusService biomeBonusService;
    private BiomeEffectScheduler biomeEffectScheduler;
    private DistrictManager districtManager;
    private DistrictProgressService districtProgressService;
    private InMemoryPlotAnalyticsService analyticsService;
    private NeighborhoodService neighborhoodService;
    private ResourceSyncScheduler resourceSyncScheduler;
    private RestApiController restApiController;
    private QuestService questService;
    private QuestTracker questTracker;
    private PlotMarketService plotMarketService;
    private Economy economy;
    private BlockChangeLogger blockChangeLogger;
    private AdminPlotService adminPlotService;
    private DailyPlotBackupService dailyPlotBackupService;
    
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

            snapshotStorage = new SnapshotStorage(this);
            getLogger().info("✓ SnapshotStorage initialized");

            snapshotManager = new SnapshotManager(this, plotManager, snapshotStorage);
            getLogger().info("✓ SnapshotManager initialized");

            ruleEngine = new RuleEngine(plotManager, new DefaultPlotLevelProvider());
            ruleEngine.registerProvider(new ExampleRules());
            biomeBonusService = new BiomeBonusService(plotManager, getConfig());
            ruleEngine.setBiomeBonusService(biomeBonusService);
            ruleEngine.reload();
            getLogger().info("✓ RuleEngine initialized");

            plotSkinService = new PlotSkinService(this, plotStorage);
            plotSkinService.start();
            getLogger().info("✓ PlotSkinService initialized");

            biomeEffectScheduler = new BiomeEffectScheduler(this, plotManager, biomeBonusService);
            biomeEffectScheduler.start();
            getLogger().info("✓ BiomeEffectScheduler initialized");

            analyticsService = new InMemoryPlotAnalyticsService();
            blockChangeLogger = new BlockChangeLogger(this, plotManager);
            adminPlotService = new AdminPlotService(plotManager, snapshotManager, blockChangeLogger);

            protectionListener = new ProtectionListener(this, plotManager);
            blockChangeListener = new BlockChangeListener(this, plotManager, blockChangeLogger, analyticsService);
            getLogger().info("✓ Protection/BlockChange listeners registered");

            ruleListener = new RuleListener(this, ruleEngine, biomeBonusService);
            getLogger().info("✓ RuleListener registered");

            setupEconomy();
            if (economy == null) {
                getLogger().warning("Vault Economy provider not found. Plot market will be disabled.");
            } else {
                getLogger().info("✓ Vault economy connected: " + economy.getName());
            }
            questService = new QuestService(this, plotStorage, ruleEngine);
            getLogger().info("✓ QuestService initialized");
            neighborhoodService = new NeighborhoodService(this, plotManager, new DistrictClusterService(), analyticsService);
            resourceSyncScheduler = new ResourceSyncScheduler(this, neighborhoodService);
            resourceSyncScheduler.start();
            getLogger().info("✓ Neighborhood system initialized");

            // Register plot command
            if (getCommand("plot") == null) {
                throw new IllegalStateException("Command 'plot' is not defined in plugin.yml");
            }
            
            // Initialize district system
            districtManager = new DistrictManager(this, plotManager);
            districtProgressService = new DistrictProgressService(this, plotManager, districtManager);
            getServer().getPluginManager().registerEvents(districtManager, this);
            getServer().getPluginManager().registerEvents(districtProgressService, this);
            questTracker = new QuestTracker(plotManager, districtManager, questService);
            getServer().getPluginManager().registerEvents(questTracker, this);
            getLogger().info("✓ District system initialized");

            plotMarketService = new PlotMarketService(this, plotManager, districtManager, analyticsService, economy);

            PlotCommandExecutor commandExecutor = new PlotCommandExecutor(this, plotManager, pathGenerator, snapshotManager, ruleEngine, plotSkinService, biomeBonusService, neighborhoodService, questService, questTracker, plotMarketService, adminPlotService, analyticsService);
            getCommand("plot").setExecutor(commandExecutor);

            // Register district command
            getCommand("district").setExecutor(new DistrictCommandExecutor(plotManager, districtManager));
            getLogger().info("✓ Commands registered");

            dailyPlotBackupService = new DailyPlotBackupService(this, plotManager, snapshotManager);
            dailyPlotBackupService.start();
            getLogger().info("✓ Daily backup scheduler initialized");

            PlotAnalyticsExporter analyticsExporter = new PlotAnalyticsExporter(analyticsService);
            restApiController = new RestApiController(this, plotManager, districtManager, analyticsService, analyticsExporter);
            restApiController.start();
            getLogger().info("✓ Dashboard API initialized");
            
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
    
    private void setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            economy = null;
            return;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        economy = rsp == null ? null : rsp.getProvider();
    }

    @Override
    public void onDisable() {
        if (plotSkinService != null) {
            plotSkinService.stop();
        }
        if (biomeEffectScheduler != null) {
            biomeEffectScheduler.stop();
        }
        if (resourceSyncScheduler != null) {
            resourceSyncScheduler.stop();
        }
        if (restApiController != null) {
            restApiController.stop();
        }
        if (dailyPlotBackupService != null) {
            dailyPlotBackupService.stop();
        }
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
