package org.falmdev.anieventmanager.minigames.tntrun;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.model.EventTeam;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


public class TNTRunPlaceholders {

    private final Anieventmanager plugin;
    private final TNTRunMiniGame  game;

    public TNTRunPlaceholders(Anieventmanager plugin, TNTRunMiniGame game) {
        this.plugin = plugin;
        this.game   = game;
    }

    public String resolve(OfflinePlayer offlinePlayer, String params) {

        switch (params) {
            case "tntrun_running" -> { return String.valueOf(game.isRunning()); }
            case "tntrun_state"   -> {
                return switch (game.getState()) {
                    case IDLE      -> "En espera";
                    case LOBBY     -> "Lobby";
                    case COUNTDOWN -> "Iniciando";
                    case RUNNING   -> "En juego";
                    case FINISHED  -> "Finalizando";
                };
            }
            case "tntrun_players"     -> { return String.valueOf(game.getActivePlayerCount()); }
            case "tntrun_teams"       -> { return String.valueOf(game.getAliveTeamCount()); }
            case "tntrun_elapsed"     -> { return game.isRunning() ? game.getElapsedFormatted() : "--:--"; }
            case "tntrun_floor_total" -> { return String.valueOf(game.getTotalFloors()); }
        }

        if (params.startsWith("tntrun_alive_")) {
            int index = Integer.parseInt(params.replace("tntrun_alive_", "")) - 1;
            List<EventTeam> alive = game.getAliveTeams();
            return index < alive.size() ? alive.get(index).getDisplayName() : "-";
        }

        Player player = offlinePlayer.getPlayer();

        if (params.equals("tntrun_iseliminated")) {
            if (player == null || !game.isRunning()) return "false";
            Optional<org.falmdev.anieventmanager.model.EventTeam> teamOpt =
                    plugin.getTeamManager().getTeamOf(player);
            if (teamOpt.isEmpty()) return "false";
            boolean eliminated = game.getAliveTeams().stream()
                    .noneMatch(t -> t.getId().equals(teamOpt.get().getId()));
            return String.valueOf(eliminated);
        }

        if (params.equals("tntrun_jump_cooldown")) {
            if (player == null) return "0";
            TNTRunListener listener = game.getGameListener();
            if (listener == null) return "0";
            return String.valueOf(listener.getJumpCooldownPercent(player.getUniqueId()));
        }

        if (params.equals("tntrun_jump_bar")) {
            if (player == null) return buildJumpBar(0);
            TNTRunListener listener = game.getGameListener();
            int pct = listener != null ? listener.getJumpCooldownPercent(player.getUniqueId()) : 0;
            return buildJumpBar(pct);
        }

        if (player == null || !game.isRunning()) {
            return switch (params) {
                case "tntrun_floor"                -> "-";
                case "tntrun_floor_players"        -> "0";
                case "tntrun_floor_playernames"    -> "";
                case "tntrun_floor_blocks"         -> "0";
                case "tntrun_floor_blocks_total"   -> "0";
                case "tntrun_floor_blocks_percent" -> "0";
                default -> null;
            };
        }

        int floor = game.getPlayerFloor(player);

        return switch (params) {
            case "tntrun_floor" -> floor > 0 ? String.valueOf(floor) : "-";

            case "tntrun_floor_players" -> {
                if (floor < 1) yield "0";
                yield String.valueOf(game.getPlayersOnFloor(floor).size());
            }

            case "tntrun_floor_playernames" -> {
                if (floor < 1) yield "";
                yield game.getPlayersOnFloor(floor).stream()
                        .map(Player::getName)
                        .collect(Collectors.joining(", "));
            }

            case "tntrun_floor_blocks" -> {
                if (floor < 1) yield "0";
                yield String.valueOf(game.getSandCountOnFloor(floor));
            }

            case "tntrun_floor_blocks_total" -> {
                if (floor < 1) yield "0";
                yield String.valueOf(game.getTotalSandOnFloor(floor));
            }

            case "tntrun_floor_blocks_percent" -> {
                if (floor < 1) yield "0";
                int total   = game.getTotalSandOnFloor(floor);
                int current = game.getSandCountOnFloor(floor);
                if (total <= 0) yield "0";
                int pct = (int) Math.round((current * 100.0) / total);
                yield String.valueOf(Math.max(0, Math.min(100, pct)));
            }

            default -> null;
        };
    }


    private String buildJumpBar(int pct) {
        int total  = 10;
        int filled = (int) Math.round((pct / 100.0) * total);
        String filledColor = pct > 60 ? "§c" : pct > 30 ? "§e" : "§a";
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < total; i++) {
            bar.append(i < filled ? filledColor + "█" : "§a█");
        }
        return bar.toString();
    }
}