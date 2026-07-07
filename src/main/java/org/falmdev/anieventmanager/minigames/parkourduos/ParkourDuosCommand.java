package org.falmdev.anieventmanager.minigames.parkourduos;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.model.EventTeam;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class ParkourDuosCommand {

    private final Anieventmanager     plugin;
    private final ParkourDuosMiniGame miniGame;

    public ParkourDuosCommand(Anieventmanager plugin, ParkourDuosMiniGame miniGame) {
        this.plugin   = plugin;
        this.miniGame = miniGame;
    }

    public void handle(Player player, String[] args) {
        if (args.length == 0) { sendHelp(player); return; }

        switch (args[0].toLowerCase()) {

            case "start" -> {
                if (miniGame.getState() != ParkourDuosMiniGame.State.IDLE) {
                    err(player, "Ya hay una partida en curso."); return;
                }
                boolean ok = miniGame.start();
                if (!ok) err(player, "No se pudo iniciar. Revisa /em pd settings.");
            }

            case "stop" -> {
                if (miniGame.getState() == ParkourDuosMiniGame.State.IDLE) {
                    err(player, "No hay ninguna partida en curso."); return;
                }
                miniGame.forceStop();
                ok(player, "Partida detenida.");
            }

            case "lobby" -> {
                org.bukkit.Location lobby = miniGame.getConfig().getLobby();
                if (lobby == null) { err(player, "El lobby no está configurado."); return; }
                for (EventTeam team : plugin.getTeamManager().getAllTeams())
                    for (Player p : team.getOnlinePlayers())
                        p.teleport(lobby);
                ok(player, "Todos los jugadores movidos al lobby.");
            }

            case "setlobby" -> {
                miniGame.getConfig().setLobby(player.getLocation());
                ok(player, "Lobby seteado en tu posición.");
            }

            case "setspawn1" -> {
                if (args.length < 2) { err(player, "Uso: /em pd setspawn1 <equipo>"); return; }
                String teamId = args[1].toLowerCase();
                if (!teamExists(teamId)) { err(player, "No existe el equipo '" + teamId + "'."); return; }
                miniGame.getConfig().setTeamSpawn1(teamId, player.getLocation());
                ok(player, "Spawn 1 del equipo '" + teamId + "' seteado.");
            }

            case "setspawn2" -> {
                if (args.length < 2) { err(player, "Uso: /em pd setspawn2 <equipo>"); return; }
                String teamId = args[1].toLowerCase();
                if (!teamExists(teamId)) { err(player, "No existe el equipo '" + teamId + "'."); return; }
                miniGame.getConfig().setTeamSpawn2(teamId, player.getLocation());
                ok(player, "Spawn 2 del equipo '" + teamId + "' seteado.");
            }

            case "setstart" -> {
                if (args.length < 2) { err(player, "Uso: /em pd setstart <equipo>"); return; }
                String teamId = args[1].toLowerCase();
                if (!teamExists(teamId)) { err(player, "No existe el equipo '" + teamId + "'."); return; }
                miniGame.getConfig().setTeamStart(teamId, player.getLocation());
                ok(player, "Punto de inicio del equipo '" + teamId + "' seteado.");
            }

            case "setfinish" -> {
                if (args.length < 2) { err(player, "Uso: /em pd setfinish <equipo>"); return; }
                String teamId = args[1].toLowerCase();
                if (!teamExists(teamId)) { err(player, "No existe el equipo '" + teamId + "'."); return; }
                miniGame.getConfig().setTeamFinish(teamId, player.getLocation());
                ok(player, "Punto de finalización del equipo '" + teamId + "' seteado.");
            }

            case "cp" -> handleCheckpoint(player, Arrays.copyOfRange(args, 1, args.length));

            case "setduration" -> {
                if (args.length < 2) { err(player, "Uso: /em pd setduration <minutos>"); return; }
                try {
                    int mins = Integer.parseInt(args[1]);
                    if (mins < 1) { err(player, "Mínimo 1 minuto."); return; }
                    miniGame.getConfig().setDurationMinutes(mins);
                    ok(player, "Duración seteada a " + mins + " minutos.");
                } catch (NumberFormatException e) { err(player, "Número inválido."); }
            }

            case "setscore" -> {
                if (args.length < 3) { err(player, "Uso: /em pd setscore <lugar> <pts>"); return; }
                try {
                    int place = Integer.parseInt(args[1]);
                    int pts   = Integer.parseInt(args[2]);
                    miniGame.getConfig().setScoreForPlace(place, pts);
                    ok(player, "Lugar #" + place + " ahora da " + pts + " pts.");
                } catch (NumberFormatException e) { err(player, "Valores inválidos."); }
            }

            case "setchain" -> {
                if (args.length < 2) { err(player, "Uso: /em pd setchain <distancia>"); return; }
                try {
                    double dist = Double.parseDouble(args[1]);
                    if (dist < 1) { err(player, "Distancia mínima: 1 bloque."); return; }
                    miniGame.getConfig().setChainMaxDistance(dist);
                    miniGame.getChainManager().setMaxDistance(dist);
                    ok(player, "Distancia máxima de cadena: " + dist + " bloques.");
                } catch (NumberFormatException e) { err(player, "Número inválido."); }
            }

            case "settings" -> {
                var cfg = miniGame.getConfig();
                player.sendMessage(Component.text("━━━ Parkour Duos — Config ━━━", NamedTextColor.GOLD));
                info(player, "Estado",          miniGame.getState().name());
                info(player, "Duración",        cfg.getDurationMinutes() + " min");
                info(player, "Countdown",       cfg.getCountdownSeconds() + " seg");
                info(player, "Cadena dist max", cfg.getChainMaxDistance() + " bloques");
                info(player, "Lobby",           cfg.getLobby() != null ? locStr(cfg.getLobby()) : "no configurado");
                info(player, "Score 1°",        String.valueOf(cfg.getScoreFirst()));
                info(player, "Score 2°",        String.valueOf(cfg.getScoreSecond()));
                info(player, "Score 3°",        String.valueOf(cfg.getScoreThird()));
                info(player, "Score default",   String.valueOf(cfg.getScoreDefault()));
                info(player, "Score/CP",        String.valueOf(cfg.getScorePerCheckpoint()));
                if (miniGame.isRunning())
                    info(player, "Tiempo restante", miniGame.getTimeLeftFormatted());

                player.sendMessage(Component.text("━━━ Equipos ━━━", NamedTextColor.GOLD));
                Collection<EventTeam> teams = plugin.getTeamManager().getAllTeams();
                if (teams.isEmpty()) {
                    player.sendMessage(Component.text("  Sin equipos.", NamedTextColor.GRAY));
                } else {
                    for (EventTeam team : teams) {
                        String err = cfg.validateTeam(team.getId());
                        Component status = err == null
                                ? Component.text(" ✔", NamedTextColor.GREEN)
                                : Component.text(" ✘ " + err, NamedTextColor.RED);
                        player.sendMessage(
                                Component.text("  " + team.getId() + ": ", NamedTextColor.GRAY)
                                        .append(Component.text(team.getDisplayName(), team.getColor()))
                                        .append(Component.text(" — " + cfg.getCheckpointCount(team.getId()) + " CPs",
                                                NamedTextColor.DARK_GRAY))
                                        .append(status));
                    }
                }
            }

            default -> sendHelp(player);
        }
    }

    private void handleCheckpoint(Player player, String[] args) {
        if (args.length == 0) { sendCpHelp(player); return; }

        switch (args[0].toLowerCase()) {

            case "add" -> {
                if (args.length < 2) { err(player, "Uso: /em pd cp add <equipo> [radio]"); return; }
                String teamId = args[1].toLowerCase();
                if (!teamExists(teamId)) { err(player, "No existe el equipo '" + teamId + "'."); return; }
                double radius = 3.0;
                if (args.length >= 3) {
                    try { radius = Double.parseDouble(args[2]); }
                    catch (NumberFormatException e) { err(player, "Radio inválido."); return; }
                }
                miniGame.getConfig().addCheckpoint(teamId, player.getLocation(), radius);
                int count = miniGame.getConfig().getCheckpointCount(teamId);
                ok(player, "Checkpoint #" + count + " agregado al equipo '" + teamId + "' (radio " + radius + ").");
            }

            case "remove" -> {
                if (args.length < 3) { err(player, "Uso: /em pd cp remove <equipo> <índice>"); return; }
                String teamId = args[1].toLowerCase();
                if (!teamExists(teamId)) { err(player, "No existe el equipo '" + teamId + "'."); return; }
                try {
                    int idx = Integer.parseInt(args[2]) - 1; // 1-based para el usuario
                    if (idx < 0 || idx >= miniGame.getConfig().getCheckpointCount(teamId)) {
                        err(player, "Índice fuera de rango."); return;
                    }
                    miniGame.getConfig().removeCheckpoint(teamId, idx);
                    ok(player, "Checkpoint eliminado.");
                } catch (NumberFormatException e) { err(player, "Índice inválido."); }
            }

            case "list" -> {
                if (args.length < 2) { err(player, "Uso: /em pd cp list <equipo>"); return; }
                String teamId = args[1].toLowerCase();
                if (!teamExists(teamId)) { err(player, "No existe el equipo '" + teamId + "'."); return; }
                List<ParkourCheckpoint> cps = miniGame.getConfig().getCheckpoints(teamId);
                if (cps.isEmpty()) {
                    player.sendMessage(Component.text("Sin checkpoints para '" + teamId + "'.", NamedTextColor.GRAY));
                    return;
                }
                player.sendMessage(Component.text("━━━ Checkpoints: " + teamId + " (" + cps.size() + ") ━━━",
                        NamedTextColor.GOLD));
                for (ParkourCheckpoint cp : cps) {
                    player.sendMessage(Component.text(
                            "  #" + (cp.getIndex() + 1) + "  " + locStr(cp.getCenter())
                                    + "  r=" + cp.getRadius(), NamedTextColor.GRAY));
                }
            }

            case "clear" -> {
                if (args.length < 2) { err(player, "Uso: /em pd cp clear <equipo>"); return; }
                String teamId = args[1].toLowerCase();
                if (!teamExists(teamId)) { err(player, "No existe el equipo '" + teamId + "'."); return; }
                miniGame.getConfig().clearCheckpoints(teamId);
                ok(player, "Checkpoints del equipo '" + teamId + "' eliminados.");
            }

            default -> sendCpHelp(player);
        }
    }

    public List<String> tabComplete(String[] args) {
        if (args.length == 1)
            return filter(List.of("start", "stop", "lobby", "setlobby", "setspawn1", "setspawn2",
                    "setstart", "setfinish", "cp", "setduration", "setscore", "setchain", "settings"), args[0]);

        List<String> teamIds = new ArrayList<>(plugin.getTeamManager().getTeamIds());

        if (args[0].equalsIgnoreCase("cp")) {
            if (args.length == 2) return filter(List.of("add", "remove", "list", "clear"), args[1]);
            if (args.length == 3) return filter(teamIds, args[2]);
        }

        if (List.of("setspawn1", "setspawn2", "setstart", "setfinish").contains(args[0].toLowerCase())
                && args.length == 2)
            return filter(teamIds, args[1]);

        if (args[0].equalsIgnoreCase("setscore") && args.length == 2)
            return filter(List.of("1", "2", "3", "4"), args[1]);

        return List.of();
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("━━━ /em pd ━━━", NamedTextColor.GOLD));
        help(player, "/em pd start",                  "Iniciar partida");
        help(player, "/em pd stop",                   "Detener partida");
        help(player, "/em pd lobby",                  "Mover a todos al lobby");
        help(player, "/em pd settings",               "Ver configuración");
        help(player, "/em pd setlobby",               "Setear lobby");
        help(player, "/em pd setspawn1 <equipo>",     "Spawn jugador 1");
        help(player, "/em pd setspawn2 <equipo>",     "Spawn jugador 2");
        help(player, "/em pd setstart <equipo>",      "Punto de inicio");
        help(player, "/em pd setfinish <equipo>",     "Punto de finalización");
        help(player, "/em pd cp add <equipo> [radio]","Agregar checkpoint");
        help(player, "/em pd cp remove <equipo> <n>", "Eliminar checkpoint");
        help(player, "/em pd cp list <equipo>",       "Listar checkpoints");
        help(player, "/em pd cp clear <equipo>",      "Limpiar checkpoints");
        help(player, "/em pd setduration <mins>",     "Duración");
        help(player, "/em pd setscore <lugar> <pts>", "Puntaje por lugar");
        help(player, "/em pd setchain <dist>",        "Distancia máxima de cadena");
    }

    private void sendCpHelp(Player player) {
        player.sendMessage(Component.text("━━━ /em pd cp ━━━", NamedTextColor.GOLD));
        help(player, "/em pd cp add <equipo> [radio]", "Agregar checkpoint en tu posición");
        help(player, "/em pd cp remove <equipo> <n>",  "Eliminar checkpoint por número");
        help(player, "/em pd cp list <equipo>",        "Listar checkpoints del equipo");
        help(player, "/em pd cp clear <equipo>",       "Limpiar todos los checkpoints");
    }


    private boolean teamExists(String id) {
        return plugin.getTeamManager().getTeam(id).isPresent();
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