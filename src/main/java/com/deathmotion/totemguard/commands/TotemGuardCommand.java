/*
 * This file is part of TotemGuard - https://github.com/Bram1903/TotemGuard
 * Copyright (C) 2024 Bram and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.deathmotion.totemguard.commands;

import com.deathmotion.totemguard.TotemGuard;
import com.deathmotion.totemguard.config.ConfigManager;
import com.deathmotion.totemguard.config.Settings;
import com.deathmotion.totemguard.data.Constants;
import com.deathmotion.totemguard.manager.AlertManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Stream;

public class TotemGuardCommand implements CommandExecutor, TabExecutor {

    private final TotemGuard plugin;
    private final ConfigManager configManager;
    private final AlertManager alertManager;

    private Component versionComponent;

    public TotemGuardCommand(TotemGuard plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.alertManager = plugin.getAlertManager();

        plugin.getCommand("totemguard").setExecutor(this);
        loadVersionComponent();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 && !hasRequiredPermissions(sender) || args.length == 1 && args[0].equalsIgnoreCase("info")) {
            sender.sendMessage(versionComponent);
            return true;
        }

        if (!hasRequiredPermissions(sender)) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return false;
        }

        if (args.length == 0) {
            sendPrefixMessage(sender, Component.text("Usage: /totemguard <alerts|reload|info>", NamedTextColor.RED));
            return false;
        }

        return switch (args[0].toLowerCase()) {
            case "alerts" -> handleAlertsCommand(sender, args);
            case "reload" -> handleReloadCommand(sender);
            default -> {
                sendPrefixMessage(sender, Component.text("Usage: /totemguard <alerts|reload|info>", NamedTextColor.RED));
                yield false;
            }
        };
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!hasRequiredPermissions(sender)) {
            return List.of("info");
        }

        if (args.length == 1) {
            return Stream.of("alerts", "reload", "info")
                    .filter(option -> option.startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("alerts") && sender.hasPermission("TotemGuard.Alerts.Others")) {
            String argsLowerCase = args[1].toLowerCase();

            if (sender instanceof Player) {
                String senderName = sender.getName().toLowerCase();
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> !name.toLowerCase().equals(senderName)) // Prevent self-suggestion
                        .filter(name -> name.toLowerCase().startsWith(argsLowerCase))
                        .toList();
            }

            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(argsLowerCase))
                    .toList();
        }

        return List.of();
    }

    private boolean hasRequiredPermissions(CommandSender sender) {
        return sender.hasPermission("TotemGuard.Alerts") || sender.hasPermission("TotemGuard.Reload");
    }

    private boolean handleAlertsCommand(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return toggleAlertsForSender(sender);
        } else if (sender.hasPermission("TotemGuard.Alerts.Others")) {
            return toggleAlertsForOther(sender, args[1]);
        } else {
            sender.sendMessage(Component.text("You do not have permission to toggle alerts for other players!", NamedTextColor.RED));
            return false;
        }
    }

    private boolean toggleAlertsForSender(CommandSender sender) {
        if (sender instanceof Player player) {
            alertManager.toggleAlerts(player);
            return true;
        } else {
            sendPrefixMessage(sender, Component.text("Only players can toggle alerts!", NamedTextColor.RED));
            return false;
        }
    }

    private boolean toggleAlertsForOther(CommandSender sender, String targetName) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            sendPrefixMessage(sender, Component.text("Player not found!", NamedTextColor.RED));
            return false;
        }

        if (sender instanceof Player player) {
            if ((player.getUniqueId().equals(target.getUniqueId()))) {
                alertManager.toggleAlerts(player);
                return true;
            }
        }

        alertManager.toggleAlerts(target);
        boolean alertsEnabled = alertManager.hasAlertsEnabled(target);

        sendPrefixMessage(sender, Component.text((alertsEnabled ? "Enabled" : "Disabled") + " alerts for " + target.getName() + "!").color(alertsEnabled ? NamedTextColor.GREEN : NamedTextColor.RED));
        return true;
    }

    private boolean handleReloadCommand(CommandSender sender) {
        configManager.reload();
        sendPrefixMessage(sender, Component.text("The configuration has been reloaded!", NamedTextColor.GREEN));
        return true;
    }

    private void sendPrefixMessage(CommandSender sender, Component message) {
        final Settings settings = plugin.getConfigManager().getSettings();

        Component prefixMessage = Component.text()
                .append(LegacyComponentSerializer.legacyAmpersand().deserialize(settings.getPrefix()))
                .append(message)
                .build();

        sender.sendMessage(prefixMessage);
    }

    private void loadVersionComponent() {
        versionComponent = Component.text()
                .append(Component.text("⚡", NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
                .append(Component.text(" Running ", NamedTextColor.GRAY))
                .append(Component.text("TotemGuard", NamedTextColor.GREEN).decorate(TextDecoration.BOLD))
                .append(Component.text(" v" + plugin.getVersion().toString(), NamedTextColor.GREEN).decorate(TextDecoration.BOLD))
                .append(Component.text(" by ", NamedTextColor.GRAY).decorate(TextDecoration.BOLD))
                .append(Component.text("Bram", NamedTextColor.GREEN).decorate(TextDecoration.BOLD))
                .append(Component.text(" and ", NamedTextColor.GRAY).decorate(TextDecoration.BOLD))
                .append(Component.text("OutDev", NamedTextColor.GREEN).decorate(TextDecoration.BOLD))
                .hoverEvent(HoverEvent.showText(Component.text("Open Github Page!", NamedTextColor.GREEN)
                        .decorate(TextDecoration.BOLD)
                        .decorate(TextDecoration.UNDERLINED)))
                .clickEvent(ClickEvent.openUrl(Constants.GITHUB_URL))
                .build();
    }

}
