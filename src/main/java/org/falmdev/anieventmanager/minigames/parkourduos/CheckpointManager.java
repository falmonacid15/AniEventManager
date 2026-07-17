package org.falmdev.anieventmanager.minigames.parkourduos;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.model.EventTeam;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CheckpointManager {

    private final Anieventmanager     plugin;
    private final ParkourDuosMiniGame miniGame;
    private final ParkourDuosConfig   config;
    private final ParkourCheckpointHologramManager hologramManager;

    private BukkitTask tickTask;
    private final Map<String, ParkourCheckpoint> lastHologramCheckpoint = new HashMap<>();
    private final Map<String, Integer> lastInsideCount = new HashMap<>();

    private static final Particle CHECKPOINT_PARTICLE = Particle.HAPPY_VILLAGER;
    private static final Particle FINISH_PARTICLE     = Particle.FIREWORK;
    private static final double   PARTICLE_RADIUS     = 0.8;
    private static final int      PARTICLE_POINTS     = 12;
    private static final double   PARTICLE_COLUMN_HEIGHT = 1.0;

    private static final Sound PARTNER_AT_CHECKPOINT_SOUND = Sound.BLOCK_NOTE_BLOCK_BELL;
    private static final float PARTNER_AT_CHECKPOINT_VOLUME = 0.5f;
    private static final float PARTNER_AT_CHECKPOINT_PITCH = 1.2f;

    private static final Sound CHECKPOINT_CAPTURED_SOUND = Sound.ENTITY_PLAYER_LEVELUP;
    private static final float CHECKPOINT_CAPTURED_VOLUME = 0.7f;
    private static final float CHECKPOINT_CAPTURED_PITCH = 1.0f;

    public CheckpointManager(Anieventmanager plugin, ParkourDuosMiniGame miniGame) {
        this.plugin   = plugin;
        this.miniGame = miniGame;
        this.config   = miniGame.getConfig();
        this.hologramManager = new ParkourCheckpointHologramManager(plugin);
    }

    public void start() {
        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
    }

    public void stop() {
        if (tickTask != null && !tickTask.isCancelled()) tickTask.cancel();
        hologramManager.hideAll();
        lastHologramCheckpoint.clear();
        lastInsideCount.clear();
    }

    private void tick() {
        Map<String, TeamParkourData> teamData = miniGame.getTeamData();
        Collection<EventTeam> teams = plugin.getTeamManager().getAllTeams();

        for (EventTeam team : teams) {
            TeamParkourData data = teamData.get(team.getId());
            if (data == null || data.isFinished()) continue;

            List<Player> members = team.getOnlinePlayers();
            if (members.isEmpty()) continue;

            ParkourCheckpoint active = data.getActiveCheckpoint();

            if (active != null) {
                if (lastHologramCheckpoint.get(team.getId()) != active) {
                    hologramManager.show(team.getId(), active.getCenter(),
                            data.getCompletedCheckpoints() + 1, data.getTotalCheckpoints());
                    lastHologramCheckpoint.put(team.getId(), active);
                    lastInsideCount.put(team.getId(), 0);
                }
            } else if (lastHologramCheckpoint.remove(team.getId()) != null) {
                hologramManager.hide(team.getId());
                lastInsideCount.remove(team.getId());
            }

            int inside = 0;
            if (active != null) {
                for (Player p : members) {
                    if (active.isInside(p.getLocation())) inside++;
                }
                data.setPlayersInCurrentCheckpoint(inside);

                int previousInside = lastInsideCount.getOrDefault(team.getId(), 0);
                if (inside > previousInside && inside < 2 && members.size() > 1) {
                    notifyPartnerAtCheckpoint(members, active);
                }
                lastInsideCount.put(team.getId(), inside);
            }

            if (active != null) {
                spawnCheckpointParticles(active.getCenter(), members);
            }

            Location finish = config.getTeamFinish(team.getId());
            if (finish != null && data.getActiveCheckpoint() == null && !data.isFinished()) {
                spawnFinishParticles(finish, members);
            }

            if (active != null && inside >= 2) {
                boolean allCheckpointsDone = data.advanceCheckpoint();
                data.addInternalScore(config.getScorePerCheckpoint());

                for (Player p : members) {
                    p.playSound(p.getLocation(), CHECKPOINT_CAPTURED_SOUND,
                            CHECKPOINT_CAPTURED_VOLUME, CHECKPOINT_CAPTURED_PITCH);
                    p.sendMessage(Component.text("✔ Checkpoint ", NamedTextColor.GREEN)
                            .append(Component.text(
                                    data.getCompletedCheckpoints() + "/" + data.getTotalCheckpoints(),
                                    NamedTextColor.YELLOW))
                            .append(Component.text(" completado!", NamedTextColor.GREEN)));
                }

                if (allCheckpointsDone) {
                    for (Player p : members) {
                        p.sendMessage(Component.text(
                                "🏁 ¡Todos los checkpoints completados! ¡Ve al finish!",
                                NamedTextColor.GOLD));
                    }
                }
            }

            if (finish != null && data.getActiveCheckpoint() == null && !data.isFinished()) {
                int inFinish = 0;
                for (Player p : members) {
                    if (finish.distanceSquared(p.getLocation()) <= 4.0 * 4.0) inFinish++;
                }
                if (inFinish >= 2) {
                    miniGame.onTeamFinished(team, data);
                }
            }
        }
    }

    private void notifyPartnerAtCheckpoint(List<Player> members, ParkourCheckpoint checkpoint) {
        for (Player p : members) {
            if (!checkpoint.isInside(p.getLocation())) {
                p.playSound(p.getLocation(), PARTNER_AT_CHECKPOINT_SOUND,
                        PARTNER_AT_CHECKPOINT_VOLUME, PARTNER_AT_CHECKPOINT_PITCH);
            }
        }
    }

    private void spawnCheckpointParticles(Location center, List<Player> receivers) {
        for (int i = 0; i < PARTICLE_POINTS; i++) {
            double angle = (2 * Math.PI / PARTICLE_POINTS) * i;
            double px = center.getX() + PARTICLE_RADIUS * Math.cos(angle);
            double pz = center.getZ() + PARTICLE_RADIUS * Math.sin(angle);
            Location particleLoc = new Location(center.getWorld(), px, center.getY() + 0.1, pz);
            for (Player p : receivers) {
                p.spawnParticle(CHECKPOINT_PARTICLE, particleLoc, 1, 0, 0, 0, 0);
            }
        }
        for (double dy = 0; dy <= PARTICLE_COLUMN_HEIGHT; dy += 0.5) {
            Location top = center.clone().add(0, dy, 0);
            for (Player p : receivers) {
                p.spawnParticle(CHECKPOINT_PARTICLE, top, 1, 0.2, 0, 0.2, 0);
            }
        }
    }

    private void spawnFinishParticles(Location finish, List<Player> receivers) {
        for (int i = 0; i < PARTICLE_POINTS; i++) {
            double angle = (2 * Math.PI / PARTICLE_POINTS) * i;
            double px = finish.getX() + 1.5 * Math.cos(angle);
            double pz = finish.getZ() + 1.5 * Math.sin(angle);
            Location loc = new Location(finish.getWorld(), px, finish.getY() + 0.1, pz);
            for (Player p : receivers) {
                p.spawnParticle(FINISH_PARTICLE, loc, 1, 0, 0.2, 0, 0.05);
            }
        }
    }
}