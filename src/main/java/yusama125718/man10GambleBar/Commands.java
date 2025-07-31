package yusama125718.man10GambleBar;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.command.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static yusama125718.man10GambleBar.Helper.CheckSystem;
import static yusama125718.man10GambleBar.Man10GambleBar.*;

public class Commands implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        if (!sender.hasPermission("mgbar.p")) return true;
        switch (args.length){
            case 0:
                if (system) sender.sendMessage(prefix + "/mgbar helpでhelpを表示");
                else sender.sendMessage(prefix + "システムは停止中です");
                return true;

            case 1:
                if (args[0].equals("help")){
                    sender.sendMessage(prefix + "/mgbar record : 記録メニューを開きます");
                    if (sender.hasPermission("mgbar.op")){
                        sender.sendMessage(prefix + "===== 管理者コマンド =====");
                        sender.sendMessage(prefix + "/mgbar open [バーの名前] : バーカウンターを開きます");
                        sender.sendMessage(prefix + "/mgbar [on/off] : システムをon/offします");
                        sender.sendMessage(prefix + "/mgbar reload :　設定を再読み込みします");
                        sender.sendMessage(prefix + "/mgbar sawn [バーデンダー名] [バーの名前] : 現在の位置にバーテンダーを配置します");
                        sender.sendMessage(prefix + "/mgbar remove : 右クリックしたバーカウンターを削除します※削除するかもう一度コマンド実行で削除モード終了");
                        sender.sendMessage(prefix + "/mgbar rank [内部名] [ページ数] : バーカウンターの一覧を表示します※一般権限で実行可能");
                        sender.sendMessage(prefix + "/mgbar give [MCID] [お酒の内部名] : 指定したプレイヤーにお酒を付与します");
                    }
                    return true;
                }
                if (args[0].equals("on") && sender.hasPermission("mgbar.op")){
                    if (system){
                        sender.sendMessage(prefix + "システムはすでに有効です");
                        return true;
                    }
                    mgbar.getConfig().set("system", true);
                    mgbar.saveConfig();
                    system = true;
                    sender.sendMessage(prefix + "システムを有効にしました");
                    return true;
                }
                if (args[0].equals("off") && sender.hasPermission("mgbar.op")){
                    if (!system){
                        sender.sendMessage(prefix + "システムはすでに無効です");
                        return true;
                    }
                    mgbar.getConfig().set("system", false);
                    mgbar.saveConfig();
                    system = false;
                    sender.sendMessage(prefix + "システムを無効にしました");
                    return true;
                }
                if (args[0].equals("reload") && sender.hasPermission("mgbar.op")){
                    system = false;
                    SetupPL();
                    sender.sendMessage(prefix + "設定を再読み込みしました");
                    return true;
                }
                if (args[0].equals("record")){
                    if (!CheckSystem((Player) sender)) return true;
                    GUI.OpenRecordMenu((Player) sender, 1);
                    return true;
                }
                if (args[0].equals("counters") && sender.hasPermission("mgbar.op")){
                    sender.sendMessage(prefix + "カウンター一覧");
                    for (String name: shops.keySet()){
                        sender.sendMessage(name);
                    }
                    return true;
                }
                if (args[0].equals("remove") && sender.hasPermission("mgbar.op")){
                    if (remove_players.contains((Player) sender)){
                        remove_players.remove((Player) sender);
                        sender.sendMessage(prefix + "削除モードを終了しました");
                        return true;
                    }
                    remove_players.add((Player) sender);
                    sender.sendMessage(prefix + "削除したいバーテンダーを右クリックしてください");
                    return true;
                }
                break;

            case 2:
                if (args[0].equals("open") && sender.hasPermission("mgbar.op")){
                    if (!CheckSystem((Player) sender)) return true;
                    if (!shops.containsKey(args[1])){
                        sender.sendMessage(prefix + "その名前のバーは存在しません");
                        return true;
                    }
                    GUI.OpenShopMenu((Player) sender, shops.get(args[1]));
                    return true;
                }
                break;

            case 3:
                if (args[0].equals("give") && sender.hasPermission("mgbar.op")){
                    Player target = Bukkit.getPlayerExact(args[1]);
                    if (target == null){
                        sender.sendMessage(prefix + "プレイヤーが見つかりませんでした");
                        return true;
                    }
                    if (!liquors.containsKey(args[2])){
                        sender.sendMessage(prefix + "その名前のお酒はありません");
                        return true;
                    }
                    Liquor liq = liquors.get(args[2]);
                    int empty_slot = target.getInventory().firstEmpty();
                    if (empty_slot == -1){
                        sender.sendMessage(prefix + "プレイヤーのインベントリが満杯です！");
                        return true;
                    }
                    UUID buy_id = UUID.randomUUID();
                    String mcid;
                    String uuid;
                    if (sender instanceof ConsoleCommandSender){
                        mcid = "[CONSOLE]";
                        uuid = "[CONSOLE]";
                    }
                    else{
                        mcid = sender.getName();
                        uuid = ((Player) sender).getUniqueId().toString();
                    }

                    // DB処理はスレッドで行う
                    Thread th = new Thread(() -> {
                        MySQLManager mysql = new MySQLManager(mgbar, "man10_token");
                        String query = "INSERT INTO bar_shop_log (time, liquor_name, mcid, uuid, price, buy_id) VALUES ('" + LocalDateTime.now() + "', '" + liq.name + "', '" + mcid + "', '" + uuid + "', 0, '" + buy_id + "')";
                        if (!mysql.execute(query)) {
                            sender.sendMessage(Component.text(prefix + "DBの保存に失敗しました"));
                            return;
                        }
                        // インベントリはメインスレッドでいじる
                        Bukkit.getScheduler().runTask(mgbar, () -> target.getInventory().addItem(liq.GenLiquor(buy_id).clone()));
                        sender.sendMessage(Component.text(prefix + mcid + "に" + liq.name + "§rを渡しました"));
                        target.sendMessage(Component.text(prefix + "§rお酒を入手しました"));
                    });
                    th.start();
                    return true;
                }
                if (args[0].equals("rank")){
                    if (!liquors.containsKey(args[1])){
                        sender.sendMessage(prefix + "その名前のお酒はありません");
                        return true;
                    }
                    if (!args[2].matches("-?\\d+")){
                        sender.sendMessage(prefix + "ページ数は数字で入力してください");
                        return true;
                    }
                    int page = Integer.parseInt(args[2]);
                    if (page < 1){
                        sender.sendMessage(prefix + "ページ数は1以上で入力してください");
                        return true;
                    }
                    Helper.SendDrinkRanking((Player) sender,liquors.get(args[1]), page);
                    return true;
                }
                if (args[0].equals("spawn") && sender.hasPermission("mgbar.op")){
                    if (!shops.containsKey(args[2])){
                        sender.sendMessage(prefix + "その名前のカウンターはありません");
                        return true;
                    }
                    Shop shop = shops.get(args[2]);
                    Location loc = ((Player) sender).getLocation();
                    Villager villager = (Villager) ((Player) sender).getWorld().spawnEntity(loc, EntityType.VILLAGER);
                    villager.setCustomName(args[1].replace("&", "§"));
                    villager.setCustomNameVisible(true);
                    villager.setAI(false); // AIを無効化（動かないようにする）
                    villager.setInvulnerable(true); // 無敵化
                    villager.setPersistent(true);
                    villager.getPersistentDataContainer().set(new NamespacedKey(mgbar, "MGBarShop"), PersistentDataType.STRING, shop.name);
                    sender.sendMessage(prefix + "バーテンダーを配置しました");
                    return true;
                }
                break;
        }

        sender.sendMessage(prefix + "コマンドが違います");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
        return List.of();
    }
}
