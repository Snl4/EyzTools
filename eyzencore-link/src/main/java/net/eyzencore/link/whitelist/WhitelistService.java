package net.eyzencore.link.whitelist;

import com.google.gson.Gson;
import net.eyzencore.link.EyzencoreLinkPlugin;
import net.eyzencore.link.api.ApiClient;
import net.eyzencore.link.api.dto.WhitelistResponse;
import net.eyzencore.link.config.PluginConfig;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

public final class WhitelistService {

    private final EyzencoreLinkPlugin plugin;
    private final PluginConfig config;
    private final ApiClient apiClient;
    private final Gson gson = new Gson();
    private final Path cacheFile;

    private final AtomicReference<Set<UUID>> uuidCache = new AtomicReference<>(Set.of());
    private final AtomicReference<Set<String>> nicknameCache = new AtomicReference<>(Set.of());
    private BukkitTask refreshTask;

    public WhitelistService(EyzencoreLinkPlugin plugin, PluginConfig config, ApiClient apiClient) {
        this.plugin = plugin;
        this.config = config;
        this.apiClient = apiClient;
        this.cacheFile = plugin.getDataFolder().toPath().resolve("whitelist-cache.json");
        loadDiskCache();
    }

    public void start() {
        restart();
    }

    public void restart() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        if (!config.whitelistEnabled()) {
            return;
        }
        refreshAsync();
        refreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refreshAsync, 20L * 60L, 20L * 60L);
    }

    public void shutdown() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    public boolean isAllowed(UUID uuid, String nickname) {
        if (!config.whitelistEnabled()) {
            return true;
        }
        if (uuid != null && uuidCache.get().contains(uuid)) {
            return true;
        }
        if (nickname != null && nicknameCache.get().contains(nickname.toLowerCase(Locale.ROOT))) {
            return true;
        }
        return false;
    }

    private void refreshAsync() {
        if (!config.whitelistEnabled()) {
            return;
        }
        apiClient.fetchWhitelist().thenAccept(response -> {
            if (response == null) {
                return;
            }
            Set<UUID> uuids = new HashSet<>();
            if (response.uuids != null) {
                for (String raw : response.uuids) {
                    try {
                        uuids.add(UUID.fromString(raw));
                    } catch (IllegalArgumentException ignored) {
                        plugin.getLogger().warning("Invalid whitelist UUID: " + raw);
                    }
                }
            }
            Set<String> nicknames = new HashSet<>();
            if (response.nicknames != null) {
                for (String nick : response.nicknames) {
                    if (nick != null && !nick.isBlank()) {
                        nicknames.add(nick.toLowerCase(Locale.ROOT));
                    }
                }
            }
            uuidCache.set(Collections.unmodifiableSet(uuids));
            nicknameCache.set(Collections.unmodifiableSet(nicknames));
            saveDiskCache(response);
            plugin.getLogger().info("Whitelist cache updated: " + uuids.size() + " UUID(s), "
                    + nicknames.size() + " nickname(s)");
        });
    }

    private void loadDiskCache() {
        if (!Files.isRegularFile(cacheFile)) {
            return;
        }
        try {
            String json = Files.readString(cacheFile, StandardCharsets.UTF_8);
            WhitelistResponse response = gson.fromJson(json, WhitelistResponse.class);
            if (response == null) {
                return;
            }
            Set<UUID> uuids = new HashSet<>();
            if (response.uuids != null) {
                for (String raw : response.uuids) {
                    try {
                        uuids.add(UUID.fromString(raw));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            Set<String> nicknames = new HashSet<>();
            if (response.nicknames != null) {
                for (String nick : response.nicknames) {
                    if (nick != null && !nick.isBlank()) {
                        nicknames.add(nick.toLowerCase(Locale.ROOT));
                    }
                }
            }
            uuidCache.set(Collections.unmodifiableSet(uuids));
            nicknameCache.set(Collections.unmodifiableSet(nicknames));
            plugin.getLogger().info("Loaded whitelist cache from disk (" + uuids.size() + " UUID(s))");
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to load whitelist cache", ex);
        }
    }

    private void saveDiskCache(WhitelistResponse response) {
        try {
            Files.createDirectories(cacheFile.getParent());
            Files.writeString(cacheFile, gson.toJson(response), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to save whitelist cache", ex);
        }
    }
}
