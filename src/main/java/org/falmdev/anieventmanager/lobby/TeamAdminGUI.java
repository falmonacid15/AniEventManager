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
import org.bukkit.inventory.meta.SkullMeta;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.model.EventTeam;
import org.falmdev.anieventmanager.utils.TeamUtil;
import org.falmdev.anieventmanager.utils.gui.GuiUtil;
import org.falmdev.anieventmanager.utils.gui.HeadUtil;
import org.falmdev.anieventmanager.utils.gui.ItemBuilder;

import java.util.*;

public class TeamAdminGUI implements Listener {

    public static final String LIST_TITLE          = "Admin: Equipos";
    public static final String DETAIL_TITLE_PREFIX = "Admin · ";

    // ── Slots de contenido ────────────────────────────────────────────────────
    private static final int SLOT_INFO          = 10;
    private static final int SLOT_RENAME        = 11;
    private static final int SLOT_DELETE        = 19;  // movido de 53 → 17 para liberar fila 5

    private static final int SLOT_SCORE_INFO    = 14;
    private static final int SLOT_SCORE_ADD     = 15;
    private static final int SLOT_SCORE_REMOVE  = 16;
    private static final int SLOT_SCORE_SET     = 24;
    private static final int SLOT_SCORE_RESET   = 25;

    private static final int SLOT_MEMBER_1      = 30;
    private static final int SLOT_FRIENDLY_FIRE = 31;
    private static final int SLOT_MEMBER_2      = 32;

    private static final int SLOT_ADD_MEMBER    = 40;

    // ── Fila 5 — navegación ───────────────────────────────────────────────────
    // GuiUtil.fillNavigation → 48=Volver a lista, 50=⌂Inicio
    // SLOT_BACK eliminado (lo maneja GuiUtil en slot 48)

    private static final String SKULL_SCORE_ADD    = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWZmMzE0MzFkNjQ1ODdmZjZlZjk4YzA2NzU4MTA2ODFmOGMxM2JmOTZmNTFkOWNiMDdlZDc4NTJiMmZmZDEifX19";
    private static final String SKULL_SCORE_REMOVE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGU0YjhiOGQyMzYyYzg2NGUwNjIzMDE0ODdkOTRkMzI3MmE2YjU3MGFmYmY4MGMyYzViMTQ4Yzk1NDU3OWQ0NiJ9fX0=";
    private static final String SKULL_INFO         = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGM0ZTQ0MWVhYzg4NGRlMzM0N2E4Nzc1YTA3YTY2YmJjNGM4MmEyNGVkMmQwY2ZlYjFhY2FmNmNlOTlkNTNiNiJ9fX0=";
    private static final String SKULL_DELETE       = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjEyZjNlZmU4NGEwZjY2NDZhODBkNDVjZWZlNDE4ZTE5OWQ5NjE5ZjhjMWZiNWY1YzVjMDA4YzYwMzA1OWFjMyJ9fX0=";

    private final Anieventmanager plugin;
    private final Map<UUID, PendingPrompt> awaiting = new HashMap<>();

    private enum PromptType { RENAME, SCORE_ADD, SCORE_REMOVE, SCORE_SET }
    private record PendingPrompt(String teamId, PromptType type) {}

    public TeamAdminGUI(Anieventmanager plugin) { this.plugin = plugin; }

    // ── Vista 1: lista ─────────────────────────────────────────────────────────
    // Tamaño variable → sin navegación estándar

    public void openList(Player admin) {
        Collection<EventTeam> teams = plugin.getTeamManager().getAllTeams();
        int size = 54;
        Inventory inv = Bukkit.createInventory(null, size, Component.text(LIST_TITLE, NamedTextColor.GOLD));
        int slot = 0;
        for (EventTeam team : teams) {
            if (slot >= size) break;
            inv.setItem(slot++, buildTeamBanner(team));
        }
        GuiUtil.fillNavigation(inv, true, true);
        admin.openInventory(inv);
    }

