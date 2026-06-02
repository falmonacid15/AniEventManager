package org.falmdev.anieventmanager.minigames.bingo;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.utils.gui.GuiUtil;
import org.falmdev.anieventmanager.utils.gui.HeadUtil;
import org.falmdev.anieventmanager.utils.gui.ItemBuilder;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class BingoGUI implements Listener {

    private static final int[] GRID_SLOTS = {
            11, 12, 13, 14, 15,
            20, 21, 22, 23, 24,
            29, 30, 31, 32, 33,
            38, 39, 40, 41, 42,
            47, 48, 49, 50, 51
    };

    private static final int INFO_SLOT = 50;
    private static final int TELEPORT_SLOT = 48;

    private final Map<UUID, BukkitTask> pendingTeleports = new HashMap<>();
    private final Map<UUID, Location>   teleportOrigins  = new HashMap<>();

    private final Anieventmanager plugin;

    public BingoGUI(Anieventmanager plugin) {
        this.plugin = plugin;
    }

    // ── Abrir GUI ─────────────────────────────────────────────────────────────

    public static void open(Player player, BingoCard card, BingoConfig config) {
        Inventory inv = Bukkit.createInventory(new BingoHolder(config), 54,
                Component.text("✦ Tarjeta de Bingo", NamedTextColor.GOLD));

        GuiUtil.fillBorders(inv);

        Map<String, BingoTask> configTasks = config.loadTasks().stream()
                .collect(Collectors.toMap(BingoTask::getId, t -> t));

        List<BingoTask> cardTasks = card.getTasks();
        for (int i = 0; i < GRID_SLOTS.length; i++) {
            if (i < cardTasks.size()) {
                BingoTask inMemory  = cardTasks.get(i);
                BingoTask fromConfig = configTasks.get(inMemory.getId());

                String displayName = fromConfig != null
                        ? fromConfig.getDisplayName()
                        : inMemory.getDisplayName();
                Material icon = fromConfig != null && fromConfig.hasCustomIcon()
                        ? fromConfig.getIcon()
                        : null;
                String description = fromConfig != null
                        ? fromConfig.getDescription()
                        : inMemory.getDescription();

                inv.setItem(GRID_SLOTS[i], buildTaskItem(inMemory, displayName, icon, description));
            } else {
                inv.setItem(GRID_SLOTS[i], GuiUtil.emptyPane());
            }
        }

        inv.setItem(INFO_SLOT, buildInfoItem(card));
        inv.setItem(TELEPORT_SLOT, buildTeleportItem(config));
        player.openInventory(inv);
    }

    // ── Construcción de ítems ─────────────────────────────────────────────────

    private static ItemStack buildTaskItem(BingoTask task, String displayName,
                                           Material icon, String description) {
        Material mat = icon != null
                ? (task.isCompleted() ? Material.LIME_STAINED_GLASS_PANE : icon)
                : (task.isCompleted()
                   ? Material.LIME_STAINED_GLASS_PANE
                   : Material.RED_STAINED_GLASS_PANE);

        // Construir el nombre — soporta &-codes via LegacyComponentSerializer
        Component nameComponent;
        if (task.isCompleted() && !displayName.contains("&")) {
            nameComponent = Component.text(displayName, NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false);
        } else {
            nameComponent = LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(displayName)
                    .decoration(TextDecoration.ITALIC, false);
        }

        ItemBuilder b = ItemBuilder.of(mat).name(nameComponent);

        // Descripción
        if (description != null && !description.isEmpty()) {
            b.emptyLine();
            for (String line : description.split("\n")) {
                b.lore(LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(line)
                        .decoration(TextDecoration.ITALIC, false));
            }
        }

        b.emptyLine();

        // Estado
        if (task.isCompleted()) {
            b.lore(NamedTextColor.GREEN, "✔ Completada");
        } else {
            b.lore(NamedTextColor.RED, "✘ Pendiente");
        }

        // Progreso
        boolean showProgress = switch (task.getType()) {
            case OBTAIN_ITEM, CRAFT_ITEM, KILL_MOB, TRADE_ANY, TRADE_ITEM -> true;
            default -> false;
        };

        if (showProgress) {
            b.emptyLine();
            b.lore(GuiUtil.noItalic(Component.text("Progreso: ", NamedTextColor.GRAY)
                    .append(Component.text(task.getProgress() + "/" + task.getRequired(),
                            task.isCompleted() ? NamedTextColor.GREEN : NamedTextColor.YELLOW))));
            b.lore(buildProgressBar(task.getProgress(), task.getRequired()));
        }

        b.emptyLine();
        b.lore(NamedTextColor.DARK_GRAY, "Tipo: " + prettyType(task.getType()));

        return b.hideDetails().build();
    }

    private static Component buildProgressBar(int current, int required) {
        int bars   = 10;
        int filled = required > 0 ? Math.min(bars, (current * bars) / required) : bars;
        int empty  = bars - filled;

        Component bar = GuiUtil.noItalic(Component.text("  ", NamedTextColor.GRAY));
        for (int i = 0; i < filled; i++)
            bar = bar.append(GuiUtil.noItalic(Component.text("█", NamedTextColor.GREEN)));
        for (int i = 0; i < empty; i++)
            bar = bar.append(GuiUtil.noItalic(Component.text("░", NamedTextColor.DARK_GRAY)));
        return bar;
    }

    private static ItemStack buildInfoItem(BingoCard card) {
        int done    = card.getCompletedCount();
        int total   = card.getTotalTasks();
        int percent = card.getCompletionPercent();

        String base64 = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTcxYTIyODVjOTFjNmM3Mjc0NzYwNDgxOWVlNTIyM2E5MGFhNTFlNmU3OWU0ZjlhZjY2MjhlYzhmMGRkN2RmYyJ9fX0=";

        return ItemBuilder.of(HeadUtil.fromBase64(base64))
                .name(GuiUtil.noItalic(Component.text("✦ ", NamedTextColor.GOLD)
                        .append(Component.text(card.getTeam().getDisplayName(), card.getTeam().getColor()))))
                .emptyLine()
                .lore(GuiUtil.label("Completadas",
                        Component.text(done + "/" + total, NamedTextColor.YELLOW)))
                .lore(GuiUtil.label("Porcentaje",
                        Component.text(percent + "%",
                                percent == 100 ? NamedTextColor.GREEN : NamedTextColor.AQUA)))
                .emptyLine()
                .lore(buildProgressBar(done, total))
                .build();
    }

    private static ItemStack buildTeleportItem(BingoConfig config) {
        ItemBuilder b = ItemBuilder.of(Material.BEACON)
                .name("✦ Teletransportarse al spawn", NamedTextColor.AQUA)
                .emptyLine();

        if (config.getSpawn() != null) {
            Location s = config.getSpawn();
            b.lore(NamedTextColor.YELLOW, "Click para ir al spawn del Bingo.");
            b.emptyLine();
            b.lore(NamedTextColor.DARK_GRAY,
                    String.format("%.1f, %.1f, %.1f", s.getX(), s.getY(), s.getZ()));
        } else {
            b.lore(NamedTextColor.RED, "Spawn no configurado.");
        }

        return b.hideDetails().build();
    }

    // ── Listener ──────────────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof BingoHolder holder) {
            event.setCancelled(true);

            if (event.getRawSlot() != TELEPORT_SLOT) return;
            if (!(event.getWhoClicked() instanceof Player player)) return;

            Location spawn = holder.getConfig().getSpawn();
            if (spawn == null) {
                player.sendMessage(Component.text(
                        "El spawn del Bingo no está configurado.", NamedTextColor.RED));
                return;
            }
            if (pendingTeleports.containsKey(player.getUniqueId())) return;
            player.closeInventory();
            startTeleportCountdown(player, spawn);
        }
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private static String prettyType(BingoTask.Type type) {
        return switch (type) {
            case OBTAIN_ITEM     -> "Obtener ítem";
            case CRAFT_ITEM      -> "Craftear ítem";
            case KILL_MOB        -> "Matar mob";
            case REACH_LOCATION  -> "Llegar a lugar";
            case EQUIP_ITEM      -> "Equipar ítem";
            case FISH_ITEM       -> "Pescar ítem";
            case VISIT_STRUCTURE -> "Visitar estructura";
            case TRADE_ANY       -> "Tradear con aldeano";
            case TRADE_ITEM      -> "Tradear ítem";
        };
    }

    private void startTeleportCountdown(Player player, Location destination) {
        UUID uid = player.getUniqueId();
        teleportOrigins.put(uid, player.getLocation().clone());

        int[] count = { 5 };

        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                cancelTeleport(uid);
                return;
            }

            Location origin = teleportOrigins.get(uid);
            Location current = player.getLocation();
            if (origin != null && (
                    Math.abs(current.getX() - origin.getX()) > 0.15 ||
                            Math.abs(current.getY() - origin.getY()) > 0.15 ||
                            Math.abs(current.getZ() - origin.getZ()) > 0.15)) {
                cancelTeleport(uid);
                player.sendMessage(Component.text(
                        "✘ Teletransporte cancelado por movimiento.", NamedTextColor.RED));
                player.showTitle(Title.title(
                        Component.text("✘ Cancelado", NamedTextColor.RED),
                        Component.text("Te moviste", NamedTextColor.GRAY),
                        Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(800), Duration.ofMillis(300))));
                return;
            }

            if (count[0] > 0) {
                player.showTitle(Title.title(
                        Component.text(String.valueOf(count[0]), NamedTextColor.YELLOW),
                        Component.text("No te muevas...", NamedTextColor.GRAY),
                        Title.Times.times(Duration.ofMillis(50), Duration.ofMillis(900), Duration.ofMillis(50))));
                count[0]--;
            } else {
                cancelTeleport(uid);
                player.teleport(destination);
                player.sendMessage(Component.text(
                        "✦ Teletransportado al spawn del Bingo.", NamedTextColor.AQUA));
                player.showTitle(Title.title(
                        Component.text("✦ ¡Listo!", NamedTextColor.GREEN),
                        Component.text("Spawn del Bingo", NamedTextColor.AQUA),
                        Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1200), Duration.ofMillis(400))));
            }
        }, 0L, 20L);

        pendingTeleports.put(uid, task);
    }

    private void cancelTeleport(UUID uid) {
        BukkitTask task = pendingTeleports.remove(uid);
        if (task != null && !task.isCancelled()) task.cancel();
        teleportOrigins.remove(uid);
    }
}