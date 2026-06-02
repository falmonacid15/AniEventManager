package org.falmdev.anieventmanager.minigames.boatracing;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.scheduler.BukkitTask;
import org.falmdev.anieventmanager.Anieventmanager;

import java.util.List;

/**
 * Animación de las 5 luces de largada.
 *
 * Secuencia:
 *   1. Todas apagadas
 *   2. Se enciende una por segundo (5 segundos)
 *   3. Pausa 1 segundo con las 5 encendidas
 *   4. Se apagan todas → callback (inicia la carrera)
 */
public class BoatRacingLights {

    private final Anieventmanager plugin;
    private final List<Location>  lights;
    private final Runnable        onComplete;
    private BukkitTask            task;

    public BoatRacingLights(Anieventmanager plugin,
                            List<Location> lights,
                            Runnable onComplete) {
        this.plugin     = plugin;
        this.lights     = lights;
        this.onComplete = onComplete;
    }

    public void start() {
        // Apagar todas primero
        lights.forEach(l -> setLamp(l, false));

        int[] step = {0};
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (step[0] < lights.size()) {
                setLamp(lights.get(step[0]), true);
                step[0]++;
            } else {
                task.cancel();
                // 1 segundo con todas encendidas → apagar y callback
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    lights.forEach(l -> setLamp(l, false));
                    plugin.getServer().getScheduler().runTaskLater(plugin,
                            onComplete::run, 3L);
                }, 20L);
            }
        }, 20L, 20L);
    }

    public void cancel() {
        if (task != null && !task.isCancelled()) task.cancel();
        lights.forEach(l -> setLamp(l, false));
    }

    private void setLamp(Location loc, boolean on) {
        Block block = loc.getBlock();
        if (block.getType() != Material.REDSTONE_LAMP)
            block.setType(Material.REDSTONE_LAMP, false);
        BlockData data = block.getBlockData();
        if (data instanceof Lightable lamp) {
            lamp.setLit(on);
            block.setBlockData(lamp, false);
        }
    }
}