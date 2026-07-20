package org.falmdev.anieventmanager.minigames.bingo;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.utils.gui.GuiUtil;
import org.falmdev.anieventmanager.utils.gui.HeadUtil;
import org.falmdev.anieventmanager.utils.gui.ItemBuilder;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BingoEditGUI implements Listener {

    private static final int SLOT_ICON  = 10;
    private static final int SLOT_TITLE = 12;
    private static final int SLOT_DESC  = 14;
    private static final int SLOT_RESET = 16;
    private static final int SLOT_INFO  = 28;

    private static final String TITLE_PREFIX = "Editar tarea: ";

    private final Anieventmanager plugin;
    private final BingoConfig config;

    private final Map<UUID, String> awaitingTitle = new HashMap<>();
    private final Map<UUID, String> awaitingDesc  = new HashMap<>();

    public BingoEditGUI(Anieventmanager plugin) {
        this.plugin = plugin;
        this.config = plugin.getBingoMiniGame().getConfig();
    }

    public void open(Player player, BingoTask task) {
        Inventory inv = Bukkit.createInventory(null, 36,
                Component.text(TITLE_PREFIX, NamedTextColor.GOLD)
                        .append(Component.text(task.getId(), NamedTextColor.YELLOW)));

        GuiUtil.fillAll(inv, Material.GRAY_STAINED_GLASS_PANE);

        inv.setItem(SLOT_ICON,  buildIconSlot(task));
        inv.setItem(SLOT_TITLE, buildTitleSlot(task));
        inv.setItem(SLOT_DESC,  buildDescSlot(task));
        inv.setItem(SLOT_RESET, buildResetSlot(task));
        inv.setItem(SLOT_INFO,  buildInfoSlot(task));

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String titlePlain = GuiUtil.getTitle(event.getView());
        if (!titlePlain.startsWith(TITLE_PREFIX)) return;

        event.setCancelled(true);

        String taskId = titlePlain.replace(TITLE_PREFIX, "").trim();
        int slot = event.getRawSlot();

        BingoTask task = config.loadTasks().stream()
                .filter(t -> t.getId().equals(taskId))
                .findFirst().orElse(null);

        if (task == null) {
            player.sendMessage(Component.text("Tarea no encontrada.", NamedTextColor.RED));
            player.closeInventory();
            return;
        }

        switch (slot) {
            case SLOT_ICON -> {
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) {
                    player.sendMessage(Component.text(
                            "Debes tener un ítem en la mano para usarlo como icono.",
                            NamedTextColor.RED));
                    return;
                }

                String texture = extractSkullTexture(hand);
                if (texture != null) {
                    task.setIconTexture(texture);
                    task.setIcon(Material.PLAYER_HEAD);
                    config.saveTask(task);
                    player.sendMessage(Component.text(
                            "✔ Icono cambiado a la cabeza personalizada.", NamedTextColor.GREEN));
                } else {
                    task.setIcon(hand.getType());
                    task.setIconTexture(null);
                    config.saveTask(task);
                    player.sendMessage(Component.text("✔ Icono cambiado a ", NamedTextColor.GREEN)
                            .append(Component.text(hand.getType().name().toLowerCase()
                                    .replace('_', ' '), NamedTextColor.WHITE))
                            .append(Component.text(".", NamedTextColor.GREEN)));
                }
                Bukkit.getScheduler().runTask(plugin, () -> open(player, task));
            }

            case SLOT_TITLE -> {
                player.closeInventory();
                awaitingTitle.put(player.getUniqueId(), taskId);
                player.sendMessage(Component.text(
                                "Escribe el nuevo título para '", NamedTextColor.YELLOW)
                        .append(Component.text(taskId, NamedTextColor.WHITE))
                        .append(Component.text("' en el chat:", NamedTextColor.YELLOW)));
                player.sendMessage(Component.text(
                        "Usa &a, &c, &l, etc. para colores. 'cancelar' para cancelar.",
                        NamedTextColor.GRAY));
            }

            case SLOT_DESC -> {
                player.closeInventory();
                awaitingDesc.put(player.getUniqueId(), taskId);
                player.sendMessage(Component.text(
                                "Escribe la descripción para '", NamedTextColor.YELLOW)
                        .append(Component.text(taskId, NamedTextColor.WHITE))
                        .append(Component.text("' en el chat:", NamedTextColor.YELLOW)));
                player.sendMessage(Component.text(
                        "Usa \\n para saltos de línea. Ej: &7Primera línea\\n&aSeg. línea",
                        NamedTextColor.GRAY));
                player.sendMessage(Component.text(
                        "'cancelar' para cancelar. 'borrar' para quitar la descripción.",
                        NamedTextColor.GRAY));
            }

            case SLOT_RESET -> {
                if (!task.hasCustomIcon() && !task.hasIconTexture()) {
                    player.sendMessage(Component.text(
                            "Esta tarea no tiene icono personalizado.", NamedTextColor.GRAY));
                    return;
                }
                task.setIcon(null);
                task.setIconTexture(null);
                config.saveTask(task);
                player.sendMessage(Component.text("✔ Icono reseteado al default.", NamedTextColor.GREEN));
                Bukkit.getScheduler().runTask(plugin, () -> open(player, task));
            }
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();

        if (awaitingTitle.containsKey(uid)) {
            event.setCancelled(true);
            String taskId = awaitingTitle.remove(uid);
            String input = event.getMessage().trim();

            if (input.equalsIgnoreCase("cancelar")) {
                player.sendMessage(Component.text("Edición cancelada.", NamedTextColor.GRAY));
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                BingoTask task = config.loadTasks().stream()
                        .filter(t -> t.getId().equals(taskId)).findFirst().orElse(null);
                if (task == null) {
                    player.sendMessage(Component.text("Tarea no encontrada.", NamedTextColor.RED));
                    return;
                }
                task.setDisplayName(input);
                config.saveTask(task);
                player.sendMessage(Component.text("✔ Título cambiado a: ", NamedTextColor.GREEN)
                        .append(LegacyComponentSerializer.legacyAmpersand().deserialize(input)));
                open(player, task);
            });
            return;
        }

        if (awaitingDesc.containsKey(uid)) {
            event.setCancelled(true);
            String taskId = awaitingDesc.remove(uid);
            String input = event.getMessage().trim();

            if (input.equalsIgnoreCase("cancelar")) {
                player.sendMessage(Component.text("Edición cancelada.", NamedTextColor.GRAY));
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                BingoTask task = config.loadTasks().stream()
                        .filter(t -> t.getId().equals(taskId)).findFirst().orElse(null);
                if (task == null) {
                    player.sendMessage(Component.text("Tarea no encontrada.", NamedTextColor.RED));
                    return;
                }

                if (input.equalsIgnoreCase("borrar")) {
                    task.setDescription("");
                    config.saveTask(task);
                    player.sendMessage(Component.text("✔ Descripción eliminada.", NamedTextColor.GREEN));
                } else {
                    String processed = input.replace("\\n", "\n");
                    task.setDescription(processed);
                    config.saveTask(task);
                    player.sendMessage(Component.text("✔ Descripción guardada.", NamedTextColor.GREEN));
                }
                open(player, task);
            });
        }
    }

    private String extractSkullTexture(ItemStack item) {
        if (item.getType() != Material.PLAYER_HEAD) return null;
        if (!(item.getItemMeta() instanceof SkullMeta skullMeta)) return null;

        PlayerProfile profile = skullMeta.getPlayerProfile();
        if (profile == null) return null;

        PlayerTextures textures = profile.getTextures();
        if (textures == null || textures.isEmpty()) return null;

        URL skinUrl = textures.getSkin();
        if (skinUrl == null) return null;

        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + skinUrl + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private ItemStack buildIconSlot(BingoTask task) {
        String currentIcon = task.hasIconTexture()
                ? "cabeza personalizada"
                : task.hasCustomIcon()
                  ? task.getIcon().name().toLowerCase().replace('_', ' ')
                  : "default (cristal)";

        ItemBuilder b = task.hasIconTexture()
                ? ItemBuilder.of(HeadUtil.fromBase64(task.getIconTexture()))
                : ItemBuilder.of(task.hasCustomIcon() ? task.getIcon() : Material.ITEM_FRAME);

        return b
                .name("Cambiar icono", NamedTextColor.GOLD)
                .emptyLine()
                .lore(GuiUtil.noItalic(Component.text("Icono actual: ", NamedTextColor.GRAY)
                        .append(Component.text(currentIcon, NamedTextColor.WHITE))))
                .emptyLine()
                .lore(NamedTextColor.YELLOW,
                        "Click con un ítem en mano",
                        "para usarlo como icono.")
                .lore(NamedTextColor.DARK_GRAY, "Funciona con cabezas personalizadas.")
                .build();
    }

    private ItemStack buildTitleSlot(BingoTask task) {
        return ItemBuilder.of(Material.NAME_TAG)
                .name("Cambiar título", NamedTextColor.GOLD)
                .emptyLine()
                .lore(GuiUtil.noItalic(Component.text("Título actual: ", NamedTextColor.GRAY)
                        .append(LegacyComponentSerializer.legacyAmpersand()
                                .deserialize(task.getDisplayName())
                                .decoration(TextDecoration.ITALIC, false))))
                .emptyLine()
                .lore(NamedTextColor.YELLOW, "Click para escribir en el chat.")
                .lore("Soporta &a, &c, &l, etc.")
                .build();
    }

    private ItemStack buildDescSlot(BingoTask task) {
        ItemBuilder b = ItemBuilder.of(Material.WRITABLE_BOOK)
                .name("Cambiar descripción", NamedTextColor.GOLD)
                .emptyLine();

        if (task.hasDescription()) {
            for (String line : task.getDescription().split("\n")) {
                b.lore(LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(line)
                        .decoration(TextDecoration.ITALIC, false));
            }
        } else {
            b.lore("Sin descripción.");
        }

        return b.emptyLine()
                .lore(NamedTextColor.YELLOW, "Click para escribir en el chat.")
                .lore("Usa \\n para saltos de línea.",
                        "Usa &a, &c, etc. para colores.",
                        "'borrar' para eliminarla.")
                .build();
    }

    private ItemStack buildResetSlot(BingoTask task) {
        ItemBuilder b = ItemBuilder.of(Material.BARRIER)
                .name("Quitar icono personalizado", NamedTextColor.RED)
                .emptyLine();

        if (task.hasCustomIcon() || task.hasIconTexture()) {
            b.lore(NamedTextColor.YELLOW,
                    "Click para volver al cristal",
                    "de color por defecto.");
        } else {
            b.lore("No hay icono personalizado.");
        }
        return b.build();
    }

    private ItemStack buildInfoSlot(BingoTask task) {
        return ItemBuilder.of(Material.PAPER)
                .name("Información", NamedTextColor.AQUA)
                .emptyLine()
                .lore("ID: " + task.getId(),
                        "Tipo: " + task.getType().name(),
                        "Descripción: " + task.getShortDescription())
                .build();
    }
}