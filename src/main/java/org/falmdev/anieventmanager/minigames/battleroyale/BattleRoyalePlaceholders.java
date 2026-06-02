package org.falmdev.anieventmanager.minigames.battleroyale;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.minigames.battleroyale.model.BRPlayer;
import org.falmdev.anieventmanager.minigames.battleroyale.zone.ZoneManager;
import org.falmdev.anieventmanager.minigames.battleroyale.zone.ZonePhase;

/**
 * Placeholders %anievent_battleroyale_...%
 *
 * Estado:
 *   state, running, players, players_alive, teams_alive
 *
 * Zona:
 *   zone_state, zone_radius, zone_phase, zone_phases_total,
 *   zone_next_time, zone_damage
 *
 * Jugador:
 *   player_kills, player_money, player_alive, player_state, player_in_zone
 *
 * Ranking de monedas (top 1-10):
 *   top_money_1_name, top_money_1_amount
 *   ... top_money_10_name, top_money_10_amount
 */
public class BattleRoyalePlaceholders {

    private final Anieventmanager      plugin;
    private final BattleRoyaleMiniGame game;

    public BattleRoyalePlaceholders(Anieventmanager plugin, BattleRoyaleMiniGame game) {
        this.plugin = plugin;
        this.game   = game;
    }

    public String resolve(OfflinePlayer offlinePlayer, String params) {

        // ── Generales ─────────────────────────────────────────────────────────
        switch (params) {
            case "state" -> {
                return switch (game.getState()) {
                    case IDLE     -> "En espera";
                    case WAITING  -> "Esperando jugadores";
                    case STARTING -> "Iniciando";
                    case DROPPING -> "Drop";
                    case IN_GAME  -> "En juego";
                    case ENDING   -> "Finalizando";
                };
            }
            case "running"       -> { return String.valueOf(game.isRunning()); }
            case "players"       -> { return String.valueOf(game.getAllPlayers().size()); }
            case "players_alive" -> { return String.valueOf(game.getAlivePlayers()); }
            case "teams_alive"   -> {
                long alive = game.getAllTeams().values().stream()
                        .filter(bt -> bt.isAlive(new java.util.ArrayList<>(game.getAllPlayers().values())))
                        .count();
                return String.valueOf(alive);
            }
        }

        // ── Top monedas ───────────────────────────────────────────────────────
        // top_money_N_name | top_money_N_amount
        if (params.startsWith("top_money_")) {
            String rest = params.substring("top_money_".length());
            int underscore = rest.indexOf('_');
            if (underscore <= 0) return null;
            int pos;
            try { pos = Integer.parseInt(rest.substring(0, underscore)); }
            catch (NumberFormatException e) { return null; }
            String field = rest.substring(underscore + 1);

            BRPlayer brp = game.getCoinManager().getRankingAt(pos);
            if (brp == null) {
                return switch (field) {
                    case "name"   -> "-";
                    case "amount" -> "0";
                    default       -> null;
                };
            }
            return switch (field) {
                case "name"   -> brp.getName();
                case "amount" -> String.valueOf(brp.getCoins());
                default       -> null;
            };
        }

        // ── Zona ──────────────────────────────────────────────────────────────
        if (params.startsWith("zone_")) {
            ZoneManager zm = game.getZoneManager();
            return switch (params) {
                case "zone_state" -> zm.getStateLabel();
                case "zone_radius" -> zm.isRunning()
                        ? String.format("%.0f", zm.getCurrentRadius()) : "-";
                case "zone_phase" -> zm.isRunning()
                        ? String.valueOf(zm.getCurrentPhase()) : "-";
                case "zone_phases_total" -> String.valueOf(
                        game.getConfig().getZonePhases().size());
                case "zone_next_time" -> zm.isRunning()
                        ? String.valueOf(zm.getSecondsLeft()) : "-";
                case "zone_damage" -> {
                    if (!zm.isRunning()) yield "0";
                    ZonePhase ph = zm.getCurrentPhaseData();
                    yield ph != null ? String.valueOf(ph.damagePerSecond()) : "0";
                }
                default -> null;
            };
        }

        // ── Jugador ───────────────────────────────────────────────────────────
        if (params.startsWith("player_")) {
            Player player = offlinePlayer.getPlayer();
            if (player == null) {
                return switch (params) {
                    case "player_kills"   -> "0";
                    case "player_money"   -> "0";
                    case "player_alive"   -> "false";
                    case "player_state"   -> "-";
                    case "player_in_zone" -> "true";
                    default -> null;
                };
            }

            BRPlayer brp = game.getBRPlayer(player);
            if (brp == null) {
                return switch (params) {
                    case "player_kills"   -> "0";
                    case "player_money"   -> "0";
                    case "player_alive"   -> "false";
                    case "player_state"   -> "No registrado";
                    case "player_in_zone" -> "true";
                    default -> null;
                };
            }

            return switch (params) {
                case "player_kills" -> String.valueOf(brp.getKills());
                case "player_money" -> String.valueOf(brp.getCoins());
                case "player_alive" -> String.valueOf(brp.isAlive());
                case "player_state" -> switch (brp.getState()) {
                    case WAITING     -> "Esperando";
                    case ON_DRAGON   -> "En avión";
                    case PARACHUTING -> "Planeando";
                    case ALIVE       -> "Vivo";
                    case DEAD        -> "Eliminado";
                    case SPECTATING  -> "Espectador";
                };
                case "player_in_zone" -> {
                    if (!game.getZoneManager().isRunning()) yield "true";
                    yield String.valueOf(!game.getZoneManager().isOutsideZone(player.getLocation()));
                }
                default -> null;
            };
        }

        return null;
    }
}