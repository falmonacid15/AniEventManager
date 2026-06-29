package org.falmdev.anieventmanager.minigames.frozenheist;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.model.EventTeam;
import org.falmdev.anieventmanager.utils.TeamUtil;
import org.falmdev.anieventmanager.utils.gui.GuiUtil;
import org.falmdev.anieventmanager.utils.gui.ItemBuilder;

import java.util.*;

public class FrozenHeistAdminGUI implements Listener {

    public static final String TITLE      = "Frozen Heist — Configuración";
    public static final String TITLE_TEAM = "FH — Equipo: ";

    private static final int TAB_GLOBAL = 12;
    private static final int TAB_TEAMS  = 14;

    private static final int GLOB_SPAWN    = 21;
    private static final int GLOB_LOBBY    = 22;
    private static final int GLOB_DURATION = 23;
    private static final int GLOB_SCORE_1  = 30;
    private static final int GLOB_SCORE_2  = 31;
    private static final int GLOB_SCORE_3  = 32;

    // Sub-vista por equipo — ahora con dos slots de base-spawn
    private static final int TEAM_BASE_SPAWN_1 = 20;
    private static final int TEAM_BASE_SPAWN_2 = 21;
    private static final int TEAM_CAPTURE      = 22;
    private static final int TEAM_FLAG_STAND   = 23;
    private static final int TEAM_CORNER1      = 24;
    private static final int TEAM_CORNER2      = 29;
    private static final int TEAM_STATUS       = 32;

    private static final int NAV_MAGIC_STICK = 4;
    private static final int NAV_START       = 16;
    private static final int NAV_STOP        = 10;

    private final Anieventmanager plugin;
    private final Map<UUID, Integer>      activeTabs    = new HashMap<>();
    private final Map<UUID, PendingInput> awaitingInput = new HashMap<>();

    private enum InputType { DURATION }
    private record PendingInput(InputType type) {}

    public FrozenHeistAdminGUI(Anieventmanager plugin) { this.plugin = plugin; }

    public void open(Player player) {
        render(player, activeTabs.getOrDefault(player.getUniqueId(), 0));
    }

    private void render(Player player, int tab) {
        activeTabs.put(player.getUniqueId(), tab);

        Inventory inv = Bukkit.createInventory(null, 54, Component.text(TITLE, NamedTextColor.AQUA));
        GuiUtil.fillSlots(inv, GuiUtil.emptyPane(), 0,1,9,7,8,17,36,45,46,52,53,44);

        inv.setItem(TAB_GLOBAL, buildTab("Global",  Material.COMPARATOR, tab == 0));
        inv.setItem(TAB_TEAMS,  buildTab("Equipos", Material.SHIELD,     tab == 1));

        GuiUtil.fillNavigationHomeOnly(inv);

        inv.setItem(NAV_MAGIC_STICK, ItemBuilder.of(Material.BLAZE_ROD)
                .name("✦ Magic Stick", NamedTextColor.GOLD, TextDecoration.BOLD)
                .emptyLine().lore(NamedTextColor.YELLOW, "Click para obtener el Magic Stick.").build());

        boolean running = plugin.getFrozenHeistMiniGame().isRunning();
        if (!running) {
            boolean ok = plugin.getFrozenHeistMiniGame().validateConfig() == null;
            inv.setItem(NAV_START, ok
                    ? ItemBuilder.of(Material.LIME_CONCRETE).name("▶ Iniciar Frozen Heist", NamedTextColor.GREEN, TextDecoration.BOLD).build()
                    : ItemBuilder.of(Material.GRAY_CONCRETE).name("▶ Iniciar", NamedTextColor.DARK_GRAY, TextDecoration.BOLD)
                      .lore(NamedTextColor.RED, plugin.getFrozenHeistMiniGame().validateConfig()).build());
        } else {
            inv.setItem(NAV_STOP, ItemBuilder.of(Material.RED_CONCRETE)
                    .name("■ Detener Frozen Heist", NamedTextColor.RED, TextDecoration.BOLD).build());
        }

        switch (tab) {
            case 0 -> fillGlobalTab(inv);
            case 1 -> fillTeamsTab(inv);
        }

        player.openInventory(inv);
    }

