package org.falmdev.anieventmanager.minigames.boatracing;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.utils.gui.ItemBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Magic Stick para Boat Racing.
 *
 * Modos:
 *   PADDOCK       → Spawn del paddock
 *   TRACK_A       → Punto A de la región de la pista
 *   TRACK_B       → Punto B de la región de la pista
 *   FINISH_A      → Extremo A de la línea de meta
 *   FINISH_B      → Extremo B de la línea de meta
 *   ADD_SPAWN     → Agrega spawn de parrilla
 *   ADD_CHECKPOINT → Agrega checkpoint (con radio configurable)
 *   ADD_LIGHT     → Registra la Redstone Lamp mirada como luz de largada
 *
 * Uso especial:
 *   - ADD_CHECKPOINT: Shift+Click abre prompt de radio por chat.
 *     Click normal = radio por defecto (4 bloques).
 *   - ADD_LIGHT: requiere mirar una Redstone Lamp.
 */
public class BoatRacingMagicStick implements Listener {

    public enum Mode {
        PADDOCK       ("Paddock Spawn",        NamedTextColor.AQUA),
        TRACK_A       ("Región Pista — A",     NamedTextColor.GREEN),
        TRACK_B       ("Región Pista — B",     NamedTextColor.RED),
        FINISH_A      ("Meta — Extremo A",     NamedTextColor.WHITE),
        FINISH_B      ("Meta — Extremo B",     NamedTextColor.YELLOW),
        ADD_SPAWN     ("+ Spawn de Parrilla",  NamedTextColor.LIGHT_PURPLE),
        ADD_CHECKPOINT("+ Checkpoint",         NamedTextColor.GOLD),
        ADD_LIGHT     ("+ Luz de Largada",     NamedTextColor.AQUA);

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

    private static final String STICK_NAME = "✦ Magic Stick [Boat Racing]";

    private final Anieventmanager plugin;
    private final Map<UUID, Mode>   playerModes   = new HashMap<>();
    // Radio personalizado para el próximo checkpoint (0 = usar default)
    private final Map<UUID, Double> checkpointRadii = new HashMap<>();

    public BoatRacingMagicStick(Anieventmanager plugin) {
        this.plugin = plugin;
    }

    // ── API pública ───────────────────────────────────────────────────────────

    public void giveMagicStick(Player player) {
        if (!playerModes.containsKey(player.getUniqueId())) {
            playerModes.put(player.getUniqueId(), Mode.PADDOCK);
        }
        Mode mode = playerModes.get(player.getUniqueId());
        player.getInventory().setItemInMainHand(buildStick(mode));
        player.sendMessage(Component.text("✦ Magic Stick [BR] — Modo: ", NamedTextColor.GOLD)
                .append(Component.text(mode.getDisplayName(), mode.getColor(), TextDecoration.BOLD)));
        player.sendMessage(Component.text(
                "  Shift+Click para cambiar modo | Click para ejecutar", NamedTextColor.DARK_GRAY));
        sendModeHint(player, mode);
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

        // Shift+Click para ADD_CHECKPOINT → pedir radio por chat
        if (player.isSneaking()) {
            Mode current = playerModes.getOrDefault(player.getUniqueId(), Mode.PADDOCK);
            if (current == Mode.ADD_CHECKPOINT) {
                promptCheckpointRadius(player);
                return;
            }
            cycleMode(player);
            return;
        }

        executeMode(player, event);
    }

    // ── Ejecución de modos ────────────────────────────────────────────────────

    private void executeMode(Player player, PlayerInteractEvent event) {
        Mode mode = playerModes.getOrDefault(player.getUniqueId(), Mode.PADDOCK);
        BoatRacingConfig cfg = plugin.getBoatRacingMiniGame().getConfig();

        switch (mode) {
            case PADDOCK -> {
                cfg.setPaddockSpawn(player.getLocation());
                ok(player, "Paddock seteado en " + locStr(player.getLocation()) + ".");
            }
            case TRACK_A -> {
                cfg.setTrackPointA(player.getLocation());
                ok(player, "Punto A de la pista seteado.");
            }
            case TRACK_B -> {
                cfg.setTrackPointB(player.getLocation());
                ok(player, "Punto B de la pista seteado.");
            }
            case FINISH_A -> {
                cfg.setFinishA(player.getLocation());
                ok(player, "Meta extremo A seteado.");
            }
            case FINISH_B -> {
                cfg.setFinishB(player.getLocation());
                ok(player, "Meta extremo B seteado.");
            }
            case ADD_SPAWN -> {
                cfg.addPlayerSpawn(player.getLocation());
                ok(player, "Spawn de parrilla #" + cfg.getPlayerSpawns().size() + " agregado.");
            }
            case ADD_CHECKPOINT -> {
                double radius = checkpointRadii.getOrDefault(player.getUniqueId(), 4.0);
                cfg.addCheckpoint(player.getLocation(), radius);
                ok(player, "Checkpoint #" + cfg.getCheckpoints().size()
                        + " agregado (radio: " + radius + " bloques).");
            }
            case ADD_LIGHT -> {
                int current = cfg.getLights().size();
                if (current >= 5) {
                    err(player, "Ya hay 5 luces. Usa /em boatracing clearlights para resetear.");
                    return;
                }
                // Necesita apuntar a un bloque
                Block target = player.getTargetBlockExact(10);
                if (target == null || target.getType() != Material.REDSTONE_LAMP) {
                    err(player, "Mira directamente a una Redstone Lamp (máx 10 bloques).");
                    return;
                }
                cfg.addLight(target.getLocation());
                ok(player, "Luz #" + cfg.getLights().size() + "/5 registrada.");
            }
        }

        // Actualizar el stick
        player.getInventory().setItemInMainHand(buildStick(mode));
    }

