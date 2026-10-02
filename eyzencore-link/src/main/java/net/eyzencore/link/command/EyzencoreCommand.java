package net.eyzencore.link.command;

import net.eyzencore.link.EyzencoreLinkPlugin;
import net.eyzencore.link.stats.StatsPushService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class EyzencoreCommand implements CommandExecutor, TabCompleter {

    private final EyzencoreLinkPlugin plugin;
    private final StatsPushService statsPushService;

    public EyzencoreCommand(EyzencoreLinkPlugin plugin, StatsPushService statsPushService) {
        this.plugin = plugin;
        this.statsPushService = statsPushService;
        PluginCommandTab.bind(plugin, this);
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!sender.hasPermission("eyzencore.admin")) {
            sender.sendMessage(Component.text("Немає прав.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(Component.text("Використання: /eyzencore <reload|push> [нік]", NamedTextColor.YELLOW));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                plugin.reloadPlugin();
                sender.sendMessage(Component.text("Конфіг EyzencoreLink перезавантажено.", NamedTextColor.GREEN));
            }
            case "push" -> {
                if (args.length >= 2) {
                    OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                    if (!target.hasPlayedBefore() && !target.isOnline()) {
                        sender.sendMessage(Component.text("Гравця не знайдено.", NamedTextColor.RED));
                        return true;
                    }
                    statsPushService.pushPlayer(target);
                    sender.sendMessage(Component.text("Статистику відправлено для " + args[1], NamedTextColor.GREEN));
                } else if (sender instanceof Player player) {
                    statsPushService.pushPlayer(player);
                    sender.sendMessage(Component.text("Твою статистику відправлено.", NamedTextColor.GREEN));
                } else {
                    statsPushService.pushDirtyOnline();
                    sender.sendMessage(Component.text("Відправлено статистику онлайн-гравців.", NamedTextColor.GREEN));
                }
            }
            default -> sender.sendMessage(Component.text("Невідома підкоманда. Використай reload або push.", NamedTextColor.RED));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("reload", "push")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("push")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(player.getName());
                }
            }
        }
        return out;
    }

    /** Tiny helper so tab completer is registered without cluttering the main plugin class. */
    private static final class PluginCommandTab {
        static void bind(EyzencoreLinkPlugin plugin, EyzencoreCommand executor) {
            var cmd = plugin.getCommand("eyzencore");
            if (cmd != null) {
                cmd.setTabCompleter(executor);
            }
        }
    }
}