    private void fillGlobalTab(Inventory inv) {
        FrozenHeistConfig cfg = plugin.getFrozenHeistMiniGame().getConfig();
        boolean hasSpawn = cfg.getGlobalSpawn() != null;
        inv.setItem(GLOB_SPAWN, ItemBuilder.of(Material.NETHER_STAR)
                .name("Spawn Global", hasSpawn ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD).emptyLine()
                .lore(GuiUtil.noItalic(Component.text("Estado: ", NamedTextColor.GRAY)
                        .append(hasSpawn ? Component.text("✔ " + locStr(cfg.getGlobalSpawn()), NamedTextColor.GREEN)
                                : Component.text("✘ No configurado", NamedTextColor.RED))))
                .emptyLine().lore(NamedTextColor.YELLOW, "Click para setear en tu posición.").build());
        inv.setItem(GLOB_LOBBY, ItemBuilder.of(Material.OAK_DOOR)
                .name("Mover al Spawn Global", NamedTextColor.AQUA, TextDecoration.BOLD)
                .emptyLine().lore(NamedTextColor.YELLOW, "Click para teleportar a todos los jugadores.").build());
        inv.setItem(GLOB_DURATION, ItemBuilder.of(Material.CLOCK)
                .name("Duración", NamedTextColor.AQUA, TextDecoration.BOLD).emptyLine()
                .lore(GuiUtil.label("Actual", Component.text(cfg.getDurationMinutes() + " minutos", NamedTextColor.WHITE)))
                .emptyLine().lore(NamedTextColor.YELLOW, "Click para cambiar.").build());
        inv.setItem(27, ItemBuilder.of(Material.GOLD_INGOT)
                .name("Puntajes Internos (FH)", NamedTextColor.GOLD, TextDecoration.BOLD).emptyLine()
                .lore(NamedTextColor.DARK_GRAY, "Puntos por captura/recuperación dentro del minijuego.").build());
        inv.setItem(GLOB_SCORE_1, ItemBuilder.of(Material.BLUE_BANNER)
                .name("Capturar bandera enemiga", NamedTextColor.AQUA, TextDecoration.BOLD).emptyLine()
                .lore(GuiUtil.label("Puntos", Component.text(TeamHeistData.POINTS_CAPTURE + " pts", NamedTextColor.YELLOW)))
                .emptyLine().lore(NamedTextColor.DARK_GRAY, "Constante en TeamHeistData.POINTS_CAPTURE").build());
        inv.setItem(GLOB_SCORE_2, ItemBuilder.of(Material.WHITE_BANNER)
                .name("Recuperar bandera propia", NamedTextColor.GREEN, TextDecoration.BOLD).emptyLine()
                .lore(GuiUtil.label("Puntos", Component.text(TeamHeistData.POINTS_RECOVER + " pts", NamedTextColor.YELLOW)))
                .emptyLine().lore(NamedTextColor.DARK_GRAY, "Constante en TeamHeistData.POINTS_RECOVER").build());
        inv.setItem(GLOB_SCORE_3, ItemBuilder.of(Material.PAPER)
                .name("Info de combate", NamedTextColor.GRAY, TextDecoration.BOLD).emptyLine()
                .lore(GuiUtil.label("Hits para congelar",  Component.text(PlayerState.HITS_TO_FREEZE, NamedTextColor.WHITE)))
                .lore(GuiUtil.label("Duración freeze",     Component.text(PlayerState.FREEZE_DURATION_MS / 1000 + "s", NamedTextColor.WHITE)))
                .lore(GuiUtil.label("Cooldown snowball",   Component.text(PlayerState.SNOWBALL_COOLDOWN_MS + "ms", NamedTextColor.WHITE)))
                .lore(GuiUtil.label("Tiempo rescate",      Component.text(PlayerState.RESCUE_TIME_MS / 1000.0 + "s", NamedTextColor.WHITE)))
                .emptyLine().lore(NamedTextColor.DARK_GRAY, "Modificar en PlayerState.java").build());
    }

