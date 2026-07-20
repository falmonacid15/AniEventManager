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

public class CinematicPlayer {

    private static final String LETTERBOX_CHAR = "\uE100";

    public static final String DEBUG_PAUSE_NAME  = "⏸ Pausar";
    public static final String DEBUG_RESUME_NAME = "▶ Reanudar";
    public static final String DEBUG_REWIND_NAME = "◀◀ Rebobinar -5s";
    public static final String DEBUG_FORWARD_NAME= "▶▶ Adelantar +5s";
    public static final String DEBUG_MARKER_NAME = "+ Agregar Marker";
    public static final String DEBUG_STOP_NAME   = "■ Detener";

    private static final int SKIP_TICKS = 100;

    private final Anieventmanager plugin;
    private final CinematicEffects effects;
    private final CinematicSpectators spectators;

    private BukkitTask task;
    private Cinematic  current;
    private Runnable   onFinish;
    private World      timeWorld;
    private long       savedWorldTime = -1;

    private boolean  debugMode    = false;
    private UUID     debugAdmin   = null;
    private boolean  debugPaused  = false;
    private int      debugTick    = 0;

    // Hotbar debug: 5 slots (era 3)
    private ItemStack[] savedDebugHotbar = null;
    private GameMode    savedDebugGM     = null;

    private final Map<UUID, GameMode> savedGameModes = new HashMap<>();
    private final Map<UUID, Location> savedLocations = new HashMap<>();

    private Method  methodGetHandle   = null;
    private Method  methodTeleportNMS = null;
    private Field   fieldConnection   = null;
    private boolean reflectionFailed  = false;

    public CinematicPlayer(Anieventmanager plugin, CinematicEffects effects, CinematicSpectators spectators) {
        this.plugin  = plugin;
        this.effects = effects;
        this.spectators = spectators;
    }

    // ── API pública ───────────────────────────────────────────────────────────

    public boolean isPlaying()     { return task != null; }
    public boolean isDebugMode()   { return debugMode; }
    public boolean isDebugPaused() { return debugPaused; }
    public int     getDebugTick()  { return debugTick; }

    public Optional<Cinematic> getCurrentCinematic() { return Optional.ofNullable(current); }

    public void play(Cinematic cinematic, Runnable onFinish) {
        startPlayback(cinematic, onFinish, false, null);
    }

    public void playDebug(Cinematic cinematic, Player admin, Runnable onFinish) {
        startPlayback(cinematic, onFinish, true, admin);
    }