    private ItemStack buildTeamBanner(EventTeam team) {
        ItemBuilder b = ItemBuilder.of(TeamUtil.colorToBannerMaterial(team.getColor()))
                .name(team.getDisplayName(), team.getColor(), TextDecoration.BOLD).emptyLine()
                .lore(GuiUtil.label("ID", Component.text(team.getId(), NamedTextColor.WHITE)));
        b.lore(GuiUtil.noItalic(Component.text("Miembros:", NamedTextColor.GRAY)));
        if (team.getMembers().isEmpty()) {
            b.lore(NamedTextColor.DARK_GRAY, "  (sin miembros)");
        } else {
            for (UUID uuid : team.getMembers()) {
                OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
                String name = TeamUtil.resolveMemberName(uuid);
                b.lore(GuiUtil.noItalic(Component.text("  ", NamedTextColor.GRAY)
                        .append(Component.text(off.isOnline() ? "● " : "○ ", off.isOnline() ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY))
                        .append(Component.text(name, NamedTextColor.WHITE))));
            }
        }
        b.lore(GuiUtil.label("Puntos", Component.text(plugin.getScoreManager().getScore(team), NamedTextColor.YELLOW)));
        b.emptyLine().lore(NamedTextColor.YELLOW, "Click para abrir el detalle.");
        return b.build();
    }

    // ── Vista 2: detalle ───────────────────────────────────────────────────────
    // 54 slots → navegación: 48=Volver a lista, 50=⌂Inicio

    public void openDetail(Player admin, EventTeam team) {
        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text(DETAIL_TITLE_PREFIX, NamedTextColor.GOLD)
                        .append(Component.text(team.getDisplayName(), team.getColor())));
        GuiUtil.fillSlots(inv, GuiUtil.emptyPane(), 0,1,9,7,8,17,36,45,46,52,53,44);

        // Fila 1
        inv.setItem(SLOT_INFO,         buildTeamInfoItem(team));
        inv.setItem(SLOT_RENAME,       buildRenameButton());
        inv.setItem(SLOT_SCORE_INFO,   buildScoreInfoItem(team));
        inv.setItem(SLOT_SCORE_ADD,    buildScoreAddButton());
        inv.setItem(SLOT_SCORE_REMOVE, buildScoreRemoveButton());
        inv.setItem(SLOT_DELETE,       buildDeleteButton());

        // Fila 2: set / reset score
        inv.setItem(SLOT_SCORE_SET,   GuiUtil.simpleButton(Material.WRITABLE_BOOK, "= Setear puntaje", NamedTextColor.AQUA, "Click para escribir el puntaje exacto."));
        inv.setItem(SLOT_SCORE_RESET, GuiUtil.simpleButton(Material.BARRIER, "↺ Resetear a 0", NamedTextColor.RED, "Click para resetear el puntaje (pide confirmación)."));

        // Fila 3: miembros + friendly fire
        List<UUID> members = new ArrayList<>(team.getMembers());
        inv.setItem(SLOT_MEMBER_1,      members.size() > 0 ? buildMemberHead(members.get(0)) : buildEmptyMemberSlot());
        inv.setItem(SLOT_FRIENDLY_FIRE, buildFriendlyFireItem());
        inv.setItem(SLOT_MEMBER_2,      members.size() > 1 ? buildMemberHead(members.get(1)) : buildEmptyMemberSlot());

        // Fila 4: agregar miembro
        inv.setItem(SLOT_ADD_MEMBER, buildAddMemberButton(team));

        // ── Fila 5: navegación completa (Volver + Inicio) ──────────────────────
        GuiUtil.fillNavigation(inv, true, true); // 48=← Volver a lista, 50=⌂ Inicio

