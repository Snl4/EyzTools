package net.eyzencore.link.config;

import net.eyzencore.link.EyzencoreLinkPlugin;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PluginConfig {

    private final EyzencoreLinkPlugin plugin;

    private String baseUrl;
    private String token;
    private int pushIntervalMinutes;
    private boolean pushOnQuit;
    private boolean whitelistEnabled;
    private String whitelistKickMessage;
    private Map<String, String> customMobTags;

    public PluginConfig(EyzencoreLinkPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        this.baseUrl = stripTrailingSlash(plugin.getConfig().getString("api.base-url", "https://eyzencore.net"));
        this.token = plugin.getConfig().getString("api.token", "CHANGE_ME");
        this.pushIntervalMinutes = Math.max(1, plugin.getConfig().getInt("stats.push-interval-minutes", 5));
        this.pushOnQuit = plugin.getConfig().getBoolean("stats.push-on-quit", true);
        this.whitelistEnabled = plugin.getConfig().getBoolean("whitelist.enabled", false);
        this.whitelistKickMessage = plugin.getConfig().getString(
                "whitelist.kick-message",
                "Подай заявку на сайті eyzencore.net"
        );

        Map<String, String> tags = new LinkedHashMap<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("custom-mobs");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                String tag = section.getString(id + ".tag");
                if (tag != null && !tag.isBlank()) {
                    tags.put(id, tag);
                }
            }
        }
        this.customMobTags = Collections.unmodifiableMap(tags);
    }

    private static String stripTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return "https://eyzencore.net";
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String token() {
        return token;
    }

    public int pushIntervalMinutes() {
        return pushIntervalMinutes;
    }

    public boolean pushOnQuit() {
        return pushOnQuit;
    }

    public boolean whitelistEnabled() {
        return whitelistEnabled;
    }

    public String whitelistKickMessage() {
        return whitelistKickMessage;
    }

    public Map<String, String> customMobTags() {
        return customMobTags;
    }
}
