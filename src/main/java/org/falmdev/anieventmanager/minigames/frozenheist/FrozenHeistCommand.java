package org.falmdev.anieventmanager.minigames.frozenheist;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.model.EventTeam;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Comandos del Frozen Heist.
 *
 * ── Configuración global ──────────────────────────────────────────
 *   /em frozenheist setspawn               → Spawn global pre-partida
 *   /em frozenheist setduration <mins>     → Duración de la partida
 *   /em frozenheist settings               → Ver configuración completa
 *
 * ── Configuración por equipo ──────────────────────────────────────
 *   /em frozenheist setbasespawn <id>      → Spawn de respawn de la base
 *   /em frozenheist setcapture <id>        → Zona de entrega de bandera
 *   /em frozenheist setflag <id>           → Posición de la bandera propia
 *   /em frozenheist setbase <id> 1|2       → Esquinas de la zona segura
 *   /em frozenheist teaminfo <id>          → Ver config de un equipo
 *
 * ── Control ───────────────────────────────────────────────────────
 *   /em frozenheist start                  → Iniciar partida
 *   /em frozenheist stop                   → Detener partida
 */
public class FrozenHeistCommand {

    private final Anieventmanager plugin;
    private final FrozenHeistMiniGame miniGame;

    public FrozenHeistCommand(Anieventmanager plugin, FrozenHeistMiniGame miniGame) {
        this.plugin   = plugin;
        this.miniGame = miniGame;
    }

