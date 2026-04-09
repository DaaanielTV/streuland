package de.streuland;

import de.streuland.admin.AdminPlotService;
import de.streuland.admin.BlockChangeLogger;
import de.streuland.admin.DailyPlotBackupService;
import de.streuland.admin.StreulandDiagnosticsService;
import de.streuland.analytics.InMemoryPlotAnalyticsService;
import de.streuland.approval.PlotApprovalService;
import de.streuland.backup.SnapshotService;
import de.streuland.bootstrap.ConfigValidationService;
import de.streuland.bootstrap.FeatureToggles;
import de.streuland.command.DistrictCommandExecutor;
import de.streuland.command.PlotCommandExecutor;
import de.streuland.command.StreulandCommandExecutor;
import de.streuland.commands.PlotApprovalCommand;
import de.streuland.commands.PlotUpgradeCommand;
import de.streuland.compat.WorldGuardCompat;
import de.streuland.dashboard.DashboardDataExporter;
import de.streuland.dashboard.RestApiController;
import de.streuland.discord.DiscordNotifier;
import de.streuland.district.DistrictClusterService;
import de.streuland.district.DistrictManager;
import de.streuland.district.DistrictProgressService;
import de.streuland.district.TraderNpcService;
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
import de.streuland.quest.QuestService;
import de.streuland.quest.QuestTracker;
import de.streuland.rules.DefaultPlotLevelProvider;
import de.streuland.rules.ExampleRules;
import de.streuland.rules.RuleEngine;
import de.streuland.rules.listener.RuleListener;
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
    private TraderNpcService traderNpcService;
    private SeasonalWeatherService seasonalWeatherService;
    private PlotFlagManager plotFlagManager;
    private PlotUpgradeService plotUpgradeService;
    private WorldGuardCompat worldGuardCompat;
    private MessageProvider messageProvider;
    private DiscordNotifier discordNotifier;
    private PlotApprovalService plotApprovalService;
    private TransactionManager transactionManager;
    private PortalManager portalManager;
    private WebServer webServer;
    private de.streuland.storage.PlotStorage configuredStorageAdapter;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        new ConfigValidationService(this).validateAndNormalize();
        FeatureToggles features = FeatureToggles.fromConfig(getConfig());
        initializeStorageAdapter();

        messageProvider = new MessageProvider(this);
        plotManager = new PlotManager(this);
        pathGenerator = new PathGenerator(this, plotManager);
        discordNotifier = new DiscordNotifier(this);

        if (features.approvalsEnabled()) {
            plotApprovalService = new PlotApprovalService(this, plotManager, pathGenerator, discordNotifier);
        }

        SnapshotStorage snapshotStorage = new SnapshotStorage(this);
        snapshotManager = new SnapshotManager(this, plotManager, snapshotStorage);
        snapshotService = new SnapshotService(this, plotManager, snapshotManager);

        ruleEngine = new RuleEngine(plotManager, new DefaultPlotLevelProvider());
        ruleEngine.registerProvider(new ExampleRules());

        biomeBonusService = new BiomeBonusService(plotManager, getConfig());
        ruleEngine.setBiomeBonusService(biomeBonusService);
        if (features.biomesEnabled()) {
            biomeEffectScheduler = new BiomeEffectScheduler(this, plotManager, biomeBonusService);
            biomeEffectScheduler.start();
        }
        ruleEngine.reload();

        plotSkinService = new PlotSkinService(this, plotManager.getStorage());
        plotSkinService.start();

        analyticsService = new InMemoryPlotAnalyticsService();
        seasonalWeatherService = new SeasonalWeatherService(this, analyticsService);
        seasonalWeatherService.start();

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
            restApiController = new RestApiController(this, plotManager, neighborhoodService, analyticsService, exporter, plotMarketService, plotApprovalService);
            restApiController.start();
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
            getCommand("district").setExecutor(new DistrictCommandExecutor(plotManager, districtManager, messageProvider));
        }
        if (getCommand("streuland") != null) {
            getCommand("streuland").setExecutor(new StreulandCommandExecutor(plotManager, new StreulandDiagnosticsService(plotManager, getLogger())));
        }

        if (features.backupsEnabled() || features.dashboardApiEnabled() || features.upgradesEnabled()) {
            getLogger().info("Optional modules active: backups=" + features.backupsEnabled() + ", dashboard/api=" + features.dashboardApiEnabled() + ", upgrades=" + features.upgradesEnabled());
        }

        portalManager = new PortalManager(this, plotManager, new de.streuland.warp.PlotEconomyHook(economy), new CooldownManager());
        getServer().getPluginManager().registerEvents(portalManager, this);
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
}

