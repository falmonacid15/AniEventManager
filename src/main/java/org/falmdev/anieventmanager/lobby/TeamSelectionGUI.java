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
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.model.EventTeam;
import org.falmdev.anieventmanager.utils.TeamUtil;
import org.falmdev.anieventmanager.utils.gui.GuiUtil;
import org.falmdev.anieventmanager.utils.gui.ItemBuilder;

import java.util.*;

/**
 * GUI que abre al hacer click en un cartel del lobby.
 *
 * Layout (27 slots = 3 filas):
 *   Fila 1: border | INFO(10) | border | RENAME?(12) | border | ADDMEMBER?(14) | border | ACTION(16) | border
 *
 * RENAME y ADDMEMBER solo aparecen si el jugador está en ESTE equipo.
 * ACTION es:
 *   - EMERALD (Unirme) si no estás en ningún equipo
 *   - RED_DYE (Salir) si estás en este equipo
 *   - BARRIER (Bloqueado) si estás en otro equipo
 */
public class TeamSelectionGUI implements Listener {

    private static final int SLOT_INFO      = 10;
    private static final int SLOT_RENAME    = 12;
    private static final int SLOT_ADDMEMBER = 14;
    private static final int SLOT_ACTION    = 16;

    public static final String GUI_TITLE_PREFIX = "Equipo: ";

    private final Anieventmanager plugin;
    private final Map<UUID, String> awaitingRename = new HashMap<>();

    public TeamSelectionGUI(Anieventmanager plugin) {
        this.plugin = plugin;
    }

    // ── Abrir el GUI ──────────────────────────────────────────────────────────

    public void open(Player player, EventTeam team) {
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text(GUI_TITLE_PREFIX, NamedTextColor.GOLD)
                        .append(Component.text(team.getDisplayName(), team.getColor())));

        boolean inThisTeam = plugin.getTeamManager().getTeamOf(player)
                .map(t -> t.getId().equals(team.getId())).orElse(false);
        boolean inAnotherTeam = plugin.getTeamManager().isInTeam(player) && !inThisTeam;

        inv.setItem(SLOT_INFO, buildInfoItem(team));

        if (inThisTeam) {
            inv.setItem(SLOT_RENAME,    buildRenameItem(team));
            inv.setItem(SLOT_ADDMEMBER, buildAddMemberItem(team));
        }

        inv.setItem(SLOT_ACTION, buildActionItem(team, inThisTeam, inAnotherTeam));

        // Rellenar slots vacíos al final, sin tocar los custom
        GuiUtil.fillEmpty(inv);

