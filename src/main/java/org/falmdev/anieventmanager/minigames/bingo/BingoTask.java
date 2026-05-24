package org.falmdev.anieventmanager.minigames.bingo;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

public class BingoTask {

    public enum Type {
        OBTAIN_ITEM,
        CRAFT_ITEM,
        KILL_MOB,
        REACH_LOCATION,
        EQUIP_ITEM,
        FISH_ITEM,
        VISIT_STRUCTURE,
        TRADE_ANY,
        TRADE_ITEM
    }

    private final String id;
    private final Type type;
    private String displayName;
    private String structureKey = "";

    private Material icon = null;
    private String description = "";

    // Para OBTAIN_ITEM, CRAFT_ITEM, EQUIP_ITEM, FISH_ITEM, TRADE_ITEM
    private Material material = Material.STONE;
    private int amount = 1;

    // Para KILL_MOB
    private EntityType mobType = EntityType.ZOMBIE;
    private int mobCount = 1;

    // Para REACH_LOCATION
    private String locationWorld = "";
    private double locationX, locationY, locationZ;
    private double locationRadius = 5.0;

    // Progreso actual
    private int progress = 0;
    private boolean completed = false;

    public BingoTask(String id, Type type, String displayName) {
        this.id          = id;
        this.type        = type;
        this.displayName = displayName;
    }

    // ── Progreso ──────────────────────────────────────────────────────────────

    public boolean increment(int amount) {
        if (completed) return false;
        progress += amount;
        int required = getRequired();
        if (progress >= required) {
            progress = required;
            completed = true;
            return true;
        }
        return false;
    }

    public boolean complete() {
        if (completed) return false;
        completed = true;
        progress  = getRequired();
        return true;
    }

    public int getRequired() {
        return switch (type) {
            case KILL_MOB -> mobCount;
            default       -> amount;
        };
    }

    public int getProgressPercent() {
        int required = getRequired();
        if (required <= 0) return 100;
        return Math.min(100, (progress * 100) / required);
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public String      getId()           { return id; }
    public Type        getType()         { return type; }
    public String      getDisplayName()  { return displayName; }
    public void        setDisplayName(String name) { this.displayName = name; }

    public Material    getIcon()           { return icon; }
    public void        setIcon(Material m) { this.icon = m; }
    public boolean     hasCustomIcon()     { return icon != null; }

    public String  getDescription()            { return description; }
    public void    setDescription(String desc) { this.description = desc != null ? desc : ""; }
    public boolean hasDescription()            { return !description.isEmpty(); }

    public String  getStructureKey()           { return structureKey; }
    public void    setStructureKey(String key) { this.structureKey = key != null ? key : ""; }

    public Material   getMaterial()            { return material; }
    public void       setMaterial(Material m)  { this.material = m; }

    public int        getAmount()              { return amount; }
    public void       setAmount(int a)         { this.amount = a; }

    public EntityType getMobType()             { return mobType; }
    public void       setMobType(EntityType t) { this.mobType = t; }

    public int        getMobCount()            { return mobCount; }
    public void       setMobCount(int c)       { this.mobCount = c; }

    public String getLocationWorld()  { return locationWorld; }
    public double getLocationX()      { return locationX; }
    public double getLocationY()      { return locationY; }
    public double getLocationZ()      { return locationZ; }
    public double getLocationRadius() { return locationRadius; }

    public void setLocation(String world, double x, double y, double z, double radius) {
        this.locationWorld  = world;
        this.locationX      = x;
        this.locationY      = y;
        this.locationZ      = z;
        this.locationRadius = radius;
    }

    public int     getProgress()  { return progress; }
    public boolean isCompleted()  { return completed; }

    public String getShortDescription() {
        return switch (type) {
            case OBTAIN_ITEM     -> "Obtener x" + amount + " " + prettyMaterial();
            case CRAFT_ITEM      -> "Craftear x" + amount + " " + prettyMaterial();
            case KILL_MOB        -> "Matar " + mobCount + " " + prettyMob();
            case REACH_LOCATION  -> "Llegar a " + displayName;
            case EQUIP_ITEM      -> "Equipar " + prettyMaterial();
            case FISH_ITEM       -> "Pescar " + prettyMaterial();
            case VISIT_STRUCTURE -> "Visitar " + prettyStructure();
            case TRADE_ANY       -> "Tradear " + amount + " veces con aldeanos";
            case TRADE_ITEM      -> "Tradear x" + amount + " " + prettyMaterial();
        };
    }

    private String prettyMaterial() {
        return material.name().toLowerCase().replace('_', ' ');
    }

    private String prettyMob() {
        return mobType.name().toLowerCase().replace('_', ' ');
    }

    private String prettyStructure() {
        String key = structureKey.contains(":") ? structureKey.split(":")[1] : structureKey;
        return key.replace('_', ' ');
    }
}