        admin.openInventory(inv);
    }

    // ── Construcción de items ──────────────────────────────────────────────────

    private ItemStack buildTeamInfoItem(EventTeam team) {
        return ItemBuilder.of(HeadUtil.fromBase64(SKULL_INFO))
                .name(team.getDisplayName(), team.getColor(), TextDecoration.BOLD).emptyLine()
                .lore(GuiUtil.label("ID",     Component.text(team.getId(), NamedTextColor.WHITE)))
                .lore(GuiUtil.label("Color",  Component.text(team.getColor().toString().toLowerCase().replace('_', ' '), team.getColor())))
                .lore(GuiUtil.label("Miembros", Component.text(team.getMemberCount() + "/2", team.getMemberCount() >= 2 ? NamedTextColor.RED : NamedTextColor.GREEN))).build();
    }

    private ItemStack buildRenameButton() {
        return GuiUtil.simpleButton(Material.NAME_TAG, "Cambiar nombre", NamedTextColor.GOLD, "Click para escribir el nuevo nombre en el chat.");
    }

    private ItemStack buildScoreAddButton() {
        return ItemBuilder.of(HeadUtil.fromBase64(SKULL_SCORE_ADD)).name("+ Agregar puntos", NamedTextColor.GREEN, TextDecoration.BOLD).emptyLine()
                .lore(NamedTextColor.YELLOW, "Click para escribir la cantidad de puntos a sumar.").build();
    }

    private ItemStack buildScoreRemoveButton() {
        return ItemBuilder.of(HeadUtil.fromBase64(SKULL_SCORE_REMOVE)).name("- Restar puntos", NamedTextColor.GOLD, TextDecoration.BOLD).emptyLine()
                .lore(NamedTextColor.YELLOW, "Click para escribir la cantidad de puntos a restar.").build();
    }

    private ItemStack buildDeleteButton() {
        return ItemBuilder.of(HeadUtil.fromBase64(SKULL_DELETE)).name("Eliminar equipo", NamedTextColor.RED, TextDecoration.BOLD).emptyLine()
                .lore(NamedTextColor.DARK_RED, "¡Esta acción es irreversible!").lore(NamedTextColor.YELLOW, "Click para confirmar.").build();
    }

    private ItemStack buildScoreInfoItem(EventTeam team) {
        int score = plugin.getScoreManager().getScore(team);
        int rank  = plugin.getScoreManager().getRank(team);
        return ItemBuilder.of(Material.GOLD_INGOT).name("Puntaje actual", NamedTextColor.YELLOW, TextDecoration.BOLD).emptyLine()
                .lore(GuiUtil.label("Puntos",   Component.text(score, NamedTextColor.GOLD, TextDecoration.BOLD)))
                .lore(GuiUtil.label("Posición", Component.text(rank == -1 ? "-" : "#" + rank, NamedTextColor.WHITE))).build();
    }

    private ItemStack buildMemberHead(UUID uuid) {
        OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
        String name = TeamUtil.resolveMemberName(uuid);
        return ItemBuilder.of(HeadUtil.fromPlayer(off)).name(name, NamedTextColor.WHITE).emptyLine()
                .lore(GuiUtil.noItalic(Component.text("Estado: ", NamedTextColor.GRAY).append(off.isOnline() ? Component.text("● online", NamedTextColor.GREEN) : Component.text("○ offline", NamedTextColor.DARK_GRAY))))
                .lore(NamedTextColor.DARK_GRAY, "UUID: " + uuid.toString().substring(0, 8) + "…").emptyLine()
                .lore(NamedTextColor.RED, "Click para quitar del equipo.").lore(NamedTextColor.DARK_GRAY, "(pide confirmación)").build();
    }

    private ItemStack buildEmptyMemberSlot() {
        return ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name("Slot vacío", NamedTextColor.DARK_GRAY).build();
    }

    private ItemStack buildFriendlyFireItem() {
        boolean enabled = plugin.getTeamManager().isFriendlyFireEnabled();
        return ItemBuilder.of(enabled ? Material.DIAMOND_SWORD : Material.SHIELD)
                .name("⚔ Friendly Fire", NamedTextColor.RED, TextDecoration.BOLD).emptyLine()
                .lore(GuiUtil.noItalic(Component.text("Estado: ", NamedTextColor.GRAY)
                        .append(Component.text(enabled ? "ACTIVADO" : "DESACTIVADO", enabled ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD))))
                .emptyLine().lore(NamedTextColor.YELLOW, "Click para cambiar estado.").build();
    }

    private ItemStack buildAddMemberButton(EventTeam team) {
        if (team.isFull()) return ItemBuilder.of(Material.BARRIER).name("Equipo lleno", NamedTextColor.RED, TextDecoration.BOLD).emptyLine().lore("El equipo ya tiene 2/2 miembros.").build();
        return ItemBuilder.of(Material.LIME_DYE).name("+ Agregar miembro", NamedTextColor.GREEN, TextDecoration.BOLD).emptyLine()
                .lore(NamedTextColor.YELLOW, "Click para abrir el selector", "de jugadores (online + offline).").build();
    }

    // ── Click listener ─────────────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player admin)) return;
        String title = GuiUtil.getTitle(event.getView());

        if (!admin.isOp()) {
            if (title.equals(LIST_TITLE) || title.startsWith(DETAIL_TITLE_PREFIX)) event.setCancelled(true);
            return;
        }

        // ── Vista lista ───────────────────────────────────────────────────────
        if (title.equals(LIST_TITLE)) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getItemMeta() == null || clicked.getItemMeta().displayName() == null) return;
            String displayName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(clicked.getItemMeta().displayName());
            EventTeam team = TeamUtil.findByDisplayName(plugin, displayName);
            if (team != null) openDetail(admin, team);
            return;
        }

        // ── Vista detalle ─────────────────────────────────────────────────────
        if (!title.startsWith(DETAIL_TITLE_PREFIX)) return;
        event.setCancelled(true);

        String displayName = title.substring(DETAIL_TITLE_PREFIX.length());
        EventTeam team = TeamUtil.findByDisplayName(plugin, displayName);
        if (team == null) { admin.sendMessage(Component.text("✘ Equipo no encontrado.", NamedTextColor.RED)); admin.closeInventory(); return; }

        int slot = event.getRawSlot();

        // Navegación: 48=Volver a lista, 50=Inicio al hub
        if (GuiUtil.handleNavigation(slot, admin, plugin, () -> openList(admin))) return;

        switch (slot) {
            case SLOT_RENAME -> {
                admin.closeInventory();
                awaiting.put(admin.getUniqueId(), new PendingPrompt(team.getId(), PromptType.RENAME));
                admin.sendMessage(Component.text("✎ Escribe el nuevo nombre del equipo ", NamedTextColor.YELLOW)
                        .append(Component.text(team.getDisplayName(), team.getColor())).append(Component.text(":", NamedTextColor.YELLOW)));
                admin.sendMessage(Component.text("  'cancelar' para cancelar.", NamedTextColor.GRAY));
            }
            case SLOT_DELETE -> {
                admin.closeInventory();
                plugin.getConfirmGUI().open(admin, "Eliminar equipo",
                        List.of("Vas a eliminar permanentemente el equipo:", "  " + team.getDisplayName() + "  (id: " + team.getId() + ")",
                                "Miembros: " + team.getMemberCount() + "/2", "Puntos: " + plugin.getScoreManager().getScore(team)),
                        () -> { plugin.getTeamManager().deleteTeam(team.getId()); plugin.getTeamLobbyManager().refreshAll(); admin.sendMessage(Component.text("✔ Equipo eliminado.", NamedTextColor.GREEN)); openList(admin); });
            }
            case SLOT_FRIENDLY_FIRE -> {
                boolean newState = !plugin.getTeamManager().isFriendlyFireEnabled();
                plugin.getTeamManager().setFriendlyFire(newState);
                admin.sendMessage(Component.text("⚔ Friendly Fire ", NamedTextColor.GRAY).append(Component.text(newState ? "activado" : "desactivado", newState ? NamedTextColor.GREEN : NamedTextColor.RED)));
                openDetail(admin, team);
            }
            case SLOT_SCORE_ADD    -> startScorePrompt(admin, team, PromptType.SCORE_ADD,    "SUMAR",              NamedTextColor.GREEN);
            case SLOT_SCORE_REMOVE -> startScorePrompt(admin, team, PromptType.SCORE_REMOVE, "RESTAR",             NamedTextColor.GOLD);
            case SLOT_SCORE_SET    -> startScorePrompt(admin, team, PromptType.SCORE_SET,    "SETEAR exactamente", NamedTextColor.AQUA);
            case SLOT_SCORE_RESET  -> {
                admin.closeInventory();
                int currentScore = plugin.getScoreManager().getScore(team);
                plugin.getConfirmGUI().open(admin, "Resetear puntaje",
                        List.of("Vas a resetear el puntaje del equipo:", "  " + team.getDisplayName(), "Puntos actuales: " + currentScore + " → 0"),
                        () -> { plugin.getScoreManager().resetScore(team); admin.sendMessage(Component.text("✔ Puntaje reseteado a 0.", NamedTextColor.GREEN)); openDetail(admin, team); });
            }
            case SLOT_MEMBER_1, SLOT_MEMBER_2 -> handleMemberClick(event, admin, team);
            case SLOT_ADD_MEMBER -> {
                if (team.isFull()) return;
                admin.closeInventory();
                plugin.getPlayerSelectorGUI().open(admin, team.getDisplayName(), pickedUuid -> adminAddMember(admin, team.getId(), pickedUuid));
            }
        }
    }

    private void startScorePrompt(Player admin, EventTeam team, PromptType type, String label, NamedTextColor color) {
        admin.closeInventory();
        awaiting.put(admin.getUniqueId(), new PendingPrompt(team.getId(), type));
        admin.sendMessage(Component.text("✎ Escribe la cantidad de puntos a " + label + ":", color));
        admin.sendMessage(Component.text("  'cancelar' para cancelar.", NamedTextColor.GRAY));
    }

    private void handleMemberClick(InventoryClickEvent event, Player admin, EventTeam team) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) return;
        if (!(clicked.getItemMeta() instanceof SkullMeta skull)) return;
        OfflinePlayer owner = skull.getOwningPlayer();
        if (owner == null) return;
        String memberName = owner.getName() != null ? owner.getName() : "jugador";
        UUID memberUuid = owner.getUniqueId();
        admin.closeInventory();
        plugin.getConfirmGUI().open(admin, "Quitar miembro",
                List.of("Vas a quitar a", "  " + memberName, "del equipo " + team.getDisplayName() + "."),
                () -> {
                    plugin.getTeamManager().removeFromCurrentTeam(memberUuid);
                    plugin.getTeamLobbyManager().refreshForTeam(team.getId());
                    admin.sendMessage(Component.text("✔ ", NamedTextColor.GREEN).append(Component.text(memberName, NamedTextColor.WHITE)).append(Component.text(" removido del equipo.", NamedTextColor.GREEN)));
                    plugin.getTeamManager().getTeam(team.getId()).ifPresentOrElse(fresh -> openDetail(admin, fresh), () -> openList(admin));
                });
    }

    private void adminAddMember(Player admin, String teamId, UUID pickedUuid) {
        var teamOpt = plugin.getTeamManager().getTeam(teamId);
        if (teamOpt.isEmpty()) { admin.sendMessage(Component.text("✘ El equipo ya no existe.", NamedTextColor.RED)); return; }
        EventTeam team = teamOpt.get();
        if (team.isFull()) { admin.sendMessage(Component.text("✘ El equipo se llenó mientras elegías.", NamedTextColor.RED)); openDetail(admin, team); return; }
        plugin.getTeamManager().removeFromCurrentTeam(pickedUuid);
        Player target = Bukkit.getPlayer(pickedUuid);
        boolean ok = (target != null) ? plugin.getTeamManager().addToTeam(teamId, target) : plugin.getTeamManager().addOfflineToTeam(teamId, pickedUuid);
        if (ok) {
            String name = TeamUtil.resolveMemberName(pickedUuid);
            admin.sendMessage(Component.text("✔ ", NamedTextColor.GREEN).append(Component.text(name, NamedTextColor.WHITE)).append(Component.text(" agregado al equipo ", NamedTextColor.GREEN)).append(Component.text(team.getDisplayName(), team.getColor())));
            if (target != null && target.isOnline()) target.sendMessage(Component.text("✔ Un admin te agregó al equipo ", NamedTextColor.GREEN).append(Component.text(team.getDisplayName(), team.getColor())));
            plugin.getTeamLobbyManager().refreshForTeam(teamId);
        } else { admin.sendMessage(Component.text("✘ No se pudo agregar.", NamedTextColor.RED)); }
        plugin.getTeamManager().getTeam(teamId).ifPresentOrElse(fresh -> openDetail(admin, fresh), () -> openList(admin));
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player admin = event.getPlayer();
        PendingPrompt prompt = awaiting.remove(admin.getUniqueId());
        if (prompt == null) return;
        event.setCancelled(true);
        String msg = event.getMessage().trim();
        if (msg.equalsIgnoreCase("cancelar")) { admin.sendMessage(Component.text("Acción cancelada.", NamedTextColor.GRAY)); return; }
        Bukkit.getScheduler().runTask(plugin, () -> {
            var teamOpt = plugin.getTeamManager().getTeam(prompt.teamId());
            if (teamOpt.isEmpty()) { admin.sendMessage(Component.text("✘ El equipo ya no existe.", NamedTextColor.RED)); return; }
            EventTeam team = teamOpt.get();
            switch (prompt.type()) {
                case RENAME -> processRename(admin, team, msg);
                case SCORE_ADD, SCORE_REMOVE, SCORE_SET -> processScore(admin, team, prompt.type(), msg);
            }
        });
    }

    private void processRename(Player admin, EventTeam team, String msg) {
        if (msg.length() > 24) { admin.sendMessage(Component.text("✘ Nombre demasiado largo (máx 24).", NamedTextColor.RED)); return; }
        if (msg.length() < 2)  { admin.sendMessage(Component.text("✘ Nombre demasiado corto (mín 2).", NamedTextColor.RED)); return; }
        plugin.getTeamManager().renameTeam(team.getId(), msg);
        admin.sendMessage(Component.text("✔ Equipo renombrado a ", NamedTextColor.GREEN).append(Component.text(msg, team.getColor())));
        plugin.getTeamLobbyManager().refreshForTeam(team.getId());
        openDetail(admin, plugin.getTeamManager().getTeam(team.getId()).orElse(team));
    }

    private void processScore(Player admin, EventTeam team, PromptType type, String msg) {
        int pts;
        try { pts = Integer.parseInt(msg); }
        catch (NumberFormatException e) { admin.sendMessage(Component.text("✘ Valor inválido.", NamedTextColor.RED)); return; }
        if (type != PromptType.SCORE_SET && pts <= 0) { admin.sendMessage(Component.text("✘ Debe ser un número positivo.", NamedTextColor.RED)); return; }
        switch (type) {
            case SCORE_ADD    -> { plugin.getScoreManager().addScore(team, pts);    admin.sendMessage(Component.text("✔ +" + pts + " puntos.", NamedTextColor.GREEN)); }
            case SCORE_REMOVE -> { plugin.getScoreManager().removeScore(team, pts); admin.sendMessage(Component.text("✔ -" + pts + " puntos.", NamedTextColor.GOLD)); }
            case SCORE_SET    -> { plugin.getScoreManager().setScore(team, pts);    admin.sendMessage(Component.text("✔ Puntaje seteado a " + pts + ".", NamedTextColor.AQUA)); }
            default -> {}
        }
        openDetail(admin, team);
    }
}