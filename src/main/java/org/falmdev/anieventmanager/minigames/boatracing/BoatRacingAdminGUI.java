package org.falmdev.anieventmanager.minigames.boatracing;

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

/**
 * GUI de administración del Boat Racing.
 *
 * Pestañas:
 *   0 — Config   (paddock, vueltas, bote, tiempo qualy, puntajes)
 *   1 — Pista    (región, meta, spawns, checkpoints, luces)
 *   2 — Control  (estado actual, start qualy, start race, stop)
 */
public class BoatRacingAdminGUI implements Listener {

    public static final String TITLE = "Boat Racing — Configuración";

    // ── Pestañas ──────────────────────────────────────────────────────────────
    private static final int TAB_CONFIG  = 1;
    private static final int TAB_TRACK   = 2;
    private static final int TAB_CONTROL = 3;

    // ── Pestaña CONFIG ────────────────────────────────────────────────────────
    private static final int CFG_PADDOCK    = 10;
    private static final int CFG_LAPS       = 12;
    private static final int CFG_BOAT       = 14;
    private static final int CFG_QUALYTIME  = 16;
    private static final int CFG_SCORE_1    = 28;
    private static final int CFG_SCORE_2    = 30;
    private static final int CFG_SCORE_3    = 32;
    private static final int CFG_SCORE_4    = 34;

    // ── Pestaña TRACK ─────────────────────────────────────────────────────────
    private static final int TRACK_REGION_A   = 10;
    private static final int TRACK_REGION_B   = 12;
    private static final int TRACK_FINISH_A   = 14;
    private static final int TRACK_FINISH_B   = 16;
    private static final int TRACK_ADD_SPAWN  = 19;
    private static final int TRACK_CLEAR_SPAWN= 21;
    private static final int TRACK_SPAWN_INFO = 22;
    private static final int TRACK_ADD_CP     = 28;
    private static final int TRACK_CLEAR_CP   = 30;
    private static final int TRACK_CP_INFO    = 31;
    private static final int TRACK_ADD_LIGHT  = 34;
    private static final int TRACK_CLEAR_LIGHT= 36;
    private static final int TRACK_LIGHT_INFO = 37;

    // ── Pestaña CONTROL ───────────────────────────────────────────────────────
    private static final int CTRL_STATUS     = 13;
    private static final int CTRL_PADDOCK    = 19;
    private static final int CTRL_QUALY      = 22;
    private static final int CTRL_RACE       = 25;
    private static final int CTRL_STOP_GAME  = 31;

    // ── Controles permanentes ─────────────────────────────────────────────────
    private static final int CTRL_BACK        = 45;
    private static final int CTRL_MAGIC_STICK = 49;
    private static final int CTRL_START       = 52; // shortcut start qualy
    private static final int CTRL_STOP        = 53;

    // ── Estado interno ────────────────────────────────────────────────────────
    private final Anieventmanager plugin;
    private final Map<UUID, Integer>      activeTabs    = new HashMap<>();
    private final Map<UUID, PendingInput> awaitingInput = new HashMap<>();

    private enum InputType {
        LAPS, QUALY_TIME, SCORE_1, SCORE_2, SCORE_3, SCORE_4, CP_RADIUS
    }
    private record PendingInput(InputType type) {}

    public BoatRacingAdminGUI(Anieventmanager plugin) {
        this.plugin = plugin;
    }

    // ── Abrir ─────────────────────────────────────────────────────────────────

    public void open(Player player) {
        render(player, activeTabs.getOrDefault(player.getUniqueId(), 0));
    }

    private void render(Player player, int tab) {
        activeTabs.put(player.getUniqueId(), tab);

        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text(TITLE, NamedTextColor.GOLD));
        GuiUtil.fillAll(inv);

        // Pestañas
        inv.setItem(TAB_CONFIG,  buildTab("Config",  Material.COMPARATOR,   tab == 0));
        inv.setItem(TAB_TRACK,   buildTab("Pista",   Material.OAK_BOAT,     tab == 1));
        inv.setItem(TAB_CONTROL, buildTab("Control", Material.REDSTONE_LAMP, tab == 2));

