package org.falmdev.anieventmanager.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.minigames.bingo.BingoCard;
import org.falmdev.anieventmanager.minigames.boatracing.BoatRacingMiniGame;
import org.falmdev.anieventmanager.minigames.boatracing.RacerData;
import org.falmdev.anieventmanager.minigames.frozenheist.FlagManager;
import org.falmdev.anieventmanager.minigames.frozenheist.TeamHeistData;
import org.falmdev.anieventmanager.minigames.tntrun.TNTRunMiniGame;
import org.falmdev.anieventmanager.model.EventTeam;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Placeholders disponibles:
 *
 * ── Equipo del jugador ────────────────────────────────────────────
 *   %anievent_has_team%
 *   %anievent_team_name%
 *   %anievent_team_id%
 *   %anievent_team_color%
 *   %anievent_team_members%
 *   %anievent_team_size%
 *   %anievent_team_isfull%
 *   %anievent_team_score%
 *   %anievent_team_rank%
 *
 * ── Ranking general (top N) ──────────────────────────────────────
 *   %anievent_top_1_name%
 *   %anievent_top_1_score%
 *   %anievent_top_1_color%
 *   %anievent_top_1_members%
 *   ... hasta top_8
 *
 * ── TNT Run ───────────────────────────────────────────────────────
 *   %anievent_tntrun_running%
 *   %anievent_tntrun_state%
 *   %anievent_tntrun_players%
 *   %anievent_tntrun_teams%
 *   %anievent_tntrun_elapsed%
 *   %anievent_tntrun_alive_1% ... alive_8
 *   %anievent_tntrun_iseliminated%
 *
 * ── Bingo ─────────────────────────────────────────────────────────
 *   %anievent_bingo_running%
 *   %anievent_bingo_time%
 *   %anievent_bingo_timepercent%
 *   %anievent_bingo_percent%
 *   %anievent_bingo_progressbar%
 *   %anievent_bingo_done%
 *   %anievent_bingo_total%
 *
 * ── Frozen Heist ──────────────────────────────────────────────────
 *   %anievent_fh_running%
 *   %anievent_fh_state%
 *   %anievent_fh_time%
 *   %anievent_fh_timepercent%
 *   %anievent_fh_top_1_name%  _score%  _color%  ... top_8
 *   %anievent_fh_score%
 *   %anievent_fh_rank%
 *   %anievent_fh_flag_state%
 *   %anievent_fh_flag_carrier%
 *   %anievent_fh_carrying%
 *   %anievent_fh_carrying_team%
 *   %anievent_fh_frozen%
 *   %anievent_fh_frozen_seconds%
 *
 * ── Boat Racing ───────────────────────────────────────────────────
 *   %anievent_br_running%            true si hay sesión activa (qualy o carrera)
 *   %anievent_br_state%              Estado: En espera / Paddock / Clasificación / Carrera / Finalizado
 *   %anievent_br_lap%                Vuelta actual del jugador (ej: "2")
 *   %anievent_br_totallaps%          Total de vueltas configuradas (ej: "3")
 *   %anievent_br_laps%               "Vuelta 2/3" — formato compacto
 *   %anievent_br_laptime%            Tiempo transcurrido en la vuelta actual (ej: "1:23.456")
 *   %anievent_br_bestlap%            Mejor vuelta del jugador (ej: "1:21.200")
 *   %anievent_br_lastlap%            Tiempo de la última vuelta completada
 *   %anievent_br_position%           Posición en tiempo real (ej: "#3")
 *   %anievent_br_position_num%       Solo el número (ej: "3")
 *   %anievent_br_qualytime%          Tiempo de clasificación del jugador ("DNF" si no terminó)
 *   %anievent_br_gridpos%            Posición de parrilla después de la qualy (ej: "#1")
 *   %anievent_br_top_1_name%         Jugador en posición 1 en tiempo real
 *   %anievent_br_top_1_lap%          Vuelta actual del jugador en pos 1
 *   %anievent_br_top_1_laptime%      Tiempo de vuelta del jugador en pos 1
 *   %anievent_br_top_2_name%         ... hasta top_8
 *   %anievent_br_top_2_lap%
 *   %anievent_br_top_2_laptime%
 *   %anievent_br_gap%                Diferencia de tiempo con el jugador delante (ej: "+2.341s")
 *   %anievent_br_interval%           Diferencia con el líder (ej: "+5.123s")
 *   %anievent_br_finished%           true si el jugador ya terminó la carrera
 *   %anievent_br_racepos%            Posición final en carrera (solo si terminó)
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

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (params.startsWith("top_"))       return resolveTop(params);
        if (params.startsWith("bingo_"))     return resolveBingo(offlinePlayer, params);
        if (params.startsWith("tntrun_"))    return resolveTNTRun(offlinePlayer, params);
        if (params.startsWith("fh_"))        return resolveFrozenHeist(offlinePlayer, params);
        if (params.startsWith("br_"))        return resolveBoatRacing(offlinePlayer, params);

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
            case "tntrun_teams"   -> String.valueOf(tnt.getAliveTeamCount());
            case "tntrun_elapsed" -> tnt.isRunning() ? tnt.getElapsedFormatted() : "--:--";
            case "tntrun_alive_1", "tntrun_alive_2", "tntrun_alive_3",
                 "tntrun_alive_4", "tntrun_alive_5", "tntrun_alive_6",
                 "tntrun_alive_7", "tntrun_alive_8" -> {
                int index = Integer.parseInt(params.replace("tntrun_alive_", "")) - 1;
                List<EventTeam> alive = tnt.getAliveTeams();
                yield index < alive.size() ? alive.get(index).getDisplayName() : "-";
            }
            case "tntrun_iseliminated" -> {
                Player player = offlinePlayer.getPlayer();
                if (player == null || !tnt.isRunning()) yield "false";
                Optional<EventTeam> teamOpt = plugin.getTeamManager().getTeamOf(player);
                if (teamOpt.isEmpty()) yield "false";
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
            case "bingo_timepercent" -> {
                if (!bingo.isRunning()) yield "0";
                int total = bingo.getConfig().getDurationMinutes() * 60;
                int left  = bingo.getTimeLeftSeconds();
                if (total <= 0) yield "0";
                int pct = (int) Math.round((left / (double) total) * 100);
                yield String.valueOf(Math.max(0, Math.min(100, pct)));
            }
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
                yield card != null ? getProgressBar(card.getCompletionPercent()) : "&8░░░░░░░░░░ &70%";
            }
            default -> null;
        };
    }

    // ── Frozen Heist ──────────────────────────────────────────────────────────

    private String resolveFrozenHeist(OfflinePlayer offlinePlayer, String params) {
        var fh = plugin.getFrozenHeistMiniGame();

        if (params.equals("fh_running")) return String.valueOf(fh.isRunning());
        if (params.equals("fh_state")) return switch (fh.getState()) {
            case IDLE     -> "En espera";
            case RUNNING  -> "En juego";
            case FINISHED -> "Finalizado";
        };
        if (params.equals("fh_time")) return fh.isRunning() ? fh.getTimeLeftFormatted() : "--:--";
        if (params.equals("fh_timepercent")) {
            if (!fh.isRunning()) return "0";
            int total = fh.getConfig().getDurationMinutes() * 60;
            if (total <= 0) return "0";
            int pct = (int) Math.round((fh.getTimeLeftSeconds() / (double) total) * 100);
            return String.valueOf(Math.max(0, Math.min(100, pct)));
        }

        if (params.startsWith("fh_top_")) {
            String[] parts = params.split("_");
            if (parts.length != 4) return "-";
            int pos; try { pos = Integer.parseInt(parts[2]); } catch (NumberFormatException e) { return "-"; }
            if (pos < 1 || pos > 8) return "-";
            if (!fh.isRunning()) return "-";
            List<TeamHeistData> ranking = fh.getTeamData().values().stream()
                    .sorted((a, b) -> Integer.compare(b.getPoints(), a.getPoints())).toList();
            if (pos > ranking.size()) return "-";
            TeamHeistData entry = ranking.get(pos - 1);
            return switch (parts[3]) {
                case "name"  -> entry.getTeam().getDisplayName();
                case "score" -> String.valueOf(entry.getPoints());
                case "color" -> namedColorToLegacy(entry.getTeam().getColor().toString());
                default      -> "-";
            };
        }

        Player player = offlinePlayer.getPlayer();
        if (player == null) return switch (params) {
            case "fh_score", "fh_frozen_seconds" -> "0";
            case "fh_rank", "fh_flag_state", "fh_flag_carrier",
                 "fh_carrying_team", "fh_racepos" -> "-";
            case "fh_carrying", "fh_frozen" -> "false";
            default -> null;
        };

        Optional<EventTeam> teamOpt = plugin.getTeamManager().getTeamOf(player);

        return switch (params) {
            case "fh_score" -> {
                if (!fh.isRunning() || teamOpt.isEmpty()) yield "0";
                var data = fh.getTeamData().get(teamOpt.get().getId());
                yield data != null ? String.valueOf(data.getPoints()) : "0";
            }
            case "fh_rank" -> {
                if (!fh.isRunning() || teamOpt.isEmpty()) yield "-";
                String myId = teamOpt.get().getId();
                List<TeamHeistData> ranked = fh.getTeamData().values().stream()
                        .sorted((a, b) -> Integer.compare(b.getPoints(), a.getPoints())).toList();
                for (int i = 0; i < ranked.size(); i++)
                    if (ranked.get(i).getTeam().getId().equals(myId)) yield "#" + (i + 1);
                yield "-";
            }
            case "fh_flag_state" -> {
                if (!fh.isRunning() || teamOpt.isEmpty()) yield "-";
                FlagManager.FlagState state = fh.getFlagManager().getState(teamOpt.get().getId());
                yield switch (state) { case IN_BASE -> "En base"; case CARRIED -> "Robada"; case DROPPED -> "Caída"; };
            }
            case "fh_flag_carrier" -> {
                if (!fh.isRunning() || teamOpt.isEmpty()) yield "-";
                UUID carrierUUID = fh.getFlagManager().getCarrier(teamOpt.get().getId());
                if (carrierUUID == null) yield "-";
                Player carrier = Bukkit.getPlayer(carrierUUID);
                yield carrier != null ? carrier.getName() : "-";
            }
            case "fh_carrying" -> {
                if (!fh.isRunning()) yield "false";
                var ps = fh.getPlayerState(player.getUniqueId());
                yield ps != null ? String.valueOf(ps.isCarryingFlag()) : "false";
            }
            case "fh_carrying_team" -> {
                if (!fh.isRunning()) yield "-";
                var ps = fh.getPlayerState(player.getUniqueId());
                if (ps == null || !ps.isCarryingFlag()) yield "-";
                var flagData = fh.getTeamData().get(ps.getCarryingFlagOf());
                yield flagData != null ? flagData.getTeam().getDisplayName() : "-";
            }
            case "fh_frozen" -> {
                if (!fh.isRunning()) yield "false";
                var ps = fh.getPlayerState(player.getUniqueId());
                yield ps != null ? String.valueOf(ps.isFrozen()) : "false";
            }
            case "fh_frozen_seconds" -> {
                if (!fh.isRunning()) yield "0";
                var ps = fh.getPlayerState(player.getUniqueId());
                yield ps != null ? String.valueOf(ps.getFrozenSecondsLeft()) : "0";
            }
            default -> null;
        };
    }

    // ── Boat Racing ───────────────────────────────────────────────────────────

    private String resolveBoatRacing(OfflinePlayer offlinePlayer, String params) {
        BoatRacingMiniGame br = plugin.getBoatRacingMiniGame();
        Player player = offlinePlayer.getPlayer();

        // ── Estado global ─────────────────────────────────────────────────────
        if (params.equals("br_running")) return String.valueOf(br.isRunning());

        if (params.equals("br_state")) return switch (br.getState()) {
            case IDLE     -> "En espera";
            case PADDOCK  -> "Paddock";
            case QUALY    -> "Clasificación";
            case RACE     -> "Carrera";
            case FINISHED -> "Finalizado";
        };

        if (params.equals("br_totallaps")) return String.valueOf(br.getConfig().getTotalLaps());

        // ── Top N en tiempo real ──────────────────────────────────────────────
        // %anievent_br_top_1_name%   %anievent_br_top_1_lap%   %anievent_br_top_1_laptime%
        if (params.startsWith("br_top_")) {
            String[] parts = params.split("_"); // br_top_N_field
            if (parts.length != 4) return "-";
            int pos; try { pos = Integer.parseInt(parts[2]); } catch (NumberFormatException e) { return "-"; }
            if (pos < 1 || pos > 8) return "-";
            if (!br.isRunning()) return "-";

            List<UUID> positions = br.getRealTimePositions();
            if (pos > positions.size()) return "-";

            UUID uuid = positions.get(pos - 1);
            RacerData rd = br.getRacers().get(uuid);
            if (rd == null) return "-";

            return switch (parts[3]) {
                case "name"    -> rd.getPlayerName();
                case "lap"     -> rd.getCurrentLap() > 0
                        ? rd.getCurrentLap() + "/" + br.getConfig().getTotalLaps()
                        : "-";
                case "laptime" -> br.getState() == BoatRacingMiniGame.State.RACE
                        ? rd.getCurrentLapTimeFormatted()
                        : rd.getQualyTimeFormatted();
                case "bestlap" -> rd.getBestLapFormatted();
                default        -> "-";
            };
        }

        // ── Placeholders del jugador ──────────────────────────────────────────
        if (player == null) return switch (params) {
            case "br_lap", "br_laps", "br_position", "br_position_num",
                 "br_gridpos", "br_racepos"           -> "-";
            case "br_laptime", "br_bestlap", "br_lastlap",
                 "br_qualytime", "br_gap", "br_interval" -> "--:--.---";
            case "br_finished"                         -> "false";
            default                                    -> null;
        };

        RacerData rd = br.getRacerData(player);

        return switch (params) {

            // Vuelta actual
            case "br_lap" -> {
                if (rd == null || rd.getCurrentLap() == 0) yield "-";
                yield String.valueOf(rd.getCurrentLap());
            }

            // "Vuelta 2/3"
            case "br_laps" -> {
                if (rd == null || rd.getCurrentLap() == 0) yield "-";
                yield "Vuelta " + rd.getCurrentLap() + "/" + br.getConfig().getTotalLaps();
            }

            // Tiempo transcurrido en la vuelta actual
            case "br_laptime" -> {
                if (rd == null || !br.isRunning()) yield "--:--.---";
                yield rd.getCurrentLapTimeFormatted();
            }

            // Mejor vuelta del jugador
            case "br_bestlap" -> {
                if (rd == null) yield "--:--.---";
                yield rd.getBestLapFormatted();
            }

            // Tiempo de la última vuelta completada
            case "br_lastlap" -> {
                if (rd == null || rd.getLastLapTimeMs() == 0) yield "--:--.---";
                yield RacerData.formatTime(rd.getLastLapTimeMs());
            }

            // Posición en tiempo real con #
            case "br_position" -> {
                if (rd == null || !br.isRunning()) yield "-";
                yield "#" + br.getRealTimePosition(player.getUniqueId());
            }

            // Solo el número
            case "br_position_num" -> {
                if (rd == null || !br.isRunning()) yield "-";
                yield String.valueOf(br.getRealTimePosition(player.getUniqueId()));
            }

            // Tiempo de clasificación
            case "br_qualytime" -> {
                if (rd == null) yield "--:--.---";
                yield rd.getQualyTimeFormatted();
            }

            // Posición de parrilla tras la qualy
            case "br_gridpos" -> {
                if (rd == null || rd.getQualyPosition() == 0) yield "-";
                yield "#" + rd.getQualyPosition();
            }

            // true si el jugador ya terminó la carrera
            case "br_finished" -> {
                if (rd == null) yield "false";
                yield String.valueOf(rd.isRaceFinished());
            }

            // Posición final (solo si terminó)
            case "br_racepos" -> {
                if (rd == null || !rd.isRaceFinished()) yield "-";
                yield "#" + rd.getRacePosition();
            }

            // Diferencia con el jugador inmediatamente delante (+X.XXXs)
            case "br_gap" -> {
                if (rd == null || !br.isRunning()) yield "-";
                List<UUID> positions = br.getRealTimePositions();
                int myPos = positions.indexOf(player.getUniqueId());
                if (myPos <= 0) yield "—"; // es el líder
                UUID aheadUUID = positions.get(myPos - 1);
                RacerData ahead = br.getRacers().get(aheadUUID);
                if (ahead == null) yield "-";
                // Diferencia basada en tiempo de vuelta actual (aproximación)
                long myTime    = System.currentTimeMillis() - rd.getBestLapTimeMs();
                long aheadTime = System.currentTimeMillis() - ahead.getBestLapTimeMs();
                long diff = Math.abs(myTime - aheadTime);
                yield "+" + String.format("%.3f", diff / 1000.0) + "s";
            }

            // Diferencia con el líder
            case "br_interval" -> {
                if (rd == null || !br.isRunning()) yield "-";
                List<UUID> positions = br.getRealTimePositions();
                if (positions.isEmpty()) yield "-";
                UUID leaderUUID = positions.get(0);
                if (leaderUUID.equals(player.getUniqueId())) yield "—";
                RacerData leader = br.getRacers().get(leaderUUID);
                if (leader == null || leader.getBestLapTimeMs() == 0
                        || rd.getBestLapTimeMs() == 0) yield "-";
                long diff = Math.abs(rd.getBestLapTimeMs() - leader.getBestLapTimeMs());
                yield "+" + String.format("%.3f", diff / 1000.0) + "s";
            }

            default -> null;
        };
    }

    // ── Ranking general ───────────────────────────────────────────────────────

    private String resolveTop(String params) {
        String[] parts = params.split("_");
        if (parts.length != 3) return null;
        int pos; try { pos = Integer.parseInt(parts[1]); if (pos < 1 || pos > 8) return null; }
        catch (NumberFormatException e) { return null; }
        String field = parts[2];
        List<Map.Entry<String, Integer>> lb = plugin.getScoreManager().getLeaderboard();
        if (pos > lb.size()) return "-";
        Map.Entry<String, Integer> entry = lb.get(pos - 1);
        return switch (field) {
            case "name"  -> plugin.getTeamManager().getTeam(entry.getKey()).map(EventTeam::getDisplayName).orElse(entry.getKey());
            case "score" -> String.valueOf(entry.getValue());
            case "color" -> plugin.getTeamManager().getTeam(entry.getKey()).map(t -> namedColorToLegacy(t.getColor().toString())).orElse("&f");
            case "members" -> plugin.getTeamManager().getTeam(entry.getKey())
                    .map(team -> team.getMembers().stream()
                            .map(uuid -> { var off = plugin.getServer().getOfflinePlayer(uuid); return off.getName() != null ? off.getName() : "desconocido"; })
                            .collect(Collectors.joining(", ")))
                    .filter(s -> !s.isEmpty()).orElse("ninguno");
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
        String color = percent < 50 ? "&c" : percent < 80 ? "&e" : "&a";
        for (int i = 0; i < totalBars; i++)
            bar.append(i < filledBars ? color + "█" : "&8░");
        return bar + " &7" + percent + "%";
    }
}