package org.falmdev.anieventmanager.minigames.parkourduos;

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
import org.falmdev.anieventmanager.model.EventTeam;
import org.falmdev.anieventmanager.utils.TeamUtil;
import org.falmdev.anieventmanager.utils.gui.GuiUtil;
import org.falmdev.anieventmanager.utils.gui.ItemBuilder;

import java.util.*;

public class ParkourDuosAdminGUI implements Listener {

    public static final String TITLE      = "Parkour Duos — Configuración";
    public static final String TITLE_TEAM = "PD — Equipo: ";

    private static final int TAB_GLOBAL = 1;
    private static final int TAB_TEAMS  = 2;

    private static final int GLOB_LOBBY     = 10;
    private static final int GLOB_DURATION  = 12;
    private static final int GLOB_COUNTDOWN = 14;
    private static final int GLOB_CHAIN     = 16;
    private static final int GLOB_SCORE_1   = 28;
    private static final int GLOB_SCORE_2   = 30;
    private static final int GLOB_SCORE_3   = 32;
    private static final int GLOB_SCORE_DEF = 34;
    private static final int GLOB_SCORE_CP  = 36;

    private static final int TEAM_SPAWN1   = 10;
    private static final int TEAM_SPAWN2   = 12;
    private static final int TEAM_START    = 14;
    private static final int TEAM_FINISH   = 16;
    private static final int TEAM_CP_PREV  = 19;
    private static final int TEAM_CP_INFO  = 22;
    private static final int TEAM_CP_NEXT  = 25;
    private static final int TEAM_CP_ADD   = 28;
    private static final int TEAM_CP_CLEAR = 34;
    private static final int TEAM_STATUS   = 40;
    private static final int[] CP_SLOTS = {36, 37, 38, 39, 40, 41, 42};

    private static final int NAV_MAGIC_STICK = 49;
    private static final int NAV_START       = 52;
    private static final int NAV_STOP        = 53;

    private final Anieventmanager plugin;
    private final Map<UUID, Integer>      activeTabs    = new HashMap<>();
    private final Map<UUID, Integer>      cpPages       = new HashMap<>();
    private final Map<UUID, PendingInput> awaitingInput = new HashMap<>();

    private enum InputType { DURATION, COUNTDOWN, CHAIN, SCORE_1, SCORE_2, SCORE_3, SCORE_DEF, SCORE_CP }
    private record PendingInput(InputType type) {}

    public ParkourDuosAdminGUI(Anieventmanager plugin) { this.plugin = plugin; }

    public void open(Player player) {
        render(player, activeTabs.getOrDefault(player.getUniqueId(), 0));
    }

    private void render(Player player, int tab) {
        activeTabs.put(player.getUniqueId(), tab);

        Inventory inv = Bukkit.createInventory(null, 54, Component.text(TITLE, NamedTextColor.GOLD));
        GuiUtil.fillAll(inv);

        inv.setItem(TAB_GLOBAL, buildTab("Global",  Material.COMPARATOR, tab == 0));
        inv.setItem(TAB_TEAMS,  buildTab("Equipos", Material.IRON_BOOTS, tab == 1));

        // ── Fila 5: HomeOnly + controles ──────────────────────────────────────
        GuiUtil.fillNavigationHomeOnly(inv);

        inv.setItem(NAV_MAGIC_STICK, ItemBuilder.of(Material.BLAZE_ROD)
                .name("✦ Magic Stick", NamedTextColor.GOLD, TextDecoration.BOLD)
                .emptyLine().lore(NamedTextColor.YELLOW, "Click para obtener el Magic Stick.").build());

        ParkourDuosMiniGame mg = plugin.getParkourDuosMiniGame();
        boolean running = mg.isRunning();
        if (!running) {
            boolean ok = mg.validateConfig() == null;
            inv.setItem(NAV_START, ok
                    ? ItemBuilder.of(Material.LIME_CONCRETE).name("▶ Iniciar Parkour Duos", NamedTextColor.GREEN, TextDecoration.BOLD).build()
                    : ItemBuilder.of(Material.GRAY_CONCRETE).name("▶ Iniciar", NamedTextColor.DARK_GRAY, TextDecoration.BOLD)
                      .lore(NamedTextColor.RED, mg.validateConfig()).build());
        } else {
            inv.setItem(NAV_STOP, ItemBuilder.of(Material.RED_CONCRETE).name("■ Detener", NamedTextColor.RED, TextDecoration.BOLD).build());
        }

        switch (tab) {
            case 0 -> fillGlobalTab(inv);
            case 1 -> fillTeamsTab(inv);
        }

        player.openInventory(inv);
    }

