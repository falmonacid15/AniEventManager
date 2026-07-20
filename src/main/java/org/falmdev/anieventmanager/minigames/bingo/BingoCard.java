package org.falmdev.anieventmanager.minigames.bingo;

import org.falmdev.anieventmanager.model.EventTeam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BingoCard {

    private static final int SIZE = 5;

    private final EventTeam team;
    private final List<BingoTask> tasks;

    public BingoCard(EventTeam team, List<BingoTask> tasks) {
        this.team  = team;
        this.tasks = new ArrayList<>(tasks);
    }

    public boolean isComplete() {
        return tasks.stream().allMatch(BingoTask::isCompleted);
    }

    public int getCompletionPercent() {
        if (tasks.isEmpty()) return 0;
        long done = tasks.stream().filter(BingoTask::isCompleted).count();
        return (int) ((done * 100) / tasks.size());
    }

    public int getCompletedCount() {
        return (int) tasks.stream().filter(BingoTask::isCompleted).count();
    }

    public BingoTask getTask(int index) {
        if (index < 0 || index >= tasks.size()) return null;
        return tasks.get(index);
    }

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