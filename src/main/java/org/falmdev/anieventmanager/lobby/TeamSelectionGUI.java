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
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.model.EventTeam;

import java.util.*;

/**
 * GUI que abre al hacer click en un cartel del lobby.
 *
 * Layout (27 slots = 3 filas):
 *   Fila 0: borders
 *   Fila 1: depende de si el jugador está o no en el equipo:
 *
 *   ┌── Sin equipo / En otro equipo ────────────────────────────────┐
 *   │ border | INFO(10) | border | (vacío) | border | (vacío) | border | CONFIRM/BARRIER(16) │
 *   └────────────────────────────────────────────────────────────────┘
 *
 *   ┌── En este equipo ──────────────────────────────────────────────┐
 *   │ border | INFO(10) | border | RENAME(12) | border | ADDMEMBER(14) | border | LEAVE(16) │
 *   └────────────────────────────────────────────────────────────────┘
 *
 *   Fila 2: borders
 *
 * El slot ADDMEMBER solo aparece si el viewer está en ESTE equipo.
 * Si NO está en ningún equipo, slot 16 es CONFIRM (esmeralda) para unirse.
 * Si está en OTRO equipo, slot 16 es BARRIER (bloqueado).
 */
public class TeamSelectionGUI implements Listener {

    private static final int SLOT_INFO      = 10;
    private static final int SLOT_RENAME    = 12;
    private static final int SLOT_ADDMEMBER = 14;
    private static final int SLOT_ACTION    = 16; // join / leave / blocked

    public static final String GUI_TITLE_PREFIX = "Equipo: ";

    private final Anieventmanager plugin;

    // Prompts pendientes (solo rename — addmember pasó al PlayerSelectorGUI)
    private final Map<UUID, String> awaitingRename = new HashMap<>();

    public TeamSelectionGUI(Anieventmanager plugin) {
        this.plugin = plugin;
    }

    // ── Abrir el GUI ──────────────────────────────────────────────────────────

    public void open(Player player, EventTeam team) {
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text(GUI_TITLE_PREFIX, NamedTextColor.GOLD)
                        .append(Component.text(team.getDisplayName(), team.getColor())));

        fillBorders(inv);

        boolean inThisTeam   = plugin.getTeamManager().getTeamOf(player)
                .map(t -> t.getId().equals(team.getId())).orElse(false);
        boolean inAnotherTeam = plugin.getTeamManager().isInTeam(player) && !inThisTeam;

        // INFO siempre visible
        inv.setItem(SLOT_INFO, buildInfoItem(team));

        // RENAME y ADDMEMBER solo si está en este equipo
        if (inThisTeam) {
            inv.setItem(SLOT_RENAME,    buildRenameItem(team));
            inv.setItem(SLOT_ADDMEMBER, buildAddMemberItem(team));
        }
        // Si no está en este equipo, esos slots quedan como border (sin item especial)

        // Slot de acción principal (16)
        inv.setItem(SLOT_ACTION, buildActionItem(team, inThisTeam, inAnotherTeam));

