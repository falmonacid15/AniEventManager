package org.falmdev.anieventmanager.minigames.bingo;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
import java.util.stream.Stream;

public class BingoCommand implements CommandExecutor, TabCompleter {

    private final Anieventmanager plugin;
    private final BingoMiniGame miniGame;

    // ── Listas para tab complete ───────────────────────────────────────────────

    // Mobs que se pueden matar (excluye mobs no-combat o irrelevantes)
    private static final List<String> KILLABLE_MOBS = List.of(
            "ZOMBIE", "SKELETON", "CREEPER", "SPIDER", "CAVE_SPIDER", "ENDERMAN",
            "WITCH", "BLAZE", "GHAST", "MAGMA_CUBE", "SLIME", "WITHER_SKELETON",
            "ZOMBIE_VILLAGER", "HUSK", "STRAY", "DROWNED", "PHANTOM", "PILLAGER",
            "VINDICATOR", "EVOKER", "RAVAGER", "VEX", "GUARDIAN", "ELDER_GUARDIAN",
            "SILVERFISH", "ENDERMITE", "SHULKER", "HOGLIN", "PIGLIN", "PIGLIN_BRUTE",
            "ZOMBIFIED_PIGLIN", "STRIDER", "ZOGLIN", "WARDEN", "BREEZE",
            "BOGGED", "CREAKING", "ENDER_DRAGON", "WITHER", "IRON_GOLEM"
    );

    // Materiales comunes para tradear (ítems que los aldeanos dan)
    private static final List<String> TRADE_RESULT_MATERIALS = List.of(
            "EMERALD", "DIAMOND", "IRON_INGOT", "GOLD_INGOT",
            "DIAMOND_SWORD", "DIAMOND_AXE", "DIAMOND_PICKAXE", "DIAMOND_SHOVEL",
            "DIAMOND_HELMET", "DIAMOND_CHESTPLATE", "DIAMOND_LEGGINGS", "DIAMOND_BOOTS",
            "ENCHANTED_BOOK", "NAME_TAG", "SADDLE",
            "IRON_SWORD", "IRON_AXE", "IRON_PICKAXE", "IRON_SHOVEL",
            "IRON_HELMET", "IRON_CHESTPLATE", "IRON_LEGGINGS", "IRON_BOOTS",
            "CHAINMAIL_HELMET", "CHAINMAIL_CHESTPLATE", "CHAINMAIL_LEGGINGS", "CHAINMAIL_BOOTS",
            "BELL", "LANTERN", "BOOKSHELF",
            "WHEAT", "BREAD", "COOKIE", "PUMPKIN_PIE", "APPLE", "GOLDEN_APPLE",
            "ARROW", "TIPPED_ARROW", "BOW", "CROSSBOW",
            "FISHING_ROD", "CLOCK", "COMPASS", "TELESCOPE",
            "POTION", "GLASS_BOTTLE", "GLOWSTONE_DUST", "REDSTONE",
            "LAPIS_LAZULI", "COAL", "PAPER", "BOOK",
            "OAK_LOG", "SPRUCE_LOG", "BIRCH_LOG", "JUNGLE_LOG", "ACACIA_LOG", "DARK_OAK_LOG",
            "GLASS", "SAND", "GRAVEL", "CLAY_BALL",
            "MAP", "FILLED_MAP", "GLOBE_BANNER_PATTERN"
    );

    // Estructuras disponibles
    private static final List<String> STRUCTURES = List.of(
            "minecraft:trial_chambers",
            "minecraft:stronghold",
            "minecraft:ancient_city",
            "minecraft:woodland_mansion",
            "minecraft:ocean_monument",
            "minecraft:nether_fortress",
            "minecraft:bastion_remnant",
            "minecraft:end_city",
            "minecraft:pillager_outpost",
            "minecraft:desert_pyramid",
            "minecraft:jungle_temple",
            "minecraft:igloo",
            "minecraft:village",
            "minecraft:ruined_portal",
            "minecraft:shipwreck",
            "minecraft:ocean_ruin_cold",
            "minecraft:ocean_ruin_warm",
            "minecraft:mineshaft",
            "minecraft:buried_treasure",
            "minecraft:swamp_hut"
    );

