package org.falmdev.anieventmanager.minigames.bingo;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.model.EventTeam;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Comandos del Bingo.
 *
 * Admin (/em bingo ...):
 *   /em bingo start                          → Inicia la partida
 *   /em bingo stop                           → Detiene la partida
 *   /em bingo settings                       → Muestra configuración
 *   /em bingo setduration <minutos>          → Duración de la partida
 *   /em bingo setscore <lugar> <pts>         → Puntaje por lugar
 *   /em bingo task add <id> <tipo> [args]    → Agrega una tarea
 *   /em bingo task remove <id>               → Elimina una tarea
 *   /em bingo task list                      → Lista las tareas
 *   /em bingo task clear                     → Elimina todas las tareas
 *
 * Jugadores (/bingo):
 *   Abre el GUI de la tarjeta del equipo
 *
 * Ejemplos de /em bingo task add:
 *   /em bingo task add tarea-1 OBTAIN_ITEM DIAMOND 5
 *   /em bingo task add tarea-2 KILL_MOB ZOMBIE 10
 *   /em bingo task add tarea-3 CRAFT_ITEM IRON_SWORD 1
 *   /em bingo task add tarea-4 EQUIP_ITEM IRON_HELMET
 *   /em bingo task add tarea-5 FISH_ITEM COD
 *   /em bingo task add tarea-6 REACH_LOCATION "La Cueva" 100 30 -50 5
 */
public class BingoCommand implements CommandExecutor, TabCompleter {

    private final Anieventmanager plugin;
    private final BingoMiniGame miniGame;

    public BingoCommand(Anieventmanager plugin, BingoMiniGame miniGame) {
        this.plugin   = plugin;
        this.miniGame = miniGame;
    }

    // ── /em bingo (admin) ─────────────────────────────────────────────────────

    public void handleAdmin(Player player, String[] args) {
        if (args.length == 0) { sendAdminHelp(player); return; }

        switch (args[0].toLowerCase()) {

            case "setspawn" -> {
                plugin.getBingoMiniGame().getConfig().setSpawn(player.getLocation());
                ok(player, "Spawn del Bingo seteado en tu posición.");
            }

            case "setcountdown" -> {
                if (args.length < 2) { err(player, "Uso: /em bingo setcountdown <segundos>"); return; }
                try {
                    int secs = Integer.parseInt(args[1]);
                    if (secs < 1) { err(player, "El countdown debe ser al menos 1 segundo."); return; }
                    plugin.getBingoMiniGame().getConfig().setCountdownSeconds(secs);
                    ok(player, "Countdown seteado a " + secs + " segundos.");
                } catch (NumberFormatException e) {
                    err(player, "'" + args[1] + "' no es un número válido.");
                }
            }

            case "lobby" -> {
                boolean ok = plugin.getBingoMiniGame().sendToSpawn();
                if (ok) ok(player, "Todos los jugadores fueron movidos al spawn del Bingo.");
                else    err(player, "El spawn del Bingo no está configurado. Usa /em bingo setspawn.");
            }

            case "start" -> {
                if (miniGame.getState() != BingoMiniGame.State.IDLE) {
                    err(player, "Ya hay una partida en curso."); return;
                }
                String error = miniGame.getConfig().validate();
                if (error != null) { err(player, error); return; }
                boolean ok = miniGame.start();
                if (!ok) err(player, "No se pudo iniciar. Verifica que haya equipos creados.");
            }

            case "stop" -> {
                if (miniGame.getState() == BingoMiniGame.State.IDLE) {
                    err(player, "No hay ninguna partida en curso."); return;
                }
                miniGame.forceStop();
                ok(player, "Partida detenida.");
            }

            case "settings" -> {
                var cfg = miniGame.getConfig();
                player.sendMessage(Component.text("━━━ Bingo — Configuración ━━━", NamedTextColor.GOLD));
                info(player, "Duración",      cfg.getDurationMinutes() + " minutos");
                info(player, "Countdown",     cfg.getCountdownSeconds() + " segundos");
                info(player, "Spawn",         cfg.getSpawn() != null ? locStr(cfg.getSpawn()) : "no configurado");
                info(player, "Tareas",        cfg.getTaskCount() + " tareas");
                info(player, "Puntaje 1°",    String.valueOf(cfg.getScoreForPlace(1)));
                info(player, "Puntaje 2°",    String.valueOf(cfg.getScoreForPlace(2)));
                info(player, "Puntaje 3°",    String.valueOf(cfg.getScoreForPlace(3)));
                info(player, "Estado",        miniGame.getState().name());
                if (miniGame.isRunning())
                    info(player, "Tiempo restante", miniGame.getTimeLeftFormatted());
                String validation = cfg.validate();
                if (validation != null) player.sendMessage(Component.text("⚠ " + validation, NamedTextColor.RED));
                else player.sendMessage(Component.text("✔ Listo para iniciar.", NamedTextColor.GREEN));
            }

            case "setduration" -> {
                if (args.length < 2) { err(player, "Uso: /em bingo setduration <minutos>"); return; }
                try {
                    int mins = Integer.parseInt(args[1]);
                    if (mins < 1) { err(player, "La duración debe ser al menos 1 minuto."); return; }
                    miniGame.getConfig().setDurationMinutes(mins);
                    ok(player, "Duración seteada a " + mins + " minutos.");
                } catch (NumberFormatException e) {
                    err(player, "'" + args[1] + "' no es un número válido.");
                }
            }

            case "setscore" -> {
                if (args.length < 3) { err(player, "Uso: /em bingo setscore <lugar> <pts>"); return; }
                try {
                    int place  = Integer.parseInt(args[1]);
                    int points = Integer.parseInt(args[2]);
                    miniGame.getConfig().setScoreForPlace(place, points);
                    ok(player, "Lugar #" + place + " ahora otorga " + points + " pts.");
                } catch (NumberFormatException e) {
                    err(player, "Los valores deben ser números enteros.");
                }
            }

            case "task" -> handleTask(player, Arrays.copyOfRange(args, 1, args.length));

            default -> sendAdminHelp(player);
        }
    }

