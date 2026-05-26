package org.falmdev.anieventmanager.cinematics;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.cinematics.model.Cinematic;
import org.falmdev.anieventmanager.cinematics.model.CinematicFrame;
import org.falmdev.anieventmanager.cinematics.model.CinematicMarker;
import org.falmdev.anieventmanager.utils.gui.ItemBuilder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.*;

/**
 * Reproductor de cinematicas.
 *
 * Modos de reproducción:
 *  - Normal: oculta HUD, jugadores, mano. Para todos los jugadores con equipo.
 *  - Debug: no oculta nada. Solo para el admin que lo inicia. Muestra en
 *    actionbar el tick actual y permite pausar/agregar markers desde el hotbar.
 */
public class CinematicPlayer {

    // ── Constantes del modo debug ─────────────────────────────────────────────

    public static final String DEBUG_PAUSE_NAME   = "⏸ Pausar";
    public static final String DEBUG_RESUME_NAME  = "▶ Reanudar";
    public static final String DEBUG_MARKER_NAME  = "+ Agregar Marker";
    public static final String DEBUG_STOP_NAME    = "■ Detener";

    private final Anieventmanager plugin;
    private final CinematicEffects effects;

    private BukkitTask task;
    private Cinematic  current;
    private Runnable   onFinish;
    private World      timeWorld;
    private long       savedWorldTime = -1;

    // ── Estado de debug ───────────────────────────────────────────────────────
    private boolean  debugMode   = false;
    private UUID     debugAdmin  = null;
    private boolean  debugPaused = false;
    private int      debugTick   = 0;

    /** Hotbar original del admin en modo debug. */
    private ItemStack[] savedDebugHotbar = null;
    private GameMode    savedDebugGM     = null;

    // ── Audiencia normal ──────────────────────────────────────────────────────
    private final Map<UUID, GameMode> savedGameModes = new HashMap<>();
    private final Map<UUID, Location> savedLocations = new HashMap<>();

    // Reflection cache
    private Method  methodGetHandle   = null;
    private Method  methodTeleportNMS = null;
    private Field   fieldConnection   = null;
    private boolean reflectionFailed  = false;

    public CinematicPlayer(Anieventmanager plugin, CinematicEffects effects) {
        this.plugin  = plugin;
        this.effects = effects;
    }

    // ── API pública ───────────────────────────────────────────────────────────

    public boolean isPlaying()   { return task != null; }
    public boolean isDebugMode() { return debugMode; }
    public boolean isDebugPaused() { return debugPaused; }
    public int     getDebugTick()  { return debugTick; }

    public Optional<Cinematic> getCurrentCinematic() {
        return Optional.ofNullable(current);
    }

    // ── Reproducción normal ───────────────────────────────────────────────────

    public void play(Cinematic cinematic, Runnable onFinish) {
        play(cinematic, onFinish, false, null);
    }

    // ── Reproducción debug ────────────────────────────────────────────────────

    /**
     * Inicia la reproducción en modo debug para un admin específico.
     * No oculta HUD ni jugadores. Muestra tick en actionbar.
     * El hotbar del admin muestra controles: pausa, reanudar, agregar marker, detener.
     */
    public void playDebug(Cinematic cinematic, Player admin, Runnable onFinish) {
        play(cinematic, onFinish, true, admin);
    }

    // ── Implementación común ──────────────────────────────────────────────────

