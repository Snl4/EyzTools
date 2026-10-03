package net.eyzencore.link;

import net.eyzencore.link.api.ApiClient;
import net.eyzencore.link.chat.ChatFormatListener;
import net.eyzencore.link.command.EyzencoreCommand;
import net.eyzencore.link.command.LinkCommand;
import net.eyzencore.link.command.UnlinkCommand;
import net.eyzencore.link.config.PluginConfig;
import net.eyzencore.link.stats.CustomMobKillTracker;
import net.eyzencore.link.stats.MythicMobTagListener;
import net.eyzencore.link.stats.StatsCollector;
import net.eyzencore.link.stats.StatsPushService;
import net.eyzencore.link.whitelist.WhitelistListener;
import net.eyzencore.link.whitelist.WhitelistService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class EyzencoreLinkPlugin extends JavaPlugin {

    private PluginConfig pluginConfig;
    private ApiClient apiClient;
    private StatsCollector statsCollector;
    private CustomMobKillTracker customMobKillTracker;
    private StatsPushService statsPushService;
    private WhitelistService whitelistService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.pluginConfig = new PluginConfig(this);
        this.pluginConfig.reload();

        this.apiClient = new ApiClient(this, pluginConfig);
        this.statsCollector = new StatsCollector(this);
        this.customMobKillTracker = new CustomMobKillTracker(this, pluginConfig);
        this.statsPushService = new StatsPushService(this, pluginConfig, apiClient, statsCollector, customMobKillTracker);
        this.whitelistService = new WhitelistService(this, pluginConfig, apiClient);

        getServer().getPluginManager().registerEvents(customMobKillTracker, this);
        getServer().getPluginManager().registerEvents(new MythicMobTagListener(this, pluginConfig), this);
        getServer().getPluginManager().registerEvents(new WhitelistListener(pluginConfig, whitelistService), this);
        getServer().getPluginManager().registerEvents(statsPushService, this);
        getServer().getPluginManager().registerEvents(new ChatFormatListener(), this);

        registerCommands();

        statsCollector.init();
        statsPushService.start();
        whitelistService.start();

        getLogger().info("EyzencoreLink v" + getPluginMeta().getVersion() + " enabled");
    }

    @Override
    public void onDisable() {
        if (statsPushService != null) {
            statsPushService.shutdown();
        }
        if (whitelistService != null) {
            whitelistService.shutdown();
        }
        if (apiClient != null) {
            apiClient.close();
        }
    }

    public void reloadPlugin() {
        reloadConfig();
        pluginConfig.reload();
        statsPushService.restart();
        whitelistService.restart();
    }

    private void registerCommands() {
        PluginCommand link = getCommand("link");
        if (link != null) {
            link.setExecutor(new LinkCommand(this, apiClient, statsPushService));
        }
        PluginCommand unlink = getCommand("unlink");
        if (unlink != null) {
            unlink.setExecutor(new UnlinkCommand(apiClient));
        }
        PluginCommand eyzencore = getCommand("eyzencore");
        if (eyzencore != null) {
            eyzencore.setExecutor(new EyzencoreCommand(this, statsPushService));
        }
    }

    public PluginConfig pluginConfig() {
        return pluginConfig;
    }

    public ApiClient apiClient() {
        return apiClient;
    }

    public StatsPushService statsPushService() {
        return statsPushService;
    }

    public StatsCollector statsCollector() {
        return statsCollector;
    }

    public CustomMobKillTracker customMobKillTracker() {
        return customMobKillTracker;
    }
}
