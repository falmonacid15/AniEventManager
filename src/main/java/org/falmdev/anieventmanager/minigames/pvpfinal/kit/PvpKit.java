package org.falmdev.anieventmanager.minigames.pvpfinal.kit;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Kit de PvP — snapshot del inventario completo de un jugador.
 *
 * Almacena:
 *  - 36 slots de hotbar + inventario principal
 *  - 4 slots de armadura
 *  - 1 slot de offhand
 *
 * La serialización usa el formato nativo de Bukkit ItemStack (NBT completo),
 * preservando encantamientos, items custom, lore, durabilidad, etc.
 */
public class PvpKit {

    private final String      name;
    private final ItemStack[] contents;   // slots 0-35
    private final ItemStack[] armor;      // helmet, chest, leggings, boots
    private final ItemStack   offhand;

    public PvpKit(String name, ItemStack[] contents, ItemStack[] armor, ItemStack offhand) {
        this.name     = name;
        this.contents = contents;
        this.armor    = armor;
        this.offhand  = offhand;
    }

    public String getName()           { return name; }
    public ItemStack[] getContents()  { return contents; }
    public ItemStack[] getArmor()     { return armor; }
    public ItemStack getOffhand()     { return offhand; }

    /**
     * Crea un kit a partir del estado actual del inventario del jugador.
     */
    public static PvpKit captureFrom(String name, Player player) {
        PlayerInventory inv = player.getInventory();

        ItemStack[] contents = new ItemStack[36];
        for (int i = 0; i < 36; i++) {
            ItemStack item = inv.getItem(i);
            contents[i] = item == null ? null : item.clone();
        }

        ItemStack[] armor = new ItemStack[4];
        ItemStack[] armorContents = inv.getArmorContents();
        for (int i = 0; i < 4; i++) {
            armor[i] = armorContents[i] == null ? null : armorContents[i].clone();
        }

        ItemStack offhand = inv.getItemInOffHand();
        offhand = (offhand == null || offhand.getType() == org.bukkit.Material.AIR)
                ? null : offhand.clone();

        return new PvpKit(name, contents, armor, offhand);
    }

    /**
     * Aplica este kit al inventario del jugador.
     * Previamente se debe vaciar el inventario (lo hace KitManager.apply).
     */
    public void applyTo(Player player) {
        PlayerInventory inv = player.getInventory();

        for (int i = 0; i < 36; i++) {
            if (contents[i] != null) {
                inv.setItem(i, contents[i].clone());
            }
        }

        ItemStack[] armorCopy = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            armorCopy[i] = armor[i] == null ? null : armor[i].clone();
        }
        inv.setArmorContents(armorCopy);

        if (offhand != null) {
            inv.setItemInOffHand(offhand.clone());
        }

        player.updateInventory();
    }
}