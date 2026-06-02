package org.falmdev.anieventmanager.minigames.pvpfinal;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.minigames.pvpfinal.combat.Combat;

import java.util.UUID;

/**
 * Placeholders %anievent_pvpfinal_...%
 *
 *  state, running, arena_ready, kits_count
 *  combat_mode, combat_alive, combat_total, combat_time
 *  fighter_1, fighter_2   (solo en 1v1)
 *  player_alive, player_state
 */
public class PvpFinalPlaceholders {

    private final Anieventmanager  plugin;
    private final PvpFinalMiniGame game;

    public PvpFinalPlaceholders(Anieventmanager plugin, PvpFinalMiniGame game) {
        this.plugin = plugin;
        this.game   = game;
    }

    public String resolve(OfflinePlayer offlinePlayer, String params) {
        switch (params) {
            case "state"       -> { return game.getCombatManager().getState().name(); }
            case "running"     -> { return String.valueOf(game.getCombatManager().isActive()); }
            case "arena_ready" -> {
                var a = game.getArenaManager().getArena();
                return String.valueOf(a != null && a.isReady());
            }
            case "kits_count"  -> { return String.valueOf(game.getKitManager().count()); }
        }

        Combat c = game.getCombatManager().getCurrentCombat();
        if (params.startsWith("combat_") || params.startsWith("fighter_")) {
            if (c == null) {
                return switch (params) {
                    case "combat_mode", "fighter_1", "fighter_2" -> "-";
                    case "combat_alive", "combat_total", "combat_time" -> "0";
                    default -> null;
                };
            }
            return switch (params) {
                case "combat_mode"  -> c.getMode().getLabel();
                case "combat_alive" -> String.valueOf(c.getAlive().size());
                case "combat_total" -> String.valueOf(c.getParticipants().size());
                case "combat_time"  -> String.valueOf(c.getElapsedSeconds());
                case "fighter_1" -> {
                    if (c.getParticipants().size() < 1) yield "-";
                    Player p = Bukkit.getPlayer(c.getParticipants().get(0));
                    yield p != null ? p.getName() : "-";
                }
                case "fighter_2" -> {
                    if (c.getParticipants().size() < 2) yield "-";
                    Player p = Bukkit.getPlayer(c.getParticipants().get(1));
                    yield p != null ? p.getName() : "-";
                }
                default -> null;
            };
        }

        if (params.startsWith("player_")) {
            Player player = offlinePlayer.getPlayer();
            if (player == null || c == null) {
                return switch (params) {
                    case "player_alive" -> "false";
                    case "player_state" -> "-";
                    default -> null;
                };
            }
            UUID uuid = player.getUniqueId();
            if (!c.isParticipant(uuid)) {
                return switch (params) {
                    case "player_alive" -> "false";
                    case "player_state" -> "No participa";
                    default -> null;
                };
            }
            return switch (params) {
                case "player_alive" -> String.valueOf(c.isAlive(uuid));
                case "player_state" -> c.isAlive(uuid) ? "Vivo" : "Eliminado";
                default -> null;
            };
        }
        return null;
    }
}