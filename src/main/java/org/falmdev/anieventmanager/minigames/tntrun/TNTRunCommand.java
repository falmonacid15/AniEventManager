package org.falmdev.anieventmanager.minigames.tntrun;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.falmdev.anieventmanager.Anieventmanager;

import java.util.List;

/**
 * Maneja todos los subcomandos de /em tntrun.
 *
 * ── Configuración de spawn/mundo ───────────────────────────────────────────────
 *   /em tntrun setworld
 *   /em tntrun setlobby
 *   /em tntrun setspectator
 *   /em tntrun setcenter
 *   /em tntrun addspawn
 *   /em tntrun clearspawns
 *
 * ── Configuración de arena ─────────────────────────────────────────────────────
 *   /em tntrun setsize <n>              Tamaño (lado/diámetro) en bloques
 *   /em tntrun setshape <square|circle> Forma de la arena
 *   /em tntrun setlayers <n>            Número de capas de TNT+SAND
 *   /em tntrun setlayergap <n>          Bloques de AIR entre capas
 *   /em tntrun setdomeheight <n>        Altura de la cúpula
 *   /em tntrun generate                 Genera la arena
 *   /em tntrun clear                    Elimina la arena generada
 *
 * ── Configuración de juego ─────────────────────────────────────────────────────
 *   /em tntrun setdelay <ticks>
 *   /em tntrun setscore <lugar> <pts>
 *   /em tntrun setjump <on|off>          Activa/desactiva el doble salto
 *   /em tntrun setjumpcooldown <segundos> Cooldown entre dobles saltos
 *   /em tntrun settings
 *
 * ── Control del juego ──────────────────────────────────────────────────────────
 *   /em tntrun lobby
 *   /em tntrun start
 *   /em tntrun stop
 */
public class TNTRunCommand {

    private final Anieventmanager plugin;
    private final TNTRunMiniGame  miniGame;

    public TNTRunCommand(Anieventmanager plugin, TNTRunMiniGame miniGame) {
        this.plugin   = plugin;
        this.miniGame = miniGame;
    }

