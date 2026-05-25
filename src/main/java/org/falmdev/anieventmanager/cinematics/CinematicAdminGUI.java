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
import org.falmdev.anieventmanager.cinematics.model.CinematicWaypoint;
import org.falmdev.anieventmanager.utils.gui.GuiUtil;
import org.falmdev.anieventmanager.utils.gui.HeadUtil;
import org.falmdev.anieventmanager.utils.gui.ItemBuilder;

import java.util.*;

public class CinematicAdminGUI implements Listener {

    public static final String LIST_TITLE          = "Admin: Cinematicas";
    public static final String DETAIL_TITLE_PREFIX = "Cin\u00b7";

    private static final int    SLOT_INFO    = 12;
    private static final String SKULL_INFO   = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTI2MDdkNjY0Nzk4OTYyZTEwZThkNzk0MmIzNWYwNzExOWY4YjRiODQ2YmNkNTNkZWViYTFlNTMyNzQ3YjQ2In19fQ==";
    private static final int    SLOT_PLAY    = 49;
    private static final String SKULL_PLAY   = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjUyN2ViYWU5ZjE1MzE1NGE3ZWQ0OWM4OGMwMmI1YTlhOWNhN2NiMTYxOGQ5OTE0YTNkOWRmOGNjYjNjODQifX19";
    private static final int    SLOT_STOP    = 50;
    private static final String SKULL_STOP   = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWE2OGNjMGM1NzlmNDIzZjUwYzAwZmUyY2QwMjZmOTlkMDJlOWUzMTdmYTdmNTFiNjY4ZGI1MGY4NjFlMzU5In19fQ==";
    private static final int    SLOT_RECORD  = 48;
    private static final String SKULL_RECORD = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTBmMGZkZDVkOGRjNmI1NGVhYjk4ZjZmODVhYzE0ZGJiM2I1ODg2MzI5NjJlYzNkMWE0YzI0YzUwYmQ0ZTMifX19";
    private static final int    SLOT_RENAME  = 14;
    private static final int    SLOT_DELETE  = 53;
    private static final String SKULL_DELETE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmViNTg4YjIxYTZmOThhZDFmZjRlMDg1YzU1MmRjYjA1MGVmYzljYWI0MjdmNDYwNDhmMThmYzgwMzQ3NWY3In19fQ==";
    private static final int    SLOT_BACK    = 45;
    private static final String SKULL_BACK   = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWExZWYzOThhMTdmMWFmNzQ3NzAxNDUxN2Y3ZjE0MWQ4ODZkZjQxYTMyYzczOGNjOGE4M2ZiNTAyOTdiZDkyMSJ9fX0=";
    private static final int    SLOT_PREV    = 46;
    private static final String SKULL_PREV   = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmQxMDZlZjFhY2RkMDk5OGU2ZGJiYjUzMmUzMGFmNTU0YjNlMGVhOTE2MjFlNGViMGQxYTkzMTlkZWJmNjU0ZSJ9fX0=";
    private static final int    SLOT_NEXT    = 52;
    private static final String SKULL_NEXT   = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmI4NmE1MDY0N2ZjM2NlNTQ0NzJiMWRlYjJkZThjOTI1OTFhYzY3ZDYxMzQ4YWIwOTFkNzllNGNkMTgxOTc5OCJ9fX0=";

    private static final int WAYPOINTS_START = 18;
    private static final int WAYPOINTS_COUNT = 18;

    // Todos los discos de música disponibles en 1.21
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

    private final Anieventmanager plugin;
    private final Random random = new Random();

    private final Map<UUID, Integer> waypointPages  = new HashMap<>();
    private final Map<UUID, String>  awaitingRename = new HashMap<>();

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

    /** Usa un disco de música aleatorio como representación de cada cinematica. */
    private ItemStack buildListItem(Cinematic c) {
        NamedTextColor stateColor = switch (c.getState()) {
            case IDLE      -> NamedTextColor.GRAY;
            case PLAYING   -> NamedTextColor.GREEN;
            case RECORDING -> NamedTextColor.RED;
        };
        String stateLabel = switch (c.getState()) {
            case IDLE      -> "IDLE";
            case PLAYING   -> "REPRODUCIENDO";
            case RECORDING -> "GRABANDO";
        };

        // Disco aleatorio — seed basado en el ID para que sea consistente
        // (la misma cinematica siempre tiene el mismo disco)
        int discIndex = Math.abs(c.getId().hashCode()) % MUSIC_DISCS.length;
        Material disc = MUSIC_DISCS[discIndex];

        return ItemBuilder.of(disc)
                .name(c.getDisplayName(), NamedTextColor.YELLOW, TextDecoration.BOLD)
                .emptyLine()
                .lore(GuiUtil.label("ID",
                        Component.text(c.getId(), NamedTextColor.WHITE)))
                .lore(GuiUtil.label("Estado",
                        Component.text(stateLabel, stateColor)))
                .lore(GuiUtil.label("Waypoints",
                        Component.text(c.getWaypoints().size(), NamedTextColor.WHITE)))
                .lore(GuiUtil.label("Duración",
                        Component.text(String.format("%.1fs", c.getTotalTicks() / 20.0),
                                NamedTextColor.WHITE)))
                .emptyLine()
                .lore(NamedTextColor.YELLOW, "Click para gestionar.")
                .build();
    }

