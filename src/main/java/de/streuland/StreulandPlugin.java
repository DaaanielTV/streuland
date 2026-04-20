package de.streuland;

import de.streuland.admin.AdminPlotService;
import de.streuland.admin.BlockChangeLogger;
import de.streuland.admin.DailyPlotBackupService;
import de.streuland.admin.StreulandDiagnosticsService;
import de.streuland.analytics.InMemoryPlotAnalyticsService;
import de.streuland.approval.PlotApprovalService;
import de.streuland.backup.SnapshotService;
import de.streuland.backup.PlotBackupCoordinator;
import de.streuland.bootstrap.ConfigValidationService;
import de.streuland.bootstrap.FeatureToggles;
import de.streuland.command.DistrictCommandExecutor;
import de.streuland.command.PlotCommandExecutor;
import de.streuland.command.StreulandCommandExecutor;
import de.streuland.commands.PlotApprovalCommand;
import de.streuland.commands.PlotUpgradeCommand;
import de.streuland.compat.WorldGuardCompat;
import de.streuland.dashboard.DashboardDataExporter;
import de.streuland.dashboard.PlotAuditLogService;
import de.streuland.dashboard.RestApiController;
import de.streuland.discord.DiscordNotifier;
import de.streuland.district.DistrictClusterService;
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
import de.streuland.web.AdminObservabilityService;
import de.streuland.dashboard.RestApiController;
import de.streuland.flags.PlotFlagManager;
import de.streuland.history.JournalManager;
import de.streuland.history.PlotChangeJournal;
import de.streuland.i18n.MessageProvider;
import de.streuland.listener.BlockChangeListener;
import de.streuland.listener.ProtectionListener;
import de.streuland.market.MarketManager;
import de.streuland.neighborhood.NeighborhoodService;
import de.streuland.neighborhood.ResourceSyncScheduler;
import de.streuland.path.PathGenerator;
import de.streuland.plot.PlotManager;
import de.streuland.plot.PlotStorage;
import de.streuland.plot.biome.BiomeBonusService;
import de.streuland.plot.biome.BiomeEffectScheduler;
import de.streuland.plot.market.PlotMarketService;
import de.streuland.plot.skin.PlotSkinService;
import de.streuland.plot.snapshot.SnapshotManager;
import de.streuland.plot.snapshot.SnapshotStorage;
import de.streuland.plot.upgrade.DefaultPlotUpgradeService;
import de.streuland.plot.upgrade.PlotOwnershipResolver;
import de.streuland.plot.upgrade.PlotStorageBackedUpgradeStorage;
import de.streuland.plot.upgrade.PlotUpgradeService;
import de.streuland.plot.upgrade.PlotUpgradeTree;
import de.streuland.plot.upgrade.YamlPlotUpgradeCatalog;
import de.streuland.plot.template.PlotTemplateRegistry;
import de.streuland.schematic.SchematicLoader;
import de.streuland.quest.QuestService;
import de.streuland.quest.QuestTracker;
import de.streuland.rules.DefaultPlotLevelProvider;
import de.streuland.rules.ExampleRules;
import de.streuland.rules.RuleEngine;
import de.streuland.rules.listener.RuleListener;
import de.streuland.warp.CooldownManager;
import de.streuland.warp.PortalManager;
import de.streuland.transaction.TransactionManager;
import de.streuland.storage.SqlitePlotStorage;
import de.streuland.storage.YamlPlotStorage;
import de.streuland.transaction.TransactionManager;
import de.streuland.warp.CooldownManager;
import de.streuland.warp.PortalManager;
import de.streuland.weather.ParticleEffectScheduler;
import de.streuland.weather.SeasonalEffectListener;
import de.streuland.weather.SeasonalWeatherService;
import de.streuland.web.WebServer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Path;

