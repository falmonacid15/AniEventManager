package org.falmdev.anieventmanager.minigames.pvpfinal.combat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.minigames.pvpfinal.PvpFinalMiniGame;
import org.falmdev.anieventmanager.minigames.pvpfinal.arena.PvpArena;
import org.falmdev.anieventmanager.minigames.pvpfinal.kit.PvpKit;
import org.falmdev.anieventmanager.minigames.pvpfinal.model.CombatMode;
import org.falmdev.anieventmanager.minigames.pvpfinal.model.CombatState;

import java.time.Duration;
import java.util.*;

/**
 * CombatManager — orquesta el combate actual.
 *
 * Flujo:
 *  1. start()            → PREPARING: teleport, kit, congelar
 *  2. countdown          → COUNTDOWN: 5-4-3-2-1-Go
 *  3. tick               → FIGHTING: combate libre
 *  4. onParticipantDeath → al morir alguien, mover a SPECTATOR en el lobby
 *  5. detectar fin       → ENDING: mostrar resultado
 *  6. cleanup            → IDLE
 *
 * Solo puede haber UN combate activo a la vez.
 */
public class CombatManager {

    private static final int COUNTDOWN_SECONDS = 5;

    private final Anieventmanager  plugin;
    private final PvpFinalMiniGame game;

    private CombatState state           = CombatState.IDLE;
    private Combat     currentCombat    = null;
    private BukkitTask preparingTask    = null;
    private BukkitTask countdownTask    = null;

    public CombatManager(Anieventmanager plugin, PvpFinalMiniGame game) {
        this.plugin = plugin;
        this.game   = game;
    }

    // ── API ───────────────────────────────────────────────────────────────────

    public CombatState getState()         { return state; }
    public Combat      getCurrentCombat() { return currentCombat; }
    public boolean     isActive()         { return state != CombatState.IDLE; }

    /**
     * Inicia un combate con los parámetros dados.
     * @param mode  modo del combate
     * @param participants lista de jugadores
     * @param teamByPlayer  mapa UUID → teamId (null permitido para FFA puro)
     * @param kit  kit a aplicar a todos
     * @param friendlyFire  permitir daño entre compañeros
     * @return true si se pudo iniciar, false si ya hay un combate activo o falta config
     */
    public boolean startCombat(CombatMode mode, List<Player> participants,
                               Map<UUID, String> teamByPlayer, PvpKit kit,
                               boolean friendlyFire) {
        if (isActive()) return false;
        if (participants.size() < 2) return false;

        PvpArena arena = game.getArenaManager().getArena();
        if (arena == null || !arena.isReady()) return false;
        if (arena.getSpawnCount() < participants.size() && mode == CombatMode.ONE_VS_ONE) {
            return false;
        }

        currentCombat = new Combat(mode, participants, teamByPlayer, kit, friendlyFire);
        state = CombatState.PREPARING;

        // Asignar spawns
        assignSpawnsAndApplyKit(participants, arena, kit);

        // Anuncio inicial
        broadcast(Component.text("⚔ Combate ", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(mode.getLabel() + " ", NamedTextColor.YELLOW))
                .append(Component.text("iniciado · " + participants.size() + " jugadores",
                        NamedTextColor.GRAY)));

        // Pasar a countdown después de 1 segundo de preparación
        preparingTask = Bukkit.getScheduler().runTaskLater(plugin, this::startCountdown, 20L);
        return true;
    }

    /** Forzar fin de combate (admin). */
    public void stopCombat() {
        if (!isActive()) return;
        endCombat(null, "Combate cancelado por administrador.");
    }

    // ── Asignación de spawns y kit ────────────────────────────────────────────

