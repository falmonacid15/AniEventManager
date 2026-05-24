package org.falmdev.anieventmanager.managers;

/**
 * Contrato común que deben implementar todos los minijuegos del plugin.
 *
 * El {@link MiniGameManager} opera exclusivamente a través de esta interfaz,
 * lo que permite agregar nuevos minijuegos sin tocar el manager.
 *
 * Ciclo de vida esperado:
 *   IDLE ──► (sendToLobby) ──► LOBBY ──► (start) ──► RUNNING ──► FINISHED ──► IDLE
 *   En cualquier momento: forceStop() → IDLE
 */
public interface MiniGame {

    // ── Identificación ────────────────────────────────────────────────────────

    /**
     * ID único del minijuego, en minúsculas sin espacios. Ej: "tntrun", "bingo".
     * Usado como clave en el {@link MiniGameManager}.
     */
    String getId();

    /**
     * Nombre para mostrar en mensajes y comandos. Ej: "TNT Run", "Bingo".
     */
    String getDisplayName();

    // ── Estado ────────────────────────────────────────────────────────────────

    /**
     * Nombre del estado actual. El formato exacto lo define cada minijuego,
     * pero se recomienda devolver el nombre del enum (IDLE, LOBBY, RUNNING…).
     */
    String getStateName();

    /**
     * true si hay una partida actualmente en curso (RUNNING o COUNTDOWN).
     * Equivale a comprobar si el minijuego necesita ser detenido antes de
     * realizar cambios estructurales (regenerar arena, recargar config…).
     */
    boolean isRunning();

    /**
     * true si el minijuego está inactivo y listo para recibir un nuevo inicio.
     * Equivale a state == IDLE.
     */
    boolean isIdle();

    // ── Ciclo de vida ─────────────────────────────────────────────────────────

    /**
     * Mueve a los jugadores al lobby previo al inicio.
     * @return true si la operación fue exitosa, false si la configuración
     *         no está completa o el estado no lo permite.
     */
    boolean sendToLobby();

    /**
     * Inicia la partida.
     * @return true si la partida arrancó correctamente.
     */
    boolean start();

    /**
     * Detiene la partida inmediatamente, sin importar el estado actual.
     * Devuelve a todos los jugadores a un estado seguro.
     */
    void forceStop();

    // ── Configuración ─────────────────────────────────────────────────────────

    /**
     * Recarga la configuración desde disco.
     * Si hay una partida en curso, los cambios deben aplicar a la próxima.
     */
    void reloadConfig();

    /**
     * Valida que la configuración mínima esté completa para poder iniciar.
     * @return null si todo está bien, o un mensaje de error descriptivo.
     */
    String validateConfig();
}