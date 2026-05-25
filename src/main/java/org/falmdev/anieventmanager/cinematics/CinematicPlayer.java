package org.falmdev.anieventmanager.cinematics;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.cinematics.model.Cinematic;
import org.falmdev.anieventmanager.cinematics.model.CinematicWaypoint;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.*;

/**
 * Reproductor de cinematicas.
 *
 * Filosofía: reproducir exactamente lo que se grabó con el magic stick.
 * La posición y rotación en cada tick se calculan con interpolación lineal
 * simple entre el waypoint anterior y el siguiente, usando el tickOffset
 * de cada uno como referencia de tiempo.
 *
 * Smoothness: se usa connection.teleport() vía reflection para enviar
 * posiciones sin el handshake de confirmación de Player#teleport().
 * El cliente de Minecraft recibe las posiciones y las interpola a su
 * propio framerate (60fps), produciendo movimiento suave.
 *
 * Sin spline, sin easing, sin substeps — la ruta es exactamente la grabada.
 */
public class CinematicPlayer {

    private final Anieventmanager plugin;
    private final CinematicEffects effects;

    private BukkitTask task;
    private Cinematic  current;
    private Runnable   onFinish;

    private final Map<UUID, GameMode> savedGameModes = new HashMap<>();
    private final Map<UUID, Location> savedLocations = new HashMap<>();

    // Reflection cache — se resuelve una vez al primer uso
    private Method  methodGetHandle   = null;
    private Method  methodTeleportNMS = null;
    private Field   fieldConnection   = null;
    private boolean reflectionFailed  = false;

    public CinematicPlayer(Anieventmanager plugin, CinematicEffects effects) {
        this.plugin  = plugin;
        this.effects = effects;
    }

    // ── API pública ───────────────────────────────────────────────────────────

    public boolean isPlaying() { return task != null; }

    public Optional<Cinematic> getCurrentCinematic() {
        return Optional.ofNullable(current);
    }

