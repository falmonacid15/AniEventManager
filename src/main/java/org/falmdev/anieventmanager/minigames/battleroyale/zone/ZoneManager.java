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

/**
 * ZoneManager — zona segura cuadrada usando WorldBorder virtuales per-player.
 *
 * Ventajas vs particles:
 *  - Renderizado nativo del cliente — pared roja característica de Minecraft
 *  - Sin lag de partículas, sin popping al girar la cámara
 *  - Animación de cierre suave nativa
 *  - Aviso visual (pantalla con borde rojo) automático cerca del borde
 *
 * Importante:
 *  - El WorldBorder virtual es solo VISUAL. El jugador puede caminar fuera.
 *  - El damage lo aplicamos manualmente desde Java (no del WorldBorder).
 *  - Cada jugador tiene su propia copia del border (pero todos apuntan al
 *    mismo cuadrado configurado para la zona).
 *
 * El WorldBorder vanilla usa un sistema donde:
 *  - center = punto medio del cuadrado
 *  - size = LONGITUD DEL LADO (no radio). Ej: size 80 = cuadrado de 80x80.
 *  - El border efectivo es: centerX ± size/2, centerZ ± size/2
 *
 * Nuestro "radius" interno equivale a size/2 (half-side).
 */
public class ZoneManager implements Listener {

    public enum State { IDLE, WAITING, SHRINKING }

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

    // Un WorldBorder virtual por jugador
    private final Map<UUID, WorldBorder> playerBorders = new HashMap<>();

    public ZoneManager(Anieventmanager plugin, BattleRoyaleMiniGame game) {
        this.plugin = plugin;
        this.game   = game;
        this.config = game.getConfig();
        // Registrar listener para join/quit
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ── API pública ───────────────────────────────────────────────────────────

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

    public String getStateLabel() {
        return switch (state) {
            case IDLE      -> "Inactiva";
            case WAITING   -> "En espera";
            case SHRINKING -> "Cerrando";
        };
    }

    // ── Inicio / parada ───────────────────────────────────────────────────────

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

        // Crear/asignar border a cada jugador del mundo de la zona
        applyBorderToAllPlayers();

        beginWaiting();

        mainTask   = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        damageTask = Bukkit.getScheduler().runTaskTimer(plugin, this::applyDamage, 20L, 20L);

        broadcast(Component.text("⚠ La zona segura está activa.", NamedTextColor.YELLOW));
    }