    // ── Vista 2: detalle ──────────────────────────────────────────────────────

    public void openDetail(Player admin, Cinematic cinematic) {
        openDetail(admin, cinematic, 0);
    }

    public void openDetail(Player admin, Cinematic cinematic, int waypointPage) {
        waypointPages.put(admin.getUniqueId(), waypointPage);

        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text(DETAIL_TITLE_PREFIX + cinematic.getId(),
                        NamedTextColor.GOLD));

        GuiUtil.fillAll(inv);

        boolean canPlay   = cinematic.isIdle()
                && cinematic.getWaypoints().size() >= 2
                && !plugin.getCinematicManager().isAnyPlaying();
        boolean canStop   = cinematic.isPlaying();
        boolean canRecord = cinematic.isIdle()
                && !plugin.getCinematicManager().getRecorder().isRecording();

        inv.setItem(SLOT_INFO, ItemBuilder.of(HeadUtil.fromBase64(SKULL_INFO))
                .name(cinematic.getDisplayName(), NamedTextColor.YELLOW, TextDecoration.BOLD)
                .emptyLine()
                .lore(GuiUtil.label("ID",
                        Component.text(cinematic.getId(), NamedTextColor.WHITE)))
                .lore(GuiUtil.label("Estado", Component.text(
                        cinematic.getState().name(),
                        cinematic.isPlaying() ? NamedTextColor.GREEN
                                : cinematic.isRecording() ? NamedTextColor.RED
                                  : NamedTextColor.GRAY)))
                .lore(GuiUtil.label("Waypoints",
                        Component.text(cinematic.getWaypoints().size(), NamedTextColor.WHITE)))
                .lore(GuiUtil.label("Duración", Component.text(
                        String.format("%.1fs  (%d ticks)",
                                cinematic.getTotalTicks() / 20.0,
                                cinematic.getTotalTicks()),
                        NamedTextColor.WHITE)))
                .build());

        inv.setItem(SLOT_PLAY, ItemBuilder.of(HeadUtil.fromBase64(SKULL_PLAY))
                .name("▶ Reproducir",
                        canPlay ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY,
                        TextDecoration.BOLD)
                .emptyLine()
                .lore(canPlay
                        ? new String[]{"Click para reproducir para todos",
                        "los jugadores con equipo."}
                        : new String[]{cinematic.getWaypoints().size() < 2
                                       ? "Necesita al menos 2 waypoints."
                                       : "Ya hay una cinematica activa."})
                .build());

        inv.setItem(SLOT_STOP, ItemBuilder.of(HeadUtil.fromBase64(SKULL_STOP))
                .name("■ Detener",
                        canStop ? NamedTextColor.RED : NamedTextColor.DARK_GRAY,
                        TextDecoration.BOLD)
                .emptyLine()
                .lore(canStop
                        ? new String[]{"Click para detener la reproducción."}
                        : new String[]{"No hay reproducción activa."})
                .build());

        inv.setItem(SLOT_RECORD, ItemBuilder.of(HeadUtil.fromBase64(SKULL_RECORD))
                .name("● Grabar",
                        canRecord ? NamedTextColor.RED : NamedTextColor.DARK_GRAY,
                        TextDecoration.BOLD)
                .emptyLine()
                .lore(canRecord
                        ? new String[]{"Click para iniciar grabación.",
                        "Shift+click para terminar."}
                        : new String[]{"No se puede grabar ahora."})
                .build());

        inv.setItem(SLOT_RENAME, GuiUtil.simpleButton(Material.NAME_TAG,
                "Renombrar", NamedTextColor.GOLD, "Click para cambiar el nombre."));

        inv.setItem(SLOT_DELETE, ItemBuilder.of(HeadUtil.fromBase64(SKULL_DELETE))
                .name("Eliminar", NamedTextColor.RED, TextDecoration.BOLD)
                .emptyLine()
                .lore("Click para eliminar (pide confirmación).")
                .build());

        inv.setItem(SLOT_BACK, ItemBuilder.of(HeadUtil.fromBase64(SKULL_BACK))
                .name("← Volver", NamedTextColor.GRAY, TextDecoration.BOLD)
                .emptyLine()
                .lore("Click para regresar a la lista.")
                .build());

