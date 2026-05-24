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
import org.bukkit.inventory.meta.SkullMeta;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.model.EventTeam;

import java.util.*;

/**
 * GUI de administración multi-equipo.
 *
 * ── Vista 1 (LIST_TITLE): lista de equipos ──────────────────────────
 *   Cada equipo como banner. Click → vista de detalles.
 *
 * ── Vista 2 (DETAIL_TITLE_PREFIX): detalle de un equipo ─────────────
 *   Slot layout (54 slots):
 *     row 0: borders
 *     row 1: INFO(10) | RENAME(11) | DELETE(12) | (vacío) | SCORE_INFO(14) | SCORE_ADD(15) | SCORE_REMOVE(16)
 *     row 2: SCORE_SET(19) | SCORE_RESET(20) | (vacío) | (vacío) | (vacío)
 *     row 3: MEMBER_1(28) | MEMBER_2(29) (cabezas)
 *     row 4: ADD_MEMBER(37)
 *     row 5: BACK(45) | (border) ... | CANCEL(49) | ... | (border)
 *
 *  Click en cabeza de miembro → confirma quitar.
 *  Click en + agregar miembro → abre PlayerSelectorGUI.
 *  Click en rename → chat prompt.
 *  Click en delete → ConfirmGUI.
 *  Click en score_add/remove/set → chat prompt para número.
 *  Click en score_reset → ConfirmGUI.
 */
public class TeamAdminGUI implements Listener {

    public static final String LIST_TITLE           = "Admin: Equipos";
    public static final String DETAIL_TITLE_PREFIX  = "Admin · ";

    // ── Slots vista detalle ──────────────────────────────────────────────────
    private static final int SLOT_INFO          = 10;
    private static final int SLOT_RENAME        = 11;
    private static final int SLOT_DELETE        = 53;
    private static final int SLOT_SCORE_INFO    = 14;
    private static final int SLOT_SCORE_ADD     = 15;
    private static final int SLOT_SCORE_REMOVE  = 16;
    private static final int SLOT_SCORE_SET     = 24;
    private static final int SLOT_SCORE_RESET   = 25;
    private static final int SLOT_MEMBER_1      = 39;
    private static final int SLOT_MEMBER_2      = 41;
    private static final int SLOT_ADD_MEMBER    = 37;
    private static final int SLOT_BACK          = 49;

    private final Anieventmanager plugin;

    // viewer → teamId al que está editando (para chat prompts)
    private final Map<UUID, PendingPrompt> awaiting = new HashMap<>();

    private enum PromptType { RENAME, SCORE_ADD, SCORE_REMOVE, SCORE_SET }
    private record PendingPrompt(String teamId, PromptType type) {}

    public TeamAdminGUI(Anieventmanager plugin) {
        this.plugin = plugin;
    }

    // ── Vista 1: lista de equipos ─────────────────────────────────────────────

    public void openList(Player admin) {
        var teams = plugin.getTeamManager().getAllTeams();
        int size = ((teams.size() / 9) + 1) * 9;
        if (size < 27) size = 27;
        if (size > 54) size = 54;

        Inventory inv = Bukkit.createInventory(null, size,
                Component.text(LIST_TITLE, NamedTextColor.GOLD));

        int slot = 0;
        for (EventTeam team : teams) {
            inv.setItem(slot++, buildTeamBanner(team));
            if (slot >= size) break;
        }

        admin.openInventory(inv);
    }

