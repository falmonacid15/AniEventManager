package org.falmdev.anieventmanager.lobby;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.utils.gui.GuiUtil;
import org.falmdev.anieventmanager.utils.gui.HeadUtil;
import org.falmdev.anieventmanager.utils.gui.ItemBuilder;

import java.util.*;
import java.util.function.Consumer;

/**
 * GUI reusable para elegir un jugador de una lista paginada que incluye
 * online + offline (los que alguna vez se conectaron al servidor).
 *
 * Uso:
 *   plugin.getPlayerSelectorGUI().open(viewer, "Título", uuid -> { ... });
 *
 * Layout (54 slots):
 *   Filas 0..4 → cabezas (45 slots)
 *   Fila 5     → controles: ← anterior (45), cancelar (49), → siguiente (53)
 */
public class PlayerSelectorGUI implements Listener {

    private static final int PAGE_SIZE = 45;
    public  static final String TITLE_PREFIX = "Elegir jugador: ";

    private final Anieventmanager plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public PlayerSelectorGUI(Anieventmanager plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer, String context, Consumer<UUID> onPick) {
        Session session = new Session(context, onPick, 0, buildSortedPlayerList());
        sessions.put(viewer.getUniqueId(), session);
        renderPage(viewer, session);
    }

    private void renderPage(Player viewer, Session session) {
        int total = session.allPlayers.size();
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) PAGE_SIZE));
        int page = Math.max(0, Math.min(session.page, totalPages - 1));
        session.page = page;

        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text(TITLE_PREFIX, NamedTextColor.GOLD)
                        .append(Component.text(session.context, NamedTextColor.YELLOW))
                        .append(Component.text("  (" + (page + 1) + "/" + totalPages + ")",
                                NamedTextColor.DARK_GRAY)));

        int start = page * PAGE_SIZE;
        int end   = Math.min(total, start + PAGE_SIZE);
        for (int i = start; i < end; i++) {
            inv.setItem(i - start, buildPlayerHead(session.allPlayers.get(i)));
        }

        // Controles
        inv.setItem(45, page > 0
                ? ItemBuilder.of(Material.ARROW).name("← Página anterior", NamedTextColor.YELLOW).build()
                : disabledArrow("Ya estás en la primera página"));

        inv.setItem(49, ItemBuilder.of(Material.BARRIER).name("✘ Cancelar", NamedTextColor.RED).build());

        inv.setItem(53, page < totalPages - 1
                ? ItemBuilder.of(Material.ARROW).name("Página siguiente →", NamedTextColor.YELLOW).build()
                : disabledArrow("Ya estás en la última página"));

        // Rellenar slots vacíos de la barra de control sin tocar los botones
        GuiUtil.fillEmpty(inv);

        viewer.openInventory(inv);
    }

    private ItemStack buildPlayerHead(OfflinePlayer p) {
        String name = p.getName() != null ? p.getName() : p.getUniqueId().toString().substring(0, 8);
        boolean online = p.isOnline();

        ItemBuilder b = ItemBuilder.of(HeadUtil.fromPlayer(p));

        // Nombre con color y bold condicional según estado online
        Component nameComponent = Component.text(name)
                .color(online ? NamedTextColor.WHITE : NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false);
        if (online) nameComponent = nameComponent.decoration(TextDecoration.BOLD, true);
        b.name(nameComponent);

        b.emptyLine();
        b.lore(GuiUtil.noItalic(Component.text("Estado: ", NamedTextColor.GRAY)
                .append(online
                        ? Component.text("● online", NamedTextColor.GREEN)
                        : Component.text("○ offline", NamedTextColor.DARK_GRAY))));

        // Equipo actual si tiene
        plugin.getTeamManager().getAllTeams().stream()
                .filter(t -> t.getMembers().contains(p.getUniqueId()))
                .findFirst()
                .ifPresent(team -> b.lore(GuiUtil.noItalic(
                        Component.text("Equipo actual: ", NamedTextColor.GRAY)
                                .append(Component.text(team.getDisplayName(), team.getColor())))));

        b.emptyLine();
        b.lore(NamedTextColor.YELLOW, "Click para seleccionar.");

        return b.build();
    }

    private ItemStack disabledArrow(String reason) {
        return ItemBuilder.of(Material.GRAY_DYE)
                .name("—", NamedTextColor.DARK_GRAY)
                .lore(NamedTextColor.DARK_GRAY, reason)
                .build();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        String title = GuiUtil.getTitle(event.getView());
        if (!title.startsWith(TITLE_PREFIX)) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player viewer)) return;

        Session session = sessions.get(viewer.getUniqueId());
        if (session == null) {
            viewer.closeInventory();
            return;
        }

        int slot = event.getRawSlot();

        if (slot == 45) { session.page--; renderPage(viewer, session); return; }
        if (slot == 53) { session.page++; renderPage(viewer, session); return; }
        if (slot == 49) {
            sessions.remove(viewer.getUniqueId());
            viewer.closeInventory();
            return;
        }

        if (slot < 0 || slot >= PAGE_SIZE) return;
        int index = session.page * PAGE_SIZE + slot;
        if (index >= session.allPlayers.size()) return;

        OfflinePlayer picked = session.allPlayers.get(index);
        Consumer<UUID> callback = session.onPick;
        sessions.remove(viewer.getUniqueId());
        viewer.closeInventory();

        Bukkit.getScheduler().runTask(plugin, () -> callback.accept(picked.getUniqueId()));
    }

    private List<OfflinePlayer> buildSortedPlayerList() {
        Set<UUID> seen = new HashSet<>();
        List<OfflinePlayer> list = new ArrayList<>();

        for (OfflinePlayer p : Bukkit.getOfflinePlayers()) {
            if (p.getName() == null) continue;
            if (seen.add(p.getUniqueId())) list.add(p);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (seen.add(p.getUniqueId())) list.add(p);
        }

        list.sort((a, b) -> {
            String na = a.getName() != null ? a.getName() : "";
            String nb = b.getName() != null ? b.getName() : "";
            return na.compareToIgnoreCase(nb);
        });

        return list;
    }

    private static class Session {
        final String context;
        final Consumer<UUID> onPick;
        int page;
        final List<OfflinePlayer> allPlayers;

        Session(String context, Consumer<UUID> onPick, int page, List<OfflinePlayer> allPlayers) {
            this.context = context;
            this.onPick = onPick;
            this.page = page;
            this.allPlayers = allPlayers;
        }
    }
}