    public void play(Cinematic cinematic, Runnable onFinish) {
        if (task != null) stop();

        this.current  = cinematic;
        this.onFinish = onFinish;

        List<CinematicWaypoint> waypoints = new ArrayList<>(cinematic.getWaypoints());
        int totalTicks = cinematic.getTotalTicks();

        // Preparar audiencia
        List<Player> audience = getAudience();
        savedGameModes.clear();
        savedLocations.clear();

        Location start = waypoints.get(0).getLocation();
        for (Player p : audience) {
            savedGameModes.put(p.getUniqueId(), p.getGameMode());
            savedLocations.put(p.getUniqueId(), p.getLocation().clone());
            p.setGameMode(GameMode.SPECTATOR);
            p.teleport(start);
        }

        effects.applyAll(audience);

        int[] tick = { 0 };
        Set<Integer> shownTitles = new HashSet<>();

        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int currentTick = tick[0]++;
            List<Player> live = getAudience();

            // ── Calcular posición lineal en este tick ─────────────────────────
            Location pos = lerpPosition(waypoints, currentTick, totalTicks);
            for (Player p : live) {
                sendPositionSmooth(p, pos);
            }

            // ── Mostrar textos en waypoints exactos ───────────────────────────
            for (int i = 0; i < waypoints.size(); i++) {
                CinematicWaypoint wp = waypoints.get(i);
                if (wp.getTickOffset() == currentTick
                        && !shownTitles.contains(i)
                        && wp.hasText()) {
                    shownTitles.add(i);
                    showText(live, wp);
                }
            }

            if (currentTick >= totalTicks) finish();

        }, 0L, 1L);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
        restoreAudience();
        if (onFinish != null) { onFinish.run(); onFinish = null; }
        current = null;
    }

    // ── Interpolación lineal ──────────────────────────────────────────────────

    /**
     * Calcula la posición en el tick dado usando interpolación lineal simple
     * entre el waypoint anterior y el siguiente.
     *
     * Esto reproduce exactamente la ruta grabada: si grabaste un giro a la
     * izquierda, la cámara gira a la izquierda; si grabaste recto, va recto.
     * La velocidad entre waypoints es constante (no hay aceleración/frenado).
     */
    private Location lerpPosition(List<CinematicWaypoint> waypoints,
                                  int tick, int totalTicks) {
        int size = waypoints.size();

        // Límites
        if (tick <= waypoints.get(0).getTickOffset()) {
            return waypoints.get(0).getLocation().clone();
        }
        if (tick >= waypoints.get(size - 1).getTickOffset()) {
            return waypoints.get(size - 1).getLocation().clone();
        }

        // Encontrar segmento activo: wp1 ≤ tick < wp2
        int segIndex = 0;
        for (int i = 0; i < size - 1; i++) {
            if (tick >= waypoints.get(i).getTickOffset()
                    && tick < waypoints.get(i + 1).getTickOffset()) {
                segIndex = i;
                break;
            }
        }

        CinematicWaypoint wp1 = waypoints.get(segIndex);
        CinematicWaypoint wp2 = waypoints.get(segIndex + 1);

        int segStart = wp1.getTickOffset();
        int segEnd   = wp2.getTickOffset();

        // t ∈ [0.0, 1.0] dentro de este segmento
        double t = (segEnd == segStart) ? 0.0
                : (double)(tick - segStart) / (double)(segEnd - segStart);
        t = Math.max(0.0, Math.min(1.0, t));

        Location a = wp1.getLocation();
        Location b = wp2.getLocation();

        double x = lerp(a.getX(), b.getX(), t);
        double y = lerp(a.getY(), b.getY(), t);
        double z = lerp(a.getZ(), b.getZ(), t);

        // Yaw con wrap-around para evitar rotaciones largas al cruzar ±180°
        float yaw   = lerpAngle(a.getYaw(),   b.getYaw(),   (float) t);
        float pitch = lerpAngle(a.getPitch(), b.getPitch(), (float) t);

        return new Location(a.getWorld(), x, y, z, yaw, pitch);
    }

    private double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /**
     * Lerp de ángulo con wrap-around correcto.
     * Evita que la cámara gire 350° cuando debería girar -10°.
     */
    private float lerpAngle(float a, float b, float t) {
        float diff = b - a;
        while (diff >  180f) diff -= 360f;
        while (diff < -180f) diff += 360f;
        return a + diff * (float) t;
    }

    // ── Fin ───────────────────────────────────────────────────────────────────

    private void finish() {
        if (task != null) { task.cancel(); task = null; }
        restoreAudience();
        if (onFinish != null) { Runnable cb = onFinish; onFinish = null; cb.run(); }
        current = null;
    }

    private void restoreAudience() {
        List<Player> audience = new ArrayList<>();
        for (UUID uid : savedGameModes.keySet()) {
            Player p = Bukkit.getPlayer(uid);
            if (p != null && p.isOnline()) audience.add(p);
        }

        effects.restoreAll(audience);

        for (Player p : audience) {
            p.setGameMode(savedGameModes.get(p.getUniqueId()));
            Location ret = savedLocations.get(p.getUniqueId());
            if (ret != null) p.teleport(ret);
            p.clearTitle();
        }
        savedGameModes.clear();
        savedLocations.clear();
    }

    // ── Reflection smooth teleport ────────────────────────────────────────────

    /**
     * Envía la posición vía connection.teleport() sin esperar confirmación
     * del cliente. El cliente la recibe e interpola suavemente a su framerate.
     */
    private void sendPositionSmooth(Player player, Location loc) {
        if (reflectionFailed) { player.teleport(loc); return; }
        try {
            if (methodGetHandle == null) {
                methodGetHandle = player.getClass().getMethod("getHandle");
                methodGetHandle.setAccessible(true);
            }
            Object nmsPlayer = methodGetHandle.invoke(player);

            if (fieldConnection == null) {
                fieldConnection = findField(nmsPlayer.getClass(),
                        "connection", "playerConnection");
                if (fieldConnection == null)
                    throw new NoSuchFieldException("connection / playerConnection");
                fieldConnection.setAccessible(true);
            }
            Object connection = fieldConnection.get(nmsPlayer);

            if (methodTeleportNMS == null) {
                methodTeleportNMS = findTeleportMethod(connection.getClass());
                if (methodTeleportNMS == null)
                    throw new NoSuchMethodException(
                            "teleport(double,double,double,float,float,Set)");
                methodTeleportNMS.setAccessible(true);
            }

            methodTeleportNMS.invoke(connection,
                    loc.getX(), loc.getY(), loc.getZ(),
                    loc.getYaw(), loc.getPitch(),
                    Set.of());

        } catch (Exception e) {
            if (!reflectionFailed) {
                reflectionFailed = true;
                plugin.getLogger().warning("[CinematicPlayer] Reflection falló, " +
                        "usando Player#teleport() como fallback: " + e.getMessage());
            }
            player.teleport(loc);
        }
    }

    private Field findField(Class<?> clazz, String... names) {
        Set<String> nameSet = new HashSet<>(Arrays.asList(names));
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields())
                if (nameSet.contains(f.getName())) return f;
            c = c.getSuperclass();
        }
        return null;
    }

    private Method findTeleportMethod(Class<?> clazz) {
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals("teleport")) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 6
                        && p[0] == double.class && p[1] == double.class
                        && p[2] == double.class && p[3] == float.class
                        && p[4] == float.class  && p[5] == Set.class)
                    return m;
            }
            c = c.getSuperclass();
        }
        return null;
    }

    // ── Títulos y actionbar ───────────────────────────────────────────────────

    private void showText(List<Player> audience, CinematicWaypoint wp) {
        boolean hasTitle    = wp.getTitleMain() != null && !wp.getTitleMain().isBlank();
        boolean hasSub      = wp.getTitleSub()  != null && !wp.getTitleSub().isBlank();
        boolean hasActionbar = wp.getActionbar() != null && !wp.getActionbar().isBlank();
        for (Player p : audience) {
            if (hasTitle || hasSub) {
                Component main = hasTitle ? legacy(wp.getTitleMain()) : Component.empty();
                Component sub  = hasSub   ? legacy(wp.getTitleSub())  : Component.empty();
                p.showTitle(Title.title(main, sub, Title.Times.times(
                        Duration.ofMillis(wp.getTitleFadeIn()  * 50L),
                        Duration.ofMillis(wp.getTitleStay()    * 50L),
                        Duration.ofMillis(wp.getTitleFadeOut() * 50L))));
            }
            if (hasActionbar) p.sendActionBar(legacy(wp.getActionbar()));
        }
    }

    private Component legacy(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    // ── Audiencia ─────────────────────────────────────────────────────────────

    private List<Player> getAudience() {
        List<Player> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers())
            if (plugin.getTeamManager().isInTeam(p)) out.add(p);
        return out;
    }
}