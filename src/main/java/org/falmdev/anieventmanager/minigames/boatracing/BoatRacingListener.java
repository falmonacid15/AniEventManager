package org.falmdev.anieventmanager.minigames.boatracing;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Boat;import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.scheduler.BukkitTask;
import org.falmdev.anieventmanager.Anieventmanager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BoatRacingListener implements Listener {

    // ms mínimos entre dos cruces del mismo jugador (evita doble detección)
    private static final long FINISH_COOLDOWN_MS = 3000;

    private final Anieventmanager   plugin;
    private final BoatRacingMiniGame miniGame;

    private boolean frozen = false;

    // Último cruce de meta por jugador
    private final Map<UUID, Long> lastFinishCross = new HashMap<>();

    private BukkitTask checkpointTask;
    private BukkitTask raceActionBarTask;
    private BukkitTask qualyActionBarTask;
    private BukkitTask gridActionBarTask;

    // Velocidad por jugador — posición anterior para calcular m/s
    private final Map<UUID, org.bukkit.Location> lastLocations = new HashMap<>();

    public BoatRacingListener(Anieventmanager plugin, BoatRacingMiniGame miniGame) {
        this.plugin   = plugin;
        this.miniGame = miniGame;
    }

    // ── Control ───────────────────────────────────────────────────────────────

    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
        if (!frozen) lastFinishCross.clear();
    }

    // ── Tick de checkpoints ───────────────────────────────────────────────────

    public void startCheckpointTick() {
        List<BoatRacingConfig.CheckpointData> checkpoints = miniGame.getConfig().getCheckpoints();
        if (checkpoints.isEmpty()) return;

        checkpointTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (miniGame.getState() != BoatRacingMiniGame.State.RACE) return;

            miniGame.getRacers().forEach((uuid, rd) -> {
                Player p = plugin.getServer().getPlayer(uuid);
                if (p == null || rd.isRaceFinished()) return;

                Location loc = p.getLocation();
                int next = rd.getLastCheckpoint() + 1;
                if (next >= checkpoints.size()) return;

                BoatRacingConfig.CheckpointData cp = checkpoints.get(next);
                if (loc.getWorld().getName().equals(cp.location().getWorld().getName())
                        && loc.distance(cp.location()) <= cp.radius()) {
                    rd.reachCheckpoint(next);
                }
            });
        }, 0L, 4L);
    }

    public void stopCheckpointTick() {
        if (checkpointTask != null && !checkpointTask.isCancelled())
            checkpointTask.cancel();
        lastFinishCross.clear();
    }

    // ── ActionBar durante la espera en parrilla ────────────────────────────────

    /**
     * Muestra la posición de parrilla mientras esperan las luces.
     * Ej: "🏎 Posición de parrilla: #3  |  Prepárate..."
     */
    public void startGridActionBar() {
        stopGridActionBar();
        gridActionBarTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            miniGame.getRacers().forEach((uuid, rd) -> {
                Player p = plugin.getServer().getPlayer(uuid);
                if (p == null) return;
                int gridPos = rd.getQualyPosition() > 0 ? rd.getQualyPosition()
                        : new java.util.ArrayList<>(miniGame.getRacers().keySet()).indexOf(uuid) + 1;
                p.sendActionBar(Component.text(
                        "🏎 Parrilla: #" + gridPos + "  |  Mira las luces...",
                        NamedTextColor.YELLOW));
            });
        }, 0L, 10L);
    }

    public void stopGridActionBar() {
        if (gridActionBarTask != null && !gridActionBarTask.isCancelled())
            gridActionBarTask.cancel();
    }

    // ── ActionBar durante la qualy ────────────────────────────────────────────

    /**
     * Muestra el cronómetro de vuelta durante la qualy.
     * Ej: "⏱ 1:23.456  |  ¡Cruza la meta!"
     */
    public void startQualyActionBar() {
        stopQualyActionBar();
        qualyActionBarTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (miniGame.getState() != BoatRacingMiniGame.State.QUALY) return;
            miniGame.getRacers().forEach((uuid, rd) -> {
                Player p = plugin.getServer().getPlayer(uuid);
                if (p == null) return;
                if (rd.isQualyFinished()) {
                    p.sendActionBar(Component.text(
                            "✔ Qualy finalizada: " + rd.getQualyTimeFormatted(),
                            NamedTextColor.GREEN));
                    return;
                }
                if (!rd.isQualyStarted()) {
                    p.sendActionBar(Component.text(
                            "🏎 Cruza la meta para iniciar el cronómetro",
                            NamedTextColor.YELLOW));
                } else {
                    p.sendActionBar(Component.text(
                            "⏱ " + rd.getCurrentLapTimeFormatted() + "  |  ¡Vuelve a la meta!",
                            NamedTextColor.AQUA));
                }
            });
        }, 0L, 4L); // cada 4 ticks = 0.2s para que el timer sea fluido
    }

    public void stopQualyActionBar() {
        if (qualyActionBarTask != null && !qualyActionBarTask.isCancelled())
            qualyActionBarTask.cancel();
    }

    // ── ActionBar durante la carrera ──────────────────────────────────────────

    /**
     * Muestra posición, vuelta, tiempo de vuelta y velocidad durante la carrera.
     * Ej: "#2  |  Vuelta 2/3  |  1:23.456  |  45.2 km/h"
     */
    public void startRaceActionBar() {
        stopRaceActionBar();
        raceActionBarTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (miniGame.getState() != BoatRacingMiniGame.State.RACE) return;

            miniGame.getRacers().forEach((uuid, rd) -> {
                Player p = plugin.getServer().getPlayer(uuid);
                if (p == null) return;

                if (rd.isRaceFinished()) {
                    p.sendActionBar(Component.text(
                            "🏁 Terminaste en posición #" + rd.getRacePosition()
                                    + "  |  Mejor vuelta: " + rd.getBestLapFormatted(),
                            NamedTextColor.GOLD));
                    return;
                }

                // Aún no cruzó la meta por primera vez
                if (!rd.isRaceStarted()) {
                    p.sendActionBar(Component.text(
                            "🏁 Cruza la meta para iniciar tu vuelta 1",
                            NamedTextColor.YELLOW));
                    return;
                }

                int pos     = miniGame.getRealTimePosition(uuid);
                int total   = miniGame.getRacers().size();
                int lap     = rd.getCurrentLap();
                int maxLaps = miniGame.getConfig().getTotalLaps();
                String lapTime = rd.getCurrentLapTimeFormatted();
                double speed   = getSpeedKmh(p);

                p.sendActionBar(
                        Component.text("#" + pos + "/" + total, NamedTextColor.GOLD)
                                .append(Component.text("  ┃  ", NamedTextColor.DARK_GRAY))
                                .append(Component.text("V" + lap + "/" + maxLaps, NamedTextColor.WHITE))
                                .append(Component.text("  ┃  ", NamedTextColor.DARK_GRAY))
                                .append(Component.text("⏱ " + lapTime, NamedTextColor.AQUA))
                                .append(Component.text("  ┃  ", NamedTextColor.DARK_GRAY))
                                .append(Component.text(String.format("%.1f", speed) + " km/h",
                                        speed > 20 ? NamedTextColor.GREEN : NamedTextColor.YELLOW))
                );
            });
        }, 0L, 4L); // cada 4 ticks para velocidad fluida
    }

    public void stopRaceActionBar() {
        if (raceActionBarTask != null && !raceActionBarTask.isCancelled())
            raceActionBarTask.cancel();
        lastLocations.clear();
    }

    // ── Cálculo de velocidad ──────────────────────────────────────────────────

    /**
     * Calcula la velocidad del jugador en km/h basándose en la distancia
     * recorrida desde la última vez que se llamó este método.
     * 1 bloque de Minecraft ≈ 1 metro. 4 ticks = 0.2s → x5 = bloques/s → x3.6 = km/h
     */
    private double getSpeedKmh(Player player) {
        org.bukkit.Location current = player.getLocation();
        org.bukkit.Location last    = lastLocations.put(player.getUniqueId(), current);
        if (last == null || !last.getWorld().equals(current.getWorld())) return 0;
        double distBlocks = last.distance(current); // bloques en 4 ticks (0.2s)
        double blocksPerSecond = distBlocks * 5;    // × 5 para pasar a bloques/s
        return blocksPerSecond * 3.6;               // × 3.6 para km/h
    }

    // ── Movimiento del vehículo ───────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onVehicleMove(VehicleMoveEvent event) {
        if (!(event.getVehicle() instanceof Boat)) return;
        var passengers = event.getVehicle().getPassengers();
        if (passengers.isEmpty()) return;
        if (!(passengers.get(0) instanceof Player player)) return;
        if (!miniGame.getRacers().containsKey(player.getUniqueId())) return;

        // Congelar durante la animación de luces
        if (frozen) {
            event.getVehicle().teleport(event.getFrom());
            return;
        }

        BoatRacingMiniGame.State state = miniGame.getState();
        if (state != BoatRacingMiniGame.State.QUALY
                && state != BoatRacingMiniGame.State.RACE) return;

        TrackRegion track = miniGame.getTrackRegion();
        if (track == null || !track.hasFinishLine()) return;
        if (!track.isCrossingFinish(event.getTo())) return;

        // Cooldown anti doble detección
        UUID uuid = player.getUniqueId();
        long now  = System.currentTimeMillis();
        Long last = lastFinishCross.get(uuid);
        if (last != null && now - last < FINISH_COOLDOWN_MS) return;
        lastFinishCross.put(uuid, now);

        if (state == BoatRacingMiniGame.State.QUALY) {
            miniGame.onQualyCrossedFinish(uuid);
        } else {
            miniGame.onRaceCrossedFinish(uuid);
        }
    }

    // ── Evitar que los jugadores se bajen del bote ────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onVehicleExit(VehicleExitEvent event) {
        if (!(event.getExited() instanceof Player player)) return;
        if (!miniGame.getRacers().containsKey(player.getUniqueId())) return;

        BoatRacingMiniGame.State state = miniGame.getState();
        if (state == BoatRacingMiniGame.State.QUALY
                || state == BoatRacingMiniGame.State.RACE) {
            event.setCancelled(true);
            player.sendMessage(Component.text(
                    "No puedes bajarte del bote durante la carrera.", NamedTextColor.RED));
        }
    }
}