    private ItemStack buildTeamBanner(EventTeam team) {
        ItemStack item = new ItemStack(colorToBannerMaterial(team.getColor()));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(team.getDisplayName(), team.getColor(), TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(line("ID", Component.text(team.getId(), NamedTextColor.WHITE)));
        lore.add(line("Miembros", Component.text(team.getMemberCount() + "/2",
                team.getMemberCount() >= 2 ? NamedTextColor.RED : NamedTextColor.GREEN)));
        lore.add(line("Puntos", Component.text(
                plugin.getScoreManager().getScore(team), NamedTextColor.YELLOW)));
        lore.add(Component.empty());
        lore.add(Component.text("Click para abrir el detalle.", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ── Vista 2: detalle de un equipo ─────────────────────────────────────────

    public void openDetail(Player admin, EventTeam team) {
        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text(DETAIL_TITLE_PREFIX, NamedTextColor.GOLD)
                        .append(Component.text(team.getDisplayName(), team.getColor())));

        fillBorders(inv);

        // ── Info y gestión del equipo ────────────────────────────────────────
        inv.setItem(SLOT_INFO,   buildTeamInfoItem(team));
        inv.setItem(SLOT_RENAME, buildSimpleButton(Material.NAME_TAG,
                "Cambiar nombre", NamedTextColor.GOLD,
                "Click para escribir el nuevo nombre en el chat."));
        inv.setItem(SLOT_DELETE, buildSimpleButton(Material.LAVA_BUCKET,
                "Eliminar equipo", NamedTextColor.RED,
                "Click para eliminar este equipo (pide confirmación)."));

        // ── Gestión de score ─────────────────────────────────────────────────
        inv.setItem(SLOT_SCORE_INFO, buildScoreInfoItem(team));
        inv.setItem(SLOT_SCORE_ADD, buildSimpleButton(Material.LIME_DYE,
                "+ Agregar puntos", NamedTextColor.GREEN,
                "Click para escribir cantidad de puntos a sumar."));
        inv.setItem(SLOT_SCORE_REMOVE, buildSimpleButton(Material.ORANGE_DYE,
                "- Restar puntos", NamedTextColor.GOLD,
                "Click para escribir cantidad de puntos a restar."));
        inv.setItem(SLOT_SCORE_SET, buildSimpleButton(Material.WRITABLE_BOOK,
                "= Setear puntaje", NamedTextColor.AQUA,
                "Click para escribir el puntaje exacto."));
        inv.setItem(SLOT_SCORE_RESET, buildSimpleButton(Material.BARRIER,
                "↺ Resetear a 0", NamedTextColor.RED,
                "Click para resetear el puntaje (pide confirmación)."));

        // ── Miembros ─────────────────────────────────────────────────────────
        List<UUID> members = new ArrayList<>(team.getMembers());
        for (int i = 0; i < 2; i++) {
            int slot = (i == 0) ? SLOT_MEMBER_1 : SLOT_MEMBER_2;
            if (i < members.size()) {
                inv.setItem(slot, buildMemberHead(members.get(i)));
            } else {
                inv.setItem(slot, buildEmptyMemberSlot());
            }
        }

        inv.setItem(SLOT_ADD_MEMBER, buildAddMemberButton(team));

        // ── Volver ───────────────────────────────────────────────────────────
        inv.setItem(SLOT_BACK, buildSimpleButton(Material.ARROW,
                "← Volver a la lista", NamedTextColor.GRAY,
                "Click para regresar."));

        admin.openInventory(inv);
    }

    private ItemStack buildTeamInfoItem(EventTeam team) {
        ItemStack item = new ItemStack(colorToBannerMaterial(team.getColor()));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(team.getDisplayName(), team.getColor(), TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(line("ID", Component.text(team.getId(), NamedTextColor.WHITE)));
        lore.add(line("Color", Component.text(
                team.getColor().toString().toLowerCase().replace('_', ' '), team.getColor())));
        lore.add(line("Miembros", Component.text(team.getMemberCount() + "/2",
                team.getMemberCount() >= 2 ? NamedTextColor.RED : NamedTextColor.GREEN)));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildScoreInfoItem(EventTeam team) {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        int score = plugin.getScoreManager().getScore(team);
        int rank  = plugin.getScoreManager().getRank(team);
        meta.displayName(Component.text("Puntaje actual", NamedTextColor.YELLOW, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(line("Puntos", Component.text(score, NamedTextColor.GOLD, TextDecoration.BOLD)));
        lore.add(line("Posición", Component.text(rank == -1 ? "-" : "#" + rank, NamedTextColor.WHITE)));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildMemberHead(UUID uuid) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
        meta.setOwningPlayer(off);

        String name = off.getName() != null ? off.getName() : uuid.toString().substring(0, 6);
        meta.displayName(Component.text(name, NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Estado: ", NamedTextColor.GRAY)
                .append(off.isOnline()
                        ? Component.text("● online", NamedTextColor.GREEN)
                        : Component.text("○ offline", NamedTextColor.DARK_GRAY))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("UUID: " + uuid.toString().substring(0, 8) + "…",
                NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Click para quitar del equipo.", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("(pide confirmación)", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildEmptyMemberSlot() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Slot vacío", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildAddMemberButton(EventTeam team) {
        ItemStack item = new ItemStack(team.isFull() ? Material.BARRIER : Material.LIME_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(
                team.isFull() ? "Equipo lleno" : "+ Agregar miembro",
                team.isFull() ? NamedTextColor.RED : NamedTextColor.GREEN,
                TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        if (team.isFull()) {
            lore.add(Component.text("El equipo ya tiene 2/2 miembros.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Click para abrir el selector", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("de jugadores (online + offline).", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildSimpleButton(Material mat, String text, NamedTextColor color, String loreText) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(text, color, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.empty(),
                Component.text(loreText, NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    private void fillBorders(Inventory inv) {
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = border.getItemMeta();
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        border.setItemMeta(meta);
        for (int i = 0; i < 54; i++) inv.setItem(i, border);
    }

    // ── Click listener ────────────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(event.getView().title());

        if (!(event.getWhoClicked() instanceof Player admin) || !admin.isOp()) {
            if (title.equals(LIST_TITLE) || title.startsWith(DETAIL_TITLE_PREFIX)) {
                event.setCancelled(true);
            }
            return;
        }

        // ── Lista de equipos ──────────────────────────────────────────────────
        if (title.equals(LIST_TITLE)) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null) return;
            ItemMeta meta = clicked.getItemMeta();
            if (meta == null || meta.displayName() == null) return;

            String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(meta.displayName());

            for (EventTeam team : plugin.getTeamManager().getAllTeams()) {
                if (team.getDisplayName().equals(name)) {
                    openDetail(admin, team);
                    return;
                }
            }
            return;
        }

        // ── Vista de detalle ──────────────────────────────────────────────────
        if (!title.startsWith(DETAIL_TITLE_PREFIX)) return;

        event.setCancelled(true);

        String displayName = title.substring(DETAIL_TITLE_PREFIX.length());
        EventTeam team = findTeamByDisplayName(displayName);
        if (team == null) {
            admin.sendMessage(Component.text("✘ Equipo no encontrado.", NamedTextColor.RED));
            admin.closeInventory();
            return;
        }

        int slot = event.getRawSlot();

        switch (slot) {
            case SLOT_BACK -> openList(admin);

            case SLOT_RENAME -> {
                admin.closeInventory();
                awaiting.put(admin.getUniqueId(), new PendingPrompt(team.getId(), PromptType.RENAME));
                admin.sendMessage(Component.text("✎ Escribe el nuevo nombre del equipo ", NamedTextColor.YELLOW)
                        .append(Component.text(team.getDisplayName(), team.getColor()))
                        .append(Component.text(":", NamedTextColor.YELLOW)));
                admin.sendMessage(Component.text("  'cancelar' para cancelar.", NamedTextColor.GRAY));
            }

            case SLOT_DELETE -> {
                admin.closeInventory();
                plugin.getConfirmGUI().open(admin,
                        "Eliminar equipo",
                        List.of("Vas a eliminar permanentemente el equipo:",
                                "  " + team.getDisplayName() + "  (id: " + team.getId() + ")",
                                "Miembros: " + team.getMemberCount() + "/2",
                                "Puntos: " + plugin.getScoreManager().getScore(team)),
                        () -> {
                            plugin.getTeamManager().deleteTeam(team.getId());
                            plugin.getTeamLobbyManager().refreshAll();
                            admin.sendMessage(Component.text("✔ Equipo eliminado.", NamedTextColor.GREEN));
                            openList(admin);
                        });
            }

            case SLOT_SCORE_ADD -> {
                admin.closeInventory();
                awaiting.put(admin.getUniqueId(), new PendingPrompt(team.getId(), PromptType.SCORE_ADD));
                admin.sendMessage(Component.text("✎ Escribe cantidad de puntos a SUMAR:",
                        NamedTextColor.GREEN));
                admin.sendMessage(Component.text("  'cancelar' para cancelar.", NamedTextColor.GRAY));
            }

            case SLOT_SCORE_REMOVE -> {
                admin.closeInventory();
                awaiting.put(admin.getUniqueId(), new PendingPrompt(team.getId(), PromptType.SCORE_REMOVE));
                admin.sendMessage(Component.text("✎ Escribe cantidad de puntos a RESTAR:",
                        NamedTextColor.GOLD));
                admin.sendMessage(Component.text("  'cancelar' para cancelar.", NamedTextColor.GRAY));
            }

            case SLOT_SCORE_SET -> {
                admin.closeInventory();
                awaiting.put(admin.getUniqueId(), new PendingPrompt(team.getId(), PromptType.SCORE_SET));
                admin.sendMessage(Component.text("✎ Escribe el puntaje EXACTO a setear:",
                        NamedTextColor.AQUA));
                admin.sendMessage(Component.text("  'cancelar' para cancelar.", NamedTextColor.GRAY));
            }

            case SLOT_SCORE_RESET -> {
                admin.closeInventory();
                int currentScore = plugin.getScoreManager().getScore(team);
                plugin.getConfirmGUI().open(admin,
                        "Resetear puntaje",
                        List.of("Vas a resetear el puntaje del equipo:",
                                "  " + team.getDisplayName(),
                                "Puntos actuales: " + currentScore + " → 0"),
                        () -> {
                            plugin.getScoreManager().resetScore(team);
                            admin.sendMessage(Component.text("✔ Puntaje reseteado a 0.", NamedTextColor.GREEN));
                            openDetail(admin, team);
                        });
            }

            case SLOT_MEMBER_1, SLOT_MEMBER_2 -> {
                ItemStack clicked = event.getCurrentItem();
                if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) return;
                if (!(clicked.getItemMeta() instanceof SkullMeta skull)) return;
                OfflinePlayer owner = skull.getOwningPlayer();
                if (owner == null) return;

                final String memberName = owner.getName() != null ? owner.getName() : "jugador";
                final UUID memberUuid = owner.getUniqueId();

                admin.closeInventory();
                plugin.getConfirmGUI().open(admin,
                        "Quitar miembro",
                        List.of("Vas a quitar a ", "  " + memberName,
                                "del equipo " + team.getDisplayName() + "."),
                        () -> {
                            plugin.getTeamManager().removeFromCurrentTeam(memberUuid);
                            plugin.getTeamLobbyManager().refreshForTeam(team.getId());
                            admin.sendMessage(Component.text("✔ ", NamedTextColor.GREEN)
                                    .append(Component.text(memberName, NamedTextColor.WHITE))
                                    .append(Component.text(" removido del equipo.", NamedTextColor.GREEN)));
                            // Reabrir con datos frescos
                            var fresh = plugin.getTeamManager().getTeam(team.getId());
                            if (fresh.isPresent()) openDetail(admin, fresh.get());
                            else openList(admin);
                        });
            }

            case SLOT_ADD_MEMBER -> {
                if (team.isFull()) return;
                admin.closeInventory();
                plugin.getPlayerSelectorGUI().open(admin, team.getDisplayName(), pickedUuid ->
                        adminAddMember(admin, team.getId(), pickedUuid));
            }
        }
    }

    private void adminAddMember(Player admin, String teamId, UUID pickedUuid) {
        var teamOpt = plugin.getTeamManager().getTeam(teamId);
        if (teamOpt.isEmpty()) {
            admin.sendMessage(Component.text("✘ El equipo ya no existe.", NamedTextColor.RED));
            return;
        }
        EventTeam team = teamOpt.get();

        if (team.isFull()) {
            admin.sendMessage(Component.text("✘ El equipo se llenó mientras elegías.", NamedTextColor.RED));
            openDetail(admin, team);
            return;
        }

        // Si el jugador ya está en otro equipo, el admin lo mueve directamente
        plugin.getTeamManager().removeFromCurrentTeam(pickedUuid);

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
            admin.sendMessage(Component.text("✔ ", NamedTextColor.GREEN)
                    .append(Component.text(name, NamedTextColor.WHITE))
                    .append(Component.text(" agregado al equipo ", NamedTextColor.GREEN))
                    .append(Component.text(team.getDisplayName(), team.getColor())));
            if (target != null && target.isOnline()) {
                target.sendMessage(Component.text("✔ Un admin te agregó al equipo ", NamedTextColor.GREEN)
                        .append(Component.text(team.getDisplayName(), team.getColor())));
            }
            plugin.getTeamLobbyManager().refreshForTeam(teamId);
        } else {
            admin.sendMessage(Component.text("✘ No se pudo agregar.", NamedTextColor.RED));
        }
        openDetail(admin, plugin.getTeamManager().getTeam(teamId).orElse(team));
    }

    // ── Chat prompts ──────────────────────────────────────────────────────────

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player admin = event.getPlayer();
        UUID uid = admin.getUniqueId();
        PendingPrompt prompt = awaiting.remove(uid);
        if (prompt == null) return;

        event.setCancelled(true);
        String msg = event.getMessage().trim();

        if (msg.equalsIgnoreCase("cancelar")) {
            admin.sendMessage(Component.text("Acción cancelada.", NamedTextColor.GRAY));
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            var teamOpt = plugin.getTeamManager().getTeam(prompt.teamId);
            if (teamOpt.isEmpty()) {
                admin.sendMessage(Component.text("✘ El equipo ya no existe.", NamedTextColor.RED));
                return;
            }
            EventTeam team = teamOpt.get();

            switch (prompt.type) {
                case RENAME -> {
                    if (msg.length() > 24) {
                        admin.sendMessage(Component.text("✘ Nombre demasiado largo (máx 24).", NamedTextColor.RED));
                        return;
                    }
                    if (msg.length() < 2) {
                        admin.sendMessage(Component.text("✘ Nombre demasiado corto (mín 2).", NamedTextColor.RED));
                        return;
                    }
                    plugin.getTeamManager().renameTeam(team.getId(), msg);
                    admin.sendMessage(Component.text("✔ Equipo renombrado a ", NamedTextColor.GREEN)
                            .append(Component.text(msg, team.getColor())));
                    plugin.getTeamLobbyManager().refreshForTeam(team.getId());
                    openDetail(admin, plugin.getTeamManager().getTeam(team.getId()).orElse(team));
                }
                case SCORE_ADD, SCORE_REMOVE, SCORE_SET -> {
                    int pts;
                    try {
                        pts = Integer.parseInt(msg);
                    } catch (NumberFormatException e) {
                        admin.sendMessage(Component.text("✘ Valor inválido. Debe ser un número.", NamedTextColor.RED));
                        return;
                    }
                    if (prompt.type != PromptType.SCORE_SET && pts <= 0) {
                        admin.sendMessage(Component.text("✘ Debe ser un número positivo.", NamedTextColor.RED));
                        return;
                    }
                    switch (prompt.type) {
                        case SCORE_ADD    -> {
                            plugin.getScoreManager().addScore(team, pts);
                            admin.sendMessage(Component.text("✔ +" + pts + " puntos.", NamedTextColor.GREEN));
                        }
                        case SCORE_REMOVE -> {
                            plugin.getScoreManager().removeScore(team, pts);
                            admin.sendMessage(Component.text("✔ -" + pts + " puntos.", NamedTextColor.GOLD));
                        }
                        case SCORE_SET    -> {
                            plugin.getScoreManager().setScore(team, pts);
                            admin.sendMessage(Component.text("✔ Puntaje seteado a " + pts + ".", NamedTextColor.AQUA));
                        }
                        default -> {}
                    }
                    openDetail(admin, team);
                }
            }
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