    public void handle(Player player, String[] args) {
        if (args.length == 0) { sendHelp(player); return; }

        switch (args[0].toLowerCase()) {

            // ── Spawn / mundo ──────────────────────────────────────────────────

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

            // ── Configuración de arena ─────────────────────────────────────────

            case "setsize" -> {
                if (args.length < 2) { err(player, "Uso: /em tntrun setsize <bloques>  (mínimo 10)"); return; }
                try {
                    int size = Integer.parseInt(args[1]);
                    if (size < 10) { err(player, "El tamaño mínimo es 10 bloques."); return; }
                    miniGame.getConfig().setArenaSize(size);
                    ok(player, "Tamaño de arena seteado a " + size + "x" + size + " bloques.");
                } catch (NumberFormatException e) { err(player, "'" + args[1] + "' no es un número válido."); }
            }

            case "setshape" -> {
                if (args.length < 2) { err(player, "Uso: /em tntrun setshape <square|circle>"); return; }
                switch (args[1].toLowerCase()) {
                    case "square" -> {
                        miniGame.getConfig().setArenaShape(TNTRunArena.Shape.SQUARE);
                        ok(player, "Forma de arena: §bCUADRADA§a.");
                    }
                    case "circle" -> {
                        miniGame.getConfig().setArenaShape(TNTRunArena.Shape.CIRCLE);
                        ok(player, "Forma de arena: §bCIRCULAR§a.");
                    }
                    default -> err(player, "Forma inválida. Usa 'square' o 'circle'.");
                }
            }

            case "setlayers" -> {
                if (args.length < 2) { err(player, "Uso: /em tntrun setlayers <n>  (mínimo 1)"); return; }
                try {
                    int layers = Integer.parseInt(args[1]);
                    if (layers < 1) { err(player, "El número mínimo de capas es 1."); return; }
                    miniGame.getConfig().setLayerCount(layers);
                    ok(player, "Número de capas seteado a " + layers + ".");
                } catch (NumberFormatException e) { err(player, "'" + args[1] + "' no es un número válido."); }
            }

            case "setlayergap" -> {
                if (args.length < 2) { err(player, "Uso: /em tntrun setlayergap <bloques>  (mínimo 1)"); return; }
                try {
                    int gap = Integer.parseInt(args[1]);
                    if (gap < 1) { err(player, "El espacio mínimo entre capas es 1 bloque."); return; }
                    miniGame.getConfig().setLayerGap(gap);
                    ok(player, "Espacio entre capas seteado a " + gap + " bloques.");
                } catch (NumberFormatException e) { err(player, "'" + args[1] + "' no es un número válido."); }
            }

            case "setdomeheight" -> {
                if (args.length < 2) { err(player, "Uso: /em tntrun setdomeheight <bloques>  (mínimo 5)"); return; }
                try {
                    int h = Integer.parseInt(args[1]);
                    if (h < 5) { err(player, "La altura mínima de la cúpula es 5 bloques."); return; }
                    miniGame.getConfig().setDomeHeight(h);
                    ok(player, "Altura de cúpula seteada a " + h + " bloques.");
                } catch (NumberFormatException e) { err(player, "'" + args[1] + "' no es un número válido."); }
            }

            case "generate" -> {
                if (miniGame.getConfig().getArenaCenter() == null) {
                    err(player, "Primero setea el centro con /em tntrun setcenter.");
                    return;
                }
                if (isGameActive()) {
                    err(player, "No puedes generar la arena mientras hay una partida en curso.");
                    return;
                }
                TNTRunArena.ArenaConfig cfg = miniGame.getConfig().buildArenaConfig();
                player.sendMessage(Component.text(
                        "Generando arena " + cfg.arenaSize() + "x" + cfg.arenaSize()
                                + " (" + cfg.shape().name().toLowerCase() + ", "
                                + cfg.layerCount() + " capas)...", NamedTextColor.YELLOW));

                TNTRunArena tempArena = new TNTRunArena(
                        miniGame.getConfig().getArenaCenter(), cfg);
                tempArena.generate();
                ok(player, "Arena generada correctamente.");
            }

            case "clear" -> {
                if (isGameActive()) {
                    err(player, "No puedes eliminar la arena mientras hay una partida en curso.");
                    return;
                }
                if (miniGame.getConfig().getArenaCenter() == null) {
                    err(player, "No hay centro de arena configurado.");
                    return;
                }
                TNTRunArena.ArenaConfig cfg = miniGame.getConfig().buildArenaConfig();
                TNTRunArena tempArena = new TNTRunArena(
                        miniGame.getConfig().getArenaCenter(), cfg);
                player.sendMessage(Component.text("Eliminando arena...", NamedTextColor.YELLOW));
                tempArena.clear();
                ok(player, "Arena eliminada correctamente.");
            }

            // ── Configuración de juego ─────────────────────────────────────────

            case "setdelay" -> {
                if (args.length < 2) { err(player, "Uso: /em tntrun setdelay <ticks>  (20 ticks = 1s)"); return; }
                try {
                    int ticks = Integer.parseInt(args[1]);
                    if (ticks < 1) { err(player, "El delay debe ser al menos 1 tick."); return; }
                    miniGame.getConfig().setBlockRemoveDelay(ticks);
                    ok(player, "Delay seteado a " + ticks + " ticks ("
                            + String.format("%.1f", ticks / 20.0) + "s).");
                } catch (NumberFormatException e) { err(player, "'" + args[1] + "' no es un número válido."); }
            }

            case "setscore" -> {
                if (args.length < 3) { err(player, "Uso: /em tntrun setscore <lugar> <puntos>"); return; }
                try {
                    int place  = Integer.parseInt(args[1]);
                    int points = Integer.parseInt(args[2]);
                    miniGame.getConfig().setScoreForPlace(place, points);
                    ok(player, "Lugar #" + place + " ahora otorga " + points + " puntos.");
                } catch (NumberFormatException e) { err(player, "Los valores deben ser números enteros."); }
            }

            case "setjump" -> {
                if (args.length < 2) { err(player, "Uso: /em tntrun setjump <on|off>"); return; }
                switch (args[1].toLowerCase()) {
                    case "on"  -> { miniGame.getConfig().setDoubleJumpEnabled(true);  ok(player, "Doble salto activado."); }
                    case "off" -> { miniGame.getConfig().setDoubleJumpEnabled(false); ok(player, "Doble salto desactivado."); }
                    default    -> err(player, "Usa 'on' o 'off'.");
                }
            }

            case "setjumpcooldown" -> {
                if (args.length < 2) { err(player, "Uso: /em tntrun setjumpcooldown <segundos>  (0 = sin cooldown)"); return; }
                try {
                    int secs = Integer.parseInt(args[1]);
                    if (secs < 0) { err(player, "El cooldown no puede ser negativo."); return; }
                    miniGame.getConfig().setDoubleJumpCooldown(secs);
                    ok(player, secs == 0
                            ? "Cooldown de doble salto desactivado."
                            : "Cooldown de doble salto seteado a " + secs + " segundos.");
                } catch (NumberFormatException e) { err(player, "'" + args[1] + "' no es un número válido."); }
            }

            case "settings" -> printSettings(player);

            // ── Control del juego ──────────────────────────────────────────────

            case "lobby" -> {
                if (isGameActive()) {
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
                if (isGameActive()) { err(player, "Ya hay una partida en curso."); return; }
                String error = miniGame.getConfig().validate();
                if (error != null) { err(player, error); return; }
                boolean ok = miniGame.start();
                if (!ok) err(player, "No se pudo iniciar. Verifica la configuración con /em tntrun settings.");
            }

            case "stop" -> {
                if (!isGameActive()) { err(player, "No hay ninguna partida en curso."); return; }
                miniGame.forceStop();
                ok(player, "Partida detenida.");
            }

            default -> sendHelp(player);
        }
    }

    // ── Tab completion ─────────────────────────────────────────────────────────

    public List<String> tabComplete(String[] args) {
        if (args.length == 1) {
            return filter(List.of(
                    "setworld", "setlobby", "setspectator", "setcenter",
                    "addspawn", "clearspawns",
                    "setsize", "setshape", "setlayers", "setlayergap", "setdomeheight",
                    "generate", "clear",
                    "setdelay", "setscore", "setjump", "setjumpcooldown",
                    "settings", "lobby", "start", "stop"
            ), args[0]);
        }
        return switch (args[0].toLowerCase()) {
            case "setshape"       -> args.length == 2 ? filter(List.of("square", "circle"), args[1])          : List.of();
            case "setjump"        -> args.length == 2 ? filter(List.of("on", "off"), args[1])                  : List.of();
            case "setsize"        -> args.length == 2 ? filter(List.of("20", "30", "40", "60", "80"), args[1]) : List.of();
            case "setlayers"      -> args.length == 2 ? filter(List.of("1", "2", "3", "4", "5"), args[1])      : List.of();
            case "setlayergap"    -> args.length == 2 ? filter(List.of("1", "2", "3", "5", "8", "10", "15"), args[1])      : List.of();
            case "setdomeheight"  -> args.length == 2 ? filter(List.of("10", "15", "20", "25", "30"), args[1]) : List.of();
            case "setdelay"       -> args.length == 2 ? filter(List.of("5", "10", "15", "20"), args[1])        : List.of();
            case "setjumpcooldown"-> args.length == 2 ? filter(List.of("0", "3", "5", "8", "10"), args[1])    : List.of();
            case "setscore" -> {
                if (args.length == 2) yield filter(List.of("1", "2", "3", "4"), args[1]);
                if (args.length == 3) yield filter(List.of("10", "6", "3", "1"), args[2]);
                yield List.of();
            }
            default -> List.of();
        };
    }

    // ── Settings ───────────────────────────────────────────────────────────────

    private void printSettings(Player player) {
        var cfg = miniGame.getConfig();
        player.sendMessage(Component.text("━━━ TNT Run — Configuración ━━━", NamedTextColor.GOLD));

        player.sendMessage(Component.text("  Spawn / mundo:", NamedTextColor.GRAY));
        info(player, "Mundo",           cfg.getWorldName().isEmpty()   ? "no configurado" : cfg.getWorldName());
        info(player, "Lobby spawn",     cfg.getLobbySpawn()     != null ? locStr(cfg.getLobbySpawn())     : "no configurado");
        info(player, "Spectator spawn", cfg.getSpectatorSpawn() != null ? locStr(cfg.getSpectatorSpawn()) : "no configurado");
        info(player, "Centro de arena", cfg.getArenaCenter()    != null ? locStr(cfg.getArenaCenter())    : "no configurado");
        info(player, "Spawns",          String.valueOf(cfg.getPlayerSpawns().size()));

        player.sendMessage(Component.text("  Arena:", NamedTextColor.GRAY));
        info(player, "Tamaño",          cfg.getArenaSize() + "x" + cfg.getArenaSize() + " bloques");
        info(player, "Forma",           cfg.getArenaShape().name().toLowerCase());
        info(player, "Capas",           String.valueOf(cfg.getLayerCount()));
        info(player, "Espacio capas",   cfg.getLayerGap() + " bloques");
        info(player, "Altura cúpula",   cfg.getDomeHeight() + " bloques");

        player.sendMessage(Component.text("  Juego:", NamedTextColor.GRAY));
        info(player, "Delay bloque",    cfg.getBlockRemoveDelay() + " ticks (" +
                String.format("%.1f", cfg.getBlockRemoveDelay() / 20.0) + "s)");
        info(player, "Countdown",       cfg.getCountdownSeconds() + "s");
        info(player, "Doble salto",     cfg.isDoubleJumpEnabled()
                ? "§aactivado §7(cooldown: " + cfg.getDoubleJumpCooldown() + "s)"
                : "§cdesactivado");
        info(player, "Puntaje 1°",      String.valueOf(cfg.getScoreForPlace(1)));
        info(player, "Puntaje 2°",      String.valueOf(cfg.getScoreForPlace(2)));
        info(player, "Puntaje 3°",      String.valueOf(cfg.getScoreForPlace(3)));
        info(player, "Estado",          miniGame.getState().name());

        String validation = cfg.validate();
        if (validation != null) {
            player.sendMessage(Component.text("⚠ " + validation, NamedTextColor.RED));
        } else {
            player.sendMessage(Component.text("✔ Configuración completa, listo para iniciar.", NamedTextColor.GREEN));
        }
    }

    // ── Ayuda ──────────────────────────────────────────────────────────────────

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("━━━ /em tntrun ━━━", NamedTextColor.GOLD));

