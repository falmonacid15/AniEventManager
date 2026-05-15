package org.falmdev.anieventmanager.minigames.bingo;

import org.falmdev.anieventmanager.model.EventTeam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tarjeta de bingo 5x5 compartida por un equipo.
 * Contiene 25 tareas organizadas en una grilla.
 *
 * Victoria:
 *   - Tarjeta completa (25/25), O
 *   - Mayor porcentaje al vencer el tiempo
 */
public class BingoCard {

    private static final int SIZE = 5; // 5x5 = 25 casillas

    private final EventTeam team;
    private final List<BingoTask> tasks; // 25 tareas en orden de grilla

    public BingoCard(EventTeam team, List<BingoTask> tasks) {
        this.team  = team;
        this.tasks = new ArrayList<>(tasks);
    }

    // ── Victoria ──────────────────────────────────────────────────────────────

    /**
     * Devuelve true si la tarjeta está completamente completa (25/25).
     */
    public boolean isComplete() {
        return tasks.stream().allMatch(BingoTask::isCompleted);
    }

    /**
     * Porcentaje completado de 0 a 100.
     */
    public int getCompletionPercent() {
        if (tasks.isEmpty()) return 0;
        long done = tasks.stream().filter(BingoTask::isCompleted).count();
        return (int) ((done * 100) / tasks.size());
    }

    /**
     * Cantidad de tareas completadas.
     */
    public int getCompletedCount() {
        return (int) tasks.stream().filter(BingoTask::isCompleted).count();
    }

    // ── Acceso a tareas ───────────────────────────────────────────────────────

    public BingoTask getTask(int index) {
        if (index < 0 || index >= tasks.size()) return null;
        return tasks.get(index);
    }

    /**
     * Busca una tarea por su id.
     */
    public BingoTask findById(String id) {
        return tasks.stream()
                .filter(t -> t.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    public List<BingoTask> getTasks()   { return Collections.unmodifiableList(tasks); }
    public EventTeam       getTeam()    { return team; }
    public int             getSize()    { return SIZE; }
    public int             getTotalTasks() { return tasks.size(); }
}