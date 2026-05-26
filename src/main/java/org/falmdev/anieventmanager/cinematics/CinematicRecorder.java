package org.falmdev.anieventmanager.cinematics;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.cinematics.model.Cinematic;
import org.falmdev.anieventmanager.cinematics.model.CinematicFrame;
import org.falmdev.anieventmanager.cinematics.model.CinematicState;
import org.falmdev.anieventmanager.utils.gui.GuiUtil;
import org.falmdev.anieventmanager.utils.gui.ItemBuilder;

import java.util.UUID;

/**
 * Grabador de cinematicas con timer continuo, velocidades de vuelo
 * seleccionables desde el hotbar y suavizado de cámara al grabar.
 *
 * Layout del hotbar durante la grabación:
 *   Slot 0: Tijeras (izq=Pausar, der=Terminar)
 *   Slot 1: Velocidad 1 — Muy lenta  (flySpeed 0.02, suavizado alto)
 *   Slot 2: Velocidad 2 — Lenta      (flySpeed 0.05, suavizado medio)
 *   Slot 3: Velocidad 3 — Media      (flySpeed 0.10, suavizado bajo)
 *   Slot 4: Velocidad 4 — Rápida     (flySpeed 0.20, suavizado mínimo)
 *   Slot 5: Velocidad 5 — Muy rápida (flySpeed 0.40, sin suavizado)
 *
 * Suavizado:
 *   Cada tick se hace un lerp entre el último frame grabado y la posición actual.
 *   alpha = 1.0 → posición exacta (sin suavizado)
 *   alpha = 0.7 → suavizado fuerte (recomendado para velocidades bajas)
 *
 * La velocidad activa se detecta automáticamente por el slot seleccionado:
 * si el admin tiene el slot 2 seleccionado, se aplica la velocidad 2.
 */
public class CinematicRecorder {

    public static final String SHEARS_NAME = "✦ Shears de Grabación";

    // ── Velocidades ───────────────────────────────────────────────────────────

    public enum RecordingSpeed {
        //         label          flySpeed  alpha  color                   material
        V1("Muy lenta",   0.02f, 0.70f, NamedTextColor.AQUA,          Material.LIGHT_BLUE_DYE),
        V2("Lenta",       0.05f, 0.80f, NamedTextColor.GREEN,          Material.LIME_DYE),
        V3("Media",       0.10f, 0.90f, NamedTextColor.YELLOW,         Material.YELLOW_DYE),
        V4("Rápida",      0.20f, 0.95f, NamedTextColor.GOLD,           Material.ORANGE_DYE),
        V5("Muy rápida",  0.40f, 1.00f, NamedTextColor.RED,            Material.RED_DYE);

        public final String         label;
        public final float          flySpeed; // velocidad de vuelo en Paper
        public final float          alpha;    // factor de lerp: 1.0 = sin suavizado
        public final NamedTextColor color;
        public final Material       material;

        RecordingSpeed(String label, float flySpeed, float alpha,
                       NamedTextColor color, Material material) {
            this.label    = label;
            this.flySpeed = flySpeed;
            this.alpha    = alpha;
            this.color    = color;
            this.material = material;
        }

        /** Slot 1 → V1, slot 2 → V2, etc. Devuelve null si el slot no es de velocidad. */
        public static RecordingSpeed fromSlot(int slot) {
            int idx = slot - 1;
            RecordingSpeed[] vals = values();
            if (idx < 0 || idx >= vals.length) return null;
            return vals[idx];
        }
    }

    // ── Estado de grabación ───────────────────────────────────────────────────

    private final Anieventmanager plugin;

    private UUID           recordingAdmin     = null;
    private Cinematic      recordingCinematic = null;
    private int            totalTicks         = 0;
    private int            elapsedTicks       = 0;
    private boolean        paused             = false;
    private boolean        nextFrameIsCut     = false;
    private RecordingSpeed currentSpeed       = RecordingSpeed.V3;

    /** Último frame grabado — base del lerp de suavizado. */
    private CinematicFrame lastFrame = null;

    private BukkitTask task = null;

    /** Hotbar original del admin (slots 0-5) para restaurar al terminar. */
    private ItemStack[] savedHotbar  = null;
    private GameMode    savedGameMode = null;

    public CinematicRecorder(Anieventmanager plugin) {
        this.plugin = plugin;
    }

