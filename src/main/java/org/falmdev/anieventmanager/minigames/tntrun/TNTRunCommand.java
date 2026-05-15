package org.falmdev.anieventmanager.minigames.tntrun;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.falmdev.anieventmanager.Anieventmanager;

import java.util.List;

/**
 * Maneja todos los subcomandos de /em tntrun.
 *
 * Comandos disponibles:
 *
 * Configuración:
 *   /em tntrun setworld          → Setea el mundo actual como mundo del minijuego
 *   /em tntrun setlobby          → Setea el spawn del lobby del minijuego
 *   /em tntrun setspectator      → Setea el spawn de espectadores
 *   /em tntrun setcenter         → Setea el centro de la arena
 *   /em tntrun addspawn          → Agrega spawn individual en tu posición
 *   /em tntrun clearspawns       → Limpia todos los spawns de jugadores
 *   /em tntrun generate          → Genera la arena en el centro configurado
 *   /em tntrun settings          → Muestra la configuración actual
 *   /em tntrun setdelay <ticks>  → Delay antes de que caiga el bloque
 *   /em tntrun setscore <lugar> <pts> → Puntaje para ese lugar
 *
 * Control del juego:
 *   /em tntrun lobby             → Mueve todos los jugadores al lobby del minijuego
 *   /em tntrun start             → Inicia la partida
 *   /em tntrun stop              → Detiene la partida
 */
public class TNTRunCommand {

    private final Anieventmanager plugin;
    private final TNTRunMiniGame miniGame;

    public TNTRunCommand(Anieventmanager plugin, TNTRunMiniGame miniGame) {
        this.plugin   = plugin;
        this.miniGame = miniGame;
    }

    public void handle(Player player, String[] args) {
        if (args.length == 0) {
            sendHelp(player);
            return;
        }

        switch (args[0].toLowerCase()) {

            // ── Configuración ─────────────────────────────────────────────────

            case "setworld" -> {
                String worldName = player.getWorld().getName();
                miniGame.getConfig().setWorldName(worldName);
                ok(player, "Mundo seteado a '" + worldName + "'.");
            }

            case "setlobby" -> {
                miniGame.getConfig().setLobbySpawn(player.getLocation());
                ok(player, "Lobby spawn seteado en tu posición.");
            }

            case "setspectator" -> {
                miniGame.getConfig().setSpectatorSpawn(player.getLocation());
                ok(player, "Spawn de espectadores seteado en tu posición.");
            }

            case "setcenter" -> {
                miniGame.getConfig().setArenaCenter(player.getLocation());
                ok(player, "Centro de la arena seteado en tu posición.");
            }

            case "addspawn" -> {
                miniGame.getConfig().addPlayerSpawn(player.getLocation());
                int count = miniGame.getConfig().getPlayerSpawns().size();
                ok(player, "Spawn #" + count + " agregado en tu posición.");
            }

            case "clearspawns" -> {
                miniGame.getConfig().clearPlayerSpawns();
                ok(player, "Todos los spawns de jugadores eliminados.");
            }

            case "generate" -> {
                if (miniGame.getConfig().getArenaCenter() == null) {
                    err(player, "Primero setea el centro con /em tntrun setcenter.");
                    return;
                }
                if (miniGame.getState() == TNTRunMiniGame.State.RUNNING
                        || miniGame.getState() == TNTRunMiniGame.State.COUNTDOWN) {
                    err(player, "No puedes generar la arena mientras hay una partida en curso.");
                    return;
                }
                player.sendMessage(Component.text("Generando arena " + TNTRunArena.getArenaSize()
                        + "x" + TNTRunArena.getArenaSize() + "...", NamedTextColor.YELLOW));
                TNTRunArena tempArena = new TNTRunArena(miniGame.getConfig().getArenaCenter());
                tempArena.generate();
                ok(player, "Arena generada correctamente.");
            }

            case "setdelay" -> {
                if (args.length < 2) {
                    err(player, "Uso: /em tntrun setdelay <ticks>  (20 ticks = 1 segundo)");
                    return;
                }
                try {
                    int ticks = Integer.parseInt(args[1]);
                    if (ticks < 1) { err(player, "El delay debe ser al menos 1 tick."); return; }
                    miniGame.getConfig().setBlockRemoveDelay(ticks);
                    ok(player, "Delay seteado a " + ticks + " ticks (" +
                            String.format("%.1f", ticks / 20.0) + " segundos).");
                } catch (NumberFormatException e) {
                    err(player, "'" + args[1] + "' no es un número válido.");
                }
            }

            case "setscore" -> {
                if (args.length < 3) {
                    err(player, "Uso: /em tntrun setscore <lugar> <puntos>");
                    return;
                }
                try {
                    int place  = Integer.parseInt(args[1]);
                    int points = Integer.parseInt(args[2]);
                    miniGame.getConfig().setScoreForPlace(place, points);
                    ok(player, "Lugar #" + place + " ahora otorga " + points + " puntos.");
                } catch (NumberFormatException e) {
                    err(player, "Los valores deben ser números enteros.");
                }
            }

            case "settings" -> {
                var cfg = miniGame.getConfig();
                player.sendMessage(Component.text("━━━ TNT Run — Configuración ━━━", NamedTextColor.GOLD));
                info(player, "Mundo",            cfg.getWorldName().isEmpty() ? "no configurado" : cfg.getWorldName());
                info(player, "Lobby spawn",      cfg.getLobbySpawn()     != null ? locStr(cfg.getLobbySpawn())     : "no configurado");
                info(player, "Spectator spawn",  cfg.getSpectatorSpawn() != null ? locStr(cfg.getSpectatorSpawn()) : "no configurado");
                info(player, "Centro de arena",  cfg.getArenaCenter()    != null ? locStr(cfg.getArenaCenter())    : "no configurado");
                info(player, "Spawns de jugadores", String.valueOf(cfg.getPlayerSpawns().size()));
                info(player, "Tamaño de arena",  TNTRunArena.getArenaSize() + "x" + TNTRunArena.getArenaSize());
                info(player, "Delay de bloque",  cfg.getBlockRemoveDelay() + " ticks (" +
                        String.format("%.1f", cfg.getBlockRemoveDelay() / 20.0) + "s)");
                info(player, "Countdown",        cfg.getCountdownSeconds() + " segundos");
                info(player, "Puntaje 1°",       String.valueOf(cfg.getScoreForPlace(1)));
                info(player, "Puntaje 2°",       String.valueOf(cfg.getScoreForPlace(2)));
                info(player, "Puntaje 3°",       String.valueOf(cfg.getScoreForPlace(3)));
                info(player, "Estado actual",    miniGame.getState().name());

                String validation = cfg.validate();
                if (validation != null) {
                    player.sendMessage(Component.text("⚠ " + validation, NamedTextColor.RED));
                } else {
                    player.sendMessage(Component.text("✔ Configuración completa, listo para iniciar.", NamedTextColor.GREEN));
                }
            }

            // ── Control del juego ─────────────────────────────────────────────

            case "lobby" -> {
                if (miniGame.getState() == TNTRunMiniGame.State.RUNNING
                        || miniGame.getState() == TNTRunMiniGame.State.COUNTDOWN) {
                    err(player, "Hay una partida en curso. Usa /em tntrun stop primero.");
                    return;
                }
                boolean ok = miniGame.sendToLobby();
                if (ok) {
                    ok(player, "Todos los jugadores fueron movidos al lobby de TNT Run.");
                } else {
                    String error = miniGame.getConfig().validate();
                    err(player, error != null ? error : "No se pudo mover al lobby.");
                }
            }

            case "start" -> {
                if (miniGame.getState() == TNTRunMiniGame.State.RUNNING
                        || miniGame.getState() == TNTRunMiniGame.State.COUNTDOWN) {
                    err(player, "Ya hay una partida en curso.");
                    return;
                }
                String error = miniGame.getConfig().validate();
                if (error != null) { err(player, error); return; }

                boolean ok = miniGame.start();
                if (!ok) {
                    err(player, "No se pudo iniciar. Verifica la configuración con /em tntrun settings.");
                }
            }

            case "stop" -> {
                if (miniGame.getState() == TNTRunMiniGame.State.IDLE
                        || miniGame.getState() == TNTRunMiniGame.State.FINISHED) {
                    err(player, "No hay ninguna partida en curso.");
                    return;
                }
                miniGame.forceStop();
                ok(player, "Partida detenida.");
            }

            default -> sendHelp(player);
        }
    }

