package org.falmdev.anieventmanager.utils.interval;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.falmdev.anieventmanager.Anieventmanager;

/**
 * IntervalManager — gestiona pausas/intervalos del evento.
 *
 * Permite iniciar un timer descendente que:
 *  - Muestra un broadcast inicial avisando del intervalo
 *  - Expone placeholders de tiempo restante (mm:ss) y porcentaje (100→0)
 *  - Expone un placeholder de estado (activo/inactivo) para condicionar bossbars
 *  - Avisa al terminar con broadcast + sonido
 *
 * Solo un intervalo a la vez (si se inicia uno nuevo, reemplaza al actual).
 */
public class IntervalManager {

    private final Anieventmanager plugin;

    // Estado actual
    private boolean    active        = false;
    private long       startMillis   = 0;
    private long       endMillis     = 0;
    private int        totalSeconds  = 0;
    private BukkitTask endTask       = null;
    private BukkitTask announceTask  = null;

    public IntervalManager(Anieventmanager plugin) {
        this.plugin = plugin;
    }

    // ── API ───────────────────────────────────────────────────────────────────

    public boolean isActive() { return active; }

    /**
     * Inicia un intervalo de la duración indicada.
     * Si ya hay uno activo, lo cancela y arranca el nuevo.
     * @param seconds duración total en segundos
     * @return true si se inició
     */
    public boolean start(int seconds) {
        if (seconds <= 0) return false;
        if (active) cancel();

        this.totalSeconds = seconds;
        this.startMillis  = System.currentTimeMillis();
        this.endMillis    = startMillis + (seconds * 1000L);
        this.active       = true;

        // Broadcast inicial
        String formatted = formatDuration(seconds);
        Component msg = Component.text("⏸ ", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text("Intervalo de ", NamedTextColor.YELLOW))
                .append(Component.text(formatted, NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(". ¡Conversen y diviértanse mientras tanto!",
                        NamedTextColor.YELLOW));
        broadcast(msg);
        playSoundAll(Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);

        // Task que termina automáticamente al cumplir el tiempo
        endTask = Bukkit.getScheduler().runTaskLater(plugin, this::onFinish, seconds * 20L);

        // Avisos intermedios: 1 min antes, 30s antes, 10s antes
        scheduleAnnouncement(seconds - 60, "⏰ Queda 1 minuto del intervalo.");
        scheduleAnnouncement(seconds - 30, "⏰ Quedan 30 segundos del intervalo.");
        scheduleAnnouncement(seconds - 10, "⏰ Quedan 10 segundos del intervalo.");

        return true;
    }

    /**
     * Cancela el intervalo actual sin avisar.
     */
    public void cancel() {
        if (!active) return;
        if (endTask      != null) { endTask.cancel();      endTask = null; }
        if (announceTask != null) { announceTask.cancel(); announceTask = null; }
        active = false;
        totalSeconds = 0;
        startMillis = 0;
        endMillis = 0;
    }

    /**
     * Termina el intervalo anticipadamente con broadcast de fin.
     */
    public void stop() {
        if (!active) return;
        onFinish();
    }

    private void onFinish() {
        if (endTask      != null) { endTask.cancel();      endTask = null; }
        if (announceTask != null) { announceTask.cancel(); announceTask = null; }
        active = false;
        totalSeconds = 0;
        startMillis = 0;
        endMillis = 0;

        Component msg = Component.text("▶ ", NamedTextColor.GREEN, TextDecoration.BOLD)
                .append(Component.text("¡Intervalo finalizado! Volvamos al evento.",
                        NamedTextColor.GREEN));
        broadcast(msg);
    }

    private void scheduleAnnouncement(int delaySeconds, String text) {
        if (delaySeconds <= 0) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active) return;
            broadcast(Component.text(text, NamedTextColor.YELLOW));
            playSoundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.5f);
        }, delaySeconds * 20L);
    }

    // ── Placeholders helpers ──────────────────────────────────────────────────

    /**
     * Segundos restantes (0 si no hay intervalo activo).
     */
    public int getSecondsLeft() {
        if (!active) return 0;
        long remaining = endMillis - System.currentTimeMillis();
        return (int) Math.max(0, remaining / 1000);
    }

    /**
     * Tiempo restante formateado mm:ss (o "00:00" si no activo).
     */
    public String getTimeLeftFormatted() {
        if (!active) return "00:00";
        int s = getSecondsLeft();
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    /**
     * Porcentaje restante 100 → 0 (para bossbar).
     */
    public int getPercentLeft() {
        if (!active || totalSeconds <= 0) return 0;
        int left = getSecondsLeft();
        return (int) Math.round((left / (double) totalSeconds) * 100);
    }

    public int getTotalSeconds() { return totalSeconds; }

    // ── Parseo de duración (5m, 30s, 2m30s, 1h, 1h30m) ────────────────────────

    /**
     * Parsea strings de duración tipo "5m", "30s", "2m30s", "1h30m".
     * Devuelve segundos totales, o -1 si es inválido.
     */
    public static int parseDuration(String input) {
        if (input == null || input.isEmpty()) return -1;
        input = input.toLowerCase().trim();

        int total = 0;
        StringBuilder num = new StringBuilder();

        for (char c : input.toCharArray()) {
            if (Character.isDigit(c)) {
                num.append(c);
            } else if (c == 'h' || c == 'm' || c == 's') {
                if (num.length() == 0) return -1;
                int value;
                try { value = Integer.parseInt(num.toString()); }
                catch (NumberFormatException e) { return -1; }
                num.setLength(0);
                total += switch (c) {
                    case 'h' -> value * 3600;
                    case 'm' -> value * 60;
                    case 's' -> value;
                    default  -> 0;
                };
            } else {
                return -1;
            }
        }

        // Si quedaron dígitos sin sufijo, asumimos segundos
        if (num.length() > 0) {
            try { total += Integer.parseInt(num.toString()); }
            catch (NumberFormatException e) { return -1; }
        }

        return total > 0 ? total : -1;
    }

    /**
     * Formato humano: "5 minutos", "1 hora 30 minutos", "45 segundos".
     */
    public static String formatDuration(int seconds) {
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append(h == 1 ? " hora" : " horas");
        if (m > 0) { if (sb.length() > 0) sb.append(" "); sb.append(m).append(m == 1 ? " minuto" : " minutos"); }
        if (s > 0 && h == 0) { if (sb.length() > 0) sb.append(" "); sb.append(s).append(s == 1 ? " segundo" : " segundos"); }
        return sb.length() > 0 ? sb.toString() : "0 segundos";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void broadcast(Component msg) {
        for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(msg);
    }

    private void playSoundAll(Sound sound, float volume, float pitch) {
        for (Player p : Bukkit.getOnlinePlayers())
            p.playSound(p.getLocation(), sound, volume, pitch);
    }
}