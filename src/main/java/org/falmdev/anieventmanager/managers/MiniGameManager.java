package org.falmdev.anieventmanager.managers;

import org.falmdev.anieventmanager.Anieventmanager;

import java.util.*;
import java.util.logging.Logger;

/**
 * Registro central de todos los minijuegos del plugin.
 *
 * Responsabilidades:
 *   - Almacenar y recuperar instancias de {@link MiniGame} por ID.
 *   - Proveer operaciones en lote: reloadAll, stopAll, getRunning.
 *   - Garantizar que no haya dos partidas activas al mismo tiempo
 *     (si se activa {@code exclusiveMode}).
 *
 * Uso típico en {@link Anieventmanager#onEnable}:
 * <pre>
 *   miniGameManager.register(tntRunMiniGame);
 *   miniGameManager.register(bingoMiniGame);
 *   // …
 * </pre>
 *
 * Luego en comandos:
 * <pre>
 *   miniGameManager.get("tntrun").ifPresent(mg -> mg.start());
 *   miniGameManager.getRunning().forEach(MiniGame::forceStop);
 * </pre>
 */
public class MiniGameManager {

    private final Anieventmanager plugin;
    private final Logger log;

    /**
     * Mapa ordenado: id → instancia. El orden de inserción se preserva para
     * mostrar listas consistentes en comandos de ayuda.
     */
    private final LinkedHashMap<String, MiniGame> registry = new LinkedHashMap<>();

    /**
     * Si es true, {@link #start(String)} cancela cualquier partida activa antes
     * de iniciar la nueva. Si es false, permite varias partidas simultáneas.
     * Por defecto: false (cada minijuego decide su propio estado).
     */
    private boolean exclusiveMode = false;

    public MiniGameManager(Anieventmanager plugin) {
        this.plugin = plugin;
        this.log    = plugin.getLogger();
    }

    // ── Registro ──────────────────────────────────────────────────────────────

    /**
     * Registra un minijuego. Si ya existe uno con el mismo ID, lo reemplaza
     * y emite una advertencia.
     */
    public void register(MiniGame miniGame) {
        String id = miniGame.getId().toLowerCase();
        if (registry.containsKey(id)) {
            log.warning("[MiniGameManager] Ya existía un minijuego con id '" + id
                    + "', será reemplazado.");
        }
        registry.put(id, miniGame);
        log.info("[MiniGameManager] Registrado: " + miniGame.getDisplayName()
                + " (id=" + id + ")");
    }

    /**
     * Elimina un minijuego del registro. Si estaba en curso, lo detiene primero.
     */
    public void unregister(String id) {
        MiniGame mg = registry.remove(id.toLowerCase());
        if (mg == null) return;
        if (mg.isRunning()) mg.forceStop();
        log.info("[MiniGameManager] Desregistrado: " + mg.getDisplayName());
    }

    // ── Acceso ────────────────────────────────────────────────────────────────

    /** Devuelve el minijuego con el ID dado, o {@link Optional#empty()}. */
    public Optional<MiniGame> get(String id) {
        return Optional.ofNullable(registry.get(id.toLowerCase()));
    }

    /** Devuelve todos los minijuegos registrados (orden de inserción). */
    public Collection<MiniGame> getAll() {
        return Collections.unmodifiableCollection(registry.values());
    }

    /** Devuelve los IDs de todos los minijuegos registrados. */
    public Set<String> getIds() {
        return Collections.unmodifiableSet(registry.keySet());
    }

    /** Devuelve los minijuegos que están actualmente en curso. */
    public List<MiniGame> getRunning() {
        return registry.values().stream()
                .filter(MiniGame::isRunning)
                .toList();
    }

    /** true si hay al menos un minijuego en curso. */
    public boolean anyRunning() {
        return registry.values().stream().anyMatch(MiniGame::isRunning);
    }

    // ── Operaciones de ciclo de vida ──────────────────────────────────────────

