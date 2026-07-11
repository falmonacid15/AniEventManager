package org.falmdev.anieventmanager.minigames.parkourduos;

import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.data.property.Visibility;
import de.oliver.fancyholograms.api.hologram.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.falmdev.anieventmanager.Anieventmanager;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ParkourCheckpointHologramManager {

    private static final String HOLOGRAM_PREFIX = "parkourduos_checkpoint_";
    private static final double  HOLOGRAM_HEIGHT = 1.5;

    private final Anieventmanager plugin;
    private final Set<String> activeIds = new HashSet<>();

    public ParkourCheckpointHologramManager(Anieventmanager plugin) {
        this.plugin = plugin;
    }

    private boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("FancyHolograms");
    }

    public void show(String teamId, Location center, int checkpointNumber, int totalCheckpoints) {
        if (!isAvailable()) return;
        try {
            HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
            String id = HOLOGRAM_PREFIX + teamId;
            remove(manager, id);

            Location holoLoc = center.clone().add(0, HOLOGRAM_HEIGHT, 0);

            TextHologramData data = new TextHologramData(id, holoLoc);
            data.setPersistent(false);
            data.setVisibility(Visibility.ALL);
            data.setVisibilityDistance(20);
            data.setScale(new Vector3f(0.7f, 0.7f, 0.7f));
            data.setShadowRadius(0.0f);
            data.setShadowStrength(1.0f);
            data.setTextShadow(true);
            data.setSeeThrough(true);
            data.setTextAlignment(TextDisplay.TextAlignment.CENTER);
            data.setBackground(Color.fromARGB(160, 0, 0, 0));
            data.setBillboard(Display.Billboard.CENTER);
            data.setBrightness(new Display.Brightness(15, 0));
            data.setText(List.of(
                    "&a&lPunto de control",
                    "&7&l\uD83E\uDC0B"
            ));

            Hologram hologram = manager.create(data);
            manager.addHologram(hologram);
            activeIds.add(id);
        } catch (Exception e) {
            plugin.getLogger().warning("[ParkourDuos] No se pudo mostrar el holograma del checkpoint: " + e.getMessage());
        }
    }

    public void hide(String teamId) {
        if (!isAvailable()) return;
        try {
            HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
            remove(manager, HOLOGRAM_PREFIX + teamId);
        } catch (Exception e) {
            plugin.getLogger().warning("[ParkourDuos] No se pudo eliminar el holograma del checkpoint: " + e.getMessage());
        }
    }

    public void hideAll() {
        if (!isAvailable()) return;
        try {
            HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
            for (String id : new HashSet<>(activeIds)) {
                remove(manager, id);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[ParkourDuos] No se pudieron eliminar los hologramas de checkpoints: " + e.getMessage());
        }
    }

    private void remove(HologramManager manager, String id) {
        manager.getHologram(id).ifPresent(manager::removeHologram);
        activeIds.remove(id);
    }
}