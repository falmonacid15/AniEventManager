package org.falmdev.anieventmanager.lobby;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Player;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.model.EventTeam;

import java.util.*;

/**
 * Aplica el estado visual de los carteles y lámparas registradas según
 * los datos actuales de cada equipo.
 *
 * Reglas:
 *
 *  Lámpara → apagada (lit=false) si el equipo tiene 1 o más miembros (reclamado),
 *           encendida (lit=true) si el equipo está vacío.
 *
 *  Cartel:
 *    Línea 0: nombre del equipo en su color, negrita
 *    Línea 1: miembros actuales / capacidad   (ej "1/2")
 *    Línea 2: nombres de los miembros (o "—" si no hay)
 *    Línea 3: "Haz clic" en gris
 *
 *  Solo se escribe en la cara frontal del cartel (Side.FRONT).
 *  Compatible con carteles normales y hanging signs (ambos extienden Sign).
 */
public class TeamLobbyManager {

    private final Anieventmanager plugin;
    private final TeamLobbyConfig config;

    public TeamLobbyManager(Anieventmanager plugin) {
        this.plugin   = plugin;
        this.config   = new TeamLobbyConfig(plugin);
    }

    public TeamLobbyConfig getConfig() { return config; }

    // ── Refresh global ────────────────────────────────────────────────────────

    /** Refresca todos los carteles y lámparas registradas. */
    public void refreshAll() {
        for (Block sign : config.getAllSignBlocks()) refreshSign(sign);
        for (Block lamp : config.getAllLampBlocks()) refreshLamp(lamp);
    }

    /** Refresca solo los carteles y lámparas asociadas a un equipo específico. */
    public void refreshForTeam(String teamId) {
        for (Block sign : config.getSignsForTeam(teamId)) refreshSign(sign);
        for (Block lamp : config.getLampsForTeam(teamId)) refreshLamp(lamp);
    }

    // ── Carteles ──────────────────────────────────────────────────────────────

    public void refreshSign(Block block) {
        if (block == null) return;
        String teamId = config.getSignTeam(block);
        if (teamId == null) return;

        BlockState state = block.getState();
        if (!(state instanceof Sign sign)) {
            // El bloque ya no es un cartel — desregistrar silenciosamente
            config.unregisterSign(block);
            return;
        }

        Optional<EventTeam> teamOpt = plugin.getTeamManager().getTeam(teamId);

        SignSide side = sign.getSide(Side.FRONT);

        if (teamOpt.isEmpty()) {
            // Equipo eliminado pero cartel sigue registrado
            side.line(0, Component.text("⚠ Equipo", NamedTextColor.RED));
            side.line(1, Component.text("eliminado", NamedTextColor.RED));
            side.line(2, Component.text("(" + teamId + ")", NamedTextColor.DARK_GRAY));
            side.line(3, Component.empty());
            sign.update(true, false);
            return;
        }

        EventTeam team = teamOpt.get();
        NamedTextColor color = team.getColor();

        // Línea 0: nombre del equipo en negrita y su color
        side.line(0, Component.text(team.getDisplayName(), color, TextDecoration.BOLD));

        // Línea 1: miembros X/2 (asumimos capacidad 2 según los comandos existentes)
        int current = team.getMemberCount();
        int max     = 2; // EventTeam.isFull() ya considera 2
        side.line(1, Component.text(current + "/" + max + " miembros", NamedTextColor.GRAY));

        // Línea 2: nombres de los miembros (o "—" si no hay)
        List<String> names = resolveMemberNames(team);
        Component membersLine;
        if (names.isEmpty()) {
            membersLine = Component.text("—", NamedTextColor.DARK_GRAY);
        } else if (names.size() == 1) {
            membersLine = Component.text(names.get(0), color);
        } else {
            // Truncar si es muy largo para caber en la línea
            membersLine = Component.text(
                    truncate(String.join(", ", names), 15),
                    color);
        }
        side.line(2, membersLine);

        // Línea 3: acción
        side.line(3, Component.text("» Haz clic «", NamedTextColor.GRAY));

        sign.update(true, false);
    }

    private List<String> resolveMemberNames(EventTeam team) {
        List<String> names = new ArrayList<>();
        for (UUID uuid : team.getMembers()) {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                names.add(online.getName());
            } else {
                var off = Bukkit.getOfflinePlayer(uuid);
                String n = off.getName();
                names.add(n != null ? n : uuid.toString().substring(0, 6));
            }
        }
        return names;
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    // ── Lámparas ──────────────────────────────────────────────────────────────

    public void refreshLamp(Block block) {
        if (block == null) return;
        String teamId = config.getLampTeam(block);
        if (teamId == null) return;

        BlockData data = block.getBlockData();
        if (!(data instanceof Lightable lightable)) {
            // El bloque ya no es lightable — desregistrar
            config.unregisterLamp(block);
            return;
        }

        Optional<EventTeam> teamOpt = plugin.getTeamManager().getTeam(teamId);

        // Lámpara apagada (oscura) cuando el equipo está reclamado (≥1 miembro)
        // Encendida (visible/brillante) cuando está disponible.
        // Si el equipo no existe, encendida por defecto.
        boolean lit = teamOpt.map(t -> t.getMemberCount() == 0).orElse(true);

        lightable.setLit(lit);
        block.setBlockData(lightable, false);
    }
}