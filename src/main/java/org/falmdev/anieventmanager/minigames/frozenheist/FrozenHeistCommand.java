package org.falmdev.anieventmanager.minigames.frozenheist;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.model.EventTeam;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
                var cfg   = miniGame.getConfig();
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
                    info(player, "    base-spawn-1",  cfg.getBaseSpawn1(id) != null ? "✔" : "✘ falta");
                    info(player, "    base-spawn-2",  cfg.getBaseSpawn2(id) != null ? "✔" : "✘ falta");
                    info(player, "    capture-zone",  cfg.getCaptureZone(id) != null ? "✔" : "✘ falta");
                    info(player, "    flag-stand",    cfg.getFlagStand(id)   != null ? "✔" : "✘ falta");
                    info(player, "    base corners",  (cfg.getBaseCorner1(id) != null && cfg.getBaseCorner2(id) != null) ? "✔" : "✘ falta");
                }

                String error = cfg.validate(teams);
                if (error != null)
                    player.sendMessage(Component.text("⚠ " + error, NamedTextColor.RED));
                else
                    player.sendMessage(Component.text("✔ Listo para iniciar.", NamedTextColor.GREEN));
            }

            case "setbasespawn" -> {
                if (args.length < 3) { err(player, "Uso: /em frozenheist setbasespawn <1|2> <id-equipo>"); return; }
                String posArg  = args[1];
                String teamArg = args[2];
                if (!posArg.equals("1") && !posArg.equals("2")) {
                    err(player, "La posición debe ser 1 o 2.");
                    return;
                }
                Optional<EventTeam> teamOpt = plugin.getTeamManager().getTeam(teamArg);
                if (teamOpt.isEmpty()) { err(player, "Equipo '" + teamArg + "' no encontrado."); return; }

                if (posArg.equals("1")) {
                    miniGame.getConfig().setBaseSpawn1(teamArg, player.getLocation());
                    ok(player, "Base-spawn 1 del equipo '" + teamArg + "' seteado.");
                } else {
                    miniGame.getConfig().setBaseSpawn2(teamArg, player.getLocation());
                    ok(player, "Base-spawn 2 del equipo '" + teamArg + "' seteado.");
                }
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
                if (args.length < 3) { err(player, "Uso: /em frozenheist setbase <id-equipo> 1|2"); return; }
                Optional<EventTeam> teamOpt = plugin.getTeamManager().getTeam(args[1]);
                if (teamOpt.isEmpty()) { err(player, "Equipo '" + args[1] + "' no encontrado."); return; }

                FrozenHeistConfig cfg = miniGame.getConfig();
                String teamId = args[1];
                EventTeam team = teamOpt.get();

                switch (args[2]) {
                    case "1" -> {
                        cfg.setBaseCorner1(teamId, player.getLocation());
                        ok(player, "Esquina 1 de la base del equipo '" + teamId + "' seteada.");
                    }
                    case "2" -> {
                        cfg.setBaseCorner2(teamId, player.getLocation());
                        ok(player, "Esquina 2 de la base del equipo '" + teamId + "' seteada.");
                        if (cfg.getBaseCorner1(teamId) != null) {
                            BaseColorizer.colorize(cfg.getBaseCorner1(teamId), cfg.getBaseCorner2(teamId), team.getColor());
                            ok(player, "Bloques de la base coloreados al color del equipo " + team.getDisplayName() + ".");
                        } else {
                            player.sendMessage(Component.text("  (la esquina 1 aún no está seteada — colorizado pendiente)", NamedTextColor.GRAY));
                        }
                    }
                    default -> err(player, "Usa 1 o 2.");
                }
            }

            case "colorize" -> {
                if (args.length < 2) { err(player, "Uso: /em frozenheist colorize <id-equipo>"); return; }
                Optional<EventTeam> teamOpt = plugin.getTeamManager().getTeam(args[1]);
                if (teamOpt.isEmpty()) { err(player, "Equipo '" + args[1] + "' no encontrado."); return; }

                FrozenHeistConfig cfg = miniGame.getConfig();
                String teamId = args[1];
                EventTeam team = teamOpt.get();

                if (cfg.getBaseCorner1(teamId) == null || cfg.getBaseCorner2(teamId) == null) {
                    err(player, "El equipo '" + teamId + "' no tiene ambas esquinas configuradas.");
                    return;
                }
                BaseColorizer.colorize(cfg.getBaseCorner1(teamId), cfg.getBaseCorner2(teamId), team.getColor());
                ok(player, "Base de " + team.getDisplayName() + " recoloreada.");
            }

            case "teaminfo" -> {
                if (args.length < 2) { err(player, "Uso: /em frozenheist teaminfo <id-equipo>"); return; }
                Optional<EventTeam> teamOpt = plugin.getTeamManager().getTeam(args[1]);
                if (teamOpt.isEmpty()) { err(player, "Equipo '" + args[1] + "' no encontrado."); return; }
                var cfg = miniGame.getConfig();
                String id = args[1];
                player.sendMessage(Component.text("━━━ Config equipo: " + id + " ━━━", NamedTextColor.AQUA));
                info(player, "base-spawn-1",  cfg.getBaseSpawn1(id) != null ? locStr(cfg.getBaseSpawn1(id)) : "no configurado");
                info(player, "base-spawn-2",  cfg.getBaseSpawn2(id) != null ? locStr(cfg.getBaseSpawn2(id)) : "no configurado");
                info(player, "capture-zone",  cfg.getCaptureZone(id) != null ? locStr(cfg.getCaptureZone(id)) : "no configurado");
                info(player, "flag-stand",    cfg.getFlagStand(id)   != null ? locStr(cfg.getFlagStand(id))   : "no configurado");
                info(player, "base corner 1", cfg.getBaseCorner1(id) != null ? locStr(cfg.getBaseCorner1(id)) : "no configurado");
                info(player, "base corner 2", cfg.getBaseCorner2(id) != null ? locStr(cfg.getBaseCorner2(id)) : "no configurado");
            }

            case "start" -> {
                if (miniGame.isRunning()) { err(player, "Ya hay una partida en curso."); return; }
                var teams = plugin.getTeamManager().getAllTeams();
                String error = miniGame.getConfig().validate(teams);
                if (error != null) { err(player, error); return; }
                boolean ok = miniGame.start();
                if (!ok) err(player, "No se pudo iniciar. Verifica que haya equipos con jugadores.");
            }

            case "stop" -> {
                if (miniGame.getState() == FrozenHeistMiniGame.State.IDLE) { err(player, "No hay ninguna partida en curso."); return; }
                miniGame.forceStop();
                ok(player, "Partida detenida.");
            }

            default -> sendHelp(player);
        }
    }

    public List<String> tabComplete(String[] args) {
        if (args.length == 1)
            return filter(List.of("setspawn", "setduration", "settings",
                    "setbasespawn", "setcapture", "setflag", "setbase", "colorize",
                    "teaminfo", "start", "stop"), args[0]);

        List<String> teamIds = new ArrayList<>(plugin.getTeamManager().getTeamIds());

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("setbasespawn"))
                return filter(List.of("1", "2"), args[1]);
            if (List.of("setcapture", "setflag", "setbase", "colorize", "teaminfo")
                    .contains(args[0].toLowerCase()))
                return filter(teamIds, args[1]);
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("setbasespawn"))
                return filter(teamIds, args[2]);
            if (args[0].equalsIgnoreCase("setbase"))
                return filter(List.of("1", "2"), args[2]);
        }

        return List.of();
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("━━━ /em frozenheist ━━━", NamedTextColor.AQUA));
        player.sendMessage(Component.text("  Configuración global:", NamedTextColor.GRAY));
        help(player, "/em frozenheist setspawn",                "Spawn global pre-partida");
        help(player, "/em frozenheist setduration <mins>",      "Duración de la partida");
        help(player, "/em frozenheist settings",                "Ver configuración completa");
        player.sendMessage(Component.text("  Por equipo:", NamedTextColor.GRAY));
        help(player, "/em frozenheist setbasespawn <1|2> <id>", "Spawn de respawn (jugador 1 o 2)");
        help(player, "/em frozenheist setcapture <id>",         "Zona de entrega de bandera");
        help(player, "/em frozenheist setflag <id>",            "Posición de la bandera propia");
        help(player, "/em frozenheist setbase <id> 1|2",        "Esquinas de la zona segura");
        help(player, "/em frozenheist colorize <id>",           "Fuerza recoloreado de la base");
        help(player, "/em frozenheist teaminfo <id>",           "Ver config de un equipo");
        player.sendMessage(Component.text("  Control:", NamedTextColor.GRAY));
        help(player, "/em frozenheist start",                   "Iniciar partida");
        help(player, "/em frozenheist stop",                    "Detener partida");
    }

    private String stateToSpanish(FrozenHeistMiniGame.State state) {
        return switch (state) {
            case IDLE      -> "En espera";
            case COUNTDOWN -> "Iniciando";
            case RUNNING   -> "En juego";
            case FINISHED  -> "Finalizado";
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