    // ── Ciclar modo ───────────────────────────────────────────────────────────

    private void cycleMode(Player player) {
        Mode next = playerModes.getOrDefault(player.getUniqueId(), Mode.PADDOCK).next();
        playerModes.put(player.getUniqueId(), next);
        player.getInventory().setItemInMainHand(buildStick(next));
        player.sendMessage(Component.text("✦ Modo: ", NamedTextColor.GOLD)
                .append(Component.text(next.getDisplayName(), next.getColor(), TextDecoration.BOLD)));
        sendModeHint(player, next);
    }

    // ── Prompt de radio para checkpoint ───────────────────────────────────────

    private void promptCheckpointRadius(Player player) {
        player.sendMessage(Component.text(
                "✎ Escribe el radio del checkpoint en bloques (ej: 4.0):", NamedTextColor.YELLOW));
        player.sendMessage(Component.text(
                "  'cancelar' para cancelar. Enter sin valor = radio 4.0.", NamedTextColor.GRAY));
        // El onChat lo captura
        checkpointRadii.put(player.getUniqueId(), -1.0); // -1 = esperando input
    }

    @EventHandler
    public void onChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!checkpointRadii.containsKey(player.getUniqueId())) return;
        if (!checkpointRadii.get(player.getUniqueId()).equals(-1.0)) return;

        event.setCancelled(true);
        String msg = event.getMessage().trim();

        if (msg.equalsIgnoreCase("cancelar")) {
            checkpointRadii.remove(player.getUniqueId());
            player.sendMessage(Component.text("Cancelado. Radio quedó en 4.0.", NamedTextColor.GRAY));
            return;
        }

        double radius;
        try {
            radius = Double.parseDouble(msg);
            if (radius < 1) { player.sendMessage(Component.text("Radio mínimo: 1 bloque.", NamedTextColor.RED)); return; }
            if (radius > 20) { player.sendMessage(Component.text("Radio máximo: 20 bloques.", NamedTextColor.RED)); return; }
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Número inválido. Radio quedó en 4.0.", NamedTextColor.RED));
            checkpointRadii.remove(player.getUniqueId());
            return;
        }

        checkpointRadii.put(player.getUniqueId(), radius);
        player.sendMessage(Component.text("✔ Radio seteado a " + radius + " bloques.", NamedTextColor.GREEN));
        player.sendMessage(Component.text(
                "  Ahora haz click derecho en el mundo para marcar el checkpoint.", NamedTextColor.GRAY));

        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            ItemStack current = player.getInventory().getItemInMainHand();
            if (isMagicStick(current)) {
                player.getInventory().setItemInMainHand(buildStick(Mode.ADD_CHECKPOINT));
            }
        });
    }

    // ── Construcción del item ─────────────────────────────────────────────────

    private ItemStack buildStick(Mode mode) {
        Double cpRadius = checkpointRadii.get(null); // placeholder

        ItemBuilder b = ItemBuilder.of(Material.BLAZE_ROD)
                .name(STICK_NAME, NamedTextColor.GOLD, TextDecoration.BOLD)
                .emptyLine()
                .lore(Component.text("Modo activo:", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(Component.text("  ▶ " + mode.getDisplayName(), mode.getColor(), TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));

        if (mode == Mode.ADD_CHECKPOINT) {
            b.lore(Component.text("  Radio: " + "4.0" + " bloques", NamedTextColor.GRAY)
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
                        .append(Component.text(" — Cambiar modo (o radio en checkpoint)", NamedTextColor.GRAY))
                        .decoration(TextDecoration.ITALIC, false));

        return b.build();
    }

    private void sendModeHint(Player player, Mode mode) {
        String hint = switch (mode) {
            case PADDOCK       -> "Para en el paddock y haz click.";
            case TRACK_A       -> "Ve a la esquina A de la pista y haz click.";
            case TRACK_B       -> "Ve a la esquina B de la pista y haz click.";
            case FINISH_A      -> "Párate en el extremo A de la meta y haz click.";
            case FINISH_B      -> "Párate en el extremo B de la meta y haz click.";
            case ADD_SPAWN     -> "Ve a cada posición de parrilla y haz click.";
            case ADD_CHECKPOINT-> "Ve a cada checkpoint y haz click. Shift+Click para radio custom.";
            case ADD_LIGHT     -> "Mira una Redstone Lamp y haz click (máx 10 bloques).";
        };
        player.sendMessage(Component.text("  → " + hint, NamedTextColor.DARK_GRAY));
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