    // ── /em bingo task ────────────────────────────────────────────────────────

    private void handleTask(Player player, String[] args) {
        if (args.length == 0) { sendTaskHelp(player); return; }

        switch (args[0].toLowerCase()) {

            // /em bingo task add <id> <tipo> [args específicos del tipo]
            case "edit" -> {
                if (args.length < 2) { err(player, "Uso: /em bingo task edit <id>"); return; }
                String taskId = args[1];
                if (!miniGame.getConfig().hasTask(taskId)) {
                    err(player, "No existe una tarea con id '" + taskId + "'."); return;
                }
                BingoTask task = miniGame.getConfig().loadTasks().stream()
                        .filter(t -> t.getId().equals(taskId))
                        .findFirst().orElse(null);
                if (task == null) { err(player, "Error al cargar la tarea."); return; }
                plugin.getBingoEditGUI().open(player, task);
            }

            case "add" -> {
                if (args.length < 3) { sendTaskHelp(player); return; }
                String id      = args[1];
                String typeStr = args[2].toUpperCase();

                if (miniGame.getConfig().hasTask(id)) {
                    err(player, "Ya existe una tarea con id '" + id + "'."); return;
                }

                BingoTask.Type type;
                try {
                    type = BingoTask.Type.valueOf(typeStr);
                } catch (IllegalArgumentException e) {
                    err(player, "Tipo inválido. Tipos válidos: OBTAIN_ITEM, CRAFT_ITEM, KILL_MOB, REACH_LOCATION, EQUIP_ITEM, FISH_ITEM");
                    return;
                }

                BingoTask task;
                try {
                    task = switch (type) {
                        case OBTAIN_ITEM, CRAFT_ITEM -> {
                            // /em bingo task add <id> OBTAIN_ITEM <MATERIAL> <cantidad>
                            if (args.length < 5) throw new IllegalArgumentException("Uso: /em bingo task add <id> " + type + " <MATERIAL> <cantidad>");
                            Material mat = Material.valueOf(args[3].toUpperCase());
                            int amount   = Integer.parseInt(args[4]);
                            BingoTask t  = new BingoTask(id, type,
                                    (type == BingoTask.Type.OBTAIN_ITEM ? "Obtener x" : "Craftear x")
                                            + amount + " " + args[3].toLowerCase().replace('_', ' '));
                            t.setMaterial(mat);
                            t.setAmount(amount);
                            yield t;
                        }
                        case KILL_MOB -> {
                            // /em bingo task add <id> KILL_MOB <ENTITY_TYPE> <cantidad>
                            if (args.length < 5) throw new IllegalArgumentException("Uso: /em bingo task add <id> KILL_MOB <ENTITY_TYPE> <cantidad>");
                            EntityType mob = EntityType.valueOf(args[3].toUpperCase());
                            int count      = Integer.parseInt(args[4]);
                            BingoTask t    = new BingoTask(id, type,
                                    "Matar " + count + " " + args[3].toLowerCase().replace('_', ' '));
                            t.setMobType(mob);
                            t.setMobCount(count);
                            yield t;
                        }
                        case EQUIP_ITEM, FISH_ITEM -> {
                            // /em bingo task add <id> EQUIP_ITEM <MATERIAL>
                            if (args.length < 4) throw new IllegalArgumentException("Uso: /em bingo task add <id> " + type + " <MATERIAL>");
                            Material mat = Material.valueOf(args[3].toUpperCase());
                            String prefix = type == BingoTask.Type.EQUIP_ITEM ? "Equipar " : "Pescar ";
                            BingoTask t   = new BingoTask(id, type,
                                    prefix + args[3].toLowerCase().replace('_', ' '));
                            t.setMaterial(mat);
                            yield t;
                        }
                        case REACH_LOCATION -> {
                            // /em bingo task add <id> REACH_LOCATION <nombre> <x> <y> <z> <radio>
                            if (args.length < 8) throw new IllegalArgumentException("Uso: /em bingo task add <id> REACH_LOCATION <nombre> <x> <y> <z> <radio>");
                            BingoTask t = new BingoTask(id, type, args[3]);
                            t.setLocation(
                                    player.getWorld().getName(),
                                    Double.parseDouble(args[4]),
                                    Double.parseDouble(args[5]),
                                    Double.parseDouble(args[6]),
                                    Double.parseDouble(args[7])
                            );
                            yield t;
                        }
                    };
                } catch (IllegalArgumentException e) {
                    err(player, e.getMessage()); return;
                }

                miniGame.getConfig().saveTask(task);
                ok(player, "Tarea '" + id + "' agregada. ("
                        + miniGame.getConfig().getTaskCount() + "/25)");
            }

            case "remove" -> {
                if (args.length < 2) { err(player, "Uso: /em bingo task remove <id>"); return; }
                if (!miniGame.getConfig().hasTask(args[1])) {
                    err(player, "No existe una tarea con id '" + args[1] + "'."); return;
                }
                miniGame.getConfig().removeTask(args[1]);
                ok(player, "Tarea '" + args[1] + "' eliminada.");
            }

            case "list" -> {
                var tasks = miniGame.getConfig().loadTasks();
                if (tasks.isEmpty()) {
                    player.sendMessage(Component.text("No hay tareas configuradas.", NamedTextColor.GRAY));
                    return;
                }
                player.sendMessage(Component.text("━━━ Tareas (" + tasks.size()
                        + "/25) ━━━", NamedTextColor.GOLD));
                for (int i = 0; i < tasks.size(); i++) {
                    BingoTask t = tasks.get(i);
                    player.sendMessage(Component.text("  " + (i + 1) + ". ", NamedTextColor.GRAY)
                            .append(Component.text("[" + t.getId() + "] ", NamedTextColor.DARK_GRAY))
                            .append(Component.text(t.getShortDescription(), NamedTextColor.WHITE)));
                }
            }

            case "clear" -> {
                miniGame.getConfig().clearTasks();
                ok(player, "Todas las tareas eliminadas.");
            }

            default -> sendTaskHelp(player);
        }
    }

