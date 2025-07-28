package yusama125718.man10GambleBar;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

import static yusama125718.man10GambleBar.Man10GambleBar.*;

public class Events implements Listener {
    public Events(Man10GambleBar plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // お酒を飲んだ時の処理
    @EventHandler
    public void DrinkLiquor(PlayerItemConsumeEvent e){
        ItemStack item = e.getItem();
        if (item.getItemMeta() instanceof PotionMeta){
            PotionMeta meta = (PotionMeta) item.getItemMeta();
            if (!meta.getPersistentDataContainer().has(new NamespacedKey(mgbar, "Man10GambleBar"), PersistentDataType.STRING)) return;
            String name = meta.getPersistentDataContainer().get(new NamespacedKey(mgbar, "Man10GambleBar"), PersistentDataType.STRING);
            String buy_id = meta.getPersistentDataContainer().get(new NamespacedKey(mgbar, "MGBarID"), PersistentDataType.STRING);
            if (!liquors.containsKey(name)){
                e.getPlayer().sendMessage(Component.text(prefix + "お酒が存在しません"));
                return;
            }
            Liquor liq = liquors.get(name);
            // 権限確認
            if (!e.getPlayer().hasPermission(liq.permission)) {
                e.getPlayer().sendMessage(Component.text(liq.permission_error.replace("&", "§")));
                e.setCancelled(true);
                return;
            }
            e.setCancelled(true);
            e.getPlayer().getInventory().removeItemAnySlot(e.getItem());
            // DB処理はスレッドで行う
            Thread th = new Thread(() -> {
                MySQLManager mysql = new MySQLManager(mgbar, "man10_token");
                try {
                    // 使用可能か確認
                    String query = "SELECT * FROM bar_shop_log LEFT OUTER JOIN bar_drink_log ON bar_shop_log.buy_id = bar_drink_log.buy_id WHERE bar_shop_log.buy_id = '" + buy_id + "' AND bar_drink_log.id IS NULL";
                    ResultSet set = mysql.query(query);
                    if (!set.next()) {
                           e.getPlayer().sendMessage(Component.text(prefix + "このお酒は有効でないので使用できません"));
                           mysql.close();
                           return;
                    }
                    mysql.close();
                } catch (SQLException error) {
                    e.getPlayer().sendMessage(Component.text(prefix + "DBの参照に失敗しました"));
                    try {
                        mysql.close();
                    } catch (NullPointerException throwables) {
                        throwables.printStackTrace();
                    }
                    throw new RuntimeException(error);
                }
                LiquorWin win = null;
                // 抽選処理
                int randomInt = ThreadLocalRandom.current().nextInt(0, 100000000);
                for (LiquorWin table: liq.wins) {
                    if (randomInt < table.chance) win = table;
                    randomInt -= table.chance;
                }

                // ログ保存
                String win_name = win == null ? null : win.name;
                int price = win == null ? 0 : win.price;
                String query = "INSERT INTO bar_drink_log (time, liquor_name, mcid, uuid, price, buy_id, win_table) VALUES ('" + LocalDateTime.now() + "', '" + liq.name + "', '" + e.getPlayer().getName() + "', '" + e.getPlayer().getUniqueId() + "', " + price + ", '" + buy_id + "', '" + win_name + "');";
                if (!mysql.execute(query)){
                    e.getPlayer().sendMessage(Component.text(prefix + "DBの保存に失敗しました"));
                    try {
                        mysql.close();
                    } catch (NullPointerException throwables) {
                        throwables.printStackTrace();
                    }
                }
                LiquorWin finalWin = win;
                Bukkit.getScheduler().runTask(mgbar, () -> {
                    // ハズレ時処理
                    if (finalWin == null) {
                        for (String cmd: liq.lose_commands) Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), cmd.replace("%player%", e.getPlayer().getName()));
                        for (String message: liq.lose_messages) e.getPlayer().sendMessage(Component.text(message.replace("&", "§")));
                    }
                    // 当選時処理
                    else {
                        for (String cmd: finalWin.commands) Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), cmd.replace("%player%", e.getPlayer().getName()));
                        for (String message: finalWin.messages) e.getPlayer().sendMessage(Component.text(message.replace("&", "§")));
                        vaultapi.deposit(e.getPlayer().getUniqueId(), finalWin.price);
                    }
                });
            });
            th.start();
        }
    }
}
