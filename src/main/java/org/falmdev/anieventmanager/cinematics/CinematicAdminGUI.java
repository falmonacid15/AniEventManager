package org.falmdev.anieventmanager.cinematics;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.cinematics.model.Cinematic;
import org.falmdev.anieventmanager.cinematics.model.CinematicMarker;
import org.falmdev.anieventmanager.utils.gui.GuiUtil;
import org.falmdev.anieventmanager.utils.gui.HeadUtil;
import org.falmdev.anieventmanager.utils.gui.ItemBuilder;

import java.util.*;

/**
 * GUI de administración de cinematicas v2.
 *
 * Cambios respecto a v1:
 *  - El botón RECORD abre un prompt para la duración antes de grabar
 *  - Los waypoints son reemplazados por markers (textos en ticks específicos)
 *  - Se puede agregar un marker en un tick arbitrario
 *  - Los markers existentes se listan en la fila de waypoints
 */
public class CinematicAdminGUI implements Listener {

    public static final String LIST_TITLE          = "Admin: Cinematicas";
    public static final String DETAIL_TITLE_PREFIX = "Cin\u00b7";

    private static final int    SLOT_INFO    = 12;
    private static final String SKULL_INFO   = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTI2MDdkNjY0Nzk4OTYyZTEwZThkNzk0MmIzNWYwNzExOWY4YjRiODQ2YmNkNTNkZWViYTFlNTMyNzQ3YjQ2In19fQ==";
    private static final int    SLOT_PLAY    = 49;
    private static final String SKULL_PLAY   = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjUyN2ViYWU5ZjE1MzE1NGE3ZWQ0OWM4OGMwMmI1YTlhOWNhN2NiMTYxOGQ5OTE0YTNkOWRmOGNjYjNjODQifX19";
    private static final int    SLOT_STOP    = 50;
    private static final String SKULL_STOP   = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWE2OGNjMGM1NzlmNDIzZjUwYzAwZmUyY2QwMjZmOTlkMDJlOWUzMTdmYTdmNTFiNjY4ZGI1MGY4NjFlMzU5In19fQ==";
    private static final int    SLOT_DEBUG   = 47;  // entre RECORD(48) y BACK(45)
    private static final String SKULL_DEBUG  = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MmQ5OWM3OWVmZTkzMWEyMzJlN2Y2OWM4Y2NjMDY5ZDIyYTU3ZWM1MjVlZjQzY2VkMGE2NjExIn19fQ==";
    private static final int    SLOT_RECORD  = 48;
    private static final String SKULL_RECORD = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTBmMGZkZDVkOGRjNmI1NGVhYjk4ZjZmODVhYzE0ZGJiM2I1ODg2MzI5NjJlYzNkMWE0YzI0YzUwYmQ0ZTMifX19";
    private static final int    SLOT_RENAME  = 14;
    private static final int    SLOT_TIME    = 16;
    private static final int    SLOT_DELETE  = 53;
    private static final String SKULL_DELETE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmViNTg4YjIxYTZmOThhZDFmZjRlMDg1YzU1MmRjYjA1MGVmYzljYWI0MjdmNDYwNDhmMThmYzgwMzQ3NWY3In19fQ==";
    private static final int    SLOT_BACK    = 45;
    private static final String SKULL_BACK   = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWExZWYzOThhMTdmMWFmNzQ3NzAxNDUxN2Y3ZjE0MWQ4ODZkZjQxYTMyYzczOGNjOGE4M2ZiNTAyOTdiZDkyMSJ9fX0=";
    private static final int    SLOT_PREV    = 46;
    private static final String SKULL_PREV   = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmQxMDZlZjFhY2RkMDk5OGU2ZGJiYjUzMmUzMGFmNTU0YjNlMGVhOTE2MjFlNGViMGQxYTkzMTlkZWJmNjU0ZSJ9fX0=";
    private static final int    SLOT_NEXT    = 52;
    private static final String SKULL_NEXT   = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmI4NmE1MDY0N2ZjM2NlNTQ0NzJiMWRlYjJkZThjOTI1OTFhYzY3ZDYxMzQ4YWIwOTFkNzllNGNkMTgxOTc5OCJ9fX0=";