    // ── API pública ───────────────────────────────────────────────────────────

    public boolean         isRecording()              { return recordingAdmin != null; }
    public boolean         isPaused()                 { return paused; }
    public boolean         isRecordingAdmin(Player p) {
        return p != null && p.getUniqueId().equals(recordingAdmin);
    }
    public Cinematic       getRecordingCinematic()    { return recordingCinematic; }
    public RecordingSpeed  getCurrentSpeed()          { return currentSpeed; }

    public boolean startRecording(Player admin, Cinematic cinematic, int durationTicks) {
        if (isRecording()) return false;
        if (!cinematic.isIdle()) return false;

        recordingAdmin     = admin.getUniqueId();
        recordingCinematic = cinematic;
        totalTicks         = durationTicks;
        elapsedTicks       = 0;
        paused             = false;
        nextFrameIsCut     = false;
        lastFrame          = null;
        currentSpeed       = RecordingSpeed.V3;

        cinematic.clearFrames();
        cinematic.setState(CinematicState.RECORDING);

        // Guardar hotbar original y reemplazar slots 0-5
        setupRecordingHotbar(admin);

        // Modo creativo con vuelo
        savedGameMode = admin.getGameMode();
        if (savedGameMode != GameMode.CREATIVE && savedGameMode != GameMode.SPECTATOR) {
            admin.setGameMode(GameMode.CREATIVE);
        }
        admin.setAllowFlight(true);
        admin.setFlying(true);
        applySpeed(admin, currentSpeed);

        admin.sendMessage(Component.text("⏺ Grabando ", NamedTextColor.RED)
                .append(Component.text(cinematic.getDisplayName(), NamedTextColor.YELLOW))
                .append(Component.text(" — " + formatTicks(durationTicks), NamedTextColor.GRAY)));
        admin.sendMessage(Component.text(
                "  Slot 0: Tijeras  |  Slots 1-5: Velocidades de vuelo",
                NamedTextColor.GRAY));

        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Player p = Bukkit.getPlayer(recordingAdmin);
            if (p == null || !p.isOnline()) { stopRecording(); return; }

            // Detectar cambio de velocidad por slot seleccionado
            int heldSlot = p.getInventory().getHeldItemSlot();
            RecordingSpeed slotSpeed = RecordingSpeed.fromSlot(heldSlot);
            if (slotSpeed != null && slotSpeed != currentSpeed) {
                currentSpeed = slotSpeed;
                applySpeed(p, currentSpeed);
                refreshSpeedItems(p); // actualizar resaltado visual
            }

            if (!paused) {
                boolean isCut = nextFrameIsCut;
                nextFrameIsCut = false;

                // Posición cruda del jugador
                double rawX     = p.getLocation().getX();
                double rawY     = p.getLocation().getY();
                double rawZ     = p.getLocation().getZ();
                float  rawYaw   = p.getLocation().getYaw();
                float  rawPitch = p.getLocation().getPitch();

                double fx, fy, fz;
                float  fyaw, fpitch;

                if (lastFrame == null || isCut) {
                    // Primer frame o corte: posición exacta sin lerp
                    fx = rawX; fy = rawY; fz = rawZ;
                    fyaw = rawYaw; fpitch = rawPitch;
                } else {
                    // Lerp suavizado entre el frame anterior y la posición actual
                    float a = currentSpeed.alpha;
                    fx     = lerp(lastFrame.getX(),     rawX,     a);
                    fy     = lerp(lastFrame.getY(),     rawY,     a);
                    fz     = lerp(lastFrame.getZ(),     rawZ,     a);
                    fyaw   = lerpAngle(lastFrame.getYaw(),   rawYaw,   a);
                    fpitch = lerpAngle(lastFrame.getPitch(), rawPitch, a);
                }

                CinematicFrame frame = new CinematicFrame(fx, fy, fz, fyaw, fpitch, isCut);
                recordingCinematic.addFrame(frame);
                lastFrame = frame;
                elapsedTicks++;

                p.sendActionBar(buildActionBar(elapsedTicks, totalTicks, false));

                if (elapsedTicks >= totalTicks) stopRecording();
            } else {
                p.sendActionBar(buildActionBar(elapsedTicks, totalTicks, true));
            }
        }, 0L, 1L);

        return true;
    }

    /** Pausa o reanuda (tijera izquierda). */
    public void togglePause(Player admin) {
        if (!isRecordingAdmin(admin)) return;
        paused = !paused;
        if (paused) {
            // Velocidad libre durante la pausa (no afecta al suavizado)
            admin.setFlySpeed(0.1f);
            admin.sendMessage(Component.text(
                    "⏸ Pausado — moví a la nueva posición y presioná tijera izq. para continuar.",
                    NamedTextColor.YELLOW));
        } else {
            nextFrameIsCut = true;
            applySpeed(admin, currentSpeed);
            admin.sendMessage(Component.text(
                    "⏺ Reanudado — corte de escena creado.", NamedTextColor.GREEN));
        }
    }

    /** Termina la grabación y guarda. */
    public void stopRecording() {
        if (task != null) { task.cancel(); task = null; }

        if (recordingCinematic != null) {
            recordingCinematic.save();
            recordingCinematic.setState(CinematicState.IDLE);

            Player admin = recordingAdmin != null
                    ? Bukkit.getPlayer(recordingAdmin) : null;
            if (admin != null && admin.isOnline()) {
                restoreHotbar(admin);
                if (savedGameMode != null) admin.setGameMode(savedGameMode);
                admin.setFlySpeed(0.1f);
                admin.setWalkSpeed(0.2f);
                admin.clearTitle();
                admin.sendActionBar(Component.empty());
                admin.sendMessage(Component.text("■ Grabación terminada. ", NamedTextColor.GREEN)
                        .append(Component.text(
                                recordingCinematic.getTotalFrames() + " frames  (" +
                                        String.format("%.1fs", recordingCinematic.getDurationSeconds()) + ")",
                                NamedTextColor.GRAY)));
            }
        }

        recordingAdmin     = null;
        recordingCinematic = null;
        totalTicks         = 0;
        elapsedTicks       = 0;
        paused             = false;
        nextFrameIsCut     = false;
        lastFrame          = null;
        savedHotbar        = null;
        savedGameMode      = null;
    }

    // ── Hotbar ────────────────────────────────────────────────────────────────

    private void setupRecordingHotbar(Player admin) {
        // Guardar slots 0-5 originales
        savedHotbar = new ItemStack[6];
        for (int i = 0; i < 6; i++) savedHotbar[i] = admin.getInventory().getItem(i);

        // Slot 0: tijeras
        admin.getInventory().setItem(0, buildShears());

        // Slots 1-5: velocidades
        for (RecordingSpeed speed : RecordingSpeed.values()) {
            admin.getInventory().setItem(speed.ordinal() + 1,
                    buildSpeedItem(speed, speed == currentSpeed));
        }

        admin.getInventory().setHeldItemSlot(0);
    }

    private void restoreHotbar(Player admin) {
        if (savedHotbar == null) return;
        for (int i = 0; i < savedHotbar.length; i++)
            admin.getInventory().setItem(i, savedHotbar[i]);
    }

    /** Refresca el resaltado visual de los items de velocidad. */
    public void refreshSpeedItems(Player admin) {
        if (!isRecordingAdmin(admin)) return;
        for (RecordingSpeed speed : RecordingSpeed.values()) {
            admin.getInventory().setItem(speed.ordinal() + 1,
                    buildSpeedItem(speed, speed == currentSpeed));
        }
    }

    // ── Construcción de items ─────────────────────────────────────────────────

    public static ItemStack buildShears() {
        return ItemBuilder.of(Material.SHEARS)
                .name(SHEARS_NAME, NamedTextColor.GOLD, TextDecoration.BOLD)
                .emptyLine()
                .lore(NamedTextColor.YELLOW, "Click izquierdo → Pausar / Reanudar")
                .lore(NamedTextColor.RED,    "Click derecho   → Terminar grabación")
                .build();
    }

    private ItemStack buildSpeedItem(RecordingSpeed speed, boolean selected) {
        // Nombre con triángulo si está activa
        Component nameComp = Component.text(
                        (selected ? "▶ " : "   ") + speed.label,
                        selected ? speed.color : NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.BOLD, selected)
                .decoration(TextDecoration.ITALIC, false);

        ItemBuilder b = ItemBuilder.of(speed.material)
                .name(nameComp)
                .emptyLine()
                .lore(GuiUtil.label("Velocidad",
                        Component.text(
                                String.format("%.0f%%", speed.flySpeed * 250f),
                                selected ? speed.color : NamedTextColor.GRAY)))
                .lore(GuiUtil.label("Suavizado",
                        Component.text(smoothingLabel(speed.alpha),
                                selected ? NamedTextColor.WHITE : NamedTextColor.GRAY)));

        if (selected) {
            b.emptyLine().lore(NamedTextColor.GREEN, "✔ Velocidad activa");
        } else {
            b.emptyLine().lore(NamedTextColor.GRAY, "Selecciona este slot para activar");
        }

        return b.build();
    }

    private String smoothingLabel(float alpha) {
        if (alpha >= 1.00f) return "Sin suavizado";
        if (alpha >= 0.95f) return "Mínimo";
        if (alpha >= 0.90f) return "Bajo";
        if (alpha >= 0.80f) return "Medio";
        return "Alto";
    }

    public static boolean isRecordingShears(ItemStack item) {
        if (item == null || item.getType() != Material.SHEARS) return false;
        if (!item.hasItemMeta() || item.getItemMeta().displayName() == null) return false;
        String name = net.kyori.adventure.text.serializer.plain
                .PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().displayName());
        return name.contains(SHEARS_NAME);
    }

    // ── Lerp y velocidad ─────────────────────────────────────────────────────

    private void applySpeed(Player admin, RecordingSpeed speed) {
        admin.setFlySpeed(speed.flySpeed);
    }

    private double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private float lerpAngle(float a, float b, float t) {
        float diff = b - a;
        while (diff >  180f) diff -= 360f;
        while (diff < -180f) diff += 360f;
        return (float)(a + diff * t);
    }

    // ── Actionbar ─────────────────────────────────────────────────────────────

    private Component buildActionBar(int elapsed, int total, boolean isPaused) {
        int bars   = 16;
        int filled = total > 0 ? Math.min(bars, (elapsed * bars) / total) : 0;
        int empty  = bars - filled;

        String icon = isPaused ? "⏸" : "⏺";
        NamedTextColor iconColor = isPaused ? NamedTextColor.YELLOW : NamedTextColor.RED;

        Component bar = Component.text(icon + " ", iconColor)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(
                        String.format("%.1fs / %.1fs  ", elapsed / 20.0, total / 20.0),
                        NamedTextColor.WHITE))
                .append(Component.text("[", NamedTextColor.DARK_GRAY));

        NamedTextColor barColor = isPaused ? NamedTextColor.YELLOW : currentSpeed.color;
        for (int i = 0; i < filled; i++)
            bar = bar.append(Component.text("█", barColor));
        for (int i = 0; i < empty; i++)
            bar = bar.append(Component.text("░", NamedTextColor.DARK_GRAY));

        bar = bar.append(Component.text("]", NamedTextColor.DARK_GRAY));

        bar = bar.append(Component.text(
                        isPaused ? "  PAUSADO" : "  " + currentSpeed.label,
                        isPaused ? NamedTextColor.YELLOW : currentSpeed.color)
                .decoration(TextDecoration.ITALIC, false));

        return bar;
    }

    // ── Parseo de duración ────────────────────────────────────────────────────

    public static String formatTicks(int ticks) {
        int totalSec = ticks / 20;
        int min = totalSec / 60, sec = totalSec % 60;
        if (min == 0) return sec + "s";
        if (sec == 0) return min + "m";
        return min + "m " + sec + "s";
    }

    public static int parseDuration(String input) {
        if (input == null || input.isBlank()) return -1;
        input = input.trim().toLowerCase();
        int ticks;
        try {
            if (input.contains("m") && input.contains("s")) {
                String[] p = input.split("m");
                ticks = (Integer.parseInt(p[0].trim()) * 60
                        + Integer.parseInt(p[1].replace("s","").trim())) * 20;
            } else if (input.contains("m")) {
                ticks = Integer.parseInt(input.replace("m","").trim()) * 60 * 20;
            } else if (input.contains("s")) {
                ticks = Integer.parseInt(input.replace("s","").trim()) * 20;
            } else {
                ticks = Integer.parseInt(input) * 20;
            }
        } catch (NumberFormatException e) { return -1; }
        if (ticks < 40 || ticks > 12000) return -1;
        return ticks;
    }
}