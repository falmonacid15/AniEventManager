package org.falmdev.anieventmanager.minigames.parkourduos;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.model.EventTeam;

import java.util.List;

/**
 * Mecánica de "cadena" entre los dos jugadores del equipo.
 *
 * - Distancia máxima configurable.
 * - Si se separan demasiado: aplica fuerza de pull elástico hacia el compañero.
 * - Visual: partículas entre los dos jugadores (solo si están cerca).
 * - Se puede activar/desactivar globalmente en el plugin (como friendlyfire),
 *   pero en ParkourDuos siempre está activa durante la partida.
 */
public class ChainManager {

    private final Anieventmanager plugin;

    // Distancia a partir de la cual se aplica el pull
    private double maxDistance;

    // Fuerza del pull (bloques/tick²)
    private static final double PULL_FORCE         = 0.18;
    private static final double PULL_FORCE_STRONG  = 0.35; // si supera 1.5x la distancia máx
    private static final double PARTICLE_SPACING   = 0.6;

    private BukkitTask tickTask;

    // Estado global del sistema de cadena (activable por comando)
    private boolean chainEnabled;

    public ChainManager(Anieventmanager plugin, boolean chainEnabled, double maxDistance) {
        this.plugin       = plugin;
        this.chainEnabled = chainEnabled;
        this.maxDistance  = maxDistance;
    }

    // ── Ciclo de vida ─────────────────────────────────────────────────────────

    /**
     * Inicia el tick de la cadena para los equipos dados.
     * En ParkourDuos se llama al comenzar la partida.
     */
    public void startForTeams(List<EventTeam> teams) {
        stopTick();
        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (EventTeam team : teams) {
                List<Player> members = team.getOnlinePlayers();
                if (members.size() < 2) continue;
                Player p1 = members.get(0);
                Player p2 = members.get(1);
                tickChain(p1, p2);
            }
        }, 1L, 1L); // cada tick
    }

    public void stopTick() {
        if (tickTask != null && !tickTask.isCancelled()) tickTask.cancel();
    }

    // ── Lógica principal ──────────────────────────────────────────────────────

    private void tickChain(Player p1, Player p2) {
        if (!p1.isOnline() || !p2.isOnline()) return;
        if (!p1.getWorld().equals(p2.getWorld())) return;

        double dist = p1.getLocation().distance(p2.getLocation());

        // Partículas de cadena (solo si están a menos de maxDistance * 1.5)
        if (dist < maxDistance * 1.5) {
            drawChainParticles(p1, p2);
        }

        // No hace falta pull si están cerca
        if (dist <= maxDistance) return;

        // Calcular fuerza de pull
        double force = dist > maxDistance * 1.5 ? PULL_FORCE_STRONG : PULL_FORCE;

        applyPull(p1, p2, force);
        applyPull(p2, p1, force);

        // Avisar si se están alejando demasiado (throttle: solo cada ~1 seg = 20 ticks)
        if ((plugin.getServer().getCurrentTick() % 20) == 0) {
            Component msg = Component.text("🔗 ", NamedTextColor.GOLD)
                    .append(Component.text("¡Estás muy lejos de tu compañero!", NamedTextColor.YELLOW));
            p1.sendActionBar(msg);
            p2.sendActionBar(msg);
        }
    }

    /**
     * Aplica un vector de pull hacia el objetivo.
     * Conserva la velocidad Y del jugador (para no interrumpir saltos).
     */
    private void applyPull(Player pulled, Player target, double force) {
        Location from = pulled.getLocation();
        Location to   = target.getLocation();

        Vector direction = to.toVector().subtract(from.toVector()).normalize().multiply(force);

        // Preservar componente Y si el jugador está subiendo (saltando)
        Vector current = pulled.getVelocity();
        double newY    = current.getY() > 0.1 ? current.getY() * 0.9 : direction.getY();

        pulled.setVelocity(new Vector(
                current.getX() * 0.5 + direction.getX(),
                newY,
                current.getZ() * 0.5 + direction.getZ()
        ));
    }

    /**
     * Dibuja partículas a lo largo de la línea entre los dos jugadores.
     * Solo visibles para ellos mismos.
     */
    private void drawChainParticles(Player p1, Player p2) {
        Location loc1 = p1.getLocation().add(0, 1, 0);
        Location loc2 = p2.getLocation().add(0, 1, 0);

        double dist = loc1.distance(loc2);
        if (dist < 0.5) return;

        Vector step = loc2.toVector().subtract(loc1.toVector())
                .normalize().multiply(PARTICLE_SPACING);

        int steps = (int) (dist / PARTICLE_SPACING);
        Location cursor = loc1.clone();

        for (int i = 0; i < steps; i++) {
            cursor.add(step);
            p1.spawnParticle(Particle.DUST,
                    cursor, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(
                            org.bukkit.Color.fromRGB(180, 180, 180), 0.6f));
            p2.spawnParticle(Particle.DUST,
                    cursor, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(
                            org.bukkit.Color.fromRGB(180, 180, 180), 0.6f));
        }
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public boolean isChainEnabled()          { return chainEnabled; }
    public void    setChainEnabled(boolean v){ this.chainEnabled = v; }
    public double  getMaxDistance()          { return maxDistance; }
    public void    setMaxDistance(double v)  { this.maxDistance = v; }
}