        player.sendMessage(Component.text("  Spawn / mundo:", NamedTextColor.GRAY));
        help(player, "/em tntrun setworld",               "Setea el mundo actual");
        help(player, "/em tntrun setlobby",               "Setea el spawn del lobby");
        help(player, "/em tntrun setspectator",           "Setea el spawn de espectadores");
        help(player, "/em tntrun setcenter",              "Setea el centro de la arena");
        help(player, "/em tntrun addspawn",               "Agrega un spawn de jugador");
        help(player, "/em tntrun clearspawns",            "Elimina todos los spawns");

        player.sendMessage(Component.text("  Arena:", NamedTextColor.GRAY));
        help(player, "/em tntrun setsize <n>",            "Tamaño lado/diámetro (bloques)");
        help(player, "/em tntrun setshape <square|circle>","Forma de la arena");
        help(player, "/em tntrun setlayers <n>",          "Número de capas TNT+SAND");
        help(player, "/em tntrun setlayergap <n>",        "Bloques de aire entre capas");
        help(player, "/em tntrun setdomeheight <n>",      "Altura de la cúpula");
        help(player, "/em tntrun generate",               "Genera la arena");
        help(player, "/em tntrun clear",                  "Elimina la arena generada");

        player.sendMessage(Component.text("  Juego:", NamedTextColor.GRAY));
        help(player, "/em tntrun setdelay <ticks>",       "Delay caída del bloque");
        help(player, "/em tntrun setscore <n> <pts>",     "Puntaje por posición");
        help(player, "/em tntrun setjump <on|off>",       "Activa/desactiva doble salto");
        help(player, "/em tntrun setjumpcooldown <s>",    "Cooldown del doble salto");
        help(player, "/em tntrun settings",               "Ver configuración completa");

        player.sendMessage(Component.text("  Control:", NamedTextColor.GRAY));
        help(player, "/em tntrun lobby",                  "Mover jugadores al lobby");
        help(player, "/em tntrun start",                  "Iniciar la partida");
        help(player, "/em tntrun stop",                   "Detener la partida");
    }

    // ── Utilidades ─────────────────────────────────────────────────────────────

    private boolean isGameActive() {
        return miniGame.getState() == TNTRunMiniGame.State.RUNNING
                || miniGame.getState() == TNTRunMiniGame.State.COUNTDOWN;
    }

    private void ok(Player p, String msg)   { p.sendMessage(Component.text("✔ " + msg, NamedTextColor.GREEN)); }
    private void err(Player p, String msg)  { p.sendMessage(Component.text("✘ " + msg, NamedTextColor.RED)); }

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