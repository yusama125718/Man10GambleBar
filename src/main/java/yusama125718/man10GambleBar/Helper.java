package yusama125718.man10GambleBar;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static net.kyori.adventure.text.event.ClickEvent.runCommand;
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
            p.sendMessage(prefix + "§cシステムは停止中です");
            return false;
        }
        return true;
    }

    public static void SendDrinkRanking(Player p, Liquor liq, int page){
        // DB処理はスレッドで行う
        Thread th = new Thread(() -> {
            List<Component> messages = new ArrayList<>();
            messages.add(Component.text(prefix + "§e§l飲んだ数ランキング"));
            messages.add(Component.text(liq.displayName));
            int cnt = 1;

            try (Connection c = mysql.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT mcid, COUNT(*) AS drink_count, COUNT(CASE WHEN win_table IS NOT NULL THEN 1 END) AS win_count " +
                         "FROM bar_drink_log WHERE liquor_name = ? GROUP BY mcid ORDER BY drink_count DESC LIMIT ? OFFSET ?"
                 )
            ) {
                ps.setString(1, liq.name);
                ps.setInt(2, 10);
                ps.setInt(3, Math.max(page - 1, 0) * 10);
                try (ResultSet set = ps.executeQuery()) {
                    while (set.next()){
                        String mcid = set.getString("mcid");
                        int drinkCount = set.getInt("drink_count");
                        messages.add(Component.text("§e§l" + ((page - 1) * 10 + cnt) + "位：§c§l" + mcid + "§r§f  " + drinkCount + "本"));
                        cnt++;
                    }
                }
            } catch (SQLException error) {
                Bukkit.getScheduler().runTask(mgbar, () -> p.sendMessage(Component.text(prefix + "§cDBの参照に失敗しました")));
                mgbar.getLogger().severe("Failed to load drink ranking for liquor: " + liq.name);
                mgbar.getLogger().severe(error.getMessage());
                return;
            }

            final int finalCnt = cnt;
            Bukkit.getScheduler().runTask(mgbar, () -> {
                for (Component message : messages) {
                    p.sendMessage(message);
                }
                if (page != 1) p.sendMessage(Component.text("§b§l§n[前のページ]").clickEvent(runCommand("/mgbar rank " + liq.name + " " + (page - 1))));
                if (finalCnt != 1) p.sendMessage(Component.text("§b§l§n[次のページ]").clickEvent(runCommand("/mgbar rank " + liq.name + " " + (page + 1))));
            });
        });
        th.start();
    }
}