    public void handle(Player player, String[] args) {
        if (args.length == 0) { sendHelp(player); return; }

        switch (args[0].toLowerCase()) {

            // ── Configuración global ──────────────────────────────────────────

            case "setspawn" -> {
                miniGame.getConfig().setGlobalSpawn(player.getLocation());
                ok(player, "Spawn global seteado en tu posición.");
            }

            case "setduration" -> {
                if (args.length < 2) { err(player, "Uso: /em frozenheist setduration <minutos>"); return; }
                try {
                    int mins = Integer.parseInt(args[1]);
                    if (mins < 1) { err(player, "Mínimo 1 minuto."); return; }
                    miniGame.getConfig().setDurationMinutes(mins);
                    ok(player, "Duración seteada a " + mins + " minutos.");
                } catch (NumberFormatException e) { err(player, "Número inválido."); }
            }

            case "settings" -> {
                var cfg = miniGame.getConfig();
                var teams = plugin.getTeamManager().getAllTeams();
                player.sendMessage(Component.text("━━━ Frozen Heist — Configuración ━━━", NamedTextColor.AQUA));
                info(player, "Estado",       stateToSpanish(miniGame.getState()));
                info(player, "Duración",     cfg.getDurationMinutes() + " minutos");
                info(player, "Spawn global", cfg.getGlobalSpawn() != null ? locStr(cfg.getGlobalSpawn()) : "no configurado");
                info(player, "Equipos",      String.valueOf(teams.size()));

                for (EventTeam team : teams) {
                    String id = team.getId();
                    player.sendMessage(Component.text("  ─ ", NamedTextColor.DARK_GRAY)
                            .append(Component.text(team.getDisplayName(), team.getColor())));
                    info(player, "    base-spawn",   cfg.getBaseSpawn(id)   != null ? "✔" : "✘ falta");
                    info(player, "    capture-zone", cfg.getCaptureZone(id) != null ? "✔" : "✘ falta");
                    info(player, "    flag-stand",   cfg.getFlagStand(id)   != null ? "✔" : "✘ falta");
                    info(player, "    base corners", (cfg.getBaseCorner1(id) != null && cfg.getBaseCorner2(id) != null) ? "✔" : "✘ falta");
                }

                String error = cfg.validate(teams);
                if (error != null)
                    player.sendMessage(Component.text("⚠ " + error, NamedTextColor.RED));
                else
                    player.sendMessage(Component.text("✔ Listo para iniciar.", NamedTextColor.GREEN));
            }

            // ── Configuración por equipo ──────────────────────────────────────

            case "setbasespawn" -> {
                if (args.length < 2) { err(player, "Uso: /em frozenheist setbasespawn <id-equipo>"); return; }
                Optional<EventTeam> teamOpt = plugin.getTeamManager().getTeam(args[1]);
                if (teamOpt.isEmpty()) { err(player, "Equipo '" + args[1] + "' no encontrado."); return; }
                miniGame.getConfig().setBaseSpawn(args[1], player.getLocation());
                ok(player, "Base-spawn del equipo '" + args[1] + "' seteado.");
            }

            case "setcapture" -> {
                if (args.length < 2) { err(player, "Uso: /em frozenheist setcapture <id-equipo>"); return; }
                Optional<EventTeam> teamOpt = plugin.getTeamManager().getTeam(args[1]);
                if (teamOpt.isEmpty()) { err(player, "Equipo '" + args[1] + "' no encontrado."); return; }
                miniGame.getConfig().setCaptureZone(args[1], player.getLocation());
                ok(player, "Zona de captura del equipo '" + args[1] + "' seteada.");
            }

            case "setflag" -> {
                if (args.length < 2) { err(player, "Uso: /em frozenheist setflag <id-equipo>"); return; }
                Optional<EventTeam> teamOpt = plugin.getTeamManager().getTeam(args[1]);
                if (teamOpt.isEmpty()) { err(player, "Equipo '" + args[1] + "' no encontrado."); return; }
                miniGame.getConfig().setFlagStand(args[1], player.getLocation());
                ok(player, "Flag-stand del equipo '" + args[1] + "' seteado.");
            }

            case "setbase" -> {
                // /em frozenheist setbase <id> 1|2
                if (args.length < 3) { err(player, "Uso: /em frozenheist setbase <id-equipo> 1|2"); return; }
                Optional<EventTeam> teamOpt = plugin.getTeamManager().getTeam(args[1]);
                if (teamOpt.isEmpty()) { err(player, "Equipo '" + args[1] + "' no encontrado."); return; }
                switch (args[2]) {
                    case "1" -> {
                        miniGame.getConfig().setBaseCorner1(args[1], player.getLocation());
                        ok(player, "Esquina 1 de la base del equipo '" + args[1] + "' seteada.");
                    }
                    case "2" -> {
                        miniGame.getConfig().setBaseCorner2(args[1], player.getLocation());
                        ok(player, "Esquina 2 de la base del equipo '" + args[1] + "' seteada.");
                    }
                    default -> err(player, "Usa 1 o 2.");
                }
            }

            case "teaminfo" -> {
                if (args.length < 2) { err(player, "Uso: /em frozenheist teaminfo <id-equipo>"); return; }
                Optional<EventTeam> teamOpt = plugin.getTeamManager().getTeam(args[1]);
                if (teamOpt.isEmpty()) { err(player, "Equipo '" + args[1] + "' no encontrado."); return; }
                var cfg = miniGame.getConfig();
                String id = args[1];
                player.sendMessage(Component.text("━━━ Config equipo: " + id + " ━━━", NamedTextColor.AQUA));
                info(player, "base-spawn",   cfg.getBaseSpawn(id)   != null ? locStr(cfg.getBaseSpawn(id))   : "no configurado");
                info(player, "capture-zone", cfg.getCaptureZone(id) != null ? locStr(cfg.getCaptureZone(id)) : "no configurado");
                info(player, "flag-stand",   cfg.getFlagStand(id)   != null ? locStr(cfg.getFlagStand(id))   : "no configurado");
                info(player, "base corner 1",cfg.getBaseCorner1(id) != null ? locStr(cfg.getBaseCorner1(id)) : "no configurado");
                info(player, "base corner 2",cfg.getBaseCorner2(id) != null ? locStr(cfg.getBaseCorner2(id)) : "no configurado");
            }

            // ── Control ───────────────────────────────────────────────────────

            case "start" -> {
                if (miniGame.isRunning()) { err(player, "Ya hay una partida en curso."); return; }
                var teams = plugin.getTeamManager().getAllTeams();
                String error = miniGame.getConfig().validate(teams);
                if (error != null) { err(player, error); return; }
                boolean ok = miniGame.start();
                if (!ok) err(player, "No se pudo iniciar. Verifica que haya equipos con jugadores.");
            }

            case "stop" -> {
                if (!miniGame.isRunning()) { err(player, "No hay ninguna partida en curso."); return; }
                miniGame.forceStop();
                ok(player, "Partida detenida.");
            }

            default -> sendHelp(player);
        }
    }

