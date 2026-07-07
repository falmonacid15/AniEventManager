package org.falmdev.anieventmanager.minigames.battleroyale.zone;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.minigames.battleroyale.BattleRoyaleConfig;
import org.falmdev.anieventmanager.minigames.battleroyale.BattleRoyaleMiniGame;
import org.falmdev.anieventmanager.minigames.battleroyale.model.BRPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ZoneManager implements Listener {

    public enum State { IDLE, PENDING, WAITING, SHRINKING }

    private final Anieventmanager      plugin;
    private final BattleRoyaleConfig   config;
    private final BattleRoyaleMiniGame game;

    private State           state           = State.IDLE;
    private List<ZonePhase> phases          = null;
    private int             currentPhaseIdx = 0;

    private double currentRadius     = 0;
    private double phaseTargetRadius = 0;

    private int subStateTicks = 0;

    private BukkitTask mainTask    = null;
    private BukkitTask damageTask  = null;

    private final Map<UUID, WorldBorder> playerBorders = new HashMap<>();

    public ZoneManager(Anieventmanager plugin, BattleRoyaleMiniGame game) {
        this.plugin = plugin;
        this.game   = game;
        this.config = game.getConfig();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public State   getState()         { return state; }
    public double  getCurrentRadius() { return currentRadius; }
    public int     getCurrentPhase()  { return currentPhaseIdx + 1; }
    public int     getTotalPhases()   { return phases != null ? phases.size() : 0; }
    public int     getSecondsLeft()   { return subStateTicks / 20; }
    public boolean isRunning()        { return state != State.IDLE; }

    public ZonePhase getCurrentPhaseData() {
        if (phases == null || currentPhaseIdx >= phases.size()) return null;
        return phases.get(currentPhaseIdx);
    }

    public int getSubStateTotalSeconds() {
        ZonePhase phase = getCurrentPhaseData();
        if (phase == null) return 0;
        return switch (state) {
            case WAITING   -> phase.waitSeconds();
            case SHRINKING -> phase.shrinkSeconds();
            default        -> 0;
        };
    }

    public double getSubStatePercentLeft() {
        int total = getSubStateTotalSeconds();
        if (total <= 0) return 0;
        double secondsLeft = Math.max(0, Math.min(total, getSecondsLeft()));
        return (secondsLeft / total) * 100.0;
    }

    public String getStateLabel() {
        return switch (state) {
            case IDLE      -> "Inactiva";
            case PENDING   -> "Visible (esperando aterrizajes)";
            case WAITING   -> "En espera";
            case SHRINKING -> "Cerrando";
        };
    }

    public void start() {
        if (state != State.IDLE) stop();

        this.phases = config.getZonePhases().stream()
                .map(p -> new ZonePhase(p.radius(), p.waitSeconds(), p.shrinkSeconds(), p.damagePerSecond()))
                .toList();

        if (phases.isEmpty()) {
            plugin.getLogger().warning("[BR] ZoneManager: sin fases o arena sin pos1/pos2.");
            return;
        }

        Location center = config.getZoneCenter();
        if (center == null) {
            plugin.getLogger().warning("[BR] ZoneManager: arena pos1/pos2 no configurada.");
            return;
        }

        plugin.getLogger().info(String.format(
                "[BR] Zona iniciada en (%.0f, %.0f) — radio máx %.1f",
                center.getX(), center.getZ(), config.getZoneMaxRadius()));
        for (int i = 0; i < phases.size(); i++) {
            ZonePhase p = phases.get(i);
            plugin.getLogger().info(String.format(
                    "[BR]   Fase %d: r=%.1f wait=%ds shrink=%ds dmg=%.1f/s",
                    i + 1, p.radius(), p.waitSeconds(), p.shrinkSeconds(), p.damagePerSecond()));
        }

        currentPhaseIdx   = 0;
        currentRadius     = phases.get(0).radius();
        phaseTargetRadius = currentRadius;

        applyBorderToAllPlayers();

        state = State.PENDING;
        subStateTicks = 0;

        mainTask   = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        damageTask = Bukkit.getScheduler().runTaskTimer(plugin, this::applyDamage, 20L, 20L);

        broadcast(Component.text("⚠ La zona segura ya es visible.", NamedTextColor.YELLOW));
    }

    public void beginCountdown() {
        if (state != State.PENDING) return;
        beginWaiting();
        broadcast(Component.text("⚠ La zona segura está activa. Comienza la cuenta para el primer cierre.",
                NamedTextColor.YELLOW));
    }

    public void stop() {
        if (mainTask   != null) { mainTask.cancel();   mainTask   = null; }
        if (damageTask != null) { damageTask.cancel(); damageTask = null; }

        for (UUID uuid : playerBorders.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) p.setWorldBorder(null);
        }
        playerBorders.clear();

        state = State.IDLE;
        phases = null;
        currentPhaseIdx = 0;
    }

    public void forceShrink() {
        if (state == State.IDLE || state == State.PENDING) return;
        if (state == State.WAITING) {
            beginShrinking();
            broadcast(Component.text("⚠ ¡La zona empezó a cerrarse anticipadamente!",
                    NamedTextColor.RED));
        } else if (state == State.SHRINKING) {
            currentRadius = phaseTargetRadius;
            updateBorderSize(currentRadius * 2, 0);
            subStateTicks = 0;
        }
    }

    public boolean skipToPhase(int phaseNumber) {
        if (phases == null) return false;
        int idx = phaseNumber - 1;
        if (idx < 0 || idx >= phases.size()) return false;
        currentPhaseIdx   = idx;
        currentRadius     = phases.get(idx).radius();
        phaseTargetRadius = currentRadius;
        updateBorderSize(currentRadius * 2, 0);
        beginWaiting();
        broadcast(Component.text("⚠ Saltaste a la fase " + phaseNumber + ".",
                NamedTextColor.LIGHT_PURPLE));
        return true;
    }

    private void applyBorderToAllPlayers() {
        Location center = config.getZoneCenter();
        if (center == null) return;
        for (Player p : center.getWorld().getPlayers()) {
            assignBorder(p);
        }
    }

    private void assignBorder(Player p) {
        Location center = config.getZoneCenter();
        if (center == null) return;

        WorldBorder wb = Bukkit.createWorldBorder();
        wb.setCenter(center.getX(), center.getZ());
        wb.setSize(currentRadius * 2);
        wb.setWarningDistance(5);
        wb.setWarningTimeTicks(15 * 20);
        wb.setDamageBuffer(0);
        wb.setDamageAmount(0);

        p.setWorldBorder(wb);
        playerBorders.put(p.getUniqueId(), wb);
    }

    private void updateBorderSize(double newSize, long ticks) {
        Location center = config.getZoneCenter();
        if (center == null) return;

        for (Map.Entry<UUID, WorldBorder> entry : playerBorders.entrySet()) {
            WorldBorder wb = entry.getValue();
            if (wb == null) continue;
            wb.setCenter(center.getX(), center.getZ());
            if (ticks > 0) {
                wb.changeSize(newSize, ticks);
            } else {
                wb.setSize(newSize);
            }
        }
    }

    private void tick() {
        if (state == State.IDLE || state == State.PENDING) return;
        subStateTicks--;

        if (state == State.SHRINKING) {
            if (currentShrinkTotalTicks > 0) {
                double progress = 1.0 - (subStateTicks / (double) currentShrinkTotalTicks);
                progress = Math.max(0, Math.min(1, progress));
                currentRadius = startShrinkRadius + (phaseTargetRadius - startShrinkRadius) * progress;
            }
        }

        if (subStateTicks <= 0) advance();
    }

    private static final int FINAL_CRUNCH_FALLBACK_SECONDS = 20;

    private double startShrinkRadius     = 0;
    private int    currentShrinkTotalTicks = 0;

    private void advance() {
        if (state == State.WAITING) {
            beginShrinking();
            broadcast(Component.text("⚠ ¡La zona empezó a cerrarse!",
                    NamedTextColor.RED, TextDecoration.BOLD));
            playSoundAll(Sound.ENTITY_WITHER_AMBIENT, 0.8f, 1.2f);
        } else if (state == State.SHRINKING) {
            currentRadius = phaseTargetRadius;
            if (currentPhaseIdx + 1 >= phases.size()) {
                state = State.WAITING;
                subStateTicks = Integer.MAX_VALUE;
                broadcast(Component.text("⚠ La zona alcanzó su tamaño mínimo.",
                        NamedTextColor.DARK_RED, TextDecoration.BOLD));
                return;
            }
            currentPhaseIdx++;
            beginWaiting();
            broadcast(Component.text("✔ Zona cerrada. Próximo cierre en " +
                    phases.get(currentPhaseIdx).waitSeconds() + "s.", NamedTextColor.YELLOW));
        }
    }

    private void beginWaiting() {
        state = State.WAITING;
        ZonePhase phase = phases.get(currentPhaseIdx);
        subStateTicks = phase.waitSeconds() * 20;
    }

    private void beginShrinking() {
        state = State.SHRINKING;
        ZonePhase phase = phases.get(currentPhaseIdx);
        startShrinkRadius = currentRadius;

        int humanPhase = currentPhaseIdx + 1;
        if (game.getLootManager() != null
                && game.getLootManager().getLootConfig().getRefillOnShrinkPhases().contains(humanPhase)) {
            var res = game.getLootManager().refill();
            broadcast(Component.text("📦 Refill de loot: " + res.filled() + " cofres rellenados.",
                    NamedTextColor.GREEN));
        }

        int nextIdx = currentPhaseIdx + 1;
        boolean isFinalCrunch = nextIdx >= phases.size();
        phaseTargetRadius = isFinalCrunch
                ? org.falmdev.anieventmanager.minigames.battleroyale.BattleRoyaleConfig.ZONE_MIN_RADIUS
                : phases.get(nextIdx).radius();

        int shrinkSeconds = phase.shrinkSeconds();
        if (isFinalCrunch && shrinkSeconds <= 0) shrinkSeconds = FINAL_CRUNCH_FALLBACK_SECONDS;

        subStateTicks = shrinkSeconds * 20;
        currentShrinkTotalTicks = subStateTicks;

        long shrinkTicks = shrinkSeconds * 20L;
        updateBorderSize(phaseTargetRadius * 2, shrinkTicks);

        plugin.getLogger().info(String.format(
                "[BR] Comprimiendo zona: %.1f -> %.1f en %ds (%d ticks)",
                startShrinkRadius, phaseTargetRadius, shrinkSeconds, shrinkTicks));
    }

    private void applyDamage() {
        if (state == State.IDLE || state == State.PENDING) return;
        Location center = config.getZoneCenter();
        if (center == null) return;

        ZonePhase phase = getCurrentPhaseData();
        if (phase == null) return;

        double dmg = phase.damagePerSecond();
        if (dmg <= 0) return;

        for (BRPlayer brp : game.getAllPlayers().values()) {
            if (!brp.isAlive() || brp.isOnDragon() || brp.isParachuting()) continue;
            Player p = Bukkit.getPlayer(brp.getUuid());
            if (p == null || !p.isOnline()) continue;
            if (p.getWorld() != center.getWorld()) continue;

            if (isOutsideZone(p.getLocation(), center, currentRadius)) {
                p.damage(dmg);
                p.sendActionBar(Component.text(
                        String.format("⚠ Fuera de zona  ·  -%.1f HP/s", dmg),
                        NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            }
        }
    }

    public boolean isOutsideZone(Location loc, Location center, double radius) {
        double dx = Math.abs(loc.getX() - center.getX());
        double dz = Math.abs(loc.getZ() - center.getZ());
        return dx > radius || dz > radius;
    }

    public boolean isOutsideZone(Location loc) {
        Location center = config.getZoneCenter();
        if (center == null) return false;
        return isOutsideZone(loc, center, currentRadius);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!isRunning()) return;
        Location center = config.getZoneCenter();
        if (center == null) return;
        Player p = event.getPlayer();
        if (p.getWorld() == center.getWorld()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && isRunning()) assignBorder(p);
            }, 5L);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerBorders.remove(event.getPlayer().getUniqueId());
    }

    private void broadcast(Component msg) {
        Location center = config.getZoneCenter();
        if (center == null) return;
        for (Player p : center.getWorld().getPlayers()) p.sendMessage(msg);
    }

    private void playSoundAll(Sound sound, float volume, float pitch) {
        Location center = config.getZoneCenter();
        if (center == null) return;
        for (Player p : center.getWorld().getPlayers())
            p.playSound(p.getLocation(), sound, volume, pitch);
    }
}