    private void assignSpawnsAndApplyKit(List<Player> participants, PvpArena arena, PvpKit kit) {
        List<Location> spawns = arena.getSpawns();

        for (int i = 0; i < participants.size(); i++) {
            Player p = participants.get(i);
            Location spawn = spawns.get(i % spawns.size());

            p.teleport(spawn);
            p.setGameMode(GameMode.SURVIVAL);
            p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());
            p.setFoodLevel(20);
            p.setSaturation(20);
            p.setFireTicks(0);
            for (var effect : p.getActivePotionEffects()) p.removePotionEffect(effect.getType());

            // Aplicar kit
            game.getKitManager().apply(kit, p);
        }
    }

    // ── Countdown ─────────────────────────────────────────────────────────────

    private void startCountdown() {
        state = CombatState.COUNTDOWN;
        final int[] seconds = {COUNTDOWN_SECONDS};

        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (currentCombat == null) {
                countdownTask.cancel();
                return;
            }
            if (seconds[0] > 0) {
                Title title = Title.title(
                        Component.text(String.valueOf(seconds[0]),
                                NamedTextColor.YELLOW, TextDecoration.BOLD),
                        Component.text("Preparate...", NamedTextColor.GRAY),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100)));
                for (UUID uuid : currentCombat.getParticipants()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        p.showTitle(title);
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    }
                }
                seconds[0]--;
            } else {
                // ¡GO!
                Title goTitle = Title.title(
                        Component.text("¡PELEEN!", NamedTextColor.RED, TextDecoration.BOLD),
                        Component.empty(),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(800), Duration.ofMillis(200)));
                for (UUID uuid : currentCombat.getParticipants()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        p.showTitle(goTitle);
                        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
                    }
                }
                state = CombatState.FIGHTING;
                countdownTask.cancel();
                countdownTask = null;
            }
        }, 0L, 20L);
    }

    // ── Muerte de un participante ─────────────────────────────────────────────

    /**
     * Llamado desde CombatListener cuando un participante muere.
     * Lo mueve al lobby como espectador y verifica fin de combate.
     */
    public void onParticipantDeath(Player victim, Player killer) {
        if (currentCombat == null) return;
        if (!currentCombat.isAlive(victim.getUniqueId())) return;

        currentCombat.markDead(victim.getUniqueId());

        // Anuncio
        Component msg;
        if (killer != null && !killer.equals(victim)) {
            msg = Component.text("☠ ", NamedTextColor.RED)
                    .append(Component.text(victim.getName(), NamedTextColor.WHITE))
                    .append(Component.text(" eliminado por ", NamedTextColor.GRAY))
                    .append(Component.text(killer.getName(), NamedTextColor.YELLOW));
        } else {
            msg = Component.text("☠ ", NamedTextColor.RED)
                    .append(Component.text(victim.getName() + " eliminado.", NamedTextColor.WHITE));
        }
        broadcast(msg);

        // Forzar respawn y mandar al lobby como spectator
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!victim.isOnline()) return;
            victim.spigot().respawn();
            PvpArena arena = game.getArenaManager().getArena();
            if (arena != null && arena.getLobby() != null) {
                victim.teleport(arena.getLobby());
            }
            victim.setGameMode(GameMode.SPECTATOR);
            game.getKitManager().clearInventory(victim);
            victim.setHealth(victim.getAttribute(Attribute.MAX_HEALTH).getValue());
            victim.setFoodLevel(20);
        });

        // Verificar fin de combate
        checkWinCondition();
    }

    private void checkWinCondition() {
        if (currentCombat == null) return;

        // Para FFA: gana el último jugador vivo
        // Para modos con equipos: gana cuando queda solo 1 equipo con vivos
        int aliveTeams = currentCombat.countAliveTeams();
        if (aliveTeams <= 1) {
            // Determinar ganador
            String winnerInfo;
            Set<UUID> survivors = currentCombat.getAlive();
            if (survivors.isEmpty()) {
                winnerInfo = "Sin ganadores (todos murieron)";
            } else if (currentCombat.getMode() == CombatMode.ONE_VS_ONE
                    || currentCombat.getMode() == CombatMode.FFA) {
                List<String> names = new ArrayList<>();
                for (UUID uuid : survivors) {
                    Player p = Bukkit.getPlayer(uuid);
                    names.add(p != null ? p.getName() : uuid.toString().substring(0, 8));
                }
                winnerInfo = String.join(", ", names);
            } else {
                // Team-based: mostrar el equipo ganador
                Set<String> aliveTeamIds = currentCombat.getAliveTeamIds();
                if (aliveTeamIds.isEmpty()) {
                    winnerInfo = "Sin ganadores";
                } else {
                    String teamId = aliveTeamIds.iterator().next();
                    var teamOpt = plugin.getTeamManager().getTeam(teamId);
                    String teamName = teamOpt.map(t -> t.getDisplayName()).orElse(teamId);

                    // Listar sobrevivientes del equipo ganador
                    List<String> names = new ArrayList<>();
                    for (UUID uuid : survivors) {
                        Player p = Bukkit.getPlayer(uuid);
                        names.add(p != null ? p.getName() : uuid.toString().substring(0, 8));
                    }
                    winnerInfo = teamName + " (" + String.join(", ", names) + ")";
                }
            }
            endCombat(winnerInfo, null);
        }
    }

    // ── Fin de combate ────────────────────────────────────────────────────────

    private void endCombat(String winnerInfo, String cancelReason) {
        state = CombatState.ENDING;
        if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
        if (preparingTask != null) { preparingTask.cancel(); preparingTask = null; }

        if (cancelReason != null) {
            broadcast(Component.text("⚠ " + cancelReason, NamedTextColor.YELLOW));
        } else if (winnerInfo != null) {
            Title title = Title.title(
                    Component.text("¡GANADOR!", NamedTextColor.GOLD, TextDecoration.BOLD),
                    Component.text(winnerInfo, NamedTextColor.YELLOW),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(500)));
            for (Player p : Bukkit.getOnlinePlayers()) p.showTitle(title);
            broadcast(Component.text("🏆 ", NamedTextColor.GOLD)
                    .append(Component.text("Ganador: ", NamedTextColor.GRAY))
                    .append(Component.text(winnerInfo, NamedTextColor.GOLD, TextDecoration.BOLD)));
        }

        // Limpiar a todos los participantes (vivos y muertos) → lobby, sin kit, SURVIVAL
        PvpArena arena = game.getArenaManager().getArena();
        if (currentCombat != null && arena != null && arena.getLobby() != null) {
            for (UUID uuid : currentCombat.getParticipants()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline()) continue;
                p.teleport(arena.getLobby());
                p.setGameMode(GameMode.SURVIVAL);
                game.getKitManager().clearInventory(p);
                p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());
                p.setFoodLevel(20);
                for (var effect : p.getActivePotionEffects()) p.removePotionEffect(effect.getType());
            }
        }

        // Resetear después de un par de segundos para que se aprecie el resultado
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            currentCombat = null;
            state = CombatState.IDLE;
        }, 40L);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void broadcast(Component msg) {
        for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(msg);
    }
}