    private void fillTeamsTab(Inventory inv) {
        Collection<EventTeam> teams = plugin.getTeamManager().getAllTeams();
        FrozenHeistConfig cfg = plugin.getFrozenHeistMiniGame().getConfig();
        int slot = 19;
        for (EventTeam team : teams) {
            if (slot >= 44) break;
            inv.setItem(slot, buildTeamStatusItem(team, cfg));
            slot++;
            if (slot % 9 == 8) slot += 2;
        }
        if (teams.isEmpty())
            inv.setItem(22, ItemBuilder.of(Material.BARRIER)
                    .name("No hay equipos creados", NamedTextColor.RED)
                    .lore(NamedTextColor.GRAY, "Crea equipos con /em team create.").build());
    }

    private ItemStack buildTeamStatusItem(EventTeam team, FrozenHeistConfig cfg) {
        String id = team.getId();
        boolean hasBS1    = cfg.getBaseSpawn1(id) != null;
        boolean hasBS2    = cfg.getBaseSpawn2(id) != null;
        boolean hasC      = cfg.getCaptureZone(id) != null;
        boolean hasF      = cfg.getFlagStand(id) != null;
        boolean hasCorners= cfg.getBaseCorner1(id) != null && cfg.getBaseCorner2(id) != null;
        boolean complete  = hasBS1 && hasBS2 && hasC && hasF && hasCorners;
        return ItemBuilder.of(TeamUtil.colorToBannerMaterial(team.getColor()))
                .name(team.getDisplayName(), team.getColor(), TextDecoration.BOLD).emptyLine()
                .lore(GuiUtil.noItalic(Component.text("Base spawn 1: ", NamedTextColor.GRAY).append(statusIcon(hasBS1))))
                .lore(GuiUtil.noItalic(Component.text("Base spawn 2: ", NamedTextColor.GRAY).append(statusIcon(hasBS2))))
                .lore(GuiUtil.noItalic(Component.text("Capture zone: ", NamedTextColor.GRAY).append(statusIcon(hasC))))
                .lore(GuiUtil.noItalic(Component.text("Flag stand:   ", NamedTextColor.GRAY).append(statusIcon(hasF))))
                .lore(GuiUtil.noItalic(Component.text("Base corners: ", NamedTextColor.GRAY).append(statusIcon(hasCorners)))).emptyLine()
                .lore(complete ? GuiUtil.noItalic(Component.text("✔ Config completa", NamedTextColor.GREEN))
                        : GuiUtil.noItalic(Component.text("✘ Config incompleta", NamedTextColor.RED)))
                .emptyLine().lore(NamedTextColor.YELLOW, "Click para configurar este equipo.").build();
    }

    // ── Sub-vista por equipo ──────────────────────────────────────────────────

    public void openTeamConfig(Player player, EventTeam team) {
        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text(TITLE_TEAM, NamedTextColor.AQUA)
                        .append(Component.text(team.getDisplayName(), team.getColor())));
        GuiUtil.fillAll(inv);

        FrozenHeistConfig cfg = plugin.getFrozenHeistMiniGame().getConfig();
        String id = team.getId();

