package org.falmdev.anieventmanager.minigames.battleroyale;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.minigames.battleroyale.death.RespawnManager;
import org.falmdev.anieventmanager.minigames.battleroyale.zone.ZoneManager;
import org.falmdev.anieventmanager.minigames.battleroyale.zone.ZonePhase;

import java.util.List;

public class BattleRoyaleCommand {

    private final Anieventmanager      plugin;
    private final BattleRoyaleMiniGame game;

    public BattleRoyaleCommand(Anieventmanager plugin, BattleRoyaleMiniGame game) {
        this.plugin = plugin;
        this.game   = game;
    }

    public void handle(Player player, String[] args) {
        if (args.length == 0) { sendHelp(player); return; }

        switch (args[0].toLowerCase()) {

            case "start" -> {
                if (game.isRunning()) { player.sendMessage(Component.text("✘ Ya está en curso.", NamedTextColor.RED)); return; }
                if (!game.start()) player.sendMessage(Component.text(
                        "✘ No se pudo iniciar. /em battleroyale status", NamedTextColor.RED));
            }

            case "stop" -> {
                game.stop();
                player.sendMessage(Component.text("✔ Battle Royale detenido.", NamedTextColor.YELLOW));
            }

            case "status" -> {
                player.sendMessage(Component.text("─── Battle Royale ───", NamedTextColor.GOLD));
                player.sendMessage(Component.text("  Estado:    ", NamedTextColor.GRAY)
                        .append(Component.text(game.getState().name(), NamedTextColor.YELLOW)));
                player.sendMessage(Component.text("  Jugadores: ", NamedTextColor.GRAY)
                        .append(Component.text(String.valueOf(game.getAllPlayers().size()), NamedTextColor.WHITE)));
                player.sendMessage(Component.text("  Vivos:     ", NamedTextColor.GRAY)
                        .append(Component.text(String.valueOf(game.getAlivePlayers()), NamedTextColor.GREEN)));
                String err = game.getConfig().validate();
                player.sendMessage(err != null
                        ? Component.text("  ⚠ Config: " + err, NamedTextColor.RED)
                        : Component.text("  ✔ Configuración completa.", NamedTextColor.GREEN));
            }

            case "setlobby" -> {
                game.getConfig().setLobbySpawn(player.getLocation());
                player.sendMessage(Component.text("✔ Lobby spawn seteado.", NamedTextColor.GREEN));
            }

            case "setcenter" -> {
                game.getConfig().setArenaCenter(player.getLocation());
                player.sendMessage(Component.text("✔ Centro de arena seteado.", NamedTextColor.GREEN));
            }

            case "arena" -> handleArena(player, args);
            case "drop"  -> handleDrop(player, args);
            case "zone"  -> handleZone(player, args);
            case "loot"  -> handleLoot(player, args);
            case "money"   -> handleMoney(player, args);
            case "points"  -> handlePoints(player, args);
            case "respawn" -> handleRespawn(player, args);
            case "revive"  -> handleRevive(player, args);
            case "revivemate" -> handleReviveMate(player, args);

            default -> sendHelp(player);
        }
    }

