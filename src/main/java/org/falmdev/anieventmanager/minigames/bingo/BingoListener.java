package org.falmdev.anieventmanager.minigames.bingo;

import io.papermc.paper.event.player.PlayerTradeEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.model.EventTeam;

import java.util.Optional;

public class BingoListener implements Listener {

    private final Anieventmanager plugin;
    private final BingoMiniGame miniGame;
    private BukkitTask locationTask;

    public BingoListener(Anieventmanager plugin, BingoMiniGame miniGame) {
        this.plugin   = plugin;
        this.miniGame = miniGame;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!miniGame.isRunning()) return;

        Optional<EventTeam> teamOpt = plugin.getTeamManager().getTeamOf(player);
        if (teamOpt.isEmpty()) return;

        Material mat = event.getItem().getItemStack().getType();
        int amount   = event.getItem().getItemStack().getAmount();
        checkTasksByType(teamOpt.get(), BingoTask.Type.OBTAIN_ITEM, mat, amount);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        if (!miniGame.isRunning()) return;

        Optional<EventTeam> teamOpt = plugin.getTeamManager().getTeamOf(event.getPlayer());
        if (teamOpt.isEmpty()) return;

        checkTasksByType(teamOpt.get(), BingoTask.Type.OBTAIN_ITEM,
                event.getItemType(), event.getItemAmount());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!miniGame.isRunning()) return;

        Optional<EventTeam> teamOpt = plugin.getTeamManager().getTeamOf(player);
        if (teamOpt.isEmpty()) return;

        ItemStack result = event.getRecipe().getResult();
        int craftedAmount;

        if (event.isShiftClick()) {
            int timesCanCraft = Integer.MAX_VALUE;
            for (ItemStack ingredient : event.getInventory().getMatrix()) {
                if (ingredient == null || ingredient.getType().isAir()) continue;
                timesCanCraft = Math.min(timesCanCraft, ingredient.getAmount());
            }
            if (timesCanCraft == Integer.MAX_VALUE) timesCanCraft = 1;
            craftedAmount = result.getAmount() * timesCanCraft;
        } else {
            craftedAmount = result.getAmount();
        }

        checkTasksByType(teamOpt.get(), BingoTask.Type.CRAFT_ITEM, result.getType(), craftedAmount);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        if (!miniGame.isRunning()) return;
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        Optional<EventTeam> teamOpt = plugin.getTeamManager().getTeamOf(killer);
        if (teamOpt.isEmpty()) return;

        var entityType = event.getEntityType();
        BingoCard card = miniGame.getCard(teamOpt.get());
        if (card == null) return;

        for (BingoTask task : card.getTasks()) {
            if (task.getType() != BingoTask.Type.KILL_MOB) continue;
            if (task.getMobType() != entityType) continue;
            if (task.isCompleted()) continue;

            boolean justCompleted = task.increment(1);
            if (!justCompleted) {
                killer.sendMessage(Component.text("⚔ ", NamedTextColor.GRAY)
                        .append(legacyTaskName(task, NamedTextColor.GRAY))
                        .append(Component.text(" — " + task.getProgress() + "/" + task.getRequired(),
                                NamedTextColor.GRAY)));
                refreshOpenGUIs(teamOpt.get(), card);
            } else {
                onTaskCompleted(teamOpt.get(), task, card);
            }
            break;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!miniGame.isRunning()) return;
        if (event.getSlotType() != InventoryType.SlotType.ARMOR) return;

        Optional<EventTeam> teamOpt = plugin.getTeamManager().getTeamOf(player);
        if (teamOpt.isEmpty()) return;

        ItemStack item = event.getCursor();
        if (item == null || item.getType().isAir()) return;

        checkTasksByType(teamOpt.get(), BingoTask.Type.EQUIP_ITEM, item.getType(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (!miniGame.isRunning()) return;
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;

        Optional<EventTeam> teamOpt = plugin.getTeamManager().getTeamOf(event.getPlayer());
        if (teamOpt.isEmpty()) return;

        if (event.getCaught() instanceof org.bukkit.entity.Item caughtItem) {
            Material mat = caughtItem.getItemStack().getType();
            checkTasksByType(teamOpt.get(), BingoTask.Type.FISH_ITEM, mat, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrade(PlayerTradeEvent event) {
        if (!miniGame.isRunning()) return;

        Optional<EventTeam> teamOpt = plugin.getTeamManager().getTeamOf(event.getPlayer());
        if (teamOpt.isEmpty()) return;

        BingoCard card = miniGame.getCard(teamOpt.get());
        if (card == null) return;

        Material result = event.getTrade().getResult().getType();

        for (BingoTask task : card.getTasks()) {
            if (task.isCompleted()) continue;

            switch (task.getType()) {
                case TRADE_ANY -> {
                    boolean done = task.increment(1);
                    if (done) {
                        onTaskCompleted(teamOpt.get(), task, card);
                    } else {
                        event.getPlayer().sendMessage(Component.text("🤝 ", NamedTextColor.GRAY)
                                .append(legacyTaskName(task, NamedTextColor.GRAY))
                                .append(Component.text(" — " + task.getProgress() + "/" + task.getRequired(),
                                        NamedTextColor.GRAY)));
                        refreshOpenGUIs(teamOpt.get(), card);
                    }
                }
                case TRADE_ITEM -> {
                    if (task.getMaterial() != result) continue;
                    boolean done = task.increment(1);
                    if (done) {
                        onTaskCompleted(teamOpt.get(), task, card);
                    } else {
                        event.getPlayer().sendMessage(Component.text("🤝 ", NamedTextColor.GRAY)
                                .append(legacyTaskName(task, NamedTextColor.GRAY))
                                .append(Component.text(" — " + task.getProgress() + "/" + task.getRequired(),
                                        NamedTextColor.GRAY)));
                        refreshOpenGUIs(teamOpt.get(), card);
                    }
                }
                default -> {}
            }
        }
    }

    @EventHandler
    public void onCardItemUse(PlayerInteractEvent event) {
        if (!miniGame.isRunning()) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isCardItem(event.getItem())) return;

        event.setCancelled(true);
        event.getPlayer().performCommand("bingo");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCardItemMoveInventory(InventoryClickEvent event) {
        if (!miniGame.isRunning()) return;
        if (isCardItem(event.getCurrentItem()) || isCardItem(event.getCursor())) {
            event.setCancelled(true);
            return;
        }
        if (event.getClick() == ClickType.NUMBER_KEY) {
            ItemStack hotbarItem = event.getWhoClicked().getInventory().getItem(event.getHotbarButton());
            if (isCardItem(hotbarItem)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCardItemDrag(InventoryDragEvent event) {
        if (!miniGame.isRunning()) return;
        if (isCardItem(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCardItemDrop(PlayerDropItemEvent event) {
        if (!miniGame.isRunning()) return;
        if (isCardItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCardItemSwap(PlayerSwapHandItemsEvent event) {
        if (!miniGame.isRunning()) return;
        if (isCardItem(event.getMainHandItem()) || isCardItem(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    public void startLocationCheck() {
        locationTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!miniGame.isRunning()) return;

            for (EventTeam team : plugin.getTeamManager().getAllTeams()) {
                BingoCard card = miniGame.getCard(team);
                if (card == null) continue;

                for (BingoTask task : card.getTasks()) {
                    if (task.isCompleted()) continue;

                    if (task.getType() == BingoTask.Type.REACH_LOCATION) {
                        for (Player p : team.getOnlinePlayers()) {
                            Location loc = p.getLocation();
                            if (!loc.getWorld().getName().equals(task.getLocationWorld())) continue;

                            double dx   = loc.getX() - task.getLocationX();
                            double dy   = loc.getY() - task.getLocationY();
                            double dz   = loc.getZ() - task.getLocationZ();
                            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

                            if (dist <= task.getLocationRadius()) {
                                if (task.complete()) onTaskCompleted(team, task, card);
                            }
                        }
                    }

                    if (task.getType() == BingoTask.Type.VISIT_STRUCTURE) {
                        for (Player p : team.getOnlinePlayers()) {
                            if (isInsideStructure(p.getLocation(), task.getStructureKey())) {
                                if (task.complete()) onTaskCompleted(team, task, card);
                                break;
                            }
                        }
                    }
                }
            }
        }, 20L, 20L);
    }

    public void stopLocationCheck() {
        if (locationTask != null && !locationTask.isCancelled()) locationTask.cancel();
    }

    private void checkTasksByType(EventTeam team, BingoTask.Type type, Material mat, int amount) {
        BingoCard card = miniGame.getCard(team);
        if (card == null) return;

        for (BingoTask task : card.getTasks()) {
            if (task.getType() == type && task.getMaterial() == mat && !task.isCompleted()) {
                if (task.increment(amount)) {
                    onTaskCompleted(team, task, card);
                } else {
                    refreshOpenGUIs(team, card);
                }
            }
        }
    }

    private boolean isInsideStructure(Location location, String structureKey) {
        if (location.getWorld() == null) return false;

        org.bukkit.NamespacedKey key;
        try {
            if (structureKey.contains(":")) {
                String[] parts = structureKey.split(":", 2);
                key = new org.bukkit.NamespacedKey(parts[0], parts[1]);
            } else {
                key = new org.bukkit.NamespacedKey("minecraft", structureKey);
            }
        } catch (Exception e) {
            return false;
        }

        org.bukkit.generator.structure.Structure structure =
                org.bukkit.Registry.STRUCTURE.get(key);
        if (structure == null) return false;

        org.bukkit.util.StructureSearchResult result =
                location.getWorld().locateNearestStructure(location, structure, 1, false);
        if (result == null) return false;

        Location structLoc = result.getLocation();
        double dx = location.getX() - structLoc.getX();
        double dz = location.getZ() - structLoc.getZ();
        return Math.sqrt(dx * dx + dz * dz) <= 80.0;
    }

    private void onTaskCompleted(EventTeam team, BingoTask task, BingoCard card) {
        Bukkit.getOnlinePlayers().forEach(p ->
                p.sendMessage(Component.text("✔ ", NamedTextColor.GREEN)
                        .append(Component.text(team.getDisplayName(), team.getColor()))
                        .append(Component.text(" completó: ", NamedTextColor.GREEN))
                        .append(legacyTaskName(task, NamedTextColor.YELLOW))
                        .append(Component.text("  (" + card.getCompletedCount()
                                + "/" + card.getTotalTasks() + ")", NamedTextColor.GRAY)))
        );
        refreshOpenGUIs(team, card);
        miniGame.checkWinCondition(team, card);
    }

    private Component legacyTaskName(BingoTask task, NamedTextColor defaultColor) {
        return LegacyComponentSerializer.legacyAmpersand()
                .deserialize(task.getDisplayName())
                .decoration(TextDecoration.ITALIC, false)
                .colorIfAbsent(defaultColor);
    }

    private boolean isCardItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(miniGame.getCardItemKey(), PersistentDataType.BOOLEAN);
    }

    private void refreshOpenGUIs(EventTeam team, BingoCard card) {
        for (Player p : team.getOnlinePlayers()) {
            String titlePlain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(p.getOpenInventory().title());
            if (titlePlain.startsWith("✦ Tarjeta de Bingo")) {
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> BingoGUI.open(p, card, miniGame.getConfig()));
            }
        }
    }
}