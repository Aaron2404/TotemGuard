package com.strealex.totemguard.checks;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.User;
import com.strealex.totemguard.TotemGuard;
import com.strealex.totemguard.config.Settings;
import com.strealex.totemguard.discord.DiscordWebhook;
import com.strealex.totemguard.manager.AlertManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public abstract class Check {

    private final ConcurrentHashMap<UUID, Integer> violations;

    private final String checkName;
    private final String checkDescription;
    private final int maxViolations;
    private final boolean experimental;

    private final TotemGuard plugin;
    private final Settings settings;
    private final AlertManager alertManager;

    public Check(TotemGuard plugin, String checkName, String checkDescription, int maxViolations, boolean experimental) {
        this.plugin = plugin;
        this.checkName = checkName;
        this.checkDescription = checkDescription;
        this.maxViolations = maxViolations;
        this.experimental = experimental;

        this.violations = new ConcurrentHashMap<>();

        this.settings = plugin.getConfigManager().getSettings();
        this.alertManager = plugin.getAlertManager();

        long resetInterval = settings.getPunish().getRemoveFlagsMin() * 60L * 20L; // Convert minutes to ticks (20 ticks = 1 second)
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::resetData, resetInterval, resetInterval);
    }

    public Check(TotemGuard plugin, String checkName, String checkDescription, int maxViolations) {
        this(plugin, checkName, checkDescription, maxViolations, false);
    }

    public final void flag(Player player, Component details) {
        UUID uuid = player.getUniqueId();
        int totalViolations = violations.compute(uuid, (key, value) -> value == null ? 1 : value + 1);

        User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        int ping = player.getPing();
        int tps = (int) Math.round(Bukkit.getTPS()[0]);

        String gamemode = String.valueOf(player.getGameMode());
        String clientBrand = Objects.requireNonNullElse(player.getClientBrandName(), "Unknown");

        Component message = createAlertComponent(tps, user, clientBrand, player, gamemode, ping, totalViolations, details);

        alertManager.sentAlert(message);
        sendWebhookMessage(player, totalViolations);
        punishPlayer(player, totalViolations);

    }

    public void resetData() {
        violations.clear();

        alertManager.sentAlert(Component.text()
                .append(LegacyComponentSerializer.legacyAmpersand().deserialize(settings.getPrefix()))
                .append(Component.text("All flag counts have been reset.", NamedTextColor.GREEN))
                .build());
    }

    private void sendWebhookMessage(Player player, int totalViolations) {
        if (!settings.getWebhook().isEnabled()) return;

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", player.getName());
            placeholders.put("check", checkName);
            placeholders.put("violations", String.valueOf(totalViolations));
            placeholders.put("max_violations", String.valueOf(maxViolations));
            placeholders.put("client_brand", player.getClientBrandName());
            placeholders.put("ping", String.valueOf(player.getPing()));
            placeholders.put("tps", String.valueOf((int) Bukkit.getTPS()[0]));

            DiscordWebhook.sendWebhook(placeholders);
        });
    }

    private void punishPlayer(Player player, int totalViolations) {
        if (!settings.getPunish().isEnabled()) return;

        if (totalViolations >= maxViolations) {
            String punishCommand = settings.getPunish().getPunishCommand().replace("%player%", player.getName());
            violations.remove(player.getUniqueId());

            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), punishCommand);
            });
        }
    }

    private Component createAlertComponent(int tps, User user, String clientBrand, Player player, String gamemode, int ping, int totalViolations, Component details) {
        Component hoverInfo = Component.text()
                .append(Component.text("TPS: ", NamedTextColor.GRAY))
                .append(Component.text(tps, NamedTextColor.GOLD))
                .append(Component.text(" |", NamedTextColor.DARK_GRAY))
                .append(Component.text(" Client Version: ", NamedTextColor.GRAY))
                .append(Component.text(user.getClientVersion().getReleaseName(), NamedTextColor.GOLD))
                .append(Component.text(" |", NamedTextColor.DARK_GRAY))
                .append(Component.text(" Client Brand: ", NamedTextColor.GRAY))
                .append(Component.text(clientBrand, NamedTextColor.GOLD))
                .append(Component.newline())
                .append(Component.newline())
                .append(Component.text("Player: ", NamedTextColor.GRAY))
                .append(Component.text(player.getName(), NamedTextColor.GOLD))
                .append(Component.newline())
                .append(Component.text("Gamemode: ", NamedTextColor.GRAY))
                .append(Component.text(gamemode, NamedTextColor.GOLD))
                .append(Component.newline())
                .append(Component.text("Ping: ", NamedTextColor.GRAY))
                .append(Component.text(ping + "ms", NamedTextColor.GOLD))
                .append(Component.newline())
                .append(Component.newline())
                .append(details)
                .append(Component.newline())
                .append(Component.newline())
                .append(Component.text("Click to ", NamedTextColor.GRAY))
                .append(Component.text("teleport ", NamedTextColor.GOLD))
                .append(Component.text("to " + player.getName() + ".", NamedTextColor.GRAY))
                .build();

        Component message = Component.text()
                .append(LegacyComponentSerializer.legacyAmpersand().deserialize(settings.getPrefix()))
                .append(Component.text(player.getName(), NamedTextColor.YELLOW))
                .append(Component.text(" failed ", NamedTextColor.GRAY))
                .append(Component.text(checkName, NamedTextColor.GOLD)
                        .hoverEvent(HoverEvent.showText(Component.text(checkDescription, NamedTextColor.GRAY))))
                .clickEvent(ClickEvent.runCommand("/tp " + player.getName()))
                .build();

        // Determine the violation format based on whether punishment is enabled
        Component totalViolationsComponent;
        if (settings.getPunish().isEnabled()) {
            totalViolationsComponent = Component.text()
                    .append(Component.text(" VL[", NamedTextColor.GRAY))
                    .append(Component.text(totalViolations + "/" + maxViolations, NamedTextColor.GOLD))
                    .append(Component.text("]", NamedTextColor.GRAY))
                    .build();

        } else {
            totalViolationsComponent = Component.text()
                    .append(Component.text(" VL[", NamedTextColor.GRAY))
                    .append(Component.text(totalViolations, NamedTextColor.GOLD))
                    .append(Component.text("]", NamedTextColor.GRAY))
                    .build();

        }
        message = message.append(totalViolationsComponent);

        message = message
                .append(Component.text(" [Info]", NamedTextColor.DARK_GRAY)
                        .hoverEvent(HoverEvent.showText(hoverInfo)));

        // Add a * if the check is experimental
        if (experimental) {
            message = message.append(Component.text(" *", NamedTextColor.RED).decorate(TextDecoration.BOLD));
        }

        return message;
    }
}