        inv.setItem(TEAM_BASE_SPAWN_1, buildLocItem("Base Spawn 1", Material.LIME_BED,
                cfg.getBaseSpawn1(id), "Spawn del jugador 1 dentro de la base.", "Click para setear en tu posición."));
        inv.setItem(TEAM_BASE_SPAWN_2, buildLocItem("Base Spawn 2", Material.RED_BED,
                cfg.getBaseSpawn2(id), "Spawn del jugador 2 dentro de la base.", "Click para setear en tu posición."));
        inv.setItem(TEAM_CAPTURE,    buildLocItem("Capture Zone", Material.BEACON,
                cfg.getCaptureZone(id), "Zona donde se entrega la bandera enemiga.", "Click para setear."));
        inv.setItem(TEAM_FLAG_STAND, buildLocItem("Flag Stand",   Material.WHITE_BANNER,
                cfg.getFlagStand(id),   "Posición original de la bandera propia.", "Click para setear."));
        inv.setItem(TEAM_CORNER1,    buildLocItem("Base Corner 1",Material.GREEN_CONCRETE,
                cfg.getBaseCorner1(id), "Esquina 1 de la zona segura (no PvP).", "Click para setear."));
        inv.setItem(TEAM_CORNER2,    buildLocItem("Base Corner 2",Material.RED_CONCRETE,
                cfg.getBaseCorner2(id), "Esquina 2 de la zona segura.", "Click para setear."));

        boolean complete = cfg.getBaseSpawn1(id) != null && cfg.getBaseSpawn2(id) != null
                && cfg.getCaptureZone(id) != null && cfg.getFlagStand(id) != null
                && cfg.getBaseCorner1(id) != null && cfg.getBaseCorner2(id) != null;
        inv.setItem(TEAM_STATUS, ItemBuilder.of(complete ? Material.LIME_CONCRETE : Material.RED_CONCRETE)
                .name(complete ? "✔ Configuración completa" : "✘ Faltan datos",
                        complete ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD).build());

        GuiUtil.fillNavigation(inv);
        inv.setItem(NAV_MAGIC_STICK, ItemBuilder.of(Material.BLAZE_ROD)
                .name("✦ Magic Stick [" + team.getDisplayName() + "]", NamedTextColor.GOLD, TextDecoration.BOLD)
                .emptyLine().lore(NamedTextColor.YELLOW, "Click para obtener el stick preconfigurado.").build());

