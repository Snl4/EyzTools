package net.eyzencore.link.stats;

import net.eyzencore.link.EyzencoreLinkPlugin;
import net.eyzencore.link.api.ApiClient;
import net.eyzencore.link.api.ApiResponse;
import net.eyzencore.link.api.dto.PlayerStatsDto;
import net.eyzencore.link.api.dto.StatsBatchRequest;
import net.eyzencore.link.config.PluginConfig;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class StatsPushService implements Listener {

    private final EyzencoreLinkPlugin plugin;
    private final PluginConfig config;
    private final ApiClient apiClient;
    private final StatsCollector collector;
    private final CustomMobKillTracker killTracker;

    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    private BukkitTask intervalTask;

    public StatsPushService(
            EyzencoreLinkPlugin plugin,
            PluginConfig config,
            ApiClient apiClient,
            StatsCollector collector,
            CustomMobKillTracker killTracker
    ) {
        this.plugin = plugin;
        this.config = config;
        this.apiClient = apiClient;
        this.collector = collector;
        this.killTracker = killTracker;
    }

    public void start() {
        restart();
    }

    public void restart() {
        if (intervalTask != null) {
            intervalTask.cancel();
        }
        long ticks = config.pushIntervalMinutes() * 60L * 20L;
        intervalTask = Bukkit.getScheduler().runTaskTimer(plugin, this::pushDirtyOnline, ticks, ticks);
    }

    public void shutdown() {
        if (intervalTask != null) {
            intervalTask.cancel();
            intervalTask = null;
        }
        // Best-effort final push for currently online players.
        for (Player player : Bukkit.getOnlinePlayers()) {
            dirty.add(player.getUniqueId());
        }
        pushNowSyncCollectAsyncSend(List.copyOf(dirty), false);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        dirty.add(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        dirty.add(uuid);
        if (config.pushOnQuit()) {
            pushNowSyncCollectAsyncSend(List.of(uuid), true);
        }
    }

    public void markDirty(UUID uuid) {
        dirty.add(uuid);
    }

    public void pushPlayer(Player player) {
        pushNowSyncCollectAsyncSend(List.of(player.getUniqueId()), true);
    }

    public void pushPlayer(OfflinePlayer player) {
        pushNowSyncCollectAsyncSend(List.of(player.getUniqueId()), true);
    }

    public void pushDirtyOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            dirty.add(player.getUniqueId());
        }
        if (dirty.isEmpty()) {
            return;
        }
        pushNowSyncCollectAsyncSend(List.copyOf(dirty), true);
    }

    /**
     * Collect statistics on the main thread, then send asynchronously.
     */
    private void pushNowSyncCollectAsyncSend(List<UUID> uuids, boolean clearDirty) {
        if (uuids.isEmpty()) {
            return;
        }
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> pushNowSyncCollectAsyncSend(uuids, clearDirty));
            return;
        }

        List<PlayerStatsDto> batch = new ArrayList<>();
        for (UUID uuid : uuids) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
            Player online = offline.getPlayer();
            Map<String, Integer> customKills = online == null
                    ? Map.of()
                    : killTracker.read(online);
            try {
                batch.add(collector.collect(offline, customKills));
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to collect stats for " + uuid, ex);
            }
        }

        if (batch.isEmpty()) {
            return;
        }

        StatsBatchRequest request = new StatsBatchRequest();
        request.server = "eyzencore";
        request.sentAt = Instant.now().toString();
        request.players = batch;

        if (clearDirty) {
            dirty.removeAll(uuids);
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ApiResponse response = apiClient.pushStats(request).join();
            if (response.ok()) {
                plugin.getLogger().fine("Pushed stats for " + batch.size() + " player(s)");
            } else if (response.statusCode() != 0) {
                plugin.getLogger().warning("Stats push failed: HTTP " + response.statusCode()
                        + " body=" + truncate(response.body()));
                // Re-queue for next attempt.
                dirty.addAll(uuids);
            } else {
                dirty.addAll(uuids);
            }
        });
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 200 ? body : body.substring(0, 200) + "...";
    }
}