    // Slot especial para agregar un marker nuevo
    private static final int SLOT_ADD_MARKER = 51;

    // Markers en slots 18..35 (18 por página)
    private static final int MARKERS_START = 18;
    private static final int MARKERS_COUNT = 18;

    private static final Material[] MUSIC_DISCS = {
            Material.MUSIC_DISC_13,    Material.MUSIC_DISC_CAT,
            Material.MUSIC_DISC_BLOCKS, Material.MUSIC_DISC_CHIRP,
            Material.MUSIC_DISC_FAR,   Material.MUSIC_DISC_MALL,
            Material.MUSIC_DISC_MELLOHI, Material.MUSIC_DISC_STAL,
            Material.MUSIC_DISC_STRAD, Material.MUSIC_DISC_WARD,
            Material.MUSIC_DISC_11,    Material.MUSIC_DISC_WAIT,
            Material.MUSIC_DISC_OTHERSIDE, Material.MUSIC_DISC_PIGSTEP,
            Material.MUSIC_DISC_5,     Material.MUSIC_DISC_RELIC,
            Material.MUSIC_DISC_CREATOR, Material.MUSIC_DISC_CREATOR_MUSIC_BOX,
            Material.MUSIC_DISC_PRECIPICE
    };

    private static final Map<String, Long> TIME_PRESETS = new LinkedHashMap<>();
    static {
        TIME_PRESETS.put("Mediodía",   0L);
        TIME_PRESETS.put("Atardecer",  6000L);
        TIME_PRESETS.put("Medianoche", 12000L);
        TIME_PRESETS.put("Amanecer",   18000L);
        TIME_PRESETS.put("Alba",       22000L);
    }

    private final Anieventmanager plugin;

    private final Map<UUID, Integer> markerPages    = new HashMap<>();
    private final Map<UUID, String>  awaitingRename = new HashMap<>();
    private final Map<UUID, String>  awaitingRecord = new HashMap<>();  // id → esperando duración
    private final Map<UUID, String>  awaitingMarker = new HashMap<>();  // id → esperando tick

    private enum TimePromptType { START, END }
    private final Map<UUID, TimePrompt> awaitingTime = new HashMap<>();
    private record TimePrompt(String cinematicId, TimePromptType type) {}

    public CinematicAdminGUI(Anieventmanager plugin) {
        this.plugin = plugin;
    }

    // ── Vista 1: lista ────────────────────────────────────────────────────────

    public void openList(Player admin) {
        var all = plugin.getCinematicManager().getAllCinematics();
        int size = Math.max(27, ((all.size() / 9) + 1) * 9);
        if (size > 54) size = 54;
        Inventory inv = Bukkit.createInventory(null, size,
                Component.text(LIST_TITLE, NamedTextColor.GOLD));
        int slot = 0;
        for (Cinematic c : all) {
            inv.setItem(slot++, buildListItem(c));
            if (slot >= size) break;
        }
        admin.openInventory(inv);
    }

    private ItemStack buildListItem(Cinematic c) {
        NamedTextColor stateColor = switch (c.getState()) {
            case IDLE -> NamedTextColor.GRAY;
            case PLAYING -> NamedTextColor.GREEN;
            case RECORDING -> NamedTextColor.RED;
        };
        String stateLabel = switch (c.getState()) {
            case IDLE -> "IDLE";
            case PLAYING -> "REPRODUCIENDO";
            case RECORDING -> "GRABANDO";
        };
        int discIndex = Math.abs(c.getId().hashCode()) % MUSIC_DISCS.length;
        return ItemBuilder.of(MUSIC_DISCS[discIndex])
                .name(c.getDisplayName(), NamedTextColor.YELLOW, TextDecoration.BOLD)
                .emptyLine()
                .lore(GuiUtil.label("ID", Component.text(c.getId(), NamedTextColor.WHITE)))
                .lore(GuiUtil.label("Estado", Component.text(stateLabel, stateColor)))
                .lore(GuiUtil.label("Frames",
                        Component.text(c.getTotalFrames(), NamedTextColor.WHITE)))
                .lore(GuiUtil.label("Duración",
                        Component.text(String.format("%.1fs", c.getDurationSeconds()),
                                NamedTextColor.WHITE)))
                .lore(GuiUtil.label("Markers",
                        Component.text(c.getMarkers().size(), NamedTextColor.WHITE)))
                .emptyLine()
                .lore(NamedTextColor.YELLOW, "Click para gestionar.")
                .build();
    }