    private void fillGlobalTab(Inventory inv) {
        ParkourDuosConfig cfg = plugin.getParkourDuosMiniGame().getConfig();
        boolean hasLobby = cfg.getLobby() != null;
        inv.setItem(GLOB_LOBBY, ItemBuilder.of(Material.OAK_DOOR)
                .name("Lobby", hasLobby ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD).emptyLine()
                .lore(GuiUtil.noItalic(Component.text("Estado: ", NamedTextColor.GRAY)
                        .append(hasLobby ? Component.text("✔ " + locStr(cfg.getLobby()), NamedTextColor.GREEN) : Component.text("✘ No configurado", NamedTextColor.RED))))
                .emptyLine().lore(NamedTextColor.YELLOW, "Click para setear en tu posición.").build());
        inv.setItem(GLOB_DURATION,  buildNumericItem("Duración",  Material.CLOCK, cfg.getDurationMinutes() + " minutos", "Tiempo límite.", "Click para cambiar. (Mínimo: 1)"));
        inv.setItem(GLOB_COUNTDOWN, buildNumericItem("Countdown", Material.CLOCK, cfg.getCountdownSeconds() + " segundos", "Cuenta regresiva.", "Click para cambiar. (Mínimo: 1)"));
        inv.setItem(GLOB_CHAIN, ItemBuilder.of(Material.LEAD).name("Distancia Máxima de Cadena", NamedTextColor.AQUA, TextDecoration.BOLD).emptyLine()
                .lore(GuiUtil.label("Actual", Component.text(cfg.getChainMaxDistance() + " bloques", NamedTextColor.WHITE)))
                .emptyLine().lore(NamedTextColor.DARK_GRAY, "Distancia máx. antes de que la cadena jale.")
                .lore(NamedTextColor.YELLOW, "Click para cambiar. (Mínimo: 1)").build());
        inv.setItem(27, ItemBuilder.of(Material.GOLD_INGOT).name("Puntajes", NamedTextColor.GOLD, TextDecoration.BOLD).build());
        inv.setItem(GLOB_SCORE_1,   buildScoreItem(1,  cfg.getScoreFirst(),   "🥇", NamedTextColor.GOLD));
        inv.setItem(GLOB_SCORE_2,   buildScoreItem(2,  cfg.getScoreSecond(),  "🥈", NamedTextColor.GRAY));
        inv.setItem(GLOB_SCORE_3,   buildScoreItem(3,  cfg.getScoreThird(),   "🥉", NamedTextColor.RED));
        inv.setItem(GLOB_SCORE_DEF, buildScoreItem(-1, cfg.getScoreDefault(), "  ", NamedTextColor.DARK_GRAY));
        inv.setItem(GLOB_SCORE_CP, ItemBuilder.of(Material.PAPER).name("Puntos por Checkpoint", NamedTextColor.AQUA, TextDecoration.BOLD).emptyLine()
                .lore(GuiUtil.label("Actual", Component.text(cfg.getScorePerCheckpoint() + " pts", NamedTextColor.YELLOW)))
                .emptyLine().lore(NamedTextColor.YELLOW, "Click para cambiar.").build());
    }

    private void fillTeamsTab(Inventory inv) {
        Collection<EventTeam> teams = plugin.getTeamManager().getAllTeams();
        ParkourDuosConfig cfg = plugin.getParkourDuosMiniGame().getConfig();
        int slot = 10;
        for (EventTeam team : teams) {
            if (slot >= 44) break;
            inv.setItem(slot, buildTeamStatusItem(team, cfg));
            slot++; if (slot % 9 == 8) slot += 2;
        }
        if (teams.isEmpty()) inv.setItem(22, ItemBuilder.of(Material.BARRIER).name("No hay equipos creados", NamedTextColor.RED).lore(NamedTextColor.GRAY, "Crea equipos con /em team create.").build());
    }

