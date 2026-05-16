package org.falmdev.anieventmanager.minigames.bingo;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.model.EventTeam;

import java.util.Optional;

/**
 * Listener del Bingo.
 * Detecta los seis tipos de tarea y notifica al minijuego.
 */
public class BingoListener implements Listener {

    private final Anieventmanager plugin;
    private final BingoMiniGame miniGame;
    private BukkitTask locationTask;

    public BingoListener(Anieventmanager plugin, BingoMiniGame miniGame) {
        this.plugin   = plugin;
        this.miniGame = miniGame;
    }

    // ── OBTAIN_ITEM — recoger ítem del suelo ──────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!miniGame.isRunning()) return;

        Optional<EventTeam> teamOpt = plugin.getTeamManager().getTeamOf(player);
        if (teamOpt.isEmpty()) return;

        Material mat = event.getItem().getItemStack().getType();
        int amount   = event.getItem().getItemStack().getAmount();
        checkObtainTasks(teamOpt.get(), mat, amount);
    }

    // ── CRAFT_ITEM — craftear ítem ────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!miniGame.isRunning()) return;

        Optional<EventTeam> teamOpt = plugin.getTeamManager().getTeamOf(player);
        if (teamOpt.isEmpty()) return;

        ItemStack result = event.getRecipe().getResult();
        checkTasksByType(teamOpt.get(), BingoTask.Type.CRAFT_ITEM, result.getType(), result.getAmount());
    }

    // ── KILL_MOB — matar mob ──────────────────────────────────────────────────

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

            // Mostrar progreso parcial en chat solo al jugador que mató
            if (!justCompleted) {
                killer.sendMessage(Component.text("⚔ " + task.getDisplayName()
                                + " — " + task.getProgress() + "/" + task.getRequired(),
                        NamedTextColor.GRAY));
                refreshOpenGUIs(teamOpt.get(), card);
            } else {
                onTaskCompleted(teamOpt.get(), task, card);
            }
            // Solo procesar la primera tarea que coincida por kill
            break;
        }
    }

    // ── EQUIP_ITEM — equipar armadura ─────────────────────────────────────────

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

    // ── FISH_ITEM — pescar ítem ───────────────────────────────────────────────

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

    // ── REACH_LOCATION — tick periódico ───────────────────────────────────────

    public void startLocationCheck() {
        locationTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!miniGame.isRunning()) return;

            for (EventTeam team : plugin.getTeamManager().getAllTeams()) {
                BingoCard card = miniGame.getCard(team);
                if (card == null) continue;

                for (BingoTask task : card.getTasks()) {
                    if (task.getType() != BingoTask.Type.REACH_LOCATION || task.isCompleted()) continue;

                    for (Player p : team.getOnlinePlayers()) {
                        Location loc = p.getLocation();
                        if (!loc.getWorld().getName().equals(task.getLocationWorld())) continue;

                        double dx = loc.getX() - task.getLocationX();
                        double dy = loc.getY() - task.getLocationY();
                        double dz = loc.getZ() - task.getLocationZ();
                        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

                        if (dist <= task.getLocationRadius()) {
                            if (task.complete()) {
                                onTaskCompleted(team, task, card);
                            }
                        }
                    }
                }
            }
        }, 20L, 20L); // cada segundo
    }

    public void stopLocationCheck() {
        if (locationTask != null && !locationTask.isCancelled()) locationTask.cancel();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void checkObtainTasks(EventTeam team, Material mat, int amount) {
        checkTasksByType(team, BingoTask.Type.OBTAIN_ITEM, mat, amount);
    }

    private void checkTasksByType(EventTeam team, BingoTask.Type type, Material mat, int amount) {
        BingoCard card = miniGame.getCard(team);
        if (card == null) return;

        for (BingoTask task : card.getTasks()) {
            if (task.getType() == type
                    && task.getMaterial() == mat
                    && !task.isCompleted()) {
                if (task.increment(amount)) {
                    onTaskCompleted(team, task, card);
                } else {
                    refreshOpenGUIs(team, card);
                }
            }
        }
    }

    /**
     * Se llama cuando una tarea se completa.
     * Notifica al equipo y verifica condición de victoria.
     */
    private void onTaskCompleted(EventTeam team, BingoTask task, BingoCard card) {
        // Anunciar en chat
        Bukkit.getOnlinePlayers().forEach(p ->
                p.sendMessage(Component.text("✔ ", NamedTextColor.GREEN)
                        .append(Component.text(team.getDisplayName(), team.getColor()))
                        .append(Component.text(" completó: ", NamedTextColor.GREEN))
                        .append(Component.text(task.getDisplayName(), NamedTextColor.YELLOW))
                        .append(Component.text("  (" + card.getCompletedCount()
                                + "/" + card.getTotalTasks() + ")", NamedTextColor.GRAY)))
        );

        // Actualizar GUI si algún miembro lo tiene abierto
        refreshOpenGUIs(team, card);

        // Verificar victoria
        miniGame.checkWinCondition(team, card);
    }

    /**
     * Actualiza el GUI de bingo para todos los miembros del equipo
     * que lo tengan abierto en ese momento.
     */
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