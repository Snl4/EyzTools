package net.eyzencore.link.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Renders LuckPerms / ItemsAdder prefixes in chat via PlaceholderAPI when present.
 */
public final class ChatFormatListener implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final boolean placeholderApi;
    private Method setPlaceholders;

    public ChatFormatListener() {
        Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        this.placeholderApi = papi != null && papi.isEnabled();
        if (placeholderApi) {
            try {
                Class<?> clazz = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                this.setPlaceholders = clazz.getMethod("setPlaceholders", org.bukkit.OfflinePlayer.class, String.class);
            } catch (ReflectiveOperationException ignored) {
                this.setPlaceholders = null;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        event.renderer((source, sourceDisplayName, message, viewer) -> {
            Component prefix = resolvePrefix(source);
            Component name = sourceDisplayName.colorIfAbsent(NamedTextColor.WHITE);
            return Component.empty()
                    .append(prefix)
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
        // Normalize section signs and strip accidental newlines from meta.
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