        player.openInventory(inv);
    }

    // ── Items ─────────────────────────────────────────────────────────────────

    private ItemStack buildInfoItem(EventTeam team) {
        ItemStack item = new ItemStack(colorToBannerMaterial(team.getColor()));
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(team.getDisplayName(), team.getColor(), TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(line("Color", Component.text(
                team.getColor().toString().toLowerCase().replace('_', ' '), team.getColor())));
        lore.add(line("ID", Component.text(team.getId(), NamedTextColor.WHITE)));
        lore.add(line("Miembros", Component.text(
                team.getMemberCount() + "/2",
                team.getMemberCount() >= 2 ? NamedTextColor.RED : NamedTextColor.GREEN)));

        if (!team.getMembers().isEmpty()) {
            lore.add(Component.empty());
            lore.add(Component.text("Jugadores:", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            for (UUID uuid : team.getMembers()) {
                Player online = Bukkit.getPlayer(uuid);
                String name;
                NamedTextColor nameColor;
                if (online != null) {
                    name = online.getName();
                    nameColor = NamedTextColor.WHITE;
                } else {
                    OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
                    name = off.getName() != null ? off.getName() : uuid.toString().substring(0, 6);
                    nameColor = NamedTextColor.GRAY;
                }
                lore.add(Component.text("  • ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(name, nameColor))
                        .decoration(TextDecoration.ITALIC, false));
            }
        }

        lore.add(Component.empty());
        lore.add(Component.text("Preview del tab:", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  ", NamedTextColor.DARK_GRAY)
                .append(Component.text(team.getDisplayName(), team.getColor()))
                .append(Component.text(" Jugador123", NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildRenameItem(EventTeam team) {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Cambiar nombre del equipo", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(line("Actual", Component.text(team.getDisplayName(), team.getColor())));
        lore.add(Component.empty());
        lore.add(Component.text("Click para escribir el nuevo", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("nombre en el chat.", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildAddMemberItem(EventTeam team) {
        ItemStack item = new ItemStack(team.isFull() ? Material.BARRIER : Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(
                        team.isFull() ? "Equipo lleno" : "Agregar miembro",
                        team.isFull() ? NamedTextColor.RED : NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        if (team.isFull()) {
            lore.add(Component.text("El equipo ya tiene 2/2 miembros.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Click para abrir el selector", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("de jugadores del servidor.", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildActionItem(EventTeam team, boolean inThisTeam, boolean inAnotherTeam) {
        if (inAnotherTeam) {
            ItemStack item = new ItemStack(Material.BARRIER);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text("No puedes unirte", NamedTextColor.RED, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.empty(),
                    Component.text("Ya estás en otro equipo.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Sal de él primero.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)));
            item.setItemMeta(meta);
            return item;
        }

        if (inThisTeam) {
            // Botón "Salir del equipo"
            ItemStack item = new ItemStack(Material.RED_DYE);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text("Salir del equipo", NamedTextColor.RED, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.empty(),
                    Component.text("Click para abandonar el equipo ", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
                            .append(Component.text(team.getDisplayName(), team.getColor()))
                            .append(Component.text(".", NamedTextColor.GRAY))));
            item.setItemMeta(meta);
            return item;
        }

        // No está en ningún equipo → unirse
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Unirme al equipo", NamedTextColor.GREEN, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(line("Equipo", Component.text(team.getDisplayName(), team.getColor())));
        lore.add(Component.empty());
        if (team.isFull()) {
            lore.add(Component.text("⚠ El equipo está lleno.", NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Click para unirte.", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void fillBorders(Inventory inv) {
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = border.getItemMeta();
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        border.setItemMeta(meta);
        for (int i = 0; i < 27; i++) inv.setItem(i, border);
        // Limpiar slots de items reales (los rellenamos después)
        inv.setItem(SLOT_INFO,      null);
        inv.setItem(SLOT_ACTION,    null);
    }

    // ── Click listener ────────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(event.getView().title());
        if (!title.startsWith(GUI_TITLE_PREFIX)) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        String displayName = title.substring(GUI_TITLE_PREFIX.length());
        EventTeam team = findTeamByDisplayName(displayName);
        if (team == null) {
            player.sendMessage(Component.text("✘ No se pudo identificar el equipo.", NamedTextColor.RED));
            player.closeInventory();
            return;
        }

        int slot = event.getRawSlot();

        boolean inThisTeam   = plugin.getTeamManager().getTeamOf(player)
                .map(t -> t.getId().equals(team.getId())).orElse(false);
        boolean inAnotherTeam = plugin.getTeamManager().isInTeam(player) && !inThisTeam;

        switch (slot) {
            case SLOT_INFO -> { /* informativo */ }

            case SLOT_RENAME -> {
                if (!inThisTeam) return;
                player.closeInventory();
                awaitingRename.put(player.getUniqueId(), team.getId());
                player.sendMessage(Component.text("✎ Escribe el nuevo nombre del equipo en el chat.", NamedTextColor.YELLOW));
                player.sendMessage(Component.text("  'cancelar' para cancelar.", NamedTextColor.GRAY));
            }

            case SLOT_ADDMEMBER -> {
                if (!inThisTeam) return;
                if (team.isFull()) {
                    player.sendMessage(Component.text("✘ El equipo ya está lleno.", NamedTextColor.RED));
                    return;
                }
                player.closeInventory();
                // Abrir selector de jugadores
                plugin.getPlayerSelectorGUI().open(player, team.getDisplayName(), pickedUuid ->
                        addMemberFromPicker(player, team.getId(), pickedUuid));
            }

            case SLOT_ACTION -> {
                if (inAnotherTeam) {
                    player.sendMessage(Component.text("✘ Ya estás en otro equipo.", NamedTextColor.RED));
                    return;
                }
                if (inThisTeam) {
                    // Salir del equipo
                    plugin.getTeamManager().removeFromCurrentTeam(player);
                    plugin.getTeamLobbyManager().refreshForTeam(team.getId());
                    player.sendMessage(Component.text("✔ Saliste del equipo ", NamedTextColor.YELLOW)
                            .append(Component.text(team.getDisplayName(), team.getColor()))
                            .append(Component.text(".", NamedTextColor.YELLOW)));
                    player.closeInventory();
                    return;
                }
                // Unirse
                if (team.isFull()) {
                    player.sendMessage(Component.text("✘ El equipo está lleno.", NamedTextColor.RED));
                    return;
                }
                boolean ok = plugin.getTeamManager().addToTeam(team.getId(), player);
                if (ok) {
                    player.sendMessage(Component.text("✔ Te uniste al equipo ", NamedTextColor.GREEN)
                            .append(Component.text(team.getDisplayName(), team.getColor())));
                    plugin.getTeamLobbyManager().refreshForTeam(team.getId());
                    player.closeInventory();
                } else {
                    player.sendMessage(Component.text("✘ No se pudo unir al equipo.", NamedTextColor.RED));
                }
            }
        }
    }

    // ── Callback del PlayerSelectorGUI ────────────────────────────────────────

    private void addMemberFromPicker(Player viewer, String teamId, UUID pickedUuid) {
        var teamOpt = plugin.getTeamManager().getTeam(teamId);
        if (teamOpt.isEmpty()) {
            viewer.sendMessage(Component.text("✘ El equipo ya no existe.", NamedTextColor.RED));
            return;
        }
        EventTeam team = teamOpt.get();

        if (team.isFull()) {
            viewer.sendMessage(Component.text("✘ El equipo se llenó mientras elegías.", NamedTextColor.RED));
            return;
        }

        // Verificar que el target no esté en otro equipo
        boolean alreadyInTeam = plugin.getTeamManager().getAllTeams().stream()
                .anyMatch(t -> t.getMembers().contains(pickedUuid));
        if (alreadyInTeam) {
            OfflinePlayer off = Bukkit.getOfflinePlayer(pickedUuid);
            String name = off.getName() != null ? off.getName() : "ese jugador";
            viewer.sendMessage(Component.text("✘ ", NamedTextColor.RED)
                    .append(Component.text(name, NamedTextColor.WHITE))
                    .append(Component.text(" ya está en un equipo.", NamedTextColor.RED)));
            return;
        }

        // Agregar — soportamos jugadores offline ya que solo guardamos UUIDs
        Player target = Bukkit.getPlayer(pickedUuid);
        boolean ok;
        if (target != null) {
            ok = plugin.getTeamManager().addToTeam(teamId, target);
        } else {
            ok = plugin.getTeamManager().addOfflineToTeam(teamId, pickedUuid);
        }

        if (ok) {
            OfflinePlayer off = Bukkit.getOfflinePlayer(pickedUuid);
            String name = off.getName() != null ? off.getName() : pickedUuid.toString().substring(0, 6);
            viewer.sendMessage(Component.text("✔ ", NamedTextColor.GREEN)
                    .append(Component.text(name, NamedTextColor.WHITE))
                    .append(Component.text(" agregado al equipo ", NamedTextColor.GREEN))
                    .append(Component.text(team.getDisplayName(), team.getColor())));
            if (target != null && target.isOnline()) {
                target.sendMessage(Component.text("✔ Te agregaron al equipo ", NamedTextColor.GREEN)
                        .append(Component.text(team.getDisplayName(), team.getColor())));
            }
            plugin.getTeamLobbyManager().refreshForTeam(teamId);
        } else {
            viewer.sendMessage(Component.text("✘ No se pudo agregar.", NamedTextColor.RED));
        }
    }

    // ── Chat prompt (rename) ──────────────────────────────────────────────────

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();
        if (!awaitingRename.containsKey(uid)) return;

        event.setCancelled(true);
        String teamId = awaitingRename.remove(uid);
        String msg = event.getMessage().trim();

        if (msg.equalsIgnoreCase("cancelar")) {
            player.sendMessage(Component.text("Renombrado cancelado.", NamedTextColor.GRAY));
            return;
        }
        if (msg.length() > 24) {
            player.sendMessage(Component.text("✘ Nombre demasiado largo (máx 24).", NamedTextColor.RED));
            return;
        }
        if (msg.length() < 2) {
            player.sendMessage(Component.text("✘ Nombre demasiado corto (mín 2).", NamedTextColor.RED));
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            var teamOpt = plugin.getTeamManager().getTeam(teamId);
            if (teamOpt.isEmpty()) {
                player.sendMessage(Component.text("✘ El equipo ya no existe.", NamedTextColor.RED));
                return;
            }
            EventTeam team = teamOpt.get();
            plugin.getTeamManager().renameTeam(team.getId(), msg);
            player.sendMessage(Component.text("✔ Nombre cambiado a ", NamedTextColor.GREEN)
                    .append(Component.text(msg, team.getColor())));
            plugin.getTeamLobbyManager().refreshForTeam(team.getId());
        });
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private EventTeam findTeamByDisplayName(String displayName) {
        for (EventTeam team : plugin.getTeamManager().getAllTeams()) {
            if (team.getDisplayName().equals(displayName)) return team;
        }
        return null;
    }

    private Component line(String label, Component value) {
        return Component.text("  " + label + ": ", NamedTextColor.GRAY)
                .append(value)
                .decoration(TextDecoration.ITALIC, false);
    }

    private Material colorToBannerMaterial(NamedTextColor color) {
        if (color == NamedTextColor.RED)          return Material.RED_BANNER;
        if (color == NamedTextColor.BLUE)         return Material.BLUE_BANNER;
        if (color == NamedTextColor.GREEN)        return Material.LIME_BANNER;
        if (color == NamedTextColor.YELLOW)       return Material.YELLOW_BANNER;
        if (color == NamedTextColor.LIGHT_PURPLE) return Material.MAGENTA_BANNER;
        if (color == NamedTextColor.AQUA)         return Material.LIGHT_BLUE_BANNER;
        if (color == NamedTextColor.GOLD)         return Material.ORANGE_BANNER;
        return Material.WHITE_BANNER;
    }
}