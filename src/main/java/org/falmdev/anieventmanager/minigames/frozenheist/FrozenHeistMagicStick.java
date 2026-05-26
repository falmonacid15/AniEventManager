package org.falmdev.anieventmanager.minigames.frozenheist;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.model.EventTeam;
import org.falmdev.anieventmanager.utils.gui.ItemBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Magic Stick para Frozen Heist.
 *
 * A diferencia del TNTRun, aquí cada operación es POR EQUIPO.
 * El stick guarda el equipo activo y el modo activo.
 *
 * Modos:
 *   SET_GLOBAL_SPAWN → Spawn global pre-partida
 *   SET_BASE_SPAWN   → Spawn de respawn del equipo activo
 *   SET_CAPTURE      → Zona de captura del equipo activo
 *   SET_FLAG_STAND   → Posición de la bandera del equipo activo
 *   SET_CORNER1      → Esquina 1 de la base del equipo activo
 *   SET_CORNER2      → Esquina 2 de la base del equipo activo
 *
 * El equipo activo se cambia con: /em frozenheist stick <teamId>
 * o se hereda al abrir la GUI de un equipo específico.
 */
public class FrozenHeistMagicStick implements Listener {

    public enum Mode {
        SET_GLOBAL_SPAWN("Spawn Global",           NamedTextColor.WHITE),
        SET_BASE_SPAWN  ("Base Spawn [equipo]",    NamedTextColor.GREEN),
        SET_CAPTURE     ("Capture Zone [equipo]",  NamedTextColor.GOLD),
        SET_FLAG_STAND  ("Flag Stand [equipo]",    NamedTextColor.RED),
        SET_CORNER1     ("Base Corner 1 [equipo]", NamedTextColor.AQUA),
        SET_CORNER2     ("Base Corner 2 [equipo]", NamedTextColor.LIGHT_PURPLE);

        private final String displayName;
        private final NamedTextColor color;

        Mode(String displayName, NamedTextColor color) {
            this.displayName = displayName;
            this.color       = color;
        }

        public String getDisplayName()   { return displayName; }
        public NamedTextColor getColor() { return color; }

        public Mode next() {
            Mode[] v = values();
            return v[(this.ordinal() + 1) % v.length];
        }
    }

    private static final String STICK_NAME = "✦ Magic Stick [Frozen Heist]";

    private final Anieventmanager plugin;
    private final Map<UUID, Mode>      playerModes = new HashMap<>();
    private final Map<UUID, EventTeam> playerTeams = new HashMap<>(); // equipo activo por jugador

    public FrozenHeistMagicStick(Anieventmanager plugin) {
        this.plugin = plugin;
    }

    // ── API pública ───────────────────────────────────────────────────────────

    /**
     * Da el Magic Stick al jugador.
     * @param team Equipo activo a preseleccionar (puede ser null → sin equipo activo)
     */
    public void giveMagicStick(Player player, EventTeam team) {
        if (!playerModes.containsKey(player.getUniqueId())) {
            playerModes.put(player.getUniqueId(), Mode.SET_GLOBAL_SPAWN);
        }
        if (team != null) {
            playerTeams.put(player.getUniqueId(), team);
        }

        Mode mode = playerModes.get(player.getUniqueId());
        player.getInventory().setItemInMainHand(buildStick(mode, playerTeams.get(player.getUniqueId())));
        player.sendMessage(Component.text("✦ Magic Stick [FH] — Modo: ", NamedTextColor.GOLD)
                .append(Component.text(mode.getDisplayName(), mode.getColor(), TextDecoration.BOLD)));
        if (team != null) {
            player.sendMessage(Component.text("  Equipo activo: ", NamedTextColor.GRAY)
                    .append(Component.text(team.getDisplayName(), team.getColor())));
        }
        player.sendMessage(Component.text(
                "  Shift+Click para ciclar modo. Click para ejecutar.", NamedTextColor.DARK_GRAY));
    }

    public Mode getMode(Player player) {
        return playerModes.get(player.getUniqueId());
    }

    // ── Listener ──────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isMagicStick(item)) return;

        boolean isRight = event.getAction() == Action.RIGHT_CLICK_BLOCK
                || event.getAction() == Action.RIGHT_CLICK_AIR;
        if (!isRight) return;
        event.setCancelled(true);

        if (player.isSneaking()) {
            cycleMode(player);
            return;
        }

