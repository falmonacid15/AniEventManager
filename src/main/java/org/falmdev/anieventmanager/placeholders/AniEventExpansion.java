package org.falmdev.anieventmanager.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.minigames.bingo.BingoCard;
import org.falmdev.anieventmanager.minigames.tntrun.TNTRunMiniGame;
import org.falmdev.anieventmanager.model.EventTeam;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Placeholders disponibles:
 *
 * ── Equipo del jugador ────────────────────────────────────────────
 *   %anievent_has_team%              true / false
 *   %anievent_team_name%             Nombre del equipo
 *   %anievent_team_id%               ID del equipo
 *   %anievent_team_color%            Codigo legacy del color (&c, &9...)
 *   %anievent_team_members%          Miembros separados por coma
 *   %anievent_team_size%             Cantidad de miembros (0, 1 o 2)
 *   %anievent_team_isfull%           true / false
 *
 * ── Puntaje del equipo del jugador ───────────────────────────────
 *   %anievent_team_score%            Puntaje total del equipo
 *   %anievent_team_rank%             Posicion en el ranking (#1, #2...)
 *
 * ── Ranking general (top N) ──────────────────────────────────────
 *   %anievent_top_1_name%            Nombre del equipo en 1er lugar
 *   %anievent_top_1_score%           Puntaje del equipo en 1er lugar
 *   %anievent_top_1_color%           Color del equipo en 1er lugar
 *   %anievent_top_1_members%         Miembros del equipo en 1er lugar
 *   %anievent_top_2_name%            ... (hasta top_8)
 *   %anievent_top_2_score%
 *
 * ── TNT Run ───────────────────────────────────────────────────────
 *   %anievent_tntrun_running%        true si hay partida en curso
 *   %anievent_tntrun_state%          Estado: IDLE / COUNTDOWN / RUNNING / FINISHED
 *   %anievent_tntrun_players%        Jugadores activos restantes (ej: "6")
 *   %anievent_tntrun_teams%          Equipos vivos restantes (ej: "3")
 *   %anievent_tntrun_elapsed%        Tiempo transcurrido mm:ss (ej: "02:34")
 *   %anievent_tntrun_alive_1%        Nombre del 1er equipo aún vivo (orden de llegada)
 *   %anievent_tntrun_alive_2%        Nombre del 2do equipo aún vivo
 *   %anievent_tntrun_iseliminated%   true si el equipo del jugador fue eliminado
 *
 * ── Bingo ─────────────────────────────────────────────────────────
 *   %anievent_bingo_running%         true si hay partida en curso
 *   %anievent_bingo_time%            Tiempo restante formateado (ej: "24:35")
 *   %anievent_bingo_timepercent%     Porcentaje restante
 *   %anievent_bingo_percent%         Porcentaje completado del equipo del jugador (ej: "60%")
 *   %anievent_bingo_progressbar%     Barra de progreso
 *   %anievent_bingo_done%            Tareas completadas del equipo (ej: "15")
 *   %anievent_bingo_total%           Total de tareas en la tarjeta (ej: "25")
 */
public class AniEventExpansion extends PlaceholderExpansion {

    private final Anieventmanager plugin;

    public AniEventExpansion(Anieventmanager plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getIdentifier() { return "anievent"; }
    @Override public @NotNull String getAuthor() { return String.join(", ", plugin.getDescription().getAuthors()); }
    @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    // ── Resolución ────────────────────────────────────────────────────────────

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (params.startsWith("top_"))    return resolveTop(params);
        if (params.startsWith("bingo_"))  return resolveBingo(offlinePlayer, params);
        if (params.startsWith("tntrun_")) return resolveTNTRun(offlinePlayer, params);

        Player player = offlinePlayer.getPlayer();

        if (player == null) {
            return switch (params) {
                case "has_team"     -> "false";
                case "team_name"    -> "Sin equipo";
                case "team_id"      -> "";
                case "team_color"   -> "&f";
                case "team_members" -> "";
                case "team_size"    -> "0";
                case "team_isfull"  -> "false";
                case "team_score"   -> "0";
                case "team_rank"    -> "-";
                default             -> null;
            };
        }

        Optional<EventTeam> teamOpt = plugin.getTeamManager().getTeamOf(player);

        return switch (params) {
            case "has_team"     -> String.valueOf(teamOpt.isPresent());
            case "team_name"    -> teamOpt.map(EventTeam::getDisplayName).orElse("Sin equipo");
            case "team_id"      -> teamOpt.map(EventTeam::getId).orElse("");
            case "team_color"   -> teamOpt.map(t -> namedColorToLegacy(t.getColor().toString())).orElse("&f");
            case "team_members" -> teamOpt.map(t -> {
                var names = t.getOnlinePlayers().stream().map(Player::getName).toList();
                return names.isEmpty() ? "ninguno" : String.join(", ", names);
            }).orElse("");
            case "team_size"    -> teamOpt.map(t -> String.valueOf(t.getMemberCount())).orElse("0");
            case "team_isfull"  -> teamOpt.map(t -> String.valueOf(t.isFull())).orElse("false");
            case "team_score"   -> teamOpt.map(t -> String.valueOf(plugin.getScoreManager().getScore(t))).orElse("0");
            case "team_rank"    -> teamOpt.map(t -> {
                int rank = plugin.getScoreManager().getRank(t);
                return rank == -1 ? "-" : "#" + rank;
            }).orElse("-");
            default -> null;
        };
    }

    // ── TNT Run ───────────────────────────────────────────────────────────────

    private String resolveTNTRun(OfflinePlayer offlinePlayer, String params) {
        TNTRunMiniGame tnt = plugin.getTNTRunMiniGame();

        return switch (params) {

            case "tntrun_running" -> String.valueOf(tnt.isRunning());

            case "tntrun_state" -> switch (tnt.getState()) {
                case IDLE      -> "En espera";
                case LOBBY     -> "Lobby";
                case COUNTDOWN -> "Iniciando";
                case RUNNING   -> "En juego";
                case FINISHED  -> "Finalizando";
            };

            case "tntrun_players" -> String.valueOf(tnt.getActivePlayerCount());

            case "tntrun_teams" -> String.valueOf(tnt.getAliveTeamCount());

            case "tntrun_elapsed" -> tnt.isRunning()
                    ? tnt.getElapsedFormatted()
                    : "--:--";

            // Equipo vivo en posición N — %anievent_tntrun_alive_1%, _2%, etc.
            case "tntrun_alive_1", "tntrun_alive_2", "tntrun_alive_3",
                 "tntrun_alive_4", "tntrun_alive_5", "tntrun_alive_6",
                 "tntrun_alive_7", "tntrun_alive_8" -> {
                int index = Integer.parseInt(params.replace("tntrun_alive_", "")) - 1;
                List<EventTeam> alive = tnt.getAliveTeams();
                yield index < alive.size()
                        ? alive.get(index).getDisplayName()
                        : "-";
            }

            // true si el equipo del jugador ya fue eliminado
            case "tntrun_iseliminated" -> {
                Player player = offlinePlayer.getPlayer();
                if (player == null || !tnt.isRunning()) yield "false";
                Optional<EventTeam> teamOpt = plugin.getTeamManager().getTeamOf(player);
                if (teamOpt.isEmpty()) yield "false";
                // Eliminado = el equipo no está en la lista de vivos
                boolean eliminated = tnt.getAliveTeams().stream()
                        .noneMatch(t -> t.getId().equals(teamOpt.get().getId()));
                yield String.valueOf(eliminated);
            }

            default -> null;
        };
    }

    // ── Bingo ─────────────────────────────────────────────────────────────────

    private String resolveBingo(OfflinePlayer offlinePlayer, String params) {
        var bingo = plugin.getBingoMiniGame();

        return switch (params) {
            case "bingo_running" -> String.valueOf(bingo.isRunning());
            case "bingo_time"    -> bingo.isRunning() ? bingo.getTimeLeftFormatted() : "--:--";

            case "bingo_percent" -> {
                Player player = offlinePlayer.getPlayer();
                if (player == null || !bingo.isRunning()) yield "0%";
                Optional<EventTeam> teamOpt = plugin.getTeamManager().getTeamOf(player);
                if (teamOpt.isEmpty()) yield "0%";
                BingoCard card = bingo.getCard(teamOpt.get());
                yield card != null ? card.getCompletionPercent() + "%" : "0%";
            }
            case "bingo_done" -> {
                Player player = offlinePlayer.getPlayer();
                if (player == null || !bingo.isRunning()) yield "0";
                Optional<EventTeam> teamOpt = plugin.getTeamManager().getTeamOf(player);
                if (teamOpt.isEmpty()) yield "0";
                BingoCard card = bingo.getCard(teamOpt.get());
                yield card != null ? String.valueOf(card.getCompletedCount()) : "0";
            }
            case "bingo_total" -> {
                Player player = offlinePlayer.getPlayer();
                if (player == null || !bingo.isRunning()) yield "0";
                Optional<EventTeam> teamOpt = plugin.getTeamManager().getTeamOf(player);
                if (teamOpt.isEmpty()) yield "0";
                BingoCard card = bingo.getCard(teamOpt.get());
                yield card != null ? String.valueOf(card.getTotalTasks()) : "0";
            }
            case "bingo_progressbar" -> {
                Player player = offlinePlayer.getPlayer();
                if (player == null || !bingo.isRunning()) yield "&8░░░░░░░░░░ &70%";

                Optional<EventTeam> teamOpt = plugin.getTeamManager().getTeamOf(player);
                if (teamOpt.isEmpty()) yield "&8░░░░░░░░░░ &70%";

                BingoCard card = bingo.getCard(teamOpt.get());
                if (card == null) yield "&8░░░░░░░░░░ &70%";

                int percent = card.getCompletionPercent();
                yield getProgressBar(percent);
            }
            case "bingo_timepercent" -> {
                if (!bingo.isRunning()) yield "0";

                int total = bingo.getTotalTimeSeconds();
                int left = bingo.getTimeLeftSeconds();

                if (total <= 0) yield "0";

                double percent = (left / (double) total) * 100;

                int rounded = (int) Math.round(percent);

                // clamp 0–100
                if (rounded < 0) rounded = 0;
                if (rounded > 100) rounded = 100;

                yield String.valueOf(rounded);
            }
            default -> null;
        };
    }

    // ── Ranking general ───────────────────────────────────────────────────────

    private String resolveTop(String params) {
        String[] parts = params.split("_");
        if (parts.length != 3) return null;

        int pos;
        try {
            pos = Integer.parseInt(parts[1]);
            if (pos < 1 || pos > 8) return null;
        } catch (NumberFormatException e) { return null; }

        String field = parts[2];
        List<Map.Entry<String, Integer>> lb = plugin.getScoreManager().getLeaderboard();
        if (pos > lb.size()) return "-";

        Map.Entry<String, Integer> entry = lb.get(pos - 1);
        return switch (field) {
            case "name"  -> plugin.getTeamManager().getTeam(entry.getKey())
                    .map(EventTeam::getDisplayName).orElse(entry.getKey());
            case "score" -> String.valueOf(entry.getValue());
            case "color" -> plugin.getTeamManager().getTeam(entry.getKey())
                    .map(t -> namedColorToLegacy(t.getColor().toString())).orElse("&f");
            case "members" -> plugin.getTeamManager().getTeam(entry.getKey())
                    .map(team -> team.getMembers().stream()
                            .map(uuid -> {
                                var offline = plugin.getServer().getOfflinePlayer(uuid);
                                return offline.getName() != null ? offline.getName() : "desconocido";
                            })
                            .collect(java.util.stream.Collectors.joining(", ")))
                    .filter(s -> !s.isEmpty())
                    .orElse("ninguno");
            default -> null;
        };
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private String namedColorToLegacy(String colorName) {
        return switch (colorName) {
            case "red"          -> "&c";
            case "blue"         -> "&9";
            case "green"        -> "&a";
            case "yellow"       -> "&e";
            case "light_purple" -> "&d";
            case "aqua"         -> "&b";
            case "gold"         -> "&6";
            case "white"        -> "&f";
            case "dark_red"     -> "&4";
            case "dark_blue"    -> "&1";
            case "dark_green"   -> "&2";
            case "dark_aqua"    -> "&3";
            case "dark_purple"  -> "&5";
            case "dark_gray"    -> "&8";
            case "gray"         -> "&7";
            case "black"        -> "&0";
            default             -> "&f";
        };
    }

    private String getProgressBar(int percent) {
        int totalBars = 10;
        int filledBars = (int) Math.round((percent / 100.0) * totalBars);

        StringBuilder bar = new StringBuilder();

        String color;
        if (percent < 50) color = "&c";
        else if (percent < 80) color = "&e";
        else color = "&a";

        for (int i = 0; i < totalBars; i++) {
            if (i < filledBars) {
                bar.append(color).append("█");
            } else {
                bar.append("&8░");
            }
        }

        return bar + " &7" + percent + "%";
    }
}