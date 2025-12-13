package yusama125718.man10GambleBar;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
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

            Player player = e.getPlayer();
            // DB処理はスレッドで行う
            Thread th = new Thread(() -> {
                LiquorWin win = null;
                // コネクション開始
                try (Connection c = mysql.getConnection()) {
                    c.setAutoCommit(false);
                    if (liq.verify_id) {
                        // 確認（排他ロックをかける）
                        try (PreparedStatement ps = c.prepareStatement(
                                "SELECT 1 FROM bar_shop_log LEFT OUTER JOIN bar_drink_log ON bar_shop_log.buy_id = bar_drink_log.buy_id WHERE bar_shop_log.buy_id = ? AND bar_drink_log.id IS NULL FOR UPDATE"
                        )) {
                            ps.setString(1, buy_id);
                            try (ResultSet set = ps.executeQuery()) {
                                if (!set.next()) {
                                    Bukkit.getScheduler().runTask(mgbar, () -> player.sendMessage(Component.text(prefix + "§cこのお酒は有効でないので使用できません")));
                                    return;
                                }
                            }
                        } catch (SQLException error) {
                            c.rollback();
                            Bukkit.getScheduler().runTask(mgbar, () -> player.sendMessage(Component.text(prefix + "§cDBの参照に失敗しました")));
                            mgbar.getLogger().severe("Failed to verify buy_id: " + buy_id);
                            mgbar.getLogger().severe(error.getMessage());
                            return;
                        }
                    }
                    // 抽選処理
                    int randomInt = ThreadLocalRandom.current().nextInt(0, 100000000);
                    for (LiquorWin table : liq.wins) {
                        if (randomInt < table.chance) {
                            win = table;
                            break;
                        }
                        randomInt -= table.chance;
                    }
                    // ログ保存
                    int price = win == null ? 0 : win.price;
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO bar_drink_log (time, liquor_name, mcid, uuid, price, buy_id, win_table) VALUES (?, ?, ?, ?, ?, ?, ?)"
                    )
                    ) {
                        ps.setObject(1, LocalDateTime.now());
                        ps.setString(2, liq.name);
                        ps.setString(3, player.getName());
                        ps.setString(4, player.getUniqueId().toString());
                        ps.setInt(5, price);
                        ps.setString(6, buy_id);
                        if (win == null) {
                            ps.setNull(7, Types.VARCHAR);
                        } else {
                            ps.setString(7, win.name);
                        }
                        ps.executeUpdate();
                    } catch (SQLException error) {
                        c.rollback();
                        Bukkit.getScheduler().runTask(mgbar, () -> player.sendMessage(Component.text(prefix + "§cDBの保存に失敗しました")));
                        mgbar.getLogger().severe("Failed to insert bar_drink_log for buy_id: " + buy_id);
                        mgbar.getLogger().severe(error.getMessage());
                        return;
                    }
                    // コミット（排他ロックを解除）
                    c.commit();
                } catch (SQLException error) {
                    Bukkit.getScheduler().runTask(mgbar, () -> player.sendMessage(Component.text(prefix + "§cDBの参照に失敗しました")));
                    mgbar.getLogger().severe("Failed on drink flow. buy_id: " + buy_id);
                    mgbar.getLogger().severe(error.getMessage());
                    return;
                }
                LiquorWin finalWin = win;
                Bukkit.getScheduler().runTask(mgbar, () -> {
                    // ハズレ時処理
                    if (finalWin == null) {
                        if (liq.lose_commands != null) for (String cmd : liq.lose_commands)
                            Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), cmd.replace("%player%", e.getPlayer().getName()));
                        if (liq.lose_messages != null) for (String message : liq.lose_messages)
                            e.getPlayer().sendMessage(Component.text(message.replace("&", "§")));
                    }
                    // 当選時処理
                    else {
                        if (finalWin.commands != null) for (String cmd : finalWin.commands)
                            Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), cmd.replace("%player%", e.getPlayer().getName()));
                        if (finalWin.messages != null) for (String message : finalWin.messages)
                            e.getPlayer().sendMessage(Component.text(message.replace("&", "§")));
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
                if (!e.getCurrentItem().getItemMeta().getPersistentDataContainer().has(new NamespacedKey(mgbar, "MGBarDisplay"))) return;
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
                Player player = (Player) e.getWhoClicked();
                String mcid = player.getName();
                String uuid = player.getUniqueId().toString();

                // DB処理はスレッドで行う
                Thread th = new Thread(() -> {
                    try (Connection c = mysql.getConnection();
                         PreparedStatement ps = c.prepareStatement(
                             "INSERT INTO bar_shop_log (time, liquor_name, mcid, uuid, price, buy_id) VALUES (?, ?, ?, ?, ?, ?)"
                         )
                    ) {
                        ps.setObject(1, LocalDateTime.now());
                        ps.setString(2, liq.name);
                        ps.setString(3, mcid);
                        ps.setString(4, uuid);
                        ps.setInt(5, liq.price);
                        ps.setString(6, buy_id.toString());
                        ps.executeUpdate();
                    } catch (SQLException error) {
                        Bukkit.getScheduler().runTask(mgbar, () -> player.sendMessage(Component.text(prefix + "§cDBの保存に失敗しました")));
                        mgbar.getLogger().severe("Failed to insert bar_shop_log for buy_id: " + buy_id);
                        mgbar.getLogger().severe(error.getMessage());
                        return;
                    }
                    // インベントリはメインスレッドでいじる
                    Bukkit.getScheduler().runTask(mgbar, () -> {
                        if (!vaultapi.withdraw(player.getUniqueId(), liq.price)) {
                            player.sendMessage(Component.text(prefix + "§c出金に失敗しました"));
                            return;
                        }
                        if (player.getInventory().firstEmpty() == -1){
                            // インベントリがいっぱいの場合自分のみ取得可能なアイテムを足元に置く
                            player.getWorld().dropItem(e.getWhoClicked().getLocation(), liq.GenLiquor(buy_id).clone(), (Item item) -> {
                                item.setPickupDelay(0);
                                item.setOwner(e.getWhoClicked().getUniqueId());
                                item.setCanMobPickup(false);
                            });
                        }
                        else {
                            player.getInventory().addItem(liq.GenLiquor(buy_id).clone());
                        }
                        player.sendMessage(Component.text(prefix + "§r" + liq.displayName + "§rを購入しました"));
                    });
                });
                th.start();
            }
            case "記録メニュー" -> {
                int page = 0;
                ItemStack page_item = e.getInventory().getItem(49);
                if (page_item != null && page_item.hasItemMeta() && page_item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(mgbar, "MGBarPage"), PersistentDataType.INTEGER)){
                    page = page_item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(mgbar, "MGBarPage"), PersistentDataType.INTEGER);
                }
                // 前のページ
                if (45 <= e.getRawSlot() && e.getRawSlot() <= 47){
                    if (page <= 1) return;
                    GUI.OpenRecordMenu((Player) e.getWhoClicked(), page - 1);
                    return;
                }
                // 次のページ
                else if (51 <= e.getRawSlot() && e.getRawSlot() <= 54){
                    if (page <= 1 || page >= liquors.size() / 9 + 1) return;
                    GUI.OpenRecordMenu((Player) e.getWhoClicked(), page + 1);
                    return;
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
                    Player player = (Player) e.getWhoClicked();
                    UUID playerUuid = player.getUniqueId();
                    // DB処理はスレッドで行う
                    Thread th = new Thread(() -> {
                        int buy_price = 0;
                        int buy_count = 0;
                        int drink_count = 0;
                        try (Connection c = mysql.getConnection();
                             PreparedStatement psBuy = c.prepareStatement(
                                 "SELECT SUM(price) AS buy_total, COUNT(*) AS buy_count FROM bar_shop_log WHERE liquor_name = ? AND uuid = ? AND price <> 0"
                             );
                             PreparedStatement psDrink = c.prepareStatement(
                                 "SELECT COUNT(*) AS drink_count FROM bar_drink_log WHERE liquor_name = ? AND uuid = ?"
                             )
                        ) {
                            psBuy.setString(1, liq_name);
                            psBuy.setString(2, playerUuid.toString());
                            try (ResultSet set = psBuy.executeQuery()) {
                                if (set.next()) {
                                    buy_price = set.getInt("buy_total");
                                    buy_count = set.getInt("buy_count");
                                }
                            }

                            psDrink.setString(1, liq_name);
                            psDrink.setString(2, playerUuid.toString());
                            try (ResultSet set = psDrink.executeQuery()) {
                                if (set.next()) {
                                    drink_count = set.getInt("drink_count");
                                }
                            }
                        } catch (SQLException error) {
                            Bukkit.getScheduler().runTask(mgbar, () -> player.sendMessage(Component.text(prefix + "§cDBの参照に失敗しました")));
                            mgbar.getLogger().severe("Failed to load personal record for uuid: " + playerUuid);
                            mgbar.getLogger().severe(error.getMessage());
                            return;
                        }

                        final int buyPriceResult = buy_price;
                        final int buyCountResult = buy_count;
                        final int drinkCountResult = drink_count;
                        Bukkit.getScheduler().runTask(mgbar, () -> {
                            player.sendMessage(Component.text(prefix + "===== 個人ログ ====="));
                            player.sendMessage(liq.displayName);
                            player.sendMessage(Component.text("購入数：" + buyCountResult + "本（総額：" + buyPriceResult + "）"));
                            player.sendMessage(Component.text("飲んだ本数：" + drinkCountResult + "本"));
                        });
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
            case "売却画面" -> {
                if (e.getCurrentItem() == null || !e.getCurrentItem().hasItemMeta() || !e.getCurrentItem().getItemMeta().getPersistentDataContainer().has(new NamespacedKey(mgbar, "Man10GambleBar"), PersistentDataType.STRING))
                    return;
                String liq_name = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(new NamespacedKey(mgbar, "Man10GambleBar"), PersistentDataType.STRING);
                String buy_id = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(new NamespacedKey(mgbar, "MGBarID"), PersistentDataType.STRING);
                if (!liquors.containsKey(liq_name)) return;
                Liquor liq = liquors.get(liq_name);
                if (liq.resale_price == 0){
                    e.getWhoClicked().closeInventory();
                    e.getWhoClicked().sendMessage(Component.text(prefix + "§cそのお酒は売れません"));
                    return;
                }
                // DB処理はスレッドで行う
                Player player = (Player) e.getWhoClicked();
                ItemStack clickedItem = e.getCurrentItem().clone();
                Thread th = new Thread(() -> {
                    // 検証
                    if (liq.verify_id){
                        // コネクションを開始
                        try (Connection c = mysql.getConnection();) {
                            c.setAutoCommit(false);
                            // 確認（排他ロックをかける）
                            try (PreparedStatement ps = c.prepareStatement(
                                    "SELECT 1 FROM bar_shop_log LEFT OUTER JOIN bar_drink_log ON bar_shop_log.buy_id = bar_drink_log.buy_id WHERE bar_shop_log.buy_id = ? AND bar_drink_log.id IS NULL FOR UPDATE"
                            )){
                                ps.setString(1, buy_id);
                                try (ResultSet set = ps.executeQuery()) {
                                    if (!set.next()) {
                                        Bukkit.getScheduler().runTask(mgbar, () -> {
                                            player.sendMessage(Component.text(prefix + "§cこのお酒は有効でないので使用できません"));
                                            player.getInventory().removeItemAnySlot(clickedItem);
                                        });
                                        return;
                                    }
                                }
                            }
                            catch (SQLException error) {
                                Bukkit.getScheduler().runTask(mgbar, () -> player.sendMessage(Component.text(prefix + "§cDBの参照に失敗しました")));
                                mgbar.getLogger().severe("Failed to verify buy_id for resale: " + buy_id);
                                mgbar.getLogger().severe(error.getMessage());
                                return;
                            }
                            // ログ保存
                            try (PreparedStatement ps = c.prepareStatement(
                                    "INSERT INTO bar_drink_log (time, liquor_name, mcid, uuid, price, buy_id, win_table) VALUES (?, ?, ?, ?, ?, ?, ?)"
                            )){
                                ps.setObject(1, LocalDateTime.now());
                                ps.setString(2, liq.name);
                                ps.setString(3, player.getName());
                                ps.setString(4, player.getUniqueId().toString());
                                ps.setDouble(5, liq.resale_price);
                                ps.setString(6, buy_id);
                                ps.setString(7, "sale:" + liq.name);
                                ps.executeUpdate();
                            }
                            catch (SQLException error) {
                                Bukkit.getScheduler().runTask(mgbar, () -> player.sendMessage(Component.text(prefix + "§cDBの保存に失敗しました")));
                                mgbar.getLogger().severe("Failed to insert resale log for buy_id: " + buy_id);
                                mgbar.getLogger().severe(error.getMessage());
                                return;
                            }
                            // コミット（排他ロックを解除）
                            c.commit();
                        } catch (SQLException error) {
                            Bukkit.getScheduler().runTask(mgbar, () -> player.sendMessage(Component.text(prefix + "§cDBの処理に失敗しました")));
                            mgbar.getLogger().severe("Failed on resale flow: " + buy_id);
                            mgbar.getLogger().severe(error.getMessage());
                            return;
                        }
                    }
                    // 問題なければお金を渡す（メインスレッドで実行）
                    Bukkit.getScheduler().runTask(mgbar, () -> {
                        player.getInventory().removeItemAnySlot(clickedItem);
                        vaultapi.deposit(player.getUniqueId(), liq.resale_price);
                        player.sendMessage(prefix + "§e売却しました");
                    });
                });
                th.start();
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
