package org.falmdev.anieventmanager.minigames.boatracing;

import org.bukkit.entity.EntityType;

public enum BoatType {
    OAK      (EntityType.OAK_BOAT),
    SPRUCE   (EntityType.SPRUCE_BOAT),
    BIRCH    (EntityType.BIRCH_BOAT),
    JUNGLE   (EntityType.JUNGLE_BOAT),
    ACACIA   (EntityType.ACACIA_BOAT),
    DARK_OAK (EntityType.DARK_OAK_BOAT),
    MANGROVE (EntityType.MANGROVE_BOAT),
    BAMBOO   (EntityType.BAMBOO_RAFT);

    private final EntityType entityType;

    BoatType(EntityType entityType) {
        this.entityType = entityType;
    }

    public EntityType getEntityType() { return entityType; }

    public static BoatType fromString(String s) {
        try { return valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) { return OAK; }
    }

    public static java.util.List<String> names() {
        java.util.List<String> list = new java.util.ArrayList<>();
        for (BoatType t : values()) list.add(t.name());
        return list;
    }
}