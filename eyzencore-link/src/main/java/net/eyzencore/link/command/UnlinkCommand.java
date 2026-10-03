package net.eyzencore.link.command;

import com.google.gson.JsonObject;
import net.eyzencore.link.api.ApiClient;
import net.eyzencore.link.api.ApiResponse;
import net.eyzencore.link.api.dto.UnlinkRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class UnlinkCommand implements CommandExecutor {

    private final ApiClient apiClient;

    public UnlinkCommand(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Лише для гравців.", NamedTextColor.RED));
            return true;
        }

        player.sendMessage(Component.text("Відв'язка акаунта...", NamedTextColor.GRAY));
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(UnlinkCommand.class);
        apiClient.unlink(new UnlinkRequest(player.getUniqueId().toString()))
                .thenAccept(response ->
                        plugin.getServer().getScheduler().runTask(plugin, () -> handleResponse(player, response))
                );
        return true;
    }

    private void handleResponse(Player player, ApiResponse response) {
        if (!player.isOnline()) {
            return;
        }
        if (response.unauthorized()) {
            player.sendMessage(Component.text("Помилка сервера (токен). Звернись до адміністратора.", NamedTextColor.RED));
            return;
        }
        if (!response.ok()) {
            JsonObject json;
            try {
                json = apiClient.parseJson(response.body());
            } catch (Exception ex) {
                json = new JsonObject();
            }
            String error = json.has("error") ? json.get("error").getAsString() : "unknown";
            player.sendMessage(Component.text("Не вдалося відв'язати акаунт (" + error + ").", NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text("Акаунт відв'язано від Discord.", NamedTextColor.GREEN));
    }
}