        // Waypoints paginados
        List<CinematicWaypoint> waypoints = cinematic.getWaypoints();
        int totalPages = Math.max(1,
                (int) Math.ceil(waypoints.size() / (double) WAYPOINTS_COUNT));
        int page = Math.max(0, Math.min(waypointPage, totalPages - 1));

        for (int i = 0; i < WAYPOINTS_COUNT; i++) {
            int wpIndex = page * WAYPOINTS_COUNT + i;
            if (wpIndex < waypoints.size()) {
                inv.setItem(WAYPOINTS_START + i,
                        buildWaypointItem(waypoints.get(wpIndex)));
            }
        }

        inv.setItem(SLOT_PREV, page > 0
                ? ItemBuilder.of(HeadUtil.fromBase64(SKULL_PREV))
                  .name("← Página anterior", NamedTextColor.YELLOW, TextDecoration.BOLD)
                  .lore("Página " + page + " de " + totalPages)
                  .build()
                : GuiUtil.emptyPane());

        inv.setItem(SLOT_NEXT, page < totalPages - 1
                ? ItemBuilder.of(HeadUtil.fromBase64(SKULL_NEXT))
                  .name("Página siguiente →", NamedTextColor.YELLOW, TextDecoration.BOLD)
                  .lore("Página " + (page + 2) + " de " + totalPages)
                  .build()
                : GuiUtil.emptyPane());