public class StreulandPlugin extends JavaPlugin {
    private PlotManager plotManager;
    private PathGenerator pathGenerator;
    private SnapshotManager snapshotManager;
    private RuleEngine ruleEngine;
    private PlotSkinService plotSkinService;
    private BiomeBonusService biomeBonusService;
    private BiomeEffectScheduler biomeEffectScheduler;
    private InMemoryPlotAnalyticsService analyticsService;
    private NeighborhoodService neighborhoodService;
    private ResourceSyncScheduler resourceSyncScheduler;
    private RestApiController restApiController;
    private QuestService questService;
    private QuestTracker questTracker;
    private PlotMarketService plotMarketService;
    private Economy economy;
    private de.streuland.economy.PlotEconomyHook plotEconomyHook;
    private BlockChangeLogger blockChangeLogger;
    private AdminPlotService adminPlotService;
    private DailyPlotBackupService dailyPlotBackupService;
    private SnapshotService snapshotService;
    private PlotBackupCoordinator plotBackupCoordinator;
    private PlotAuditLogService plotAuditLogService;
    private TraderNpcService traderNpcService;
    private SeasonalWeatherService seasonalWeatherService;
    private PlotFlagManager plotFlagManager;
    private PlotTemplateRegistry plotTemplateRegistry;
    private PlotUpgradeService plotUpgradeService;
    private WorldGuardCompat worldGuardCompat;
    private MessageProvider messageProvider;
    private DiscordNotifier discordNotifier;
    private PlotApprovalService plotApprovalService;
    private TransactionManager transactionManager;
    private PortalManager portalManager;
    private WebServer webServer;
    private de.streuland.storage.PlotStorage configuredStorageAdapter;
    private de.streuland.clan.ClanManager clanManager;
    
