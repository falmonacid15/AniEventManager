package org.falmdev.anieventmanager.minigames.pvpfinal.combat;

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

import java.util.List;

public class PvpFinalHologramManager {

    private static final String HOLOGRAM_ID = "pvpfinal_combat_info";

    private final Anieventmanager plugin;

    public PvpFinalHologramManager(Anieventmanager plugin) {
        this.plugin = plugin;
    }

    private boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("FancyHolograms");
    }

    public void show(Location location) {
        if (location == null || !isAvailable()) return;
        try {
            HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
            removeExisting(manager);

            TextHologramData data = new TextHologramData(HOLOGRAM_ID, location);
            data.setPersistent(false);
            data.setVisibility(Visibility.ALL);
            data.setVisibilityDistance(100);
            data.setScale(new Vector3f(2.0f, 2.0f, 2.0f));
            data.setShadowRadius(0.0f);
            data.setShadowStrength(1.0f);
            data.setTextShadow(true);
            data.setSeeThrough(false);
            data.setTextAlignment(TextDisplay.TextAlignment.CENTER);
            data.setTextUpdateInterval(1);
            data.setBackground(Color.fromARGB(200, 0, 0, 0));
            data.setBillboard(Display.Billboard.CENTER);
            data.setBrightness(new Display.Brightness(15, 0));
            data.setText(List.of(
                    "",
                    "&6&l⚔ COMBATE &e[%anievent_pvpfinal_combat_mode%]",
                    "",
                    "&7&l%anievent_pvpfinal_side_1_name% &fvs &7&l%anievent_pvpfinal_side_2_name%",
                    "",
                    "&7%anievent_pvpfinal_side_1_health% &c❤   &7┃   %anievent_pvpfinal_side_2_health% &c❤",
                    "",
                    "&e⏱ &f%anievent_pvpfinal_combat_time%s",
                    ""
            ));

            Hologram hologram = manager.create(data);
            manager.addHologram(hologram);
        } catch (Exception e) {
            plugin.getLogger().warning("[PvP] No se pudo mostrar el holograma: " + e.getMessage());
        }
    }

    public void hide() {
        if (!isAvailable()) return;
        try {
            removeExisting(FancyHologramsPlugin.get().getHologramManager());
        } catch (Exception e) {
            plugin.getLogger().warning("[PvP] No se pudo eliminar el holograma: " + e.getMessage());
        }
    }

    private void removeExisting(HologramManager manager) {
        manager.getHologram(HOLOGRAM_ID).ifPresent(manager::removeHologram);
    }
}