    // ── Vista 2: detalle ──────────────────────────────────────────────────────

    public void openDetail(Player admin, Cinematic cinematic) {
        openDetail(admin, cinematic, 0);
    }

    public void openDetail(Player admin, Cinematic cinematic, int markerPage) {
        markerPages.put(admin.getUniqueId(), markerPage);

        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text(DETAIL_TITLE_PREFIX + cinematic.getId(),
                        NamedTextColor.GOLD));
        GuiUtil.fillAll(inv);

        boolean canPlay   = cinematic.isIdle() && cinematic.getTotalFrames() > 0
                && !plugin.getCinematicManager().isAnyPlaying();
        boolean canStop   = cinematic.isPlaying();
        boolean canRecord = cinematic.isIdle()
                && !plugin.getCinematicManager().getRecorder().isRecording();

        // INFO
        inv.setItem(SLOT_INFO, ItemBuilder.of(HeadUtil.fromBase64(SKULL_INFO))
                .name(cinematic.getDisplayName(), NamedTextColor.YELLOW, TextDecoration.BOLD)
                .emptyLine()
                .lore(GuiUtil.label("ID", Component.text(cinematic.getId(), NamedTextColor.WHITE)))
                .lore(GuiUtil.label("Estado", Component.text(cinematic.getState().name(),
                        cinematic.isPlaying() ? NamedTextColor.GREEN
                                : cinematic.isRecording() ? NamedTextColor.RED
                                  : NamedTextColor.GRAY)))
                .lore(GuiUtil.label("Frames",
                        Component.text(cinematic.getTotalFrames(), NamedTextColor.WHITE)))
                .lore(GuiUtil.label("Duración", Component.text(
                        String.format("%.1fs  (%d ticks)",
                                cinematic.getDurationSeconds(), cinematic.getTotalFrames()),
                        NamedTextColor.WHITE)))
                .lore(GuiUtil.label("Markers",
                        Component.text(cinematic.getMarkers().size(), NamedTextColor.WHITE)))
                .build());

        // PLAY
        inv.setItem(SLOT_PLAY, ItemBuilder.of(HeadUtil.fromBase64(SKULL_PLAY))
                .name("▶ Reproducir",
                        canPlay ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY,
                        TextDecoration.BOLD)
                .emptyLine()
                .lore(canPlay
                        ? new String[]{"Click para reproducir."}
                        : new String[]{cinematic.getTotalFrames() == 0
                                       ? "No hay frames grabados."
                                       : "Ya hay una cinematica activa."})
                .build());
        boolean canDebug = cinematic.isIdle() && cinematic.getTotalFrames() > 0
                && !plugin.getCinematicManager().isAnyPlaying();
        inv.setItem(SLOT_DEBUG, ItemBuilder.of(HeadUtil.fromBase64(SKULL_DEBUG))
                .name("▶ Reproducción Debug",
                        canDebug ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.DARK_GRAY,
                        TextDecoration.BOLD)
                .emptyLine()
                .lore(canDebug
                        ? new String[]{
                        "Solo visible para vos.",
                        "Muestra tick en actionbar.",
                        "Hotbar: pausa, marker, detener."}
                        : new String[]{"No hay frames o ya hay una activa."})
                .build());
        // STOP
        inv.setItem(SLOT_STOP, ItemBuilder.of(HeadUtil.fromBase64(SKULL_STOP))
                .name("■ Detener",
                        canStop ? NamedTextColor.RED : NamedTextColor.DARK_GRAY,
                        TextDecoration.BOLD)
                .emptyLine()
                .lore(canStop ? new String[]{"Click para detener."}
                        : new String[]{"No hay reproducción activa."})
                .build());

        // RECORD — ahora pide duración antes de grabar
        inv.setItem(SLOT_RECORD, ItemBuilder.of(HeadUtil.fromBase64(SKULL_RECORD))
                .name("● Grabar",
                        canRecord ? NamedTextColor.RED : NamedTextColor.DARK_GRAY,
                        TextDecoration.BOLD)
                .emptyLine()
                .lore(canRecord
                        ? new String[]{"Click para escribir la duración",
                        "y comenzar a grabar.",
                        "Ejemplos: 30s, 1m, 2m30s"}
                        : new String[]{"No se puede grabar ahora."})
                .build());

