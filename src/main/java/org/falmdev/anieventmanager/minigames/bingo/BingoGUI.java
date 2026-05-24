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
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.falmdev.anieventmanager.Anieventmanager;

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

        fillBorders(inv);

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
                inv.setItem(GRID_SLOTS[i], buildEmptySlot());
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

        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();

        // Nombre con color codes
        Component nameComponent = LegacyComponentSerializer.legacyAmpersand()
                .deserialize(displayName)
                .decoration(TextDecoration.ITALIC, false);

        if (task.isCompleted() && !displayName.contains("&")) {
            nameComponent = Component.text(displayName, NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false);
        }

        meta.displayName(nameComponent);

        List<Component> lore = new ArrayList<>();

        // Descripción — soporta \n como saltos de línea reales
        if (description != null && !description.isEmpty()) {
            lore.add(Component.empty());
            for (String line : description.split("\n")) {
                lore.add(LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(line)
                        .decoration(TextDecoration.ITALIC, false));
            }
        }

        lore.add(Component.empty());

        // Estado
        if (task.isCompleted()) {
            lore.add(Component.text("✔ Completada", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("✘ Pendiente", NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
        }

        // Progreso — tipos que usan barra
        boolean showProgress = switch (task.getType()) {
            case OBTAIN_ITEM, CRAFT_ITEM, KILL_MOB, TRADE_ANY, TRADE_ITEM -> true;
            default -> false;
        };

        if (showProgress) {
            lore.add(Component.empty());
            lore.add(Component.text("Progreso: ", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(task.getProgress() + "/" + task.getRequired(),
                                    task.isCompleted() ? NamedTextColor.GREEN : NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false)));
            lore.add(buildProgressBar(task.getProgress(), task.getRequired()));
        }

        lore.add(Component.empty());
        lore.add(Component.text("Tipo: " + prettyType(task.getType()), NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        hideItemDetails(meta);
        item.setItemMeta(meta);
        return item;
    }

    private static Component buildProgressBar(int current, int required) {
        int bars   = 10;
        int filled = required > 0 ? Math.min(bars, (current * bars) / required) : bars;
        int empty  = bars - filled;

        Component bar = Component.text("  ", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false);
        for (int i = 0; i < filled; i++)
            bar = bar.append(Component.text("█", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
        for (int i = 0; i < empty; i++)
            bar = bar.append(Component.text("░", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        return bar;
    }

    private static ItemStack buildInfoItem(BingoCard card) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta  meta = item.getItemMeta();

        meta.displayName(
                Component.text("✦ ", NamedTextColor.GOLD)
                        .append(Component.text(card.getTeam().getDisplayName(), card.getTeam().getColor()))
                        .decoration(TextDecoration.ITALIC, false));

        int done    = card.getCompletedCount();
        int total   = card.getTotalTasks();
        int percent = card.getCompletionPercent();

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Completadas: ", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(done + "/" + total, NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false)));
        lore.add(Component.text("Porcentaje: ", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(percent + "%",
                                percent == 100 ? NamedTextColor.GREEN : NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false)));
        lore.add(Component.empty());
        lore.add(buildProgressBar(done, total));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildTeleportItem(BingoConfig config) {
        ItemStack item = new ItemStack(Material.BEACON);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.text("✦ Teletransportarse al spawn", NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        if (config.getSpawn() != null) {
            org.bukkit.Location s = config.getSpawn();
            lore.add(Component.text("Click para ir al spawn del Bingo.", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text(String.format("%.1f, %.1f, %.1f", s.getX(), s.getY(), s.getZ()),
                    NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Spawn no configurado.", NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        hideItemDetails(meta);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildEmptySlot() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static void fillBorders(Inventory inv) {
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta  meta   = border.getItemMeta();
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        border.setItemMeta(meta);

        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int row = 1; row <= 5; row++) {
            inv.setItem(row * 9,     border);
            inv.setItem(row * 9 + 1, border);
            inv.setItem(row * 9 + 6, border);
            inv.setItem(row * 9 + 7, border);
            inv.setItem(row * 9 + 8, border);
        }
        for (int i = 45; i < 54; i++) inv.setItem(i, border);
    }

    // ── Listener ──────────────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof BingoHolder holder) {
            event.setCancelled(true);

            if (event.getRawSlot() != TELEPORT_SLOT) return;
            if (!(event.getWhoClicked() instanceof Player player)) return;

            org.bukkit.Location spawn = holder.getConfig().getSpawn();
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

    private void startTeleportCountdown(Player player, org.bukkit.Location destination) {
        UUID uid = player.getUniqueId();
        teleportOrigins.put(uid, player.getLocation().clone());

        int[] count = { 5 };

        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            // Verificar que el jugador sigue online
            if (!player.isOnline()) {
                cancelTeleport(uid);
                return;
            }

            // Verificar que no se haya movido (comparar bloque XZ+Y)
            Location origin = teleportOrigins.get(uid);
            Location current = player.getLocation();
            if (origin != null && (
                    Math.abs(current.getX() - origin.getX()) > 0.15 ||
                            Math.abs(current.getY() - origin.getY()) > 0.15 ||
                            Math.abs(current.getZ() - origin.getZ()) > 0.15)) {
                cancelTeleport(uid);
                player.sendMessage(Component.text(
                        "✘ Teletransporte cancelado por movimiento.", NamedTextColor.RED));
                Title cancelTitle = Title.title(
                        Component.text("✘ Cancelado", NamedTextColor.RED),
                        Component.text("Te moviste", NamedTextColor.GRAY),
                        Title.Times.times(
                                Duration.ofMillis(100),
                                Duration.ofMillis(800),
                                Duration.ofMillis(300)
                        )
                );
                player.showTitle(cancelTitle);
                return;
            }

            if (count[0] > 0) {
                // Mostrar countdown
                Title countdown = Title.title(
                        Component.text(String.valueOf(count[0]), NamedTextColor.YELLOW),
                        Component.text("No te muevas...", NamedTextColor.GRAY),
                        Title.Times.times(
                                Duration.ofMillis(50),
                                Duration.ofMillis(900),
                                Duration.ofMillis(50)
                        )
                );
                player.showTitle(countdown);
                count[0]--;
            } else {
                // Teletransportar
                cancelTeleport(uid);
                player.teleport(destination);
                player.sendMessage(Component.text(
                        "✦ Teletransportado al spawn del Bingo.", NamedTextColor.AQUA));
                Title goTitle = Title.title(
                        Component.text("✦ ¡Listo!", NamedTextColor.GREEN),
                        Component.text("Spawn del Bingo", NamedTextColor.AQUA),
                        Title.Times.times(
                                Duration.ofMillis(100),
                                Duration.ofMillis(1200),
                                Duration.ofMillis(400)
                        )
                );
                player.showTitle(goTitle);
            }
        }, 0L, 20L);

        pendingTeleports.put(uid, task);
    }

    private void cancelTeleport(UUID uid) {
        BukkitTask task = pendingTeleports.remove(uid);
        if (task != null && !task.isCancelled()) task.cancel();
        teleportOrigins.remove(uid);
    }

    private static void hideItemDetails(ItemMeta meta) {
        meta.addItemFlags(
                ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_UNBREAKABLE,
                ItemFlag.HIDE_DESTROYS,
                ItemFlag.HIDE_PLACED_ON
        );
    }
}