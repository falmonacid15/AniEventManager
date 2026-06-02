package org.falmdev.anieventmanager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.falmdev.anieventmanager.utils.gui.GuiUtil;
import org.falmdev.anieventmanager.utils.gui.ItemBuilder;

public class EventManagerGUI implements Listener {

    public static final String TITLE        = "✦ AniEvent Manager";
    public static final String TITLE_SCORES = "Puntajes del Evento";

    private static final int SLOT_TEAMS      = 10;
    private static final int SLOT_SCORES     = 11;
    private static final int SLOT_CINEMATICS = 13;
    private static final int SLOT_TNTRUN      = 28;
    private static final int SLOT_BINGO       = 29;
    private static final int SLOT_FROZENHEIST = 30;
    private static final int SLOT_PARKOURDUOS = 31;
    private static final int SLOT_BOATRACING  = 32;

    private static final String SKULL_TEAMS =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZn" +
                    "QubmV0L3RleHR1cmUvYTc5YTVjOTVlZTE3YWJmZWY0NWM4ZGM3NTJhN2YyZWQyZjZlNWVj" +
                    "NmY3ZjZhNzFiNGQ5NmM2MzBiMzgxNCJ9fX0=";

    private static final String SKULL_SCORES =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZn" +
                    "QubmV0L3RleHR1cmUvZGM0ZTQ0MWVhYzg4NGRlMzM0N2E4Nzc1YTA3YTY2YmJjNGM4MmEy" +
                    "NGVkMmQwY2ZlYjFhY2FmNmNlOTlkNTNiNiJ9fX0=";

    private static final String SKULL_CINEMATICS =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZn" +
                    "QubmV0L3RleHR1cmUvYjczNGQ0NjYzMzI5ZmVlODMzZGY0MGFiY2JmZGNhNDM4OTA4OGYw" +
                    "OGY0MGJlYTg4OTc2NjVkMmI0ZDNiZiJ9fX0=";

    private final Anieventmanager plugin;

    public EventManagerGUI(Anieventmanager plugin) {
        this.plugin = plugin;
    }

