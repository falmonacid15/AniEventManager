package org.falmdev.anieventmanager.minigames.tntrun;

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

public class TNTRunAdminGUI implements Listener {

    public static final String TITLE = "TNT Run — Configuración";

    private static final int TAB_SPAWN = 12;
    private static final int TAB_ARENA = 13;
    private static final int TAB_GAME  = 14;

    private static final int SPAWN_WORLD     = 20;
    private static final int SPAWN_LOBBY     = 23;
    private static final int SPAWN_SPECTATOR = 24;
    private static final int SPAWN_CENTER    = 21;
    private static final int SPAWN_ADD       = 30;
    private static final int SPAWN_LIST      = 31;
    private static final int SPAWN_CLEAR     = 32;

    private static final int ARENA_SIZE     = 21;
    private static final int ARENA_SHAPE    = 22;
    private static final int ARENA_LAYERS   = 29;
    private static final int ARENA_GAP      = 30;
    private static final int ARENA_DOME     = 23;
    private static final int ARENA_GENERATE = 32;
    private static final int ARENA_CLEAR    = 33;

    private static final int GAME_DELAY        = 21;
    private static final int GAME_COUNTDOWN    = 22;
    private static final int GAME_ENDDELAY     = 23;
    private static final int GAME_JUMP         = 30;
    private static final int GAME_JUMPCOOLDOWN = 32;
    private static final int GAME_SCORE_1      = 39;
    private static final int GAME_SCORE_2      = 40;
    private static final int GAME_SCORE_3      = 41;
    private static final int GAME_SCORE_DEF    = 42;

    private static final int NAV_MAGIC_STICK = 4;
    private static final int NAV_START       = 16;
    private static final int NAV_STOP        = 10;

    private final Anieventmanager plugin;
    private final Map<UUID, Integer>      activeTabs    = new HashMap<>();
    private final Map<UUID, PendingInput> awaitingInput = new HashMap<>();

    private enum InputType {
        ARENA_SIZE, ARENA_LAYERS, ARENA_GAP, ARENA_DOME,
        GAME_DELAY, GAME_COUNTDOWN, GAME_ENDDELAY, GAME_JUMPCOOLDOWN,
        GAME_SCORE_1, GAME_SCORE_2, GAME_SCORE_3, GAME_SCORE_DEF
    }
    private record PendingInput(InputType type, String prompt) {}

    public TNTRunAdminGUI(Anieventmanager plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        render(player, activeTabs.getOrDefault(player.getUniqueId(), 0));
    }

    private void render(Player player, int tab) {
        activeTabs.put(player.getUniqueId(), tab);

        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text(TITLE, NamedTextColor.GOLD));
        GuiUtil.fillSlots(inv, GuiUtil.emptyPane(), 0,1,9,7,8,17,36,45,46,52,53,44);


        inv.setItem(TAB_SPAWN, buildTab("Spawn / Mundo", Material.COMPASS,    tab == 0));
        inv.setItem(TAB_ARENA, buildTab("Arena",         Material.GRASS_BLOCK, tab == 1));
        inv.setItem(TAB_GAME,  buildTab("Juego",         Material.CLOCK,       tab == 2));

        GuiUtil.fillNavigationHomeOnly(inv);

        inv.setItem(NAV_MAGIC_STICK, buildMagicStickButton(player));

        boolean running = plugin.getTNTRunMiniGame().isRunning();
        if (!running) {
            boolean configured = plugin.getTNTRunMiniGame().validateConfig() == null;
            inv.setItem(NAV_START, configured
                    ? ItemBuilder.of(Material.LIME_CONCRETE)
                      .name("▶ Iniciar partida", NamedTextColor.GREEN, TextDecoration.BOLD)
                      .lore(NamedTextColor.GRAY, "Inicia el TNT Run.").build()
                    : ItemBuilder.of(Material.GRAY_CONCRETE)
                      .name("▶ Iniciar partida", NamedTextColor.DARK_GRAY, TextDecoration.BOLD)
                      .lore(NamedTextColor.RED, "Configuración incompleta.")
                      .lore(NamedTextColor.DARK_GRAY,
                              plugin.getTNTRunMiniGame().validateConfig() != null
                              ? plugin.getTNTRunMiniGame().validateConfig() : "").build());
        } else {
            inv.setItem(NAV_STOP, ItemBuilder.of(Material.RED_CONCRETE)
                    .name("■ Detener partida", NamedTextColor.RED, TextDecoration.BOLD)
                    .lore(NamedTextColor.GRAY, "Detiene el TNT Run en curso.").build());
        }

