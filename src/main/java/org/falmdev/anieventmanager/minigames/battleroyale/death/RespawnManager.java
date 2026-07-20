package org.falmdev.anieventmanager.minigames.battleroyale.death;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitTask;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.minigames.battleroyale.BattleRoyaleMiniGame;
import org.falmdev.anieventmanager.minigames.battleroyale.model.BRPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RespawnManager {

    private static final int    ANIMATION_TOTAL_TICKS = 60;
    private static final int    SPAWN_TICK            = 30;
    private static final double HELIX_RADIUS          = 1.0;
    private static final double HELIX_HEIGHT          = 3.0;
    private static final int    PARTICLES_PER_TICK    = 4;

    private static final Color[] FIREWORK_COLORS = {
            Color.RED, Color.YELLOW, Color.AQUA, Color.LIME, Color.FUCHSIA, Color.ORANGE
    };

    private final Anieventmanager      plugin;
    private final BattleRoyaleMiniGame game;

    public RespawnManager(Anieventmanager plugin, BattleRoyaleMiniGame game) {
        this.plugin = plugin;
        this.game   = game;
    }

    public List<Location> getRespawnPoints() {
        return game.getConfig().getRespawnPoints();
    }

    public void addRespawnPoint(Location loc) {
        game.getConfig().addRespawnPoint(loc);
    }

    public boolean removeRespawnPoint(int index) {
        return game.getConfig().removeRespawnPoint(index);
    }

    public void clearRespawnPoints() {
        game.getConfig().clearRespawnPoints();
    }

    public Location getClosestRespawnPoint(Location from) {
        List<Location> points = getRespawnPoints();
        if (points.isEmpty()) return null;
        if (from == null) return points.get(0);

        Location closest = null;
        double minDist = Double.MAX_VALUE;
        for (Location p : points) {
            if (p.getWorld() != from.getWorld()) continue;
            double d = p.distanceSquared(from);
            if (d < minDist) { minDist = d; closest = p; }
        }
        return closest != null ? closest : points.get(0);
    }

    public enum ReviveResult {
        OK,
        TARGET_OFFLINE,
        TARGET_NOT_IN_GAME,
        TARGET_NOT_DEAD,
        NO_RESPAWN_POINTS,
        DIFFERENT_TEAM,
        SAME_PLAYER
    }

    public ReviveResult revivePlayer(Player target, Player reviver) {
        if (target == null || !target.isOnline()) return ReviveResult.TARGET_OFFLINE;
        if (reviver != null && reviver.equals(target)) return ReviveResult.SAME_PLAYER;

        BRPlayer brpTarget = game.getBRPlayer(target);
        if (brpTarget == null) return ReviveResult.TARGET_NOT_IN_GAME;
        if (!brpTarget.isDead()) return ReviveResult.TARGET_NOT_DEAD;

        if (reviver != null) {
            var teamReviver = plugin.getTeamManager().getTeamOf(reviver);
            var teamTarget  = plugin.getTeamManager().getTeamOf(target);
            if (teamReviver.isEmpty() || teamTarget.isEmpty()) return ReviveResult.DIFFERENT_TEAM;
            if (!teamReviver.get().getId().equals(teamTarget.get().getId()))
                return ReviveResult.DIFFERENT_TEAM;
        }

        Location reviveLoc;
        if (reviver != null) {
            reviveLoc = getClosestRespawnPoint(reviver.getLocation());
        } else {
            reviveLoc = getClosestRespawnPoint(target.getLocation());
        }
        if (reviveLoc == null) {
            reviveLoc = game.getConfig().getLobbySpawn();
        }
        if (reviveLoc == null) return ReviveResult.NO_RESPAWN_POINTS;

        playReviveAnimation(target, reviveLoc.clone(), brpTarget);
        return ReviveResult.OK;
    }

    private void playReviveAnimation(Player target, Location spawnLoc, BRPlayer brp) {
        final Location loc = spawnLoc.clone();
        final World world  = loc.getWorld();

        world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.2f);

        new org.bukkit.scheduler.BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                tick++;

                drawHelix(world, loc, tick);

                if (tick == SPAWN_TICK) {
                    if (target.isOnline()) {
                        brp.setState(BRPlayer.State.ALIVE);
                        brp.setHasLanded(true);
                        target.setGameMode(GameMode.SURVIVAL);
                        target.getInventory().clear();
                        target.setHealth(20);
                        target.setFoodLevel(20);
                        target.teleport(loc);

                        world.playSound(loc, Sound.ITEM_TRIDENT_THUNDER, 0.8f, 1.5f);
                        world.spawnParticle(Particle.FLASH, loc, 1);
                        launchCelebrationFireworks(world, loc);

                        target.sendMessage(Component.text("✔ Has sido revivido. ¡Vuelve a la lucha!",
                                NamedTextColor.GREEN));
                    }
                }

                if (tick >= ANIMATION_TOTAL_TICKS) {
                    world.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void drawHelix(World world, Location base, int tick) {
        Particle.DustOptions cyan = new Particle.DustOptions(Color.AQUA, 1.5f);

        for (int i = 0; i < PARTICLES_PER_TICK; i++) {
            double yProgress = ((tick * PARTICLES_PER_TICK + i) % (ANIMATION_TOTAL_TICKS * PARTICLES_PER_TICK))
                    / (double)(ANIMATION_TOTAL_TICKS * PARTICLES_PER_TICK);
            double y = yProgress * HELIX_HEIGHT;

            double angle1 = yProgress * Math.PI * 6;
            double angle2 = angle1 + Math.PI;

            double x1 = Math.cos(angle1) * HELIX_RADIUS;
            double z1 = Math.sin(angle1) * HELIX_RADIUS;
            double x2 = Math.cos(angle2) * HELIX_RADIUS;
            double z2 = Math.sin(angle2) * HELIX_RADIUS;

            world.spawnParticle(Particle.DUST,
                    base.getX() + x1, base.getY() + y, base.getZ() + z1,
                    1, 0, 0, 0, 0, cyan);
            world.spawnParticle(Particle.DUST,
                    base.getX() + x2, base.getY() + y, base.getZ() + z2,
                    1, 0, 0, 0, 0, cyan);
        }

        if (tick % 5 == 0) {
            for (int i = 0; i < 16; i++) {
                double angle = (i * Math.PI * 2) / 16;
                double x = Math.cos(angle) * (HELIX_RADIUS + 0.3);
                double z = Math.sin(angle) * (HELIX_RADIUS + 0.3);
                world.spawnParticle(Particle.END_ROD,
                        base.getX() + x, base.getY() + 0.1, base.getZ() + z,
                        1, 0, 0, 0, 0);
            }
        }
    }

    private void launchCelebrationFireworks(World world, Location loc) {
        int count = 2 + ThreadLocalRandom.current().nextInt(2);
        for (int i = 0; i < count; i++) {
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> spawnSingleFirework(world, loc), i * 4L);
        }
    }

    private void spawnSingleFirework(World world, Location loc) {
        Firework firework = (Firework) world.spawnEntity(loc, EntityType.FIREWORK_ROCKET);
        FireworkMeta meta = firework.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
                .withColor(randomFireworkColor())
                .withFade(randomFireworkColor())
                .with(pickRandomFireworkType())
                .trail(true)
                .flicker(true)
                .build());
        meta.setPower(0);
        firework.setFireworkMeta(meta);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!firework.isDead()) firework.detonate();
        }, 2L);
    }

    private Color randomFireworkColor() {
        return FIREWORK_COLORS[ThreadLocalRandom.current().nextInt(FIREWORK_COLORS.length)];
    }

    private FireworkEffect.Type pickRandomFireworkType() {
        FireworkEffect.Type[] types = FireworkEffect.Type.values();
        return types[ThreadLocalRandom.current().nextInt(types.length)];
    }
}