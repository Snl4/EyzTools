package net.eyzencore.link.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.eyzencore.link.config.PluginConfig;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Local / global chat with LuckPerms prefixes.
 * <ul>
 *   <li>Local (default): players within configured radius</li>
 *   <li>Global: message starts with {@code !}</li>
 * </ul>
 */
public final class ChatFormatListener implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final PluginConfig config;
    private final Method setPlaceholders;

    public ChatFormatListener(PluginConfig config) {
        this.config = config;
        Method method = null;
        Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        if (papi != null && papi.isEnabled()) {
            try {
                Class<?> clazz = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                method = clazz.getMethod("setPlaceholders", org.bukkit.OfflinePlayer.class, String.class);
            } catch (ReflectiveOperationException ignored) {
                method = null;
            }
        }
        this.setPlaceholders = method;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!config.chatEnabled()) {
            applyRenderer(event, false);
            return;
        }

        Player source = event.getPlayer();
        String plain = PLAIN.serialize(event.message()).strip();
        boolean global = plain.startsWith("!");

        if (global) {
            String body = plain.substring(1).stripLeading();
            if (body.isEmpty()) {
                event.setCancelled(true);
                source.sendMessage(Component.text("Напиши текст після ! для глобального чату.", NamedTextColor.RED));
                return;
            }
            event.message(Component.text(body));
        } else {
            double range = config.chatLocalRadius();
            double rangeSq = range * range;
            event.viewers().removeIf(audience -> shouldHideLocal(audience, source, rangeSq));
        }

        applyRenderer(event, global);
    }

    private static boolean shouldHideLocal(Audience audience, Player source, double rangeSq) {
        if (!(audience instanceof Player viewer)) {
            return false;
        }
        if (viewer.getUniqueId().equals(source.getUniqueId())) {
            return false;
        }
        if (!viewer.getWorld().equals(source.getWorld())) {
            return true;
        }
        return viewer.getLocation().distanceSquared(source.getLocation()) > rangeSq;
    }

    private void applyRenderer(AsyncChatEvent event, boolean global) {
        event.renderer((source, sourceDisplayName, message, viewer) -> {
            Component channel = global
                    ? LEGACY.deserialize(config.chatGlobalPrefix())
                    : LEGACY.deserialize(config.chatLocalPrefix());
            Component luckPrefix = resolvePrefix(source);
            Component name = sourceDisplayName.colorIfAbsent(NamedTextColor.WHITE);
            return Component.empty()
                    .append(channel)
                    .append(luckPrefix)
                    .append(name)
                    .append(Component.text(": ", NamedTextColor.DARK_GRAY))
                    .append(message.colorIfAbsent(NamedTextColor.GRAY));
        });
    }

    private Component resolvePrefix(Player player) {
        String raw = placeholders(player, "%luckperms_prefix%");
        if (raw == null || raw.isBlank() || raw.contains("%luckperms_prefix%")) {
            raw = placeholders(player, "%vault_prefix%");
        }
        if (raw == null || raw.isBlank() || raw.contains("%")) {
            return Component.empty();
        }
        raw = raw.replace('\u00A7', '&').replace("\n", "").replace("\r", "");
        return LEGACY.deserialize(raw);
    }

    private String placeholders(Player player, String input) {
        if (setPlaceholders == null) {
            return input;
        }
        try {
            Object out = setPlaceholders.invoke(null, player, input);
            return out == null ? input : out.toString();
        } catch (ReflectiveOperationException ex) {
            return input;
        }
    }
}
