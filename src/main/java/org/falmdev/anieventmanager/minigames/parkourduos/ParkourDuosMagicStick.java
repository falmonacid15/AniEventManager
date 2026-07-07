package org.falmdev.anieventmanager.minigames.parkourduos;

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

public class ParkourDuosMagicStick implements Listener {

    public enum Mode {
        SET_LOBBY     ("Lobby General",          NamedTextColor.WHITE),
        SET_SPAWN1    ("Spawn 1 [equipo]",        NamedTextColor.GREEN),
        SET_SPAWN2    ("Spawn 2 [equipo]",        NamedTextColor.AQUA),
        SET_START     ("Start [equipo]",          NamedTextColor.YELLOW),
        SET_FINISH    ("Finish [equipo]",         NamedTextColor.GOLD),
        ADD_CHECKPOINT("+ Checkpoint [equipo]",   NamedTextColor.LIGHT_PURPLE);

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

    private static final String STICK_NAME = "✦ Magic Stick [Parkour Duos]";

    private final Anieventmanager plugin;
    private final Map<UUID, Mode>      playerModes    = new HashMap<>();
    private final Map<UUID, EventTeam> playerTeams    = new HashMap<>();
    private final Map<UUID, Double>    cpRadii        = new HashMap<>();  // radio próximo checkpoint
    private final Map<UUID, Boolean>   awaitingRadius = new HashMap<>();  // esperando radio en chat

    public ParkourDuosMagicStick(Anieventmanager plugin) {
        this.plugin = plugin;
    }

    public void giveMagicStick(Player player, EventTeam team) {
        if (!playerModes.containsKey(player.getUniqueId())) {
            playerModes.put(player.getUniqueId(), Mode.SET_LOBBY);
        }
        if (team != null) playerTeams.put(player.getUniqueId(), team);

        Mode mode = playerModes.get(player.getUniqueId());
        player.getInventory().setItemInMainHand(buildStick(mode, playerTeams.get(player.getUniqueId())));
        player.sendMessage(Component.text("✦ Magic Stick [PD] — Modo: ", NamedTextColor.GOLD)
                .append(Component.text(mode.getDisplayName(), mode.getColor(), TextDecoration.BOLD)));
        if (team != null) {
            player.sendMessage(Component.text("  Equipo activo: ", NamedTextColor.GRAY)
                    .append(Component.text(team.getDisplayName(), team.getColor())));
        }
        player.sendMessage(Component.text(
                "  Click para ejecutar | Shift+Click para ciclar modo", NamedTextColor.DARK_GRAY));
        if (mode == Mode.ADD_CHECKPOINT) {
            player.sendMessage(Component.text(
                    "  Shift+Click en modo CP para cambiar el radio.", NamedTextColor.DARK_GRAY));
        }
    }

    public Mode getMode(Player player) {
        return playerModes.get(player.getUniqueId());
    }

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

        Mode mode = playerModes.getOrDefault(player.getUniqueId(), Mode.SET_LOBBY);

        if (player.isSneaking()) {
            // Shift+Click en ADD_CHECKPOINT → cambiar radio
            if (mode == Mode.ADD_CHECKPOINT) {
                promptRadius(player);
            } else {
                cycleMode(player);
            }
            return;
        }

        executeMode(player, mode);
    }

    private void executeMode(Player player, Mode mode) {
        ParkourDuosConfig cfg = plugin.getParkourDuosMiniGame().getConfig();
        EventTeam team = playerTeams.get(player.getUniqueId());

        if (mode != Mode.SET_LOBBY && team == null) {
            err(player, "No hay equipo activo. Abre la GUI de un equipo para preseleccionarlo.");
            return;
        }

        switch (mode) {
            case SET_LOBBY -> {
                cfg.setLobby(player.getLocation());
                ok(player, "Lobby seteado en " + locStr(player.getLocation()) + ".");
            }
            case SET_SPAWN1 -> {
                cfg.setTeamSpawn1(team.getId(), player.getLocation());
                ok(player, "[" + team.getDisplayName() + "] Spawn 1 seteado.");
            }
            case SET_SPAWN2 -> {
                cfg.setTeamSpawn2(team.getId(), player.getLocation());
                ok(player, "[" + team.getDisplayName() + "] Spawn 2 seteado.");
            }
            case SET_START -> {
                cfg.setTeamStart(team.getId(), player.getLocation());
                ok(player, "[" + team.getDisplayName() + "] Start seteado.");
            }
            case SET_FINISH -> {
                cfg.setTeamFinish(team.getId(), player.getLocation());
                ok(player, "[" + team.getDisplayName() + "] Finish seteado.");
            }
            case ADD_CHECKPOINT -> {
                double radius = cpRadii.getOrDefault(player.getUniqueId(), 3.0);
                cfg.addCheckpoint(team.getId(), player.getLocation(), radius);
                int count = cfg.getCheckpointCount(team.getId());
                ok(player, "[" + team.getDisplayName() + "] CP #" + count
                        + " agregado (radio: " + radius + " bloques).");
            }
        }

        player.getInventory().setItemInMainHand(buildStick(mode, team));
    }

