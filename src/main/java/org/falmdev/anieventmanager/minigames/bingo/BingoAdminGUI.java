package org.falmdev.anieventmanager.minigames.bingo;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.utils.gui.GuiUtil;
import org.falmdev.anieventmanager.utils.gui.ItemBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BingoAdminGUI implements Listener {

    public static final String TITLE = "Bingo — Configuración";

    // ── Pestañas ──────────────────────────────────────────────────────────────
    private static final int TAB_CONFIG = 12;
    private static final int TAB_TASKS  = 13;
    private static final int TAB_WALLS  = 14;

    // ── Pestaña CONFIG ────────────────────────────────────────────────────────
    private static final int CFG_SPAWN     = 20;
    private static final int CFG_LOBBY     = 21;
    private static final int CFG_DURATION  = 23;
    private static final int CFG_COUNTDOWN = 24;
    private static final int CFG_SCORE_1   = 39;
    private static final int CFG_SCORE_2   = 40;
    private static final int CFG_SCORE_3   = 41;
    private static final int CFG_SCORE_DEF = 42;

    // ── Pestaña TASKS ─────────────────────────────────────────────────────────
    private static final int[] TASK_SLOTS = {
            19,20,21,22,23,24,25,
            28,29,30,31,32,33,34
    };

    // ── Pestaña WALLS ─────────────────────────────────────────────────────────
    private static final int[] WALL_SLOTS = {
            19,20,21,22,23,24,25,
            28,29,30,31,32,33,34
    };

    // ── Fila 5 — navegación ───────────────────────────────────────────────────
    // GuiUtil.fillNavigationHomeOnly → 50=⌂Inicio, 49=libre
    private static final int NAV_MAGIC_STICK = 4;
    private static final int NAV_START       = 16;
    private static final int NAV_STOP        = 10;

    private final Anieventmanager plugin;
    private final Map<UUID, Integer>      activeTabs    = new HashMap<>();
    private final Map<UUID, Integer>      taskPages     = new HashMap<>();
    private final Map<UUID, Integer>      wallPages     = new HashMap<>();
    private final Map<UUID, PendingInput> awaitingInput = new HashMap<>();

    private enum InputType { DURATION, COUNTDOWN, SCORE_1, SCORE_2, SCORE_3, SCORE_DEF }
    private record PendingInput(InputType type) {}

    public BingoAdminGUI(Anieventmanager plugin) { this.plugin = plugin; }

    public void open(Player player) {
        render(player, activeTabs.getOrDefault(player.getUniqueId(), 0));
    }

    private void render(Player player, int tab) {
        activeTabs.put(player.getUniqueId(), tab);

        Inventory inv = Bukkit.createInventory(null, 54, Component.text(TITLE, NamedTextColor.GOLD));
        GuiUtil.fillSlots(inv, GuiUtil.emptyPane(), 0,1,9,7,8,17,36,45,46,52,53,44);


        // Pestañas
        inv.setItem(TAB_CONFIG, buildTab("Config",  Material.COMPARATOR, tab == 0));
        inv.setItem(TAB_TASKS,  buildTab("Tareas",  Material.PAPER,      tab == 1));
        inv.setItem(TAB_WALLS,  buildTab("Paredes", Material.BARRIER,    tab == 2));

        // ── Fila 5: HomeOnly + controles en slots libres ──────────────────────
        GuiUtil.fillNavigationHomeOnly(inv); // 50=⌂Inicio, resto negro

        inv.setItem(NAV_MAGIC_STICK, ItemBuilder.of(Material.BLAZE_ROD)
                .name("✦ Magic Stick", NamedTextColor.GOLD, TextDecoration.BOLD)
                .emptyLine()
                .lore(NamedTextColor.YELLOW, "Click para obtener el Magic Stick.")
                .lore(NamedTextColor.DARK_GRAY, "Marca spawns y esquinas de paredes.")
                .build());

        boolean running = plugin.getBingoMiniGame().isRunning();
        if (!running) {
            boolean configured = plugin.getBingoMiniGame().validateConfig() == null;
            inv.setItem(NAV_START, configured
                    ? ItemBuilder.of(Material.LIME_CONCRETE).name("▶ Iniciar Bingo", NamedTextColor.GREEN, TextDecoration.BOLD).build()
                    : ItemBuilder.of(Material.GRAY_CONCRETE).name("▶ Iniciar Bingo", NamedTextColor.DARK_GRAY, TextDecoration.BOLD)
                      .lore(NamedTextColor.RED, plugin.getBingoMiniGame().validateConfig()).build());
        } else {
            inv.setItem(NAV_STOP, ItemBuilder.of(Material.RED_CONCRETE)
                    .name("■ Detener Bingo", NamedTextColor.RED, TextDecoration.BOLD).build());
        }

        switch (tab) {
            case 0 -> fillConfigTab(inv);
            case 1 -> fillTasksTab(inv, player);
            case 2 -> fillWallsTab(inv, player);
        }

        player.openInventory(inv);
    }

    // ── Pestaña 0: Config ─────────────────────────────────────────────────────

    private void fillConfigTab(Inventory inv) {
        BingoConfig cfg = plugin.getBingoMiniGame().getConfig();
        boolean hasSpawn = cfg.getSpawn() != null;
        inv.setItem(CFG_SPAWN, ItemBuilder.of(Material.NETHER_STAR)
                .name("Spawn del Bingo", hasSpawn ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD)
                .emptyLine()
                .lore(GuiUtil.noItalic(Component.text("Estado: ", NamedTextColor.GRAY)
                        .append(hasSpawn ? Component.text("✔ " + locStr(cfg.getSpawn()), NamedTextColor.GREEN)
                                : Component.text("✘ No configurado", NamedTextColor.RED))))
                .emptyLine().lore(NamedTextColor.YELLOW, "Click para setear en tu posición actual.").build());
        inv.setItem(CFG_LOBBY, ItemBuilder.of(Material.OAK_DOOR)
                .name("Mover al Lobby", NamedTextColor.AQUA, TextDecoration.BOLD).emptyLine()
                .lore(NamedTextColor.YELLOW, "Click para teleportar a todos los jugadores.").build());
        inv.setItem(CFG_DURATION,  buildNumericItem("Duración de Partida", Material.CLOCK, cfg.getDurationMinutes() + " minutos", "Tiempo total.", "Click para cambiar. (Mínimo: 1)"));
        inv.setItem(CFG_COUNTDOWN, buildNumericItem("Countdown", Material.CLOCK, cfg.getCountdownSeconds() + " segundos", "Cuenta regresiva.", "Click para cambiar. (Mínimo: 1)"));
        inv.setItem(38, ItemBuilder.of(Material.GOLD_INGOT).name("Puntajes por Posición", NamedTextColor.GOLD, TextDecoration.BOLD).build());
        inv.setItem(CFG_SCORE_1,   buildScoreItem(1,  cfg.getScoreForPlace(1),  "🥇", NamedTextColor.GOLD));
        inv.setItem(CFG_SCORE_2,   buildScoreItem(2,  cfg.getScoreForPlace(2),  "🥈", NamedTextColor.GRAY));
        inv.setItem(CFG_SCORE_3,   buildScoreItem(3,  cfg.getScoreForPlace(3),  "🥉", NamedTextColor.RED));
        inv.setItem(CFG_SCORE_DEF, buildScoreItem(-1, cfg.getScoreForPlace(4),  "  ", NamedTextColor.DARK_GRAY));
    }

    private void fillTasksTab(Inventory inv, Player player) {
        List<BingoTask> tasks = plugin.getBingoMiniGame().getConfig().loadTasks();
        int page = taskPages.getOrDefault(player.getUniqueId(), 0);
        int totalPages = Math.max(1, (int) Math.ceil(tasks.size() / (double) TASK_SLOTS.length));
        page = Math.max(0, Math.min(page, totalPages - 1));
        taskPages.put(player.getUniqueId(), page);
        int start = page * TASK_SLOTS.length;
        for (int i = 0; i < TASK_SLOTS.length; i++) {
            int taskIdx = start + i;
            if (taskIdx < tasks.size()) inv.setItem(TASK_SLOTS[i], buildTaskItem(tasks.get(taskIdx)));
        }
        inv.setItem(40, ItemBuilder.of(Material.PAPER).name("Tareas", NamedTextColor.GOLD, TextDecoration.BOLD).emptyLine()
                .lore(GuiUtil.label("Total", Component.text(tasks.size() + "/25", NamedTextColor.WHITE)))
                .lore(GuiUtil.label("Página", Component.text((page+1) + "/" + totalPages, NamedTextColor.WHITE))).build());
        if (page > 0)           inv.setItem(39, ItemBuilder.of(Material.ARROW).name("← Página anterior", NamedTextColor.YELLOW).build());
        if (page < totalPages-1) inv.setItem(41, ItemBuilder.of(Material.ARROW).name("Página siguiente →", NamedTextColor.YELLOW).build());
    }

    private ItemStack buildTaskItem(BingoTask task) {
        Material icon = task.hasCustomIcon() ? task.getIcon() : Material.PAPER;
        return ItemBuilder.of(icon).name(task.getDisplayName(), NamedTextColor.WHITE, TextDecoration.BOLD).emptyLine()
                .lore(GuiUtil.label("ID",   Component.text(task.getId(), NamedTextColor.DARK_GRAY)))
                .lore(GuiUtil.label("Tipo", Component.text(prettyType(task.getType()), NamedTextColor.WHITE)))
                .lore(GuiUtil.label("Desc", Component.text(task.getShortDescription(), NamedTextColor.GRAY)))
                .emptyLine().lore(NamedTextColor.YELLOW, "Click para editar.")
                .lore(NamedTextColor.RED, "Shift+Click para eliminar.").build();
    }

    private void fillWallsTab(Inventory inv, Player player) {
        List<BingoWall> walls = plugin.getBingoMiniGame().getConfig().loadWalls();
        int page = wallPages.getOrDefault(player.getUniqueId(), 0);
        int totalPages = Math.max(1, (int) Math.ceil(walls.size() / (double) WALL_SLOTS.length));
        page = Math.max(0, Math.min(page, totalPages - 1));
        wallPages.put(player.getUniqueId(), page);
        int start = page * WALL_SLOTS.length;
        for (int i = 0; i < WALL_SLOTS.length; i++) {
            int wallIdx = start + i;
            if (wallIdx < walls.size()) inv.setItem(WALL_SLOTS[i], buildWallItem(walls.get(wallIdx)));
        }
        inv.setItem(42, ItemBuilder.of(Material.LIME_DYE).name("+ Agregar pared", NamedTextColor.GREEN, TextDecoration.BOLD).emptyLine()
                .lore(NamedTextColor.YELLOW, "Click para iniciar selección de pared.").build());
        inv.setItem(40, ItemBuilder.of(Material.PAPER).name("Paredes", NamedTextColor.GOLD, TextDecoration.BOLD)
                .lore(GuiUtil.label("Total", Component.text(walls.size(), NamedTextColor.WHITE)))
                .lore(GuiUtil.label("Página", Component.text((page+1) + "/" + totalPages, NamedTextColor.WHITE))).build());
        if (page > 0)           inv.setItem(39, ItemBuilder.of(Material.ARROW).name("← Página anterior", NamedTextColor.YELLOW).build());
        if (page < totalPages-1) inv.setItem(41, ItemBuilder.of(Material.ARROW).name("Página siguiente →", NamedTextColor.YELLOW).build());
    }

    private ItemStack buildWallItem(BingoWall wall) {
        return ItemBuilder.of(Material.BARRIER).name("Pared: " + wall.getId(), NamedTextColor.AQUA, TextDecoration.BOLD).emptyLine()
                .lore(GuiUtil.label("Mundo", Component.text(wall.getWorld(), NamedTextColor.WHITE)))
                .lore(GuiUtil.label("Esquina 1", Component.text(wall.getX1() + ", " + wall.getY1() + ", " + wall.getZ1(), NamedTextColor.WHITE)))
                .lore(GuiUtil.label("Esquina 2", Component.text(wall.getX2() + ", " + wall.getY2() + ", " + wall.getZ2(), NamedTextColor.WHITE)))
                .emptyLine().lore(NamedTextColor.YELLOW, "Click → Colocar barriers.")
                .lore(NamedTextColor.GRAY, "Shift+Click → Quitar barriers.")
                .lore(NamedTextColor.RED, "Click derecho → Eliminar pared.").build();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!GuiUtil.getTitle(event.getView()).equals(TITLE)) return;
        event.setCancelled(true);
        if (!player.isOp()) return;

        int slot = event.getRawSlot();
        int tab  = activeTabs.getOrDefault(player.getUniqueId(), 0);

        // Pestañas
        if (slot == TAB_CONFIG) { render(player, 0); return; }
        if (slot == TAB_TASKS)  { render(player, 1); return; }
        if (slot == TAB_WALLS)  { render(player, 2); return; }

        // Navegación: null = pantalla raíz, no hay "atrás"
        if (GuiUtil.handleNavigation(slot, player, plugin, null)) return;

        // Magic Stick
        if (slot == NAV_MAGIC_STICK) {
            player.closeInventory();
            plugin.getBingoMagicStick().giveMagicStick(player);
            return;
        }

        // Start / Stop
        if (slot == NAV_START) {
            if (plugin.getBingoMiniGame().isRunning() || plugin.getBingoMiniGame().validateConfig() != null) return;
            plugin.getBingoMiniGame().start(); render(player, tab); return;
        }
        if (slot == NAV_STOP) {
            if (!plugin.getBingoMiniGame().isRunning()) return;
            plugin.getBingoMiniGame().forceStop(); render(player, tab); return;
        }

        // Paginación tareas/paredes
        if (tab == 1) {
            if (slot == 36) { taskPages.merge(player.getUniqueId(), -1, Integer::sum); render(player, 1); return; }
            if (slot == 44) { taskPages.merge(player.getUniqueId(),  1, Integer::sum); render(player, 1); return; }
            handleTaskClick(event, player, slot); return;
        }
        if (tab == 2) {
            if (slot == 36) { wallPages.merge(player.getUniqueId(), -1, Integer::sum); render(player, 2); return; }
            if (slot == 44) { wallPages.merge(player.getUniqueId(),  1, Integer::sum); render(player, 2); return; }
            handleWallClick(event, player, slot); return;
        }

        handleConfigClick(player, slot);
    }

    private void handleConfigClick(Player player, int slot) {
        BingoConfig cfg = plugin.getBingoMiniGame().getConfig();
        switch (slot) {
            case CFG_SPAWN    -> { cfg.setSpawn(player.getLocation()); ok(player, "Spawn seteado."); render(player, 0); }
            case CFG_LOBBY    -> { boolean ok = plugin.getBingoMiniGame().sendToSpawn(); if (ok) ok(player, "Jugadores teleportados."); else err(player, "Spawn no configurado."); }
            case CFG_DURATION  -> promptInput(player, InputType.DURATION,  "Duración en minutos (mínimo 1):");
            case CFG_COUNTDOWN -> promptInput(player, InputType.COUNTDOWN, "Countdown en segundos (mínimo 1):");
            case CFG_SCORE_1   -> promptInput(player, InputType.SCORE_1,   "Puntos para el 1er lugar:");
            case CFG_SCORE_2   -> promptInput(player, InputType.SCORE_2,   "Puntos para el 2do lugar:");
            case CFG_SCORE_3   -> promptInput(player, InputType.SCORE_3,   "Puntos para el 3er lugar:");
            case CFG_SCORE_DEF -> promptInput(player, InputType.SCORE_DEF, "Puntos por defecto:");
        }
    }

    private void handleTaskClick(InventoryClickEvent event, Player player, int slot) {
        List<BingoTask> tasks = plugin.getBingoMiniGame().getConfig().loadTasks();
        int page = taskPages.getOrDefault(player.getUniqueId(), 0);
        for (int i = 0; i < TASK_SLOTS.length; i++) {
            if (TASK_SLOTS[i] != slot) continue;
            int taskIdx = page * TASK_SLOTS.length + i;
            if (taskIdx >= tasks.size()) break;
            BingoTask task = tasks.get(taskIdx);
            if (event.isShiftClick()) {
                player.closeInventory();
                plugin.getConfirmGUI().open(player, "Eliminar tarea",
                        List.of("¿Eliminar la tarea '" + task.getId() + "'?", task.getShortDescription()),
                        () -> { plugin.getBingoMiniGame().getConfig().removeTask(task.getId()); ok(player, "Tarea eliminada."); render(player, 1); });
            } else {
                player.closeInventory();
                plugin.getBingoEditGUI().open(player, task);
            }
            break;
        }
    }

    private void handleWallClick(InventoryClickEvent event, Player player, int slot) {
        if (slot == 40) {
            player.closeInventory();
            player.sendMessage(Component.text("✎ Escribe el ID de la nueva pared en el chat:", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("  'cancelar' para cancelar.", NamedTextColor.GRAY));
            awaitingInput.put(player.getUniqueId(), new PendingInput(null)); return;
        }
        List<BingoWall> walls = plugin.getBingoMiniGame().getConfig().loadWalls();
        int page = wallPages.getOrDefault(player.getUniqueId(), 0);
        for (int i = 0; i < WALL_SLOTS.length; i++) {
            if (WALL_SLOTS[i] != slot) continue;
            int wallIdx = page * WALL_SLOTS.length + i;
            if (wallIdx >= walls.size()) break;
            BingoWall wall = walls.get(wallIdx);
            if (event.isShiftClick()) {
                plugin.getBingoWallManager().placeWall(wall, org.bukkit.Material.AIR);
                ok(player, "Barriers de '" + wall.getId() + "' quitados.");
            } else if (event.getAction() == org.bukkit.event.inventory.InventoryAction.PICKUP_ALL) {
                plugin.getBingoWallManager().placeWall(wall, org.bukkit.Material.BARRIER);
                ok(player, "Barriers de '" + wall.getId() + "' colocados.");
            } else if (event.getAction() == org.bukkit.event.inventory.InventoryAction.PICKUP_HALF) {
                player.closeInventory();
                plugin.getConfirmGUI().open(player, "Eliminar pared", List.of("¿Eliminar la pared '" + wall.getId() + "'?"),
                        () -> { plugin.getBingoMiniGame().getConfig().removeWall(wall.getId()); ok(player, "Pared eliminada."); render(player, 2); });
            }
            break;
        }
    }

    // ── Chat prompts ──────────────────────────────────────────────────────────

    private void promptInput(Player player, InputType type, String prompt) {
        awaitingInput.put(player.getUniqueId(), new PendingInput(type));
        player.closeInventory();
        player.sendMessage(Component.text("✎ " + prompt, NamedTextColor.YELLOW));
        player.sendMessage(Component.text("  'cancelar' para cancelar.", NamedTextColor.GRAY));
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        PendingInput pending = awaitingInput.get(player.getUniqueId());
        if (pending == null) return;
        event.setCancelled(true);
        String msg = event.getMessage().trim();
        if (msg.equalsIgnoreCase("cancelar")) {
            awaitingInput.remove(player.getUniqueId());
            player.sendMessage(Component.text("Cancelado.", NamedTextColor.GRAY));
            Bukkit.getScheduler().runTask(plugin, () -> render(player, activeTabs.getOrDefault(player.getUniqueId(), 0)));
            return;
        }
        if (pending.type() == null) {
            awaitingInput.remove(player.getUniqueId());
            String wallId = msg.toLowerCase().replaceAll("[^a-z0-9_]", "");
            if (wallId.isEmpty()) { err(player, "ID inválido."); Bukkit.getScheduler().runTask(plugin, () -> render(player, 2)); return; }
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getBingoWallManager().startSelection(player, wallId));
            return;
        }
        awaitingInput.remove(player.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> {
            BingoConfig cfg = plugin.getBingoMiniGame().getConfig();
            int val; try { val = Integer.parseInt(msg); } catch (NumberFormatException e) { err(player, "Número inválido."); render(player, activeTabs.getOrDefault(player.getUniqueId(), 0)); return; }
            switch (pending.type()) {
                case DURATION  -> { if (val < 1) err(player, "Mínimo 1."); else { cfg.setDurationMinutes(val);  ok(player, "Duración: " + val + " min."); } }
                case COUNTDOWN -> { if (val < 1) err(player, "Mínimo 1."); else { cfg.setCountdownSeconds(val); ok(player, "Countdown: " + val + "s."); } }
                case SCORE_1   -> { cfg.setScoreForPlace(1, val); ok(player, "1er lugar: " + val + " pts."); }
                case SCORE_2   -> { cfg.setScoreForPlace(2, val); ok(player, "2do lugar: " + val + " pts."); }
                case SCORE_3   -> { cfg.setScoreForPlace(3, val); ok(player, "3er lugar: " + val + " pts."); }
                case SCORE_DEF -> { cfg.setScoreForPlace(4, val); ok(player, "Defecto: " + val + " pts."); }
            }
            render(player, activeTabs.getOrDefault(player.getUniqueId(), 0));
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ItemStack buildTab(String name, Material icon, boolean active) {
        return ItemBuilder.of(icon).name(name, active ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY, active ? TextDecoration.BOLD : TextDecoration.ITALIC)
                .lore(active ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY, active ? "▲ Pestaña activa" : "Click para cambiar.").build();
    }

    private ItemStack buildNumericItem(String name, Material icon, String value, String desc, String hint) {
        return ItemBuilder.of(icon).name(name, NamedTextColor.AQUA, TextDecoration.BOLD).emptyLine()
                .lore(GuiUtil.label("Actual", Component.text(value, NamedTextColor.WHITE)))
                .emptyLine().lore(NamedTextColor.DARK_GRAY, desc).lore(NamedTextColor.YELLOW, hint).build();
    }

    private ItemStack buildScoreItem(int place, int score, String medal, NamedTextColor color) {
        String label = place == -1 ? "Resto (por defecto)" : medal + " Lugar #" + place;
        return ItemBuilder.of(Material.GOLD_NUGGET).name(label, color, TextDecoration.BOLD).emptyLine()
                .lore(GuiUtil.label("Puntos", Component.text(score + " pts", NamedTextColor.YELLOW)))
                .emptyLine().lore(NamedTextColor.YELLOW, "Click para cambiar.").build();
    }

    private String prettyType(BingoTask.Type type) {
        return switch (type) {
            case OBTAIN_ITEM -> "Obtener ítem"; case CRAFT_ITEM -> "Craftear ítem";
            case KILL_MOB -> "Matar mob"; case REACH_LOCATION -> "Llegar a lugar";
            case EQUIP_ITEM -> "Equipar ítem"; case FISH_ITEM -> "Pescar ítem";
            case VISIT_STRUCTURE -> "Visitar estructura"; case TRADE_ANY -> "Tradear con aldeano";
            case TRADE_ITEM -> "Tradear ítem";
        };
    }

    private String locStr(org.bukkit.Location l) { return String.format("%.0f, %.0f, %.0f", l.getX(), l.getY(), l.getZ()); }
    private void ok(Player p, String msg)  { p.sendMessage(Component.text("✔ " + msg, NamedTextColor.GREEN)); }
    private void err(Player p, String msg) { p.sendMessage(Component.text("✘ " + msg, NamedTextColor.RED)); }
}