        // RENAME
        inv.setItem(SLOT_RENAME, GuiUtil.simpleButton(Material.NAME_TAG,
                "Renombrar", NamedTextColor.GOLD, "Click para cambiar el nombre."));

        // TIME
        inv.setItem(SLOT_TIME, buildTimeItem(cinematic));

        // DELETE
        inv.setItem(SLOT_DELETE, ItemBuilder.of(HeadUtil.fromBase64(SKULL_DELETE))
                .name("Eliminar", NamedTextColor.RED, TextDecoration.BOLD)
                .emptyLine()
                .lore("Click para eliminar (pide confirmación).")
                .build());

        // BACK
        inv.setItem(SLOT_BACK, ItemBuilder.of(HeadUtil.fromBase64(SKULL_BACK))
                .name("← Volver", NamedTextColor.GRAY, TextDecoration.BOLD)
                .emptyLine()
                .lore("Click para regresar a la lista.")
                .build());

        // ADD MARKER
        inv.setItem(SLOT_ADD_MARKER, ItemBuilder.of(Material.GLOW_INK_SAC)
                .name("+ Agregar marker", NamedTextColor.GREEN, TextDecoration.BOLD)
                .emptyLine()
                .lore(NamedTextColor.YELLOW, "Click para escribir el tick")
                .lore("donde quieras agregar un texto.")
                .lore(NamedTextColor.GRAY, "Rango: 0 - " + Math.max(0,
                        cinematic.getTotalFrames() - 1))
                .build());

        // Markers paginados
        List<CinematicMarker> markers = cinematic.getMarkers();
        int totalPages = Math.max(1,
                (int) Math.ceil(markers.size() / (double) MARKERS_COUNT));
        int page = Math.max(0, Math.min(markerPage, totalPages - 1));

        for (int i = 0; i < MARKERS_COUNT; i++) {
            int mIdx = page * MARKERS_COUNT + i;
            if (mIdx < markers.size())
                inv.setItem(MARKERS_START + i, buildMarkerItem(markers.get(mIdx)));
        }

        inv.setItem(SLOT_PREV, page > 0
                ? ItemBuilder.of(HeadUtil.fromBase64(SKULL_PREV))
                  .name("← Página anterior", NamedTextColor.YELLOW, TextDecoration.BOLD)
                  .lore("Página " + page + " de " + totalPages).build()
                : GuiUtil.emptyPane());
        inv.setItem(SLOT_NEXT, page < totalPages - 1
                ? ItemBuilder.of(HeadUtil.fromBase64(SKULL_NEXT))
                  .name("Página siguiente →", NamedTextColor.YELLOW, TextDecoration.BOLD)
                  .lore("Página " + (page + 2) + " de " + totalPages).build()
                : GuiUtil.emptyPane());