    // ── /bingo (jugadores) ────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo jugadores pueden usar este comando.");
            return true;
        }

        if (!miniGame.isRunning()) {
            player.sendMessage(Component.text("No hay ninguna partida de Bingo en curso.", NamedTextColor.RED));
            return true;
        }

        Optional<EventTeam> teamOpt = plugin.getTeamManager().getTeamOf(player);
        if (teamOpt.isEmpty()) {
            player.sendMessage(Component.text("No estás en ningún equipo.", NamedTextColor.RED));
            return true;
        }

        BingoCard card = miniGame.getCard(teamOpt.get());
        if (card == null) {
            player.sendMessage(Component.text("No tienes una tarjeta de bingo asignada.", NamedTextColor.RED));
            return true;
        }

        BingoGUI.open(player, card, miniGame.getConfig());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }

    // ── Tab completion para /em bingo ─────────────────────────────────────────

    public List<String> tabComplete(String[] args) {
        if (args.length == 1)
            return filter(List.of("start", "stop", "settings", "setspawn", "setcountdown",
                    "setduration", "setscore", "task", "lobby"), args[0]);
        if (args[0].equalsIgnoreCase("task")) {
            if (args.length == 2) return filter(List.of("add", "edit", "remove", "list", "clear"), args[1]);
            if ((args[1].equalsIgnoreCase("edit") || args[1].equalsIgnoreCase("remove")) && args.length == 3)
                return filter(new ArrayList<>(miniGame.getConfig().loadTasks().stream()
                        .map(BingoTask::getId).toList()), args[2]);
            if (args[1].equalsIgnoreCase("add") && args.length == 4)
                return filter(List.of("OBTAIN_ITEM", "CRAFT_ITEM", "KILL_MOB",
                        "REACH_LOCATION", "EQUIP_ITEM", "FISH_ITEM"), args[3]);
        }
        if (args[0].equalsIgnoreCase("setscore") && args.length == 2)
            return filter(List.of("1", "2", "3", "4"), args[1]);
        return List.of();
    }

    // ── Ayuda ─────────────────────────────────────────────────────────────────

    private void sendAdminHelp(Player player) {
        player.sendMessage(Component.text("━━━ /em bingo ━━━", NamedTextColor.GOLD));
        help(player, "/em bingo start",                  "Inicia la partida");
        help(player, "/em bingo stop",                   "Detiene la partida");
        help(player, "/em bingo lobby",                  "Mover jugadores al spawn");
        help(player, "/em bingo settings",               "Ver configuración");
        help(player, "/em bingo setspawn",               "Setea spawn en tu posición");
        help(player, "/em bingo setcountdown <segs>",    "Segundos de cuenta regresiva");
        help(player, "/em bingo setduration <mins>",     "Duración de la partida");
        help(player, "/em bingo setscore <n> <pts>",     "Puntaje por posición");
        help(player, "/em bingo task add <id> <tipo>",   "Agregar tarea");
        help(player, "/em bingo task edit <id>",         "Editar tarea (GUI)");
        help(player, "/em bingo task remove <id>",       "Eliminar tarea");
        help(player, "/em bingo task list",              "Listar tareas");
        help(player, "/em bingo task clear",             "Limpiar todas las tareas");
    }

    private void sendTaskHelp(Player player) {
        player.sendMessage(Component.text("━━━ Tipos de tarea ━━━", NamedTextColor.GOLD));
        help(player, "/em bingo task add <id> OBTAIN_ITEM <MATERIAL> <cantidad>",  "Obtener ítem");
        help(player, "/em bingo task add <id> CRAFT_ITEM <MATERIAL> <cantidad>",   "Craftear ítem");
        help(player, "/em bingo task add <id> KILL_MOB <ENTITY_TYPE> <cantidad>",  "Matar mob");
        help(player, "/em bingo task add <id> EQUIP_ITEM <MATERIAL>",              "Equipar ítem");
        help(player, "/em bingo task add <id> FISH_ITEM <MATERIAL>",               "Pescar ítem");
        help(player, "/em bingo task add <id> REACH_LOCATION <nombre> <x> <y> <z> <radio>", "Llegar a lugar");
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