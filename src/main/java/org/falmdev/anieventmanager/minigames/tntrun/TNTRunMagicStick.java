package org.falmdev.anieventmanager.minigames.tntrun;

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
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.utils.gui.ItemBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


public class TNTRunMagicStick implements Listener {

    public enum Mode {
        SET_WORLD     ("Setear Mundo",            NamedTextColor.AQUA),
        SET_LOBBY     ("Setear Lobby Spawn",       NamedTextColor.GREEN),
        SET_SPECTATOR ("Setear Spectator Spawn",   NamedTextColor.YELLOW),
        SET_CENTER    ("Setear Centro de Arena",   NamedTextColor.GOLD),
        ADD_SPAWN     ("Agregar Spawn de Jugador", NamedTextColor.LIGHT_PURPLE);

        private final String displayName;
        private final NamedTextColor color;

        Mode(String displayName, NamedTextColor color) {
            this.displayName = displayName;
            this.color       = color;
        }

        public String getDisplayName()   { return displayName; }
        public NamedTextColor getColor() { return color; }

        public Mode next() {
            Mode[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }

    private static final String STICK_NAME = "✦ Magic Stick [TNT Run]";

    private final Anieventmanager plugin;
    private final Map<UUID, Mode> playerModes = new HashMap<>();

    public TNTRunMagicStick(Anieventmanager plugin) {
        this.plugin = plugin;
    }

    public void giveMagicStick(Player player) {
        if (!playerModes.containsKey(player.getUniqueId())) {
            playerModes.put(player.getUniqueId(), Mode.SET_LOBBY);
        }
        Mode mode = playerModes.get(player.getUniqueId());
        ItemStack stick = buildStick(mode);

        player.getInventory().setItemInMainHand(stick);
        player.sendMessage(Component.text("✦ Magic Stick activo — Modo: ", NamedTextColor.GOLD)
                .append(Component.text(mode.getDisplayName(), mode.getColor(), TextDecoration.BOLD)));
        sendModeHint(player, mode);
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

        if (player.isSneaking()
                && (event.getAction() == Action.RIGHT_CLICK_BLOCK
                || event.getAction() == Action.RIGHT_CLICK_AIR)) {
            event.setCancelled(true);
            cycleMode(player);
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                || event.getAction() == Action.RIGHT_CLICK_AIR) {
            event.setCancelled(true);
            executeMode(player);
        }
    }

    @EventHandler
    public void onHeldChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
        if (newItem == null || !isMagicStick(newItem)) return;

        Mode mode = playerModes.getOrDefault(player.getUniqueId(), Mode.SET_LOBBY);
        player.getInventory().setItem(event.getNewSlot(), buildStick(mode));
    }

    private void cycleMode(Player player) {
        Mode current = playerModes.getOrDefault(player.getUniqueId(), Mode.SET_LOBBY);
        Mode next    = current.next();
        playerModes.put(player.getUniqueId(), next);

        player.getInventory().setItemInMainHand(buildStick(next));

        player.sendMessage(Component.text("✦ Modo: ", NamedTextColor.GOLD)
                .append(Component.text(next.getDisplayName(), next.getColor(), TextDecoration.BOLD)));
        sendModeHint(player, next);
    }

    private void executeMode(Player player) {
        Mode mode = playerModes.getOrDefault(player.getUniqueId(), Mode.SET_LOBBY);
        TNTRunConfig cfg = plugin.getTNTRunMiniGame().getConfig();

        switch (mode) {
            case SET_WORLD -> {
                String worldName = player.getWorld().getName();
                cfg.setWorldName(worldName);
                ok(player, "Mundo seteado a '" + worldName + "'.");
            }
            case SET_LOBBY -> {
                cfg.setLobbySpawn(player.getLocation());
                ok(player, "Lobby spawn seteado en " + locStr(player.getLocation()) + ".");
            }
            case SET_SPECTATOR -> {
                cfg.setSpectatorSpawn(player.getLocation());
                ok(player, "Spectator spawn seteado en " + locStr(player.getLocation()) + ".");
            }
            case SET_CENTER -> {
                cfg.setArenaCenter(player.getLocation());
                ok(player, "Centro de arena seteado en " + locStr(player.getLocation()) + ".");
            }
            case ADD_SPAWN -> {
                cfg.addPlayerSpawn(player.getLocation());
                int count = cfg.getPlayerSpawns().size();
                ok(player, "Spawn #" + count + " agregado en " + locStr(player.getLocation()) + ".");
            }
        }

        player.getInventory().setItemInMainHand(buildStick(mode));
    }

    private ItemStack buildStick(Mode mode) {
        Mode[] modes = Mode.values();

        ItemBuilder b = ItemBuilder.of(Material.BLAZE_ROD)
                .name(STICK_NAME, NamedTextColor.GOLD, TextDecoration.BOLD)
                .emptyLine()
                .lore(Component.text("Modo activo:", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(Component.text("  ▶ " + mode.getDisplayName(), mode.getColor(), TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false))
                .emptyLine()
                .lore(Component.text("Modos disponibles:", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));

        for (Mode m : modes) {
            boolean active = m == mode;
            b.lore(Component.text(
                            (active ? "  ▶ " : "  ○ ") + m.getDisplayName(),
                            active ? m.getColor() : NamedTextColor.DARK_GRAY
                    ).decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, active));
        }

        b.emptyLine()
                .lore(Component.text("Click derecho", NamedTextColor.YELLOW)
                        .append(Component.text(" — Ejecutar modo", NamedTextColor.GRAY))
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

    private void sendModeHint(Player player, Mode mode) {
        String hint = switch (mode) {
            case SET_WORLD     -> "Haz click derecho para setear el mundo actual.";
            case SET_LOBBY     -> "Para en la posición del lobby y haz click derecho.";
            case SET_SPECTATOR -> "Para en el spawn de espectadores y haz click derecho.";
            case SET_CENTER    -> "Para en el centro de la arena y haz click derecho.";
            case ADD_SPAWN     -> "Para donde quieres el spawn y haz click derecho.";
        };
        player.sendMessage(Component.text("  → " + hint, NamedTextColor.DARK_GRAY));
        player.sendMessage(Component.text("  → Shift+Click para cambiar de modo.", NamedTextColor.DARK_GRAY));
    }

    private String locStr(org.bukkit.Location l) {
        return String.format("%.0f, %.0f, %.0f", l.getX(), l.getY(), l.getZ());
    }

    private void ok(Player p, String msg) {
        p.sendMessage(Component.text("✔ " + msg, NamedTextColor.GREEN));
    }
}