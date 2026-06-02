package org.falmdev.anieventmanager.minigames.pvpfinal;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.minigames.pvpfinal.arena.PvpArena;
import org.falmdev.anieventmanager.minigames.pvpfinal.kit.PvpKit;
import org.falmdev.anieventmanager.minigames.pvpfinal.model.CombatMode;
import org.falmdev.anieventmanager.model.EventTeam;

import java.util.*;

/**
 * Comando del minijuego PvP Final.
 *
 * Estructura:
 *   /em pvpfinal arena create
 *   /em pvpfinal arena setspawn <n>
 *   /em pvpfinal arena setlobby
 *   /em pvpfinal arena delete
 *   /em pvpfinal arena info
 *
 *   /em pvpfinal kit create <name>
 *   /em pvpfinal kit delete <name>
 *   /em pvpfinal kit list
 *   /em pvpfinal kit preview <name>
 *
 *   /em pvpfinal start 1v1 <p1> <p2> <kit>
 *   /em pvpfinal start teamvsteam <team1> <team2> <kit>
 *   /em pvpfinal start allteams <kit>
 *   /em pvpfinal start ffa <kit>
 *   /em pvpfinal stop
 *   /em pvpfinal status
 */
public class PvpFinalCommand {

    private final Anieventmanager  plugin;
    private final PvpFinalMiniGame game;

    public PvpFinalCommand(Anieventmanager plugin, PvpFinalMiniGame game) {
        this.plugin = plugin;
        this.game   = game;
    }

    public void handle(Player player, String[] args) {
        if (args.length == 0) { sendHelp(player); return; }

        switch (args[0].toLowerCase()) {
            case "arena"  -> handleArena(player, args);
            case "kit"    -> handleKit(player, args);
            case "start"  -> handleStart(player, args);
            case "stop"   -> handleStop(player);
            case "status" -> handleStatus(player);
            default       -> sendHelp(player);
        }
    }

    // ── arena ────────────────────────────────────────────────────────────────