    private void handleArena(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                    "Uso: /em battleroyale arena [pos1|pos2|setymin <n>|setymax <n>|info]",
                    NamedTextColor.YELLOW));
            return;
        }

        switch (args[1].toLowerCase()) {
            case "pos1" -> {
                game.getConfig().setArenaPos1(player.getLocation());
                player.sendMessage(Component.text("✔ Pos1 seteada en X=" +
                        Math.round(player.getLocation().getX()) + " Z=" +
                        Math.round(player.getLocation().getZ()), NamedTextColor.GREEN));
            }
            case "pos2" -> {
                game.getConfig().setArenaPos2(player.getLocation());
                player.sendMessage(Component.text("✔ Pos2 seteada en X=" +
                        Math.round(player.getLocation().getX()) + " Z=" +
                        Math.round(player.getLocation().getZ()), NamedTextColor.GREEN));
            }
            case "setymin" -> {
                if (args.length < 3) { player.sendMessage(Component.text("Uso: setymin <n>", NamedTextColor.YELLOW)); return; }
                try {
                    game.getConfig().setYMin(Integer.parseInt(args[2]));
                    player.sendMessage(Component.text("✔ Y mínima: " + args[2], NamedTextColor.GREEN));
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("✘ Número inválido.", NamedTextColor.RED));
                }
            }
            case "setymax" -> {
                if (args.length < 3) { player.sendMessage(Component.text("Uso: setymax <n>", NamedTextColor.YELLOW)); return; }
                try {
                    game.getConfig().setYMax(Integer.parseInt(args[2]));
                    player.sendMessage(Component.text("✔ Y máxima: " + args[2], NamedTextColor.GREEN));
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("✘ Número inválido.", NamedTextColor.RED));
                }
            }
            case "info" -> {
                var cfg = game.getConfig();
                var p1 = cfg.getArenaPos1();
                var p2 = cfg.getArenaPos2();
                player.sendMessage(Component.text("─── Arena ───", NamedTextColor.GOLD));
                player.sendMessage(Component.text("  Mundo: ",  NamedTextColor.GRAY)
                        .append(Component.text(cfg.getArenaWorld(), NamedTextColor.WHITE)));
                player.sendMessage(Component.text("  Pos1:  ", NamedTextColor.GRAY)
                        .append(Component.text(p1 == null ? "no seteada" :
                                        String.format("(%.0f, %.0f)", p1.getX(), p1.getZ()),
                                p1 == null ? NamedTextColor.RED : NamedTextColor.WHITE)));
                player.sendMessage(Component.text("  Pos2:  ", NamedTextColor.GRAY)
                        .append(Component.text(p2 == null ? "no seteada" :
                                        String.format("(%.0f, %.0f)", p2.getX(), p2.getZ()),
                                p2 == null ? NamedTextColor.RED : NamedTextColor.WHITE)));
                player.sendMessage(Component.text("  Y:     ", NamedTextColor.GRAY)
                        .append(Component.text(cfg.getYMin() + " a " + cfg.getYMax(), NamedTextColor.WHITE)));
            }
            default -> player.sendMessage(Component.text(
                    "Uso: /em battleroyale arena [pos1|pos2|setymin|setymax|info]",
                    NamedTextColor.YELLOW));
        }
    }

    private void handleDrop(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                    "Uso: /em battleroyale drop [setstart|setend|setheight|setspeed|setdir|setparachute]",
                    NamedTextColor.YELLOW));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "setstart" -> {
                game.getConfig().setDropStart(player.getLocation());
                player.sendMessage(Component.text("✔ Inicio del drop seteado.", NamedTextColor.GREEN));
            }
            case "setend" -> {
                game.getConfig().setDropEnd(player.getLocation());
                player.sendMessage(Component.text("✔ Final del drop seteado.", NamedTextColor.GREEN));
            }
            case "setheight" -> setDouble(player, args, "setheight <n>",
                    v -> { game.getConfig().setDropHeight(v); return "Altura: " + v; });
            case "setspeed" -> setDouble(player, args, "setspeed <n>",
                    v -> { game.getConfig().setDropSpeed(v); return "Velocidad: " + v + " bl/tick"; });
            case "setdir" -> {
                if (args.length < 4) { player.sendMessage(Component.text("Uso: setdir <x> <z>", NamedTextColor.YELLOW)); return; }
                try {
                    game.getConfig().setDropDirection(Double.parseDouble(args[2]), Double.parseDouble(args[3]));
                    player.sendMessage(Component.text("✔ Dirección: (" + args[2] + ", " + args[3] + ")", NamedTextColor.GREEN));
                } catch (NumberFormatException e) { player.sendMessage(Component.text("✘ Inválido.", NamedTextColor.RED)); }
            }
            case "setparachute" -> {
                if (args.length < 4) { player.sendMessage(Component.text("Uso: setparachute <amp> <ticks>", NamedTextColor.YELLOW)); return; }
                try {
                    game.getConfig().setParachuteSFAmplifier(Integer.parseInt(args[2]));
                    game.getConfig().setParachuteTicks(Integer.parseInt(args[3]));
                    player.sendMessage(Component.text("✔ Paracaídas: amp=" + args[2] + " ticks=" + args[3], NamedTextColor.GREEN));
                } catch (NumberFormatException e) { player.sendMessage(Component.text("✘ Inválido.", NamedTextColor.RED)); }
            }
            default -> player.sendMessage(Component.text(
                    "Uso: /em battleroyale drop [setstart|setend|setheight|setspeed|setdir|setparachute]",
                    NamedTextColor.YELLOW));
        }
    }

    private void handleZone(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                    "Uso: /em battleroyale zone [info|forceshrink|skipto <n>|stop|teststart]",
                    NamedTextColor.YELLOW));
            return;
        }

        ZoneManager zm = game.getZoneManager();

        switch (args[1].toLowerCase()) {
            case "info" -> {
                player.sendMessage(Component.text("─── Zona ───", NamedTextColor.GOLD));
                var zCenter = game.getConfig().getZoneCenter();
                double maxR = game.getConfig().getZoneMaxRadius();
                player.sendMessage(Component.text("  Centro:   ", NamedTextColor.GRAY)
                        .append(Component.text(zCenter == null ? "sin arena"
                                        : String.format("(%.0f, %.0f)", zCenter.getX(), zCenter.getZ()),
                                zCenter == null ? NamedTextColor.RED : NamedTextColor.WHITE))
                        .append(Component.text(" [auto]", NamedTextColor.DARK_GRAY)));
                player.sendMessage(Component.text("  Radio máx:", NamedTextColor.GRAY)
                        .append(Component.text(String.format(" %.1f bloques [auto]", maxR), NamedTextColor.WHITE)));
                player.sendMessage(Component.text("  Estado:   ", NamedTextColor.GRAY)
                        .append(Component.text(zm.getStateLabel(), NamedTextColor.YELLOW)));
                if (zm.isRunning()) {
                    player.sendMessage(Component.text("  Fase:     ", NamedTextColor.GRAY)
                            .append(Component.text(zm.getCurrentPhase() + "/" + zm.getTotalPhases(),
                                    NamedTextColor.WHITE)));
                    player.sendMessage(Component.text("  Radio:    ", NamedTextColor.GRAY)
                            .append(Component.text(String.format("%.1f bloques", zm.getCurrentRadius()),
                                    NamedTextColor.WHITE)));
                    player.sendMessage(Component.text("  Restante: ", NamedTextColor.GRAY)
                            .append(Component.text(zm.getSecondsLeft() + "s", NamedTextColor.WHITE)));
                    ZonePhase ph = zm.getCurrentPhaseData();
                    if (ph != null) {
                        player.sendMessage(Component.text("  Daño:     ", NamedTextColor.GRAY)
                                .append(Component.text(ph.damagePerSecond() + " HP/s", NamedTextColor.RED)));
                    }
                }
                player.sendMessage(Component.text("  ─── Fases configuradas ───", NamedTextColor.DARK_GRAY));
                List<BattleRoyaleConfig.ZonePhaseData> phases = game.getConfig().getZonePhases();
                for (int i = 0; i < phases.size(); i++) {
                    var p = phases.get(i);
                    boolean current = zm.isRunning() && i == zm.getCurrentPhase() - 1;
                    player.sendMessage(Component.text(
                            "  " + (current ? "→ " : "  ") + "#" + (i + 1) +
                                    " r=" + (int) p.radius() +
                                    " wait=" + p.waitSeconds() + "s" +
                                    " shrink=" + p.shrinkSeconds() + "s" +
                                    " dmg=" + p.damagePerSecond() + "/s",
                            current ? NamedTextColor.GREEN : NamedTextColor.GRAY));
                }
            }

            case "forceshrink" -> {
                if (!zm.isRunning()) {
                    player.sendMessage(Component.text("✘ La zona no está activa.", NamedTextColor.RED));
                    return;
                }
                zm.forceShrink();
                player.sendMessage(Component.text("✔ Compresión forzada.", NamedTextColor.GREEN));
            }

            case "skipto" -> {
                if (!zm.isRunning()) {
                    player.sendMessage(Component.text("✘ La zona no está activa.", NamedTextColor.RED));
                    return;
                }
                if (args.length < 3) {
                    player.sendMessage(Component.text("Uso: zone skipto <fase>", NamedTextColor.YELLOW));
                    return;
                }
                try {
                    int phase = Integer.parseInt(args[2]);
                    if (zm.skipToPhase(phase)) {
                        player.sendMessage(Component.text("✔ Saltado a fase " + phase, NamedTextColor.GREEN));
                    } else {
                        player.sendMessage(Component.text("✘ Fase inválida.", NamedTextColor.RED));
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("✘ Número inválido.", NamedTextColor.RED));
                }
            }

            case "stop" -> {
                zm.stop();
                player.sendMessage(Component.text("✔ Zona detenida.", NamedTextColor.YELLOW));
            }

            case "teststart" -> {
                if (zm.isRunning()) {
                    player.sendMessage(Component.text("✘ La zona ya está activa.", NamedTextColor.RED));
                    return;
                }
                String err = game.getConfig().validate();
                if (err != null && err.contains("zona")) {
                    player.sendMessage(Component.text("✘ " + err, NamedTextColor.RED));
                    return;
                }
                zm.start();
                zm.beginCountdown();
                player.sendMessage(Component.text("✔ Zona iniciada en modo test.", NamedTextColor.GREEN));
                player.sendMessage(Component.text("  Usa /em battleroyale zone stop para detenerla.", NamedTextColor.GRAY));
            }

            default -> player.sendMessage(Component.text(
                    "Uso: /em battleroyale zone [info|forceshrink|skipto|stop|teststart]",
                    NamedTextColor.YELLOW));
        }
    }

    public void handleMoneyConsole(org.bukkit.command.CommandSender sender, String[] args) {
        handleMoney(sender, args);
    }

    private void handleMoney(org.bukkit.command.CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text(
                    "Uso: /em battleroyale money [get|set|add|remove|give|top] ...",
                    NamedTextColor.YELLOW));
            return;
        }

        var coins = game.getCoinManager();

        switch (args[1].toLowerCase()) {

            case "get" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Uso: money get <jugador>", NamedTextColor.YELLOW));
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    sender.sendMessage(Component.text("✘ Jugador no encontrado.", NamedTextColor.RED));
                    return;
                }
                if (game.getBRPlayer(target) == null) {
                    sender.sendMessage(Component.text("✘ " + target.getName() + " no está en la partida.",
                            NamedTextColor.RED));
                    return;
                }
                sender.sendMessage(Component.text(target.getName() + " tiene ", NamedTextColor.GRAY)
                        .append(Component.text(coins.get(target) + " monedas", NamedTextColor.GOLD)));
            }

            case "set" -> {
                if (args.length < 4) {
                    sender.sendMessage(Component.text("Uso: money set <jugador> <cantidad>", NamedTextColor.YELLOW));
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    sender.sendMessage(Component.text("✘ Jugador no encontrado.", NamedTextColor.RED));
                    return;
                }
                int amount;
                try { amount = Integer.parseInt(args[3]); }
                catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("✘ Cantidad inválida.", NamedTextColor.RED));
                    return;
                }
                if (!coins.set(target, amount)) {
                    sender.sendMessage(Component.text("✘ " + target.getName() + " no está en la partida.",
                            NamedTextColor.RED));
                    return;
                }
                sender.sendMessage(Component.text(
                        "✔ " + target.getName() + " ahora tiene " + amount + " monedas.",
                        NamedTextColor.GREEN));
            }

            case "add" -> {
                if (args.length < 4) {
                    sender.sendMessage(Component.text("Uso: money add <jugador> <cantidad>", NamedTextColor.YELLOW));
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    sender.sendMessage(Component.text("✘ Jugador no encontrado.", NamedTextColor.RED));
                    return;
                }
                int amount;
                try { amount = Integer.parseInt(args[3]); }
                catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("✘ Cantidad inválida.", NamedTextColor.RED));
                    return;
                }
                if (!coins.add(target, amount)) {
                    sender.sendMessage(Component.text("✘ " + target.getName() + " no está en la partida.",
                            NamedTextColor.RED));
                    return;
                }
                sender.sendMessage(Component.text(
                        "✔ +" + amount + " monedas a " + target.getName() +
                                " (total: " + coins.get(target) + ")", NamedTextColor.GREEN));
            }

            case "remove" -> {
                if (args.length < 4) {
                    sender.sendMessage(Component.text("Uso: money remove <jugador> <cantidad>", NamedTextColor.YELLOW));
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    sender.sendMessage(Component.text("✘ Jugador no encontrado.", NamedTextColor.RED));
                    return;
                }
                int amount;
                try { amount = Integer.parseInt(args[3]); }
                catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("✘ Cantidad inválida.", NamedTextColor.RED));
                    return;
                }
                if (!coins.remove(target, amount)) {
                    sender.sendMessage(Component.text(
                            "✘ No tiene suficientes monedas o no está en la partida.",
                            NamedTextColor.RED));
                    return;
                }
                sender.sendMessage(Component.text(
                        "✔ -" + amount + " monedas a " + target.getName() +
                                " (total: " + coins.get(target) + ")", NamedTextColor.YELLOW));
            }

            case "give" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Uso: money give <cantidad>", NamedTextColor.YELLOW));
                    return;
                }
                int amount;
                try { amount = Integer.parseInt(args[2]); }
                catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("✘ Cantidad inválida.", NamedTextColor.RED));
                    return;
                }
                int affected = coins.giveAll(amount);
                sender.sendMessage(Component.text(
                        "✔ +" + amount + " monedas a " + affected + " jugadores.",
                        NamedTextColor.GREEN));
            }

            case "top" -> {
                sender.sendMessage(Component.text("─── Top monedas ───", NamedTextColor.GOLD));
                var ranking = coins.getRanking();
                if (ranking.isEmpty()) {
                    sender.sendMessage(Component.text("  Sin jugadores en partida.", NamedTextColor.GRAY));
                    return;
                }
                int max = Math.min(10, ranking.size());
                for (int i = 0; i < max; i++) {
                    var brp = ranking.get(i);
                    sender.sendMessage(Component.text(
                                    "  #" + (i + 1) + " " + brp.getName() + " — ", NamedTextColor.GRAY)
                            .append(Component.text(brp.getCoins() + " monedas", NamedTextColor.GOLD)));
                }
            }

            default -> sender.sendMessage(Component.text(
                    "Uso: /em battleroyale money [get|set|add|remove|give|top] ...",
                    NamedTextColor.YELLOW));
        }
    }

    private void handleLoot(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                    "Uso: /em battleroyale loot [scan|list|refill|empty|clear|reload]",
                    NamedTextColor.YELLOW));
            return;
        }

        var loot = game.getLootManager();

        switch (args[1].toLowerCase()) {

            case "scan" -> {
                player.sendMessage(Component.text("⏳ Escaneando cofres...", NamedTextColor.YELLOW));
                var res = loot.scan();
                if (!res.ok()) {
                    player.sendMessage(Component.text("✘ " + res.message(), NamedTextColor.RED));
                    return;
                }
                player.sendMessage(Component.text(
                        "✔ Escaneo completo: " + res.chestsFound() + " cofres encontrados.",
                        NamedTextColor.GREEN));
                res.distribution().forEach((tier, count) ->
                        player.sendMessage(Component.text("  " + tier + ": " + count, NamedTextColor.GRAY)));
            }

            case "list" -> {
                player.sendMessage(Component.text("─── Cofres registrados ───", NamedTextColor.GOLD));
                player.sendMessage(Component.text("  Total: ", NamedTextColor.GRAY)
                        .append(Component.text(loot.getChestCount(), NamedTextColor.WHITE)));
                var dist = loot.getTierDistribution();
                if (dist.isEmpty()) {
                    player.sendMessage(Component.text("  Sin cofres. Usa scan.", NamedTextColor.RED));
                } else {
                    player.sendMessage(Component.text("  Por tier:", NamedTextColor.GRAY));
                    dist.forEach((tier, count) ->
                            player.sendMessage(Component.text("    " + tier + ": " + count, NamedTextColor.YELLOW)));
                }
                player.sendMessage(Component.text("─── Tiers configurados ───", NamedTextColor.GOLD));
                loot.getLootConfig().getTiers().forEach(t ->
                        player.sendMessage(Component.text(
                                "  " + t.id() + " w=" + t.weight() +
                                        " items=" + t.items().size() +
                                        " (" + t.minItems() + "-" + t.maxItems() + " por cofre)",
                                NamedTextColor.GRAY)));
            }

            case "refill" -> {
                var res = loot.refill();
                player.sendMessage(Component.text(
                        "✔ Refill: " + res.filled() + " cofres rellenados (" +
                                res.skipped() + " saltados).", NamedTextColor.GREEN));
            }

            case "empty" -> {
                int n = loot.emptyAll();
                player.sendMessage(Component.text(
                        "✔ " + n + " cofres vaciados.", NamedTextColor.YELLOW));
            }

            case "clear" -> {
                loot.clearChests();
                player.sendMessage(Component.text(
                        "✔ Registro de cofres limpiado. Usa 'scan' para volver a registrarlos.",
                        NamedTextColor.YELLOW));
            }

            case "reload" -> {
                loot.getLootConfig().reload();
                player.sendMessage(Component.text(
                        "✔ Tablas de loot recargadas.", NamedTextColor.GREEN));
            }

            case "particles" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("Uso: loot particles [on|off]", NamedTextColor.YELLOW));
                    return;
                }
                if (args[2].equalsIgnoreCase("on")) {
                    loot.startParticles();
                    player.sendMessage(Component.text("✔ Particles activadas.", NamedTextColor.GREEN));
                } else if (args[2].equalsIgnoreCase("off")) {
                    loot.stopParticles();
                    player.sendMessage(Component.text("✔ Particles desactivadas.", NamedTextColor.YELLOW));
                }
            }

            default -> player.sendMessage(Component.text(
                    "Uso: /em battleroyale loot [scan|list|refill|empty|clear|reload|particles]",
                    NamedTextColor.YELLOW));
        }
    }

    private void handlePoints(Player player, String[] args) {
        if (args.length < 2) {
            showPointsList(player);
            return;
        }

        int placement = switch (args[1].toLowerCase()) {
            case "first"  -> 1;
            case "second" -> 2;
            case "third"  -> 3;
            case "other"  -> 4;
            default -> -1;
        };
        if (placement == -1) {
            player.sendMessage(Component.text("Uso: /em battleroyale points [first|second|third|other] [<n>]",
                    NamedTextColor.YELLOW));
            return;
        }

        if (args.length < 3) {
            int current = game.getConfig().getPointsForPlacement(placement);
            player.sendMessage(Component.text(args[1] + ": " + current + " puntos", NamedTextColor.YELLOW));
            return;
        }

        try {
            int value = Integer.parseInt(args[2]);
            game.getConfig().setPointsForPlacement(placement, value);
            player.sendMessage(Component.text("✔ " + args[1] + " ahora otorga " + value + " puntos.",
                    NamedTextColor.GREEN));
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("✘ Número inválido.", NamedTextColor.RED));
        }
    }

    private void showPointsList(Player player) {
        var cfg = game.getConfig();
        player.sendMessage(Component.text("─── Puntos por posición ───", NamedTextColor.GOLD));
        player.sendMessage(Component.text("  1er lugar: ", NamedTextColor.GRAY)
                .append(Component.text(cfg.getPointsForPlacement(1), NamedTextColor.WHITE)));
        player.sendMessage(Component.text("  2do lugar: ", NamedTextColor.GRAY)
                .append(Component.text(cfg.getPointsForPlacement(2), NamedTextColor.WHITE)));
        player.sendMessage(Component.text("  3er lugar: ", NamedTextColor.GRAY)
                .append(Component.text(cfg.getPointsForPlacement(3), NamedTextColor.WHITE)));
        player.sendMessage(Component.text("  Resto:     ", NamedTextColor.GRAY)
                .append(Component.text(cfg.getPointsForPlacement(4), NamedTextColor.WHITE)));
    }

    private void handleRespawn(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                    "Uso: /em battleroyale respawn [add|remove|list|clear]",
                    NamedTextColor.YELLOW));
            return;
        }
        var rm = game.getRespawnManager();

        switch (args[1].toLowerCase()) {
            case "add" -> {
                rm.addRespawnPoint(player.getLocation());
                player.sendMessage(Component.text(
                        "✔ Punto de respawn agregado en tu posición. Total: "
                                + rm.getRespawnPoints().size(),
                        NamedTextColor.GREEN));
            }

            case "remove" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("Uso: respawn remove <índice>", NamedTextColor.YELLOW));
                    return;
                }
                try {
                    int idx = Integer.parseInt(args[2]) - 1;
                    if (rm.removeRespawnPoint(idx)) {
                        player.sendMessage(Component.text("✔ Punto #" + (idx + 1) + " eliminado.",
                                NamedTextColor.YELLOW));
                    } else {
                        player.sendMessage(Component.text("✘ Índice inválido.", NamedTextColor.RED));
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("✘ Número inválido.", NamedTextColor.RED));
                }
            }

            case "list" -> {
                var points = rm.getRespawnPoints();
                player.sendMessage(Component.text("─── Puntos de respawn ───", NamedTextColor.GOLD));
                if (points.isEmpty()) {
                    player.sendMessage(Component.text("  Sin puntos configurados.", NamedTextColor.GRAY));
                } else {
                    for (int i = 0; i < points.size(); i++) {
                        var p = points.get(i);
                        player.sendMessage(Component.text(String.format(
                                        "  #%d  %s (%.0f, %.0f, %.0f)",
                                        i + 1, p.getWorld().getName(), p.getX(), p.getY(), p.getZ()),
                                NamedTextColor.GRAY));
                    }
                }
            }

            case "clear" -> {
                rm.clearRespawnPoints();
                player.sendMessage(Component.text("✔ Todos los puntos de respawn eliminados.",
                        NamedTextColor.YELLOW));
            }

            default -> player.sendMessage(Component.text(
                    "Uso: /em battleroyale respawn [add|remove|list|clear]",
                    NamedTextColor.YELLOW));
        }
    }

    private void handleRevive(Player sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text(
                    "Uso: /em battleroyale revive <jugador> [force]",
                    NamedTextColor.YELLOW));
            return;
        }
        Player target = org.bukkit.Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("✘ Jugador no encontrado.", NamedTextColor.RED));
            return;
        }

        boolean force = args.length >= 3 && args[2].equalsIgnoreCase("force");
        Player reviver = force ? null : sender;

        RespawnManager.ReviveResult result = game.getRespawnManager().revivePlayer(target, reviver);
        switch (result) {
            case OK -> sender.sendMessage(Component.text("✔ " + target.getName() + " ha sido revivido.",
                    NamedTextColor.GREEN));
            case TARGET_OFFLINE       -> sender.sendMessage(Component.text("✘ Jugador no online.", NamedTextColor.RED));
            case TARGET_NOT_IN_GAME   -> sender.sendMessage(Component.text("✘ El jugador no está en la partida.", NamedTextColor.RED));
            case TARGET_NOT_DEAD      -> sender.sendMessage(Component.text("✘ El jugador no está muerto.", NamedTextColor.RED));
            case NO_RESPAWN_POINTS    -> sender.sendMessage(Component.text("✘ No hay puntos de respawn configurados.", NamedTextColor.RED));
            case DIFFERENT_TEAM       -> sender.sendMessage(Component.text("✘ No son del mismo equipo.", NamedTextColor.RED));
            case SAME_PLAYER          -> sender.sendMessage(Component.text("✘ No podés revivirte a vos mismo.", NamedTextColor.RED));
        }
    }

    public void handleReviveMateConsole(CommandSender sender, String[] args) {
        handleReviveMate(sender, args);
    }

    private void handleReviveMate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text(
                    "Uso: /em battleroyale revivemate <jugador>",
                    NamedTextColor.YELLOW));
            return;
        }

        Player reviver = Bukkit.getPlayerExact(args[1]);
        if (reviver == null) {
            sender.sendMessage(Component.text("✘ Jugador no encontrado.", NamedTextColor.RED));
            return;
        }

        var teamOpt = plugin.getTeamManager().getTeamOf(reviver);
        if (teamOpt.isEmpty()) {
            reviver.sendMessage(Component.text("✘ No pertenecés a ningún equipo.", NamedTextColor.RED));
            return;
        }

        Player target = teamOpt.get().getOnlinePlayers().stream()
                .filter(p -> !p.getUniqueId().equals(reviver.getUniqueId()))
                .filter(p -> {
                    var brp = game.getBRPlayer(p);
                    return brp != null && brp.isDead();
                })
                .findFirst()
                .orElse(null);

        if (target == null) {
            reviver.sendMessage(Component.text(
                    "✘ No tenés ningún compañero muerto para revivir.", NamedTextColor.RED));
            return;
        }

        RespawnManager.ReviveResult result = game.getRespawnManager().revivePlayer(target, reviver);
        switch (result) {
            case OK -> reviver.sendMessage(Component.text("✔ " + target.getName() + " ha sido revivido.",
                    NamedTextColor.GREEN));
            case TARGET_OFFLINE       -> reviver.sendMessage(Component.text("✘ Jugador no online.", NamedTextColor.RED));
            case TARGET_NOT_IN_GAME   -> reviver.sendMessage(Component.text("✘ El jugador no está en la partida.", NamedTextColor.RED));
            case TARGET_NOT_DEAD      -> reviver.sendMessage(Component.text("✘ El jugador no está muerto.", NamedTextColor.RED));
            case NO_RESPAWN_POINTS    -> reviver.sendMessage(Component.text("✘ No hay puntos de respawn configurados.", NamedTextColor.RED));
            case DIFFERENT_TEAM       -> reviver.sendMessage(Component.text("✘ No son del mismo equipo.", NamedTextColor.RED));
            case SAME_PLAYER          -> reviver.sendMessage(Component.text("✘ No podés revivirte a vos mismo.", NamedTextColor.RED));
        }
    }

    public List<String> tabComplete(String[] args) {
        if (args.length == 1)
            return filter(List.of("start","stop","status","setlobby","setcenter","arena","drop","zone","loot","money","points","respawn","revive","revivemate"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("arena"))
            return filter(List.of("pos1","pos2","setymin","setymax","info"), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("drop"))
            return filter(List.of("setstart","setend","setheight","setspeed","setdir","setparachute"), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("zone"))
            return filter(List.of("info","forceshrink","skipto","stop","teststart"), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("loot"))
            return filter(List.of("scan","list","refill","empty","clear","reload","particles"), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("money"))
            return filter(List.of("get","set","add","remove","give","top"), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("points"))
            return filter(List.of("first","second","third","other"), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("respawn"))
            return filter(List.of("add","remove","list","clear"), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("revive")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("revivemate")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("money")
                && List.of("get","set","add","remove").contains(args[1].toLowerCase())) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                    .toList();
        }
        return List.of();
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("─── /em battleroyale ───", NamedTextColor.GOLD));
        player.sendMessage(h("start",                    "Iniciar partida"));
        player.sendMessage(h("stop",                     "Detener partida"));
        player.sendMessage(h("status",                   "Ver estado y config"));
        player.sendMessage(h("setlobby",                 "Setear spawn del lobby"));
        player.sendMessage(h("setcenter",                "Setear centro de la arena"));
        player.sendMessage(Component.text("  ─ Arena ─", NamedTextColor.DARK_GRAY));
        player.sendMessage(h("arena pos1",               "Esquina 1 de la región"));
        player.sendMessage(h("arena pos2",               "Esquina 2 de la región"));
        player.sendMessage(h("arena setymin <n>",        "Y mínima"));
        player.sendMessage(h("arena setymax <n>",        "Y máxima"));
        player.sendMessage(h("arena info",               "Ver región configurada"));
        player.sendMessage(Component.text("  ─ Drop ─", NamedTextColor.DARK_GRAY));
        player.sendMessage(h("drop setstart",            "Inicio del avión"));
        player.sendMessage(h("drop setend",              "Final del avión"));
        player.sendMessage(h("drop setheight <n>",       "Altura de vuelo"));
        player.sendMessage(h("drop setspeed <n>",        "Velocidad (bl/tick)"));
        player.sendMessage(h("drop setparachute <a> <t>","Slow falling amp y ticks"));
        player.sendMessage(Component.text("  ─ Zone ─", NamedTextColor.DARK_GRAY));
        player.sendMessage(h("zone info",                "Estado y fases"));
        player.sendMessage(h("zone forceshrink",         "Forzar compresión"));
        player.sendMessage(h("zone skipto <fase>",       "Saltar a fase específica"));
        player.sendMessage(h("zone teststart",           "Iniciar solo la zona (test)"));
        player.sendMessage(h("zone stop",                "Detener zona"));
        player.sendMessage(Component.text("  ─ Loot ─", NamedTextColor.DARK_GRAY));
        player.sendMessage(h("loot scan",                "Escanea cofres del área"));
        player.sendMessage(h("loot list",                "Ver cofres y tiers"));
        player.sendMessage(h("loot refill",              "Rellena todos los cofres"));
        player.sendMessage(h("loot empty",               "Vacía todos los cofres"));
        player.sendMessage(h("loot clear",               "Borra registro de cofres"));
        player.sendMessage(h("loot reload",              "Recarga tablas de loot"));
        player.sendMessage(h("loot particles [on|off]",  "Activar/desactivar particles"));
        player.sendMessage(Component.text("  ─ Money ─", NamedTextColor.DARK_GRAY));
        player.sendMessage(h("money get <p>",            "Ver balance de un jugador"));
        player.sendMessage(h("money set <p> <n>",        "Setear balance"));
        player.sendMessage(h("money add <p> <n>",        "Sumar monedas"));
        player.sendMessage(h("money remove <p> <n>",     "Restar monedas"));
        player.sendMessage(h("money give <n>",           "Dar a todos los jugadores"));
        player.sendMessage(h("money top",                "Ranking de monedas"));
        player.sendMessage(Component.text("  ─ Puntos ─", NamedTextColor.DARK_GRAY));
        player.sendMessage(h("points",                   "Ver puntos por posición"));
        player.sendMessage(h("points <pos> <n>",         "Setear puntos (first|second|third|other)"));
        player.sendMessage(Component.text("  ─ Respawn / Revive ─", NamedTextColor.DARK_GRAY));
        player.sendMessage(h("respawn add",              "Agregar punto en tu pos"));
        player.sendMessage(h("respawn remove <n>",       "Eliminar punto por índice"));
        player.sendMessage(h("respawn list",             "Ver puntos configurados"));
        player.sendMessage(h("respawn clear",            "Borrar todos los puntos"));
        player.sendMessage(h("revive <p>",               "Revivir compañero (mismo equipo)"));
        player.sendMessage(h("revive <p> force",         "Revivir sin validación"));
        player.sendMessage(h("revivemate <p>",           "Revive automáticamente al compañero muerto de <p>"));
    }

    private Component h(String sub, String desc) {
        return Component.text("  /em battleroyale " + sub, NamedTextColor.YELLOW)
                .append(Component.text(" — " + desc, NamedTextColor.GRAY));
    }

    private List<String> filter(List<String> opts, String input) {
        return opts.stream().filter(s -> s.startsWith(input.toLowerCase())).toList();
    }

    private void setDouble(Player player, String[] args, String usage,
                           java.util.function.Function<Double, String> apply) {
        if (args.length < 3) { player.sendMessage(Component.text("Uso: " + usage, NamedTextColor.YELLOW)); return; }
        try {
            double v = Double.parseDouble(args[2]);
            player.sendMessage(Component.text("✔ " + apply.apply(v), NamedTextColor.GREEN));
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("✘ Número inválido.", NamedTextColor.RED));
        }
    }
}