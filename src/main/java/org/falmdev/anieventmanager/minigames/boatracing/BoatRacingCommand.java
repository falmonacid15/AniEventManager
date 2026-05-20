package org.falmdev.anieventmanager.minigames.boatracing;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.falmdev.anieventmanager.Anieventmanager;

import java.util.Arrays;
import java.util.List;

/**
 * Comandos del Boat Racing.
 *
 * ── Configuración global ──────────────────────────────────────────
 *   /em boatracing setpaddock            → Spawn sala de espera
 *   /em boatracing setlaps <n>           → Número de vueltas
 *   /em boatracing setboat <tipo>        → Bote por defecto
 *   /em boatracing setmyboat <j> <tipo>  → Bote de un jugador específico
 *   /em boatracing setqualytime <s>      → Duración máxima de qualy
 *   /em boatracing setscore <pos> <pts>  → Puntos por posición
 *   /em boatracing settings              → Ver configuración completa
 *
 * ── Configuración de la pista ─────────────────────────────────────
 *   /em boatracing track a               → Punto A de la región de la pista
 *   /em boatracing track b               → Punto B de la región de la pista
 *   /em boatracing finish a              → Extremo A de la línea de meta
 *   /em boatracing finish b              → Extremo B de la línea de meta
 *   /em boatracing addspawn              → Spawn de parrilla (orden de llegada)
 *   /em boatracing clearspawns           → Limpiar spawns de parrilla
 *   /em boatracing addcheckpoint [r]     → Checkpoint en tu posición (radio opcional)
 *   /em boatracing clearcheckpoints      → Limpiar checkpoints
 *   /em boatracing addlight              → Luz de largada (máx 5) mirando bloque
 *   /em boatracing clearlights           → Limpiar luces
 *
 * ── Control ───────────────────────────────────────────────────────
 *   /em boatracing paddock               → Mover jugadores al paddock
 *   /em boatracing startqualy            → Iniciar vuelta de clasificación
 *   /em boatracing startrace             → Iniciar carrera
 *   /em boatracing stop                  → Detener sesión
 */
public class BoatRacingCommand {

    private final Anieventmanager    plugin;
    private final BoatRacingMiniGame miniGame;

    public BoatRacingCommand(Anieventmanager plugin, BoatRacingMiniGame miniGame) {
        this.plugin   = plugin;
        this.miniGame = miniGame;
    }