    // ── Tab completion ─────────────────────────────────────────────────────────

    public List<String> tabComplete(String[] args) {
        if (args.length == 1)
            return filter(List.of("setspawn", "setduration", "settings",
                    "setbasespawn", "setcapture", "setflag", "setbase",
                    "teaminfo", "start", "stop"), args[0]);

        List<String> teamIds = new ArrayList<>(plugin.getTeamManager().getTeamIds());

        if (args.length == 2 && List.of("setbasespawn", "setcapture", "setflag", "setbase", "teaminfo")
                .contains(args[0].toLowerCase()))
            return filter(teamIds, args[1]);

        if (args.length == 3 && args[0].equalsIgnoreCase("setbase"))
            return filter(List.of("1", "2"), args[2]);

        return List.of();
    }

    // ── Ayuda ─────────────────────────────────────────────────────────────────

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("━━━ /em frozenheist ━━━", NamedTextColor.AQUA));
        player.sendMessage(Component.text("  Configuración global:", NamedTextColor.GRAY));
        help(player, "/em frozenheist setspawn",              "Spawn global pre-partida");
        help(player, "/em frozenheist setduration <mins>",   "Duración de la partida");
        help(player, "/em frozenheist settings",             "Ver configuración completa");
        player.sendMessage(Component.text("  Por equipo:", NamedTextColor.GRAY));
        help(player, "/em frozenheist setbasespawn <id>",    "Spawn de respawn en la base");
        help(player, "/em frozenheist setcapture <id>",      "Zona de entrega de bandera");
        help(player, "/em frozenheist setflag <id>",         "Posición de la bandera propia");
        help(player, "/em frozenheist setbase <id> 1|2",     "Esquinas de la zona segura");
        help(player, "/em frozenheist teaminfo <id>",        "Ver config de un equipo");
        player.sendMessage(Component.text("  Control:", NamedTextColor.GRAY));
        help(player, "/em frozenheist start",                "Iniciar partida");
        help(player, "/em frozenheist stop",                 "Detener partida");
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private String stateToSpanish(FrozenHeistMiniGame.State state) {
        return switch (state) {
            case IDLE     -> "En espera";
            case RUNNING  -> "En juego";
            case FINISHED -> "Finalizado";
        };
    }

    private void ok(Player p, String msg)  { p.sendMessage(Component.text("✔ " + msg, NamedTextColor.GREEN)); }
    private void err(Player p, String msg) { p.sendMessage(Component.text("✘ " + msg, NamedTextColor.RED)); }

    private void help(Player p, String cmd, String desc) {
        p.sendMessage(Component.text("  " + cmd, NamedTextColor.YELLOW)
                .append(Component.text(" — " + desc, NamedTextColor.GRAY)));
    }

    private void info(Player p, String label, String value) {
        p.sendMessage(Component.text("  " + label + ": ", NamedTextColor.GRAY)
                .append(Component.text(value, NamedTextColor.WHITE)));
    }

    private String locStr(org.bukkit.Location l) {
        return String.format("%.1f, %.1f, %.1f", l.getX(), l.getY(), l.getZ());
    }

    private List<String> filter(List<String> opts, String input) {
        String lower = input.toLowerCase();
        return opts.stream().filter(s -> s.toLowerCase().startsWith(lower)).toList();
    }
}