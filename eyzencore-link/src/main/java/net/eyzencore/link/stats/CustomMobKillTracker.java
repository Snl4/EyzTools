package net.eyzencore.link.stats;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.eyzencore.link.EyzencoreLinkPlugin;
import net.eyzencore.link.config.PluginConfig;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class CustomMobKillTracker implements Listener {

    private static final Type MAP_TYPE = new TypeToken<Map<String, Integer>>() {}.getType();

    private final EyzencoreLinkPlugin plugin;
    private final PluginConfig config;
    private final Gson gson = new Gson();
    private final NamespacedKey killsKey;

    public CustomMobKillTracker(EyzencoreLinkPlugin plugin, PluginConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.killsKey = new NamespacedKey(plugin, "custom_mob_kills");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        String mobId = resolveCustomMobId(event.getEntity());
        if (mobId == null) {
            return;
        }
        Map<String, Integer> kills = new HashMap<>(read(killer));
        kills.put(mobId, kills.getOrDefault(mobId, 0) + 1);
        write(killer, kills);
    }

    public Map<String, Integer> read(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        String raw = pdc.get(killsKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Integer> parsed = gson.fromJson(raw, MAP_TYPE);
            return parsed == null ? Collections.emptyMap() : Collections.unmodifiableMap(parsed);
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    private void write(Player player, Map<String, Integer> kills) {
        player.getPersistentDataContainer().set(killsKey, PersistentDataType.STRING, gson.toJson(kills));
    }

    private String resolveCustomMobId(Entity entity) {
        Set<String> tags = entity.getScoreboardTags();
        if (tags.isEmpty()) {
            // Also try Mythic-style fallback via entity custom name? No — tags only per README.
            return null;
        }
        for (Map.Entry<String, String> entry : config.customMobTags().entrySet()) {
            if (tags.contains(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }
}
