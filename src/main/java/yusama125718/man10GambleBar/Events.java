package yusama125718.man10GambleBar;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;
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
            e.setCancelled(true);
            if (!Helper.CheckSystem(e.getPlayer())) return;
            if (!liquors.containsKey(name)){
                e.getPlayer().sendMessage(Component.text(prefix + "§cお酒が存在しません"));
                return;
            }
            Liquor liq = liquors.get(name);
            if (disable_worlds.contains(e.getPlayer().getWorld().getName())){
                e.getPlayer().sendMessage(Component.text(prefix + "§cこのワールドでは飲めません"));
                return;
            }
            // 権限確認
            if (!e.getPlayer().hasPermission(liq.permission)) {
                e.getPlayer().sendMessage(Component.text(liq.permission_error.replace("&", "§")));
                return;
            }
            PlayerInventory inv = e.getPlayer().getInventory();
            inv.removeItemAnySlot(item);

            // 飲んだスロットに同じお酒があれば手に持たせる
            for (ItemStack content: inv.getContents()){
                if (content == null || item.equals(content) || !content.getType().equals(Material.POTION) || !content.hasItemMeta() || !content.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(mgbar, "Man10GambleBar"), PersistentDataType.STRING)) continue;
                String str = content.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(mgbar, "Man10GambleBar"), PersistentDataType.STRING);
                if (!str.equals(name)) continue;
                inv.removeItemAnySlot(content);
                if (e.getHand().equals(EquipmentSlot.OFF_HAND)) inv.setItemInOffHand(content);
                else inv.setItemInMainHand(content.clone());
                break;
            }

            try {
                // UUIDが正しい形式か確認（SQLインジェクション対策）
                UUID.fromString(buy_id);
            } catch (IllegalArgumentException error) {
                e.getPlayer().sendMessage(Component.text(prefix + "§c不正なお酒を検知しました"));
                return;
            }
            // DB処理はスレッドで行う
            Thread th = new Thread(() -> {
                MySQLManager mysql = new MySQLManager(mgbar, "man10_gamble_bar");
                // 検証
                if (liq.verify_id){
                    try {
                        // 使用可能か確認
                        String query = "SELECT * FROM bar_shop_log LEFT OUTER JOIN bar_drink_log ON bar_shop_log.buy_id = bar_drink_log.buy_id WHERE bar_shop_log.buy_id = '" + buy_id + "' AND bar_drink_log.id IS NULL";
                        ResultSet set = mysql.query(query);
                        if (!set.next()) {
                            e.getPlayer().sendMessage(Component.text(prefix + "§cこのお酒は有効でないので使用できません"));
                            mysql.close();
                            return;
                        }
                        mysql.close();
                    } catch (SQLException error) {
                        e.getPlayer().sendMessage(Component.text(prefix + "§cDBの参照に失敗しました"));
                        try {
                            mysql.close();
                        } catch (NullPointerException throwables) {
                            throwables.printStackTrace();
                        }
                        throw new RuntimeException(error);
                    }
                }
                LiquorWin win = null;
                // 抽選処理
                int randomInt = ThreadLocalRandom.current().nextInt(0, 100000000);
                for (LiquorWin table: liq.wins) {
                    if (randomInt < table.chance) {
                        win = table;
                        break;
                    }
                    randomInt -= table.chance;
                }

                // ログ保存
                String win_name = win == null ? "NULL" : "'" + win.name + "'";
                int price = win == null ? 0 : win.price;
                String query = "INSERT INTO bar_drink_log (time, liquor_name, mcid, uuid, price, buy_id, win_table) VALUES ('" + LocalDateTime.now() + "', '" + liq.name + "', '" + e.getPlayer().getName() + "', '" + e.getPlayer().getUniqueId() + "', " + price + ", '" + buy_id + "', " + win_name + ");";
                if (!mysql.execute(query)){
                    e.getPlayer().sendMessage(Component.text(prefix + "§cDBの保存に失敗しました"));
                    try {
                        mysql.close();
                    } catch (NullPointerException throwables) {
                        throwables.printStackTrace();
                    }
                    return;
                }
                LiquorWin finalWin = win;
                Bukkit.getScheduler().runTask(mgbar, () -> {
                    // ハズレ時処理
                    if (finalWin == null) {
                        if (liq.lose_commands != null) for (String cmd: liq.lose_commands) Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), cmd.replace("%player%", e.getPlayer().getName()));
                        if (liq.lose_messages != null) for (String message: liq.lose_messages) e.getPlayer().sendMessage(Component.text(message.replace("&", "§")));
                    }
                    // 当選時処理
                    else {
                        if (finalWin.commands != null) for (String cmd: finalWin.commands) Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), cmd.replace("%player%", e.getPlayer().getName()));
                        if (finalWin.messages != null) for (String message: finalWin.messages) e.getPlayer().sendMessage(Component.text(message.replace("&", "§")));
                        vaultapi.deposit(e.getPlayer().getUniqueId(), finalWin.price);
                    }
                });
            });
            th.start();
        }
    }

    // インベントリクリック時の処理
    @EventHandler
    public void GUIClick(InventoryClickEvent e) {
        Component component = e.getView().title();
        String title = "";
        if (component instanceof TextComponent text) title = text.content();
        if (!title.startsWith("[Man10GambleBar] ")) return;
        e.setCancelled(true);
        if (!Helper.CheckSystem((Player) e.getWhoClicked())) {
            e.getWhoClicked().closeInventory();
            return;
        }
        // 数字キーなどでアイテムが取れてしまう問題の対策
        if (e.getClick().equals(ClickType.NUMBER_KEY) || e.getClick().equals(ClickType.SWAP_OFFHAND)) return;
        String page_name = title.substring(17);
        switch (page_name) {
            case "バーカウンター" -> {
                if (e.getCurrentItem() == null || !e.getCurrentItem().hasItemMeta() || !e.getCurrentItem().getItemMeta().getPersistentDataContainer().has(new NamespacedKey(mgbar, "Man10GambleBar"), PersistentDataType.STRING))
                    return;
                String liq_name = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(new NamespacedKey(mgbar, "Man10GambleBar"), PersistentDataType.STRING);
                if (!liquors.containsKey(liq_name)) return;
                Liquor liq = liquors.get(liq_name);
                if (vaultapi.getBalance(e.getWhoClicked().getUniqueId()) < liq.price) {
                    e.getWhoClicked().sendMessage(Component.text(prefix + "§c残高が不足しています！"));
                    e.getWhoClicked().closeInventory();
                    return;
                }
                if (e.getWhoClicked().getInventory().firstEmpty() == -1) {
                    e.getWhoClicked().sendMessage(Component.text(prefix + "§cインベントリが満杯です"));
                    e.getWhoClicked().closeInventory();
                    return;
                }
                UUID buy_id = UUID.randomUUID();
                String mcid = e.getWhoClicked().getName();
                String uuid = e.getWhoClicked().getUniqueId().toString();

                // DB処理はスレッドで行う
                Thread th = new Thread(() -> {
                    MySQLManager mysql = new MySQLManager(mgbar, "man10_gamble_bar");
                    String query = "INSERT INTO bar_shop_log (time, liquor_name, mcid, uuid, price, buy_id) VALUES ('" + LocalDateTime.now() + "', '" + liq.name + "', '" + mcid + "', '" + uuid + "', " + liq.price + ", '" + buy_id + "')";
                    if (!mysql.execute(query)) {
                        e.getWhoClicked().sendMessage(Component.text(prefix + "§cDBの保存に失敗しました"));
                        return;
                    }
                    // インベントリはメインスレッドでいじる
                    Bukkit.getScheduler().runTask(mgbar, () -> {
                        if (!vaultapi.withdraw(e.getWhoClicked().getUniqueId(), liq.price)) {
                            e.getWhoClicked().sendMessage(Component.text(prefix + "§c出金に失敗しました"));
                            return;
                        }
                        e.getWhoClicked().getInventory().addItem(liq.GenLiquor(buy_id).clone());
                        e.getWhoClicked().sendMessage(Component.text(prefix + "§r" + liq.displayName + "§rを購入しました"));
                    });
                });
                th.start();
            }
            case "記録メニュー" -> {
                // 前のページ
                if (45 <= e.getRawSlot() && e.getRawSlot() <= 47){

                }
                if (e.getCurrentItem() == null || !e.getCurrentItem().hasItemMeta() || !e.getCurrentItem().getItemMeta().getPersistentDataContainer().has(new NamespacedKey(mgbar, "Man10GambleBar"), PersistentDataType.STRING))
                    return;
                String liq_name = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(new NamespacedKey(mgbar, "Man10GambleBar"), PersistentDataType.STRING);
                if (!liquors.containsKey(liq_name)) return;
                Liquor liq = liquors.get(liq_name);
                GUI.OpenRecordChoice((Player) e.getWhoClicked(), liq);
            }
            case "記録種別選択" -> {
                ItemStack display = e.getInventory().getItem(4);
                if (display == null) return;
                if (e.getCurrentItem() == null || !display.hasItemMeta() || !display.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(mgbar, "Man10GambleBar"), PersistentDataType.STRING))
                    return;
                String liq_name = display.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(mgbar, "Man10GambleBar"), PersistentDataType.STRING);
                if (!liquors.containsKey(liq_name)) return;
                Liquor liq = liquors.get(liq_name);
                // 自分の記録
                if (e.getRawSlot() == 2) {
                    e.getWhoClicked().closeInventory();
                    // DB処理はスレッドで行う
                    Thread th = new Thread(() -> {
                        MySQLManager mysql = new MySQLManager(mgbar, "man10_gamble_bar");
                        try {
                            // 使用可能か確認
                            String query = "SELECT SUM(price) AS buy_total, COUNT(*) AS buy_count FROM bar_shop_log WHERE liquor_name = '" + liq_name + "' AND uuid = '" + e.getWhoClicked().getUniqueId() + "' AND price <> 0;";
                            ResultSet set = mysql.query(query);
                            int buy_price = 0;
                            int buy_count = 0;
                            if (set.next()) {
                                buy_price = set.getInt("buy_total");
                                buy_count = set.getInt("buy_count");
                            }
                            query = "SELECT SUM(price) AS win_total, COUNT(*) AS drink_count, COUNT(CASE WHEN win_table IS NOT NULL THEN 1 END) AS win_count FROM bar_drink_log WHERE liquor_name = '" + liq_name + "' AND uuid = '" + e.getWhoClicked().getUniqueId() + "';";
                            set = mysql.query(query);
                            int win_price = 0;
                            int drink_count = 0;
                            int win_count = 0;
                            if (set.next()) {
                                win_price = set.getInt("win_total");
                                drink_count = set.getInt("drink_count");
                                win_count = set.getInt("win_count");
                            }
                            mysql.close();
                            e.getWhoClicked().sendMessage(Component.text(prefix + "===== 個人ログ ====="));
                            e.getWhoClicked().sendMessage(liq.displayName);
                            e.getWhoClicked().sendMessage(Component.text("購入数：" + buy_count + "本（総額：" + buy_price + "）"));
                            e.getWhoClicked().sendMessage(Component.text("飲んだ本数：" + drink_count + "本うち" + win_count + "が当選（総額：" + win_price + "）"));
                        } catch (SQLException error) {
                            e.getWhoClicked().sendMessage(Component.text(prefix + "DBの参照に失敗しました"));
                            try {
                                mysql.close();
                            } catch (NullPointerException throwables) {
                                throwables.printStackTrace();
                            }
                            throw new RuntimeException(error);
                        }
                    });
                    th.start();
                    return;
                }
                // ランキング
                else if (e.getRawSlot() == 6) {
                    e.getWhoClicked().closeInventory();
                    Helper.SendDrinkRanking((Player) e.getWhoClicked(), liq, 1);
                    return;
                }
            }
        }
    }

    // ショップキーパークリック時処理
    @EventHandler
    public void onVillagerInteract(PlayerInteractAtEntityEvent e) {
        if (!(e.getRightClicked() instanceof Villager)) return;
        Villager villager = (Villager) e.getRightClicked();

        if (!villager.getPersistentDataContainer().has(new NamespacedKey(mgbar, "MGBarShop"))) return;
        String shop_name = villager.getPersistentDataContainer().get(new NamespacedKey(mgbar, "MGBarShop"), PersistentDataType.STRING);
        e.setCancelled(true);
        if (remove_players.contains(e.getPlayer())){
            villager.remove();
            remove_players.remove(e.getPlayer());
            e.getPlayer().sendMessage(Component.text(prefix + "バーカウンターを削除しました"));
            e.getPlayer().sendMessage(Component.text(prefix + "削除モードを終了しました"));
            return;
        }
        if (!Helper.CheckSystem(e.getPlayer())) return;
        if (!shops.containsKey(shop_name)) return;
        Shop shop = shops.get(shop_name);
        if (!e.getPlayer().hasPermission(shop.permission)){
            e.getPlayer().sendMessage(Component.text(shop.permission_error));
            return;
        }

        GUI.OpenShopMenu(e.getPlayer(), shop);
    }

    // 同時にこっちも呼ばれるのでこっちもキャンセルしないといけない
    @EventHandler
    public void onVillagerInteract2(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Villager)) return;
        Villager villager = (Villager) e.getRightClicked();
        if (!villager.getPersistentDataContainer().has(new NamespacedKey(mgbar, "MGBarShop"))) return;
        e.setCancelled(true);
    }

    // 村人が死ぬのを防止
    @EventHandler
    public void EntityDamageEvent(EntityDamageEvent e){
        if (!(e.getEntity() instanceof Villager)) return;
        Villager villager = (Villager) e.getEntity();
        if (!villager.getPersistentDataContainer().has(new NamespacedKey(mgbar, "MGBarShop"))) return;
        e.setCancelled(true);
    }
}