        player.openInventory(inv);
    }

    // ── Click listener ────────────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = GuiUtil.getTitle(event.getView());

        if (title.startsWith(TITLE_TEAM)) {
            event.setCancelled(true);
            if (!player.isOp()) return;
            handleTeamConfigClick(player, title, event.getRawSlot());
            return;
        }

        if (!title.equals(TITLE)) return;
        event.setCancelled(true);
        if (!player.isOp()) return;

        int slot = event.getRawSlot();
        int tab  = activeTabs.getOrDefault(player.getUniqueId(), 0);

        if (slot == TAB_GLOBAL) { render(player, 0); return; }
        if (slot == TAB_TEAMS)  { render(player, 1); return; }

        if (GuiUtil.handleNavigation(slot, player, plugin, null)) return;

        if (slot == NAV_MAGIC_STICK) { player.closeInventory(); plugin.getFrozenHeistMagicStick().giveMagicStick(player, null); return; }
        if (slot == NAV_START) { if (plugin.getFrozenHeistMiniGame().isRunning() || plugin.getFrozenHeistMiniGame().validateConfig() != null) return; plugin.getFrozenHeistMiniGame().start(); render(player, tab); return; }
        if (slot == NAV_STOP)  { if (!plugin.getFrozenHeistMiniGame().isRunning()) return; plugin.getFrozenHeistMiniGame().forceStop(); render(player, tab); return; }

        if (tab == 0) handleGlobalClick(player, slot);
        if (tab == 1) handleTeamsTabClick(player, slot);
    }

    private void handleGlobalClick(Player player, int slot) {
        FrozenHeistConfig cfg = plugin.getFrozenHeistMiniGame().getConfig();
        switch (slot) {
            case GLOB_SPAWN    -> { cfg.setGlobalSpawn(player.getLocation()); ok(player, "Spawn global seteado."); render(player, 0); }
            case GLOB_LOBBY    -> { Location spawn = cfg.getGlobalSpawn(); if (spawn == null) { err(player, "Spawn no configurado."); return; } Bukkit.getOnlinePlayers().forEach(p -> p.teleport(spawn)); ok(player, "Todos teleportados."); }
            case GLOB_DURATION -> promptInput(player, InputType.DURATION, "Duración en minutos (mínimo 1):");
        }
    }

    private void handleTeamsTabClick(Player player, int slot) {
        List<EventTeam> teamList = new ArrayList<>(plugin.getTeamManager().getAllTeams());
        int s = 19;
        for (EventTeam team : teamList) {
            if (s >= 44) break;
            if (s == slot) { openTeamConfig(player, team); return; }
            s++;
            if (s % 9 == 8) s += 2;
        }
    }

    private void handleTeamConfigClick(Player player, String title, int slot) {
        String displayName = title.substring(TITLE_TEAM.length());
        EventTeam team = TeamUtil.findByDisplayName(plugin, displayName);
        if (team == null) { player.closeInventory(); return; }

        if (GuiUtil.handleNavigation(slot, player, plugin, () -> render(player, 1))) return;

        FrozenHeistConfig cfg = plugin.getFrozenHeistMiniGame().getConfig();
        String id = team.getId();
        switch (slot) {
            case TEAM_BASE_SPAWN_1 -> { cfg.setBaseSpawn1(id, player.getLocation()); ok(player, "Base spawn 1 seteado."); openTeamConfig(player, team); }
            case TEAM_BASE_SPAWN_2 -> { cfg.setBaseSpawn2(id, player.getLocation()); ok(player, "Base spawn 2 seteado."); openTeamConfig(player, team); }
            case TEAM_CAPTURE      -> { cfg.setCaptureZone(id, player.getLocation()); ok(player, "Capture zone seteada."); openTeamConfig(player, team); }
            case TEAM_FLAG_STAND   -> { cfg.setFlagStand(id, player.getLocation()); ok(player, "Flag stand seteado.");    openTeamConfig(player, team); }
            case TEAM_CORNER1      -> { cfg.setBaseCorner1(id, player.getLocation()); ok(player, "Corner 1 seteado.");    openTeamConfig(player, team); }
            case TEAM_CORNER2      -> { cfg.setBaseCorner2(id, player.getLocation()); ok(player, "Corner 2 seteado.");    openTeamConfig(player, team); }
            case 49                -> { player.closeInventory(); plugin.getFrozenHeistMagicStick().giveMagicStick(player, team); }
        }
    }

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
            Bukkit.getScheduler().runTask(plugin, () -> render(player, activeTabs.getOrDefault(player.getUniqueId(), 0)));
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            int val;
            try { val = Integer.parseInt(msg); }
            catch (NumberFormatException e) { err(player, "Número inválido."); render(player, activeTabs.getOrDefault(player.getUniqueId(), 0)); return; }
            if (pending.type() == InputType.DURATION) {
                if (val < 1) err(player, "Mínimo 1.");
                else { plugin.getFrozenHeistMiniGame().getConfig().setDurationMinutes(val); ok(player, "Duración: " + val + " min."); }
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
                        active ? "▲ Activa" : "Click para cambiar.").build();
    }

    private ItemStack buildLocItem(String name, Material icon, Location loc, String desc, String hint) {
        boolean configured = loc != null;
        return ItemBuilder.of(icon)
                .name(name, configured ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD).emptyLine()
                .lore(GuiUtil.noItalic(Component.text("Estado: ", NamedTextColor.GRAY)
                        .append(configured
                                ? Component.text("✔ " + locStr(loc), NamedTextColor.GREEN)
                                : Component.text("✘ No configurado", NamedTextColor.RED))))
                .emptyLine()
                .lore(NamedTextColor.DARK_GRAY, desc)
                .lore(NamedTextColor.YELLOW, hint).build();
    }

    private Component statusIcon(boolean ok) {
        return ok ? Component.text("✔", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)
                : Component.text("✘", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false);
    }

    private String locStr(Location l) { return String.format("%.0f, %.0f, %.0f", l.getX(), l.getY(), l.getZ()); }
    private void ok(Player p, String msg)  { p.sendMessage(Component.text("✔ " + msg, NamedTextColor.GREEN)); }
    private void err(Player p, String msg) { p.sendMessage(Component.text("✘ " + msg, NamedTextColor.RED)); }
}