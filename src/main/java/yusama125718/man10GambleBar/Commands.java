package yusama125718.man10GambleBar;

import net.kyori.adventure.text.Component;
import org.apache.commons.lang.exception.ExceptionUtils;
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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static yusama125718.man10GambleBar.Helper.CheckSystem;
import static yusama125718.man10GambleBar.Man10GambleBar.*;

public class Commands implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        if (!sender.hasPermission("mgbar.p")) return true;
        switch (args.length){
            case 0:
                if (system) sender.sendMessage(prefix + "§7/mgbar help§f§lでhelpを表示");
                else sender.sendMessage(prefix + "§cシステムは停止中です");
                return true;

            case 1:
                if (args[0].equals("help")){
                    sender.sendMessage(prefix + "§7/mgbar record §f§l: 記録メニューを開きます");
                    sender.sendMessage(prefix + "§7/mgbar resale §f§l: 売却メニューを開きます");
                    if (sender.hasPermission("mgbar.op")){
                        sender.sendMessage(prefix + "§l===== 管理者コマンド =====");
                        sender.sendMessage(prefix + "§7/mgbar open [バーの名前] §f§l: バーカウンターを開きます");
                        sender.sendMessage(prefix + "§7/mgbar [on/off] §f§l: システムをon/offします");
                        sender.sendMessage(prefix + "§7/mgbar reload §f§l:　設定を再読み込みします");
                        sender.sendMessage(prefix + "§7/mgbar sawn [バーデンダー名] [バーの名前] §f§l: 現在の位置にバーテンダーを配置します");
                        sender.sendMessage(prefix + "§7/mgbar remove §f§l: 右クリックしたバーカウンターを削除します※削除するかもう一度コマンド実行で削除モード終了");
                        sender.sendMessage(prefix + "§7/mgbar rank [内部名] [ページ数] §f§l: ランキングメニューを開きます※一般権限で実行可能");
                        sender.sendMessage(prefix + "§7/mgbar give [MCID] [お酒の内部名] §f§l: 指定したプレイヤーにお酒を付与します");
                        sender.sendMessage(prefix + "§7/mgbar counters §f§l: バーの一覧を表示します");
                        sender.sendMessage(prefix + "§7/mgbar world §f§l: 無効化するワールドに今いるワールドを追加します※追加済みの場合は削除します");
                        sender.sendMessage(prefix + "§7/mgbar simulate [お酒の内部名] [回数] §f§l: 指定回数お酒の抽選を実施し、結果を表示します");
                    }
                    return true;
                }
                if (args[0].equals("on") && sender.hasPermission("mgbar.op")){
                    if (system){
                        sender.sendMessage(prefix + "§cシステムはすでに有効です");
                        return true;
                    }
                    mgbar.getConfig().set("system", true);
                    mgbar.saveConfig();
                    system = true;
                    sender.sendMessage(prefix + "§fシステムを有効にしました");
                    return true;
                }
                if (args[0].equals("off") && sender.hasPermission("mgbar.op")){
                    if (!system){
                        sender.sendMessage(prefix + "§cシステムはすでに無効です");
                        return true;
                    }
                    mgbar.getConfig().set("system", false);
                    mgbar.saveConfig();
                    system = false;
                    sender.sendMessage(prefix + "§fシステムを無効にしました");
                    return true;
                }
                if (args[0].equals("reload") && sender.hasPermission("mgbar.op")){
                    system = false;
                    liquors = new LinkedHashMap<>();
                    shops = new HashMap<>();
                    SetupPL();
                    sender.sendMessage(prefix + "§f設定を再読み込みしました");
                    return true;
                }
                if (args[0].equals("record")){
                    if (!sender.hasPermission("mgbar.record")){
                        sender.sendMessage(prefix + "§c権限がありません");
                        return true;
                    }
                    if (!CheckSystem((Player) sender)) return true;
                    GUI.OpenRecordMenu((Player) sender, 1);
                    return true;
                }
                if (args[0].equals("counters") && sender.hasPermission("mgbar.op")){
                    sender.sendMessage(prefix + "§e§lカウンター一覧");
                    for (String name: shops.keySet()){
                        sender.sendMessage(name);
                    }
                    return true;
                }
                if (args[0].equals("remove") && sender.hasPermission("mgbar.op")){
                    if (remove_players.contains((Player) sender)){
                        remove_players.remove((Player) sender);
                        sender.sendMessage(prefix + "§f削除モードを終了しました");
                        return true;
                    }
                    remove_players.add((Player) sender);
                    sender.sendMessage(prefix + "§e削除したいバーテンダーを右クリックしてください");
                    return true;
                }
                if (args[0].equals("world") && sender.hasPermission("mgbar.op")){
                    String name = ((Player) sender).getWorld().getName();
                    if (disable_worlds.contains(name)){
                        disable_worlds.remove(name);
                        sender.sendMessage(prefix + "§e禁止リストから削除しました");
                    }
                    else {
                        disable_worlds.add(name);
                        sender.sendMessage(prefix + "§e禁止に追加しました");
                    }
                    mgbar.getConfig().set("disable_worlds", disable_worlds);
                    mgbar.saveConfig();
                    return true;
                }
                if (args[0].equals("resale")){
                    if (!system){
                        sender.sendMessage(prefix + "§cシステムはすでに無効です");
                        return true;
                    }
                    GUI.OpenResale((Player) sender);
                    return true;
                }
                break;

            case 2:
                if (args[0].equals("open") && sender.hasPermission("mgbar.op")){
                    if (!CheckSystem((Player) sender)) return true;
                    if (!shops.containsKey(args[1])){
                        sender.sendMessage(prefix + "§cその名前のバーは存在しません");
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
                        sender.sendMessage(prefix + "§cプレイヤーが見つかりませんでした");
                        return true;
                    }
                    if (!liquors.containsKey(args[2])){
                        sender.sendMessage(prefix + "§cその名前のお酒はありません");
                        return true;
                    }
                    Liquor liq = liquors.get(args[2]);
                    int empty_slot = target.getInventory().firstEmpty();
                    if (empty_slot == -1){
                        sender.sendMessage(prefix + "§cプレイヤーのインベントリが満杯です！");
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

                        try (Connection c = mysql.getConnection();
                             PreparedStatement ps = c.prepareStatement(
                                 "INSERT INTO bar_shop_log (time, liquor_name, mcid, uuid, price, buy_id) VALUES (?, ?, ?, ?, 0, ?)"
                             )
                        ) {
                            ps.setObject(1, LocalDateTime.now());
                            ps.setString(2, liq.name);
                            ps.setString(3, mcid);
                            ps.setString(4, uuid);
                            ps.setString(5, buy_id.toString());
                            ps.executeUpdate();
                        } catch (Exception e) {
                            Bukkit.getScheduler().runTask(mgbar, () -> {
                                sender.sendMessage(Component.text(prefix + "§cDBの保存に失敗しました"));
                            });
                            mgbar.getLogger().severe("Failed to save bar_shop_log.");
                            mgbar.getLogger().severe(ExceptionUtils.getFullStackTrace(e));
                        }

                        // インベントリ操作とメッセージ送信はメインスレッドで行う
                        Bukkit.getScheduler().runTask(mgbar, () -> {
                            target.getInventory().addItem(liq.GenLiquor(buy_id).clone());
                            sender.sendMessage(Component.text(prefix + "§r§c§l" + mcid + "§rに" + liq.displayName + "§r§f§lを渡しました"));
                            target.sendMessage(Component.text(prefix + "§r" + liq.displayName + "§rを入手しました"));
                        });
                    });
                    th.start();
                    return true;
                }
                if (args[0].equals("rank")){
                    if (!sender.hasPermission("mgbar.record")){
                        sender.sendMessage(prefix + "§c権限がありません");
                        return true;
                    }
                    if (!liquors.containsKey(args[1])){
                        sender.sendMessage(prefix + "§cその名前のお酒はありません");
                        return true;
                    }
                    if (!args[2].matches("-?\\d+")){
                        sender.sendMessage(prefix + "§cページ数は数字で入力してください");
                        return true;
                    }
                    int page = Integer.parseInt(args[2]);
                    if (page < 1){
                        sender.sendMessage(prefix + "§cページ数は1以上で入力してください");
                        return true;
                    }
                    Helper.SendDrinkRanking((Player) sender,liquors.get(args[1]), page);
                    return true;
                }
                if (args[0].equals("spawn") && sender.hasPermission("mgbar.op")){
                    if (!shops.containsKey(args[2])){
                        sender.sendMessage(prefix + "§cその名前のカウンターはありません");
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
                    sender.sendMessage(prefix + "§e§lバーテンダーを配置しました");
                    return true;
                }
                if (args[0].equals("simulate") && sender.hasPermission("mgbar.op")){
                    if (!liquors.containsKey(args[1])){
                        sender.sendMessage(prefix + "§cその名前のお酒はありません");
                        return true;
                    }
                    if (!args[2].matches("-?\\d+")){
                        sender.sendMessage(prefix + "§c回数は数字で入力してください");
                        return true;
                    }
                    if (args[2].length() >= 10){
                        sender.sendMessage(prefix + "§c回数は10億回未満にしてください");
                        return true;
                    }
                    int times = Integer.parseInt(args[2]);
                    if (times < 1){
                        sender.sendMessage(prefix + "§c回数は1以上で入力してください");
                        return true;
                    }
                    Thread th = new Thread(() -> {
                        Map<String, Integer> win_count = new HashMap<>();
                        Liquor liq = liquors.get(args[1]);
                        for (int i = 0; i < times; i++){
                            int randomInt = ThreadLocalRandom.current().nextInt(0, 100000000);
                            boolean win = false;
                            for (LiquorWin table: liq.wins) {
                                if (randomInt < table.chance) {
                                    int cnt = 1;
                                    if (win_count.containsKey(table.name)) cnt += win_count.get(table.name);
                                    win_count.put(table.name, cnt);
                                    win = true;
                                    break;
                                }
                                randomInt -= table.chance;
                            }
                            if (!win){
                                int cnt = 1;
                                if (win_count.containsKey("None")) cnt += win_count.get("None");
                                win_count.put("None", cnt);
                            }
                        }
                        sender.sendMessage(prefix + liq.name + "シミュレート結果");
                        for (String name : win_count.keySet()){
                            sender.sendMessage(name + ":" + win_count.get(name) + "回(" + (float) win_count.get(name) / (float) times * 100 + "%)");
                        }
                    });
                    th.start();
                    return true;
                }
                break;
        }

        sender.sendMessage(prefix + "§cコマンドが違います");
        return true;
    }

    // Tab補完
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String str, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("mgbar") || !sender.hasPermission("mgbar.p")) return null;
        if (args.length == 1){
            if (args[0].isEmpty())
            {
                if (sender.hasPermission("mgbar.op")) return Arrays.asList("give","off","on","open","record","reload","remove","resale","spawn", "simulate", "world");
                else return Collections.singletonList("record");
            }
            else{
                if (!sender.hasPermission("mgbar.op")){
                    if ("record".startsWith(args[0]) && "resale".startsWith(args[0])) {
                        return Arrays.asList("record", "resale");
                    }
                    else if ("record".startsWith(args[0])) {
                        return Collections.singletonList("record");
                    }
                    else if ("resale".startsWith(args[0])) {
                        return Collections.singletonList("resale");
                    }
                }
                else {
                    if ("give".startsWith(args[0])) {
                        return Collections.singletonList("give");
                    }
                    else if ("off".startsWith(args[0]) && "on".startsWith(args[0]) && "open".startsWith(args[0])) {
                        return Arrays.asList("off", "on", "open");
                    }
                    else if ("off".startsWith(args[0])) {
                        return Collections.singletonList("off");
                    }
                    else if ("on".startsWith(args[0])) {
                        return Collections.singletonList("on");
                    }
                    else if ("open".startsWith(args[0])) {
                        return Collections.singletonList("open");
                    }
                    else if ("record".startsWith(args[0]) && "reload".startsWith(args[0]) && "remove".startsWith(args[0]) && ("resale").startsWith(args[0])) {
                        return Arrays.asList("record", "reload", "remove", "resale");
                    }
                    else if ("record".startsWith(args[0])) {
                        return Collections.singletonList("record");
                    }
                    else if ("reload".startsWith(args[0])) {
                        return Collections.singletonList("reload");
                    }
                    else if ("recipe".startsWith(args[0])) {
                        return Collections.singletonList("remove");
                    }
                    else if ("resale".startsWith(args[0])) {
                        return Collections.singletonList("resale");
                    }
                    else if ("spawn".startsWith(args[0])) {
                        return Collections.singletonList("spawn");
                    }
                    else if ("simulate".startsWith(args[0])) {
                        return Collections.singletonList("simulate");
                    }
                    else if ("world".startsWith(args[0])) {
                        return Collections.singletonList("world");
                    }
                }
            }
        }
        else if (args.length == 2 && sender.hasPermission("mgbar.op")){
            if (args[0].equals("give")){
                List<String> returnlist = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) returnlist.add(p.getName());
                return returnlist;
            }
            else if (args[0].equals("rank")){
                return Collections.singletonList("[内部名]");
            }
            else if (args[0].equals("spawn")){
                return Collections.singletonList("[バーテンダー名]");
            }
            else if (args[0].equals("open")){
                List<String> returnlist = new ArrayList<>();
                for (Shop s : shops.values()) returnlist.add(s.name);
                return returnlist;
            }
            else if (args[0].equals("simulate")){
                List<String> returnlist = new ArrayList<>();
                for (Liquor l : liquors.values()) returnlist.add(l.name);
                return returnlist;
            }
        }
        else if (args.length == 3 && sender.hasPermission("mgbar.op")){
            if (args[0].equals("rank")){
                return Collections.singletonList("[ページ]");
            }
            else if (args[0].equals("spawn")){
                List<String> returnlist = new ArrayList<>();
                for (Shop s : shops.values()) returnlist.add(s.name);
                return returnlist;
            }
            else if (args[0].equals("give")){
                List<String> returnlist = new ArrayList<>();
                for (Liquor l : liquors.values()) returnlist.add(l.name);
                return returnlist;
            }
            else if (args[0].equals("simulate")){
                return Collections.singletonList("[回数]");
            }
        }
        return null;
    }
}