        player.openInventory(inv);
    }

    // ── Items ─────────────────────────────────────────────────────────────────

    private ItemStack buildInfoItem(EventTeam team) {
        ItemBuilder b = ItemBuilder.of(TeamUtil.colorToBannerMaterial(team.getColor()))
                .name(team.getDisplayName(), team.getColor(), TextDecoration.BOLD)
                .emptyLine()
                .lore(GuiUtil.label("Color", Component.text(
                        team.getColor().toString().toLowerCase().replace('_', ' '), team.getColor())))
                .lore(GuiUtil.label("ID", Component.text(team.getId(), NamedTextColor.WHITE)))
                .lore(GuiUtil.label("Miembros", Component.text(
                        team.getMemberCount() + "/2",
                        team.getMemberCount() >= 2 ? NamedTextColor.RED : NamedTextColor.GREEN)));

        if (!team.getMembers().isEmpty()) {
            b.emptyLine();
            b.lore(GuiUtil.noItalic(Component.text("Jugadores:", NamedTextColor.GRAY)));
            for (UUID uuid : team.getMembers()) {
                Player online = Bukkit.getPlayer(uuid);
                String name;
                NamedTextColor nameColor;
                if (online != null) {
                    name = online.getName();
                    nameColor = NamedTextColor.WHITE;
                } else {
                    name = TeamUtil.resolveMemberName(uuid);
                    nameColor = NamedTextColor.GRAY;
                }
                b.lore(GuiUtil.noItalic(Component.text("  • ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(name, nameColor))));
            }
        }

        b.emptyLine();
        b.lore(GuiUtil.noItalic(Component.text("Preview del tab:", NamedTextColor.GRAY)));
        b.lore(GuiUtil.noItalic(Component.text("  ", NamedTextColor.DARK_GRAY)
                .append(Component.text(team.getDisplayName(), team.getColor()))
                .append(Component.text(" Jugador123", NamedTextColor.WHITE))));

        return b.build();
    }

    private ItemStack buildRenameItem(EventTeam team) {
        return ItemBuilder.of(Material.NAME_TAG)
                .name("Cambiar nombre del equipo", NamedTextColor.GOLD)
                .emptyLine()
                .lore(GuiUtil.label("Actual",
                        Component.text(team.getDisplayName(), team.getColor())))
                .emptyLine()
                .lore(NamedTextColor.YELLOW,
                        "Click para escribir el nuevo",
                        "nombre en el chat.")
                .build();
    }

    private ItemStack buildAddMemberItem(EventTeam team) {
        if (team.isFull()) {
            return ItemBuilder.of(Material.BARRIER)
                    .name("Equipo lleno", NamedTextColor.RED)
                    .emptyLine()
                    .lore("El equipo ya tiene 2/2 miembros.")
                    .build();
        }
        return ItemBuilder.of(Material.PLAYER_HEAD)
                .name("Agregar miembro", NamedTextColor.GOLD)
                .emptyLine()
                .lore(NamedTextColor.YELLOW,
                        "Click para abrir el selector",
                        "de jugadores del servidor.")
                .build();
    }

    private ItemStack buildActionItem(EventTeam team, boolean inThisTeam, boolean inAnotherTeam) {
        if (inAnotherTeam) {
            return ItemBuilder.of(Material.BARRIER)
                    .name("No puedes unirte", NamedTextColor.RED, TextDecoration.BOLD)
                    .emptyLine()
                    .lore("Ya estás en otro equipo.", "Sal de él primero.")
                    .build();
        }

        if (inThisTeam) {
            return ItemBuilder.of(Material.RED_DYE)
                    .name("Salir del equipo", NamedTextColor.RED, TextDecoration.BOLD)
                    .emptyLine()
                    .lore(GuiUtil.noItalic(Component.text("Click para abandonar el equipo ", NamedTextColor.GRAY)
                            .append(Component.text(team.getDisplayName(), team.getColor()))
                            .append(Component.text(".", NamedTextColor.GRAY))))
                    .build();
        }

        ItemBuilder b = ItemBuilder.of(Material.EMERALD)
                .name("Unirme al equipo", NamedTextColor.GREEN, TextDecoration.BOLD)
                .emptyLine()
                .lore(GuiUtil.label("Equipo",
                        Component.text(team.getDisplayName(), team.getColor())))
                .emptyLine();
        if (team.isFull()) {
            b.lore(NamedTextColor.RED, "⚠ El equipo está lleno.");
        } else {
            b.lore(NamedTextColor.YELLOW, "Click para unirte.");
        }
        return b.build();
    }

    // ── Click listener ────────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = GuiUtil.getTitle(event.getView());
        if (!title.startsWith(GUI_TITLE_PREFIX)) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        String displayName = title.substring(GUI_TITLE_PREFIX.length());
        EventTeam team = TeamUtil.findByDisplayName(plugin, displayName);
        if (team == null) {
            player.sendMessage(Component.text("✘ No se pudo identificar el equipo.", NamedTextColor.RED));
            player.closeInventory();
            return;
        }

        int slot = event.getRawSlot();
        boolean inThisTeam = plugin.getTeamManager().getTeamOf(player)
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
                plugin.getPlayerSelectorGUI().open(player, team.getDisplayName(), pickedUuid ->
                        addMemberFromPicker(player, team.getId(), pickedUuid));
            }

            case SLOT_ACTION -> {
                if (inAnotherTeam) {
                    player.sendMessage(Component.text("✘ Ya estás en otro equipo.", NamedTextColor.RED));
                    return;
                }
                if (inThisTeam) {
                    plugin.getTeamManager().removeFromCurrentTeam(player);
                    plugin.getTeamLobbyManager().refreshForTeam(team.getId());
                    player.sendMessage(Component.text("✔ Saliste del equipo ", NamedTextColor.YELLOW)
                            .append(Component.text(team.getDisplayName(), team.getColor()))
                            .append(Component.text(".", NamedTextColor.YELLOW)));
                    player.closeInventory();
                    return;
                }
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

        boolean alreadyInTeam = plugin.getTeamManager().getAllTeams().stream()
                .anyMatch(t -> t.getMembers().contains(pickedUuid));
        if (alreadyInTeam) {
            String name = TeamUtil.resolveMemberName(pickedUuid);
            viewer.sendMessage(Component.text("✘ ", NamedTextColor.RED)
                    .append(Component.text(name, NamedTextColor.WHITE))
                    .append(Component.text(" ya está en un equipo.", NamedTextColor.RED)));
            return;
        }

        Player target = Bukkit.getPlayer(pickedUuid);
        boolean ok = (target != null)
                ? plugin.getTeamManager().addToTeam(teamId, target)
                : plugin.getTeamManager().addOfflineToTeam(teamId, pickedUuid);

        if (ok) {
            String name = TeamUtil.resolveMemberName(pickedUuid);
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
}