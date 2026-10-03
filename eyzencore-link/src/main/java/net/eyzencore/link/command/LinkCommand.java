package net.eyzencore.link.command;

import com.google.gson.JsonObject;
import net.eyzencore.link.EyzencoreLinkPlugin;
import net.eyzencore.link.api.ApiClient;
import net.eyzencore.link.api.ApiResponse;
import net.eyzencore.link.api.dto.LinkRequest;
import net.eyzencore.link.stats.StatsPushService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.regex.Pattern;

public final class LinkCommand implements CommandExecutor {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9]{6}$");

    private final EyzencoreLinkPlugin plugin;
    private final ApiClient apiClient;
    private final StatsPushService statsPushService;

    public LinkCommand(EyzencoreLinkPlugin plugin, ApiClient apiClient, StatsPushService statsPushService) {
        this.plugin = plugin;
        this.apiClient = apiClient;
        this.statsPushService = statsPushService;
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
        if (args.length != 1) {
            player.sendMessage(Component.text("Використання: /link <код>", NamedTextColor.YELLOW));
            return true;
        }

        String code = args[0].trim().toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(code).matches()) {
            player.sendMessage(Component.text("Код має бути 6 символів (A-Z, 0-9).", NamedTextColor.RED));
            return true;
        }

        player.sendMessage(Component.text("Прив'язка акаунта...", NamedTextColor.GRAY));

        LinkRequest request = new LinkRequest(code, player.getUniqueId().toString(), player.getName());
        apiClient.link(request).thenAccept(response ->
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
            JsonObject json = safeJson(response.body());
            String error = json.has("error") ? json.get("error").getAsString() : "unknown";
            player.sendMessage(Component.text(errorMessage(error), NamedTextColor.RED));
            return;
        }

        JsonObject json = safeJson(response.body());
        String discord = json.has("discordUsername") ? json.get("discordUsername").getAsString() : "Discord";
        player.sendMessage(Component.text("Акаунт прив'язано до ", NamedTextColor.GREEN)
                .append(Component.text(discord, NamedTextColor.AQUA))
                .append(Component.text("!", NamedTextColor.GREEN)));
        statsPushService.pushPlayer(player);
    }

    private JsonObject safeJson(String body) {
        try {
            return apiClient.parseJson(body);
        } catch (Exception ex) {
            return new JsonObject();
        }
    }

    private static String errorMessage(String error) {
        return switch (error) {
            case "invalid_code" -> "Невірний код. Згенеруй новий на сайті.";
            case "expired_code" -> "Код протермінований. Згенеруй новий на сайті.";
            case "already_linked" -> "Цей Minecraft-акаунт уже прив'язаний.";
            default -> "Не вдалося прив'язати акаунт (" + error + ").";
        };
    }
}
