package net.eyzencore.link.whitelist;

import net.eyzencore.link.config.PluginConfig;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

public final class WhitelistListener implements Listener {

    private final PluginConfig config;
    private final WhitelistService whitelistService;

    public WhitelistListener(PluginConfig config, WhitelistService whitelistService) {
        this.config = config;
        this.whitelistService = whitelistService;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!config.whitelistEnabled()) {
            return;
        }
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        if (whitelistService.isAllowed(event.getUniqueId(), event.getName())) {
            return;
        }
        event.disallow(
                AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST,
                Component.text(config.whitelistKickMessage())
        );
    }
}
