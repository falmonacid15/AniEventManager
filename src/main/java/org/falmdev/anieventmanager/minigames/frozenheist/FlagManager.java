package org.falmdev.anieventmanager.minigames.frozenheist;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.utils.TeamColorUtil;

import java.util.HashMap;
import java.util.Map;


public class FlagManager {

    public static final long FLAG_TIMEOUT_MS = 12000;

    public enum FlagState { IN_BASE, CARRIED, DROPPED }

    private final Anieventmanager plugin;
    private final Map<String, TeamHeistData> teamData;

    private final Map<String, FlagState>  flagStates   = new HashMap<>();
    private final Map<String, java.util.UUID> flagCarrier  = new HashMap<>();
    private final Map<String, Item>       flagItems    = new HashMap<>();
    private final Map<String, BukkitTask> returnTasks  = new HashMap<>();

    private final Map<String, org.bukkit.entity.ArmorStand> flagStands = new HashMap<>();

    private final Map<String, BukkitTask> baseParticleTasks  = new HashMap<>();
    private final Map<String, BukkitTask> dropParticleTasks  = new HashMap<>();

    private final Map<java.util.UUID, String> glowingCarriers = new HashMap<>();


    public FlagManager(Anieventmanager plugin, Map<String, TeamHeistData> teamData) {
        this.plugin   = plugin;
        this.teamData = teamData;
    }

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
        center.setY(center.getY() + 0.1);

        final float fr = r, fg = g, fb = b;
        final int POINTS = 24;
        final double RADIUS = 1.8;
        final double STEP = (2 * Math.PI) / POINTS;

        final double[] angleOffset = {0};

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (flagStates.getOrDefault(teamId, FlagState.IN_BASE) != FlagState.IN_BASE) return;

            angleOffset[0] += 0.08;

