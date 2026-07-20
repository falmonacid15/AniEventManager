package org.falmdev.anieventmanager.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.minigames.bingo.BingoCard;
import org.falmdev.anieventmanager.minigames.frozenheist.FlagManager;
import org.falmdev.anieventmanager.minigames.frozenheist.TeamHeistData;
import org.falmdev.anieventmanager.minigames.parkourduos.HotbarAbility;
import org.falmdev.anieventmanager.minigames.tntrun.TNTRunMiniGame;
import org.falmdev.anieventmanager.model.EventTeam;
import org.falmdev.anieventmanager.utils.interval.IntervalManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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
        if (params.startsWith("battleroyale_")) return plugin.getBattleRoyalePlaceholders()
                .resolve(offlinePlayer, params.substring("battleroyale_".length()));
        if (params.startsWith("pvpfinal_")) return plugin.getPvpFinalPlaceholders()
                .resolve(offlinePlayer, params.substring("pvpfinal_".length()));
        if (params.startsWith("teamid_"))    return resolveTeamById(params);
        if (params.startsWith("pd_"))        return resolveParkourDuos(offlinePlayer, params);
        if (params.startsWith("interval_"))   return resolveInterval(params);

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
            case "teams_players_total" -> {
                int total = plugin.getTeamManager().getTeams().values().stream()
                        .mapToInt(EventTeam::getMemberCount)
                        .sum();
                yield String.valueOf(total);
            }

            case "teams_with_players" -> {
                long count = plugin.getTeamManager().getTeams().values().stream()
                        .filter(team -> team.getMemberCount() > 0)
                        .count();
                yield String.valueOf(count);
            }
            default -> null;
        };
    }

    // ── TNT Run ───────────────────────────────────────────────────────────────
    private String resolveInterval(String params) {
        var im = plugin.getIntervalManager();
        return switch (params) {
            case "interval_active"  -> String.valueOf(im.isActive());
            case "interval_time"    -> im.getTimeLeftFormatted();
            case "interval_seconds" -> String.valueOf(im.getSecondsLeft());
            case "interval_percent" -> String.valueOf(im.getPercentLeft());
            case "interval_total"   -> IntervalManager.formatDuration(im.getTotalSeconds());
            default -> null;
        };
    }

    private String resolveTNTRun(OfflinePlayer offlinePlayer, String params) {
        return plugin.getTNTRunPlaceholders().resolve(offlinePlayer, params);
    }


    private String resolveParkourDuos(OfflinePlayer offlinePlayer, String params) {
        var pd = plugin.getParkourDuosMiniGame();

        if (params.equals("pd_running")) return String.valueOf(pd.isRunning());

        if (params.equals("pd_state")) return switch (pd.getState()) {
            case IDLE      -> "En espera";
            case COUNTDOWN -> "Iniciando";
            case RUNNING   -> "En juego";
            case FINISHED  -> "Finalizado";
        };

        if (params.equals("pd_time")) {
            return pd.isRunning() ? pd.getTimeLeftFormatted() : "--:--";
        }

        if (params.equals("pd_time_seconds")) {
            return pd.isRunning() ? String.valueOf(pd.getTimeLeftSeconds()) : "0";
        }

        if (params.equals("pd_time_total_seconds")) {
            return String.valueOf(pd.getConfig().getDurationMinutes() * 60);
        }

        if (params.equals("pd_timepercent")) {
            if (!pd.isRunning()) return "0";
            int total = pd.getConfig().getDurationMinutes() * 60;
            if (total <= 0) return "0";
            int pct = (int) Math.round((pd.getTimeLeftSeconds() / (double) total) * 100);
            return String.valueOf(Math.max(0, Math.min(100, pct)));
        }

        if (params.startsWith("pd_top_")) {
            String[] parts = params.split("_");
            if (parts.length != 4) return "-";
            int pos;
            try { pos = Integer.parseInt(parts[2]); }
            catch (NumberFormatException e) { return "-"; }
            if (pos < 1 || pos > 8) return "-";
            if (!pd.isRunning()) return "-";

            var ranking = pd.getTeamData().values().stream()
                    .sorted((a, b) -> {
                        if (a.isFinished() && b.isFinished())
                            return Integer.compare(a.getFinishRank(), b.getFinishRank());
                        if (a.isFinished()) return -1;
                        if (b.isFinished()) return 1;
                        return Integer.compare(b.getCompletedCheckpoints(), a.getCompletedCheckpoints());
                    })
                    .toList();

            if (pos > ranking.size()) return "-";
            var entry = ranking.get(pos - 1);

            return switch (parts[3]) {
                case "name"     -> entry.getTeam().getDisplayName();
                case "color"    -> namedColorToLegacy(entry.getTeam().getColor().toString());
                case "cps"      -> entry.getCompletedCheckpoints() + "/" + entry.getTotalCheckpoints();
                case "finished" -> String.valueOf(entry.isFinished());
                case "score"    -> String.valueOf(entry.getInternalScore());
                default         -> "-";
            };
        }

        Player player = offlinePlayer.getPlayer();

        if (player == null || plugin.getTeamManager().getTeamOf(player).isEmpty()) {
            return switch (params) {
                case "pd_checkpoint", "pd_checkpoints", "pd_players_in_cp",
                     "pd_rank", "pd_score",
                     "pd_cd_return", "pd_cd_jump", "pd_cd_tp",
                     "pd_cd_return_pct", "pd_cd_jump_pct", "pd_cd_tp_pct",
                     "pd_jumpboost_seconds", "pd_jumpboost_pct" -> "0";
                case "pd_progress"   -> "-/-";
                case "pd_finished"   -> "false";
                case "pd_cd_return_ready", "pd_cd_jump_ready",
                     "pd_cd_tp_ready", "pd_jumpboost_active" -> "false";
                case "pd_status_return", "pd_status_jump",
                     "pd_status_tp" -> "Listo";
                default -> null;
            };
        }

        Optional<EventTeam> teamOpt = plugin.getTeamManager().getTeamOf(player);
        var data = pd.getDataFor(teamOpt.get());

        if (params.equals("pd_checkpoint") && data != null)
            return String.valueOf(data.getCompletedCheckpoints() + 1);

        if (params.equals("pd_checkpoints") && data != null)
            return String.valueOf(data.getTotalCheckpoints());

        if (params.equals("pd_checkpoints_remaining") && data != null) {
            int remaining = data.getTotalCheckpoints() - data.getCompletedCheckpoints();
            return String.valueOf(Math.max(0, remaining));
        }

        if (params.equals("pd_progress") && data != null)
            return (data.getCompletedCheckpoints() + 1) + "/" + data.getTotalCheckpoints();

        if (params.equals("pd_progress_pct") && data != null) {
            if (data.getTotalCheckpoints() == 0) return "0";
            int pct = (int) Math.round(data.getProgressFraction() * 100);
            return String.valueOf(pct);
        }

        if (params.equals("pd_players_in_cp") && data != null)
            return data.getPlayersInCurrentCheckpoint() + "/2";

        if (params.equals("pd_finished") && data != null)
            return String.valueOf(data.isFinished());

        if (params.equals("pd_rank") && data != null)
            return data.isFinished() ? "#" + data.getFinishRank() : "-";

        if (params.equals("pd_score") && data != null)
            return String.valueOf(data.getInternalScore());

        if (!pd.isRunning() || pd.getAbilityManager() == null) {
            return switch (params) {
                case "pd_cd_return", "pd_cd_jump", "pd_cd_tp",
                     "pd_cd_return_pct", "pd_cd_jump_pct", "pd_cd_tp_pct",
                     "pd_jumpboost_seconds", "pd_jumpboost_pct" -> "0";
                case "pd_cd_return_ready", "pd_cd_jump_ready",
                     "pd_cd_tp_ready", "pd_jumpboost_active" -> "true";
                case "pd_status_return", "pd_status_jump",
                     "pd_status_tp" -> "Listo";
                default -> null;
            };
        }

        var am = pd.getAbilityManager();

        return switch (params) {
            case "pd_cd_return"       -> String.valueOf(am.getCooldownLeft(player, HotbarAbility.RETURN_CHECKPOINT));
            case "pd_cd_jump"         -> String.valueOf(am.getCooldownLeft(player, HotbarAbility.JUMP_BOOST));
            case "pd_cd_tp"           -> String.valueOf(am.getCooldownLeft(player, HotbarAbility.TELEPORT_TO_TEAMMATE));
            case "pd_cd_return_pct"   -> String.valueOf(am.getCooldownPercent(player, HotbarAbility.RETURN_CHECKPOINT));
            case "pd_cd_jump_pct"     -> String.valueOf(am.getCooldownPercent(player, HotbarAbility.JUMP_BOOST));
            case "pd_cd_tp_pct"       -> String.valueOf(am.getCooldownPercent(player, HotbarAbility.TELEPORT_TO_TEAMMATE));
            case "pd_cd_return_ready" -> String.valueOf(!am.isOnCooldown(player, HotbarAbility.RETURN_CHECKPOINT));
            case "pd_cd_jump_ready"   -> String.valueOf(!am.isOnCooldown(player, HotbarAbility.JUMP_BOOST));
            case "pd_cd_tp_ready"     -> String.valueOf(!am.isOnCooldown(player, HotbarAbility.TELEPORT_TO_TEAMMATE));
            case "pd_jumpboost_active"  -> String.valueOf(am.getJumpBoostSecondsLeft(player) > 0);
            case "pd_jumpboost_seconds" -> String.valueOf(am.getJumpBoostSecondsLeft(player));
            case "pd_jumpboost_pct"     -> String.valueOf(am.getJumpBoostPercent(player));
            case "pd_status_return"     -> am.getAbilityStatus(player, HotbarAbility.RETURN_CHECKPOINT);
            case "pd_status_jump"       -> am.getAbilityStatus(player, HotbarAbility.JUMP_BOOST);
            case "pd_status_tp"         -> am.getAbilityStatus(player, HotbarAbility.TELEPORT_TO_TEAMMATE);
            default -> null;
        };
    }

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

    private String resolveFrozenHeist(OfflinePlayer offlinePlayer, String params) {
        var fh = plugin.getFrozenHeistMiniGame();

        if (params.equals("fh_running")) return String.valueOf(fh.isRunning());

        if (params.equals("fh_state")) return switch (fh.getState()) {
            case COUNTDOWN -> "Cuenta atras";
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
            case "fh_score", "fh_frozen_seconds",
                 "fh_base_member_count"          -> "0";
            case "fh_rank", "fh_flag_state", "fh_flag_carrier",
                 "fh_carrying_team"              -> "-";
            case "fh_carrying", "fh_frozen",
                 "fh_base_has_members"           -> "false";
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
                yield switch (state) {
                    case IN_BASE -> "En base";
                    case CARRIED -> "Robada";
                    case DROPPED -> "Caída";
                };
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

            case "fh_base_has_members" -> {
                if (!fh.isRunning() || teamOpt.isEmpty()) yield "false";
                var data = fh.getTeamData().get(teamOpt.get().getId());
                if (data == null) yield "false";
                boolean anyInBase = teamOpt.get().getOnlinePlayers().stream()
                        .anyMatch(p -> data.isInsideBase(p.getLocation()));
                yield String.valueOf(anyInBase);
            }

            case "fh_base_member_count" -> {
                if (!fh.isRunning() || teamOpt.isEmpty()) yield "0";
                var data = fh.getTeamData().get(teamOpt.get().getId());
                if (data == null) yield "0";
                long count = teamOpt.get().getOnlinePlayers().stream()
                        .filter(p -> data.isInsideBase(p.getLocation()))
                        .count();
                yield String.valueOf(count);
            }

            default -> null;
        };
    }

    private String resolveTeamById(String params) {
        String withoutPrefix = params.substring("teamid_".length());

        String matchedTeamId = null;
        String matchedField  = null;

        for (String id : plugin.getTeamManager().getTeamIds()) {
            String expectedPrefix = id + "_";
            if (withoutPrefix.startsWith(expectedPrefix)) {
                String candidate = withoutPrefix.substring(expectedPrefix.length());
                if (matchedTeamId == null || id.length() > matchedTeamId.length()) {
                    matchedTeamId = id;
                    matchedField  = candidate;
                }
            }
        }

        if (matchedTeamId == null || matchedField == null || matchedField.isEmpty()) return null;

        final String finalTeamId = matchedTeamId;
        final String field       = matchedField;
        Optional<EventTeam> teamOpt = plugin.getTeamManager().getTeam(finalTeamId);

        return switch (field) {
            case "name",    "fh_name"    -> teamOpt.map(EventTeam::getDisplayName).orElse("-");
            case "color",   "fh_color"   -> teamOpt.map(t -> namedColorToLegacy(t.getColor().toString())).orElse("&f");
            case "members", "fh_members" -> teamOpt.map(t -> {
                var names = t.getOnlinePlayers().stream().map(Player::getName).toList();
                return names.isEmpty() ? "ninguno" : String.join(", ", names);
            }).orElse("ninguno");
            case "size",    "fh_size"    -> teamOpt.map(t -> String.valueOf(t.getMemberCount())).orElse("0");
            case "score"                 -> teamOpt.map(t -> String.valueOf(plugin.getScoreManager().getScore(t))).orElse("0");
            case "rank"                  -> teamOpt.map(t -> {
                int rank = plugin.getScoreManager().getRank(t);
                return rank == -1 ? "-" : "#" + rank;
            }).orElse("-");

            case "fh_score" -> {
                var fh = plugin.getFrozenHeistMiniGame();
                if (!fh.isRunning()) yield "0";
                var data = fh.getTeamData().get(finalTeamId);
                yield data != null ? String.valueOf(data.getPoints()) : "0";
            }
            case "fh_flag" -> {
                var fh = plugin.getFrozenHeistMiniGame();
                if (!fh.isRunning()) yield "-";
                var flagState = fh.getFlagManager().getState(finalTeamId);
                yield switch (flagState) {
                    case IN_BASE -> "En base";
                    case CARRIED -> "Robada";
                    case DROPPED -> "Caída";
                };
            }
            case "fh_rank" -> {
                var fh = plugin.getFrozenHeistMiniGame();
                if (!fh.isRunning()) yield "-";
                var ranked = fh.getTeamData().values().stream()
                        .sorted((a, b) -> Integer.compare(b.getPoints(), a.getPoints()))
                        .toList();
                for (int i = 0; i < ranked.size(); i++)
                    if (ranked.get(i).getTeam().getId().equals(finalTeamId)) yield "#" + (i + 1);
                yield "-";
            }
            case "fh_base_has_members" -> {
                var fh = plugin.getFrozenHeistMiniGame();
                if (!fh.isRunning()) yield "false";
                var data = fh.getTeamData().get(finalTeamId);
                if (data == null) yield "false";
                Optional<EventTeam> t = plugin.getTeamManager().getTeam(finalTeamId);
                if (t.isEmpty()) yield "false";
                boolean any = t.get().getOnlinePlayers().stream()
                        .anyMatch(p -> data.isInsideBase(p.getLocation()));
                yield String.valueOf(any);
            }
            case "fh_base_member_count" -> {
                var fh = plugin.getFrozenHeistMiniGame();
                if (!fh.isRunning()) yield "0";
                var data = fh.getTeamData().get(finalTeamId);
                if (data == null) yield "0";
                Optional<EventTeam> t = plugin.getTeamManager().getTeam(finalTeamId);
                if (t.isEmpty()) yield "0";
                long count = t.get().getOnlinePlayers().stream()
                        .filter(p -> data.isInsideBase(p.getLocation()))
                        .count();
                yield String.valueOf(count);
            }

            default -> null;
        };
    }

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
        int totalBars  = 10;
        int filledBars = (int) Math.round((percent / 100.0) * totalBars);
        StringBuilder bar = new StringBuilder();
        String color = percent < 50 ? "&c" : percent < 80 ? "&e" : "&a";
        for (int i = 0; i < totalBars; i++)
            bar.append(i < filledBars ? color + "█" : "&8░");
        return bar + " &7" + percent + "%";
    }
}