        // Controles permanentes
        inv.setItem(CTRL_BACK, ItemBuilder.of(Material.ARROW)
                .name("← Volver al menú", NamedTextColor.GRAY).build());
        inv.setItem(CTRL_MAGIC_STICK, ItemBuilder.of(Material.BLAZE_ROD)
                .name("✦ Magic Stick", NamedTextColor.GOLD, TextDecoration.BOLD)
                .emptyLine()
                .lore(NamedTextColor.YELLOW, "Click para obtener el Magic Stick.")
                .lore(NamedTextColor.DARK_GRAY, "Marca todos los puntos de la pista.")
                .build());

        BoatRacingMiniGame mg = plugin.getBoatRacingMiniGame();
        boolean running = mg.isRunning();
        boolean configured = mg.validateConfig() == null;

        if (!running) {
            inv.setItem(CTRL_START, configured
                    ? ItemBuilder.of(Material.LIME_CONCRETE)
                      .name("▶ Iniciar Qualy", NamedTextColor.GREEN, TextDecoration.BOLD)
                      .lore(NamedTextColor.GRAY, "Shortcut: manda al paddock y arranca.")
                      .build()
                    : ItemBuilder.of(Material.GRAY_CONCRETE)
                      .name("▶ Iniciar", NamedTextColor.DARK_GRAY, TextDecoration.BOLD)
                      .lore(NamedTextColor.RED, mg.validateConfig() != null ? mg.validateConfig() : "")
                      .build());
        } else {
            inv.setItem(CTRL_STOP, ItemBuilder.of(Material.RED_CONCRETE)
                    .name("■ Detener", NamedTextColor.RED, TextDecoration.BOLD).build());
        }

        switch (tab) {
            case 0 -> fillConfigTab(inv);
            case 1 -> fillTrackTab(inv);
            case 2 -> fillControlTab(inv);
        }

