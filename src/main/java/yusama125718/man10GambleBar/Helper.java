package yusama125718.man10GambleBar;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.ResultSet;
import java.sql.SQLException;

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
            MySQLManager mysql = new MySQLManager(mgbar, "man10_gamble_bar");
            try {
                // 使用可能か確認
                String query = "SELECT mcid, COUNT(*) AS drink_count, COUNT(CASE WHEN win_table IS NOT NULL THEN 1 END) AS win_count FROM bar_drink_log WHERE liquor_name = '" + liq.name + "' GROUP BY mcid ORDER BY drink_count DESC LIMIT 10 OFFSET " + (page - 1) * 10 + ";";
                ResultSet set = mysql.query(query);
                p.sendMessage(Component.text(prefix + "§e§l飲んだ数ランキング"));
                p.sendMessage(liq.displayName);
                int cnt = 1;
                while (set.next()){
                    String win_count = String.valueOf(set.getInt("win_count"));
                    if (liq.hide_win_count) win_count = "**";
                    p.sendMessage(Component.text("§e§l" + ((page - 1) * 10) + cnt + "位：§c§l" + set.getString("mcid") + "§r§f  " + set.getInt("drink_count") + "本（うち" + win_count + "本当選）"));
                    cnt++;
                }
                if (page != 1) p.sendMessage(Component.text("§b§l§n[前のページ]").clickEvent(runCommand("/mgbar rank " + liq.name + " " + (page - 1))));
                if (cnt != 1) p.sendMessage(Component.text("§b§l§n[次のページ]").clickEvent(runCommand("/mgbar rank " + liq.name + " " + (page + 1))));
                mysql.close();
            } catch (SQLException error) {
                p.sendMessage(Component.text(prefix + "DBの参照に失敗しました"));
                try {
                    mysql.close();
                } catch (NullPointerException throwables) {
                    throwables.printStackTrace();
                }
                throw new RuntimeException(error);
            }
        });
        th.start();
    }
}
