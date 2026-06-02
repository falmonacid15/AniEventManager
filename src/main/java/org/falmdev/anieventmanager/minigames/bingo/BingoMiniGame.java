package org.falmdev.anieventmanager.minigames.bingo;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.scheduler.BukkitTask;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.managers.MiniGame;
import org.falmdev.anieventmanager.model.EventTeam;

import java.time.Duration;
import java.util.*;

public class BingoMiniGame implements MiniGame {

    public enum State { IDLE, COUNTDOWN, RUNNING, FINISHED }

    private final Anieventmanager plugin;
    private final BingoConfig config;
    private BingoListener gameListener;

    private State state = State.IDLE;

    private final Map<String, BingoCard> cards = new HashMap<>();

    private BukkitTask timerTask;
    private BukkitTask countdownTask;
    private int timeLeftSeconds;
    private boolean finishing = false;

    public BingoMiniGame(Anieventmanager plugin) {
        this.plugin = plugin;
        this.config = new BingoConfig(plugin);
    }

    // ── MiniGame interface ────────────────────────────────────────────────────

    @Override public String getId()          { return "bingo"; }
    @Override public String getDisplayName() { return "Bingo"; }
    @Override public String getStateName()   { return state.name(); }
    @Override public boolean isIdle()        { return state == State.IDLE; }

    @Override
    public boolean isRunning() {
        return state == State.RUNNING || state == State.COUNTDOWN;
    }

    /** sendToLobby en Bingo equivale a sendToSpawn. */
    @Override
    public boolean sendToLobby() {
        return sendToSpawn();
    }

    @Override
    public void reloadConfig() {
        config.reload();
    }

    @Override
    public String validateConfig() {
        return config.validate();
    }

    // ── Ciclo de vida ─────────────────────────────────────────────────────────

    public boolean sendToSpawn() {
        Location spawn = config.getSpawn();
        if (spawn == null) return false;

        for (EventTeam team : plugin.getTeamManager().getAllTeams()) {
            for (var p : team.getOnlinePlayers()) {
                p.teleport(spawn);
                p.setGameMode(GameMode.ADVENTURE);
            }
        }
        return true;
    }

    @Override
    public boolean start() {
        if (state != State.IDLE) return false;

        String error = config.validate();
        if (error != null) return false;

        Collection<EventTeam> teams = plugin.getTeamManager().getAllTeams();
        if (teams.isEmpty()) return false;

        finishing = false;
        cards.clear();

        Location spawn = config.getSpawn();
        if (spawn != null) {
            for (EventTeam team : teams) {
                for (var p : team.getOnlinePlayers()) {
                    p.teleport(spawn);
                    p.setGameMode(GameMode.SURVIVAL);
                }
            }
        }

        List<BingoTask> taskPool = config.loadTasks();
        for (EventTeam team : teams) {
            cards.put(team.getId(), new BingoCard(team, cloneTasks(taskPool)));
        }

        if (gameListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(gameListener);
            gameListener = null;
        }

        gameListener = new BingoListener(plugin, this);
        plugin.getServer().getPluginManager().registerEvents(gameListener, plugin);

        state = State.COUNTDOWN;
        showIntroAndCountdown();
        return true;
    }

    @Override
    public void forceStop() {
        cancelTasks();
        if (gameListener != null) {
            gameListener.stopLocationCheck();
            org.bukkit.event.HandlerList.unregisterAll(gameListener);
            gameListener = null;
        }
        broadcastAll(Component.text("El Bingo fue detenido por un admin.", NamedTextColor.RED));
        Location endSpawn = config.getSpawn();
        for (EventTeam team : plugin.getTeamManager().getAllTeams()) {
            for (var p : team.getOnlinePlayers()) {
                p.getInventory().clear();
                if (endSpawn != null) p.teleport(endSpawn);
            }
        }
        state = State.FINISHED;
        cards.clear();
        Bukkit.getScheduler().runTaskLater(plugin, () -> state = State.IDLE, 20L);
    }