        player.openInventory(inv);
    }

    // ── Pestaña 0: Config ─────────────────────────────────────────────────────

    private void fillConfigTab(Inventory inv) {
        BoatRacingConfig cfg = plugin.getBoatRacingMiniGame().getConfig();

        boolean hasPaddock = cfg.getPaddockSpawn() != null;
        inv.setItem(CFG_PADDOCK, ItemBuilder.of(Material.OAK_DOOR)
                .name("Paddock Spawn", hasPaddock ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD)
                .emptyLine()
                .lore(GuiUtil.noItalic(Component.text("Estado: ", NamedTextColor.GRAY)
                        .append(hasPaddock
                                ? Component.text("✔ " + locStr(cfg.getPaddockSpawn()), NamedTextColor.GREEN)
                                : Component.text("✘ No configurado", NamedTextColor.RED))))
                .emptyLine()
                .lore(NamedTextColor.YELLOW, "Click para setear en tu posición.")
                .build());

        inv.setItem(CFG_LAPS, buildNumericItem("Total de Vueltas", Material.MAP,
                String.valueOf(cfg.getTotalLaps()),
                "Número de vueltas de la carrera.", "Click para cambiar. (Mínimo: 1)"));

        inv.setItem(CFG_BOAT, buildCycleItem("Bote por Defecto", Material.OAK_BOAT,
                cfg.getDefaultBoat().name(),
                "Click para cambiar el tipo de bote."));

        inv.setItem(CFG_QUALYTIME, buildNumericItem("Duración de Qualy", Material.CLOCK,
                cfg.getQualyDuration() + " segundos",
                "Tiempo máximo de vuelta de clasificación.", "Click para cambiar."));

        // Sección puntajes
        inv.setItem(27, ItemBuilder.of(Material.GOLD_INGOT)
                .name("Puntajes por Posición", NamedTextColor.GOLD, TextDecoration.BOLD).build());

        for (int i = 1; i <= 4; i++) {
            inv.setItem(new int[]{CFG_SCORE_1, CFG_SCORE_2, CFG_SCORE_3, CFG_SCORE_4}[i-1],
                    buildScoreItem(i, cfg.getScoreForPosition(i)));
        }
    }

    // ── Pestaña 1: Pista ──────────────────────────────────────────────────────

    private void fillTrackTab(Inventory inv) {
        BoatRacingConfig cfg = plugin.getBoatRacingMiniGame().getConfig();

        // Región de la pista
        inv.setItem(TRACK_REGION_A, buildLocItem("Región Pista — Punto A", Material.GREEN_CONCRETE,
                cfg.getTrackPointA(), "Esquina A de la región de la pista.",
                "Magic Stick → Track A / Click para setear."));
        inv.setItem(TRACK_REGION_B, buildLocItem("Región Pista — Punto B", Material.RED_CONCRETE,
                cfg.getTrackPointB(), "Esquina B de la región de la pista.",
                "Magic Stick → Track B / Click para setear."));
        inv.setItem(TRACK_FINISH_A, buildLocItem("Línea de Meta — Extremo A", Material.WHITE_CONCRETE,
                cfg.getFinishA(), "Extremo A de la línea de meta.",
                "Magic Stick → Finish A / Click para setear."));
        inv.setItem(TRACK_FINISH_B, buildLocItem("Línea de Meta — Extremo B", Material.YELLOW_CONCRETE,
                cfg.getFinishB(), "Extremo B de la línea de meta.",
                "Magic Stick → Finish B / Click para setear."));

        // Spawns de parrilla
        int spawnCount = cfg.getPlayerSpawns().size();
        inv.setItem(TRACK_ADD_SPAWN, ItemBuilder.of(Material.LIME_DYE)
                .name("+ Agregar Spawn de Parrilla", NamedTextColor.GREEN, TextDecoration.BOLD)
                .emptyLine()
                .lore(GuiUtil.label("Spawns actuales", Component.text(spawnCount, NamedTextColor.WHITE)))
                .emptyLine()
                .lore(NamedTextColor.YELLOW, "Click para agregar tu posición actual.")
                .lore(NamedTextColor.DARK_GRAY, "El orden de adición = orden de parrilla.")
                .build());
        inv.setItem(TRACK_CLEAR_SPAWN, ItemBuilder.of(Material.BARRIER)
                .name("✘ Limpiar Spawns de Parrilla", NamedTextColor.RED, TextDecoration.BOLD)
                .lore(NamedTextColor.DARK_RED, "Elimina todos los spawns (" + spawnCount + ").")
                .build());
        inv.setItem(TRACK_SPAWN_INFO, buildListInfo("Spawns de Parrilla",
                spawnCount, "posiciones configuradas"));

        // Checkpoints
        int cpCount = cfg.getCheckpoints().size();
        inv.setItem(TRACK_ADD_CP, ItemBuilder.of(Material.LIME_DYE)
                .name("+ Agregar Checkpoint", NamedTextColor.GREEN, TextDecoration.BOLD)
                .emptyLine()
                .lore(GuiUtil.label("Checkpoints", Component.text(cpCount, NamedTextColor.WHITE)))
                .emptyLine()
                .lore(NamedTextColor.YELLOW, "Click para agregar en tu posición.")
                .lore(NamedTextColor.DARK_GRAY, "Radio por defecto: 4 bloques.")
                .lore(NamedTextColor.DARK_GRAY, "Para radio custom: usa el Magic Stick.")
                .build());
        inv.setItem(TRACK_CLEAR_CP, ItemBuilder.of(Material.BARRIER)
                .name("✘ Limpiar Checkpoints", NamedTextColor.RED, TextDecoration.BOLD)
                .lore(NamedTextColor.DARK_RED, "Elimina todos los checkpoints (" + cpCount + ").")
                .build());
        inv.setItem(TRACK_CP_INFO, buildListInfo("Checkpoints", cpCount, "configurados"));

        // Luces
        int lightCount = cfg.getLights().size();
        boolean lightsComplete = lightCount >= 5;
        inv.setItem(TRACK_ADD_LIGHT, ItemBuilder.of(Material.REDSTONE_LAMP)
                .name("+ Agregar Luz de Largada", lightsComplete ? NamedTextColor.DARK_GRAY : NamedTextColor.GREEN,
                        TextDecoration.BOLD)
                .emptyLine()
                .lore(GuiUtil.noItalic(Component.text("Luces: ", NamedTextColor.GRAY)
                        .append(Component.text(lightCount + "/5",
                                lightsComplete ? NamedTextColor.GREEN : NamedTextColor.YELLOW))))
                .emptyLine()
                .lore(lightsComplete
                                ? NamedTextColor.DARK_GRAY
                                : NamedTextColor.YELLOW,
                        lightsComplete ? "Ya hay 5 luces. Límite alcanzado."
                                : "Click = mirar un bloque Redstone Lamp.")
                .build());
        inv.setItem(TRACK_CLEAR_LIGHT, ItemBuilder.of(Material.BARRIER)
                .name("✘ Limpiar Luces", NamedTextColor.RED, TextDecoration.BOLD)
                .lore(NamedTextColor.DARK_RED, "Elimina todas las luces (" + lightCount + "/5).")
                .build());
        inv.setItem(TRACK_LIGHT_INFO, buildListInfo("Luces de Largada", lightCount, "/5 registradas"));
    }

    // ── Pestaña 2: Control ────────────────────────────────────────────────────

    private void fillControlTab(Inventory inv) {
        BoatRacingMiniGame mg = plugin.getBoatRacingMiniGame();
        BoatRacingConfig cfg = mg.getConfig();
        boolean configured = mg.validateConfig() == null;

        // Estado actual
        inv.setItem(CTRL_STATUS, ItemBuilder.of(Material.COMPARATOR)
                .name("Estado actual", NamedTextColor.GOLD, TextDecoration.BOLD)
                .emptyLine()
                .lore(GuiUtil.label("Fase",
                        Component.text(stateToSpanish(mg.getState()), NamedTextColor.WHITE)))
                .lore(GuiUtil.label("Config",
                        configured
                                ? Component.text("✔ Completa", NamedTextColor.GREEN)
                                : Component.text("✘ Incompleta — " + mg.validateConfig(), NamedTextColor.RED)))
                .lore(GuiUtil.label("Corredores",
                        Component.text(mg.getRacers().size(), NamedTextColor.WHITE)))
                .build());

        // Botón Paddock
        inv.setItem(CTRL_PADDOCK, ItemBuilder.of(Material.OAK_DOOR)
                .name("→ Mover al Paddock", NamedTextColor.AQUA, TextDecoration.BOLD)
                .emptyLine()
                .lore(NamedTextColor.YELLOW, "Teletransporta a todos los jugadores",
                        "al paddock y prepara la sesión.")
                .lore(NamedTextColor.DARK_GRAY, "Estado requerido: IDLE")
                .build());

        // Botón Qualy
        boolean canQualy = mg.getState() == BoatRacingMiniGame.State.PADDOCK && configured;
        inv.setItem(CTRL_QUALY, ItemBuilder.of(canQualy ? Material.CLOCK : Material.GRAY_CONCRETE)
                .name("▶ Iniciar Qualy", canQualy ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY,
                        TextDecoration.BOLD)
                .emptyLine()
                .lore(NamedTextColor.GRAY, "Inicia la vuelta de clasificación.")
                .lore(NamedTextColor.DARK_GRAY, "Estado requerido: PADDOCK")
                .build());

        // Botón Carrera
        boolean canRace = mg.getState() == BoatRacingMiniGame.State.PADDOCK && configured;
        inv.setItem(CTRL_RACE, ItemBuilder.of(canRace ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE)
                .name("▶ Iniciar Carrera", canRace ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY,
                        TextDecoration.BOLD)
                .emptyLine()
                .lore(NamedTextColor.GRAY, "Inicia la carrera directamente",
                        "(sin qualy previa, o después de la qualy).")
                .lore(NamedTextColor.DARK_GRAY, "Estado requerido: PADDOCK")
                .build());

        // Botón Stop
        boolean running = mg.isRunning();
        inv.setItem(CTRL_STOP_GAME, ItemBuilder.of(running ? Material.RED_CONCRETE : Material.GRAY_CONCRETE)
                .name("■ Detener Sesión", running ? NamedTextColor.RED : NamedTextColor.DARK_GRAY,
                        TextDecoration.BOLD)
                .emptyLine()
                .lore(NamedTextColor.GRAY, "Detiene la sesión y retorna a todos al paddock.")
                .build());
    }

    // ── Click listener ────────────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!GuiUtil.getTitle(event.getView()).equals(TITLE)) return;

        event.setCancelled(true);
        if (!player.isOp()) return;

        int slot = event.getRawSlot();
        int tab  = activeTabs.getOrDefault(player.getUniqueId(), 0);
        BoatRacingMiniGame mg = plugin.getBoatRacingMiniGame();

        // Pestañas
        if (slot == TAB_CONFIG)  { render(player, 0); return; }
        if (slot == TAB_TRACK)   { render(player, 1); return; }
        if (slot == TAB_CONTROL) { render(player, 2); return; }

        // Controles permanentes
        if (slot == CTRL_BACK) { plugin.getEventManagerGUI().open(player); return; }
        if (slot == CTRL_MAGIC_STICK) {
            player.closeInventory();
            plugin.getBoatRacingMagicStick().giveMagicStick(player);
            return;
        }
        if (slot == CTRL_START) {
            if (mg.isRunning() || mg.validateConfig() != null) return;
            mg.start();
            render(player, tab);
            return;
        }
        if (slot == CTRL_STOP) {
            if (!mg.isRunning()) return;
            mg.forceStop();
            render(player, tab);
            return;
        }

        switch (tab) {
            case 0 -> handleConfigClick(player, slot);
            case 1 -> handleTrackClick(player, slot, event);
            case 2 -> handleControlClick(player, slot);
        }
    }

    private void handleConfigClick(Player player, int slot) {
        BoatRacingConfig cfg = plugin.getBoatRacingMiniGame().getConfig();
        switch (slot) {
            case CFG_PADDOCK -> {
                cfg.setPaddockSpawn(player.getLocation());
                ok(player, "Paddock seteado.");
                render(player, 0);
            }
            case CFG_LAPS       -> promptInput(player, InputType.LAPS,
                    "Escribe el número de vueltas (mínimo 1):");
            case CFG_BOAT       -> {
                // Ciclar entre tipos de bote
                BoatType[] types = BoatType.values();
                BoatType current = cfg.getDefaultBoat();
                BoatType next = types[(current.ordinal() + 1) % types.length];
                cfg.setDefaultBoat(next);
                ok(player, "Bote: " + next.name() + ".");
                render(player, 0);
            }
            case CFG_QUALYTIME  -> promptInput(player, InputType.QUALY_TIME,
                    "Escribe la duración de la qualy en segundos:");
            case CFG_SCORE_1    -> promptInput(player, InputType.SCORE_1, "Puntos para la 1ra posición:");
            case CFG_SCORE_2    -> promptInput(player, InputType.SCORE_2, "Puntos para la 2da posición:");
            case CFG_SCORE_3    -> promptInput(player, InputType.SCORE_3, "Puntos para la 3ra posición:");
            case CFG_SCORE_4    -> promptInput(player, InputType.SCORE_4, "Puntos para la 4ta posición:");
        }
    }

    private void handleTrackClick(Player player, int slot, InventoryClickEvent event) {
        BoatRacingConfig cfg = plugin.getBoatRacingMiniGame().getConfig();
        switch (slot) {
            case TRACK_REGION_A  -> { cfg.setTrackPointA(player.getLocation()); ok(player, "Punto A seteado."); render(player, 1); }
            case TRACK_REGION_B  -> { cfg.setTrackPointB(player.getLocation()); ok(player, "Punto B seteado."); render(player, 1); }
            case TRACK_FINISH_A  -> { cfg.setFinishA(player.getLocation()); ok(player, "Meta extremo A seteado."); render(player, 1); }
            case TRACK_FINISH_B  -> { cfg.setFinishB(player.getLocation()); ok(player, "Meta extremo B seteado."); render(player, 1); }
            case TRACK_ADD_SPAWN -> {
                cfg.addPlayerSpawn(player.getLocation());
                ok(player, "Spawn #" + cfg.getPlayerSpawns().size() + " agregado.");
                render(player, 1);
            }
            case TRACK_CLEAR_SPAWN -> {
                player.closeInventory();
                plugin.getConfirmGUI().open(player, "Limpiar spawns",
                        List.of("¿Eliminar todos los spawns de parrilla?",
                                "Total: " + cfg.getPlayerSpawns().size()),
                        () -> { cfg.clearPlayerSpawns(); ok(player, "Spawns eliminados."); render(player, 1); });
            }
            case TRACK_ADD_CP -> {
                cfg.addCheckpoint(player.getLocation(), 4.0);
                ok(player, "Checkpoint #" + cfg.getCheckpoints().size() + " agregado (radio 4).");
                render(player, 1);
            }
            case TRACK_CLEAR_CP -> {
                player.closeInventory();
                plugin.getConfirmGUI().open(player, "Limpiar checkpoints",
                        List.of("¿Eliminar todos los checkpoints?",
                                "Total: " + cfg.getCheckpoints().size()),
                        () -> { cfg.clearCheckpoints(); ok(player, "Checkpoints eliminados."); render(player, 1); });
            }
            case TRACK_ADD_LIGHT -> {
                if (cfg.getLights().size() >= 5) { err(player, "Ya hay 5 luces."); return; }
                var target = player.getTargetBlockExact(10);
                if (target == null) { err(player, "Mira un bloque Redstone Lamp (máx 10 bloques)."); return; }
                cfg.addLight(target.getLocation());
                ok(player, "Luz #" + cfg.getLights().size() + "/5 registrada.");
                render(player, 1);
            }
            case TRACK_CLEAR_LIGHT -> {
                player.closeInventory();
                plugin.getConfirmGUI().open(player, "Limpiar luces",
                        List.of("¿Eliminar todas las luces de largada?"),
                        () -> { cfg.clearLights(); ok(player, "Luces eliminadas."); render(player, 1); });
            }
        }
    }

    private void handleControlClick(Player player, int slot) {
        BoatRacingMiniGame mg = plugin.getBoatRacingMiniGame();
        switch (slot) {
            case CTRL_PADDOCK -> {
                if (mg.isRunning()) { err(player, "Hay una sesión activa. Detenerla primero."); return; }
                boolean ok = mg.sendToPaddock();
                if (ok) ok(player, "Jugadores movidos al paddock.");
                else    err(player, "Paddock no configurado.");
                render(player, 2);
            }
            case CTRL_QUALY -> {
                if (mg.getState() != BoatRacingMiniGame.State.PADDOCK) {
                    err(player, "Estado requerido: PADDOCK."); return;
                }
                if (mg.validateConfig() != null) { err(player, mg.validateConfig()); return; }
                boolean ok = mg.startQualy();
                if (ok) ok(player, "Qualy iniciada.");
                else    err(player, "No se pudo iniciar la qualy.");
                render(player, 2);
            }
            case CTRL_RACE -> {
                if (mg.getState() != BoatRacingMiniGame.State.PADDOCK) {
                    err(player, "Estado requerido: PADDOCK."); return;
                }
                if (mg.validateConfig() != null) { err(player, mg.validateConfig()); return; }
                boolean ok = mg.startRace();
                if (ok) ok(player, "Carrera iniciada.");
                else    err(player, "No se pudo iniciar la carrera.");
                render(player, 2);
            }
            case CTRL_STOP_GAME -> {
                if (!mg.isRunning()) { err(player, "No hay sesión activa."); return; }
                mg.forceStop();
                ok(player, "Sesión detenida.");
                render(player, 2);
            }
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
        PendingInput pending = awaitingInput.remove(player.getUniqueId());
        if (pending == null) return;

        event.setCancelled(true);
        String msg = event.getMessage().trim();

        if (msg.equalsIgnoreCase("cancelar")) {
            player.sendMessage(Component.text("Cancelado.", NamedTextColor.GRAY));
            Bukkit.getScheduler().runTask(plugin, () -> render(player,
                    activeTabs.getOrDefault(player.getUniqueId(), 0)));
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            BoatRacingConfig cfg = plugin.getBoatRacingMiniGame().getConfig();
            int val;
            try { val = Integer.parseInt(msg); }
            catch (NumberFormatException e) {
                err(player, "Número inválido.");
                render(player, activeTabs.getOrDefault(player.getUniqueId(), 0));
                return;
            }

            switch (pending.type()) {
                case LAPS       -> { if (val < 1) err(player, "Mínimo 1."); else { cfg.setTotalLaps(val); ok(player, "Vueltas: " + val + "."); } }
                case QUALY_TIME -> { if (val < 10) err(player, "Mínimo 10s."); else { cfg.setQualyDuration(val); ok(player, "Qualy: " + val + "s."); } }
                case SCORE_1    -> { cfg.setScoreForPosition(1, val); ok(player, "Pos 1: " + val + " pts."); }
                case SCORE_2    -> { cfg.setScoreForPosition(2, val); ok(player, "Pos 2: " + val + " pts."); }
                case SCORE_3    -> { cfg.setScoreForPosition(3, val); ok(player, "Pos 3: " + val + " pts."); }
                case SCORE_4    -> { cfg.setScoreForPosition(4, val); ok(player, "Pos 4: " + val + " pts."); }
                case CP_RADIUS  -> {} // reservado para magic stick
            }
            render(player, activeTabs.getOrDefault(player.getUniqueId(), 0));
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ItemStack buildTab(String name, Material icon, boolean active) {
        return ItemBuilder.of(icon)
                .name(name, active ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY,
                        active ? TextDecoration.BOLD : TextDecoration.ITALIC)
                .lore(active ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY,
                        active ? "▲ Activa" : "Click para cambiar.")
                .build();
    }

    private ItemStack buildLocItem(String name, Material icon, org.bukkit.Location loc,
                                   String desc, String hint) {
        boolean configured = loc != null;
        return ItemBuilder.of(icon)
                .name(name, configured ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD)
                .emptyLine()
                .lore(GuiUtil.noItalic(Component.text("Estado: ", NamedTextColor.GRAY)
                        .append(configured
                                ? Component.text("✔ " + locStr(loc), NamedTextColor.GREEN)
                                : Component.text("✘ No configurado", NamedTextColor.RED))))
                .emptyLine()
                .lore(NamedTextColor.DARK_GRAY, desc)
                .lore(NamedTextColor.YELLOW, hint)
                .build();
    }

    private ItemStack buildNumericItem(String name, Material icon, String value, String desc, String hint) {
        return ItemBuilder.of(icon)
                .name(name, NamedTextColor.AQUA, TextDecoration.BOLD)
                .emptyLine()
                .lore(GuiUtil.label("Actual", Component.text(value, NamedTextColor.WHITE)))
                .emptyLine()
                .lore(NamedTextColor.DARK_GRAY, desc)
                .lore(NamedTextColor.YELLOW, hint)
                .build();
    }

    private ItemStack buildCycleItem(String name, Material icon, String value, String hint) {
        return ItemBuilder.of(icon)
                .name(name, NamedTextColor.AQUA, TextDecoration.BOLD)
                .emptyLine()
                .lore(GuiUtil.label("Actual", Component.text(value, NamedTextColor.WHITE)))
                .emptyLine()
                .lore(NamedTextColor.YELLOW, hint)
                .build();
    }

    private ItemStack buildScoreItem(int pos, int score) {
        String medal = switch (pos) { case 1 -> "🥇"; case 2 -> "🥈"; case 3 -> "🥉"; default -> "#" + pos; };
        return ItemBuilder.of(Material.GOLD_NUGGET)
                .name(medal + " Posición #" + pos, NamedTextColor.YELLOW, TextDecoration.BOLD)
                .emptyLine()
                .lore(GuiUtil.label("Puntos", Component.text(score + " pts", NamedTextColor.YELLOW)))
                .emptyLine()
                .lore(NamedTextColor.YELLOW, "Click para cambiar.")
                .build();
    }

    private ItemStack buildListInfo(String name, int count, String suffix) {
        return ItemBuilder.of(Material.PAPER)
                .name(name, NamedTextColor.GOLD, TextDecoration.BOLD)
                .lore(GuiUtil.label("Total", Component.text(count + " " + suffix, NamedTextColor.WHITE)))
                .build();
    }

    private String stateToSpanish(BoatRacingMiniGame.State s) {
        return switch (s) {
            case IDLE     -> "En espera";
            case PADDOCK  -> "Paddock";
            case QUALY    -> "Clasificación";
            case RACE     -> "Carrera";
            case FINISHED -> "Finalizado";
        };
    }

    private String locStr(org.bukkit.Location l) {
        return String.format("%.0f, %.0f, %.0f", l.getX(), l.getY(), l.getZ());
    }

    private void ok(Player p, String msg)  { p.sendMessage(Component.text("✔ " + msg, NamedTextColor.GREEN)); }
    private void err(Player p, String msg) { p.sendMessage(Component.text("✘ " + msg, NamedTextColor.RED)); }
}