    private void handleArena(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                    "Uso: /em pvpfinal arena [create|setspawn|setlobby|delete|info]",
                    NamedTextColor.YELLOW));
            return;
        }
        var am = game.getArenaManager();

        switch (args[1].toLowerCase()) {
            case "create" -> {
                String name = args.length >= 3 ? args[2] : "main";
                am.createArena(name);
                player.sendMessage(Component.text("✔ Arena '" + name + "' creada.",
                        NamedTextColor.GREEN));
            }
            case "setspawn" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("Uso: arena setspawn <numero>",
                            NamedTextColor.YELLOW));
                    return;
                }
                if (!am.hasArena()) {
                    player.sendMessage(Component.text("✘ No hay arena. Usa 'arena create' primero.",
                            NamedTextColor.RED));
                    return;
                }
                int n;
                try { n = Integer.parseInt(args[2]); }
                catch (NumberFormatException e) {
                    player.sendMessage(Component.text("✘ Número inválido.", NamedTextColor.RED));
                    return;
                }
                if (n < 1) {
                    player.sendMessage(Component.text("✘ El número debe ser >= 1.", NamedTextColor.RED));
                    return;
                }
                am.setSpawn(n, player.getLocation());
                player.sendMessage(Component.text("✔ Spawn #" + n + " configurado.",
                        NamedTextColor.GREEN));
            }
            case "setlobby" -> {
                if (!am.hasArena()) {
                    player.sendMessage(Component.text("✘ No hay arena. Usa 'arena create' primero.",
                            NamedTextColor.RED));
                    return;
                }
                am.setLobby(player.getLocation());
                player.sendMessage(Component.text("✔ Lobby configurado.", NamedTextColor.GREEN));
            }
            case "delete" -> {
                am.deleteArena();
                player.sendMessage(Component.text("✔ Arena eliminada.", NamedTextColor.YELLOW));
            }
            case "info" -> {
                if (!am.hasArena()) {
                    player.sendMessage(Component.text("✘ No hay arena.", NamedTextColor.RED));
                    return;
                }
                PvpArena a = am.getArena();
                player.sendMessage(Component.text("─── Arena ───", NamedTextColor.GOLD));
                player.sendMessage(Component.text("  Nombre: ", NamedTextColor.GRAY)
                        .append(Component.text(a.getName(), NamedTextColor.WHITE)));
                player.sendMessage(Component.text("  Spawns: ", NamedTextColor.GRAY)
                        .append(Component.text(a.getSpawnCount(), NamedTextColor.WHITE)));
                player.sendMessage(Component.text("  Lobby: ", NamedTextColor.GRAY)
                        .append(Component.text(a.getLobby() != null ? "✔" : "✘",
                                a.getLobby() != null ? NamedTextColor.GREEN : NamedTextColor.RED)));
                player.sendMessage(Component.text("  Ready: ", NamedTextColor.GRAY)
                        .append(Component.text(a.isReady() ? "Sí" : "No",
                                a.isReady() ? NamedTextColor.GREEN : NamedTextColor.RED)));
            }
            default -> player.sendMessage(Component.text(
                    "Uso: /em pvpfinal arena [create|setspawn|setlobby|delete|info]",
                    NamedTextColor.YELLOW));
        }
    }

    // ── kit ──────────────────────────────────────────────────────────────────

    private void handleKit(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Uso: /em pvpfinal kit [create|delete|list|preview]",
                    NamedTextColor.YELLOW));
            return;
        }
        var km = game.getKitManager();

        switch (args[1].toLowerCase()) {
            case "create" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("Uso: kit create <nombre>", NamedTextColor.YELLOW));
                    return;
                }
                String name = args[2];
                boolean overwrite = km.exists(name);
                km.createFromPlayer(name, player);
                player.sendMessage(Component.text(
                        "✔ Kit '" + name + "' " + (overwrite ? "actualizado" : "creado") + ".",
                        NamedTextColor.GREEN));
            }
            case "delete" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("Uso: kit delete <nombre>", NamedTextColor.YELLOW));
                    return;
                }
                String name = args[2];
                if (km.delete(name)) {
                    player.sendMessage(Component.text("✔ Kit '" + name + "' eliminado.",
                            NamedTextColor.YELLOW));
                } else {
                    player.sendMessage(Component.text("✘ Kit no encontrado.", NamedTextColor.RED));
                }
            }
            case "list" -> {
                player.sendMessage(Component.text("─── Kits (" + km.count() + ") ───",
                        NamedTextColor.GOLD));
                if (km.count() == 0) {
                    player.sendMessage(Component.text("  Sin kits. Usa 'kit create <nombre>'.",
                            NamedTextColor.GRAY));
                } else {
                    for (String name : km.getNames()) {
                        player.sendMessage(Component.text("  · " + name, NamedTextColor.WHITE));
                    }
                }
            }
            case "preview" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("Uso: kit preview <nombre>", NamedTextColor.YELLOW));
                    return;
                }
                PvpKit kit = km.get(args[2]);
                if (kit == null) {
                    player.sendMessage(Component.text("✘ Kit no encontrado.", NamedTextColor.RED));
                    return;
                }
                player.sendMessage(Component.text("─── Kit: " + kit.getName() + " ───",
                        NamedTextColor.GOLD));
                int itemCount = 0;
                for (var item : kit.getContents()) if (item != null) itemCount++;
                int armorCount = 0;
                for (var a : kit.getArmor()) if (a != null) armorCount++;
                player.sendMessage(Component.text("  Items: " + itemCount, NamedTextColor.GRAY));
                player.sendMessage(Component.text("  Armadura: " + armorCount, NamedTextColor.GRAY));
                player.sendMessage(Component.text("  Offhand: "
                        + (kit.getOffhand() != null ? "✔" : "✘"), NamedTextColor.GRAY));
            }
            default -> player.sendMessage(Component.text(
                    "Uso: /em pvpfinal kit [create|delete|list|preview]", NamedTextColor.YELLOW));
        }
    }

    // ── start ────────────────────────────────────────────────────────────────

    private void handleStart(Player player, String[] args) {
        if (game.getCombatManager().isActive()) {
            player.sendMessage(Component.text("✘ Ya hay un combate activo. Usa 'stop' primero.",
                    NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text(
                    "Uso: /em pvpfinal start [1v1|teamvsteam|allteams|ffa] ...",
                    NamedTextColor.YELLOW));
            return;
        }

        switch (args[1].toLowerCase()) {
            case "1v1"        -> startOneVsOne(player, args);
            case "teamvsteam" -> startTeamVsTeam(player, args);
            case "allteams"   -> startAllTeams(player, args);
            case "ffa"        -> startFfa(player, args);
            default -> player.sendMessage(Component.text(
                    "Modo desconocido. Usa: 1v1, teamvsteam, allteams, ffa",
                    NamedTextColor.RED));
        }
    }

    private void startOneVsOne(Player sender, String[] args) {
        // /em pvpfinal start 1v1 <p1> <p2> <kit>
        if (args.length < 5) {
            sender.sendMessage(Component.text(
                    "Uso: start 1v1 <jugador1> <jugador2> <kit>", NamedTextColor.YELLOW));
            return;
        }
        Player p1 = Bukkit.getPlayerExact(args[2]);
        Player p2 = Bukkit.getPlayerExact(args[3]);
        PvpKit kit = game.getKitManager().get(args[4]);

        if (p1 == null) { sender.sendMessage(Component.text("✘ " + args[2] + " no está online.", NamedTextColor.RED)); return; }
        if (p2 == null) { sender.sendMessage(Component.text("✘ " + args[3] + " no está online.", NamedTextColor.RED)); return; }
        if (p1.equals(p2)) { sender.sendMessage(Component.text("✘ Los jugadores deben ser diferentes.", NamedTextColor.RED)); return; }
        if (kit == null) { sender.sendMessage(Component.text("✘ Kit '" + args[4] + "' no existe.", NamedTextColor.RED)); return; }

        // Mismo equipo → friendly fire ON
        var t1 = plugin.getTeamManager().getTeamOf(p1);
        var t2 = plugin.getTeamManager().getTeamOf(p2);
        boolean sameTeam = t1.isPresent() && t2.isPresent()
                && t1.get().getId().equals(t2.get().getId());

        Map<UUID, String> teamMap = new HashMap<>();
        t1.ifPresent(t -> teamMap.put(p1.getUniqueId(), t.getId()));
        t2.ifPresent(t -> teamMap.put(p2.getUniqueId(), t.getId()));

        boolean started = game.getCombatManager().startCombat(
                CombatMode.ONE_VS_ONE,
                List.of(p1, p2),
                teamMap,
                kit,
                sameTeam);  // friendly fire si son del mismo equipo

        if (!started) {
            sender.sendMessage(Component.text(
                    "✘ No se pudo iniciar (arena no lista o ya hay combate).",
                    NamedTextColor.RED));
        }
    }

    private void startTeamVsTeam(Player sender, String[] args) {
        // /em pvpfinal start teamvsteam <team1> <team2> <kit>
        if (args.length < 5) {
            sender.sendMessage(Component.text(
                    "Uso: start teamvsteam <equipo1> <equipo2> <kit>", NamedTextColor.YELLOW));
            return;
        }
        var t1Opt = plugin.getTeamManager().getTeam(args[2]);
        var t2Opt = plugin.getTeamManager().getTeam(args[3]);
        PvpKit kit = game.getKitManager().get(args[4]);

        if (t1Opt.isEmpty()) { sender.sendMessage(Component.text("✘ Equipo '" + args[2] + "' no existe.", NamedTextColor.RED)); return; }
        if (t2Opt.isEmpty()) { sender.sendMessage(Component.text("✘ Equipo '" + args[3] + "' no existe.", NamedTextColor.RED)); return; }
        if (t1Opt.get().getId().equals(t2Opt.get().getId())) {
            sender.sendMessage(Component.text("✘ Los equipos deben ser diferentes.", NamedTextColor.RED)); return;
        }
        if (kit == null) { sender.sendMessage(Component.text("✘ Kit no existe.", NamedTextColor.RED)); return; }

        List<Player> participants = new ArrayList<>();
        Map<UUID, String> teamMap = new HashMap<>();
        for (Player p : t1Opt.get().getOnlinePlayers()) {
            participants.add(p);
            teamMap.put(p.getUniqueId(), t1Opt.get().getId());
        }
        for (Player p : t2Opt.get().getOnlinePlayers()) {
            participants.add(p);
            teamMap.put(p.getUniqueId(), t2Opt.get().getId());
        }

        if (participants.size() < 2) {
            sender.sendMessage(Component.text("✘ No hay suficientes jugadores online.", NamedTextColor.RED));
            return;
        }

        boolean started = game.getCombatManager().startCombat(
                CombatMode.TEAM_VS_TEAM, participants, teamMap, kit, false);
        if (!started) sender.sendMessage(Component.text("✘ No se pudo iniciar el combate.", NamedTextColor.RED));
    }

    private void startAllTeams(Player sender, String[] args) {
        // /em pvpfinal start allteams <kit>
        if (args.length < 3) {
            sender.sendMessage(Component.text("Uso: start allteams <kit>", NamedTextColor.YELLOW));
            return;
        }
        PvpKit kit = game.getKitManager().get(args[2]);
        if (kit == null) { sender.sendMessage(Component.text("✘ Kit no existe.", NamedTextColor.RED)); return; }

        List<Player> participants = new ArrayList<>();
        Map<UUID, String> teamMap = new HashMap<>();
        for (EventTeam team : plugin.getTeamManager().getTeams().values()) {
            for (Player p : team.getOnlinePlayers()) {
                participants.add(p);
                teamMap.put(p.getUniqueId(), team.getId());
            }
        }
        if (participants.size() < 2) {
            sender.sendMessage(Component.text("✘ No hay suficientes jugadores en equipos.", NamedTextColor.RED));
            return;
        }
        boolean started = game.getCombatManager().startCombat(
                CombatMode.ALL_TEAMS, participants, teamMap, kit, false);
        if (!started) sender.sendMessage(Component.text("✘ No se pudo iniciar el combate.", NamedTextColor.RED));
    }

    private void startFfa(Player sender, String[] args) {
        // /em pvpfinal start ffa <kit>
        // Todos vs todos, respetando equipos (no friendly fire), pero entre equipos hay daño
        if (args.length < 3) {
            sender.sendMessage(Component.text("Uso: start ffa <kit>", NamedTextColor.YELLOW));
            return;
        }
        PvpKit kit = game.getKitManager().get(args[2]);
        if (kit == null) { sender.sendMessage(Component.text("✘ Kit no existe.", NamedTextColor.RED)); return; }

        // Mismo comportamiento que allteams (todos los equipos, friendly fire OFF entre equipos)
        // La diferencia conceptual es solo de "vibe" — admin lo usa como pvp casual
        List<Player> participants = new ArrayList<>();
        Map<UUID, String> teamMap = new HashMap<>();
        for (EventTeam team : plugin.getTeamManager().getTeams().values()) {
            for (Player p : team.getOnlinePlayers()) {
                participants.add(p);
                teamMap.put(p.getUniqueId(), team.getId());
            }
        }
        if (participants.size() < 2) {
            sender.sendMessage(Component.text("✘ No hay suficientes jugadores.", NamedTextColor.RED));
            return;
        }
        boolean started = game.getCombatManager().startCombat(
                CombatMode.FFA, participants, teamMap, kit, false);
        if (!started) sender.sendMessage(Component.text("✘ No se pudo iniciar el combate.", NamedTextColor.RED));
    }

    // ── stop ─────────────────────────────────────────────────────────────────

    private void handleStop(Player player) {
        if (!game.getCombatManager().isActive()) {
            player.sendMessage(Component.text("✘ No hay combate activo.", NamedTextColor.RED));
            return;
        }
        game.getCombatManager().stopCombat();
    }

    // ── status ───────────────────────────────────────────────────────────────

    private void handleStatus(Player player) {
        player.sendMessage(Component.text("─── PvP Final ───", NamedTextColor.GOLD));
        player.sendMessage(Component.text("  Estado: ", NamedTextColor.GRAY)
                .append(Component.text(game.getCombatManager().getState().name(),
                        NamedTextColor.YELLOW)));
        if (game.getCombatManager().isActive()) {
            var c = game.getCombatManager().getCurrentCombat();
            if (c != null) {
                player.sendMessage(Component.text("  Modo: ", NamedTextColor.GRAY)
                        .append(Component.text(c.getMode().getLabel(), NamedTextColor.WHITE)));
                player.sendMessage(Component.text("  Vivos: ", NamedTextColor.GRAY)
                        .append(Component.text(c.getAlive().size() + "/" + c.getParticipants().size(),
                                NamedTextColor.WHITE)));
                player.sendMessage(Component.text("  Friendly fire: ", NamedTextColor.GRAY)
                        .append(Component.text(c.isFriendlyFire() ? "Sí" : "No",
                                c.isFriendlyFire() ? NamedTextColor.RED : NamedTextColor.GREEN)));
                player.sendMessage(Component.text("  Tiempo: ", NamedTextColor.GRAY)
                        .append(Component.text(c.getElapsedSeconds() + "s", NamedTextColor.WHITE)));
            }
        }
        var arena = game.getArenaManager().getArena();
        player.sendMessage(Component.text("  Arena lista: ", NamedTextColor.GRAY)
                .append(Component.text(arena != null && arena.isReady() ? "✔" : "✘",
                        arena != null && arena.isReady() ? NamedTextColor.GREEN : NamedTextColor.RED)));
        player.sendMessage(Component.text("  Kits: ", NamedTextColor.GRAY)
                .append(Component.text(game.getKitManager().count(), NamedTextColor.WHITE)));
    }

    // ── Help & tab complete ──────────────────────────────────────────────────

    private void sendHelp(Player p) {
        p.sendMessage(Component.text("─── /em pvpfinal ───", NamedTextColor.GOLD));
        h(p, "arena create [nombre]",                "Crear arena");
        h(p, "arena setspawn <n>",                   "Spawn N en tu pos");
        h(p, "arena setlobby",                       "Lobby en tu pos");
        h(p, "arena delete",                         "Borrar arena");
        h(p, "arena info",                           "Ver info de arena");
        h(p, "kit create <name>",                    "Crear kit de tu inv");
        h(p, "kit delete <name>",                    "Borrar kit");
        h(p, "kit list",                             "Listar kits");
        h(p, "kit preview <name>",                   "Ver kit");
        h(p, "start 1v1 <p1> <p2> <kit>",            "Combate 1v1");
        h(p, "start teamvsteam <t1> <t2> <kit>",     "Combate de equipos");
        h(p, "start allteams <kit>",                 "Todos los equipos");
        h(p, "start ffa <kit>",                      "PvP casual");
        h(p, "stop",                                 "Terminar combate actual");
        h(p, "status",                               "Ver estado");
    }

    private void h(Player p, String cmd, String desc) {
        p.sendMessage(Component.text("  /em pvpfinal ", NamedTextColor.GRAY)
                .append(Component.text(cmd, NamedTextColor.YELLOW))
                .append(Component.text("  " + desc, NamedTextColor.GRAY)));
    }

    public List<String> tabComplete(String[] args) {
        if (args.length == 1)
            return filter(List.of("arena","kit","start","stop","status"), args[0]);
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "arena" -> filter(List.of("create","setspawn","setlobby","delete","info"), args[1]);
                case "kit"   -> filter(List.of("create","delete","list","preview"), args[1]);
                case "start" -> filter(List.of("1v1","teamvsteam","allteams","ffa"), args[1]);
                default      -> List.of();
            };
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("kit")
                && (args[1].equalsIgnoreCase("delete") || args[1].equalsIgnoreCase("preview"))) {
            return filter(new ArrayList<>(game.getKitManager().getNames()), args[2]);
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("start")) {
            String mode = args[1].toLowerCase();
            if (mode.equals("1v1")) {
                // args[2], args[3] = jugadores; args[4] = kit
                if (args.length == 3 || args.length == 4) {
                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                            .toList();
                }
                if (args.length == 5)
                    return filter(new ArrayList<>(game.getKitManager().getNames()), args[4]);
            }
            if (mode.equals("teamvsteam")) {
                if (args.length == 3 || args.length == 4) {
                    return plugin.getTeamManager().getTeams().keySet().stream()
                            .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                            .toList();
                }
                if (args.length == 5)
                    return filter(new ArrayList<>(game.getKitManager().getNames()), args[4]);
            }
            if ((mode.equals("allteams") || mode.equals("ffa")) && args.length == 3)
                return filter(new ArrayList<>(game.getKitManager().getNames()), args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase();
        return options.stream().filter(s -> s.toLowerCase().startsWith(p)).toList();
    }
}