    // ── Intro + Countdown ─────────────────────────────────────────────────────

    private void showIntroAndCountdown() {
        Title intro = Title.title(
                Component.text("BINGO", NamedTextColor.GOLD),
                Component.text("Completa todas las tareas antes de que acabe el tiempo",
                        NamedTextColor.YELLOW),
                Title.Times.times(
                        Duration.ofMillis(300),
                        Duration.ofSeconds(3),
                        Duration.ofMillis(700)
                )
        );
        Bukkit.getOnlinePlayers().forEach(p -> p.showTitle(intro));
        broadcastAll(Component.text("━━━ BINGO ━━━", NamedTextColor.GOLD));
        broadcastAll(Component.text("Completa todas las tareas de tu tarjeta antes de que acabe el tiempo.",
                NamedTextColor.YELLOW));
        broadcastAll(Component.text("Usa ", NamedTextColor.GRAY)
                .append(Component.text("/bingo", NamedTextColor.WHITE))
                .append(Component.text(" para ver tu tarjeta en cualquier momento.", NamedTextColor.GRAY)));

        Bukkit.getScheduler().runTaskLater(plugin, this::startCountdown, 80L);
    }

    private void startCountdown() {
        int[] seconds = { config.getCountdownSeconds() };

        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (seconds[0] <= 0) {
                countdownTask.cancel();
                beginGame();
                return;
            }

            Title countdown = Title.title(
                    Component.text(String.valueOf(seconds[0]), NamedTextColor.YELLOW),
                    Component.text("¡Prepárate!", NamedTextColor.GRAY),
                    Title.Times.times(
                            Duration.ofMillis(100),
                            Duration.ofMillis(800),
                            Duration.ofMillis(100)
                    )
            );
            Bukkit.getOnlinePlayers().forEach(p -> p.showTitle(countdown));
            seconds[0]--;
        }, 0L, 20L);
    }

    private void beginGame() {
        plugin.getBingoWallManager().clearAllWalls();
        state = State.RUNNING;
        for (EventTeam team : plugin.getTeamManager().getAllTeams()) {
            for (var p : team.getOnlinePlayers()) {
                giveKit(p, team);
            }
        }
        gameListener.startLocationCheck();

        Title go = Title.title(
                Component.text("¡YA!", NamedTextColor.GREEN),
                Component.text("¡Completa tu tarjeta!", NamedTextColor.WHITE),
                Title.Times.times(
                        Duration.ofMillis(100),
                        Duration.ofMillis(1000),
                        Duration.ofMillis(200)
                )
        );
        Bukkit.getOnlinePlayers().forEach(p -> p.showTitle(go));
        broadcastAll(Component.text("━━━ ¡BINGO COMENZÓ! ━━━", NamedTextColor.GREEN));
        broadcastAll(Component.text("Tiempo: " + config.getDurationMinutes()
                + " minutos.", NamedTextColor.GRAY));

        timeLeftSeconds = config.getDurationMinutes() * 60;
        startTimer();
    }

    // ── Timer ─────────────────────────────────────────────────────────────────

    private void startTimer() {
        timerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            timeLeftSeconds--;

            if (timeLeftSeconds == 600)
                broadcastAll(Component.text("⏱ Quedan 10 minutos.", NamedTextColor.YELLOW));
            else if (timeLeftSeconds == 300)
                broadcastAll(Component.text("⏱ Quedan 5 minutos.", NamedTextColor.YELLOW));
            else if (timeLeftSeconds == 60)
                broadcastAll(Component.text("⏱ ¡Queda 1 minuto!", NamedTextColor.RED));
            else if (timeLeftSeconds <= 10 && timeLeftSeconds > 0)
                broadcastAll(Component.text("⏱ " + timeLeftSeconds + "...", NamedTextColor.RED));

            if (timeLeftSeconds <= 0) {
                timerTask.cancel();
                broadcastAll(Component.text("⏱ ¡Se acabó el tiempo!", NamedTextColor.RED));
                finishByTime();
            }
        }, 20L, 20L);
    }

    private void finishByTime() {
        if (finishing) return;

        List<EventTeam> ranking = plugin.getTeamManager().getAllTeams().stream()
                .filter(t -> cards.containsKey(t.getId()))
                .sorted((a, b) -> {
                    int pa = cards.get(a.getId()).getCompletionPercent();
                    int pb = cards.get(b.getId()).getCompletionPercent();
                    return Integer.compare(pb, pa);
                })
                .toList();

        broadcastAll(Component.text("━━━ Resultado Final ━━━", NamedTextColor.GOLD));
        for (int i = 0; i < ranking.size(); i++) {
            EventTeam team = ranking.get(i);
            int pct  = cards.get(team.getId()).getCompletionPercent();
            int done = cards.get(team.getId()).getCompletedCount();
            broadcastAll(Component.text("  " + (i + 1) + ". ", NamedTextColor.GRAY)
                    .append(Component.text(team.getDisplayName(), team.getColor()))
                    .append(Component.text(" — " + done + "/" + cards.get(team.getId()).getTotalTasks()
                            + " (" + pct + "%)", NamedTextColor.YELLOW)));
        }

        finish(new ArrayList<>(ranking));
    }

    // ── Victoria ──────────────────────────────────────────────────────────────

    public void checkWinCondition(EventTeam team, BingoCard card) {
        if (finishing || state != State.RUNNING) return;
        if (!card.isComplete()) return;

        finishing = true;
        if (timerTask != null && !timerTask.isCancelled()) timerTask.cancel();

        broadcastAll(Component.text("🎉 ¡", NamedTextColor.GOLD)
                .append(Component.text(team.getDisplayName(), team.getColor()))
                .append(Component.text(" completó el BINGO!", NamedTextColor.GOLD)));

        Title winTitle = Title.title(
                Component.text("🎉 BINGO!", NamedTextColor.GOLD),
                Component.text(team.getDisplayName(), team.getColor()),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500))
        );
        Bukkit.getOnlinePlayers().forEach(p -> p.showTitle(winTitle));

        List<EventTeam> ranking = new ArrayList<>();
        ranking.add(team);
        plugin.getTeamManager().getAllTeams().stream()
                .filter(t -> !t.getId().equals(team.getId()))
                .sorted((a, b) -> {
                    int pa = cards.getOrDefault(a.getId(), dummyCard(a)).getCompletionPercent();
                    int pb = cards.getOrDefault(b.getId(), dummyCard(b)).getCompletionPercent();
                    return Integer.compare(pb, pa);
                })
                .forEach(ranking::add);

        Bukkit.getScheduler().runTaskLater(plugin, () -> finish(ranking), 80L);
    }

    // ── Fin ───────────────────────────────────────────────────────────────────

    private void finish(List<EventTeam> ranking) {
        cancelTasks();
        if (gameListener != null) {
            gameListener.stopLocationCheck();
            org.bukkit.event.HandlerList.unregisterAll(gameListener);
            gameListener = null;
        }
        state = State.FINISHED;
        cards.clear();

        Location endSpawn = config.getSpawn();
        for (EventTeam team : plugin.getTeamManager().getAllTeams()) {
            for (var p : team.getOnlinePlayers()) {
                p.getInventory().clear();
                if (endSpawn != null) p.teleport(endSpawn);
            }
        }

        broadcastAll(Component.text("━━━ Puntajes ━━━", NamedTextColor.GOLD));
        for (int i = 0; i < ranking.size(); i++) {
            EventTeam team = ranking.get(i);
            int score = config.getScoreForPlace(i + 1);
            plugin.getScoreManager().addScore(team, score);
            broadcastAll(Component.text("  +" + score + " pts → ", NamedTextColor.YELLOW)
                    .append(Component.text(team.getDisplayName(), team.getColor())));
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> state = State.IDLE, 20L);
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    public BingoCard getCard(EventTeam team) { return cards.get(team.getId()); }
    public State     getState()              { return state; }
    public BingoConfig getConfig()           { return config; }
    public int       getTimeLeftSeconds()    { return timeLeftSeconds; }

    public String getTimeLeftFormatted() {
        int mins = timeLeftSeconds / 60;
        int secs = timeLeftSeconds % 60;
        return String.format("%02d:%02d", mins, secs);
    }

    public int getTotalTimeSeconds() {
        return config.getDurationMinutes() * 60;
    }

    private void broadcastAll(Component msg) {
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(msg));
    }

    private void cancelTasks() {
        if (timerTask     != null && !timerTask.isCancelled())     timerTask.cancel();
        if (countdownTask != null && !countdownTask.isCancelled()) countdownTask.cancel();
    }

    private List<BingoTask> cloneTasks(List<BingoTask> original) {
        List<BingoTask> cloned = new ArrayList<>();
        for (BingoTask t : original) {
            BingoTask clone = new BingoTask(t.getId(), t.getType(), t.getDisplayName());
            clone.setMaterial(t.getMaterial());
            clone.setAmount(t.getAmount());
            clone.setMobType(t.getMobType());
            clone.setMobCount(t.getMobCount());
            clone.setIcon(t.getIcon());
            clone.setDescription(t.getDescription());
            clone.setLocation(t.getLocationWorld(),
                    t.getLocationX(), t.getLocationY(), t.getLocationZ(),
                    t.getLocationRadius());
            clone.setStructureKey(t.getStructureKey());
            cloned.add(clone);
        }
        return cloned;
    }

    private void giveKit(org.bukkit.entity.Player player, EventTeam team) {
        player.getInventory().clear();

        NamedTextColor color = team.getColor();
        TrimMaterial trimMat;
        if      (color == NamedTextColor.RED)          trimMat = Registry.TRIM_MATERIAL.get(org.bukkit.NamespacedKey.minecraft("redstone"));
        else if (color == NamedTextColor.BLUE)         trimMat = Registry.TRIM_MATERIAL.get(org.bukkit.NamespacedKey.minecraft("lapis"));
        else if (color == NamedTextColor.GREEN)        trimMat = Registry.TRIM_MATERIAL.get(org.bukkit.NamespacedKey.minecraft("emerald"));
        else if (color == NamedTextColor.YELLOW)       trimMat = Registry.TRIM_MATERIAL.get(org.bukkit.NamespacedKey.minecraft("gold"));
        else if (color == NamedTextColor.LIGHT_PURPLE) trimMat = Registry.TRIM_MATERIAL.get(org.bukkit.NamespacedKey.minecraft("amethyst"));
        else if (color == NamedTextColor.AQUA)         trimMat = Registry.TRIM_MATERIAL.get(org.bukkit.NamespacedKey.minecraft("diamond"));
        else if (color == NamedTextColor.GOLD)         trimMat = Registry.TRIM_MATERIAL.get(org.bukkit.NamespacedKey.minecraft("copper"));
        else                                           trimMat = Registry.TRIM_MATERIAL.get(org.bukkit.NamespacedKey.minecraft("iron"));

        TrimPattern silencePattern = Registry.TRIM_PATTERN.get(org.bukkit.NamespacedKey.minecraft("silence"));
        ArmorTrim trim = new ArmorTrim(trimMat, silencePattern);

        ItemStack helmet = new ItemStack(Material.NETHERITE_HELMET);
        ArmorMeta helmetMeta = (ArmorMeta) helmet.getItemMeta();
        helmetMeta.setTrim(trim);
        helmetMeta.addEnchant(org.bukkit.enchantments.Enchantment.PROTECTION,    4, true);
        helmetMeta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING,    3, true);
        helmetMeta.addEnchant(org.bukkit.enchantments.Enchantment.RESPIRATION,   3, true);
        helmetMeta.addEnchant(org.bukkit.enchantments.Enchantment.AQUA_AFFINITY, 1, true);
        helmet.setItemMeta(helmetMeta);

        ItemStack chestplate = new ItemStack(Material.NETHERITE_CHESTPLATE);
        ArmorMeta chestMeta = (ArmorMeta) chestplate.getItemMeta();
        chestMeta.setTrim(trim);
        chestMeta.addEnchant(org.bukkit.enchantments.Enchantment.PROTECTION, 4, true);
        chestMeta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 3, true);
        chestplate.setItemMeta(chestMeta);

        ItemStack leggings = new ItemStack(Material.NETHERITE_LEGGINGS);
        ArmorMeta legMeta = (ArmorMeta) leggings.getItemMeta();
        legMeta.setTrim(trim);
        legMeta.addEnchant(org.bukkit.enchantments.Enchantment.PROTECTION,  4, true);
        legMeta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING,  3, true);
        legMeta.addEnchant(org.bukkit.enchantments.Enchantment.SWIFT_SNEAK, 3, true);
        leggings.setItemMeta(legMeta);

        ItemStack boots = new ItemStack(Material.NETHERITE_BOOTS);
        ArmorMeta bootsMeta = (ArmorMeta) boots.getItemMeta();
        bootsMeta.setTrim(trim);
        bootsMeta.addEnchant(org.bukkit.enchantments.Enchantment.PROTECTION,      4, true);
        bootsMeta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING,      3, true);
        bootsMeta.addEnchant(org.bukkit.enchantments.Enchantment.FEATHER_FALLING, 4, true);
        bootsMeta.addEnchant(org.bukkit.enchantments.Enchantment.DEPTH_STRIDER,   3, true);
        boots.setItemMeta(bootsMeta);

        player.getInventory().setHelmet(helmet);
        player.getInventory().setChestplate(chestplate);
        player.getInventory().setLeggings(leggings);
        player.getInventory().setBoots(boots);

        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        var swordMeta = sword.getItemMeta();
        swordMeta.addEnchant(org.bukkit.enchantments.Enchantment.SHARPNESS,    5, true);
        swordMeta.addEnchant(org.bukkit.enchantments.Enchantment.SWEEPING_EDGE,3, true);
        swordMeta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING,   3, true);
        swordMeta.addEnchant(org.bukkit.enchantments.Enchantment.LOOTING,      3, true);
        sword.setItemMeta(swordMeta);

        ItemStack axe = new ItemStack(Material.NETHERITE_AXE);
        var axeMeta = axe.getItemMeta();
        axeMeta.addEnchant(org.bukkit.enchantments.Enchantment.SHARPNESS,  5, true);
        axeMeta.addEnchant(org.bukkit.enchantments.Enchantment.EFFICIENCY, 5, true);
        axeMeta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 3, true);
        axe.setItemMeta(axeMeta);

        ItemStack pickaxe = new ItemStack(Material.NETHERITE_PICKAXE);
        var pickMeta = pickaxe.getItemMeta();
        pickMeta.addEnchant(org.bukkit.enchantments.Enchantment.EFFICIENCY, 5, true);
        pickMeta.addEnchant(org.bukkit.enchantments.Enchantment.FORTUNE,    3, true);
        pickMeta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 3, true);
        pickaxe.setItemMeta(pickMeta);

        ItemStack shovel = new ItemStack(Material.NETHERITE_SHOVEL);
        var shovelMeta = shovel.getItemMeta();
        shovelMeta.addEnchant(org.bukkit.enchantments.Enchantment.EFFICIENCY, 5, true);
        shovelMeta.addEnchant(org.bukkit.enchantments.Enchantment.FORTUNE,    3, true);
        shovelMeta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 3, true);
        shovel.setItemMeta(shovelMeta);

        player.getInventory().addItem(
                sword, axe, pickaxe, shovel,
                new ItemStack(Material.GOLDEN_CARROT, 64),
                new ItemStack(Material.TORCH, 64)
        );
    }

    public void openAdminGUI(Player player) {
        plugin.getBingoAdminGUI().open(player);
    }

    private BingoCard dummyCard(EventTeam team) {
        return new BingoCard(team, new ArrayList<>());
    }
}