package de.streuland;

import de.streuland.admin.AdminPlotService;
import de.streuland.admin.BlockChangeLogger;
import de.streuland.admin.DailyPlotBackupService;
import de.streuland.backup.SnapshotService;
import de.streuland.commands.PlotPortalCommand;
import de.streuland.commands.PlotUpgradeCommand;
import de.streuland.commands.PlotSchematicCommand;
import de.streuland.command.PlotCommandExecutor;
import de.streuland.commands.PlotApprovalCommand;
import de.streuland.commands.PlotBackupCommand;
import de.streuland.command.DistrictCommandExecutor;
import de.streuland.command.StreulandCommandExecutor;
import de.streuland.history.PlotChangeJournal;
import de.streuland.history.JournalManager;
import de.streuland.commands.PlotHistoryCommand;
import de.streuland.admin.StreulandDiagnosticsService;
import de.streuland.analytics.InMemoryPlotAnalyticsService;
import de.streuland.district.DistrictClusterService;
import de.streuland.discord.DiscordNotifier;
import de.streuland.approval.PlotApprovalService;
import de.streuland.district.DistrictManager;
import de.streuland.district.DistrictProgressService;
import de.streuland.district.TraderNpcService;
import de.streuland.dashboard.DashboardDataExporter;
import de.streuland.quest.QuestService;
import de.streuland.quest.QuestTracker;
import de.streuland.weather.ParticleEffectScheduler;
import de.streuland.weather.SeasonalEffectListener;
import de.streuland.weather.SeasonalWeatherService;
import de.streuland.web.WebServer;
import de.streuland.dashboard.RestApiController;
import de.streuland.flags.PlotFlagManager;
import de.streuland.listener.BlockChangeListener;
import de.streuland.i18n.MessageProvider;
import de.streuland.listener.ProtectionListener;
import de.streuland.compat.WorldGuardCompat;
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
import de.streuland.plot.upgrade.DefaultPlotUpgradeService;
import de.streuland.plot.upgrade.PlotOwnershipResolver;
import de.streuland.plot.upgrade.PlotStorageBackedUpgradeStorage;
import de.streuland.plot.upgrade.PlotUpgradeService;
import de.streuland.plot.upgrade.PlotUpgradeTree;
import de.streuland.plot.upgrade.YamlPlotUpgradeCatalog;
import de.streuland.pricing.PricingEngine;
import de.streuland.commands.PlotPriceCommand;
import de.streuland.commands.PlotMarketCommand;
import de.streuland.economy.PlotEconomyHook;
import de.streuland.market.MarketManager;
import de.streuland.rules.DefaultPlotLevelProvider;
import de.streuland.rules.ExampleRules;
import de.streuland.rules.RuleEngine;
import de.streuland.rules.listener.RuleListener;
import de.streuland.warp.CooldownManager;
import de.streuland.warp.PlotEconomyHook;
import de.streuland.warp.PortalManager;
import de.streuland.transaction.TransactionManager;
import de.streuland.storage.SqlitePlotStorage;
import de.streuland.storage.YamlPlotStorage;
import de.streuland.schematic.SchematicLoader;
import de.streuland.schematic.SchematicPaster;
import de.streuland.schematic.SchematicPreview;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import net.milkbowl.vault.economy.Economy;

import java.nio.file.Path;
import java.io.File;

/**
 * Streuland Main Plugin Class
 * 
 * Initializes all components and manages the plugin lifecycle.
 */
public class StreulandPlugin extends JavaPlugin {
    private PlotManager plotManager;
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
    private PricingEngine pricingEngine;
    private PlotPriceCommand plotPriceCommand;
    private Economy economy;
    private PlotEconomyHook plotEconomyHook;
    private MarketManager marketManager;
    private PlotMarketCommand plotMarketCommand;
    private BlockChangeLogger blockChangeLogger;
    private AdminPlotService adminPlotService;
    private DailyPlotBackupService dailyPlotBackupService;
    private SnapshotService snapshotService;
    private TraderNpcService traderNpcService;
    private SeasonalWeatherService seasonalWeatherService;
    private PlotChangeJournal plotChangeJournal;
    private JournalManager journalManager;
    private ParticleEffectScheduler particleEffectScheduler;
    private PlotFlagManager plotFlagManager;
    private PlotUpgradeService plotUpgradeService;
    private WorldGuardCompat worldGuardCompat;
    