    @Override
    public void onEnable() {
        saveDefaultConfig();

        try {
            validateStartupConfiguration();
            initializeStorageAdapter();
            // Initialize components in dependency order
            messageProvider = new MessageProvider(this);
            plotManager = new PlotManager(this);
            getLogger().info("✓ PlotManager initialized");

            plotTemplateRegistry = new PlotTemplateRegistry(new SchematicLoader(this));
            plotTemplateRegistry.registerDefaults();
            getLogger().info("✓ PlotTemplateRegistry initialized");

            pathGenerator = new PathGenerator(this, plotManager);
            getLogger().info("✓ PathGenerator initialized");

            clanManager = new de.streuland.clan.ClanManager(this, plotManager, pathGenerator);
            getLogger().info("✓ ClanManager initialized");

        if (features.approvalsEnabled()) {
            plotApprovalService = new PlotApprovalService(this, plotManager, pathGenerator, discordNotifier);
        }

        SnapshotStorage snapshotStorage = new SnapshotStorage(this);
        snapshotManager = new SnapshotManager(this, plotManager, snapshotStorage);
        snapshotService = new SnapshotService(this, plotManager, snapshotManager);
        plotBackupCoordinator = new PlotBackupCoordinator(plotManager, snapshotService);
        plotAuditLogService = new PlotAuditLogService(5000);

        ruleEngine = new RuleEngine(plotManager, new DefaultPlotLevelProvider());
        ruleEngine.registerProvider(new ExampleRules());

        biomeBonusService = new BiomeBonusService(plotManager, getConfig());
        ruleEngine.setBiomeBonusService(biomeBonusService);
        if (features.biomesEnabled()) {
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

            protectionListener = new ProtectionListener(this, plotManager, plotFlagManager, messageProvider);
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

            pricingEngine = new PricingEngine(this, plotManager, neighborhoodService);
            plotPriceCommand = new PlotPriceCommand(pricingEngine);
            plotMarketService = new PlotMarketService(this, plotManager, districtManager, analyticsService, economy, discordNotifier);

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
            portalManager = new PortalManager(this, plotManager, new de.streuland.warp.PlotEconomyHook(economy), new CooldownManager());
            getServer().getPluginManager().registerEvents(portalManager, this);

            PlotCommandExecutor commandExecutor = new PlotCommandExecutor(this, plotManager, pathGenerator, snapshotManager, ruleEngine, plotSkinService, biomeBonusService, neighborhoodService, questService, questTracker, plotMarketService, adminPlotService, analyticsService, traderNpcService, seasonalWeatherService, plotFlagManager, plotUpgradeCommand);
            getCommand("plot").setExecutor(commandExecutor);
            getCommand("plot").setTabCompleter(commandExecutor);
            if (getCommand("plotapprove") != null) {
                getCommand("plotapprove").setExecutor(new PlotApprovalCommand(plotApprovalService));
            }

        ParticleEffectScheduler particleEffectScheduler = new ParticleEffectScheduler(this, seasonalWeatherService);
        particleEffectScheduler.start();

        transactionManager = new TransactionManager(this);
        blockChangeLogger = new BlockChangeLogger(this, plotManager);
        PlotChangeJournal plotChangeJournal = new PlotChangeJournal(this, plotManager);
        JournalManager journalManager = new JournalManager(this, plotChangeJournal);
        adminPlotService = new AdminPlotService(plotManager, snapshotManager, blockChangeLogger);

        plotFlagManager = new PlotFlagManager(plotManager);
        worldGuardCompat = new WorldGuardCompat(this, plotManager, plotFlagManager);
        plotFlagManager.registerHook(worldGuardCompat);
        worldGuardCompat.syncAllPlots();

        new ProtectionListener(this, plotManager, plotFlagManager);
        new BlockChangeListener(this, plotManager, blockChangeLogger, analyticsService);
        new RuleListener(this, ruleEngine, biomeBonusService);
        getServer().getPluginManager().registerEvents(new SeasonalEffectListener(seasonalWeatherService, analyticsService), this);

        setupEconomy();
        plotEconomyHook = new de.streuland.economy.PlotEconomyHook(this);
        MarketManager marketManager = new MarketManager(this, plotManager.getStorage(), plotEconomyHook);

        districtBootstrap();

        if (features.marketEnabled()) {
            plotMarketService = new PlotMarketService(this, plotManager, districtManager, analyticsService, economy, discordNotifier);
        } else {
            plotMarketService = new PlotMarketService(this, plotManager, districtManager, analyticsService, null, discordNotifier);
        }

        registerCommands(features, journalManager);

        if (features.backupsEnabled()) {
            dailyPlotBackupService = new DailyPlotBackupService(this, snapshotService);
            dailyPlotBackupService.start();
        }

        if (features.dashboardApiEnabled()) {
            DashboardDataExporter exporter = new DashboardDataExporter(plotManager.getStorage());
            restApiController = new RestApiController(
                    this,
                    plotManager,
                    neighborhoodService,
                    analyticsService,
                    exporter,
                    plotMarketService,
                    plotApprovalService,
                    districtManager,
                    plotBackupCoordinator,
                    plotAuditLogService
            );
            restApiController.start();
            getLogger().info("✓ Dashboard API initialized");

            if (getConfig().getBoolean("web.enabled", false)) {
                String token = getConfig().getString("web.token", "");
                int webPort = getConfig().getInt("web.port", 8090);
                WebServer.PlotGatewayAdapter gateway = new WebServer.PlotGatewayAdapter(plotManager);
                AdminObservabilityService observabilityService = new AdminObservabilityService(gateway, analyticsService);
                webServer = new WebServer("0.0.0.0", webPort, token, gateway, observabilityService, getLogger());
                webServer.start();
                getLogger().info("✓ Admin web server listening on http://0.0.0.0:" + webPort);
            }
            
            getLogger().info("===============================================");
            getLogger().info("Streuland enabled successfully!");
            getLogger().info("Loaded " + plotManager.getAllPlots().size() + " plots");
            getLogger().info("===============================================");
            
        } catch (Exception e) {
            getLogger().severe("Failed to initialize Streuland: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }

        if (getConfig().getBoolean("web.enabled", false)) {
            webServer = new WebServer("0.0.0.0", getConfig().getInt("web.port", 8090), getConfig().getString("web.token", ""), new WebServer.PlotGatewayAdapter(plotManager), getLogger());
            webServer.start();
        }
    }

    private DistrictManager districtManager;
    private DistrictProgressService districtProgressService;

    private void districtBootstrap() {
        districtManager = new DistrictManager(this, plotManager);
        plotManager.setDistrictManager(districtManager);
        districtProgressService = new DistrictProgressService(this, plotManager, districtManager);
        getServer().getPluginManager().registerEvents(districtManager, this);
        getServer().getPluginManager().registerEvents(districtProgressService, this);

        traderNpcService = new TraderNpcService(this, plotManager, districtManager, analyticsService, economy);
        traderNpcService.start();
        getServer().getPluginManager().registerEvents(traderNpcService, this);

        questService = new QuestService(this, plotManager.getStorage(), ruleEngine);
        questTracker = new QuestTracker(plotManager, districtManager, questService);
        getServer().getPluginManager().registerEvents(questTracker, this);

        neighborhoodService = new NeighborhoodService(this, plotManager, new DistrictClusterService(), analyticsService);
        resourceSyncScheduler = new ResourceSyncScheduler(this, neighborhoodService);
        resourceSyncScheduler.start();
    }

    private void registerCommands(FeatureToggles features, JournalManager journalManager) {
        PluginCommand plotCommand = getCommand("plot");
        if (plotCommand == null) {
            throw new IllegalStateException("Command 'plot' is not defined in plugin.yml");
        }

        saveResource("plot-upgrades.yml", false);
        PlotUpgradeTree upgradeTree = YamlPlotUpgradeCatalog.load(new File(getDataFolder(), "plot-upgrades.yml"));
        PlotOwnershipResolver ownershipResolver = (plotId, playerId) -> {
            de.streuland.plot.Plot plot = plotManager.getStorage(plotManager.getWorldForPlot(plotId)).getPlot(plotId);
            return plot != null && playerId != null && playerId.equals(plot.getOwner());
        };
        plotUpgradeService = new DefaultPlotUpgradeService(upgradeTree, new PlotStorageBackedUpgradeStorage(plotManager.getStorage()), plotEconomyHook, ownershipResolver);
        PlotUpgradeCommand upgradeCommand = new PlotUpgradeCommand(plotManager, plotUpgradeService);

        PlotCommandExecutor commandExecutor = new PlotCommandExecutor(
                this,
                plotManager,
                pathGenerator,
                snapshotManager,
                ruleEngine,
                plotSkinService,
                biomeBonusService,
                neighborhoodService,
                questService,
                questTracker,
                plotMarketService,
                adminPlotService,
                analyticsService,
                traderNpcService,
                seasonalWeatherService,
                plotFlagManager,
                upgradeCommand
        );

        plotCommand.setExecutor(commandExecutor);
        plotCommand.setTabCompleter(commandExecutor);

        if (plotApprovalService != null && getCommand("plotapprove") != null) {
            getCommand("plotapprove").setExecutor(new PlotApprovalCommand(plotApprovalService));
        }
        if (getCommand("district") != null) {
            DistrictCommandExecutor districtCommandExecutor = new DistrictCommandExecutor(plotManager, districtManager, messageProvider);
            getCommand("district").setExecutor(districtCommandExecutor);
            getCommand("district").setTabCompleter(districtCommandExecutor);
        }
        if (getCommand("streuland") != null) {
            getCommand("streuland").setExecutor(new StreulandCommandExecutor(
                    plotManager,
                    new StreulandDiagnosticsService(plotManager, getLogger()),
                    plotBackupCoordinator,
                    plotAuditLogService
            ));
        }
        if (getCommand("clan") != null) {
            getCommand("clan").setExecutor(new de.streuland.clan.ClanCommand(clanManager));
        }

        if (features.backupsEnabled() || features.dashboardApiEnabled() || features.upgradesEnabled()) {
            getLogger().info("Optional modules active: backups=" + features.backupsEnabled() + ", dashboard/api=" + features.dashboardApiEnabled() + ", upgrades=" + features.upgradesEnabled());
        }

        portalManager = new PortalManager(this, plotManager, new de.streuland.warp.PlotEconomyHook(economy), new CooldownManager());
        getServer().getPluginManager().registerEvents(portalManager, this);
    }


    private void validateStartupConfiguration() {
        if (getConfig().getConfigurationSection("worlds") == null && getConfig().getString("world.name") == null) {
            throw new IllegalStateException("Missing world configuration. Expected 'world.name' or configured worlds section.");
        }
        if (getCommand("plot") == null) {
            throw new IllegalStateException("Command 'plot' is not defined in plugin.yml");
        }
        if (getCommand("district") == null) {
            getLogger().warning("Command 'district' is missing in plugin.yml; district command will not be available.");
        }
        if (getConfig().getBoolean("web.enabled", false)) {
            String token = getConfig().getString("web.token", "");
            if (token == null || token.isBlank()) {
                getLogger().warning("web.enabled is true but web.token is empty. Web API will still start but is insecure.");
            }
        }
    }

    private void initializeStorageAdapter() {
        String type = getConfig().getString("storage.type", "yaml").toLowerCase();
        Path dataPath = getDataFolder().toPath();
        if ("sqlite".equals(type)) {
            Path sqlitePath = dataPath.resolve(getConfig().getString("storage.sqlite-file", "db.sqlite"));
            SqlitePlotStorage sqliteStorage = new SqlitePlotStorage(sqlitePath);
            if (getConfig().getBoolean("storage.migrate-yaml-on-startup", false)) {
                int migrated = sqliteStorage.migrateFromYaml(dataPath.resolve(getConfig().getString("storage.data-folder", "plots")));
                getLogger().info("SQLite migration completed. Migrated plots: " + migrated);
            }
            configuredStorageAdapter = sqliteStorage;
            return;
        }
        configuredStorageAdapter = new YamlPlotStorage(dataPath.resolve(getConfig().getString("storage.data-folder", "plots")));
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
        if (plotSkinService != null) plotSkinService.stop();
        if (biomeEffectScheduler != null) biomeEffectScheduler.stop();
        if (resourceSyncScheduler != null) resourceSyncScheduler.stop();
        if (seasonalWeatherService != null) seasonalWeatherService.stop();
        if (restApiController != null) restApiController.stop();
        if (dailyPlotBackupService != null) dailyPlotBackupService.stop();
        if (webServer != null) webServer.stop();
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

    public PlotApprovalService getPlotApprovalService() {
        return plotApprovalService;
    }

    public PlotTemplateRegistry getPlotTemplateRegistry() {
        return plotTemplateRegistry;
    }
}