        executeMode(player);
    }

    // ── Lógica ────────────────────────────────────────────────────────────────

    private void cycleMode(Player player) {
        Mode next = playerModes.getOrDefault(player.getUniqueId(), Mode.SET_GLOBAL_SPAWN).next();
        playerModes.put(player.getUniqueId(), next);
        player.getInventory().setItemInMainHand(buildStick(next, playerTeams.get(player.getUniqueId())));
        player.sendMessage(Component.text("✦ Modo: ", NamedTextColor.GOLD)
                .append(Component.text(next.getDisplayName(), next.getColor(), TextDecoration.BOLD)));
    }

    private void executeMode(Player player) {
        Mode mode = playerModes.getOrDefault(player.getUniqueId(), Mode.SET_GLOBAL_SPAWN);
        EventTeam team = playerTeams.get(player.getUniqueId());
        FrozenHeistConfig cfg = plugin.getFrozenHeistMiniGame().getConfig();

        if (mode != Mode.SET_GLOBAL_SPAWN && team == null) {
            err(player, "No hay equipo activo. Abre la GUI de un equipo específico para preseleccionarlo.");
            err(player, "O cambia al modo 'Spawn Global' con Shift+Click.");
            return;
        }

        switch (mode) {
            case SET_GLOBAL_SPAWN -> {
                cfg.setGlobalSpawn(player.getLocation());
                ok(player, "Spawn global seteado en " + locStr(player.getLocation()) + ".");
            }
            case SET_BASE_SPAWN -> {
                cfg.setBaseSpawn(team.getId(), player.getLocation());
                ok(player, "[" + team.getDisplayName() + "] Base spawn seteado.");
            }
            case SET_CAPTURE -> {
                cfg.setCaptureZone(team.getId(), player.getLocation());
                ok(player, "[" + team.getDisplayName() + "] Capture zone seteada.");
            }
            case SET_FLAG_STAND -> {
                cfg.setFlagStand(team.getId(), player.getLocation());
                ok(player, "[" + team.getDisplayName() + "] Flag stand seteado.");
            }
            case SET_CORNER1 -> {
                cfg.setBaseCorner1(team.getId(), player.getLocation());
                ok(player, "[" + team.getDisplayName() + "] Corner 1 seteado.");
            }
            case SET_CORNER2 -> {
                cfg.setBaseCorner2(team.getId(), player.getLocation());
                ok(player, "[" + team.getDisplayName() + "] Corner 2 seteado.");
            }
        }

        // Actualizar stick para reflejar cambios
        player.getInventory().setItemInMainHand(buildStick(mode, team));
    }

    // ── Construcción del item ─────────────────────────────────────────────────

    private ItemStack buildStick(Mode mode, EventTeam activeTeam) {
        String teamLabel = activeTeam != null
                ? activeTeam.getDisplayName()
                : "ninguno";

        ItemBuilder b = ItemBuilder.of(Material.BLAZE_ROD)
                .name(STICK_NAME, NamedTextColor.GOLD, TextDecoration.BOLD)
                .emptyLine()
                .lore(Component.text("Equipo activo: ", NamedTextColor.GRAY)
                        .append(activeTeam != null
                                ? Component.text(teamLabel, activeTeam.getColor(), TextDecoration.BOLD)
                                : Component.text(teamLabel, NamedTextColor.DARK_GRAY))
                        .decoration(TextDecoration.ITALIC, false))
                .emptyLine()
                .lore(Component.text("Modo activo:", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(Component.text("  ▶ " + mode.getDisplayName(), mode.getColor(), TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false))
                .emptyLine();

        for (Mode m : Mode.values()) {
            boolean active = m == mode;
            b.lore(Component.text(
                    (active ? "  ▶ " : "  ○ ") + m.getDisplayName(),
                    active ? m.getColor() : NamedTextColor.DARK_GRAY
            ).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, active));
        }

        b.emptyLine()
                .lore(Component.text("Click derecho", NamedTextColor.YELLOW)
                        .append(Component.text(" — Ejecutar", NamedTextColor.GRAY))
                        .decoration(TextDecoration.ITALIC, false))
                .lore(Component.text("Shift + Click", NamedTextColor.YELLOW)
                        .append(Component.text(" — Cambiar modo", NamedTextColor.GRAY))
                        .decoration(TextDecoration.ITALIC, false));

        return b.build();
    }

    public static boolean isMagicStick(ItemStack item) {
        if (item == null || item.getType() != Material.BLAZE_ROD) return false;
        if (!item.hasItemMeta() || item.getItemMeta().displayName() == null) return false;
        String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(item.getItemMeta().displayName());
        return name.equals(STICK_NAME);
    }

    private String locStr(org.bukkit.Location l) {
        return String.format("%.0f, %.0f, %.0f", l.getX(), l.getY(), l.getZ());
    }

    private void ok(Player p, String msg)  { p.sendMessage(Component.text("✔ " + msg, NamedTextColor.GREEN)); }
    private void err(Player p, String msg) { p.sendMessage(Component.text("✘ " + msg, NamedTextColor.RED)); }
}