            for (int i = 0; i < POINTS; i++) {
                double angle = STEP * i + angleOffset[0];
                double x = center.getX() + RADIUS * Math.cos(angle);
                double z = center.getZ() + RADIUS * Math.sin(angle);
                Location particleLoc = new Location(center.getWorld(), x, center.getY(), z);

                center.getWorld().spawnParticle(
                        org.bukkit.Particle.DUST,
                        particleLoc,
                        1,
                        0, 0, 0,
                        0,
                        new org.bukkit.Particle.DustOptions(
                                org.bukkit.Color.fromRGB(
                                        (int)(fr * 255),
                                        (int)(fg * 255),
                                        (int)(fb * 255)),
                                1.2f)
                );
            }
        }, 0L, 2L);

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

        final org.bukkit.Color color = org.bukkit.Color.fromRGB(
                (int) (r * 255), (int) (g * 255), (int) (b * 255));

        final int RINGS = 3;
        final int POINTS_PER_RING = 8;
        final double BASE_RADIUS = 1.1;
        final double RING_HEIGHT_STEP = 0.55;

        final double[] angleOffset = {0};

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Item item = flagItems.get(teamId);
            if (item == null || item.isDead()) {
                stopDropParticles(teamId);
                return;
            }

            Location base = item.getLocation();
            angleOffset[0] += 0.12;

            for (int ring = 0; ring < RINGS; ring++) {
                double radius = BASE_RADIUS - (ring * 0.2);
                double height = 0.2 + (ring * RING_HEIGHT_STEP);
                double ringOffset = angleOffset[0] + (ring * 0.5);

                for (int i = 0; i < POINTS_PER_RING; i++) {
                    double angle = (2 * Math.PI / POINTS_PER_RING) * i + ringOffset;
                    double x = base.getX() + radius * Math.cos(angle);
                    double z = base.getZ() + radius * Math.sin(angle);
                    Location particleLoc = new Location(base.getWorld(), x, base.getY() + height, z);

                    base.getWorld().spawnParticle(
                            org.bukkit.Particle.DUST,
                            particleLoc,
                            1, 0, 0, 0, 0,
                            new org.bukkit.Particle.DustOptions(color, 3.2f)
                    );
                }
            }

            base.getWorld().spawnParticle(
                    org.bukkit.Particle.DUST,
                    base.clone().add(0, RINGS * RING_HEIGHT_STEP + 0.4, 0),
                    1, 0, 0, 0, 0,
                    new org.bukkit.Particle.DustOptions(color, 4.0f)
            );
        }, 0L, 3L);

        dropParticleTasks.put(teamId, task);
    }

    private void stopDropParticles(String teamId) {
        BukkitTask task = dropParticleTasks.remove(teamId);
        if (task != null && !task.isCancelled()) task.cancel();
    }

    private void spawnFlagStand(String teamId) {
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
            stand.getPersistentDataContainer().set(
                    key,
                    org.bukkit.persistence.PersistentDataType.STRING,
                    teamId
            );
            flagStands.put(teamId, stand);
        });

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
        if (item != null && !item.isDead()) {
            clearItemGlow(ownerTeamId, item);
            item.remove();
        }

        flagStates.put(ownerTeamId, FlagState.CARRIED);
        flagCarrier.put(ownerTeamId, playerUUID);
        ps.setCarryingFlag(ownerTeamId);
        return true;
    }

    public void dropFlag(String ownerTeamId, Location dropLoc) {
        flagStates.put(ownerTeamId, FlagState.DROPPED);
        flagCarrier.remove(ownerTeamId);

        Location spawnLoc = dropLoc.clone();
        double angle = Math.random() * 3 * Math.PI;
        spawnLoc.add(Math.cos(angle) * 3.0, 0.3, Math.sin(angle) * 3.0);

        TeamHeistData data = teamData.get(ownerTeamId);
        Item item = spawnLoc.getWorld().dropItem(spawnLoc,
                buildFlagItem(ownerTeamId, data != null ? data.getTeam() : null));
        item.setPickupDelay(40);

        item.setGravity(true);
        item.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        flagItems.put(ownerTeamId, item);

        applyItemGlow(item, ownerTeamId);
        startDropParticles(ownerTeamId);

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            returnToBase(ownerTeamId);
            if (data != null) {
                Component msg = Component.text("🚩 Tu bandera volvió a la base.", NamedTextColor.YELLOW);
                data.getTeam().getOnlinePlayers().forEach(p -> p.sendMessage(msg));
            }
        }, (FLAG_TIMEOUT_MS / 50));
        returnTasks.put(ownerTeamId, task);
    }

    public void returnToBase(String ownerTeamId) {
        cancelReturnTask(ownerTeamId);
        stopDropParticles(ownerTeamId);
        Item item = flagItems.remove(ownerTeamId);
        if (item != null && !item.isDead()) {
            clearItemGlow(ownerTeamId, item);
            item.remove();
        }
        flagCarrier.remove(ownerTeamId);
        flagStates.put(ownerTeamId, FlagState.IN_BASE);
        spawnFlagStand(ownerTeamId);
    }

    public void returnAll() {
        for (String teamId : teamData.keySet()) {
            stopBaseParticles(teamId);
            stopDropParticles(teamId);
            returnToBase(teamId);
            removeFlagStand(teamId);
        }
    }

    public FlagState getState(String ownerTeamId) {
        return flagStates.getOrDefault(ownerTeamId, FlagState.IN_BASE);
    }

    public boolean isInBase(String ownerTeamId) {
        return getState(ownerTeamId) == FlagState.IN_BASE;
    }

    public java.util.UUID getCarrier(String ownerTeamId) {
        return flagCarrier.get(ownerTeamId);
    }

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

    public void applyCarrierGlow(Player player, String flagTeamId) {
        clearCarrierGlow(player);

        org.bukkit.scoreboard.Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        org.bukkit.scoreboard.Team team = getOrCreateGlowTeam(scoreboard, flagTeamId);
        team.addEntry(player.getName());
        player.setGlowing(true);
        glowingCarriers.put(player.getUniqueId(), flagTeamId);
    }

    public void clearCarrierGlow(Player player) {
        String previousTeamId = glowingCarriers.remove(player.getUniqueId());
        player.setGlowing(false);
        if (previousTeamId == null) return;

        org.bukkit.scoreboard.Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        org.bukkit.scoreboard.Team team = scoreboard.getTeam(glowTeamName(previousTeamId));
        if (team != null) team.removeEntry(player.getName());
    }

    private void applyItemGlow(Item item, String flagTeamId) {
        org.bukkit.scoreboard.Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        org.bukkit.scoreboard.Team team = getOrCreateGlowTeam(scoreboard, flagTeamId);
        team.addEntry(item.getUniqueId().toString());
        item.setGlowing(true);
    }

    private void clearItemGlow(String flagTeamId, Item item) {
        if (item == null) return;
        org.bukkit.scoreboard.Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        org.bukkit.scoreboard.Team team = scoreboard.getTeam(glowTeamName(flagTeamId));
        if (team != null) team.removeEntry(item.getUniqueId().toString());
    }

    private org.bukkit.scoreboard.Team getOrCreateGlowTeam(org.bukkit.scoreboard.Scoreboard scoreboard, String flagTeamId) {
        String name = glowTeamName(flagTeamId);
        org.bukkit.scoreboard.Team team = scoreboard.getTeam(name);
        if (team == null) {
            team = scoreboard.registerNewTeam(name);
        }
        TeamHeistData data = teamData.get(flagTeamId);
        if (data != null) team.color(data.getTeam().getColor());
        return team;
    }

    private String glowTeamName(String flagTeamId) {
        return "fh_glow_" + flagTeamId;
    }

    private ItemStack buildFlagItem(String ownerTeamId, org.falmdev.anieventmanager.model.EventTeam team) {
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
        meta.setEnchantmentGlintOverride(true);
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
}