    private ItemStack buildTeamStatusItem(EventTeam team, ParkourDuosConfig cfg) {
        String id = team.getId();
        String err = cfg.validateTeam(id);
        int cpCount = cfg.getCheckpointCount(id);
        return ItemBuilder.of(TeamUtil.colorToBannerMaterial(team.getColor()))
                .name(team.getDisplayName(), team.getColor(), TextDecoration.BOLD).emptyLine()
                .lore(GuiUtil.noItalic(Component.text("Spawn 1:   ", NamedTextColor.GRAY).append(statusIcon(cfg.getTeamSpawn1(id) != null))))
                .lore(GuiUtil.noItalic(Component.text("Spawn 2:   ", NamedTextColor.GRAY).append(statusIcon(cfg.getTeamSpawn2(id) != null))))
                .lore(GuiUtil.noItalic(Component.text("Start:     ", NamedTextColor.GRAY).append(statusIcon(cfg.getTeamStart(id) != null))))
                .lore(GuiUtil.noItalic(Component.text("Finish:    ", NamedTextColor.GRAY).append(statusIcon(cfg.getTeamFinish(id) != null))))
                .lore(GuiUtil.noItalic(Component.text("CPs:       ", NamedTextColor.GRAY)
                        .append(Component.text(cpCount + " configurados", cpCount > 0 ? NamedTextColor.GREEN : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)))).emptyLine()
                .lore(err == null ? GuiUtil.noItalic(Component.text("✔ Config completa", NamedTextColor.GREEN)) : GuiUtil.noItalic(Component.text("✘ " + err, NamedTextColor.RED)))
                .emptyLine().lore(NamedTextColor.YELLOW, "Click para configurar este equipo.").build();
    }

    // ── Sub-vista por equipo ──────────────────────────────────────────────────

    public void openTeamConfig(Player player, EventTeam team) {
        cpPages.putIfAbsent(player.getUniqueId(), 0);
        renderTeamConfig(player, team, cpPages.get(player.getUniqueId()));
    }

    private void renderTeamConfig(Player player, EventTeam team, int cpPage) {
        cpPages.put(player.getUniqueId(), cpPage);

        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text(TITLE_TEAM, NamedTextColor.GOLD).append(Component.text(team.getDisplayName(), team.getColor())));
        GuiUtil.fillAll(inv);

        ParkourDuosConfig cfg = plugin.getParkourDuosMiniGame().getConfig();
        String id = team.getId();

        inv.setItem(TEAM_SPAWN1, buildLocItem("Spawn 1", Material.LIME_BED,       cfg.getTeamSpawn1(id), "Spawn del jugador 1.", "Click para setear."));
        inv.setItem(TEAM_SPAWN2, buildLocItem("Spawn 2", Material.CYAN_BED,       cfg.getTeamSpawn2(id), "Spawn del jugador 2.", "Click para setear."));
        inv.setItem(TEAM_START,  buildLocItem("Start",   Material.GREEN_CONCRETE, cfg.getTeamStart(id),  "Punto de inicio.", "Click para setear."));
        inv.setItem(TEAM_FINISH, buildLocItem("Finish",  Material.GOLD_BLOCK,     cfg.getTeamFinish(id), "Punto de finalización.", "Click para setear."));

        List<ParkourCheckpoint> cps = cfg.getCheckpoints(id);
        int totalPages = Math.max(1, (int) Math.ceil(cps.size() / (double) CP_SLOTS.length));
        int page = Math.max(0, Math.min(cpPage, totalPages - 1));