    public void stop() {
        if (mainTask   != null) { mainTask.cancel();   mainTask   = null; }
        if (damageTask != null) { damageTask.cancel(); damageTask = null; }

        // Quitar borders virtuales — restaurar al del mundo
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
        if (state == State.IDLE) return;
        if (state == State.WAITING) {
            beginShrinking();
            broadcast(Component.text("⚠ ¡La zona empezó a cerrarse anticipadamente!",
                    NamedTextColor.RED));
        } else if (state == State.SHRINKING) {
            // Forzar tamaño final inmediato
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
        // Aplicar tamaño instantáneo (sin animación)
        updateBorderSize(currentRadius * 2, 0);
        beginWaiting();
        broadcast(Component.text("⚠ Saltaste a la fase " + phaseNumber + ".",
                NamedTextColor.LIGHT_PURPLE));
        return true;
    }

    // ── WorldBorder helpers ───────────────────────────────────────────────────

    private void applyBorderToAllPlayers() {
        Location center = config.getZoneCenter();
        if (center == null) return;
        for (Player p : center.getWorld().getPlayers()) {
            assignBorder(p);
        }
    }

    /**
     * Crea o reasigna un WorldBorder al jugador.
     * Cada jugador tiene su propio border, pero todos representan el mismo
     * cuadrado lógico de la zona.
     */
    private void assignBorder(Player p) {
        Location center = config.getZoneCenter();
        if (center == null) return;

        WorldBorder wb = Bukkit.createWorldBorder();
        wb.setCenter(center.getX(), center.getZ());
        wb.setSize(currentRadius * 2); // side length = 2 × half-side
        wb.setWarningDistance(5);
        wb.setWarningTimeTicks(15 * 20);
        wb.setDamageBuffer(0); // sin buffer — damage manual lo gestionamos nosotros
        wb.setDamageAmount(0); // damage 0 del border — solo visual

        p.setWorldBorder(wb);
        playerBorders.put(p.getUniqueId(), wb);
    }

    /**
     * Actualiza el tamaño del border para todos los jugadores.
     * Si seconds > 0, anima el cierre suavemente (animación nativa del cliente).
     */
    /**
     * Actualiza el tamaño del border para todos los jugadores.
     * @param newSize tamaño final (side length, no radius)
     * @param ticks   ticks de animación. 0 = instantáneo.
     */
    private void updateBorderSize(double newSize, long ticks) {
        Location center = config.getZoneCenter();
        if (center == null) return;

        for (Map.Entry<UUID, WorldBorder> entry : playerBorders.entrySet()) {
            WorldBorder wb = entry.getValue();
            if (wb == null) continue;
            // Asegurar centro correcto antes de animar
            wb.setCenter(center.getX(), center.getZ());
            if (ticks > 0) {
                wb.changeSize(newSize, ticks);
            } else {
                wb.setSize(newSize);
            }
        }
    }

    // ── Tick principal ────────────────────────────────────────────────────────

    private void tick() {
        if (state == State.IDLE) return;
        subStateTicks--;

        if (state == State.SHRINKING) {
            // No actualizamos currentRadius cada tick — el WorldBorder lo hace
            // visualmente solo. Pero necesitamos saber el valor actual para
            // el daño. Interpolamos el valor lógico.
            ZonePhase phase = phases.get(currentPhaseIdx);
            int totalTicks = phase.shrinkSeconds() * 20;
            if (totalTicks > 0) {
                double progress = 1.0 - (subStateTicks / (double) totalTicks);
                progress = Math.max(0, Math.min(1, progress));
                // Interpolar entre el radio inicial y target
                // phaseTargetRadius es el target; el inicial era el radio
                // cuando empezó el shrink (capturado en beginShrinking).
                currentRadius = startShrinkRadius + (phaseTargetRadius - startShrinkRadius) * progress;
            }
        }

        if (subStateTicks <= 0) advance();
    }

    private double startShrinkRadius = 0;

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
        // No tocar el border — se queda en el tamaño actual
    }

    private void beginShrinking() {
        state = State.SHRINKING;
        ZonePhase phase = phases.get(currentPhaseIdx);
        startShrinkRadius = currentRadius;

        // Notificar al LootManager para que haga refill si esta fase lo dispara
        int humanPhase = currentPhaseIdx + 1;
        if (game.getLootManager() != null
                && game.getLootManager().getLootConfig().getRefillOnShrinkPhases().contains(humanPhase)) {
            var res = game.getLootManager().refill();
            broadcast(Component.text("📦 Refill de loot: " + res.filled() + " cofres rellenados.",
                    NamedTextColor.GREEN));
        }

        // Target = radio de la SIGUIENTE fase (donde se va a comprimir).
        // Si ya es la última fase, comprimir al mínimo razonable (1 bloque).
        int nextIdx = currentPhaseIdx + 1;
        phaseTargetRadius = (nextIdx < phases.size())
                ? phases.get(nextIdx).radius()
                : 1.0;

        subStateTicks = phase.shrinkSeconds() * 20;

        long shrinkTicks = phase.shrinkSeconds() * 20L;
        updateBorderSize(phaseTargetRadius * 2, shrinkTicks);

        plugin.getLogger().info(String.format(
                "[BR] Comprimiendo zona: %.1f -> %.1f en %ds (%d ticks)",
                startShrinkRadius, phaseTargetRadius, phase.shrinkSeconds(), shrinkTicks));
    }

    // ── Daño ──────────────────────────────────────────────────────────────────

    private void applyDamage() {
        if (state == State.IDLE) return;
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

    // ── Listeners ─────────────────────────────────────────────────────────────

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!isRunning()) return;
        Location center = config.getZoneCenter();
        if (center == null) return;
        Player p = event.getPlayer();
        if (p.getWorld() == center.getWorld()) {
            // Aplicar border al jugador que entra
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && isRunning()) assignBorder(p);
            }, 5L);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerBorders.remove(event.getPlayer().getUniqueId());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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