    public void handle(Player player, String[] args) {
        if (args.length == 0) { sendHelp(player); return; }

        switch (args[0].toLowerCase()) {

            // ── Configuración global ──────────────────────────────────────────

            case "setpaddock" -> {
                miniGame.getConfig().setPaddockSpawn(player.getLocation());
                ok(player, "Paddock seteado en tu posición.");
            }

            case "setlaps" -> {
                if (args.length < 2) { err(player, "Uso: /em boatracing setlaps <n>"); return; }
                try {
                    int n = Integer.parseInt(args[1]);
                    if (n < 1) { err(player, "Mínimo 1 vuelta."); return; }
                    miniGame.getConfig().setTotalLaps(n);
                    ok(player, "Vueltas seteadas a " + n + ".");
                } catch (NumberFormatException e) { err(player, "Número inválido."); }
            }

            case "setboat" -> {
                if (args.length < 2) { err(player, "Uso: /em boatracing setboat <tipo>"); return; }
                try {
                    BoatType type = BoatType.valueOf(args[1].toUpperCase());
                    miniGame.getConfig().setDefaultBoat(type);
                    ok(player, "Bote por defecto: " + type.name() + ".");
                } catch (IllegalArgumentException e) {
                    err(player, "Tipo inválido. Opciones: " + String.join(", ", BoatType.names()));
                }
            }

            case "setmyboat" -> {
                if (args.length < 3) { err(player, "Uso: /em boatracing setmyboat <jugador> <tipo>"); return; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { err(player, "Jugador '" + args[1] + "' no está online."); return; }
                try {
                    BoatType type = BoatType.valueOf(args[2].toUpperCase());
                    miniGame.getConfig().setPlayerBoat(target.getUniqueId(), type);
                    ok(player, "Bote de " + target.getName() + " seteado a " + type.name() + ".");
                    target.sendMessage(Component.text(
                            "Tu bote fue cambiado a " + type.name() + ".", NamedTextColor.YELLOW));
                } catch (IllegalArgumentException e) {
                    err(player, "Tipo inválido. Opciones: " + String.join(", ", BoatType.names()));
                }
            }

            case "setqualytime" -> {
                if (args.length < 2) { err(player, "Uso: /em boatracing setqualytime <segundos>"); return; }
                try {
                    int s = Integer.parseInt(args[1]);
                    miniGame.getConfig().setQualyDuration(s);
                    ok(player, "Tiempo de qualy: " + s + " segundos.");
                } catch (NumberFormatException e) { err(player, "Número inválido."); }
            }

            case "setscore" -> {
                if (args.length < 3) { err(player, "Uso: /em boatracing setscore <pos> <pts>"); return; }
                try {
                    int pos = Integer.parseInt(args[1]), pts = Integer.parseInt(args[2]);
                    miniGame.getConfig().setScoreForPosition(pos, pts);
                    ok(player, "Posición #" + pos + " → " + pts + " pts.");
                } catch (NumberFormatException e) { err(player, "Valores inválidos."); }
            }

            case "settings" -> {
                var cfg = miniGame.getConfig();
                player.sendMessage(Component.text("━━━ Boat Racing — Config ━━━", NamedTextColor.GOLD));
                info(player, "Estado",         stateToSpanish(miniGame.getState()));
                info(player, "Vueltas",        String.valueOf(cfg.getTotalLaps()));
                info(player, "Bote default",   cfg.getDefaultBoat().name());
                info(player, "Tiempo qualy",   cfg.getQualyDuration() + "s");
                info(player, "Paddock",        cfg.getPaddockSpawn() != null ? locStr(cfg.getPaddockSpawn()) : "✘");
                info(player, "Región track",   cfg.hasTrackRegion() ? "✔ A y B configurados" : "✘");
                info(player, "Línea de meta",  cfg.hasFinishLine() ? "✔ A y B configurados" : "✘");
                info(player, "Spawns parrilla",String.valueOf(cfg.getPlayerSpawns().size()));
                info(player, "Checkpoints",    String.valueOf(cfg.getCheckpoints().size()));
                info(player, "Luces",          cfg.getLights().size() + "/5");
                info(player, "Pts pos 1",      String.valueOf(cfg.getScoreForPosition(1)));
                info(player, "Pts pos 2",      String.valueOf(cfg.getScoreForPosition(2)));
                info(player, "Pts pos 3",      String.valueOf(cfg.getScoreForPosition(3)));
                String err = cfg.validate();
                if (err != null) player.sendMessage(Component.text("⚠ " + err, NamedTextColor.RED));
                else             player.sendMessage(Component.text("✔ Listo para iniciar.", NamedTextColor.GREEN));
            }

            // ── Configuración de la pista ──────────────────────────────────────

            case "track" -> {
                if (args.length < 2) { err(player, "Uso: /em boatracing track a|b"); return; }
                switch (args[1].toLowerCase()) {
                    case "a" -> { miniGame.getConfig().setTrackPointA(player.getLocation()); ok(player, "Punto A de la pista seteado."); }
                    case "b" -> { miniGame.getConfig().setTrackPointB(player.getLocation()); ok(player, "Punto B de la pista seteado."); }
                    default  -> err(player, "Usa 'a' o 'b'.");
                }
            }

            case "finish" -> {
                if (args.length < 2) { err(player, "Uso: /em boatracing finish a|b"); return; }
                switch (args[1].toLowerCase()) {
                    case "a" -> { miniGame.getConfig().setFinishA(player.getLocation()); ok(player, "Extremo A de la meta seteado."); }
                    case "b" -> { miniGame.getConfig().setFinishB(player.getLocation()); ok(player, "Extremo B de la meta seteado."); }
                    default  -> err(player, "Usa 'a' o 'b'.");
                }
            }

            case "addspawn" -> {
                miniGame.getConfig().addPlayerSpawn(player.getLocation());
                int n = miniGame.getConfig().getPlayerSpawns().size();
                ok(player, "Spawn de parrilla #" + n + " agregado.");
            }

            case "clearspawns" -> {
                miniGame.getConfig().clearPlayerSpawns();
                ok(player, "Spawns de parrilla eliminados.");
            }

            case "addcheckpoint" -> {
                double radius = 4.0;
                if (args.length >= 2) {
                    try { radius = Double.parseDouble(args[1]); }
                    catch (NumberFormatException e) { err(player, "Radio inválido, usando 4.0."); }
                }
                miniGame.getConfig().addCheckpoint(player.getLocation(), radius);
                int n = miniGame.getConfig().getCheckpoints().size();
                ok(player, "Checkpoint #" + n + " agregado (radio: " + radius + " bloques).");
            }

            case "clearcheckpoints" -> {
                miniGame.getConfig().clearCheckpoints();
                ok(player, "Checkpoints eliminados.");
            }

            case "addlight" -> {
                int cur = miniGame.getConfig().getLights().size();
                if (cur >= 5) { err(player, "Ya hay 5 luces. Usa /em boatracing clearlights."); return; }
                var target = player.getTargetBlockExact(10);
                if (target == null) { err(player, "No estás mirando ningún bloque (máx 10 bloques)."); return; }
                miniGame.getConfig().addLight(target.getLocation());
                ok(player, "Luz #" + (cur + 1) + "/5 registrada en " + locStr(target.getLocation()) + ".");
            }

            case "clearlights" -> {
                miniGame.getConfig().clearLights();
                ok(player, "Luces eliminadas.");
            }

            // ── Control ───────────────────────────────────────────────────────

            case "paddock" -> {
                if (miniGame.isRunning()) { err(player, "Hay una sesión activa. Usa stop primero."); return; }
                boolean ok = miniGame.sendToPaddock();
                if (ok) ok(player, "Jugadores movidos al paddock.");
                else    err(player, "El paddock no está configurado.");
            }

            case "startqualy" -> {
                String error = miniGame.getConfig().validate();
                if (error != null) { err(player, error); return; }
                boolean ok = miniGame.startQualy();
                if (!ok) err(player, "No se pudo iniciar la qualy. Estado: " + stateToSpanish(miniGame.getState()));
            }

            case "startrace" -> {
                String error = miniGame.getConfig().validate();
                if (error != null) { err(player, error); return; }
                boolean ok = miniGame.startRace();
                if (!ok) err(player, "No se pudo iniciar la carrera. Estado: " + stateToSpanish(miniGame.getState()));
            }

            case "stop" -> {
                if (!miniGame.isRunning() && miniGame.getState() == BoatRacingMiniGame.State.IDLE) {
                    err(player, "No hay ninguna sesión activa."); return;
                }
                miniGame.forceStop();
                ok(player, "Sesión detenida.");
            }

            default -> sendHelp(player);
        }
    }

    // ── Tab completion ─────────────────────────────────────────────────────────

    public List<String> tabComplete(String[] args) {
        if (args.length == 1)
            return filter(List.of(
                    "setpaddock", "setlaps", "setboat", "setmyboat", "setqualytime",
                    "setscore", "settings", "track", "finish", "addspawn", "clearspawns",
                    "addcheckpoint", "clearcheckpoints", "addlight", "clearlights",
                    "paddock", "startqualy", "startrace", "stop"
            ), args[0]);

        if (args[0].equalsIgnoreCase("track") && args.length == 2)
            return filter(List.of("a", "b"), args[1]);
        if (args[0].equalsIgnoreCase("finish") && args.length == 2)
            return filter(List.of("a", "b"), args[1]);
        if (args[0].equalsIgnoreCase("setboat") && args.length == 2)
            return filter(BoatType.names(), args[1]);
        if (args[0].equalsIgnoreCase("setmyboat")) {
            if (args.length == 2)
                return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
            if (args.length == 3)
                return filter(BoatType.names(), args[2]);
        }
        if (args[0].equalsIgnoreCase("setscore") && args.length == 2)
            return filter(List.of("1","2","3","4","5","6","7","8"), args[1]);

        return List.of();
    }

    // ── Ayuda ─────────────────────────────────────────────────────────────────

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("━━━ /em boatracing ━━━", NamedTextColor.GOLD));
        player.sendMessage(Component.text("  Configuración global:", NamedTextColor.GRAY));
        help(player, "/em boatracing setpaddock",           "Spawn del paddock");
        help(player, "/em boatracing setlaps <n>",          "Número de vueltas");
        help(player, "/em boatracing setboat <tipo>",       "Bote por defecto");
        help(player, "/em boatracing setmyboat <j> <tipo>", "Bote de un jugador");
        help(player, "/em boatracing setqualytime <s>",     "Duración máxima de qualy");
        help(player, "/em boatracing setscore <pos> <pts>", "Puntos por posición");
        help(player, "/em boatracing settings",             "Ver configuración");
        player.sendMessage(Component.text("  Pista:", NamedTextColor.GRAY));
        help(player, "/em boatracing track a|b",            "Región de la pista");
        help(player, "/em boatracing finish a|b",           "Línea de meta");
        help(player, "/em boatracing addspawn",             "Spawn de parrilla");
        help(player, "/em boatracing addcheckpoint [r]",    "Checkpoint (radio opcional)");
        help(player, "/em boatracing addlight",             "Luz de largada (máx 5)");
        player.sendMessage(Component.text("  Control:", NamedTextColor.GRAY));
        help(player, "/em boatracing paddock",              "Mover al paddock");
        help(player, "/em boatracing startqualy",           "Iniciar clasificación");
        help(player, "/em boatracing startrace",            "Iniciar carrera");
        help(player, "/em boatracing stop",                 "Detener sesión");
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private String stateToSpanish(BoatRacingMiniGame.State s) {
        return switch (s) {
            case IDLE     -> "En espera";
            case PADDOCK  -> "Paddock";
            case QUALY    -> "Clasificación";
            case RACE     -> "Carrera";
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