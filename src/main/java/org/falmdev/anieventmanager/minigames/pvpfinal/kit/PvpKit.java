package org.falmdev.anieventmanager.minigames.pvpfinal.kit;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class PvpKit {

    private final String      name;
    private final ItemStack[] contents;
    private final ItemStack[] armor;
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