package net.eyzencore.link.stats;

import net.eyzencore.link.EyzencoreLinkPlugin;
import net.eyzencore.link.api.dto.PlayerStatsDto;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class StatsCollector {

    private static final Set<Statistic> DISTANCE_STATS = EnumSet.of(
            Statistic.WALK_ONE_CM,
            Statistic.SPRINT_ONE_CM,
            Statistic.CROUCH_ONE_CM,
            Statistic.SWIM_ONE_CM,
            Statistic.WALK_ON_WATER_ONE_CM,
            Statistic.WALK_UNDER_WATER_ONE_CM,
            Statistic.CLIMB_ONE_CM,
            Statistic.FLY_ONE_CM,
            Statistic.AVIATE_ONE_CM,
            Statistic.HORSE_ONE_CM,
            Statistic.BOAT_ONE_CM,
            Statistic.MINECART_ONE_CM,
            Statistic.PIG_ONE_CM,
            Statistic.STRIDER_ONE_CM
    );

    private final EyzencoreLinkPlugin plugin;
    private final List<Material> blockMaterials = new ArrayList<>();
    private final List<Advancement> trackedAdvancements = new ArrayList<>();
    private int advancementsTotal;

    public StatsCollector(EyzencoreLinkPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        blockMaterials.clear();
        for (Material material : Material.values()) {
            if (material.isBlock() && !material.isAir()) {
                blockMaterials.add(material);
            }
        }

        trackedAdvancements.clear();
        Iterator<Advancement> iterator = Bukkit.advancementIterator();
        while (iterator.hasNext()) {
            Advancement advancement = iterator.next();
            NamespacedKey key = advancement.getKey();
            if ("minecraft".equals(key.getNamespace()) && key.getKey().startsWith("recipes/")) {
                continue;
            }
            trackedAdvancements.add(advancement);
        }
        advancementsTotal = trackedAdvancements.size();
        plugin.getLogger().info("Cached " + blockMaterials.size() + " block materials and "
                + advancementsTotal + " advancements for stats");
    }

    public int advancementsTotal() {
        return advancementsTotal;
    }

    /**
     * Must be called on the main thread.
     */
    public PlayerStatsDto collect(OfflinePlayer offlinePlayer, Map<String, Integer> customMobKills) {
        PlayerStatsDto dto = new PlayerStatsDto();
        UUID uuid = offlinePlayer.getUniqueId();
        dto.uuid = uuid.toString();
        dto.nickname = offlinePlayer.getName() == null ? "Unknown" : offlinePlayer.getName();
        dto.online = offlinePlayer.isOnline();
        dto.firstJoin = toIso(offlinePlayer.getFirstPlayed());
        dto.lastSeen = dto.online
                ? Instant.now().toString()
                : toIso(offlinePlayer.getLastSeen());

        dto.playtime = safeStat(offlinePlayer, Statistic.PLAY_ONE_MINUTE) / 20L / 60L;
        dto.deaths = safeStat(offlinePlayer, Statistic.DEATHS);
        dto.mobKills = safeStat(offlinePlayer, Statistic.MOB_KILLS);
        dto.playerKills = safeStat(offlinePlayer, Statistic.PLAYER_KILLS);
        dto.blocksMined = sumTyped(offlinePlayer, Statistic.MINE_BLOCK);
        dto.blocksPlaced = sumTyped(offlinePlayer, Statistic.USE_ITEM);
        dto.distance = sumDistance(offlinePlayer) / 100L;
        dto.advancements = countAdvancements(offlinePlayer);
        dto.advancementsTotal = advancementsTotal;
        dto.customMobKills = customMobKills == null ? Map.of() : customMobKills;
        return dto;
    }

    private long sumTyped(OfflinePlayer player, Statistic statistic) {
        long total = 0L;
        for (Material material : blockMaterials) {
            try {
                total += player.getStatistic(statistic, material);
            } catch (IllegalArgumentException ignored) {
                // Material not applicable for this statistic.
            }
        }
        return total;
    }

    private long sumDistance(OfflinePlayer player) {
        long total = 0L;
        for (Statistic statistic : DISTANCE_STATS) {
            total += safeStat(player, statistic);
        }
        return total;
    }

    private int countAdvancements(OfflinePlayer offlinePlayer) {
        Player player = offlinePlayer.getPlayer();
        if (player == null) {
            return 0;
        }
        int done = 0;
        for (Advancement advancement : trackedAdvancements) {
            if (player.getAdvancementProgress(advancement).isDone()) {
                done++;
            }
        }
        return done;
    }

    private static int safeStat(OfflinePlayer player, Statistic statistic) {
        try {
            return player.getStatistic(statistic);
        } catch (IllegalArgumentException ex) {
            return 0;
        }
    }

    private static String toIso(long epochMs) {
        if (epochMs <= 0L) {
            return Instant.EPOCH.toString();
        }
        return Instant.ofEpochMilli(epochMs).toString();
    }
}