    @Override
    public void onEnable() {
        getLogger().info("===============================================");
        getLogger().info("Streuland Plot Plugin v1.0.0 - Enabling");
        getLogger().info("===============================================");
        
        // Load configuration
        saveDefaultConfig();
        initializeStorageAdapter();

        try {
            // Initialize components in dependency order
            messageProvider = new MessageProvider(this);
            plotManager = new PlotManager(this);
            getLogger().info("✓ PlotManager initialized");

            pathGenerator = new PathGenerator(this, plotManager);
            getLogger().info("✓ PathGenerator initialized");

            discordNotifier = new DiscordNotifier(this);
            plotApprovalService = new PlotApprovalService(this, plotManager, pathGenerator, discordNotifier);

            snapshotStorage = new SnapshotStorage(this);
            getLogger().info("✓ SnapshotStorage initialized");

            snapshotManager = new SnapshotManager(this, plotManager, snapshotStorage);
            snapshotService = new SnapshotService(this, plotManager, snapshotManager);
            getLogger().info("✓ SnapshotManager initialized");

            ruleEngine = new RuleEngine(plotManager, new DefaultPlotLevelProvider());
            ruleEngine.registerProvider(new ExampleRules());
            biomeBonusService = new BiomeBonusService(plotManager, getConfig());
            ruleEngine.setBiomeBonusService(biomeBonusService);
            ruleEngine.reload();
            getLogger().info("✓ RuleEngine initialized");

            plotSkinService = new PlotSkinService(this, plotManager.getStorage());
            plotSkinService.start();
            getLogger().info("✓ PlotSkinService initialized");

            biomeEffectScheduler = new BiomeEffectScheduler(this, plotManager, biomeBonusService);
            biomeEffectScheduler.start();
            getLogger().info("✓ BiomeEffectScheduler initialized");

            analyticsService = new InMemoryPlotAnalyticsService();
            seasonalWeatherService = new SeasonalWeatherService(this, analyticsService);
            seasonalWeatherService.start();
            getLogger().info("✓ SeasonalWeatherService initialized");

            particleEffectScheduler = new ParticleEffectScheduler(this, seasonalWeatherService);
            particleEffectScheduler.start();
            getLogger().info("✓ ParticleEffectScheduler initialized");

            transactionManager = new TransactionManager(this);
            getLogger().info("✓ TransactionManager initialized");

            blockChangeLogger = new BlockChangeLogger(this, plotManager);
            plotChangeJournal = new PlotChangeJournal(this, plotManager);
            journalManager = new JournalManager(this, plotChangeJournal);
            adminPlotService = new AdminPlotService(plotManager, snapshotManager, blockChangeLogger);

            plotFlagManager = new PlotFlagManager(plotManager);
            worldGuardCompat = new WorldGuardCompat(this, plotManager, plotFlagManager);
            plotFlagManager.registerHook(worldGuardCompat);
            worldGuardCompat.syncAllPlots();

            protectionListener = new ProtectionListener(this, plotManager, plotFlagManager);
            blockChangeListener = new BlockChangeListener(this, plotManager, blockChangeLogger, analyticsService);
            getLogger().info("✓ Protection/BlockChange listeners registered");

            ruleListener = new RuleListener(this, ruleEngine, biomeBonusService);
            getLogger().info("✓ RuleListener registered");

            getServer().getPluginManager().registerEvents(new SeasonalEffectListener(seasonalWeatherService, analyticsService), this);
            getLogger().info("✓ SeasonalEffectListener registered");

            setupEconomy();
            plotEconomyHook = new PlotEconomyHook(this);
            if (economy == null) {
                getLogger().warning("Vault Economy provider not found. Plot market will be disabled.");
            } else {
                getLogger().info("✓ Vault economy connected: " + economy.getName());
            }
            marketManager = new MarketManager(this, plotManager.getStorage(), plotEconomyHook);
            plotMarketCommand = new PlotMarketCommand(plotManager, marketManager);
            questService = new QuestService(this, plotManager.getStorage(), ruleEngine);
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
            traderNpcService = new TraderNpcService(this, plotManager, districtManager, analyticsService, economy);
            traderNpcService.start();
            getServer().getPluginManager().registerEvents(traderNpcService, this);
            questTracker = new QuestTracker(plotManager, districtManager, questService);
            getServer().getPluginManager().registerEvents(questTracker, this);
            getLogger().info("✓ District system initialized");

            plotMarketService = new PlotMarketService(this, plotManager, districtManager, analyticsService, economy, discordNotifier);

            PlotCommandExecutor commandExecutor = new PlotCommandExecutor(this, plotManager, pathGenerator, snapshotManager, ruleEngine, plotSkinService, biomeBonusService, neighborhoodService, questService, questTracker, plotMarketService, adminPlotService, analyticsService, traderNpcService, seasonalWeatherService, plotApprovalService, discordNotifier);
            pricingEngine = new PricingEngine(this, plotManager, neighborhoodService);
            plotPriceCommand = new PlotPriceCommand(pricingEngine);
            plotMarketService = new PlotMarketService(this, plotManager, districtManager, analyticsService, economy, pricingEngine);

            PlotCommandExecutor commandExecutor = new PlotCommandExecutor(this, plotManager, pathGenerator, snapshotManager, ruleEngine, plotSkinService, biomeBonusService, neighborhoodService, questService, questTracker, plotMarketService, plotPriceCommand, adminPlotService, analyticsService, traderNpcService, seasonalWeatherService);
            plotMarketService = new PlotMarketService(this, plotManager, districtManager, analyticsService, economy);
            portalManager = new PortalManager(this, plotManager, new PlotEconomyHook(economy), new CooldownManager());
            getServer().getPluginManager().registerEvents(portalManager, this);
            PlotPortalCommand plotPortalCommand = new PlotPortalCommand(plotManager, portalManager);

            PlotCommandExecutor commandExecutor = new PlotCommandExecutor(this, plotManager, pathGenerator, snapshotManager, ruleEngine, plotSkinService, biomeBonusService, neighborhoodService, questService, questTracker, plotMarketService, adminPlotService, analyticsService, traderNpcService, seasonalWeatherService, messageProvider);
            PlotCommandExecutor commandExecutor = new PlotCommandExecutor(this, plotManager, pathGenerator, snapshotManager, ruleEngine, plotSkinService, biomeBonusService, neighborhoodService, questService, questTracker, plotMarketService, adminPlotService, analyticsService, traderNpcService, seasonalWeatherService, plotPortalCommand);
            PlotCommandExecutor commandExecutor = new PlotCommandExecutor(this, plotManager, pathGenerator, snapshotManager, ruleEngine, plotSkinService, biomeBonusService, neighborhoodService, questService, questTracker, plotMarketService, adminPlotService, analyticsService, traderNpcService, seasonalWeatherService, new PlotHistoryCommand(journalManager));
            SchematicLoader schematicLoader = new SchematicLoader(this);
            SchematicPreview schematicPreview = new SchematicPreview();
            SchematicPaster schematicPaster = new SchematicPaster(this);
            PlotSchematicCommand plotSchematicCommand = new PlotSchematicCommand(schematicLoader, schematicPreview, schematicPaster);

            saveResource("plot-upgrades.yml", false);
            PlotUpgradeTree upgradeTree = YamlPlotUpgradeCatalog.load(new File(getDataFolder(), "plot-upgrades.yml"));
            PlotOwnershipResolver ownershipResolver = (plotId, playerId) -> {
                de.streuland.plot.Plot plot = plotManager.getStorage(plotManager.getWorldForPlot(plotId)).getPlot(plotId);
                return plot != null && playerId != null && playerId.equals(plot.getOwner());
            };
            plotUpgradeService = new DefaultPlotUpgradeService(
                    upgradeTree,
                    new PlotStorageBackedUpgradeStorage(plotManager.getStorage()),
                    plotEconomyHook,
                    ownershipResolver
            );
            PlotUpgradeCommand plotUpgradeCommand = new PlotUpgradeCommand(plotManager, plotUpgradeService);
            PlotCommandExecutor commandExecutor = new PlotCommandExecutor(this, plotManager, pathGenerator, snapshotManager, ruleEngine, plotSkinService, biomeBonusService, neighborhoodService, questService, questTracker, plotMarketService, adminPlotService, analyticsService, traderNpcService, seasonalWeatherService, plotFlagManager, plotUpgradeCommand);
            getCommand("plot").setExecutor(commandExecutor);
            getCommand("plot").setTabCompleter(commandExecutor);
            if (getCommand("plotapprove") != null) {
                getCommand("plotapprove").setExecutor(new PlotApprovalCommand(plotApprovalService));
            }

            // Register district command
            getCommand("district").setExecutor(new DistrictCommandExecutor(plotManager, districtManager, messageProvider));
            if (getCommand("streuland") != null) {
                getCommand("streuland").setExecutor(new StreulandCommandExecutor(plotManager, new StreulandDiagnosticsService(plotManager, getLogger())));
            }
            getLogger().info("✓ Commands registered");

            dailyPlotBackupService = new DailyPlotBackupService(this, snapshotService);
            dailyPlotBackupService.start();
            getLogger().info("✓ Daily backup scheduler initialized");

            DashboardDataExporter dataExporter = new DashboardDataExporter(plotManager.getStorage());
            restApiController = new RestApiController(this, plotManager, neighborhoodService, analyticsService, dataExporter, plotMarketService, plotApprovalService);
            restApiController.start();
            getLogger().info("✓ Dashboard API initialized");

            if (getConfig().getBoolean("web.enabled", false)) {
                String token = getConfig().getString("web.token", "");
                int webPort = getConfig().getInt("web.port", 8090);
                webServer = new WebServer("0.0.0.0", webPort, token, new WebServer.PlotGatewayAdapter(plotManager), getLogger());
                webServer.start();
                getLogger().info("✓ Admin web server listening on http://0.0.0.0:" + webPort);
            }
            
            getLogger().info("===============================================");
            getLogger().info("Streuland enabled successfully!");
            getLogger().info("Loaded " + plotManager.getAllPlots().size() + " plots");
            getLogger().info("===============================================");
            
        } catch (Exception e) {
            getLogger().severe("Failed to initialize Streuland!");
            e.printStackTrace();
            setEnabled(false);
        }
    }
    