    /**
     * Intenta iniciar el minijuego con el ID dado.
     *
     * Si {@code exclusiveMode} está activo, detiene cualquier partida en curso
     * antes de iniciar la nueva.
     *
     * @return true si la partida arrancó, false si el ID no existe,
     *         la validación falla o el minijuego ya estaba corriendo.
     */
    public boolean start(String id) {
        MiniGame mg = registry.get(id.toLowerCase());
        if (mg == null) {
            log.warning("[MiniGameManager] start(): minijuego '" + id + "' no encontrado.");
            return false;
        }

        String validation = mg.validateConfig();
        if (validation != null) {
            log.warning("[MiniGameManager] start() bloqueado para '" + id
                    + "': " + validation);
            return false;
        }

        if (mg.isRunning()) {
            log.info("[MiniGameManager] start() ignorado para '" + id
                    + "': ya está en curso.");
            return false;
        }

        if (exclusiveMode) {
            getRunning().forEach(running -> {
                log.info("[MiniGameManager] Modo exclusivo — deteniendo '"
                        + running.getId() + "' antes de iniciar '" + id + "'.");
                running.forceStop();
            });
        }

        boolean ok = mg.start();
        if (ok) {
            log.info("[MiniGameManager] Iniciado: " + mg.getDisplayName());
        } else {
            log.warning("[MiniGameManager] start() retornó false para '"
                    + id + "'.");
        }
        return ok;
    }

    /**
     * Detiene el minijuego con el ID dado si está en curso.
     *
     * @return true si fue detenido, false si no existía o no estaba corriendo.
     */
    public boolean stop(String id) {
        MiniGame mg = registry.get(id.toLowerCase());
        if (mg == null || !mg.isRunning()) return false;
        mg.forceStop();
        log.info("[MiniGameManager] Detenido: " + mg.getDisplayName());
        return true;
    }

    /**
     * Detiene todos los minijuegos en curso.
     */
    public void stopAll() {
        getRunning().forEach(mg -> {
            mg.forceStop();
            log.info("[MiniGameManager] Detenido (stopAll): " + mg.getDisplayName());
        });
    }

    /**
     * Recarga la configuración de todos los minijuegos registrados.
     * Si un minijuego está en curso, la recarga se delega a él
     * (cada implementación decide si aplica de inmediato o en la próxima partida).
     *
     * @return lista de minijuegos que fueron recargados.
     */
    public List<String> reloadAll() {
        List<String> reloaded = new ArrayList<>();
        for (MiniGame mg : registry.values()) {
            mg.reloadConfig();
            reloaded.add(mg.getDisplayName());
            log.info("[MiniGameManager] Config recargada: " + mg.getDisplayName()
                    + (mg.isRunning() ? " (en curso — aplica a la próxima partida)" : ""));
        }
        return reloaded;
    }

    /**
     * Recarga la configuración de un único minijuego.
     *
     * @return true si existía y fue recargado.
     */
    public boolean reload(String id) {
        MiniGame mg = registry.get(id.toLowerCase());
        if (mg == null) return false;
        mg.reloadConfig();
        return true;
    }

    // ── Modo exclusivo ────────────────────────────────────────────────────────

    public boolean isExclusiveMode()         { return exclusiveMode; }
    public void setExclusiveMode(boolean v)  { this.exclusiveMode = v; }

    // ── Info / debug ──────────────────────────────────────────────────────────

    /**
     * Devuelve un resumen de texto de todos los minijuegos registrados.
     * Útil para el comando /em status o logs de inicio.
     */
    public List<String> getSummary() {
        List<String> lines = new ArrayList<>();
        for (MiniGame mg : registry.values()) {
            lines.add(String.format("  %-14s [%s]  valid=%s",
                    mg.getDisplayName(),
                    mg.getStateName(),
                    mg.validateConfig() == null ? "✔" : "✘"));
        }
        return lines;
    }
}