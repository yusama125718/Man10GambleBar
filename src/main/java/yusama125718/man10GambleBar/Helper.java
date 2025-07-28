package yusama125718.man10GambleBar;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import static yusama125718.man10GambleBar.Man10GambleBar.*;

public class Helper {
    public static ItemStack GetItem(Material mate, String name, Integer cmd){
        ItemStack item = new ItemStack(mate, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        meta.setCustomModelData(cmd);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack GetItem(Material mate, Component name, Integer cmd){
        ItemStack item = new ItemStack(mate, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        meta.setCustomModelData(cmd);
        item.setItemMeta(meta);
        return item;
    }

    public static Color ColorFromString(String name) {
        return switch (name.toUpperCase()) {
            case "RED" -> Color.RED;
            case "BLUE" -> Color.BLUE;
            case "GREEN" -> Color.GREEN;
            case "YELLOW" -> Color.YELLOW;
            case "WHITE" -> Color.WHITE;
            case "BLACK" -> Color.BLACK;
            case "AQUA" -> Color.AQUA;
            case "FUCHSIA" -> Color.FUCHSIA;
            case "GRAY" -> Color.GRAY;
            case "LIME" -> Color.LIME;
            case "MAROON" -> Color.MAROON;
            case "NAVY" -> Color.NAVY;
            case "OLIVE" -> Color.OLIVE;
            case "ORANGE" -> Color.ORANGE;
            case "PURPLE" -> Color.PURPLE;
            case "SILVER" -> Color.SILVER;
            case "TEAL" -> Color.TEAL;
            default -> null; // または例外を投げる
        };
    }

    public static Boolean CheckSystem(Player p){
        if (!system){
            p.sendMessage(prefix + "システムは停止中です");
            return false;
        }
        return true;
    }
}