        switch (tab) {
            case 0 -> fillSpawnTab(inv);
            case 1 -> fillArenaTab(inv);
            case 2 -> fillGameTab(inv);
        }

        player.openInventory(inv);
    }

    private void fillSpawnTab(Inventory inv) {
        TNTRunConfig cfg = plugin.getTNTRunMiniGame().getConfig();
        inv.setItem(SPAWN_WORLD, buildLocItem("Mundo", Material.GRASS_BLOCK,
                cfg.getWorldName().isEmpty() ? null : cfg.getWorldName(),
                "Mundo donde se juega el TNT Run.", "Usa el Magic Stick → Set World."));
        inv.setItem(SPAWN_LOBBY, buildLocItem("Lobby Spawn", Material.OAK_DOOR,
                cfg.getLobbySpawn() != null ? locStr(cfg.getLobbySpawn()) : null,
                "Spawn previo al inicio.", "Magic Stick → Set Lobby."));
        inv.setItem(SPAWN_SPECTATOR, buildLocItem("Spectator Spawn", Material.ENDER_EYE,
                cfg.getSpectatorSpawn() != null ? locStr(cfg.getSpectatorSpawn()) : null,
                "Donde se teletransportan los eliminados.", "Magic Stick → Set Spectator."));
        inv.setItem(SPAWN_CENTER, buildLocItem("Centro de Arena", Material.NETHER_STAR,
                cfg.getArenaCenter() != null ? locStr(cfg.getArenaCenter()) : null,
                "Centro geométrico de la arena.", "Magic Stick → Set Center."));
        int spawnCount = cfg.getPlayerSpawns().size();
        inv.setItem(SPAWN_ADD, ItemBuilder.of(Material.LIME_DYE)
                .name("+ Agregar spawn de jugador", NamedTextColor.GREEN, TextDecoration.BOLD)
                .emptyLine()
                .lore(GuiUtil.label("Spawns actuales", Component.text(spawnCount, NamedTextColor.WHITE)))
                .emptyLine()
                .lore(NamedTextColor.YELLOW, "Click para agregar tu posición actual.").build());
        inv.setItem(SPAWN_LIST, ItemBuilder.of(Material.BOOK)
                .name("Ver spawns de jugadores", NamedTextColor.AQUA, TextDecoration.BOLD)
                .emptyLine()
                .lore(GuiUtil.label("Total", Component.text(spawnCount, NamedTextColor.WHITE)))
                .emptyLine()
                .lore(buildSpawnListLore(cfg)).build());
        inv.setItem(SPAWN_CLEAR, ItemBuilder.of(Material.BARRIER)
                .name("✘ Limpiar spawns", NamedTextColor.RED, TextDecoration.BOLD)
                .emptyLine()
                .lore(NamedTextColor.DARK_RED, "Elimina todos los spawns de jugadores.")
                .lore(NamedTextColor.YELLOW, "Click para confirmar.").build());
    }

    private Component[] buildSpawnListLore(TNTRunConfig cfg) {
        var spawns = cfg.getPlayerSpawns();
        if (spawns.isEmpty()) return new Component[]{
                Component.text("  (ninguno)", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)};
        Component[] lines = new Component[Math.min(spawns.size(), 8)];
        for (int i = 0; i < lines.length; i++)
            lines[i] = Component.text("  #" + (i+1) + ": " + locStr(spawns.get(i)),
                    NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false);
        return lines;
    }


    private void fillArenaTab(Inventory inv) {
        TNTRunConfig cfg = plugin.getTNTRunMiniGame().getConfig();
        inv.setItem(ARENA_SIZE, buildNumericItem("Tamaño de Arena", Material.FILLED_MAP,
                cfg.getArenaSize() + "×" + cfg.getArenaSize() + " bloques",
                "Lado del cuadrado / diámetro del círculo.", "Click para cambiar. (Mínimo: 10)"));
        boolean isCircle = cfg.getArenaShape() == TNTRunArena.Shape.CIRCLE;
        inv.setItem(ARENA_SHAPE, ItemBuilder.of(isCircle ? Material.CLOCK : Material.FILLED_MAP)
                .name("Forma de Arena", NamedTextColor.AQUA, TextDecoration.BOLD).emptyLine()
                .lore(GuiUtil.noItalic(Component.text("Actual: ", NamedTextColor.GRAY)
                        .append(Component.text(isCircle ? "⬤ Círculo" : "■ Cuadrado", NamedTextColor.WHITE))))
                .emptyLine().lore(NamedTextColor.YELLOW, "Click para alternar.").build());
        inv.setItem(ARENA_LAYERS, buildNumericItem("Capas de TNT+SAND", Material.SANDSTONE,
                cfg.getLayerCount() + " capas", "Número de pisos.", "Click para cambiar. (Mínimo: 1)"));
        inv.setItem(ARENA_GAP, buildNumericItem("Espacio entre Capas", Material.WHITE_STAINED_GLASS,
                cfg.getLayerGap() + " bloques de aire", "Distancia vertical entre capas.", "Click para cambiar. (Mínimo: 1)"));
        inv.setItem(ARENA_DOME, buildNumericItem("Altura de Cúpula", Material.GLASS,
                cfg.getDomeHeight() + " bloques", "Altura del techo de cristal.", "Click para cambiar. (Mínimo: 5)"));
        boolean hasCenter = cfg.getArenaCenter() != null;
        boolean isRunning = plugin.getTNTRunMiniGame().isRunning();
        inv.setItem(ARENA_GENERATE, hasCenter && !isRunning
                ? ItemBuilder.of(Material.LIME_CONCRETE).name("⚙ Generar Arena", NamedTextColor.GREEN, TextDecoration.BOLD)
                  .emptyLine()
                  .lore(GuiUtil.label("Centro", Component.text(locStr(cfg.getArenaCenter()), NamedTextColor.WHITE)))
                  .emptyLine().lore(NamedTextColor.YELLOW, "Click para generar.").build()
                : ItemBuilder.of(Material.GRAY_CONCRETE).name("⚙ Generar Arena", NamedTextColor.DARK_GRAY, TextDecoration.BOLD)
                  .emptyLine().lore(NamedTextColor.RED, isRunning ? "No disponible durante la partida." : "Primero configura el centro.").build());
        inv.setItem(ARENA_CLEAR, hasCenter && !isRunning
                ? ItemBuilder.of(Material.RED_CONCRETE).name("✘ Borrar Arena", NamedTextColor.RED, TextDecoration.BOLD)
                  .emptyLine().lore(NamedTextColor.DARK_RED, "Elimina todos los bloques.")
                  .lore(NamedTextColor.YELLOW, "Click para confirmar.").build()
                : ItemBuilder.of(Material.GRAY_CONCRETE).name("✘ Borrar Arena", NamedTextColor.DARK_GRAY, TextDecoration.BOLD)
                  .emptyLine().lore(NamedTextColor.RED, isRunning ? "No disponible durante la partida." : "Primero configura el centro.").build());
    }

    private void fillGameTab(Inventory inv) {
        TNTRunConfig cfg = plugin.getTNTRunMiniGame().getConfig();
        inv.setItem(GAME_DELAY, buildNumericItem("Delay de Bloque", Material.CLOCK,
                cfg.getBlockRemoveDelay() + " ticks (" + String.format("%.1f", cfg.getBlockRemoveDelay() / 20.0) + "s)",
                "Tiempo antes de que caiga el bloque al pisarlo.", "Click para cambiar."));
        inv.setItem(GAME_COUNTDOWN, buildNumericItem("Countdown", Material.CLOCK,
                cfg.getCountdownSeconds() + " segundos", "Cuenta regresiva antes de empezar.", "Click para cambiar."));
        inv.setItem(GAME_ENDDELAY, buildNumericItem("Delay de Fin", Material.CLOCK,
                cfg.getEndDelaySeconds() + " segundos", "Tiempo en arena al terminar.", "Click para cambiar. (Mínimo: 5)"));
        boolean jumpEnabled = cfg.isDoubleJumpEnabled();
        inv.setItem(GAME_JUMP, ItemBuilder.of(jumpEnabled ? Material.FEATHER : Material.DEAD_BUSH)
                .name("Doble Salto", NamedTextColor.AQUA, TextDecoration.BOLD).emptyLine()
                .lore(GuiUtil.noItalic(Component.text("Estado: ", NamedTextColor.GRAY)
                        .append(Component.text(jumpEnabled ? "ACTIVADO" : "DESACTIVADO",
                                jumpEnabled ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD))))
                .emptyLine().lore(NamedTextColor.YELLOW, "Click para alternar.").build());
        inv.setItem(GAME_JUMPCOOLDOWN, buildNumericItem("Cooldown de Doble Salto", Material.CLOCK,
                cfg.getDoubleJumpCooldown() == 0 ? "sin cooldown" : cfg.getDoubleJumpCooldown() + " segundos",
                "Tiempo entre dobles saltos.", "Click para cambiar. (0 = sin cooldown)"));
        inv.setItem(38, ItemBuilder.of(Material.GOLD_INGOT)
                .name("Puntajes por Posición", NamedTextColor.GOLD, TextDecoration.BOLD).build());
        inv.setItem(GAME_SCORE_1,   buildScoreItem(1,  cfg.getScoreForPlace(1),  "🥇", NamedTextColor.GOLD));
        inv.setItem(GAME_SCORE_2,   buildScoreItem(2,  cfg.getScoreForPlace(2),  "🥈", NamedTextColor.GRAY));
        inv.setItem(GAME_SCORE_3,   buildScoreItem(3,  cfg.getScoreForPlace(3),  "🥉", NamedTextColor.RED));
        inv.setItem(GAME_SCORE_DEF, buildScoreItem(-1, cfg.getScoreForPlace(4),  "  ", NamedTextColor.DARK_GRAY));
    }


    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!GuiUtil.getTitle(event.getView()).equals(TITLE)) return;
        event.setCancelled(true);
        if (!player.isOp()) return;

        int slot = event.getRawSlot();
        int tab  = activeTabs.getOrDefault(player.getUniqueId(), 0);
        TNTRunConfig cfg = plugin.getTNTRunMiniGame().getConfig();

        if (slot == TAB_SPAWN) { render(player, 0); return; }
        if (slot == TAB_ARENA) { render(player, 1); return; }
        if (slot == TAB_GAME)  { render(player, 2); return; }

        if (GuiUtil.handleNavigation(slot, player, plugin, null)) return;

        if (slot == NAV_MAGIC_STICK) {
            player.closeInventory();
            plugin.getTNTRunMagicStick().giveMagicStick(player);
            player.sendMessage(Component.text("✦ Magic Stick entregado.", NamedTextColor.GOLD));
            return;
        }

        if (slot == NAV_START) {
            if (plugin.getTNTRunMiniGame().validateConfig() != null || plugin.getTNTRunMiniGame().isRunning()) return;
            if (plugin.getTNTRunMiniGame().start())
                player.sendMessage(Component.text("▶ TNT Run iniciado.", NamedTextColor.GREEN));
            else
                player.sendMessage(Component.text("✘ No se pudo iniciar.", NamedTextColor.RED));
            render(player, tab);
            return;
        }
        if (slot == NAV_STOP) {
            if (!plugin.getTNTRunMiniGame().isRunning()) return;
            plugin.getTNTRunMiniGame().forceStop();
            player.sendMessage(Component.text("■ TNT Run detenido.", NamedTextColor.YELLOW));
            render(player, tab);
            return;
        }

        switch (tab) {
            case 0 -> handleSpawnClick(player, slot, cfg);
            case 1 -> handleArenaClick(player, slot, cfg);
            case 2 -> handleGameClick(player, slot, cfg);
        }
    }

    private void handleSpawnClick(Player player, int slot, TNTRunConfig cfg) {
        switch (slot) {
            case SPAWN_WORLD     -> { cfg.setWorldName(player.getWorld().getName()); ok(player, "Mundo seteado."); render(player, 0); }
            case SPAWN_LOBBY     -> { cfg.setLobbySpawn(player.getLocation()); ok(player, "Lobby spawn seteado."); render(player, 0); }
            case SPAWN_SPECTATOR -> { cfg.setSpectatorSpawn(player.getLocation()); ok(player, "Spectator spawn seteado."); render(player, 0); }
            case SPAWN_CENTER    -> { cfg.setArenaCenter(player.getLocation()); ok(player, "Centro seteado."); render(player, 0); }
            case SPAWN_ADD       -> { cfg.addPlayerSpawn(player.getLocation()); ok(player, "Spawn #" + cfg.getPlayerSpawns().size() + " agregado."); render(player, 0); }
            case SPAWN_CLEAR     -> {
                player.closeInventory();
                plugin.getConfirmGUI().open(player, "Limpiar spawns",
                        List.of("¿Eliminar todos los spawns?", "Total: " + cfg.getPlayerSpawns().size()),
                        () -> { cfg.clearPlayerSpawns(); ok(player, "Spawns eliminados."); render(player, 0); });
            }
        }
    }

    private void handleArenaClick(Player player, int slot, TNTRunConfig cfg) {
        switch (slot) {
            case ARENA_SIZE   -> promptInput(player, InputType.ARENA_SIZE,   "Escribe el tamaño de arena (mínimo 10):");
            case ARENA_SHAPE  -> { cfg.setArenaShape(cfg.getArenaShape() == TNTRunArena.Shape.SQUARE ? TNTRunArena.Shape.CIRCLE : TNTRunArena.Shape.SQUARE); ok(player, "Forma cambiada."); render(player, 1); }
            case ARENA_LAYERS -> promptInput(player, InputType.ARENA_LAYERS, "Escribe el número de capas (mínimo 1):");
            case ARENA_GAP    -> promptInput(player, InputType.ARENA_GAP,    "Escribe el espacio entre capas (mínimo 1):");
            case ARENA_DOME   -> promptInput(player, InputType.ARENA_DOME,   "Escribe la altura de la cúpula (mínimo 5):");
            case ARENA_GENERATE -> {
                if (cfg.getArenaCenter() == null) { err(player, "Primero configura el centro."); return; }
                if (plugin.getTNTRunMiniGame().isRunning()) { err(player, "No disponible durante la partida."); return; }
                player.sendMessage(Component.text("Generando arena...", NamedTextColor.YELLOW));
                new TNTRunArena(cfg.getArenaCenter(), cfg.buildArenaConfig()).generate();
                ok(player, "Arena generada."); render(player, 1);
            }
            case ARENA_CLEAR -> {
                if (cfg.getArenaCenter() == null) { err(player, "Sin centro configurado."); return; }
                if (plugin.getTNTRunMiniGame().isRunning()) { err(player, "No disponible durante la partida."); return; }
                player.closeInventory();
                plugin.getConfirmGUI().open(player, "Borrar arena", List.of("¿Eliminar todos los bloques?"),
                        () -> { new TNTRunArena(cfg.getArenaCenter(), cfg.buildArenaConfig()).clear(); ok(player, "Arena eliminada."); render(player, 1); });
            }
        }
    }

    private void handleGameClick(Player player, int slot, TNTRunConfig cfg) {
        switch (slot) {
            case GAME_DELAY        -> promptInput(player, InputType.GAME_DELAY,        "Delay en ticks (20 = 1s):");
            case GAME_COUNTDOWN    -> promptInput(player, InputType.GAME_COUNTDOWN,    "Countdown en segundos:");
            case GAME_ENDDELAY     -> promptInput(player, InputType.GAME_ENDDELAY,     "Delay de fin en segundos (mínimo 5):");
            case GAME_JUMP         -> { cfg.setDoubleJumpEnabled(!cfg.isDoubleJumpEnabled()); ok(player, "Doble salto " + (cfg.isDoubleJumpEnabled() ? "activado" : "desactivado") + "."); render(player, 2); }
            case GAME_JUMPCOOLDOWN -> promptInput(player, InputType.GAME_JUMPCOOLDOWN, "Cooldown en segundos (0 = sin cooldown):");
            case GAME_SCORE_1      -> promptInput(player, InputType.GAME_SCORE_1,      "Puntos para el 1er lugar:");
            case GAME_SCORE_2      -> promptInput(player, InputType.GAME_SCORE_2,      "Puntos para el 2do lugar:");
            case GAME_SCORE_3      -> promptInput(player, InputType.GAME_SCORE_3,      "Puntos para el 3er lugar:");
            case GAME_SCORE_DEF    -> promptInput(player, InputType.GAME_SCORE_DEF,    "Puntos por defecto:");
        }
    }

    private void promptInput(Player player, InputType type, String prompt) {
        awaitingInput.put(player.getUniqueId(), new PendingInput(type, prompt));
        player.closeInventory();
        player.sendMessage(Component.text("✎ " + prompt, NamedTextColor.YELLOW));
        player.sendMessage(Component.text("  'cancelar' para cancelar.", NamedTextColor.GRAY));
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        PendingInput pending = awaitingInput.remove(player.getUniqueId());
        if (pending == null) return;
        event.setCancelled(true);
        String msg = event.getMessage().trim();
        if (msg.equalsIgnoreCase("cancelar")) {
            player.sendMessage(Component.text("Cancelado.", NamedTextColor.GRAY));
            Bukkit.getScheduler().runTask(plugin, () -> render(player, activeTabs.getOrDefault(player.getUniqueId(), 0)));
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            TNTRunConfig cfg = plugin.getTNTRunMiniGame().getConfig();
            int val; try { val = Integer.parseInt(msg); } catch (NumberFormatException e) { err(player, "Número inválido."); render(player, activeTabs.getOrDefault(player.getUniqueId(), 0)); return; }
            switch (pending.type()) {
                case ARENA_SIZE    -> { if (val < 10) err(player, "Mínimo 10."); else { cfg.setArenaSize(val);            ok(player, "Tamaño: " + val + "×" + val + "."); } }
                case ARENA_LAYERS  -> { if (val < 1)  err(player, "Mínimo 1.");  else { cfg.setLayerCount(val);           ok(player, "Capas: " + val + "."); } }
                case ARENA_GAP     -> { if (val < 1)  err(player, "Mínimo 1.");  else { cfg.setLayerGap(val);             ok(player, "Espacio: " + val + " bloques."); } }
                case ARENA_DOME    -> { if (val < 5)  err(player, "Mínimo 5.");  else { cfg.setDomeHeight(val);           ok(player, "Cúpula: " + val + " bloques."); } }
                case GAME_DELAY    -> { if (val < 1)  err(player, "Mínimo 1.");  else { cfg.setBlockRemoveDelay(val);     ok(player, "Delay: " + val + " ticks."); } }
                case GAME_COUNTDOWN-> { if (val < 1)  err(player, "Mínimo 1.");  else { cfg.setCountdownSeconds(val);     ok(player, "Countdown: " + val + "s."); } }
                case GAME_ENDDELAY -> { if (val < 5)  err(player, "Mínimo 5.");  else { cfg.setEndDelaySeconds(val);      ok(player, "Delay fin: " + val + "s."); } }
                case GAME_JUMPCOOLDOWN -> { if (val < 0) err(player, "No negativo."); else { cfg.setDoubleJumpCooldown(val); ok(player, "Cooldown: " + (val == 0 ? "desactivado" : val + "s") + "."); } }
                case GAME_SCORE_1   -> { cfg.setScoreForPlace(1, val); ok(player, "1er lugar: " + val + " pts."); }
                case GAME_SCORE_2   -> { cfg.setScoreForPlace(2, val); ok(player, "2do lugar: " + val + " pts."); }
                case GAME_SCORE_3   -> { cfg.setScoreForPlace(3, val); ok(player, "3er lugar: " + val + " pts."); }
                case GAME_SCORE_DEF -> { cfg.setScoreForPlace(4, val); ok(player, "Defecto: " + val + " pts."); }
            }
            render(player, activeTabs.getOrDefault(player.getUniqueId(), 0));
        });
    }


    private ItemStack buildTab(String name, Material icon, boolean active) {
        return ItemBuilder.of(icon).name(name, active ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY, active ? TextDecoration.BOLD : TextDecoration.ITALIC)
                .lore(active ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY, active ? "▲ Pestaña activa" : "Click para cambiar.").build();
    }

    private ItemStack buildLocItem(String name, Material icon, String currentValue, String desc, String hint) {
        boolean configured = currentValue != null;
        return ItemBuilder.of(icon).name(name, configured ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD).emptyLine()
                .lore(GuiUtil.noItalic(Component.text("Estado: ", NamedTextColor.GRAY).append(configured ? Component.text("✔ " + currentValue, NamedTextColor.GREEN) : Component.text("✘ No configurado", NamedTextColor.RED))))
                .emptyLine().lore(NamedTextColor.DARK_GRAY, desc).lore(NamedTextColor.YELLOW, hint).build();
    }

    private ItemStack buildNumericItem(String name, Material icon, String currentValue, String desc, String hint) {
        return ItemBuilder.of(icon).name(name, NamedTextColor.AQUA, TextDecoration.BOLD).emptyLine()
                .lore(GuiUtil.label("Actual", Component.text(currentValue, NamedTextColor.WHITE)))
                .emptyLine().lore(NamedTextColor.DARK_GRAY, desc).lore(NamedTextColor.YELLOW, hint).build();
    }

    private ItemStack buildScoreItem(int place, int score, String medal, NamedTextColor color) {
        String label = place == -1 ? "Resto (por defecto)" : medal + " Lugar #" + place;
        return ItemBuilder.of(Material.GOLD_NUGGET).name(label, color, TextDecoration.BOLD).emptyLine()
                .lore(GuiUtil.label("Puntos", Component.text(score + " pts", NamedTextColor.YELLOW)))
                .emptyLine().lore(NamedTextColor.YELLOW, "Click para cambiar.").build();
    }

    private ItemStack buildMagicStickButton(Player player) {
        TNTRunMagicStick.Mode mode = plugin.getTNTRunMagicStick().getMode(player);
        String modeLabel = mode == null ? "Inactivo" : mode.getDisplayName();
        NamedTextColor modeColor = mode == null ? NamedTextColor.DARK_GRAY : NamedTextColor.YELLOW;
        return ItemBuilder.of(Material.BLAZE_ROD).name("✦ Magic Stick", NamedTextColor.GOLD, TextDecoration.BOLD).emptyLine()
                .lore(GuiUtil.noItalic(Component.text("Modo: ", NamedTextColor.GRAY).append(Component.text(modeLabel, modeColor))))
                .emptyLine().lore(NamedTextColor.YELLOW, "Click para obtener el Magic Stick.").build();
    }

    private String locStr(org.bukkit.Location l) { return String.format("%.0f, %.0f, %.0f", l.getX(), l.getY(), l.getZ()); }
    private void ok(Player p, String msg)  { p.sendMessage(Component.text("✔ " + msg, NamedTextColor.GREEN)); }
    private void err(Player p, String msg) { p.sendMessage(Component.text("✘ " + msg, NamedTextColor.RED)); }
}