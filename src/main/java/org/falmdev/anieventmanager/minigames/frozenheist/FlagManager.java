package org.falmdev.anieventmanager.minigames.frozenheist;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.utils.TeamColorUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * Gestiona el estado de todas las banderas en la partida.
 *
 * Estados:
 *   IN_BASE  → bandera en su stand original
 *   CARRIED  → jugador lleva la bandera
 *   DROPPED  → bandera en el suelo (item entity)
 *
 * Auto-return: si una bandera está DROPPED por más de FLAG_TIMEOUT_MS,
 * vuelve automáticamente a su base.
 */
public class FlagManager {

    // MODIFICAR AQUI para cambiar el tiempo de auto-retorno de banderas
    public static final long FLAG_TIMEOUT_MS = 12000; // 12 segundos en el suelo

    public enum FlagState { IN_BASE, CARRIED, DROPPED }

    private final Anieventmanager plugin;
    private final Map<String, TeamHeistData> teamData; // teamId → data

    // Estado actual de cada bandera
    private final Map<String, FlagState>  flagStates   = new HashMap<>();
    // Quién lleva cada bandera
    private final Map<String, java.util.UUID> flagCarrier  = new HashMap<>();
    // Item entity cuando está DROPPED
    private final Map<String, Item>       flagItems    = new HashMap<>();
    // Task de auto-retorno
    private final Map<String, BukkitTask> returnTasks  = new HashMap<>();

    private final Map<String, org.bukkit.entity.ArmorStand> flagStands = new HashMap<>();

    // Task del círculo de partículas en la base (por equipo)
    private final Map<String, BukkitTask> baseParticleTasks  = new HashMap<>();
    // Task de partículas subiendo al item dropeado (por equipo)
    private final Map<String, BukkitTask> dropParticleTasks  = new HashMap<>();


    public FlagManager(Anieventmanager plugin, Map<String, TeamHeistData> teamData) {
        this.plugin   = plugin;
        this.teamData = teamData;
    }

    // ── Inicialización ────────────────────────────────────────────────────────

    /** Inicializa todas las banderas en estado IN_BASE al comenzar */
    public void initAll() {
        for (String teamId : teamData.keySet()) {
            flagStates.put(teamId, FlagState.IN_BASE);
            flagCarrier.remove(teamId);
            flagItems.remove(teamId);
            spawnFlagStand(teamId);
        }
    }