        inv.setItem(TEAM_CP_INFO, ItemBuilder.of(Material.PAPER).name("Checkpoints", NamedTextColor.GOLD, TextDecoration.BOLD)
                .lore(GuiUtil.label("Total", Component.text(cps.size(), NamedTextColor.WHITE)))
                .lore(GuiUtil.label("Página", Component.text((page+1) + "/" + totalPages, NamedTextColor.WHITE))).build());
        if (page > 0)           inv.setItem(TEAM_CP_PREV, ItemBuilder.of(Material.ARROW).name("← Página anterior", NamedTextColor.YELLOW).build());
        if (page < totalPages-1) inv.setItem(TEAM_CP_NEXT, ItemBuilder.of(Material.ARROW).name("Página siguiente →", NamedTextColor.YELLOW).build());

        int start = page * CP_SLOTS.length;
        for (int i = 0; i < CP_SLOTS.length; i++) {
            int cpIdx = start + i;
            if (cpIdx < cps.size()) {
                ParkourCheckpoint cp = cps.get(cpIdx);
                inv.setItem(CP_SLOTS[i], ItemBuilder.of(Material.LIME_DYE).name("CP #" + (cpIdx+1), NamedTextColor.AQUA, TextDecoration.BOLD).emptyLine()
                        .lore(GuiUtil.label("Pos",   Component.text(locStr(cp.getCenter()), NamedTextColor.WHITE)))
                        .lore(GuiUtil.label("Radio", Component.text(cp.getRadius() + " bloques", NamedTextColor.WHITE)))
                        .emptyLine().lore(NamedTextColor.RED, "Shift+Click para eliminar.").build());
            }
        }

        inv.setItem(TEAM_CP_ADD, ItemBuilder.of(Material.LIME_DYE).name("+ Agregar Checkpoint", NamedTextColor.GREEN, TextDecoration.BOLD).emptyLine()
                .lore(NamedTextColor.YELLOW, "Click para agregar en tu posición.", "Radio por defecto: 3 bloques.").build());
        inv.setItem(TEAM_CP_CLEAR, ItemBuilder.of(Material.BARRIER).name("✘ Limpiar Checkpoints", NamedTextColor.RED, TextDecoration.BOLD)
                .lore(NamedTextColor.DARK_RED, "Elimina todos los CPs (" + cps.size() + ").").build());

        String err = cfg.validateTeam(id);
        inv.setItem(TEAM_STATUS, ItemBuilder.of(err == null ? Material.LIME_CONCRETE : Material.RED_CONCRETE)
                .name(err == null ? "✔ Config completa" : "✘ Incompleta", err == null ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD).build());

