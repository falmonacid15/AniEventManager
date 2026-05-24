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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.falmdev.anieventmanager.Anieventmanager;

import java.util.*;
import java.util.function.Consumer;

/**
 * GUI reusable para elegir un jugador de una lista paginada que incluye
 * todos los jugadores online y offline (los que alguna vez se conectaron al
 * servidor, según el usercache).
 *
 * Uso:
 *   plugin.getPlayerSelectorGUI().open(viewer, "Título", uuid -> { ... });
 *
 * Layout (54 slots):
 *   Filas 0..4 → cabezas de jugadores (45 slots)
 *   Fila 5     → controles:
 *     slot 45 ← anterior
 *     slot 49 cancelar
 *     slot 53 → siguiente
 *
 * La sesión guarda la página actual y el callback, indexados por viewer UUID.
 */
public class PlayerSelectorGUI implements Listener {

    private static final int PAGE_SIZE = 45;
    public  static final String TITLE_PREFIX = "Elegir jugador: ";

    private final Anieventmanager plugin;

    /** Estado por viewer: callback + página actual + título completo. */
    private final Map<UUID, Session> sessions = new HashMap<>();

    public PlayerSelectorGUI(Anieventmanager plugin) {
        this.plugin = plugin;
    }

    // ── API pública ───────────────────────────────────────────────────────────

    /**
     * Abre el selector para un jugador.
     *
     * @param viewer    quien ve la GUI
     * @param context   texto corto que aparece en el título (ej: nombre del equipo)
     * @param onPick    callback con el UUID elegido. Si el viewer cancela, no se llama.
     */
    public void open(Player viewer, String context, Consumer<UUID> onPick) {
        Session session = new Session(context, onPick, 0, buildSortedPlayerList());
        sessions.put(viewer.getUniqueId(), session);
        renderPage(viewer, session);
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

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

        // Cabezas
        int start = page * PAGE_SIZE;
        int end   = Math.min(total, start + PAGE_SIZE);
        for (int i = start; i < end; i++) {
            OfflinePlayer p = session.allPlayers.get(i);
            inv.setItem(i - start, buildPlayerHead(p));
        }

        // Controles
        inv.setItem(45, page > 0
                ? button(Material.ARROW, "← Página anterior", NamedTextColor.YELLOW)
                : disabledArrow("Ya estás en la primera página"));

        inv.setItem(49, button(Material.BARRIER, "✘ Cancelar", NamedTextColor.RED));

        inv.setItem(53, page < totalPages - 1
                ? button(Material.ARROW, "Página siguiente →", NamedTextColor.YELLOW)
                : disabledArrow("Ya estás en la última página"));

        // Slots de relleno en la barra de control
        ItemStack border = button(Material.BLACK_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY);
        for (int i = 46; i < 53; i++) if (inv.getItem(i) == null) inv.setItem(i, border);

        viewer.openInventory(inv);
    }

    private ItemStack buildPlayerHead(OfflinePlayer p) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(p);

        String name = p.getName() != null ? p.getName()
                : p.getUniqueId().toString().substring(0, 8);

        boolean online = p.isOnline();
        Component nameComponent = Component.text(name)
                .color(online ? NamedTextColor.WHITE : NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false);
        if (online) nameComponent = nameComponent.decoration(TextDecoration.BOLD, true);
        meta.displayName(nameComponent);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());

        // Estado online/offline
        lore.add(Component.text("Estado: ", NamedTextColor.GRAY)
                .append(online
                        ? Component.text("● online", NamedTextColor.GREEN)
                        : Component.text("○ offline", NamedTextColor.DARK_GRAY))
                .decoration(TextDecoration.ITALIC, false));

        // Equipo actual si tiene
        if (online) {
            Player pl = p.getPlayer();
            if (pl != null) {
                plugin.getTeamManager().getTeamOf(pl).ifPresent(team ->
                        lore.add(Component.text("Equipo actual: ", NamedTextColor.GRAY)
                                .append(Component.text(team.getDisplayName(), team.getColor()))
                                .decoration(TextDecoration.ITALIC, false)));
            }
        } else {
            // Buscar en los datos persistidos si está en algún equipo
            plugin.getTeamManager().getAllTeams().stream()
                    .filter(t -> t.getMembers().contains(p.getUniqueId()))
                    .findFirst()
                    .ifPresent(team ->
                            lore.add(Component.text("Equipo actual: ", NamedTextColor.GRAY)
                                    .append(Component.text(team.getDisplayName(), team.getColor()))
                                    .decoration(TextDecoration.ITALIC, false)));
        }

        lore.add(Component.empty());
        lore.add(Component.text("Click para seleccionar.", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack button(Material mat, String text, NamedTextColor color) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(text, color).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack disabledArrow(String reason) {
        ItemStack item = new ItemStack(Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("—", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text(reason, NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    // ── Listener ──────────────────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(event.getView().title());
        if (!title.startsWith(TITLE_PREFIX)) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player viewer)) return;

        Session session = sessions.get(viewer.getUniqueId());
        if (session == null) {
            viewer.closeInventory();
            return;
        }

        int slot = event.getRawSlot();

        // Controles
        if (slot == 45) { session.page--; renderPage(viewer, session); return; }
        if (slot == 53) { session.page++; renderPage(viewer, session); return; }
        if (slot == 49) {
            sessions.remove(viewer.getUniqueId());
            viewer.closeInventory();
            return;
        }

        // Slot de jugador
        if (slot < 0 || slot >= PAGE_SIZE) return;
        int index = session.page * PAGE_SIZE + slot;
        if (index >= session.allPlayers.size()) return;

        OfflinePlayer picked = session.allPlayers.get(index);
        Consumer<UUID> callback = session.onPick;
        sessions.remove(viewer.getUniqueId());
        viewer.closeInventory();

        // Ejecutar el callback en el tick siguiente para evitar problemas
        // de abrir otra GUI dentro del InventoryClickEvent
        Bukkit.getScheduler().runTask(plugin, () -> callback.accept(picked.getUniqueId()));
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private List<OfflinePlayer> buildSortedPlayerList() {
        // OfflinePlayer[] incluye todos los que alguna vez se conectaron
        // (según el usercache de Paper/Spigot).
        Set<UUID> seen = new HashSet<>();
        List<OfflinePlayer> list = new ArrayList<>();

        for (OfflinePlayer p : Bukkit.getOfflinePlayers()) {
            if (p.getName() == null) continue;       // entries corruptas
            if (seen.add(p.getUniqueId())) list.add(p);
        }
        // Asegurar que los online estén incluidos (a veces no aparecen en el cache hasta el primer save)
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (seen.add(p.getUniqueId())) list.add(p);
        }

        // Orden alfabético, online primero solo en empate de nombre (raro)
        list.sort((a, b) -> {
            String na = a.getName() != null ? a.getName() : "";
            String nb = b.getName() != null ? b.getName() : "";
            return na.compareToIgnoreCase(nb);
        });

        return list;
    }

    // ── Estado de sesión ──────────────────────────────────────────────────────

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