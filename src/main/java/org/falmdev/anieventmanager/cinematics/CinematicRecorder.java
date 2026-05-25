package org.falmdev.anieventmanager.cinematics;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.cinematics.model.Cinematic;
import org.falmdev.anieventmanager.cinematics.model.CinematicState;
import org.falmdev.anieventmanager.cinematics.model.CinematicWaypoint;
import org.falmdev.anieventmanager.utils.gui.ItemBuilder;

import java.util.UUID;

/**
 * Gestiona la sesión de grabación de una cinematica.
 *
 * Un admin recibe el Magic Stick e interactúa:
 *  - Click derecho en el aire → graba waypoint en la posición actual
 *  - Click derecho en bloque  → graba waypoint + abre editor de título
 *  - Shift+click derecho      → termina la grabación y guarda
 *
 * Los tickOffsets se asignan de forma incremental: el primero en tick 0,
 * cada siguiente en el tick del anterior + {@link #DEFAULT_TICK_GAP}.
 * El admin puede ajustar los tickOffsets desde la GUI después.
 */
public class CinematicRecorder {

    /** Ticks entre waypoints consecutivos al grabar (2 segundos). Configurable desde GUI. */
    public static final int DEFAULT_TICK_GAP = 40;

    public static final String MAGIC_STICK_NAME = "✦ Magic Stick";

    private final Anieventmanager plugin;

    private UUID      recordingAdmin    = null;
    private Cinematic recordingCinematic = null;
    private int       nextTickOffset    = 0;
    private int       tickGap          = DEFAULT_TICK_GAP;

    public CinematicRecorder(Anieventmanager plugin) {
        this.plugin = plugin;
    }

    // ── API pública ───────────────────────────────────────────────────────────

    public boolean isRecording() { return recordingAdmin != null; }

    public boolean isRecordingAdmin(Player p) {
        return p != null && p.getUniqueId().equals(recordingAdmin);
    }

    public Cinematic getRecordingCinematic() { return recordingCinematic; }

    /**
     * Inicia la grabación de una cinematica para el admin dado.
     * Le da el magic stick y pone la cinematica en estado RECORDING.
     */
    public boolean startRecording(Player admin, Cinematic cinematic) {
        if (isRecording()) return false;
        if (!cinematic.isIdle()) return false;

        recordingAdmin     = admin.getUniqueId();
        recordingCinematic = cinematic;
        nextTickOffset     = cinematic.getWaypoints().isEmpty() ? 0
                : cinematic.getWaypoints().get(cinematic.getWaypoints().size() - 1).getTickOffset() + tickGap;
        tickGap = DEFAULT_TICK_GAP;

        cinematic.setState(CinematicState.RECORDING);
        giveMagicStick(admin);

        admin.sendMessage(Component.text("▶ Grabando cinematica ", NamedTextColor.GREEN)
                .append(Component.text(cinematic.getDisplayName(), NamedTextColor.YELLOW))
                .append(Component.text(".", NamedTextColor.GREEN)));
        admin.sendMessage(Component.text("  Click derecho en el aire → waypoint sin texto.", NamedTextColor.GRAY));
        admin.sendMessage(Component.text("  Click derecho en bloque  → waypoint + editor de texto.", NamedTextColor.GRAY));
        admin.sendMessage(Component.text("  Shift+click derecho      → terminar y guardar.", NamedTextColor.GRAY));
        return true;
    }

    /**
     * Agrega un waypoint en la posición actual del admin.
     * @param openTextEditor si es true, después de grabar se debe abrir el editor de texto
     * @return el waypoint creado, o null si no hay sesión activa
     */
    public CinematicWaypoint addWaypoint(Player admin, boolean openTextEditor) {
        if (!isRecordingAdmin(admin)) return null;

        CinematicWaypoint wp = new CinematicWaypoint(admin.getLocation(), nextTickOffset);
        recordingCinematic.addWaypoint(wp);
        nextTickOffset += tickGap;

        admin.sendMessage(Component.text("✔ Waypoint #" + (recordingCinematic.getWaypoints().size())
                + " guardado en tick " + wp.getTickOffset() + ".", NamedTextColor.GREEN));

        if (!openTextEditor) return wp;
        // El CinematicListener abre la GUI de texto después de devolver el waypoint
        return wp;
    }

    /**
     * Termina la grabación, guarda y restaura el estado.
     */
    public void stopRecording() {
        if (recordingCinematic != null) {
            recordingCinematic.save();
            recordingCinematic.setState(CinematicState.IDLE);

            Player admin = recordingAdmin != null ? plugin.getServer().getPlayer(recordingAdmin) : null;
            if (admin != null && admin.isOnline()) {
                removeMagicStick(admin);
                admin.sendMessage(Component.text("■ Grabación terminada. Waypoints: "
                        + recordingCinematic.getWaypoints().size(), NamedTextColor.YELLOW));
            }
        }
        recordingAdmin     = null;
        recordingCinematic = null;
        nextTickOffset     = 0;
    }

    public int  getTickGap()             { return tickGap; }
    public void setTickGap(int tickGap)  { this.tickGap = Math.max(1, tickGap); }

    // ── Magic Stick ───────────────────────────────────────────────────────────

    /** Construye el item Magic Stick. */
    public static ItemStack buildMagicStick() {
        return ItemBuilder.of(Material.BLAZE_ROD)
                .name(MAGIC_STICK_NAME, NamedTextColor.GOLD, TextDecoration.BOLD)
                .emptyLine()
                .lore(NamedTextColor.YELLOW, "Click derecho  → agregar waypoint")
                .lore(NamedTextColor.YELLOW, "Clic en bloque → waypoint + texto")
                .lore(NamedTextColor.RED,    "Shift+click    → terminar grabación")
                .build();
    }

    public static boolean isMagicStick(ItemStack item) {
        if (item == null || item.getType() != Material.BLAZE_ROD) return false;
        if (!item.hasItemMeta() || item.getItemMeta().displayName() == null) return false;
        String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(item.getItemMeta().displayName());
        return name.contains(MAGIC_STICK_NAME);
    }

    private void giveMagicStick(Player admin) {
        admin.getInventory().addItem(buildMagicStick());
    }

    private void removeMagicStick(Player admin) {
        admin.getInventory().remove(Material.BLAZE_ROD);
    }
}