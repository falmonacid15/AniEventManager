package org.falmdev.anieventmanager.minigames.parkourduos;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.model.EventTeam;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Maneja la lógica de checkpoints durante la partida:
 * - Detectar si los dos jugadores están dentro del checkpoint activo
 * - Mostrar partículas en el checkpoint activo
 * - Mostrar action bar con progreso (jugadores dentro, checkpoint actual)
 * - Detectar si el equipo llega al finish
 */
public class CheckpointManager {

    private final Anieventmanager plugin;
    private final ParkourDuosMiniGame miniGame;
    private final ParkourDuosConfig config;

    private BukkitTask tickTask;

    // Partículas del checkpoint activo (se renderizan cada tick)
    private static final Particle CHECKPOINT_PARTICLE = Particle.HAPPY_VILLAGER;
    private static final Particle FINISH_PARTICLE      = Particle.FIREWORK;
    private static final double   PARTICLE_RADIUS      = 0.8;
    private static final int      PARTICLE_POINTS      = 12;

    public CheckpointManager(Anieventmanager plugin, ParkourDuosMiniGame miniGame) {
        this.plugin   = plugin;
        this.miniGame = miniGame;
        this.config   = miniGame.getConfig();
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    public void start() {
        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
    }

    public void stop() {
        if (tickTask != null && !tickTask.isCancelled()) tickTask.cancel();
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

            // ── Contar jugadores en el checkpoint activo ──────────────────────
            int inside = 0;
            if (active != null) {
                for (Player p : members) {
                    if (active.isInside(p.getLocation())) inside++;
                }
                data.setPlayersInCurrentCheckpoint(inside);
            }

            // ── Partículas del checkpoint activo ──────────────────────────────
            if (active != null) {
                spawnCheckpointParticles(active.getCenter(), members);
            }

            // ── Partículas del finish (siempre visibles cuando hay finish) ────
            Location finish = config.getTeamFinish(team.getId());
            if (finish != null && data.getActiveCheckpoint() == null && !data.isFinished()) {
                // Todos los checkpoints pasados — mostrar finish
                spawnFinishParticles(finish, members);
            }

            // ── Action bar ───────────────────────────────────────────────────
            showActionBar(team, data, members, inside);

            // ── Validar checkpoint (los 2 deben estar dentro) ─────────────────
            if (active != null && inside >= 2) {
                boolean allCheckpointsDone = data.advanceCheckpoint();
                data.addInternalScore(config.getScorePerCheckpoint());

                // Anunciar al equipo
                for (Player p : members) {
                    p.sendMessage(Component.text("✔ Checkpoint ", NamedTextColor.GREEN)
                            .append(Component.text(data.getCompletedCheckpoints() + "/" + data.getTotalCheckpoints(),
                                    NamedTextColor.YELLOW))
                            .append(Component.text(" completado!", NamedTextColor.GREEN)));
                }

                if (allCheckpointsDone) {
                    // Solo queda llegar al finish — mostrar mensaje
                    for (Player p : members) {
                        p.sendMessage(Component.text(
                                "🏁 ¡Todos los checkpoints completados! ¡Ve al finish!",
                                NamedTextColor.GOLD));
                    }
                }
            }

            // ── Validar finish ────────────────────────────────────────────────
            if (finish != null && data.getActiveCheckpoint() == null && !data.isFinished()) {
                // Revisar si los 2 jugadores están en el finish
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

    // ── Partículas ────────────────────────────────────────────────────────────

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
        // Línea vertical
        for (double dy = 0; dy <= 2.0; dy += 0.5) {
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

    // ── Action bar ────────────────────────────────────────────────────────────

    private void showActionBar(EventTeam team, TeamParkourData data,
                               List<Player> members, int insideCurrent) {
        ParkourCheckpoint active = data.getActiveCheckpoint();

        Component bar;
        if (active == null) {
            // Todos los CPs completados, ir al finish
            bar = Component.text("🏁 Ve al ", NamedTextColor.GOLD)
                    .append(Component.text("FINISH", NamedTextColor.GREEN)
                            .decoration(TextDecoration.BOLD, true))
                    .append(Component.text("  —  ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(data.getCompletedCheckpoints() + "/" + data.getTotalCheckpoints() + " CPs",
                            NamedTextColor.YELLOW));
        } else {
            // Mostrar CP activo y jugadores dentro
            NamedTextColor insideColor = insideCurrent >= 2 ? NamedTextColor.GREEN
                    : insideCurrent == 1 ? NamedTextColor.YELLOW
                      : NamedTextColor.RED;

            bar = Component.text("📍 CP ", NamedTextColor.AQUA)
                    .append(Component.text((active.getIndex() + 1) + "/" + data.getTotalCheckpoints(),
                            NamedTextColor.WHITE))
                    .append(Component.text("  —  ", NamedTextColor.DARK_GRAY))
                    .append(Component.text("Jugadores: ", NamedTextColor.GRAY))
                    .append(Component.text(insideCurrent + "/2", insideColor));
        }

        for (Player p : members) {
            p.sendActionBar(bar);
        }
    }
}