    private void startBaseParticles(String teamId) {
        stopBaseParticles(teamId);

        TeamHeistData data = teamData.get(teamId);
        if (data == null || data.getFlagStand() == null) return;

        org.falmdev.anieventmanager.model.EventTeam team = data.getTeam();
        net.kyori.adventure.text.format.NamedTextColor textColor = team.getColor();

        // Convertir NamedTextColor a RGB para las partículas de redstone
        float r, g, b;
        if (textColor == NamedTextColor.RED)          { r=1f;    g=0f;    b=0f;    }
        else if (textColor == NamedTextColor.BLUE)    { r=0f;    g=0f;    b=1f;    }
        else if (textColor == NamedTextColor.GREEN)   { r=0f;    g=0.5f;  b=0f;    }
        else if (textColor == NamedTextColor.YELLOW)  { r=1f;    g=1f;    b=0f;    }
        else if (textColor == NamedTextColor.LIGHT_PURPLE) { r=1f; g=0.33f; b=1f;  }
        else if (textColor == NamedTextColor.AQUA)    { r=0f;    g=1f;    b=1f;    }
        else if (textColor == NamedTextColor.GOLD)    { r=1f;    g=0.67f; b=0f;    }
        else                                          { r=1f;    g=1f;    b=1f;    }

        Location center = data.getFlagStand().clone();
        // Y + 0.1 para que las partículas queden justo sobre el suelo
        center.setY(center.getY() + 0.1);

        final float fr = r, fg = g, fb = b;
        final int POINTS = 24;   // puntos del círculo
        final double RADIUS = 1.8;
        final double STEP = (2 * Math.PI) / POINTS;

        // Offset rotatorio para animar el círculo girando lentamente
        final double[] angleOffset = {0};

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Si la bandera ya no está en la base, no dibujar el círculo
            if (flagStates.getOrDefault(teamId, FlagState.IN_BASE) != FlagState.IN_BASE) return;

            angleOffset[0] += 0.08; // velocidad de rotación

            for (int i = 0; i < POINTS; i++) {
                double angle = STEP * i + angleOffset[0];
                double x = center.getX() + RADIUS * Math.cos(angle);
                double z = center.getZ() + RADIUS * Math.sin(angle);
                Location particleLoc = new Location(center.getWorld(), x, center.getY(), z);

                center.getWorld().spawnParticle(
                        org.bukkit.Particle.DUST,
                        particleLoc,
                        1,       // count
                        0, 0, 0, // offset
                        0,       // speed (ignorado para DUST)
                        new org.bukkit.Particle.DustOptions(
                                org.bukkit.Color.fromRGB(
                                        (int)(fr * 255),
                                        (int)(fg * 255),
                                        (int)(fb * 255)),
                                1.2f) // tamaño de la partícula
                );
            }
        }, 0L, 2L); // cada 2 ticks = 10 veces por segundo, fluido sin ser costoso

        baseParticleTasks.put(teamId, task);
    }

    private void stopBaseParticles(String teamId) {
        BukkitTask task = baseParticleTasks.remove(teamId);
        if (task != null && !task.isCancelled()) task.cancel();
    }

    private void startDropParticles(String teamId) {
        stopDropParticles(teamId);

        TeamHeistData data = teamData.get(teamId);
        org.falmdev.anieventmanager.model.EventTeam team = data != null ? data.getTeam() : null;

        float r, g, b;
        if (team == null) { r=1f; g=1f; b=1f; }
        else {
            net.kyori.adventure.text.format.NamedTextColor textColor = team.getColor();
            if (textColor == NamedTextColor.RED)               { r=1f;    g=0f;    b=0f;   }
            else if (textColor == NamedTextColor.BLUE)         { r=0f;    g=0f;    b=1f;   }
            else if (textColor == NamedTextColor.GREEN)        { r=0f;    g=0.5f;  b=0f;   }
            else if (textColor == NamedTextColor.YELLOW)       { r=1f;    g=1f;    b=0f;   }
            else if (textColor == NamedTextColor.LIGHT_PURPLE) { r=1f;    g=0.33f; b=1f;   }
            else if (textColor == NamedTextColor.AQUA)         { r=0f;    g=1f;    b=1f;   }
            else if (textColor == NamedTextColor.GOLD)         { r=1f;    g=0.67f; b=0f;   }
            else                                               { r=1f;    g=1f;    b=1f;   }
        }

        final float fr = r, fg = g, fb = b;

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Item item = flagItems.get(teamId);
            // Cancelar si el item ya no existe
            if (item == null || item.isDead()) {
                stopDropParticles(teamId);
                return;
            }

            Location loc = item.getLocation().clone().add(0, 0.2, 0);

            // Partículas subiendo — emitimos en distintas alturas para efecto columna
            for (int i = 0; i < 3; i++) {
                Location pLoc = loc.clone().add(
                        (Math.random() - 0.5) * 0.3,  // pequeño spread en X
                        i * 0.25,                       // escalonadas en altura
                        (Math.random() - 0.5) * 0.3   // pequeño spread en Z
                );
                loc.getWorld().spawnParticle(
                        org.bukkit.Particle.DUST,
                        pLoc,
                        1,
                        0, 0, 0,
                        0,
                        new org.bukkit.Particle.DustOptions(
                                org.bukkit.Color.fromRGB(
                                        (int)(fr * 255),
                                        (int)(fg * 255),
                                        (int)(fb * 255)),
                                1.5f) // un poco más grandes para que se vean entre el caos
                );
            }
        }, 0L, 3L); // cada 3 ticks — balance entre visibilidad y rendimiento

        dropParticleTasks.put(teamId, task);
    }

    private void stopDropParticles(String teamId) {
        BukkitTask task = dropParticleTasks.remove(teamId);
        if (task != null && !task.isCancelled()) task.cancel();
    }

    private void spawnFlagStand(String teamId) {
        // Remover el anterior si existía
        removeFlagStand(teamId);

        TeamHeistData data = teamData.get(teamId);
        if (data == null || data.getFlagStand() == null) return;

        Location loc = data.getFlagStand().clone();
        loc.setY(loc.getY() - 1.75);

        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(
                org.falmdev.anieventmanager.Anieventmanager.getInstance(), "flag_stand_owner");


        loc.getWorld().spawn(loc, org.bukkit.entity.ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setCollidable(false);
            stand.setSmall(false);
            stand.getEquipment().setHelmet(buildFlagItem(teamId, data.getTeam()));
            // Guardar referencia en PersistentData para identificarlo si hace falta
            stand.getPersistentDataContainer().set(
                    key,
                    org.bukkit.persistence.PersistentDataType.STRING,
                    teamId
            );
            flagStands.put(teamId, stand);
        });

        // Guardamos la referencia buscando el stand recién spawneado
        // (el consumer se ejecuta antes de que spawn() retorne, así que buscamos en el chunk)
        loc.getWorld().getNearbyEntities(loc, 1, 1, 1).stream()
                .filter(e -> e instanceof org.bukkit.entity.ArmorStand)
                .filter(e -> e.getPersistentDataContainer().has(
                        new org.bukkit.NamespacedKey(
                                org.falmdev.anieventmanager.Anieventmanager.getInstance(), "flag_stand_owner"),
                        org.bukkit.persistence.PersistentDataType.STRING))
                .map(e -> (org.bukkit.entity.ArmorStand) e)
                .findFirst()
                .ifPresent(stand -> flagStands.put(teamId, stand));

        startBaseParticles(teamId);
    }

    private void removeFlagStand(String teamId) {
        stopBaseParticles(teamId);
        org.bukkit.entity.ArmorStand stand = flagStands.remove(teamId);
        if (stand != null && !stand.isDead()) stand.remove();
    }

    // ── Pickup ────────────────────────────────────────────────────────────────

    /**
     * Intenta que un jugador tome una bandera.
     * Devuelve true si la tomó.
     * Lógica:
     *  - Si es bandera enemiga → ROBAR (portador recibe slowness)
     *  - Si es bandera propia  → RECUPERAR (portador recibe speed boost)
     */
    public boolean tryPickup(java.util.UUID playerUUID, String ownerTeamId,
                             String playerTeamId, PlayerState ps) {
        FlagState state = flagStates.get(ownerTeamId);
        if (state == null || state == FlagState.CARRIED) return false;
        if (ps.isCarryingFlag()) return false;

        boolean isOwnFlag = ownerTeamId.equals(playerTeamId);

        if (isOwnFlag && state == FlagState.IN_BASE) return false;

        cancelReturnTask(ownerTeamId);
        stopDropParticles(ownerTeamId);
        removeFlagStand(ownerTeamId);

        Item item = flagItems.remove(ownerTeamId);
        if (item != null && !item.isDead()) item.remove();

        flagStates.put(ownerTeamId, FlagState.CARRIED);
        flagCarrier.put(ownerTeamId, playerUUID);
        ps.setCarryingFlag(ownerTeamId);
        return true;
    }

    // ── Drop ──────────────────────────────────────────────────────────────────

    /**
     * Suelta la bandera al suelo en la ubicación dada.
     * Programa el auto-retorno.
     */
    public void dropFlag(String ownerTeamId, Location dropLoc) {
        flagStates.put(ownerTeamId, FlagState.DROPPED);
        flagCarrier.remove(ownerTeamId);

        // ── FIX 2: Desplazar el drop ~2 bloques en dirección aleatoria para
        //           que el jugador congelado no la vuelva a recoger al instante.
        Location spawnLoc = dropLoc.clone();
        double angle = Math.random() * 3 * Math.PI;
        spawnLoc.add(Math.cos(angle) * 3.0, 0.3, Math.sin(angle) * 3.0);

        TeamHeistData data = teamData.get(ownerTeamId);
        Item item = spawnLoc.getWorld().dropItem(spawnLoc,
                buildFlagItem(ownerTeamId, data != null ? data.getTeam() : null));
        item.setPickupDelay(40); // 2s de delay (era 1s, damos margen extra)

        item.setGravity(true);
        item.setVelocity(new org.bukkit.util.Vector(0, 0, 0)); // sin impulso extra
        flagItems.put(ownerTeamId, item);

        startDropParticles(ownerTeamId);

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            returnToBase(ownerTeamId);
            broadcastAll(Component.text("🚩 La bandera de ", NamedTextColor.YELLOW)
                    .append(Component.text(
                            data != null ? data.getTeam().getDisplayName() : ownerTeamId,
                            data != null ? data.getTeam().getColor() : NamedTextColor.WHITE))
                    .append(Component.text(" volvió a su base.", NamedTextColor.YELLOW)));
        }, (FLAG_TIMEOUT_MS / 50));
        returnTasks.put(ownerTeamId, task);
    }

    // ── Return to base ────────────────────────────────────────────────────────

    /** Devuelve la bandera a su base (limpia item, cancela tasks) */
    public void returnToBase(String ownerTeamId) {
        cancelReturnTask(ownerTeamId);
        stopDropParticles(ownerTeamId);
        Item item = flagItems.remove(ownerTeamId);
        if (item != null && !item.isDead()) item.remove();
        flagCarrier.remove(ownerTeamId);
        flagStates.put(ownerTeamId, FlagState.IN_BASE);
        spawnFlagStand(ownerTeamId);
    }

    /** Devuelve todas las banderas a sus bases (al terminar la partida) */
    public void returnAll() {
        for (String teamId : teamData.keySet()) {
            stopBaseParticles(teamId);
            stopDropParticles(teamId);
            returnToBase(teamId);
            removeFlagStand(teamId);
        }
    }

    // ── Consultas ─────────────────────────────────────────────────────────────

    public FlagState getState(String ownerTeamId) {
        return flagStates.getOrDefault(ownerTeamId, FlagState.IN_BASE);
    }

    public boolean isInBase(String ownerTeamId) {
        return getState(ownerTeamId) == FlagState.IN_BASE;
    }

    public java.util.UUID getCarrier(String ownerTeamId) {
        return flagCarrier.get(ownerTeamId);
    }

    /**
     * Devuelve el teamId de la bandera más cercana al jugador dentro del radio dado.
     * Busca entre banderas IN_BASE y DROPPED.
     */
    public String getNearbyFlag(Location loc, double radius) {
        double radiusSq = radius * radius;
        for (Map.Entry<String, TeamHeistData> entry : teamData.entrySet()) {
            String teamId = entry.getKey();
            FlagState state = flagStates.getOrDefault(teamId, FlagState.IN_BASE);

            if (state == FlagState.CARRIED) continue;

            if (state == FlagState.IN_BASE) {
                Location stand = entry.getValue().getFlagStand();
                if (stand != null && stand.getWorld().getName().equals(loc.getWorld().getName())
                        && stand.distanceSquared(loc) <= radiusSq) {
                    return teamId;
                }
            } else if (state == FlagState.DROPPED) {
                Item item = flagItems.get(teamId);
                if (item != null && !item.isDead()
                        && item.getLocation().getWorld().getName().equals(loc.getWorld().getName())
                        && item.getLocation().distanceSquared(loc) <= radiusSq) {
                    return teamId;
                }
            }
        }
        return null;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ItemStack buildFlagItem(String ownerTeamId, org.falmdev.anieventmanager.model.EventTeam team) {
        // Elegir el material de banner según el color del equipo
        DyeColor dyeColor = team != null
                ? TeamColorUtil.toDyeColor(team.getColor())
                : DyeColor.WHITE;

        Material bannerMaterial = getBannerMaterial(dyeColor);
        ItemStack item = new ItemStack(bannerMaterial);
        org.bukkit.inventory.meta.BannerMeta meta =
                (org.bukkit.inventory.meta.BannerMeta) item.getItemMeta();

        String name = team != null
                ? team.getDisplayName() + " — Bandera"
                : ownerTeamId + " — Bandera";
        meta.displayName(Component.text("🚩 " + name,
                team != null ? team.getColor() : NamedTextColor.WHITE));

        meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(
                        org.falmdev.anieventmanager.Anieventmanager.getInstance(), "flag_owner"),
                org.bukkit.persistence.PersistentDataType.STRING,
                ownerTeamId
        );
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack buildPublicFlagItem(String ownerTeamId,
                                         org.falmdev.anieventmanager.model.EventTeam team) {
        return buildFlagItem(ownerTeamId, team);
    }

    private Material getBannerMaterial(DyeColor color) {
        return switch (color) {
            case RED    -> Material.RED_BANNER;
            case BLUE   -> Material.BLUE_BANNER;
            case GREEN  -> Material.GREEN_BANNER;
            case YELLOW -> Material.YELLOW_BANNER;
            case PINK   -> Material.PINK_BANNER;
            case CYAN   -> Material.CYAN_BANNER;
            case ORANGE -> Material.ORANGE_BANNER;
            case WHITE  -> Material.WHITE_BANNER;
            default     -> Material.WHITE_BANNER;
        };
    }

    private void cancelReturnTask(String teamId) {
        BukkitTask task = returnTasks.remove(teamId);
        if (task != null && !task.isCancelled()) task.cancel();
    }

    private void broadcastAll(Component msg) {
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(msg));
    }
}