    private void initializeStorageAdapter() {
        String type = getConfig().getString("storage.type", "yaml").toLowerCase();
        String dataFolderName = getConfig().getString("storage.data-folder", "plots");
        Path yamlDir = getDataFolder().toPath().resolve(dataFolderName);

        if ("sqlite".equals(type)) {
            String sqliteFile = getConfig().getString("storage.sqlite-file", "db.sqlite");
            Path sqlitePath = getDataFolder().toPath().resolve(sqliteFile);
            SqlitePlotStorage sqliteStorage = new SqlitePlotStorage(sqlitePath);
            if (getConfig().getBoolean("storage.migrate-yaml-on-startup", false)) {
                int migrated = sqliteStorage.migrateFromYaml(yamlDir);
                getLogger().info("SQLite migration completed. Migrated plots: " + migrated);
            }
            configuredStorageAdapter = sqliteStorage;
            getLogger().info("Configured plot storage adapter: sqlite (" + sqlitePath + ")");
            return;
        }

        configuredStorageAdapter = new YamlPlotStorage(yamlDir);
        getLogger().info("Configured plot storage adapter: yaml (" + yamlDir + ")");
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
        if (particleEffectScheduler != null) {
            particleEffectScheduler.stop();
        }
        if (seasonalWeatherService != null) {
            seasonalWeatherService.stop();
        }
        if (restApiController != null) {
            restApiController.stop();
        }
        if (dailyPlotBackupService != null) {
            dailyPlotBackupService.stop();
        }
        if (webServer != null) {
            webServer.stop();
        }
        getLogger().info("Streuland disabled");
    }
    
    public PlotManager getPlotManager() {
        return plotManager;
    }
    
    public PlotStorage getPlotStorage() {
        return plotManager.getStorage();
    }
    
    public PathGenerator getPathGenerator() {
        return pathGenerator;
    }

    public TransactionManager getTransactionManager() {
        return transactionManager;
    }
}