        // ── Fila 5: navegación completa (Volver + Inicio) ─────────────────────
        GuiUtil.fillNavigation(inv); // 48=Volver a lista, 50=⌂Inicio
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
            handleTeamConfigClick(player, title, event);
            return;
        }

        if (!title.equals(TITLE)) return;
        event.setCancelled(true);
        if (!player.isOp()) return;

        int slot = event.getRawSlot();
        int tab  = activeTabs.getOrDefault(player.getUniqueId(), 0);
        ParkourDuosMiniGame mg = plugin.getParkourDuosMiniGame();

        if (slot == TAB_GLOBAL) { render(player, 0); return; }
        if (slot == TAB_TEAMS)  { render(player, 1); return; }

        // Navegación: pantalla raíz → null
        if (GuiUtil.handleNavigation(slot, player, plugin, null)) return;

        if (slot == NAV_MAGIC_STICK) { player.closeInventory(); plugin.getParkourDuosMagicStick().giveMagicStick(player, null); return; }
        if (slot == NAV_START) { if (mg.isRunning() || mg.validateConfig() != null) return; mg.start(); render(player, tab); return; }
        if (slot == NAV_STOP)  { if (!mg.isRunning()) return; mg.forceStop(); render(player, tab); return; }

        if (tab == 0) handleGlobalClick(player, slot);
        if (tab == 1) handleTeamsTabClick(player, slot);
    }

    private void handleGlobalClick(Player player, int slot) {
        ParkourDuosConfig cfg = plugin.getParkourDuosMiniGame().getConfig();
        switch (slot) {
            case GLOB_LOBBY     -> { cfg.setLobby(player.getLocation()); ok(player, "Lobby seteado."); render(player, 0); }
            case GLOB_DURATION  -> promptInput(player, InputType.DURATION,  "Duración en minutos (mínimo 1):");
            case GLOB_COUNTDOWN -> promptInput(player, InputType.COUNTDOWN, "Countdown en segundos (mínimo 1):");
            case GLOB_CHAIN     -> promptInput(player, InputType.CHAIN,     "Distancia máxima de cadena en bloques (mínimo 1):");
            case GLOB_SCORE_1   -> promptInput(player, InputType.SCORE_1,   "Puntos para el 1er lugar:");
            case GLOB_SCORE_2   -> promptInput(player, InputType.SCORE_2,   "Puntos para el 2do lugar:");
            case GLOB_SCORE_3   -> promptInput(player, InputType.SCORE_3,   "Puntos para el 3er lugar:");
            case GLOB_SCORE_DEF -> promptInput(player, InputType.SCORE_DEF, "Puntos por defecto:");
            case GLOB_SCORE_CP  -> promptInput(player, InputType.SCORE_CP,  "Puntos por checkpoint:");
        }
    }

    private void handleTeamsTabClick(Player player, int slot) {
        List<EventTeam> teamList = new ArrayList<>(plugin.getTeamManager().getAllTeams());
        int s = 10;
        for (EventTeam team : teamList) {
            if (s >= 44) break;
            if (s == slot) { openTeamConfig(player, team); return; }
            s++; if (s % 9 == 8) s += 2;
        }
    }

    private void handleTeamConfigClick(Player player, String title, InventoryClickEvent event) {
        String displayName = title.substring(TITLE_TEAM.length());
        EventTeam team = TeamUtil.findByDisplayName(plugin, displayName);
        if (team == null) { player.closeInventory(); return; }

        int slot = event.getRawSlot();
        ParkourDuosConfig cfg = plugin.getParkourDuosMiniGame().getConfig();
        String id = team.getId();
        int page = cpPages.getOrDefault(player.getUniqueId(), 0);

        // Navegación sub-vista: 48=Volver a lista equipos, 50=Inicio
        if (GuiUtil.handleNavigation(slot, player, plugin, () -> render(player, 1))) return;

        switch (slot) {
            case TEAM_SPAWN1   -> { cfg.setTeamSpawn1(id, player.getLocation()); ok(player, "Spawn 1 seteado."); renderTeamConfig(player, team, page); }
            case TEAM_SPAWN2   -> { cfg.setTeamSpawn2(id, player.getLocation()); ok(player, "Spawn 2 seteado."); renderTeamConfig(player, team, page); }
            case TEAM_START    -> { cfg.setTeamStart(id, player.getLocation());  ok(player, "Start seteado.");   renderTeamConfig(player, team, page); }
            case TEAM_FINISH   -> { cfg.setTeamFinish(id, player.getLocation()); ok(player, "Finish seteado.");  renderTeamConfig(player, team, page); }
            case TEAM_CP_PREV  -> renderTeamConfig(player, team, page - 1);
            case TEAM_CP_NEXT  -> renderTeamConfig(player, team, page + 1);
            case TEAM_CP_ADD   -> { cfg.addCheckpoint(id, player.getLocation(), 3.0); ok(player, "Checkpoint #" + cfg.getCheckpointCount(id) + " agregado."); renderTeamConfig(player, team, page); }
            case TEAM_CP_CLEAR -> {
                player.closeInventory();
                plugin.getConfirmGUI().open(player, "Limpiar checkpoints",
                        List.of("¿Eliminar todos los CPs de " + team.getDisplayName() + "?", "Total: " + cfg.getCheckpointCount(id)),
                        () -> { cfg.clearCheckpoints(id); ok(player, "Checkpoints eliminados."); renderTeamConfig(player, team, 0); });
            }
            case 49 -> { player.closeInventory(); plugin.getParkourDuosMagicStick().giveMagicStick(player, team); }
            default -> {
                if (!event.isShiftClick()) return;
                List<Integer> cpSlotList = Arrays.stream(CP_SLOTS).boxed().toList();
                int cpSlotIdx = cpSlotList.indexOf(slot);
                if (cpSlotIdx < 0) return;
                int cpIdx = page * CP_SLOTS.length + cpSlotIdx;
                if (cpIdx >= cfg.getCheckpointCount(id)) return;
                cfg.removeCheckpoint(id, cpIdx);
                ok(player, "Checkpoint #" + (cpIdx+1) + " eliminado.");
                renderTeamConfig(player, team, page);
            }
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
        if (msg.equalsIgnoreCase("cancelar")) { player.sendMessage(Component.text("Cancelado.", NamedTextColor.GRAY)); Bukkit.getScheduler().runTask(plugin, () -> render(player, activeTabs.getOrDefault(player.getUniqueId(), 0))); return; }
        Bukkit.getScheduler().runTask(plugin, () -> {
            ParkourDuosConfig cfg = plugin.getParkourDuosMiniGame().getConfig();
            if (pending.type() == InputType.CHAIN) {
                try { double val = Double.parseDouble(msg); if (val < 1) err(player, "Mínimo 1."); else { cfg.setChainMaxDistance(val); plugin.getParkourDuosMiniGame().getChainManager().setMaxDistance(val); ok(player, "Cadena: " + val + " bloques."); } }
                catch (NumberFormatException e) { err(player, "Número inválido."); }
                render(player, activeTabs.getOrDefault(player.getUniqueId(), 0)); return;
            }
            int val; try { val = Integer.parseInt(msg); } catch (NumberFormatException e) { err(player, "Número inválido."); render(player, activeTabs.getOrDefault(player.getUniqueId(), 0)); return; }
            switch (pending.type()) {
                case DURATION   -> { if (val < 1) err(player, "Mínimo 1."); else { cfg.setDurationMinutes(val);  ok(player, "Duración: " + val + " min."); } }
                case COUNTDOWN  -> { if (val < 1) err(player, "Mínimo 1."); else { cfg.setCountdownSeconds(val); ok(player, "Countdown: " + val + "s."); } }
                case SCORE_1    -> { cfg.setScoreForPlace(1, val); ok(player, "1er lugar: " + val + " pts."); }
                case SCORE_2    -> { cfg.setScoreForPlace(2, val); ok(player, "2do lugar: " + val + " pts."); }
                case SCORE_3    -> { cfg.setScoreForPlace(3, val); ok(player, "3er lugar: " + val + " pts."); }
                case SCORE_DEF  -> { cfg.setScoreForPlace(4, val); ok(player, "Defecto: " + val + " pts."); }
                case SCORE_CP   -> { cfg.setScorePerCheckpoint(val); ok(player, "Pts/CP: " + val + "."); }
                default -> {}
            }
            render(player, activeTabs.getOrDefault(player.getUniqueId(), 0));
        });
    }

    private ItemStack buildTab(String name, Material icon, boolean active) {
        return ItemBuilder.of(icon).name(name, active ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY, active ? TextDecoration.BOLD : TextDecoration.ITALIC)
                .lore(active ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY, active ? "▲ Activa" : "Click para cambiar.").build();
    }

    private ItemStack buildLocItem(String name, Material icon, org.bukkit.Location loc, String desc, String hint) {
        boolean configured = loc != null;
        return ItemBuilder.of(icon).name(name, configured ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD).emptyLine()
                .lore(GuiUtil.noItalic(Component.text("Estado: ", NamedTextColor.GRAY).append(configured ? Component.text("✔ " + locStr(loc), NamedTextColor.GREEN) : Component.text("✘ No configurado", NamedTextColor.RED))))
                .emptyLine().lore(NamedTextColor.DARK_GRAY, desc).lore(NamedTextColor.YELLOW, hint).build();
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

    private Component statusIcon(boolean ok) {
        return ok ? Component.text("✔", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)
                : Component.text("✘", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false);
    }

    private String locStr(org.bukkit.Location l) { return String.format("%.0f, %.0f, %.0f", l.getX(), l.getY(), l.getZ()); }
    private void ok(Player p, String msg)  { p.sendMessage(Component.text("✔ " + msg, NamedTextColor.GREEN)); }
    private void err(Player p, String msg) { p.sendMessage(Component.text("✘ " + msg, NamedTextColor.RED)); }
}