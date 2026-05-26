package org.falmdev.anieventmanager.cinematics;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.cinematics.model.Cinematic;
import org.falmdev.anieventmanager.cinematics.model.CinematicFrame;
import org.falmdev.anieventmanager.cinematics.model.CinematicMarker;
import org.falmdev.anieventmanager.utils.gui.GuiUtil;
import org.falmdev.anieventmanager.utils.gui.ItemBuilder;

import java.util.*;

/**
 * GUI de edición de markers de texto.
 * Reemplaza CinematicWaypointGUI.
 *
 * Layout (27 slots):
 *   Fila 0: borders
 *   Fila 1: INFO(10) | TITLE(11) | SUBTITLE(12) | ACTIONBAR(13) | TIMING(14) | PREVIEW(15) | DELETE(16)
 *   Fila 2: borders | BACK(22)
 *
 * PREVIEW teleporta al admin a la posición del frame en ese tick
 * para que vea desde dónde se ve la cámara en ese momento.
 */
public class CinematicMarkerGUI implements Listener {

    public static final String TITLE_PREFIX = "Marker tick ";

    private static final int SLOT_INFO      = 10;
    private static final int SLOT_TITLE     = 11;
    private static final int SLOT_SUBTITLE  = 12;
    private static final int SLOT_ACTIONBAR = 13;
    private static final int SLOT_TIMING    = 14;
    private static final int SLOT_PREVIEW   = 15;
    private static final int SLOT_DELETE    = 16;
    private static final int SLOT_BACK      = 22;

    private final Anieventmanager plugin;

    private enum EditField { TITLE, SUBTITLE, ACTIONBAR, TIMING }
    private record Session(Cinematic cinematic, CinematicMarker marker,
                           EditField field, Runnable onReturn) {}

    private final Map<UUID, Session> awaitingChat = new HashMap<>();

    public CinematicMarkerGUI(Anieventmanager plugin) {
        this.plugin = plugin;
    }

    // ── Abrir ─────────────────────────────────────────────────────────────────

    public void open(Player admin, Cinematic cinematic, CinematicMarker marker) {
        open(admin, cinematic, marker, null);
    }

    public void open(Player admin, Cinematic cinematic, CinematicMarker marker,
                     Runnable onReturn) {
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text(TITLE_PREFIX + marker.getTick(), NamedTextColor.GOLD)
                        .append(Component.text(" — " + cinematic.getDisplayName(),
                                NamedTextColor.YELLOW)));

        GuiUtil.fillAll(inv);

        double seconds = marker.getTick() / 20.0;
        inv.setItem(SLOT_INFO, ItemBuilder.of(Material.CLOCK)
                .name("Marker en tick " + marker.getTick(), NamedTextColor.WHITE,
                        TextDecoration.BOLD)
                .emptyLine()
                .lore(GuiUtil.label("Tick", Component.text(marker.getTick(),
                        NamedTextColor.YELLOW)))
                .lore(GuiUtil.label("Segundo", Component.text(
                        String.format("%.1fs", seconds), NamedTextColor.GRAY)))
                .lore(GuiUtil.noItalic(marker.hasText()
                        ? Component.text("✔ Tiene texto.", NamedTextColor.GREEN)
                        : Component.text("Sin texto.", NamedTextColor.DARK_GRAY)))
                .build());

        inv.setItem(SLOT_TITLE, buildFieldButton(Material.NAME_TAG,
                "Título principal", NamedTextColor.GOLD, marker.getTitleMain(),
                "Soporta &a, &c, &l, etc."));

        inv.setItem(SLOT_SUBTITLE, buildFieldButton(Material.PAPER,
                "Subtítulo", NamedTextColor.YELLOW, marker.getTitleSub(),
                "Soporta &a, &c, &l, etc."));

        inv.setItem(SLOT_ACTIONBAR, buildFieldButton(Material.WRITABLE_BOOK,
                "Actionbar", NamedTextColor.AQUA, marker.getActionbar(),
                "Texto en la barra de acción."));

        inv.setItem(SLOT_TIMING, ItemBuilder.of(Material.COMPARATOR)
                .name("Tiempos del título", NamedTextColor.GOLD, TextDecoration.BOLD)
                .emptyLine()
                .lore(GuiUtil.label("Fade in",
                        Component.text(marker.getTitleFadeIn() + " ticks",
                                NamedTextColor.WHITE)))
                .lore(GuiUtil.label("Stay",
                        Component.text(marker.getTitleStay() + " ticks",
                                NamedTextColor.WHITE)))
                .lore(GuiUtil.label("Fade out",
                        Component.text(marker.getTitleFadeOut() + " ticks",
                                NamedTextColor.WHITE)))
                .emptyLine()
                .lore(NamedTextColor.YELLOW, "Click → escribir: fadeIn stay fadeOut")
                .lore("Ejemplo: 10 60 10")
                .build());