    public List<String> tabComplete(String[] args) {
        if (args.length == 1) {
            return filter(List.of(
                    "setworld", "setlobby", "setspectator", "setcenter",
                    "addspawn", "clearspawns", "generate", "setdelay",
                    "setscore", "settings", "lobby", "start", "stop"
            ), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setscore"))
            return filter(List.of("1", "2", "3", "4"), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("setdelay"))
            return filter(List.of("5", "10", "15", "20"), args[1]);
        if (args.length == 3 && args[0].equalsIgnoreCase("setscore"))
            return filter(List.of("10", "6", "3", "1"), args[2]);
        return List.of();
    }

    // ── Ayuda ─────────────────────────────────────────────────────────────────

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("━━━ /em tntrun ━━━", NamedTextColor.GOLD));
        player.sendMessage(Component.text("  Configuración:", NamedTextColor.GRAY));
        help(player, "/em tntrun setworld",            "Setea el mundo actual");
        help(player, "/em tntrun setlobby",            "Setea el spawn del lobby");
        help(player, "/em tntrun setspectator",        "Setea el spawn de espectadores");
        help(player, "/em tntrun setcenter",           "Setea el centro de la arena");
        help(player, "/em tntrun addspawn",            "Agrega un spawn de jugador");
        help(player, "/em tntrun clearspawns",         "Elimina todos los spawns");
        help(player, "/em tntrun generate",            "Genera la arena");
        help(player, "/em tntrun setdelay <ticks>",    "Delay de caída del bloque");
        help(player, "/em tntrun setscore <n> <pts>",  "Puntaje por posición");
        help(player, "/em tntrun settings",            "Ver configuración completa");
        player.sendMessage(Component.text("  Control:", NamedTextColor.GRAY));
        help(player, "/em tntrun lobby",               "Mover jugadores al lobby");
        help(player, "/em tntrun start",               "Iniciar la partida");
        help(player, "/em tntrun stop",                "Detener la partida");
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private void ok(Player p, String msg) {
        p.sendMessage(Component.text("✔ " + msg, NamedTextColor.GREEN));
    }

    private void err(Player p, String msg) {
        p.sendMessage(Component.text("✘ " + msg, NamedTextColor.RED));
    }

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

    private List<String> filter(List<String> options, String input) {
        String lower = input.toLowerCase();
        return options.stream().filter(s -> s.toLowerCase().startsWith(lower)).toList();
    }
}