package org.falmdev.anieventmanager.minigames.bingo;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

/**
 * Representa una casilla de la tarjeta de bingo.
 *
 * Tipos soportados:
 *   OBTAIN_ITEM   → Tener X cantidad de un ítem en el inventario
 *   CRAFT_ITEM    → Craftear X cantidad de un ítem
 *   KILL_MOB      → Matar X cantidad de un tipo de mob
 *   REACH_LOCATION→ Llegar a unas coordenadas dentro de un radio
 *   EQUIP_ITEM    → Equipar un ítem específico en el slot de armadura
 *   FISH_ITEM     → Pescar un ítem específico
 */
public class BingoTask {

    public enum Type {
        OBTAIN_ITEM,
        CRAFT_ITEM,
        KILL_MOB,
        REACH_LOCATION,
        EQUIP_ITEM,
        FISH_ITEM
    }

    private final String id;
    private final Type type;
    private String displayName;

    // Icono personalizado que se muestra en el GUI (null = usar cristal por defecto)
    private Material icon = null;

    // Para OBTAIN_ITEM, CRAFT_ITEM, EQUIP_ITEM, FISH_ITEM
    private Material material = Material.STONE;
    private int amount = 1;

    // Para KILL_MOB
    private EntityType mobType = EntityType.ZOMBIE;
    private int mobCount = 1;

    // Para REACH_LOCATION
    private String locationWorld = "";
    private double locationX, locationY, locationZ;
    private double locationRadius = 5.0;

    // Progreso actual (para OBTAIN, CRAFT, KILL)
    private int progress = 0;
    private boolean completed = false;

    public BingoTask(String id, Type type, String displayName) {
        this.id          = id;
        this.type        = type;
        this.displayName = displayName;
    }

    // ── Progreso ──────────────────────────────────────────────────────────────

    /**
     * Incrementa el progreso. Devuelve true si se completa con este incremento.
     */
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

    /**
     * Marca la tarea como completa directamente.
     * Usado para REACH_LOCATION, EQUIP_ITEM y FISH_ITEM.
     */
    public boolean complete() {
        if (completed) return false;
        completed = true;
        progress  = getRequired();
        return true;
    }

    /**
     * Cantidad requerida según el tipo de tarea.
     */
    public int getRequired() {
        return switch (type) {
            case KILL_MOB -> mobCount;
            default       -> amount;
        };
    }

    /**
     * Porcentaje de completado (0-100).
     */
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

    public Material    getIcon()         { return icon; }
    public void        setIcon(Material m) { this.icon = m; }
    public boolean     hasCustomIcon()   { return icon != null; }

    public Material    getMaterial()     { return material; }
    public void        setMaterial(Material m) { this.material = m; }

    public int         getAmount()       { return amount; }
    public void        setAmount(int a)  { this.amount = a; }

    public EntityType  getMobType()      { return mobType; }
    public void        setMobType(EntityType t) { this.mobType = t; }

    public int         getMobCount()     { return mobCount; }
    public void        setMobCount(int c){ this.mobCount = c; }

    public String      getLocationWorld()  { return locationWorld; }
    public double      getLocationX()      { return locationX; }
    public double      getLocationY()      { return locationY; }
    public double      getLocationZ()      { return locationZ; }
    public double      getLocationRadius() { return locationRadius; }

    public void setLocation(String world, double x, double y, double z, double radius) {
        this.locationWorld  = world;
        this.locationX      = x;
        this.locationY      = y;
        this.locationZ      = z;
        this.locationRadius = radius;
    }

    public int     getProgress()  { return progress; }
    public boolean isCompleted()  { return completed; }

    /**
     * Descripción corta para mostrar en el GUI (máx 2 líneas).
     */
    public String getShortDescription() {
        return switch (type) {
            case OBTAIN_ITEM    -> "Obtener x" + amount + " " + prettyMaterial();
            case CRAFT_ITEM     -> "Craftear x" + amount + " " + prettyMaterial();
            case KILL_MOB       -> "Matar " + mobCount + " " + prettyMob();
            case REACH_LOCATION -> "Llegar a " + displayName;
            case EQUIP_ITEM     -> "Equipar " + prettyMaterial();
            case FISH_ITEM      -> "Pescar " + prettyMaterial();
        };
    }

    private String prettyMaterial() {
        return material.name().toLowerCase().replace('_', ' ');
    }

    private String prettyMob() {
        return mobType.name().toLowerCase().replace('_', ' ');
    }
}