        admin.openInventory(inv);
    }

    private ItemStack buildMarkerItem(CinematicMarker m) {
        ItemStack item = new ItemStack(Material.ITEM_FRAME);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Marker — tick " + m.getTick(),
                m.hasText() ? NamedTextColor.GOLD : NamedTextColor.WHITE,
                TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));

        if (m.hasText()) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(GuiUtil.label("Tick",
                Component.text(m.getTick(), NamedTextColor.YELLOW)));
        lore.add(GuiUtil.label("Segundo",
                Component.text(String.format("%.1fs", m.getTick() / 20.0),
                        NamedTextColor.GRAY)));

        if (m.hasText()) {
            lore.add(Component.empty());
            if (m.getTitleMain() != null)
                lore.add(Component.text("Título: " + m.getTitleMain(),
                        NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            if (m.getTitleSub() != null)
                lore.add(Component.text("Sub:    " + m.getTitleSub(),
                        NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            if (m.getActionbar() != null)
                lore.add(Component.text("Action: " + m.getActionbar(),
                        NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        }

        lore.add(Component.empty());
        lore.add(Component.text("Click para editar.",
                NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Click derecho → previsualizar posición.",
                NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildTimeItem(Cinematic c) {
        ItemBuilder b = ItemBuilder.of(Material.CLOCK)
                .name("Control de tiempo", NamedTextColor.AQUA, TextDecoration.BOLD)
                .emptyLine();

        if (c.hasTimeControl()) {
            b.lore(GuiUtil.label("Inicio",
                    Component.text(c.getTimeStart() + " ticks  (" +
                            timeLabel(c.getTimeStart()) + ")", NamedTextColor.YELLOW)));
            b.lore(GuiUtil.label("Fin",
                    Component.text(c.getTimeEnd() + " ticks  (" +
                            timeLabel(c.getTimeEnd()) + ")", NamedTextColor.YELLOW)));
            b.emptyLine()
                    .lore(NamedTextColor.GREEN, "✔ Control de tiempo activo.")
                    .emptyLine()
                    .lore(NamedTextColor.YELLOW, "Click izquierdo → cambiar inicio.")
                    .lore(NamedTextColor.YELLOW, "Click derecho   → cambiar fin.")
                    .lore(NamedTextColor.RED,    "Shift+click     → desactivar.");
        } else {
            b.lore(NamedTextColor.GRAY, "Sin control de tiempo.")
                    .emptyLine()
                    .lore(NamedTextColor.YELLOW, "Click izquierdo → setear inicio.")
                    .lore(NamedTextColor.YELLOW, "Click derecho   → setear fin.")
                    .emptyLine();
            TIME_PRESETS.forEach((name, ticks) ->
                    b.lore(NamedTextColor.DARK_GRAY, "  " + name + " = " + ticks + " ticks"));
        }
        return b.build();
    }

    private String timeLabel(long ticks) {
        String closest = "";
        long minDist = Long.MAX_VALUE;
        for (var e : TIME_PRESETS.entrySet()) {
            long dist = Math.abs(e.getValue() - ticks);
            if (dist < minDist) { minDist = dist; closest = e.getKey(); }
        }
        return minDist < 500 ? closest : ticksToHHMM(ticks);
    }

    private String ticksToHHMM(long ticks) {
        long totalMinutes = (ticks * 24 * 60 / 24000 + 6 * 60) % (24 * 60);
        return String.format("%02d:%02d", totalMinutes / 60, totalMinutes % 60);
    }

    // ── Click listener ────────────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        String title = GuiUtil.getTitle(event.getView());

        if (!(event.getWhoClicked() instanceof Player admin) || !admin.isOp()) {
            if (title.equals(LIST_TITLE) || title.startsWith(DETAIL_TITLE_PREFIX))
                event.setCancelled(true);
            return;
        }

        if (title.equals(LIST_TITLE)) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;
            if (clicked.getItemMeta().lore() == null) return;
            String id = extractIdFromLore(clicked);
            if (id == null) return;
            plugin.getCinematicManager().get(id).ifPresent(c -> openDetail(admin, c));
            return;
        }

        if (!title.startsWith(DETAIL_TITLE_PREFIX)) return;
        event.setCancelled(true);

        String cinematicId = title.substring(DETAIL_TITLE_PREFIX.length()).trim();
        Cinematic cinematic = plugin.getCinematicManager().get(cinematicId).orElse(null);
        if (cinematic == null) { admin.closeInventory(); return; }

        int slot = event.getRawSlot();
        int page = markerPages.getOrDefault(admin.getUniqueId(), 0);

        switch (slot) {
            case SLOT_BACK -> openList(admin);

            case SLOT_PLAY -> {
                if (!cinematic.isIdle() || cinematic.getTotalFrames() == 0
                        || plugin.getCinematicManager().isAnyPlaying()) return;
                boolean ok = plugin.getCinematicManager().play(cinematic.getId());
                admin.sendMessage(ok
                        ? Component.text("▶ Reproduciendo '", NamedTextColor.GREEN)
                          .append(Component.text(cinematic.getDisplayName(),
                                  NamedTextColor.YELLOW))
                          .append(Component.text("'.", NamedTextColor.GREEN))
                        : Component.text("✘ No se pudo reproducir.", NamedTextColor.RED));
                if (ok) admin.closeInventory();
                else openDetail(admin, cinematic, page);
            }
            case SLOT_DEBUG -> {
                if (!cinematic.isIdle() || cinematic.getTotalFrames() == 0
                        || plugin.getCinematicManager().isAnyPlaying()) return;
                admin.closeInventory();
                plugin.getCinematicManager().playDebug(cinematic.getId(), admin);
            }

            case SLOT_STOP -> {
                if (!cinematic.isPlaying()) return;
                plugin.getCinematicManager().stop();
                admin.sendMessage(Component.text("■ Detenida.", NamedTextColor.YELLOW));
                openDetail(admin, cinematic, page);
            }

            case SLOT_RECORD -> {
                if (!cinematic.isIdle()
                        || plugin.getCinematicManager().getRecorder().isRecording()) return;
                admin.closeInventory();
                awaitingRecord.put(admin.getUniqueId(), cinematic.getId());
                admin.sendMessage(Component.text(
                        "✎ Escribe la duración de la grabación:",
                        NamedTextColor.YELLOW));
                admin.sendMessage(Component.text(
                        "  Ejemplos: 30s  |  1m  |  2m30s  |  5m",
                        NamedTextColor.GRAY));
                admin.sendMessage(Component.text(
                        "  'cancelar' para cancelar.", NamedTextColor.GRAY));
            }

            case SLOT_RENAME -> {
                admin.closeInventory();
                awaitingRename.put(admin.getUniqueId(), cinematic.getId());
                admin.sendMessage(Component.text("✎ Nuevo nombre para '",
                                NamedTextColor.YELLOW)
                        .append(Component.text(cinematic.getDisplayName(), NamedTextColor.WHITE))
                        .append(Component.text("':", NamedTextColor.YELLOW)));
                admin.sendMessage(Component.text("  'cancelar' para cancelar.",
                        NamedTextColor.GRAY));
            }

            case SLOT_TIME -> handleTimeClick(admin, cinematic, event, page);

            case SLOT_DELETE -> {
                admin.closeInventory();
                plugin.getConfirmGUI().open(admin,
                        "Eliminar cinematica",
                        List.of("Vas a eliminar permanentemente:",
                                "  " + cinematic.getDisplayName()
                                        + " (id: " + cinematic.getId() + ")",
                                "Frames: " + cinematic.getTotalFrames(),
                                "Markers: " + cinematic.getMarkers().size()),
                        () -> {
                            plugin.getCinematicManager().delete(cinematic.getId());
                            admin.sendMessage(Component.text(
                                    "✔ Cinematica eliminada.", NamedTextColor.GREEN));
                            openList(admin);
                        });
            }

            case SLOT_ADD_MARKER -> {
                if (cinematic.getTotalFrames() == 0) {
                    admin.sendMessage(Component.text(
                            "✘ Primero grabá la cinematica.", NamedTextColor.RED));
                    return;
                }
                admin.closeInventory();
                awaitingMarker.put(admin.getUniqueId(), cinematic.getId());
                admin.sendMessage(Component.text(
                        "✎ Escribe el tick donde quieres agregar el marker:",
                        NamedTextColor.YELLOW));
                admin.sendMessage(Component.text(
                        "  Rango: 0 - " + (cinematic.getTotalFrames() - 1)
                                + "  (= 0s - " + String.format("%.1fs",
                                cinematic.getDurationSeconds()) + ")",
                        NamedTextColor.GRAY));
                admin.sendMessage(Component.text(
                        "  'cancelar' para cancelar.", NamedTextColor.GRAY));
            }

            case SLOT_PREV -> openDetail(admin, cinematic, Math.max(0, page - 1));
            case SLOT_NEXT -> openDetail(admin, cinematic, page + 1);

            default -> {
                int mSlotOffset = slot - MARKERS_START;
                if (mSlotOffset < 0 || mSlotOffset >= MARKERS_COUNT) return;
                int mIdx = page * MARKERS_COUNT + mSlotOffset;
                List<CinematicMarker> markers = cinematic.getMarkers();
                if (mIdx >= markers.size()) return;

                CinematicMarker marker = markers.get(mIdx);

                if (event.isRightClick()) {
                    // Preview de posición en click derecho del marker

                    var frame = cinematic.getFrame(marker.getTick());
                    var world = plugin.getCinematicManager()
                            .getCinematicWorld(cinematic.getId());
                    if (frame != null && world != null) {
                        var loc = new org.bukkit.Location(world,
                                frame.getX(), frame.getY(), frame.getZ(),
                                frame.getYaw(), frame.getPitch());
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            admin.teleport(loc);
                            admin.sendMessage(Component.text(
                                    "✔ Posición del marker en tick " + marker.getTick(),
                                    NamedTextColor.LIGHT_PURPLE));
                        });
                    }
                } else {
                    // Abrir editor de marker
                    admin.closeInventory();
                    Bukkit.getScheduler().runTask(plugin, () ->
                            plugin.getCinematicMarkerGUI().open(admin, cinematic, marker,
                                    () -> openDetail(admin, cinematic, page)));
                }
            }
        }
    }

    // ── Control de tiempo ─────────────────────────────────────────────────────

    private void handleTimeClick(Player admin, Cinematic cinematic,
                                 InventoryClickEvent event, int page) {
        if (event.isShiftClick()) {
            cinematic.clearTimeControl();
            cinematic.save();
            admin.sendMessage(Component.text(
                    "✔ Control de tiempo desactivado.", NamedTextColor.YELLOW));
            openDetail(admin, cinematic, page);
            return;
        }
        TimePromptType type = event.isRightClick()
                ? TimePromptType.END : TimePromptType.START;
        String label = type == TimePromptType.END ? "FIN" : "INICIO";
        admin.closeInventory();
        awaitingTime.put(admin.getUniqueId(),
                new TimePrompt(cinematic.getId(), type));
        admin.sendMessage(Component.text(
                "✎ Escribe el tick de " + label + " del tiempo del mundo:",
                NamedTextColor.YELLOW));
        admin.sendMessage(Component.text(
                "  Ejemplos: 0 (mediodía), 6000 (atardecer), 12000 (medianoche), 18000 (amanecer)",
                NamedTextColor.GRAY));
        admin.sendMessage(Component.text(
                "  O escribe: mediodia, atardecer, medianoche, amanecer, alba",
                NamedTextColor.GRAY));
        admin.sendMessage(Component.text("  'cancelar' para cancelar.",
                NamedTextColor.GRAY));
    }

    // ── Chat prompts ──────────────────────────────────────────────────────────

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player admin = event.getPlayer();
        UUID uid = admin.getUniqueId();
        String msg = event.getMessage().trim();

        // ── Rename ────────────────────────────────────────────────────────────
        if (awaitingRename.containsKey(uid)) {
            String id = awaitingRename.remove(uid);
            event.setCancelled(true);
            if (msg.equalsIgnoreCase("cancelar")) {
                admin.sendMessage(Component.text("Cancelado.", NamedTextColor.GRAY));
                return;
            }
            if (msg.length() < 2 || msg.length() > 32) {
                admin.sendMessage(Component.text(
                        "✘ Nombre inválido (2-32 caracteres).", NamedTextColor.RED));
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () ->
                    plugin.getCinematicManager().get(id).ifPresent(c -> {
                        c.setDisplayName(msg);
                        c.save();
                        admin.sendMessage(Component.text("✔ Renombrada a ",
                                        NamedTextColor.GREEN)
                                .append(Component.text(msg, NamedTextColor.YELLOW)));
                        openDetail(admin, c);
                    }));
            return;
        }

        // ── Record (prompt de duración) ────────────────────────────────────────
        if (awaitingRecord.containsKey(uid)) {
            String id = awaitingRecord.remove(uid);
            event.setCancelled(true);
            if (msg.equalsIgnoreCase("cancelar")) {
                admin.sendMessage(Component.text("Cancelado.", NamedTextColor.GRAY));
                return;
            }
            int durationTicks = CinematicRecorder.parseDuration(msg);
            if (durationTicks < 0) {
                admin.sendMessage(Component.text(
                        "✘ Duración inválida. Usa: 30s, 1m, 2m30s (mín 2s, máx 10m).",
                        NamedTextColor.RED));
                return;
            }
            int finalDuration = durationTicks;
            Bukkit.getScheduler().runTask(plugin, () ->
                    plugin.getCinematicManager().get(id).ifPresent(c -> {
                        boolean ok = plugin.getCinematicManager()
                                .startRecording(admin, c, finalDuration);
                        if (!ok) {
                            admin.sendMessage(Component.text(
                                    "✘ No se pudo iniciar la grabación.",
                                    NamedTextColor.RED));
                        }
                    }));
            return;
        }

        // ── Agregar marker ─────────────────────────────────────────────────────
        if (awaitingMarker.containsKey(uid)) {
            String id = awaitingMarker.remove(uid);
            event.setCancelled(true);
            if (msg.equalsIgnoreCase("cancelar")) {
                admin.sendMessage(Component.text("Cancelado.", NamedTextColor.GRAY));
                return;
            }
            try {
                int tick = Integer.parseInt(msg.trim());
                Bukkit.getScheduler().runTask(plugin, () ->
                        plugin.getCinematicManager().get(id).ifPresent(c -> {
                            if (tick < 0 || tick >= c.getTotalFrames()) {
                                admin.sendMessage(Component.text(
                                        "✘ Tick fuera de rango (0 - "
                                                + (c.getTotalFrames() - 1) + ").",
                                        NamedTextColor.RED));
                                return;
                            }
                            CinematicMarker marker = new CinematicMarker(tick);
                            c.addMarker(marker);
                            c.save();
                            admin.sendMessage(Component.text(
                                    "✔ Marker creado en tick " + tick + ". Ahora podés editarlo.",
                                    NamedTextColor.GREEN));
                            // Abrir el editor del marker inmediatamente
                            plugin.getCinematicMarkerGUI().open(admin, c, marker,
                                    () -> openDetail(admin, c));
                        }));
            } catch (NumberFormatException e) {
                admin.sendMessage(Component.text(
                        "✘ Escribe un número de tick válido.", NamedTextColor.RED));
            }
            return;
        }

        // ── Tiempo del mundo ──────────────────────────────────────────────────
        if (awaitingTime.containsKey(uid)) {
            TimePrompt prompt = awaitingTime.remove(uid);
            event.setCancelled(true);
            if (msg.equalsIgnoreCase("cancelar")) {
                admin.sendMessage(Component.text("Cancelado.", NamedTextColor.GRAY));
                return;
            }
            Long ticks = parseTimeInput(msg);
            if (ticks == null) {
                admin.sendMessage(Component.text(
                        "✘ Valor inválido.", NamedTextColor.RED));
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () ->
                    plugin.getCinematicManager().get(prompt.cinematicId()).ifPresent(c -> {
                        if (prompt.type() == TimePromptType.START) {
                            c.setTimeStart(ticks);
                            admin.sendMessage(Component.text(
                                    "✔ Inicio: " + ticks + " ticks  (" + timeLabel(ticks) + ")",
                                    NamedTextColor.GREEN));
                        } else {
                            c.setTimeEnd(ticks);
                            admin.sendMessage(Component.text(
                                    "✔ Fin: " + ticks + " ticks  (" + timeLabel(ticks) + ")",
                                    NamedTextColor.GREEN));
                        }
                        c.save();
                        openDetail(admin, c);
                    }));
        }
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private Long parseTimeInput(String input) {
        String lower = input.toLowerCase().trim();
        for (var e : TIME_PRESETS.entrySet())
            if (e.getKey().toLowerCase().equals(lower)) return e.getValue();
        if (lower.equals("mediodia") || lower.equals("mediodía")) return 0L;
        if (lower.equals("amanecer")) return 18000L;
        if (lower.equals("atardecer")) return 6000L;
        if (lower.equals("medianoche")) return 12000L;
        if (lower.equals("alba")) return 22000L;
        try {
            long v = Long.parseLong(input.trim());
            return (v >= 0 && v <= 23999) ? v : null;
        } catch (NumberFormatException e) { return null; }
    }

    private String extractIdFromLore(ItemStack item) {
        if (!item.hasItemMeta()) return null;
        List<Component> lore = item.getItemMeta().lore();
        if (lore == null) return null;
        for (Component line : lore) {
            String plain = net.kyori.adventure.text.serializer.plain
                    .PlainTextComponentSerializer.plainText().serialize(line);
            if (plain.startsWith("  ID: ")) return plain.substring(6).trim();
        }
        return null;
    }
}