    private void cycleMode(Player player) {
        Mode next = playerModes.getOrDefault(player.getUniqueId(), Mode.SET_LOBBY).next();
        playerModes.put(player.getUniqueId(), next);
        EventTeam team = playerTeams.get(player.getUniqueId());
        player.getInventory().setItemInMainHand(buildStick(next, team));
        player.sendMessage(Component.text("✦ Modo: ", NamedTextColor.GOLD)
                .append(Component.text(next.getDisplayName(), next.getColor(), TextDecoration.BOLD)));
        if (next == Mode.ADD_CHECKPOINT) {
            player.sendMessage(Component.text(
                    "  Radio actual: " + cpRadii.getOrDefault(player.getUniqueId(), 3.0) + " bloques  |  Shift+Click para cambiar.",
                    NamedTextColor.DARK_GRAY));
        }
    }

    private void promptRadius(Player player) {
        awaitingRadius.put(player.getUniqueId(), true);
        player.sendMessage(Component.text("✎ Escribe el radio del checkpoint en bloques (ej: 3.0):",
                NamedTextColor.YELLOW));
        player.sendMessage(Component.text("  'cancelar' para cancelar.", NamedTextColor.GRAY));
    }

    @EventHandler
    public void onChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!Boolean.TRUE.equals(awaitingRadius.get(player.getUniqueId()))) return;

        event.setCancelled(true);
        String msg = event.getMessage().trim();

        if (msg.equalsIgnoreCase("cancelar")) {
            awaitingRadius.remove(player.getUniqueId());
            player.sendMessage(Component.text("Cancelado.", NamedTextColor.GRAY));
            return;
        }

        try {
            double radius = Double.parseDouble(msg);
            if (radius < 1)  { player.sendMessage(Component.text("Mínimo 1 bloque.", NamedTextColor.RED)); return; }
            if (radius > 20) { player.sendMessage(Component.text("Máximo 20 bloques.", NamedTextColor.RED)); return; }
            cpRadii.put(player.getUniqueId(), radius);
            awaitingRadius.remove(player.getUniqueId());
            player.sendMessage(Component.text("✔ Radio de checkpoint: " + radius + " bloques.", NamedTextColor.GREEN));
            player.sendMessage(Component.text("  Haz click derecho para agregar el checkpoint.", NamedTextColor.GRAY));
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                EventTeam team = playerTeams.get(player.getUniqueId());
                player.getInventory().setItemInMainHand(buildStick(Mode.ADD_CHECKPOINT, team));
            });
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Número inválido.", NamedTextColor.RED));
        }
    }

    private ItemStack buildStick(Mode mode, EventTeam activeTeam) {
        ItemBuilder b = ItemBuilder.of(Material.BLAZE_ROD)
                .name(STICK_NAME, NamedTextColor.GOLD, TextDecoration.BOLD)
                .emptyLine()
                .lore(Component.text("Equipo activo: ", NamedTextColor.GRAY)
                        .append(activeTeam != null
                                ? Component.text(activeTeam.getDisplayName(), activeTeam.getColor(), TextDecoration.BOLD)
                                : Component.text("ninguno", NamedTextColor.DARK_GRAY))
                        .decoration(TextDecoration.ITALIC, false))
                .emptyLine()
                .lore(Component.text("Modo activo:", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(Component.text("  ▶ " + mode.getDisplayName(), mode.getColor(), TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));

        if (mode == Mode.ADD_CHECKPOINT) {
            double r = cpRadii.getOrDefault(activeTeam != null ? activeTeam.getMembers().stream().findFirst().orElse(null) : null, 3.0);
            b.lore(Component.text("  Radio: 3.0 bloques", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false))
                    .lore(Component.text("  Shift+Click para cambiar radio", NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false));
        }

        b.emptyLine();
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
                        .append(Component.text(" — Cambiar modo (o radio en CP)", NamedTextColor.GRAY))
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