        admin.openInventory(inv);
    }

    /**
     * Waypoints:
     *  - Sin texto → ITEM_FRAME (antes QUARTZ)
     *  - Con texto  → ITEM_FRAME encantado con brillo (antes BEACON)
     *
     * El brillo se aplica con un encantamiento invisible:
     * addEnchant(UNBREAKING, 1, true) + addItemFlags(HIDE_ENCHANTS)
     */
    private ItemStack buildWaypointItem(CinematicWaypoint wp) {
        ItemStack item = new ItemStack(Material.ITEM_FRAME);
        ItemMeta meta = item.getItemMeta();

        // Nombre
        Component name = Component.text("Waypoint #" + (wp.getIndex() + 1),
                        wp.hasText() ? NamedTextColor.GOLD : NamedTextColor.WHITE,
                        TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false);
        meta.displayName(name);

        // Si tiene texto → aplicar brillo (enchant invisible)
        if (wp.hasText()) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        // Lore
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(GuiUtil.label("Tick",
                Component.text(wp.getTickOffset(), NamedTextColor.YELLOW)));
        lore.add(GuiUtil.label("Segundos",
                Component.text(String.format("%.1fs",
                        wp.getTickOffset() / 20.0), NamedTextColor.GRAY)));

        if (wp.hasText()) {
            lore.add(Component.empty());
            if (wp.getTitleMain() != null)
                lore.add(Component.text("Título: " + wp.getTitleMain(),
                        NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            if (wp.getTitleSub() != null)
                lore.add(Component.text("Sub:    " + wp.getTitleSub(),
                        NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            if (wp.getActionbar() != null)
                lore.add(Component.text("Action: " + wp.getActionbar(),
                        NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        }

        lore.add(Component.empty());
        lore.add(Component.text("Click para editar textos.",
                NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ── Click listener ────────────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        String title = GuiUtil.getTitle(event.getView());

        if (!(event.getWhoClicked() instanceof Player admin) || !admin.isOp()) {
            if (title.equals(LIST_TITLE) || title.startsWith(DETAIL_TITLE_PREFIX)) {
                event.setCancelled(true);
            }
            return;
        }

        if (title.equals(LIST_TITLE)) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;
            if (clicked.getItemMeta().lore() == null) return;

            String cinematicId = extractIdFromLore(clicked);
            if (cinematicId == null) return;

            plugin.getCinematicManager().get(cinematicId)
                    .ifPresent(c -> openDetail(admin, c));
            return;
        }

        if (!title.startsWith(DETAIL_TITLE_PREFIX)) return;
        event.setCancelled(true);

        String cinematicId = title.substring(DETAIL_TITLE_PREFIX.length()).trim();
        Cinematic cinematic = plugin.getCinematicManager().get(cinematicId).orElse(null);

        if (cinematic == null) {
            admin.sendMessage(Component.text("✘ Cinematica no encontrada: '"
                    + cinematicId + "'.", NamedTextColor.RED));
            admin.closeInventory();
            return;
        }

        int slot = event.getRawSlot();
        int page = waypointPages.getOrDefault(admin.getUniqueId(), 0);

        switch (slot) {
            case SLOT_BACK -> openList(admin);

            case SLOT_PLAY -> {
                if (!canPlay(cinematic)) return;
                boolean ok = plugin.getCinematicManager().play(cinematic.getId());
                admin.sendMessage(ok
                        ? Component.text("▶ Reproduciendo '", NamedTextColor.GREEN)
                          .append(Component.text(cinematic.getDisplayName(),
                                  NamedTextColor.YELLOW))
                          .append(Component.text("'.", NamedTextColor.GREEN))
                        : Component.text("✘ No se pudo reproducir.", NamedTextColor.RED));
                openDetail(admin, cinematic, page);
            }

            case SLOT_STOP -> {
                if (!cinematic.isPlaying()) return;
                plugin.getCinematicManager().stop();
                admin.sendMessage(Component.text("■ Cinematica detenida.",
                        NamedTextColor.YELLOW));
                openDetail(admin, cinematic, page);
            }

            case SLOT_RECORD -> {
                if (!cinematic.isIdle()
                        || plugin.getCinematicManager().getRecorder().isRecording()) return;
                admin.closeInventory();
                plugin.getCinematicManager().getRecorder().startRecording(admin, cinematic);
            }

            case SLOT_RENAME -> {
                admin.closeInventory();
                awaitingRename.put(admin.getUniqueId(), cinematic.getId());
                admin.sendMessage(Component.text("✎ Nuevo nombre para '",
                                NamedTextColor.YELLOW)
                        .append(Component.text(cinematic.getDisplayName(),
                                NamedTextColor.WHITE))
                        .append(Component.text("':", NamedTextColor.YELLOW)));
                admin.sendMessage(Component.text(
                        "  'cancelar' para cancelar.", NamedTextColor.GRAY));
            }

            case SLOT_DELETE -> {
                admin.closeInventory();
                plugin.getConfirmGUI().open(admin,
                        "Eliminar cinematica",
                        List.of("Vas a eliminar permanentemente:",
                                "  " + cinematic.getDisplayName()
                                        + " (id: " + cinematic.getId() + ")",
                                "Waypoints: " + cinematic.getWaypoints().size()),
                        () -> {
                            plugin.getCinematicManager().delete(cinematic.getId());
                            admin.sendMessage(Component.text(
                                    "✔ Cinematica eliminada.", NamedTextColor.GREEN));
                            openList(admin);
                        });
            }

            case SLOT_PREV -> openDetail(admin, cinematic, Math.max(0, page - 1));
            case SLOT_NEXT -> openDetail(admin, cinematic, page + 1);

            default -> {
                int wpSlotOffset = slot - WAYPOINTS_START;
                if (wpSlotOffset < 0 || wpSlotOffset >= WAYPOINTS_COUNT) return;

                int wpIndex = page * WAYPOINTS_COUNT + wpSlotOffset;
                List<CinematicWaypoint> waypoints = cinematic.getWaypoints();
                if (wpIndex >= waypoints.size()) return;

                CinematicWaypoint wp = waypoints.get(wpIndex);
                admin.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () ->
                        plugin.getCinematicWaypointGUI().open(admin, cinematic, wp,
                                () -> openDetail(admin, cinematic, page)));
            }
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player admin = event.getPlayer();
        UUID uid = admin.getUniqueId();
        String cinematicId = awaitingRename.remove(uid);
        if (cinematicId == null) return;

        event.setCancelled(true);
        String msg = event.getMessage().trim();

        if (msg.equalsIgnoreCase("cancelar")) {
            admin.sendMessage(Component.text("Renombrado cancelado.", NamedTextColor.GRAY));
            return;
        }
        if (msg.length() < 2 || msg.length() > 32) {
            admin.sendMessage(Component.text(
                    "✘ Nombre inválido (2-32 caracteres).", NamedTextColor.RED));
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () ->
                plugin.getCinematicManager().get(cinematicId).ifPresent(c -> {
                    c.setDisplayName(msg);
                    c.save();
                    admin.sendMessage(Component.text("✔ Renombrada a ", NamedTextColor.GREEN)
                            .append(Component.text(msg, NamedTextColor.YELLOW)));
                    openDetail(admin, c);
                }));
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private boolean canPlay(Cinematic c) {
        return c.isIdle()
                && c.getWaypoints().size() >= 2
                && !plugin.getCinematicManager().isAnyPlaying();
    }

    private String extractIdFromLore(ItemStack item) {
        if (!item.hasItemMeta()) return null;
        List<Component> lore = item.getItemMeta().lore();
        if (lore == null) return null;
        for (Component line : lore) {
            String plain = net.kyori.adventure.text.serializer.plain
                    .PlainTextComponentSerializer.plainText().serialize(line);
            if (plain.startsWith("  ID: ")) {
                return plain.substring(6).trim();
            }
        }
        return null;
    }
}