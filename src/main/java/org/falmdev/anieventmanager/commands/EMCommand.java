package org.falmdev.anieventmanager.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Lightable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.cinematics.CinematicRecorder;
import org.falmdev.anieventmanager.cinematics.model.Cinematic;
import org.falmdev.anieventmanager.model.EventTeam;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class EMCommand implements CommandExecutor, TabCompleter {

    private final Anieventmanager plugin;

    public EMCommand(Anieventmanager plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo jugadores pueden usar este comando.");
            return true;
        }
        if (!player.isOp()) {
            player.sendMessage(Component.text("No tienes permisos para usar este comando.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) { sendHelp(player); return true; }

        switch (args[0].toLowerCase()) {
            case "team"        -> handleTeam(player,  Arrays.copyOfRange(args, 1, args.length));
            case "score"       -> handleScore(player, Arrays.copyOfRange(args, 1, args.length));
            case "tntrun"      -> plugin.getTNTRunCommand().handle(player, Arrays.copyOfRange(args, 1, args.length));
            case "bingo"       -> plugin.getBingoCommand().handleAdmin(player, Arrays.copyOfRange(args, 1, args.length));
            case "boatracing"  -> plugin.getBoatRacingCommand().handle(player, Arrays.copyOfRange(args, 1, args.length));
            case "frozenheist" -> plugin.getFrozenHeistCommand().handle(player, Arrays.copyOfRange(args, 1, args.length));
            case "pd"          -> plugin.getParkourDuosCommand().handle(player, Arrays.copyOfRange(args, 1, args.length));
            case "cinematic" -> handleCinematic(player, Arrays.copyOfRange(args, 1, args.length));
            case "help"        -> sendHelp(player);
            case "reload"      -> plugin.reloadAll(player);
            default            -> player.sendMessage(Component.text("Subcomando desconocido. Usa ", NamedTextColor.RED)
                    .append(Component.text("/em help", NamedTextColor.YELLOW)));
        }
        return true;
    }

    // ── /em team ──────────────────────────────────────────────────────────────

    private void handleTeam(Player player, String[] args) {
        if (args.length == 0) { sendTeamHelp(player); return; }
        switch (args[0].toLowerCase()) {

            // ── Subcomandos del LOBBY ────────────────────────────────────────
            case "admin" -> plugin.getTeamAdminGUI().openList(player);

            case "refresh" -> {
                plugin.getTeamLobbyManager().refreshAll();
                player.sendMessage(Component.text("✔ Lobby refrescado.", NamedTextColor.GREEN));
            }

            case "registersign" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Uso: /em team registersign <teamId>", NamedTextColor.YELLOW));
                    return;
                }
                Block target = getTargetBlock(player);
                if (target == null || !(target.getState() instanceof Sign)) {
                    player.sendMessage(Component.text("✘ Mira directamente a un cartel.", NamedTextColor.RED));
                    return;
                }
                if (plugin.getTeamManager().getTeam(args[1]).isEmpty()) {
                    player.sendMessage(teamNotFound(args[1]));
                    return;
                }
                plugin.getTeamLobbyManager().getConfig().registerSign(target, args[1]);
                plugin.getTeamLobbyManager().refreshSign(target);
                player.sendMessage(Component.text("✔ Cartel registrado al equipo '", NamedTextColor.GREEN)
                        .append(Component.text(args[1].toLowerCase(), NamedTextColor.YELLOW))
                        .append(Component.text("'.", NamedTextColor.GREEN)));
            }

            case "unregistersign" -> {
                Block target = getTargetBlock(player);
                if (target == null) {
                    player.sendMessage(Component.text("✘ Mira directamente a un cartel.", NamedTextColor.RED));
                    return;
                }
                boolean ok = plugin.getTeamLobbyManager().getConfig().unregisterSign(target);
                player.sendMessage(ok
                        ? Component.text("✔ Cartel desregistrado.", NamedTextColor.GREEN)
                        : Component.text("✘ Ese cartel no estaba registrado.", NamedTextColor.RED));
            }

            case "registerlamp" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Uso: /em team registerlamp <teamId>", NamedTextColor.YELLOW));
                    return;
                }
                Block target = getTargetBlock(player);
                if (target == null || !(target.getBlockData() instanceof Lightable)) {
                    player.sendMessage(Component.text(
                            "✘ Mira directamente a una lámpara, redstone lamp, copper lamp o similar.",
                            NamedTextColor.RED));
                    return;
                }
                if (plugin.getTeamManager().getTeam(args[1]).isEmpty()) {
                    player.sendMessage(teamNotFound(args[1]));
                    return;
                }
                plugin.getTeamLobbyManager().getConfig().registerLamp(target, args[1]);
                plugin.getTeamLobbyManager().refreshLamp(target);
                player.sendMessage(Component.text("✔ Lámpara registrada al equipo '", NamedTextColor.GREEN)
                        .append(Component.text(args[1].toLowerCase(), NamedTextColor.YELLOW))
                        .append(Component.text("'.", NamedTextColor.GREEN)));
            }

            case "unregisterlamp" -> {
                Block target = getTargetBlock(player);
                if (target == null) {
                    player.sendMessage(Component.text("✘ Mira directamente a una lámpara.", NamedTextColor.RED));
                    return;
                }
                boolean ok = plugin.getTeamLobbyManager().getConfig().unregisterLamp(target);
                player.sendMessage(ok
                        ? Component.text("✔ Lámpara desregistrada.", NamedTextColor.GREEN)
                        : Component.text("✘ Esa lámpara no estaba registrada.", NamedTextColor.RED));
            }

            // ── Subcomandos existentes ────────────────────────────────────────
            case "create" -> {
                if (args.length < 3) { player.sendMessage(Component.text("Uso: /em team create <id> <nombre>", NamedTextColor.YELLOW)); return; }
                String id = args[1]; String name = joinFrom(args, 2);
                EventTeam team = plugin.getTeamManager().createTeam(id, name);
                if (team == null) { player.sendMessage(Component.text("Ya existe un equipo con la id '", NamedTextColor.RED).append(Component.text(id, NamedTextColor.YELLOW)).append(Component.text("'.", NamedTextColor.RED))); return; }
                player.sendMessage(Component.text("✔ Equipo ", NamedTextColor.GREEN).append(Component.text(team.getDisplayName(), team.getColor())).append(Component.text(" creado.", NamedTextColor.GREEN)));
            }
            case "delete" -> {
                if (args.length < 2) { player.sendMessage(Component.text("Uso: /em team delete <id>", NamedTextColor.YELLOW)); return; }
                boolean ok = plugin.getTeamManager().deleteTeam(args[1]);
                player.sendMessage(ok ? Component.text("✔ Equipo '", NamedTextColor.GREEN).append(Component.text(args[1], NamedTextColor.YELLOW)).append(Component.text("' eliminado.", NamedTextColor.GREEN)) : Component.text("No existe un equipo con la id '", NamedTextColor.RED).append(Component.text(args[1], NamedTextColor.YELLOW)).append(Component.text("'.", NamedTextColor.RED)));
                plugin.getTeamLobbyManager().refreshAll();
            }
            case "rename" -> {
                if (args.length < 3) { player.sendMessage(Component.text("Uso: /em team rename <id> <nuevo nombre>", NamedTextColor.YELLOW)); return; }
                String newName = joinFrom(args, 2);
                boolean ok = plugin.getTeamManager().renameTeam(args[1], newName);
                if (ok) {
                    var t = plugin.getTeamManager().getTeam(args[1]).get();
                    player.sendMessage(Component.text("✔ Equipo renombrado a ", NamedTextColor.GREEN)
                            .append(Component.text(newName, t.getColor())));
                    plugin.getTeamLobbyManager().refreshForTeam(args[1]);
                } else {
                    player.sendMessage(teamNotFound(args[1]));
                }
            }
            case "add" -> {
                if (args.length < 3) { player.sendMessage(Component.text("Uso: /em team add <id> <jugador>", NamedTextColor.YELLOW)); return; }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) { player.sendMessage(Component.text("El jugador '", NamedTextColor.RED).append(Component.text(args[2], NamedTextColor.YELLOW)).append(Component.text("' no esta online.", NamedTextColor.RED))); return; }
                if (plugin.getTeamManager().getTeam(args[1]).isEmpty()) { player.sendMessage(Component.text("No existe un equipo con la id '", NamedTextColor.RED).append(Component.text(args[1], NamedTextColor.YELLOW)).append(Component.text("'.", NamedTextColor.RED))); return; }
                boolean ok = plugin.getTeamManager().addToTeam(args[1], target);
                if (ok) { EventTeam team = plugin.getTeamManager().getTeam(args[1]).get(); player.sendMessage(Component.text("✔ ", NamedTextColor.GREEN).append(Component.text(target.getName(), NamedTextColor.WHITE)).append(Component.text(" agregado al equipo ", NamedTextColor.GREEN)).append(Component.text(team.getDisplayName(), team.getColor())).append(Component.text(".", NamedTextColor.GREEN))); target.sendMessage(Component.text("✔ Te agregaron al equipo ", NamedTextColor.GREEN).append(Component.text(team.getDisplayName(), team.getColor())).append(Component.text(".", NamedTextColor.GREEN))); plugin.getTeamLobbyManager().refreshForTeam(args[1]); }
                else player.sendMessage(Component.text("No se pudo agregar — el equipo puede estar lleno (max. 2).", NamedTextColor.RED));
            }
            case "remove" -> {
                if (args.length < 2) { player.sendMessage(Component.text("Uso: /em team remove <jugador>", NamedTextColor.YELLOW)); return; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { player.sendMessage(Component.text("El jugador '", NamedTextColor.RED).append(Component.text(args[1], NamedTextColor.YELLOW)).append(Component.text("' no esta online.", NamedTextColor.RED))); return; }
                var teamBefore = plugin.getTeamManager().getTeamOf(target);
                boolean ok = plugin.getTeamManager().removeFromCurrentTeam(target);
                player.sendMessage(ok ? Component.text("✔ ", NamedTextColor.GREEN).append(Component.text(target.getName(), NamedTextColor.WHITE)).append(Component.text(" removido de su equipo.", NamedTextColor.GREEN)) : Component.text(target.getName() + " no esta en ningun equipo.", NamedTextColor.RED));
                teamBefore.ifPresent(t -> plugin.getTeamLobbyManager().refreshForTeam(t.getId()));
            }
            case "list" -> {
                var allTeams = plugin.getTeamManager().getAllTeams();
                if (allTeams.isEmpty()) { player.sendMessage(Component.text("No hay equipos creados.", NamedTextColor.GRAY)); return; }
                boolean ff = plugin.getTeamManager().isFriendlyFireEnabled();
                player.sendMessage(Component.text("━━━ Equipos (" + allTeams.size() + ") ━━━", NamedTextColor.GOLD).append(Component.text("  Friendly fire: ", NamedTextColor.GRAY)).append(ff ? Component.text("ON", NamedTextColor.GREEN) : Component.text("OFF", NamedTextColor.RED)));
                for (EventTeam team : allTeams) {
                    List<String> memberNames = team.getOnlinePlayers().stream().map(Player::getName).toList();
                    String members = memberNames.isEmpty() ? "sin jugadores" : String.join(", ", memberNames);
                    int score = plugin.getScoreManager().getScore(team);
                    player.sendMessage(Component.text("  " + team.getId() + " → ", NamedTextColor.GRAY).append(Component.text(team.getDisplayName(), team.getColor())).append(Component.text("  [" + members + "]", NamedTextColor.DARK_GRAY)).append(Component.text("  (" + team.getMemberCount() + "/2)", NamedTextColor.GRAY)).append(Component.text("  " + score + " pts", NamedTextColor.YELLOW)));
                }
            }
            case "clear" -> { plugin.getTeamManager().clearAll(); plugin.getTeamLobbyManager().refreshAll(); player.sendMessage(Component.text("✔ Todos los equipos eliminados.", NamedTextColor.GREEN)); }
            case "friendlyfire" -> {
                if (args.length < 2) { boolean c = plugin.getTeamManager().isFriendlyFireEnabled(); player.sendMessage(Component.text("Friendly fire: ", NamedTextColor.GRAY).append(c ? Component.text("ON", NamedTextColor.GREEN) : Component.text("OFF", NamedTextColor.RED))); return; }
                switch (args[1].toLowerCase()) {
                    case "on"  -> { plugin.getTeamManager().setFriendlyFire(true);  player.sendMessage(Component.text("✔ Friendly fire activado.", NamedTextColor.GREEN)); }
                    case "off" -> { plugin.getTeamManager().setFriendlyFire(false); player.sendMessage(Component.text("✔ Friendly fire desactivado.", NamedTextColor.RED)); }
                    default    -> player.sendMessage(Component.text("Uso: /em team friendlyfire <on|off>", NamedTextColor.YELLOW));
                }
            }
            default -> sendTeamHelp(player);
        }
    }

    // ── /em score (sin cambios) ───────────────────────────────────────────────

    private void handleScore(Player player, String[] args) {
        if (args.length == 0) { sendScoreHelp(player); return; }
        switch (args[0].toLowerCase()) {
            case "add" -> {
                if (args.length < 3) { player.sendMessage(Component.text("Uso: /em score add <id> <puntos>", NamedTextColor.YELLOW)); return; }
                var teamOpt = plugin.getTeamManager().getTeam(args[1]);
                if (teamOpt.isEmpty()) { player.sendMessage(teamNotFound(args[1])); return; }
                try { int pts = Integer.parseInt(args[2]); if (pts <= 0) { player.sendMessage(Component.text("Los puntos deben ser positivos.", NamedTextColor.RED)); return; } EventTeam t = teamOpt.get(); plugin.getScoreManager().addScore(t, pts); player.sendMessage(Component.text("✔ +" + pts + " pts al equipo ", NamedTextColor.GREEN).append(Component.text(t.getDisplayName(), t.getColor())).append(Component.text("  (total: " + plugin.getScoreManager().getScore(t) + " pts)", NamedTextColor.GRAY))); } catch (NumberFormatException e) { player.sendMessage(Component.text("Valor inválido.", NamedTextColor.RED)); }
            }
            case "remove" -> {
                if (args.length < 3) { player.sendMessage(Component.text("Uso: /em score remove <id> <puntos>", NamedTextColor.YELLOW)); return; }
                var teamOpt = plugin.getTeamManager().getTeam(args[1]);
                if (teamOpt.isEmpty()) { player.sendMessage(teamNotFound(args[1])); return; }
                try { int pts = Integer.parseInt(args[2]); if (pts <= 0) { player.sendMessage(Component.text("Los puntos deben ser positivos.", NamedTextColor.RED)); return; } EventTeam t = teamOpt.get(); plugin.getScoreManager().removeScore(t, pts); player.sendMessage(Component.text("✔ -" + pts + " pts al equipo ", NamedTextColor.RED).append(Component.text(t.getDisplayName(), t.getColor())).append(Component.text("  (total: " + plugin.getScoreManager().getScore(t) + " pts)", NamedTextColor.GRAY))); } catch (NumberFormatException e) { player.sendMessage(Component.text("Valor inválido.", NamedTextColor.RED)); }
            }
            case "set" -> {
                if (args.length < 3) { player.sendMessage(Component.text("Uso: /em score set <id> <puntos>", NamedTextColor.YELLOW)); return; }
                var teamOpt = plugin.getTeamManager().getTeam(args[1]);
                if (teamOpt.isEmpty()) { player.sendMessage(teamNotFound(args[1])); return; }
                try { int pts = Integer.parseInt(args[2]); EventTeam t = teamOpt.get(); plugin.getScoreManager().setScore(t, pts); player.sendMessage(Component.text("✔ Puntaje de ", NamedTextColor.GREEN).append(Component.text(t.getDisplayName(), t.getColor())).append(Component.text(" seteado a " + pts + " pts.", NamedTextColor.GREEN))); } catch (NumberFormatException e) { player.sendMessage(Component.text("Valor inválido.", NamedTextColor.RED)); }
            }
            case "reset" -> {
                if (args.length < 2) { player.sendMessage(Component.text("Uso: /em score reset <id|all>", NamedTextColor.YELLOW)); return; }
                if (args[1].equalsIgnoreCase("all")) { plugin.getScoreManager().resetAll(); player.sendMessage(Component.text("✔ Todos los puntajes reseteados.", NamedTextColor.GREEN)); }
                else { var teamOpt = plugin.getTeamManager().getTeam(args[1]); if (teamOpt.isEmpty()) { player.sendMessage(teamNotFound(args[1])); return; } plugin.getScoreManager().resetScore(teamOpt.get()); player.sendMessage(Component.text("✔ Puntaje reseteado.", NamedTextColor.GREEN)); }
            }
            case "list" -> {
                var lb = plugin.getScoreManager().getLeaderboard();
                if (lb.isEmpty()) { player.sendMessage(Component.text("No hay puntajes registrados.", NamedTextColor.GRAY)); return; }
                player.sendMessage(Component.text("━━━ Tabla de puntajes ━━━", NamedTextColor.GOLD));
                int pos = 1;
                for (Map.Entry<String, Integer> entry : lb) {
                    var t = plugin.getTeamManager().getTeam(entry.getKey());
                    String name = t.map(EventTeam::getDisplayName).orElse(entry.getKey());
                    NamedTextColor col = t.map(EventTeam::getColor).orElse(NamedTextColor.WHITE);
                    String medal = switch (pos) { case 1 -> "§6#1"; case 2 -> "§7#2"; case 3 -> "§c#3"; default -> "§f#" + pos; };
                    player.sendMessage(Component.text("  " + medal + " ").append(Component.text(name, col)).append(Component.text(" — " + entry.getValue() + " pts", NamedTextColor.YELLOW)));
                    pos++;
                }
            }
            default -> sendScoreHelp(player);
        }
    }

    // ── Tab completion ─────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || !player.isOp()) return List.of();

        if (args.length == 1)
            return filter(List.of("team", "score", "tntrun", "bingo", "boatracing", "frozenheist", "pd", "cinematic", "help", "reload"), args[0]);

        if (args[0].equalsIgnoreCase("team")) {
            if (args.length == 2) return filter(List.of(
                    "create", "delete", "rename", "add", "remove", "list", "clear", "friendlyfire",
                    "admin", "refresh",
                    "registersign", "unregistersign",
                    "registerlamp", "unregisterlamp"), args[1]);
            if (args[1].equalsIgnoreCase("delete") && args.length == 3) return filter(new ArrayList<>(plugin.getTeamManager().getTeamIds()), args[2]);
            if (args[1].equalsIgnoreCase("rename") && args.length == 3) return filter(new ArrayList<>(plugin.getTeamManager().getTeamIds()), args[2]);
            if (args[1].equalsIgnoreCase("add")) { if (args.length == 3) return filter(new ArrayList<>(plugin.getTeamManager().getTeamIds()), args[2]); if (args.length == 4) return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[3]); }
            if (args[1].equalsIgnoreCase("remove") && args.length == 3) return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
            if (args[1].equalsIgnoreCase("friendlyfire") && args.length == 3) return filter(List.of("on", "off"), args[2]);
            if (args[1].equalsIgnoreCase("record") && args.length == 4)
                return filter(List.of("30s", "1m", "2m", "2m30s", "5m", "10m"), args[3]);
            if ((args[1].equalsIgnoreCase("registersign") || args[1].equalsIgnoreCase("registerlamp"))
                    && args.length == 3) {
                return filter(new ArrayList<>(plugin.getTeamManager().getTeamIds()), args[2]);
            }
        }

        if (args[0].equalsIgnoreCase("score")) {
            if (args.length == 2) return filter(List.of("add", "remove", "set", "reset", "list"), args[1]);
            if (args.length == 3) { if (args[1].equalsIgnoreCase("reset")) { List<String> opts = new ArrayList<>(plugin.getTeamManager().getTeamIds()); opts.add("all"); return filter(opts, args[2]); } if (List.of("add", "remove", "set").contains(args[1].toLowerCase())) return filter(new ArrayList<>(plugin.getTeamManager().getTeamIds()), args[2]); }
            if (args.length == 4 && List.of("add", "remove", "set").contains(args[1].toLowerCase())) return filter(List.of("5", "10", "15", "20"), args[3]);
        }

        if (args[0].equalsIgnoreCase("tntrun"))
            return plugin.getTNTRunCommand().tabComplete(Arrays.copyOfRange(args, 1, args.length));

        if (args[0].equalsIgnoreCase("bingo"))
            return plugin.getBingoCommand().tabComplete(Arrays.copyOfRange(args, 1, args.length));

        if (args[0].equalsIgnoreCase("boatracing"))
            return plugin.getBoatRacingCommand().tabComplete(Arrays.copyOfRange(args, 1, args.length));

        if (args[0].equalsIgnoreCase("frozenheist"))
            return plugin.getFrozenHeistCommand().tabComplete(Arrays.copyOfRange(args, 1, args.length));

         if (args[0].equalsIgnoreCase("cinematic")) {
             if (args.length == 2) return filter(List.of(
                     "create", "delete", "list", "record", "stop-record", "play", "stop", "gui"), args[1]);
            if (args.length == 3 && List.of("delete", "record", "play").contains(args[1].toLowerCase()))
                 return filter(new ArrayList<>(plugin.getCinematicManager().getIds()), args[2]);
         }

        if (args[0].equalsIgnoreCase("pd"))
            return plugin.getParkourDuosCommand().tabComplete(Arrays.copyOfRange(args, 1, args.length));

        return List.of();
    }

    // ── Ayuda ─────────────────────────────────────────────────────────────────

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("━━━ AniEventManager ━━━", NamedTextColor.GOLD));
        player.sendMessage(help("/em team ...",        "Gestion de equipos"));
        player.sendMessage(help("/em score ...",       "Gestion de puntajes"));
        player.sendMessage(help("/em tntrun ...",      "Minijuego TNT Run"));
        player.sendMessage(help("/em bingo ...",       "Minijuego Bingo"));
        player.sendMessage(help("/em boatracing ...",  "Minijuego Boat Racing"));
        player.sendMessage(help("/em frozenheist ...", "Minijuego Frozen Heist"));
        player.sendMessage(help("/em cinematic ...", "Gestión de cinematicas"));
        player.sendMessage(help("/em pd ...",          "Minijuego Parkour Duos"));
        player.sendMessage(help("/em reload",          "Recarga la configuración"));
        player.sendMessage(help("/em help",            "Muestra esta ayuda"));
    }

    private void sendTeamHelp(Player player) {
        player.sendMessage(Component.text("━━━ /em team ━━━", NamedTextColor.GOLD));
        player.sendMessage(Component.text("  Gestión básica:", NamedTextColor.GRAY));
        player.sendMessage(help("/em team create <id> <nombre>",  "Crea un equipo"));
        player.sendMessage(help("/em team delete <id>",           "Elimina un equipo"));
        player.sendMessage(help("/em team rename <id> <nombre>",  "Renombra un equipo"));
        player.sendMessage(help("/em team add <id> <jugador>",    "Agrega jugador al equipo"));
        player.sendMessage(help("/em team remove <jugador>",      "Saca al jugador de su equipo"));
        player.sendMessage(help("/em team list",                  "Lista equipos y puntajes"));
        player.sendMessage(help("/em team clear",                 "Elimina todos los equipos"));
        player.sendMessage(help("/em team friendlyfire <on|off>", "Activa/desactiva fuego amigo"));
        player.sendMessage(Component.text("  Lobby de selección:", NamedTextColor.GRAY));
        player.sendMessage(help("/em team admin",                  "Abre el GUI de administración"));
        player.sendMessage(help("/em team refresh",                "Refresca carteles y lámparas"));
        player.sendMessage(help("/em team registersign <id>",      "Registra el cartel que miras"));
        player.sendMessage(help("/em team unregistersign",         "Desregistra el cartel que miras"));
        player.sendMessage(help("/em team registerlamp <id>",      "Registra la lámpara que miras"));
        player.sendMessage(help("/em team unregisterlamp",         "Desregistra la lámpara que miras"));
    }

    private void sendScoreHelp(Player player) {
        player.sendMessage(Component.text("━━━ /em score ━━━", NamedTextColor.GOLD));
        player.sendMessage(help("/em score add <id> <pts>",    "Agrega puntos"));
        player.sendMessage(help("/em score remove <id> <pts>", "Resta puntos"));
        player.sendMessage(help("/em score set <id> <pts>",    "Setea el puntaje exacto"));
        player.sendMessage(help("/em score reset <id|all>",    "Resetea puntaje(s)"));
        player.sendMessage(help("/em score list",              "Tabla de puntajes"));
    }

    private void handleCinematic(Player player, String[] args) {
        if (args.length == 0) { sendCinematicHelp(player); return; }

        switch (args[0].toLowerCase()) {

            case "create" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("Uso: /em cinematic create <id> <nombre>",
                            NamedTextColor.YELLOW));
                    return;
                }
                String id   = args[1].toLowerCase();
                String name = joinFrom(args, 2);
                var c = plugin.getCinematicManager().create(id, name);
                if (c == null) {
                    player.sendMessage(Component.text("✘ Ya existe una cinematica con la id '",
                                    NamedTextColor.RED)
                            .append(Component.text(id, NamedTextColor.YELLOW))
                            .append(Component.text("'.", NamedTextColor.RED)));
                } else {
                    player.sendMessage(Component.text("✔ Cinematica '", NamedTextColor.GREEN)
                            .append(Component.text(name, NamedTextColor.YELLOW))
                            .append(Component.text("' creada.", NamedTextColor.GREEN)));
                }
            }

            case "delete" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Uso: /em cinematic delete <id>",
                            NamedTextColor.YELLOW));
                    return;
                }
                boolean ok = plugin.getCinematicManager().delete(args[1]);
                player.sendMessage(ok
                        ? Component.text("✔ Cinematica eliminada.", NamedTextColor.GREEN)
                        : Component.text("✘ No se pudo eliminar (no existe o está en uso).",
                        NamedTextColor.RED));
            }

            case "list" -> {
                var all = plugin.getCinematicManager().getAllCinematics();
                if (all.isEmpty()) {
                    player.sendMessage(Component.text("No hay cinematicas creadas.",
                            NamedTextColor.GRAY));
                    return;
                }
                player.sendMessage(Component.text("━━━ Cinematicas (" + all.size() + ") ━━━",
                        NamedTextColor.GOLD));
                for (var c : all) {
                    player.sendMessage(
                            Component.text("  " + c.getId() + " → ", NamedTextColor.GRAY)
                                    .append(Component.text(c.getDisplayName(), NamedTextColor.YELLOW))
                                    .append(Component.text(
                                            "  [" + c.getTotalFrames() + " frames  " +
                                                    String.format("%.1fs", c.getDurationSeconds()) + "]",
                                            NamedTextColor.DARK_GRAY))
                                    .append(Component.text(
                                            "  " + c.getMarkers().size() + " markers",
                                            NamedTextColor.DARK_GRAY))
                                    .append(Component.text(
                                            "  " + c.getState().name(),
                                            NamedTextColor.GRAY)));
                }
            }

            case "record" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text(
                            "Uso: /em cinematic record <id> <duración>",
                            NamedTextColor.YELLOW));
                    player.sendMessage(Component.text(
                            "  Ejemplos: 30s  |  1m  |  2m30s  |  5m",
                            NamedTextColor.GRAY));
                    return;
                }
                var cOpt = plugin.getCinematicManager().get(args[1]);
                if (cOpt.isEmpty()) {
                    player.sendMessage(Component.text("✘ Cinematica no encontrada.",
                            NamedTextColor.RED));
                    return;
                }
                int durationTicks = CinematicRecorder.parseDuration(args[2]);
                if (durationTicks < 0) {
                    player.sendMessage(Component.text(
                            "✘ Duración inválida. Usa: 30s, 1m, 2m30s (mín 2s, máx 10m).",
                            NamedTextColor.RED));
                    return;
                }
                boolean ok = plugin.getCinematicManager()
                        .startRecording(player, cOpt.get(), durationTicks);
                if (!ok) {
                    player.sendMessage(Component.text(
                            "✘ No se pudo iniciar (ya hay grabación activa o cinematica no está IDLE).",
                            NamedTextColor.RED));
                }
            }

            case "stop-record" -> {
                if (!plugin.getCinematicManager().getRecorder().isRecording()) {
                    player.sendMessage(Component.text(
                            "No hay ninguna grabación activa.", NamedTextColor.GRAY));
                    return;
                }
                Cinematic recordingCinematic =
                        plugin.getCinematicManager().getRecorder().getRecordingCinematic();
                if (recordingCinematic != null) {
                    plugin.getCinematicManager().setCinematicWorld(
                            recordingCinematic.getId(), player.getWorld());
                }
                plugin.getCinematicManager().getRecorder().stopRecording();
            }

            case "play" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Uso: /em cinematic play <id>",
                            NamedTextColor.YELLOW));
                    return;
                }
                boolean ok = plugin.getCinematicManager().play(args[1]);
                player.sendMessage(ok
                        ? Component.text("▶ Cinematica reproduciéndose.", NamedTextColor.GREEN)
                        : Component.text(
                        "✘ No se pudo reproducir (no existe, sin frames grabados, o ya hay una activa).",
                        NamedTextColor.RED));
            }

            case "stop" -> {
                boolean ok = plugin.getCinematicManager().stop();
                player.sendMessage(ok
                        ? Component.text("■ Reproducción detenida.", NamedTextColor.YELLOW)
                        : Component.text("No hay ninguna cinematica reproduciéndose.",
                        NamedTextColor.GRAY));
            }

            case "gui" -> plugin.getCinematicAdminGUI().openList(player);

            default -> sendCinematicHelp(player);
        }
    }

    private void sendCinematicHelp(Player player) {
        player.sendMessage(Component.text("━━━ /em cinematic ━━━", NamedTextColor.GOLD));
        player.sendMessage(help("/em cinematic create <id> <nombre>", "Crea una cinematica"));
        player.sendMessage(help("/em cinematic delete <id>",          "Elimina una cinematica"));
        player.sendMessage(help("/em cinematic list",                  "Lista todas las cinematicas"));
        player.sendMessage(help("/em cinematic record <id>",           "Inicia grabación con Magic Stick"));
        player.sendMessage(help("/em cinematic stop-record",           "Termina la grabación actual"));
        player.sendMessage(help("/em cinematic play <id>",             "Reproduce una cinematica (debug)"));
        player.sendMessage(help("/em cinematic stop",                  "Detiene la reproducción"));
        player.sendMessage(help("/em cinematic gui",                   "Abre el panel de administración"));
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    /** Raytrace: devuelve el bloque que el jugador está mirando, max 6 bloques. */
    private Block getTargetBlock(Player player) {
        return player.getTargetBlockExact(6);
    }

    private Component help(String cmd, String desc) {
        return Component.text("  " + cmd, NamedTextColor.YELLOW)
                .append(Component.text(" — " + desc, NamedTextColor.GRAY));
    }

    private Component teamNotFound(String id) {
        return Component.text("No existe un equipo con la id '", NamedTextColor.RED)
                .append(Component.text(id, NamedTextColor.YELLOW))
                .append(Component.text("'.", NamedTextColor.RED));
    }

    private String joinFrom(String[] arr, int from) {
        return String.join(" ", Arrays.copyOfRange(arr, from, arr.length));
    }

    private List<String> filter(List<String> options, String input) {
        String lower = input.toLowerCase();
        return options.stream().filter(s -> s.toLowerCase().startsWith(lower)).toList();
    }
}