    private void play(Cinematic cinematic, Runnable onFinish,
                      boolean debug, Player debugAdminPlayer) {
        if (task != null) stop();

        this.current    = cinematic;
        this.onFinish   = onFinish;
        this.debugMode  = debug;
        this.debugTick  = 0;
        this.debugPaused = false;
        this.debugAdmin = debugAdminPlayer != null
                ? debugAdminPlayer.getUniqueId() : null;

        List<CinematicFrame>  frames  = cinematic.getFrames();
        List<CinematicMarker> markers = cinematic.getMarkers();
        int totalFrames = frames.size();
        if (totalFrames == 0) { finish(); return; }

        if (debug) {
            // ── Modo debug: solo el admin ─────────────────────────────────────
            if (debugAdminPlayer == null) { finish(); return; }
            setupDebugHotbar(debugAdminPlayer);
            savedDebugGM = debugAdminPlayer.getGameMode();
            debugAdminPlayer.setGameMode(GameMode.CREATIVE);

            CinematicFrame first = frames.get(0);
            Location startLoc = toLocation(first, cinematic);
            if (startLoc != null) debugAdminPlayer.teleport(startLoc);

        } else {
            // ── Modo normal: todos con equipo ─────────────────────────────────
            List<Player> audience = getAudience();
            savedGameModes.clear();
            savedLocations.clear();

            CinematicFrame first = frames.get(0);
            Location startLoc = toLocation(first, cinematic);

            for (Player p : audience) {
                savedGameModes.put(p.getUniqueId(), p.getGameMode());
                savedLocations.put(p.getUniqueId(), p.getLocation().clone());
                p.setGameMode(GameMode.SPECTATOR);
                if (startLoc != null) p.teleport(startLoc);
            }
            effects.applyAll(audience);

            // Control de tiempo del mundo
            if (cinematic.hasTimeControl() && startLoc != null) {
                timeWorld = startLoc.getWorld();
                if (timeWorld != null) {
                    savedWorldTime = timeWorld.getTime();
                    timeWorld.setGameRule(GameRules.ADVANCE_TIME, false);
                    timeWorld.setTime(cinematic.getTimeStart());
                }
            }
        }

        Set<Integer> shownMarkers = new HashSet<>();

        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {

            // ── Modo debug: manejar pausa ─────────────────────────────────────
            if (debugMode) {
                Player da = debugAdmin != null ? Bukkit.getPlayer(debugAdmin) : null;
                if (da == null || !da.isOnline()) { finish(); return; }

                if (debugPaused) {
                    // Mostrar actionbar de pausa
                    da.sendActionBar(buildDebugActionBar(debugTick, totalFrames, true));
                    return; // no avanzar el tick
                }

                // Avanzar tick en debug
                if (debugTick >= totalFrames) { finish(); return; }

                CinematicFrame frame = frames.get(debugTick);
                sendPositionSmooth(da, frame, cinematic);
                da.sendActionBar(buildDebugActionBar(debugTick, totalFrames, false));

                // Markers en modo debug también
                for (int i = 0; i < markers.size(); i++) {
                    CinematicMarker m = markers.get(i);
                    if (m.getTick() == debugTick && !shownMarkers.contains(i) && m.hasText()) {
                        shownMarkers.add(i);
                        showText(List.of(da), m);
                    }
                }

                debugTick++;

            } else {
                // ── Modo normal ───────────────────────────────────────────────
                if (debugTick >= totalFrames) { finish(); return; }

                CinematicFrame frame = frames.get(debugTick);
                List<Player> live = getAudience();

                if (frame.isCut()) {
                    Location cutLoc = toLocation(frame, cinematic);
                    if (cutLoc != null) for (Player p : live) p.teleport(cutLoc);
                } else {
                    for (Player p : live) sendPositionSmooth(p, frame, cinematic);
                }

                if (timeWorld != null && cinematic.hasTimeControl()) {
                    long wt = cinematic.getWorldTimeAt(debugTick);
                    if (wt >= 0) timeWorld.setTime(wt);
                }

                for (int i = 0; i < markers.size(); i++) {
                    CinematicMarker m = markers.get(i);
                    if (m.getTick() == debugTick && !shownMarkers.contains(i) && m.hasText()) {
                        shownMarkers.add(i);
                        showText(live, m);
                    }
                }

                debugTick++;
            }

        }, 0L, 1L);
    }

    // ── Controles del modo debug ──────────────────────────────────────────────

    public void toggleDebugPause() {
        if (!debugMode) return;
        debugPaused = !debugPaused;
        updateDebugHotbar();
    }

    public boolean isDebugAdmin(Player p) {
        return debugMode && p != null && p.getUniqueId().equals(debugAdmin);
    }

    /**
     * Agrega un marker en el tick actual del debug.
     * Abre la GUI de edición del marker.
     */
    public void addMarkerAtCurrentTick(Player admin) {
        if (!debugMode || current == null) return;
        CinematicMarker marker = new CinematicMarker(debugTick);
        current.addMarker(marker);
        current.save();
        admin.sendMessage(Component.text(
                "✔ Marker agregado en tick " + debugTick + "  (" +
                        String.format("%.1fs", debugTick / 20.0) + ")",
                NamedTextColor.GREEN));
        // Abrir editor del marker después de un tick
        Bukkit.getScheduler().runTaskLater(plugin, () ->
                        plugin.getCinematicMarkerGUI().open(admin, current, marker,
                                () -> {/* no volver a ningún lado, la debug sigue corriendo */}),
                1L);
    }

    // ── Fin ───────────────────────────────────────────────────────────────────

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
        if (debugMode) restoreDebug();
        else { restoreWorldTime(); restoreAudience(); }
        if (onFinish != null) { onFinish.run(); onFinish = null; }
        current = null;
        debugMode = false;
        debugAdmin = null;
        debugPaused = false;
        debugTick = 0;
    }

    private void finish() {
        if (task != null) { task.cancel(); task = null; }
        if (debugMode) restoreDebug();
        else { restoreWorldTime(); restoreAudience(); }
        if (onFinish != null) { Runnable cb = onFinish; onFinish = null; cb.run(); }
        current = null;
        debugMode = false;
        debugAdmin = null;
        debugPaused = false;
        debugTick = 0;
    }

    private void restoreDebug() {
        Player admin = debugAdmin != null ? Bukkit.getPlayer(debugAdmin) : null;
        if (admin != null && admin.isOnline()) {
            restoreDebugHotbar(admin);
            if (savedDebugGM != null) admin.setGameMode(savedDebugGM);
            admin.clearTitle();
            admin.sendActionBar(Component.empty());
            admin.sendMessage(Component.text(
                    "■ Reproducción debug terminada.", NamedTextColor.YELLOW));
        }
        savedDebugHotbar = null;
        savedDebugGM     = null;
    }

    private void restoreWorldTime() {
        if (timeWorld != null && savedWorldTime >= 0) {
            timeWorld.setTime(savedWorldTime);
            timeWorld.setGameRule(GameRules.ADVANCE_TIME, true);
        }
        timeWorld = null;
        savedWorldTime = -1;
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

    // ── Hotbar debug ──────────────────────────────────────────────────────────

    private void setupDebugHotbar(Player admin) {
        savedDebugHotbar = new ItemStack[4];
        for (int i = 0; i < 4; i++) savedDebugHotbar[i] = admin.getInventory().getItem(i);

        // Slot 0: pausa/play  Slot 1: agregar marker  Slot 2: detener
        updateDebugHotbar(admin);
        admin.getInventory().setHeldItemSlot(0);
    }

    private void updateDebugHotbar() {
        Player admin = debugAdmin != null ? Bukkit.getPlayer(debugAdmin) : null;
        if (admin != null) updateDebugHotbar(admin);
    }

    private void updateDebugHotbar(Player admin) {
        // Slot 0: pause/resume
        String pauseLabel = debugPaused ? DEBUG_RESUME_NAME : DEBUG_PAUSE_NAME;
        NamedTextColor pauseColor = debugPaused ? NamedTextColor.GREEN : NamedTextColor.YELLOW;
        admin.getInventory().setItem(0, ItemBuilder.of(
                        debugPaused ? Material.LIME_DYE : Material.YELLOW_DYE)
                .name(pauseLabel, pauseColor, TextDecoration.BOLD)
                .emptyLine()
                .lore(NamedTextColor.GRAY, "Click para " +
                        (debugPaused ? "reanudar" : "pausar") + " la reproducción.")
                .build());

        // Slot 1: agregar marker en tick actual
        admin.getInventory().setItem(1, ItemBuilder.of(Material.GLOW_INK_SAC)
                .name(DEBUG_MARKER_NAME, NamedTextColor.GREEN, TextDecoration.BOLD)
                .emptyLine()
                .lore(NamedTextColor.GRAY, "Click para agregar un marker")
                .lore("en el tick actual de la reproducción.")
                .build());

        // Slot 2: detener
        admin.getInventory().setItem(2, ItemBuilder.of(Material.RED_DYE)
                .name(DEBUG_STOP_NAME, NamedTextColor.RED, TextDecoration.BOLD)
                .emptyLine()
                .lore(NamedTextColor.GRAY, "Click para detener la reproducción.")
                .build());
    }

    private void restoreDebugHotbar(Player admin) {
        if (savedDebugHotbar == null) return;
        for (int i = 0; i < savedDebugHotbar.length; i++)
            admin.getInventory().setItem(i, savedDebugHotbar[i]);
    }

    /** Verifica si un item del hotbar de debug es uno de los botones de control. */
    public boolean handleDebugHotbarClick(Player admin, int slot) {
        if (!isDebugAdmin(admin)) return false;
        return switch (slot) {
            case 0 -> { toggleDebugPause(); yield true; }
            case 1 -> { addMarkerAtCurrentTick(admin); yield true; }
            case 2 -> { stop(); yield true; }
            default -> false;
        };
    }

    // ── Actionbar debug ───────────────────────────────────────────────────────

    private Component buildDebugActionBar(int tick, int total, boolean paused) {
        int bars   = 20;
        int filled = total > 0 ? Math.min(bars, (tick * bars) / total) : 0;
        int empty  = bars - filled;

        String icon = paused ? "⏸" : "▶";
        NamedTextColor iconColor = paused ? NamedTextColor.YELLOW : NamedTextColor.GREEN;

        Component bar = Component.text(icon + " ", iconColor)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(
                        String.format("Tick %d / %d  (%.1fs)", tick, total, tick / 20.0),
                        NamedTextColor.WHITE))
                .append(Component.text("  [", NamedTextColor.DARK_GRAY));

        for (int i = 0; i < filled; i++)
            bar = bar.append(Component.text("█",
                    paused ? NamedTextColor.YELLOW : NamedTextColor.GREEN));
        for (int i = 0; i < empty; i++)
            bar = bar.append(Component.text("░", NamedTextColor.DARK_GRAY));

        bar = bar.append(Component.text("]", NamedTextColor.DARK_GRAY));

        // Mostrar el marker más cercano — construir fuera del lambda
        if (current != null) {
            Component markerText = current.getMarkers().stream()
                    .filter(m -> m.getTick() >= tick)
                    .findFirst()
                    .map(m -> Component.text(
                            "  → Marker en tick " + m.getTick() +
                                    " (" + String.format("%.1fs", m.getTick() / 20.0) + ")",
                            NamedTextColor.GOLD))
                    .orElse(Component.empty());
            bar = bar.append(markerText);
        }

        return bar;
    }

    // ── Conversión frame → Location ───────────────────────────────────────────

    private Location toLocation(CinematicFrame frame, Cinematic cinematic) {
        World world = plugin.getCinematicManager().getCinematicWorld(cinematic.getId());
        if (world == null) return null;
        return new Location(world,
                frame.getX(), frame.getY(), frame.getZ(),
                frame.getYaw(), frame.getPitch());
    }

    // ── Reflection smooth teleport ────────────────────────────────────────────

    private void sendPositionSmooth(Player player, CinematicFrame frame, Cinematic cinematic) {
        if (reflectionFailed) {
            Location loc = toLocation(frame, cinematic);
            if (loc != null) player.teleport(loc);
            return;
        }
        try {
            if (methodGetHandle == null) {
                methodGetHandle = player.getClass().getMethod("getHandle");
                methodGetHandle.setAccessible(true);
            }
            Object nmsPlayer = methodGetHandle.invoke(player);

            if (fieldConnection == null) {
                fieldConnection = findField(nmsPlayer.getClass(), "connection", "playerConnection");
                if (fieldConnection == null)
                    throw new NoSuchFieldException("connection / playerConnection");
                fieldConnection.setAccessible(true);
            }
            Object connection = fieldConnection.get(nmsPlayer);

            if (methodTeleportNMS == null) {
                methodTeleportNMS = findTeleportMethod(connection.getClass());
                if (methodTeleportNMS == null)
                    throw new NoSuchMethodException("teleport(d,d,d,f,f,Set)");
                methodTeleportNMS.setAccessible(true);
            }

            methodTeleportNMS.invoke(connection,
                    frame.getX(), frame.getY(), frame.getZ(),
                    frame.getYaw(), frame.getPitch(), Set.of());

        } catch (Exception e) {
            if (!reflectionFailed) {
                reflectionFailed = true;
                plugin.getLogger().warning("[CinematicPlayer] Reflection falló: " + e.getMessage());
            }
            Location loc = toLocation(frame, cinematic);
            if (loc != null) player.teleport(loc);
        }
    }

    private Field findField(Class<?> clazz, String... names) {
        Set<String> set = new HashSet<>(Arrays.asList(names));
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) if (set.contains(f.getName())) return f;
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
                if (p.length == 6 && p[0] == double.class && p[3] == float.class
                        && p[5] == Set.class) return m;
            }
            c = c.getSuperclass();
        }
        return null;
    }

    // ── Textos ────────────────────────────────────────────────────────────────

    private void showText(List<Player> audience, CinematicMarker m) {
        boolean hasTitle    = m.getTitleMain() != null && !m.getTitleMain().isBlank();
        boolean hasSub      = m.getTitleSub()  != null && !m.getTitleSub().isBlank();
        boolean hasActionbar = m.getActionbar() != null && !m.getActionbar().isBlank();
        for (Player p : audience) {
            if (hasTitle || hasSub) {
                Component main = hasTitle ? legacy(m.getTitleMain()) : Component.empty();
                Component sub  = hasSub   ? legacy(m.getTitleSub())  : Component.empty();
                p.showTitle(Title.title(main, sub, Title.Times.times(
                        Duration.ofMillis(m.getTitleFadeIn()  * 50L),
                        Duration.ofMillis(m.getTitleStay()    * 50L),
                        Duration.ofMillis(m.getTitleFadeOut() * 50L))));
            }
            if (hasActionbar) p.sendActionBar(legacy(m.getActionbar()));
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