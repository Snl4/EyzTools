package net.eyzencore.link.stats;

import net.eyzencore.link.EyzencoreLinkPlugin;
import net.eyzencore.link.config.PluginConfig;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Soft-depends on MythicMobs: if an entity is a Mythic mob whose internal name
 * matches a configured custom-mob id, attach the corresponding scoreboard tag.
 */
public final class MythicMobTagListener implements Listener {

    private final EyzencoreLinkPlugin plugin;
    private final PluginConfig config;
    private final boolean mythicPresent;

    private Method getTypeMethod;
    private Method getInternalNameMethod;

    public MythicMobTagListener(EyzencoreLinkPlugin plugin, PluginConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.mythicPresent = Bukkit.getPluginManager().getPlugin("MythicMobs") != null;
        if (mythicPresent) {
            plugin.getLogger().info("MythicMobs detected — custom mob tags will be applied on spawn");
            Bukkit.getScheduler().runTaskLater(plugin, this::tagLoadedEntities, 40L);
        }
    }

    public boolean enabled() {
        return mythicPresent;
    }

    public void tagLoadedEntities() {
        if (!enabled()) {
            return;
        }
        int tagged = 0;
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (tryTag(entity)) {
                    tagged++;
                }
            }
        }
        if (tagged > 0) {
            plugin.getLogger().info("Tagged " + tagged + " existing Mythic custom mob(s)");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!enabled()) {
            return;
        }
        // Mythic often finishes setup one tick later.
        Entity entity = event.getEntity();
        Bukkit.getScheduler().runTask(plugin, () -> tryTag(entity));
    }

    private boolean tryTag(Entity entity) {
        try {
            Object manager = resolveMobManager();
            if (manager == null) {
                return false;
            }
            Method isMythicMob = manager.getClass().getMethod("isMythicMob", Entity.class);
            Boolean mythic = (Boolean) isMythicMob.invoke(manager, entity);
            if (mythic == null || !mythic) {
                return false;
            }
            Method getInstance = manager.getClass().getMethod("getMythicMobInstance", Entity.class);
            Object activeMob = getInstance.invoke(manager, entity);
            if (activeMob == null) {
                return false;
            }
            String internal = resolveInternalName(activeMob);
            if (internal == null) {
                return false;
            }
            return tagExact(entity, internal.toLowerCase(Locale.ROOT));
        } catch (Exception ex) {
            plugin.getLogger().log(Level.FINEST, "Mythic tag apply skipped", ex);
            return false;
        }
    }

    private boolean tagExact(Entity entity, String internalLower) {
        String id = internalLower;
        if (id.startsWith("baby_")) {
            id = id.substring(5);
        }
        if (id.contains("_tail") || id.contains("_hitbox")) {
            return false;
        }
        String tag = config.customMobTags().get(id);
        if (tag == null) {
            return false;
        }
        if (entity.getScoreboardTags().contains(tag)) {
            return false;
        }
        entity.addScoreboardTag(tag);
        return true;
    }

    private Object resolveMobManager() throws Exception {
        Class<?> mythicBukkit = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
        Object api = mythicBukkit.getMethod("inst").invoke(null);
        return api.getClass().getMethod("getMobManager").invoke(api);
    }

    private String resolveInternalName(Object activeMob) throws Exception {
        if (getTypeMethod == null) {
            try {
                getTypeMethod = activeMob.getClass().getMethod("getType");
            } catch (NoSuchMethodException ignored) {
                getTypeMethod = activeMob.getClass().getMethod("getMobType");
            }
        }
        Object type = getTypeMethod.invoke(activeMob);
        if (type == null) {
            return null;
        }
        if (getInternalNameMethod == null) {
            try {
                getInternalNameMethod = type.getClass().getMethod("getInternalName");
            } catch (NoSuchMethodException ignored) {
                getInternalNameMethod = type.getClass().getMethod("getName");
            }
        }
        Object name = getInternalNameMethod.invoke(type);
        return name == null ? null : name.toString();
    }
}