    // Materiales obtenibles / crafteables comunes
    private static final List<String> COMMON_MATERIALS = Stream.of(Material.values())
            .filter(m -> !m.isAir() && !m.name().startsWith("LEGACY_") && m.isItem())
            .map(Material::name)
            .sorted()
            .toList();

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
            case "wall" -> handleWall(player, Arrays.copyOfRange(args, 1, args.length));

            default -> sendAdminHelp(player);
        }
    }

    // ── /em bingo task ────────────────────────────────────────────────────────

    private void handleTask(Player player, String[] args) {
        if (args.length == 0) { sendTaskHelp(player); return; }

        switch (args[0].toLowerCase()) {

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
                    err(player, "Tipo inválido. Usa Tab para ver los tipos disponibles.");
                    return;
                }

                BingoTask task;
                try {
                    task = switch (type) {
                        case OBTAIN_ITEM, CRAFT_ITEM -> {
                            if (args.length < 5) throw new IllegalArgumentException(
                                    "Uso: /em bingo task add <id> " + type + " <MATERIAL> <cantidad>");
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
                            if (args.length < 5) throw new IllegalArgumentException(
                                    "Uso: /em bingo task add <id> KILL_MOB <ENTITY_TYPE> <cantidad>");
                            EntityType mob = EntityType.valueOf(args[3].toUpperCase());
                            int count      = Integer.parseInt(args[4]);
                            BingoTask t    = new BingoTask(id, type,
                                    "Matar " + count + " " + args[3].toLowerCase().replace('_', ' '));
                            t.setMobType(mob);
                            t.setMobCount(count);
                            yield t;
                        }
                        case EQUIP_ITEM, FISH_ITEM -> {
                            if (args.length < 4) throw new IllegalArgumentException(
                                    "Uso: /em bingo task add <id> " + type + " <MATERIAL>");
                            Material mat  = Material.valueOf(args[3].toUpperCase());
                            String prefix = type == BingoTask.Type.EQUIP_ITEM ? "Equipar " : "Pescar ";
                            BingoTask t   = new BingoTask(id, type,
                                    prefix + args[3].toLowerCase().replace('_', ' '));
                            t.setMaterial(mat);
                            yield t;
                        }
                        case REACH_LOCATION -> {
                            if (args.length < 8) throw new IllegalArgumentException(
                                    "Uso: /em bingo task add <id> REACH_LOCATION <nombre> <x> <y> <z> <radio>");
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
                        case VISIT_STRUCTURE -> {
                            if (args.length < 4) throw new IllegalArgumentException(
                                    "Uso: /em bingo task add <id> VISIT_STRUCTURE <structure_key> [nombre]");
                            String structKey = args[3].toLowerCase();
                            String name = args.length >= 5
                                    ? String.join(" ", Arrays.copyOfRange(args, 4, args.length))
                                    : prettyStructureName(structKey);
                            BingoTask t = new BingoTask(id, type, "Visitar " + name);
                            t.setStructureKey(structKey);
                            yield t;
                        }
                        case TRADE_ANY -> {
                            if (args.length < 4) throw new IllegalArgumentException(
                                    "Uso: /em bingo task add <id> TRADE_ANY <cantidad>");
                            int count   = Integer.parseInt(args[3]);
                            BingoTask t = new BingoTask(id, type,
                                    "Tradear " + count + " veces con aldeanos");
                            t.setAmount(count);
                            yield t;
                        }
                        case TRADE_ITEM -> {
                            if (args.length < 5) throw new IllegalArgumentException(
                                    "Uso: /em bingo task add <id> TRADE_ITEM <MATERIAL> <cantidad>");
                            Material mat = Material.valueOf(args[3].toUpperCase());
                            int count    = Integer.parseInt(args[4]);
                            BingoTask t  = new BingoTask(id, type,
                                    "Tradear x" + count + " " + args[3].toLowerCase().replace('_', ' '));
                            t.setMaterial(mat);
                            t.setAmount(count);
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
                player.sendMessage(Component.text("━━━ Tareas (" + tasks.size() + "/25) ━━━",
                        NamedTextColor.GOLD));
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

    // ── /em bingo wall ────────────────────────────────────────────────────────

    private void handleWall(Player player, String[] args) {
        if (args.length == 0) { sendWallHelp(player); return; }
        var wallManager = plugin.getBingoWallManager();

        switch (args[0].toLowerCase()) {

            case "add" -> {
                if (args.length < 2) { err(player, "Uso: /em bingo wall add <id>"); return; }
                wallManager.startSelection(player, args[1]);
            }

            case "remove" -> {
                if (args.length < 2) { err(player, "Uso: /em bingo wall remove <id>"); return; }
                if (!miniGame.getConfig().hasWall(args[1])) {
                    err(player, "No existe la pared '" + args[1] + "'."); return;
                }
                miniGame.getConfig().removeWall(args[1]);
                ok(player, "Pared '" + args[1] + "' eliminada.");
            }

            case "list" -> {
                var walls = miniGame.getConfig().loadWalls();
                if (walls.isEmpty()) {
                    player.sendMessage(Component.text("No hay paredes configuradas.", NamedTextColor.GRAY));
                    return;
                }
                player.sendMessage(Component.text("━━━ Paredes (" + walls.size() + ") ━━━",
                        NamedTextColor.GOLD));
                for (BingoWall w : walls) {
                    player.sendMessage(Component.text("  [" + w.getId() + "] ", NamedTextColor.YELLOW)
                            .append(Component.text(
                                    w.getWorld() + "  (" + w.getX1() + "," + w.getY1() + "," + w.getZ1()
                                            + ") → (" + w.getX2() + "," + w.getY2() + "," + w.getZ2() + ")",
                                    NamedTextColor.GRAY)));
                }
            }

            case "place" -> {
                if (args.length < 2) { err(player, "Uso: /em bingo wall place <id|all>"); return; }
                if (args[1].equalsIgnoreCase("all")) {
                    wallManager.placeAllWalls();
                    ok(player, "Todas las paredes colocadas.");
                } else {
                    BingoWall wall = miniGame.getConfig().loadWalls().stream()
                            .filter(w -> w.getId().equals(args[1])).findFirst().orElse(null);
                    if (wall == null) { err(player, "No existe la pared '" + args[1] + "'."); return; }
                    wallManager.placeWall(wall, org.bukkit.Material.BARRIER);
                    ok(player, "Pared '" + args[1] + "' colocada.");
                }
            }

            case "clear" -> {
                if (args.length < 2) { err(player, "Uso: /em bingo wall clear <id|all>"); return; }
                if (args[1].equalsIgnoreCase("all")) {
                    wallManager.clearAllWalls();
                    ok(player, "Todas las paredes quitadas.");
                } else {
                    BingoWall wall = miniGame.getConfig().loadWalls().stream()
                            .filter(w -> w.getId().equals(args[1])).findFirst().orElse(null);
                    if (wall == null) { err(player, "No existe la pared '" + args[1] + "'."); return; }
                    wallManager.placeWall(wall, org.bukkit.Material.AIR);
                    ok(player, "Pared '" + args[1] + "' quitada.");
                }
            }

            default -> sendWallHelp(player);
        }
    }

    // ── /bingo (jugadores) ────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo jugadores pueden usar este comando.");
            return true;
        }

        Optional<EventTeam> teamOpt = plugin.getTeamManager().getTeamOf(player);
        if (teamOpt.isEmpty()) {
            player.sendMessage(Component.text("No estás en ningún equipo.", NamedTextColor.RED));
            return true;
        }

        BingoCard card = miniGame.getCard(teamOpt.get());
        if (card != null) {
            BingoGUI.open(player, card, miniGame.getConfig());
            return true;
        }

        List<BingoTask> tasks = miniGame.getConfig().loadTasks();
        if (tasks.isEmpty()) {
            player.sendMessage(Component.text("No hay tareas configuradas aún.", NamedTextColor.GRAY));
            return true;
        }

        BingoCard preview = new BingoCard(teamOpt.get(), tasks);
        BingoGUI.open(player, preview, miniGame.getConfig());
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
                    "setduration", "setscore", "task", "lobby", "wall"), args[0]);

        // ── task ──────────────────────────────────────────────────────────────
        if (args[0].equalsIgnoreCase("task")) {
            if (args.length == 2)
                return filter(List.of("add", "edit", "remove", "list", "clear"), args[1]);

            // task edit/remove <id>
            if ((args[1].equalsIgnoreCase("edit") || args[1].equalsIgnoreCase("remove"))
                    && args.length == 3)
                return filter(taskIds(), args[2]);

            if (args[1].equalsIgnoreCase("add")) {
                // task add <id> <TYPE>
                if (args.length == 4)
                    return filter(List.of("OBTAIN_ITEM", "CRAFT_ITEM", "KILL_MOB",
                            "REACH_LOCATION", "EQUIP_ITEM", "FISH_ITEM",
                            "VISIT_STRUCTURE", "TRADE_ANY", "TRADE_ITEM"), args[3]);

                String type = args.length >= 4 ? args[3].toUpperCase() : "";

                // task add <id> OBTAIN_ITEM|CRAFT_ITEM|TRADE_ITEM <MATERIAL>
                if ((type.equals("OBTAIN_ITEM") || type.equals("CRAFT_ITEM")
                        || type.equals("TRADE_ITEM")) && args.length == 5)
                    return filter(COMMON_MATERIALS, args[4]);

                // task add <id> EQUIP_ITEM <MATERIAL> (armaduras)
                if (type.equals("EQUIP_ITEM") && args.length == 5)
                    return filter(EQUIP_MATERIALS, args[4]);

                // task add <id> FISH_ITEM <MATERIAL> (peces y loot de pesca)
                if (type.equals("FISH_ITEM") && args.length == 5)
                    return filter(FISH_MATERIALS, args[4]);

                // task add <id> KILL_MOB <MOB>
                if (type.equals("KILL_MOB") && args.length == 5)
                    return filter(KILLABLE_MOBS, args[4]);

                // task add <id> VISIT_STRUCTURE <structure_key>
                if (type.equals("VISIT_STRUCTURE") && args.length == 5)
                    return filter(STRUCTURES, args[4]);

                // task add <id> TRADE_ANY <cantidad>
                if (type.equals("TRADE_ANY") && args.length == 5)
                    return filter(List.of("1", "3", "5", "10", "20"), args[4]);

                // task add <id> TRADE_ITEM <MATERIAL> <cantidad>
                if (type.equals("TRADE_ITEM") && args.length == 6)
                    return filter(List.of("1", "2", "3", "5", "10"), args[5]);

                // task add <id> OBTAIN_ITEM|CRAFT_ITEM <MATERIAL> <cantidad>
                if ((type.equals("OBTAIN_ITEM") || type.equals("CRAFT_ITEM")) && args.length == 6)
                    return filter(List.of("1", "2", "4", "8", "16", "32", "64"), args[5]);

                // task add <id> KILL_MOB <MOB> <cantidad>
                if (type.equals("KILL_MOB") && args.length == 6)
                    return filter(List.of("1", "2", "5", "10", "20"), args[5]);
            }
        }

        // ── wall ──────────────────────────────────────────────────────────────
        if (args[0].equalsIgnoreCase("wall")) {
            if (args.length == 2)
                return filter(List.of("add", "remove", "list", "place", "clear"), args[1]);
            if ((args[1].equalsIgnoreCase("remove")
                    || args[1].equalsIgnoreCase("place")
                    || args[1].equalsIgnoreCase("clear")) && args.length == 3) {
                List<String> ids = new ArrayList<>(miniGame.getConfig().loadWalls()
                        .stream().map(BingoWall::getId).toList());
                if (!args[1].equalsIgnoreCase("remove")) ids.add("all");
                return filter(ids, args[2]);
            }
        }

        // ── setscore ──────────────────────────────────────────────────────────
        if (args[0].equalsIgnoreCase("setscore") && args.length == 2)
            return filter(List.of("1", "2", "3", "4"), args[1]);

        return List.of();
    }

    // ── Listas de materiales específicas ──────────────────────────────────────

    private static final List<String> EQUIP_MATERIALS = List.of(
            "LEATHER_HELMET", "LEATHER_CHESTPLATE", "LEATHER_LEGGINGS", "LEATHER_BOOTS",
            "IRON_HELMET", "IRON_CHESTPLATE", "IRON_LEGGINGS", "IRON_BOOTS",
            "GOLDEN_HELMET", "GOLDEN_CHESTPLATE", "GOLDEN_LEGGINGS", "GOLDEN_BOOTS",
            "CHAINMAIL_HELMET", "CHAINMAIL_CHESTPLATE", "CHAINMAIL_LEGGINGS", "CHAINMAIL_BOOTS",
            "DIAMOND_HELMET", "DIAMOND_CHESTPLATE", "DIAMOND_LEGGINGS", "DIAMOND_BOOTS",
            "NETHERITE_HELMET", "NETHERITE_CHESTPLATE", "NETHERITE_LEGGINGS", "NETHERITE_BOOTS",
            "TURTLE_HELMET", "ELYTRA"
    );

    private static final List<String> FISH_MATERIALS = List.of(
            "COD", "SALMON", "TROPICAL_FISH", "PUFFERFISH",
            "BOWL", "STICK", "STRING", "BONE",
            "SADDLE", "NAME_TAG", "LILY_PAD",
            "NAUTILUS_SHELL", "INK_SAC", "TRIPWIRE_HOOK",
            "FISHING_ROD", "ENCHANTED_BOOK", "LEATHER", "LEATHER_BOOTS",
            "ROTTEN_FLESH", "WATER_BUCKET"
    );

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
        help(player, "/em bingo wall ...",               "Gestionar paredes de barriers");
    }

    private void sendTaskHelp(Player player) {
        player.sendMessage(Component.text("━━━ Tipos de tarea ━━━", NamedTextColor.GOLD));
        help(player, "/em bingo task add <id> OBTAIN_ITEM <MATERIAL> <cantidad>",            "Obtener ítem");
        help(player, "/em bingo task add <id> CRAFT_ITEM <MATERIAL> <cantidad>",             "Craftear ítem");
        help(player, "/em bingo task add <id> KILL_MOB <ENTITY_TYPE> <cantidad>",            "Matar mob");
        help(player, "/em bingo task add <id> EQUIP_ITEM <MATERIAL>",                        "Equipar ítem");
        help(player, "/em bingo task add <id> FISH_ITEM <MATERIAL>",                         "Pescar ítem");
        help(player, "/em bingo task add <id> REACH_LOCATION <nombre> <x> <y> <z> <radio>", "Llegar a lugar");
        help(player, "/em bingo task add <id> VISIT_STRUCTURE <structure_key> [nombre]",     "Visitar estructura");
        help(player, "/em bingo task add <id> TRADE_ANY <cantidad>",                         "Tradear con aldeanos");
        help(player, "/em bingo task add <id> TRADE_ITEM <MATERIAL> <cantidad>",             "Tradear ítem específico");
    }

    private void sendWallHelp(Player player) {
        player.sendMessage(Component.text("━━━ /em bingo wall ━━━", NamedTextColor.GOLD));
        help(player, "/em bingo wall add <id>",       "Marcar esquinas con click derecho");
        help(player, "/em bingo wall remove <id>",    "Eliminar configuración de pared");
        help(player, "/em bingo wall list",           "Listar paredes");
        help(player, "/em bingo wall place <id|all>", "Colocar barriers");
        help(player, "/em bingo wall clear <id|all>", "Quitar barriers (pone air)");
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private List<String> taskIds() {
        return new ArrayList<>(miniGame.getConfig().loadTasks().stream()
                .map(BingoTask::getId).toList());
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

    private List<String> filter(List<String> options, String input) {
        String lower = input.toLowerCase();
        return options.stream().filter(s -> s.toLowerCase().startsWith(lower)).toList();
    }

    private String prettyStructureName(String key) {
        String part = key.contains(":") ? key.split(":")[1] : key;
        return part.replace('_', ' ');
    }
}