    private void startPlayback(Cinematic cinematic, Runnable onFinish,
                               boolean debug, Player debugAdminPlayer) {
        if (task != null) stop();

        this.current     = cinematic;
        this.onFinish    = onFinish;
        this.debugMode   = debug;
        this.debugTick   = 0;
        this.debugPaused = false;
        this.debugAdmin  = debugAdminPlayer != null ? debugAdminPlayer.getUniqueId() : null;

        List<CinematicFrame>  frames  = cinematic.getFrames();
        List<CinematicMarker> markers = cinematic.getMarkers();
        int totalFrames = frames.size();
        if (totalFrames == 0) { finish(); return; }

        if (debug) {
            if (debugAdminPlayer == null) { finish(); return; }
            setupDebugHotbar(debugAdminPlayer);
            savedDebugGM = debugAdminPlayer.getGameMode();

            debugAdminPlayer.setGameMode(GameMode.CREATIVE);
            debugAdminPlayer.setAllowFlight(true);
            debugAdminPlayer.setFlying(true);

            CinematicFrame first = frames.get(0);
            Location startLoc = toLocation(first, cinematic);
            if (startLoc != null) debugAdminPlayer.teleport(startLoc);

        } else {
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

            final List<Player> audienceSnapshot = new ArrayList<>(audience);
            final int fadeIn = cinematic.getFadeInTicks();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Player p : audienceSnapshot) {
                    if (p.isOnline()) effects.applyLetterbox(p, fadeIn);
                }
            }, 2L);

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

            if (debugMode) {
                Player da = debugAdmin != null ? Bukkit.getPlayer(debugAdmin) : null;
                if (da == null || !da.isOnline()) { finish(); return; }

                if (debugPaused) {
                    da.sendActionBar(buildDebugActionBar(debugTick, totalFrames, true));
                    return;
                }
                if (debugTick >= totalFrames) { finish(); return; }

                CinematicFrame frame = frames.get(debugTick);
                sendPositionSmooth(da, frame, cinematic);
                da.sendActionBar(buildDebugActionBar(debugTick, totalFrames, false));

                for (int i = 0; i < markers.size(); i++) {
                    CinematicMarker m = markers.get(i);
                    if (m.getTick() == debugTick && !shownMarkers.contains(i) && m.hasText()) {
                        shownMarkers.add(i);
                        showTextOnly(List.of(da), m);
                    }
                }
                debugTick++;

            } else {
                if (debugTick >= totalFrames) {
                    int fadeOut = cinematic.getFadeOutTicks();
                    if (fadeOut > 0) {
                        task.cancel();
                        task = null;
                        List<Player> live = getAudience();
                        if (live.isEmpty()) {
                            finish();
                        } else {
                            int[] triggered = {0};
                            for (Player p : live) {
                                effects.applyFadeOut(p, fadeOut, () -> {
                                    if (triggered[0]++ == 0) finish();
                                });
                            }
                        }
                    } else {
                        finish();
                    }
                    return;
                }

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
                        for (Player p : live) showText(p, m);
                    }
                }

                debugTick++;
            }

        }, 0L, 1L);
    }

    private void showText(Player player, CinematicMarker m) {
        boolean hasTitle    = m.getTitleMain() != null && !m.getTitleMain().isBlank();
        boolean hasSub      = m.getTitleSub()  != null && !m.getTitleSub().isBlank();
        boolean hasActionbar = m.getActionbar() != null && !m.getActionbar().isBlank();
        if (hasTitle || hasSub) {
            player.showTitle(Title.title(
                    hasTitle ? legacy(m.getTitleMain()) : Component.empty(),
                    hasSub   ? legacy(m.getTitleSub())  : Component.empty(),
                    Title.Times.times(
                            Duration.ofMillis(m.getTitleFadeIn()  * 50L),
                            Duration.ofMillis(m.getTitleStay()    * 50L),
                            Duration.ofMillis(m.getTitleFadeOut() * 50L))));
        }
        if (hasActionbar) player.sendActionBar(legacy(m.getActionbar()));
    }

    private void showTextOnly(List<Player> audience, CinematicMarker m) {
        boolean hasTitle = m.getTitleMain() != null && !m.getTitleMain().isBlank();
        boolean hasSub   = m.getTitleSub()  != null && !m.getTitleSub().isBlank();
        for (Player p : audience) {
            if (hasTitle || hasSub) {
                p.showTitle(Title.title(
                        hasTitle ? legacy(m.getTitleMain()) : Component.empty(),
                        hasSub   ? legacy(m.getTitleSub())  : Component.empty(),
                        Title.Times.times(
                                Duration.ofMillis(m.getTitleFadeIn()  * 50L),
                                Duration.ofMillis(m.getTitleStay()    * 50L),
                                Duration.ofMillis(m.getTitleFadeOut() * 50L))));
            }
            if (m.getActionbar() != null && !m.getActionbar().isBlank())
                p.sendActionBar(legacy(m.getActionbar()));
        }
    }

    public void toggleDebugPause() {
        if (!debugMode) return;
        debugPaused = !debugPaused;
        updateDebugHotbar();
    }

    public boolean isDebugAdmin(Player p) {
        return debugMode && p != null && p.getUniqueId().equals(debugAdmin);
    }

    public void seekDebug(int deltaTicks) {
        if (!debugMode || current == null) return;
        int newTick = Math.max(0, Math.min(current.getTotalFrames() - 1, debugTick + deltaTicks));
        debugTick = newTick;

        Player admin = debugAdmin != null ? Bukkit.getPlayer(debugAdmin) : null;
        if (admin == null) return;

        CinematicFrame frame = current.getFrame(debugTick);
        if (frame == null) return;

        World world = plugin.getCinematicManager().getCinematicWorld(current.getId());
        if (world == null) return;

        Location loc = new Location(world, frame.getX(), frame.getY(), frame.getZ(),
                frame.getYaw(), frame.getPitch());
        admin.teleport(loc);

        String direction = deltaTicks > 0 ? "▶▶ +" : "◀◀ ";
        admin.sendActionBar(Component.text(
                direction + Math.abs(deltaTicks / 20) + "s  →  Tick " + debugTick
                        + " / " + current.getTotalFrames()
                        + "  (" + String.format("%.1fs", debugTick / 20.0) + ")",
                NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
    }

    public void addMarkerAtCurrentTick(Player admin) {
        if (!debugMode || current == null) return;
        CinematicMarker marker = new CinematicMarker(debugTick);
        current.addMarker(marker);
        current.save();
        admin.sendMessage(Component.text(
                "✔ Marker agregado en tick " + debugTick + "  (" +
                        String.format("%.1fs", debugTick / 20.0) + ")", NamedTextColor.GREEN));
        Bukkit.getScheduler().runTaskLater(plugin, () ->
                plugin.getCinematicMarkerGUI().open(admin, current, marker, null), 1L);
    }

    public boolean handleDebugHotbarClick(Player admin, int slot) {
        if (!isDebugAdmin(admin)) return false;
        return switch (slot) {
            case 0 -> { toggleDebugPause();                        yield true; }
            case 1 -> { seekDebug(-SKIP_TICKS);                    yield true; }
            case 2 -> { seekDebug(+SKIP_TICKS);                    yield true; }
            case 3 -> { addMarkerAtCurrentTick(admin);             yield true; }
            case 4 -> { stop();                                     yield true; }
            default -> false;
        };
    }

    private void setupDebugHotbar(Player admin) {
        savedDebugHotbar = new ItemStack[5];
        for (int i = 0; i < 5; i++) savedDebugHotbar[i] = admin.getInventory().getItem(i);
        updateDebugHotbar(admin);
        admin.getInventory().setHeldItemSlot(0);
    }

    private void updateDebugHotbar() {
        Player admin = debugAdmin != null ? Bukkit.getPlayer(debugAdmin) : null;
        if (admin != null) updateDebugHotbar(admin);
    }

    private void updateDebugHotbar(Player admin) {
        admin.getInventory().setItem(0, ItemBuilder.of(
                        debugPaused ? Material.LIME_DYE : Material.YELLOW_DYE)
                .name(debugPaused ? DEBUG_RESUME_NAME : DEBUG_PAUSE_NAME,
                        debugPaused ? NamedTextColor.GREEN : NamedTextColor.YELLOW,
                        TextDecoration.BOLD)
                .emptyLine()
                .lore(NamedTextColor.GRAY, "Click para " + (debugPaused ? "reanudar." : "pausar."))
                .build());

        admin.getInventory().setItem(1, ItemBuilder.of(Material.CLOCK)
                .name(DEBUG_REWIND_NAME, NamedTextColor.AQUA, TextDecoration.BOLD)
                .emptyLine()
                .lore(NamedTextColor.GRAY, "Retrocede " + (SKIP_TICKS / 20) + " segundos.")
                .lore(NamedTextColor.DARK_GRAY, "Tick actual: " + debugTick)
                .build());

        admin.getInventory().setItem(2, ItemBuilder.of(Material.CLOCK)
                .name(DEBUG_FORWARD_NAME, NamedTextColor.AQUA, TextDecoration.BOLD)
                .emptyLine()
                .lore(NamedTextColor.GRAY, "Avanza " + (SKIP_TICKS / 20) + " segundos.")
                .lore(NamedTextColor.DARK_GRAY, "Tick actual: " + debugTick)
                .build());

        admin.getInventory().setItem(3, ItemBuilder.of(Material.GLOW_INK_SAC)
                .name(DEBUG_MARKER_NAME, NamedTextColor.GREEN, TextDecoration.BOLD)
                .emptyLine()
                .lore(NamedTextColor.GRAY, "Click para agregar marker en tick actual.")
                .build());

        admin.getInventory().setItem(4, ItemBuilder.of(Material.RED_DYE)
                .name(DEBUG_STOP_NAME, NamedTextColor.RED, TextDecoration.BOLD)
                .emptyLine()
                .lore(NamedTextColor.GRAY, "Click para detener.")
                .build());
    }

    private void restoreDebugHotbar(Player admin) {
        if (savedDebugHotbar == null) return;
        for (int i = 0; i < savedDebugHotbar.length; i++)
            admin.getInventory().setItem(i, savedDebugHotbar[i]);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
        if (debugMode) restoreDebug();
        else { restoreWorldTime(); restoreAudience(); }
        if (onFinish != null) { onFinish.run(); onFinish = null; }
        current = null; debugMode = false; debugAdmin = null;
        debugPaused = false; debugTick = 0;
    }

    private void finish() {
        if (task != null) { task.cancel(); task = null; }
        if (debugMode) restoreDebug();
        else { restoreWorldTime(); restoreAudience(); }
        if (onFinish != null) { Runnable cb = onFinish; onFinish = null; cb.run(); }
        current = null; debugMode = false; debugAdmin = null;
        debugPaused = false; debugTick = 0;
    }

    private void restoreDebug() {
        Player admin = debugAdmin != null ? Bukkit.getPlayer(debugAdmin) : null;
        if (admin != null && admin.isOnline()) {
            restoreDebugHotbar(admin);
            if (savedDebugGM != null) admin.setGameMode(savedDebugGM);
            admin.clearTitle();
            admin.sendActionBar(Component.empty());
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
        for (Player p : audience) p.clearTitle();
        effects.restoreAll(audience);
        for (Player p : audience) {
            p.setGameMode(savedGameModes.get(p.getUniqueId()));
            Location ret = savedLocations.get(p.getUniqueId());
            if (ret != null) p.teleport(ret);
        }
        savedGameModes.clear();
        savedLocations.clear();
    }

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
            bar = bar.append(Component.text("█", paused ? NamedTextColor.YELLOW : NamedTextColor.GREEN));
        for (int i = 0; i < empty; i++)
            bar = bar.append(Component.text("░", NamedTextColor.DARK_GRAY));

        bar = bar.append(Component.text("]", NamedTextColor.DARK_GRAY));

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

    private Location toLocation(CinematicFrame frame, Cinematic cinematic) {
        World world = plugin.getCinematicManager().getCinematicWorld(cinematic.getId());
        if (world == null) return null;
        return new Location(world, frame.getX(), frame.getY(), frame.getZ(),
                frame.getYaw(), frame.getPitch());
    }

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
                if (fieldConnection == null) throw new NoSuchFieldException("connection / playerConnection");
                fieldConnection.setAccessible(true);
            }
            Object connection = fieldConnection.get(nmsPlayer);

            if (methodTeleportNMS == null) {
                methodTeleportNMS = findTeleportMethod(connection.getClass());
                if (methodTeleportNMS == null) throw new NoSuchMethodException("teleport(d,d,d,f,f,Set)");
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

    private Component legacy(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    private List<Player> getAudience() {
        List<Player> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers())
            if (plugin.getTeamManager().isInTeam(p) || spectators.isSpectator(p)) out.add(p);
        return out;
    }
}