        // PREVIEW — teleporta al admin al frame de ese tick
        boolean hasFrame = marker.getTick() < cinematic.getTotalFrames();
        inv.setItem(SLOT_PREVIEW, ItemBuilder.of(Material.ENDER_EYE)
                .name("Previsualizar posición", NamedTextColor.LIGHT_PURPLE,
                        TextDecoration.BOLD)
                .emptyLine()
                .lore(hasFrame
                        ? new String[]{"Click para teleportarte al punto",
                        "donde estará la cámara en este tick."}
                        : new String[]{"No hay frame grabado en este tick."})
                .build());

        inv.setItem(SLOT_DELETE, ItemBuilder.of(Material.BARRIER)
                .name("Eliminar marker", NamedTextColor.RED, TextDecoration.BOLD)
                .emptyLine()
                .lore("Click para eliminar este marker.")
                .build());

        inv.setItem(SLOT_BACK, GuiUtil.simpleButton(Material.ARROW,
                "← Volver", NamedTextColor.GRAY, "Click para regresar."));

        admin.openInventory(inv);
        // Guardar sesión para saber a qué cinematica/marker pertenece este GUI
        awaitingChat.remove(admin.getUniqueId()); // limpiar prompts previos
    }

    private org.bukkit.inventory.ItemStack buildFieldButton(Material mat, String label,
                                                            NamedTextColor color, String currentValue, String hint) {
        ItemBuilder b = ItemBuilder.of(mat)
                .name(label, color, TextDecoration.BOLD)
                .emptyLine();

        if (currentValue != null && !currentValue.isBlank()) {
            b.lore(GuiUtil.noItalic(Component.text("Actual: ", NamedTextColor.GRAY)
                    .append(LegacyComponentSerializer.legacyAmpersand()
                            .deserialize(currentValue)
                            .decoration(TextDecoration.ITALIC, false))));
        } else {
            b.lore(NamedTextColor.DARK_GRAY, "Sin texto.");
        }

        return b.emptyLine()
                .lore(NamedTextColor.YELLOW, "Click para escribir en el chat.")
                .lore(hint)
                .lore("'borrar' para vaciar el campo.")
                .build();
    }

    // ── Click listener ────────────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        String title = GuiUtil.getTitle(event.getView());
        if (!title.startsWith(TITLE_PREFIX)) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player admin)) return;

        // Recuperar cinematica y marker desde el título
        // Formato: "Marker tick <N> — <displayName>"
        String[] parts = title.split(" — ", 2);
        if (parts.length < 2) return;

        String tickStr = parts[0].replace(TITLE_PREFIX, "").trim();
        String displayName = parts[1].trim();

        int tick;
        try { tick = Integer.parseInt(tickStr); }
        catch (NumberFormatException e) { return; }

        Cinematic cinematic = plugin.getCinematicManager().getAllCinematics()
                .stream().filter(c -> c.getDisplayName().equals(displayName))
                .findFirst().orElse(null);
        if (cinematic == null) { admin.closeInventory(); return; }

        CinematicMarker marker = cinematic.getMarkerAt(tick).orElse(null);
        if (marker == null) { admin.closeInventory(); return; }

        int slot = event.getRawSlot();

        switch (slot) {
            case SLOT_TITLE -> promptChat(admin, cinematic, marker, EditField.TITLE,
                    "Escribe el título principal (soporta &-codes):");
            case SLOT_SUBTITLE -> promptChat(admin, cinematic, marker, EditField.SUBTITLE,
                    "Escribe el subtítulo (soporta &-codes):");
            case SLOT_ACTIONBAR -> promptChat(admin, cinematic, marker, EditField.ACTIONBAR,
                    "Escribe el texto del actionbar (soporta &-codes):");
            case SLOT_TIMING -> promptChat(admin, cinematic, marker, EditField.TIMING,
                    "Escribe los tiempos: fadeIn stay fadeOut (ej: 10 60 10)");

            case SLOT_PREVIEW -> {
                if (marker.getTick() >= cinematic.getTotalFrames()) return;
                CinematicFrame frame = cinematic.getFrame(marker.getTick());
                if (frame == null) return;
                World world = plugin.getCinematicManager()
                        .getCinematicWorld(cinematic.getId());
                if (world == null) {
                    admin.sendMessage(Component.text(
                            "✘ No se encontró el mundo de la cinematica.",
                            NamedTextColor.RED));
                    return;
                }

                Location previewLoc = new Location(world,
                        frame.getX(), frame.getY(), frame.getZ(),
                        frame.getYaw(), frame.getPitch());
                // Teleport en el tick siguiente para que no conflictúe
                Bukkit.getScheduler().runTask(plugin, () -> {
                    admin.teleport(previewLoc);
                    admin.sendMessage(Component.text(
                            "✔ Teleportado al frame del tick " + marker.getTick()
                                    + " — esta es la posición de la cámara en ese momento.",
                            NamedTextColor.LIGHT_PURPLE));
                });
            }

            case SLOT_DELETE -> {
                cinematic.removeMarker(marker.getTick());
                cinematic.save();
                admin.sendMessage(Component.text(
                        "✔ Marker del tick " + marker.getTick() + " eliminado.",
                        NamedTextColor.GREEN));
                admin.closeInventory();
                // Volver al detalle de la cinematica
                Bukkit.getScheduler().runTask(plugin, () ->
                        plugin.getCinematicAdminGUI().openDetail(admin, cinematic));
            }

            case SLOT_BACK -> {
                admin.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () ->
                        plugin.getCinematicAdminGUI().openDetail(admin, cinematic));
            }
        }
    }

    private void promptChat(Player admin, Cinematic cinematic, CinematicMarker marker,
                            EditField field, String prompt) {
        admin.closeInventory();
        awaitingChat.put(admin.getUniqueId(),
                new Session(cinematic, marker, field, null));
        admin.sendMessage(Component.text("✎ " + prompt, NamedTextColor.YELLOW));
        admin.sendMessage(Component.text(
                "  'cancelar' para cancelar.", NamedTextColor.GRAY));
    }

    // ── Chat prompt ───────────────────────────────────────────────────────────

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player admin = event.getPlayer();
        UUID uid = admin.getUniqueId();
        Session session = awaitingChat.remove(uid);
        if (session == null) return;

        event.setCancelled(true);
        String msg = event.getMessage().trim();

        if (msg.equalsIgnoreCase("cancelar")) {
            admin.sendMessage(Component.text("Cancelado.", NamedTextColor.GRAY));
            Bukkit.getScheduler().runTask(plugin, () ->
                    open(admin, session.cinematic(), session.marker(), session.onReturn()));
            return;
        }

        CinematicMarker marker = session.marker();
        boolean borrar = msg.equalsIgnoreCase("borrar");

        Bukkit.getScheduler().runTask(plugin, () -> {
            switch (session.field()) {
                case TITLE -> {
                    marker.setTitleMain(borrar ? null : msg);
                    admin.sendMessage(Component.text(
                            borrar ? "✔ Título borrado." : "✔ Título guardado.",
                            NamedTextColor.GREEN));
                }
                case SUBTITLE -> {
                    marker.setTitleSub(borrar ? null : msg);
                    admin.sendMessage(Component.text(
                            borrar ? "✔ Subtítulo borrado." : "✔ Subtítulo guardado.",
                            NamedTextColor.GREEN));
                }
                case ACTIONBAR -> {
                    marker.setActionbar(borrar ? null : msg);
                    admin.sendMessage(Component.text(
                            borrar ? "✔ Actionbar borrado." : "✔ Actionbar guardado.",
                            NamedTextColor.GREEN));
                }
                case TIMING -> {
                    String[] parts = msg.trim().split("\\s+");
                    if (parts.length != 3) {
                        admin.sendMessage(Component.text(
                                "✘ Formato incorrecto. Usa: fadeIn stay fadeOut (ej: 10 60 10)",
                                NamedTextColor.RED));
                        open(admin, session.cinematic(), marker, session.onReturn());
                        return;
                    }
                    try {
                        marker.setTitleFadeIn( Math.max(0, Integer.parseInt(parts[0])));
                        marker.setTitleStay(   Math.max(0, Integer.parseInt(parts[1])));
                        marker.setTitleFadeOut(Math.max(0, Integer.parseInt(parts[2])));
                        admin.sendMessage(Component.text(
                                "✔ Tiempos guardados.", NamedTextColor.GREEN));
                    } catch (NumberFormatException e) {
                        admin.sendMessage(Component.text(
                                "✘ Valor inválido.", NamedTextColor.RED));
                    }
                }
            }
            session.cinematic().save();
            open(admin, session.cinematic(), marker, session.onReturn());
        });
    }
}