    // ── Abrir ─────────────────────────────────────────────────────────────────

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text(TITLE, NamedTextColor.GOLD));

        GuiUtil.fillAll(inv);

        // Etiquetas de sección (decorativas)
        inv.setItem(0,  buildSectionLabel("Gestión del Evento", Material.BOOK));
        inv.setItem(18, buildSectionLabel("Minijuegos",         Material.COMPASS));

        // ── Gestión general ───────────────────────────────────────────────────
        inv.setItem(SLOT_TEAMS,      buildTeamsItem());
        inv.setItem(SLOT_SCORES,     buildScoresItem());
        inv.setItem(SLOT_CINEMATICS, buildCinematicsItem());

        // ── Minijuegos ────────────────────────────────────────────────────────
        inv.setItem(SLOT_TNTRUN,      buildMiniGameItem("TNT Run",       Material.TNT,
                plugin.getTNTRunMiniGame()));
        inv.setItem(SLOT_BINGO,       buildMiniGameItem("Bingo",         Material.PAPER,
                plugin.getBingoMiniGame()));
        inv.setItem(SLOT_FROZENHEIST, buildMiniGameItem("Frozen Heist",  Material.BLUE_ICE,
                plugin.getFrozenHeistMiniGame()));
        inv.setItem(SLOT_PARKOURDUOS, buildMiniGameItem("Parkour Duos",  Material.IRON_BOOTS,
                plugin.getParkourDuosMiniGame()));
        inv.setItem(SLOT_BOATRACING,  buildMiniGameItem("Boat Racing",   Material.OAK_BOAT,
                plugin.getBoatRacingMiniGame()));

        // ── Navegación: pantalla raíz → sin back ni home ──────────────────────
        GuiUtil.fillNavigationNone(inv);

        player.openInventory(inv);
    }

    // ── Scores ────────────────────────────────────────────────────────────────

    private void openScores(Player player) {
        // Siempre 54 slots para que la navegación sea consistente
        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text(TITLE_SCORES, NamedTextColor.GOLD));
        GuiUtil.fillAll(inv);

        var lb = plugin.getScoreManager().getLeaderboard();
        int slot = 10;
        int pos  = 1;
        for (var entry : lb) {
            if (slot >= 44) break; // dejar fila 5 para navegación
            var teamOpt = plugin.getTeamManager().getTeam(entry.getKey());
            String name     = teamOpt.map(t -> t.getDisplayName()).orElse(entry.getKey());
            NamedTextColor color = teamOpt.map(t -> t.getColor()).orElse(NamedTextColor.WHITE);
            Material mat    = teamOpt.map(t ->
                            org.falmdev.anieventmanager.utils.TeamUtil.colorToBannerMaterial(t.getColor()))
                    .orElse(Material.WHITE_BANNER);

            String medal = switch (pos) {
                case 1 -> "🥇 #1"; case 2 -> "🥈 #2"; case 3 -> "🥉 #3"; default -> "#" + pos;
            };

            inv.setItem(slot, ItemBuilder.of(mat)
                    .name(medal + " " + name, color, TextDecoration.BOLD)
                    .emptyLine()
                    .lore(GuiUtil.label("Puntos",
                            Component.text(entry.getValue() + " pts", NamedTextColor.YELLOW)))
                    .build());

            slot++;
            if (slot % 9 == 8) slot += 2; // saltar bordes laterales
            pos++;
        }

        if (lb.isEmpty()) {
            inv.setItem(22, ItemBuilder.of(Material.BARRIER)
                    .name("Sin puntajes registrados", NamedTextColor.GRAY).build());
        }

        // Scores es sub-vista del hub → solo botón Volver (48), sin Home (no tiene sentido
        // poner Inicio cuando ya estás a un click del hub)
        GuiUtil.fillNavigation(inv, true, false);
        // Slot 48 = ← Volver al hub (lo pone fillNavigation automáticamente)

        player.openInventory(inv);
    }

    // ── Items ─────────────────────────────────────────────────────────────────

    private ItemStack buildTeamsItem() {
        int teamCount    = plugin.getTeamManager().getTeamCount();
        int totalPlayers = plugin.getTeamManager().getAllTeams().stream()
                .mapToInt(t -> t.getMemberCount()).sum();

        return ItemBuilder.fromString("head-base64-" + SKULL_TEAMS)
                .name("Equipos", NamedTextColor.AQUA, TextDecoration.BOLD)
                .emptyLine()
                .lore(GuiUtil.label("Equipos creados",   Component.text(teamCount,    NamedTextColor.WHITE)))
                .lore(GuiUtil.label("Jugadores asignados", Component.text(totalPlayers, NamedTextColor.WHITE)))
                .emptyLine()
                .lore(NamedTextColor.YELLOW, "Click para administrar equipos.")
                .build();
    }

    private ItemStack buildScoresItem() {
        var lb = plugin.getScoreManager().getLeaderboard();
        String leader = lb.isEmpty() ? "—" :
                plugin.getTeamManager().getTeam(lb.get(0).getKey())
                .map(t -> t.getDisplayName() + " (" + lb.get(0).getValue() + " pts)")
                .orElse("—");

        return ItemBuilder.fromString("head-base64-" + SKULL_SCORES)
                .name("Puntajes", NamedTextColor.GOLD, TextDecoration.BOLD)
                .emptyLine()
                .lore(GuiUtil.label("Líder",        Component.text(leader, NamedTextColor.YELLOW)))
                .lore(GuiUtil.label("Puntaje máx.", Component.text(plugin.getScoreManager().getTopScore() + " pts", NamedTextColor.WHITE)))
                .emptyLine()
                .lore(NamedTextColor.YELLOW, "Click para ver tabla de puntajes.")
                .build();
    }

    private ItemStack buildCinematicsItem() {
        int count = plugin.getCinematicManager().getIds().size();
        boolean anyPlaying = plugin.getCinematicManager().getAllCinematics().stream()
                .anyMatch(c -> c.getState() ==
                        org.falmdev.anieventmanager.cinematics.model.CinematicState.PLAYING);

        return ItemBuilder.fromString("head-base64-" + SKULL_CINEMATICS)
                .name("Cinematicas", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .emptyLine()
                .lore(GuiUtil.label("Registradas", Component.text(count, NamedTextColor.WHITE)))
                .lore(GuiUtil.noItalic(Component.text("Estado: ", NamedTextColor.GRAY)
                        .append(anyPlaying
                                ? Component.text("▶ Reproduciendo", NamedTextColor.GREEN)
                                : Component.text("■ Inactiva",       NamedTextColor.DARK_GRAY))))
                .emptyLine()
                .lore(NamedTextColor.YELLOW, "Click para administrar cinematicas.")
                .build();
    }

    private ItemStack buildMiniGameItem(String name, Material icon,
                                        org.falmdev.anieventmanager.managers.MiniGame mg) {
        boolean running    = mg.isRunning();
        boolean configured = mg.validateConfig() == null;
        NamedTextColor stateColor = running ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY;
        String stateLabel = (running ? "▶ " : "■ ") + mg.getStateName();

        return ItemBuilder.of(icon)
                .name(name, NamedTextColor.WHITE, TextDecoration.BOLD)
                .emptyLine()
                .lore(GuiUtil.noItalic(Component.text("Estado: ", NamedTextColor.GRAY)
                        .append(Component.text(stateLabel, stateColor))))
                .lore(GuiUtil.noItalic(Component.text("Config: ", NamedTextColor.GRAY)
                        .append(configured
                                ? Component.text("✔ Completa",   NamedTextColor.GREEN)
                                : Component.text("✘ Incompleta", NamedTextColor.RED))))
                .emptyLine()
                .lore(NamedTextColor.YELLOW, "Click para configurar.")
                .build();
    }

    private ItemStack buildSectionLabel(String text, Material icon) {
        return ItemBuilder.of(icon)
                .name(text, NamedTextColor.GOLD, TextDecoration.BOLD)
                .build();
    }

    // ── Click listener ────────────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = GuiUtil.getTitle(event.getView());
        event.setCancelled(true);
        if (!player.isOp()) return;

        // ── Hub principal ─────────────────────────────────────────────────────
        if (title.equals(TITLE)) {
            switch (event.getRawSlot()) {
                case SLOT_TEAMS       -> plugin.getTeamAdminGUI().openList(player);
                case SLOT_SCORES      -> openScores(player);
                case SLOT_CINEMATICS  -> plugin.getCinematicAdminGUI().openList(player);
                case SLOT_TNTRUN      -> plugin.getTNTRunAdminGUI().open(player);
                case SLOT_BINGO       -> plugin.getBingoMiniGame().openAdminGUI(player);
                case SLOT_FROZENHEIST -> plugin.getFrozenHeistMiniGame().openAdminGUI(player);
                case SLOT_PARKOURDUOS -> plugin.getParkourDuosMiniGame().openAdminGUI(player);
                case SLOT_BOATRACING  -> plugin.getBoatRacingMiniGame().openAdminGUI(player);
            }
            return;
        }

        // ── Vista de Scores ───────────────────────────────────────────────────
        if (title.equals(TITLE_SCORES)) {
            // Slot 48 = ← Volver al hub
            if (event.getRawSlot() == GuiUtil.NAV_BACK) open(player);
        }
    }
}