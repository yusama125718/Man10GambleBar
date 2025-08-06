package yusama125718.man10GambleBar;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public final class Man10GambleBar extends JavaPlugin {

    public static JavaPlugin mgbar;
    public static String prefix;
    public static Boolean system;
    public static Map<String, Shop> shops;
    public static LinkedHashMap<String, Liquor> liquors;
    public static VaultAPI vaultapi;
    public static List<Player> remove_players;
    public static List<String> disable_worlds;

    private static File shop_folder;
    private static File liquor_folder;

    @Override
    public void onEnable() {
        mgbar = this;
        new Events(this);
        getCommand("mgbar").setExecutor(new Commands());
        mgbar.saveDefaultConfig();
        Thread th = new Thread(() -> {
            MySQLManager mysql = new MySQLManager(mgbar, "man10_gamble_bar");
            mysql.execute("create table if not exists bar_shop_log(id int auto_increment,time datetime,liquor_name varchar(20),mcid varchar(16),uuid varchar(36),price integer,buy_id varchar(36),primary key(id))");
            mysql.execute("create table if not exists bar_drink_log(id int auto_increment,time datetime,liquor_name varchar(20),mcid varchar(16),uuid varchar(36),price integer,buy_id varchar(36),win_table varchar(20),primary key(id))");
        });
        th.start();
        vaultapi = new VaultAPI();
        SetupPL();
    }

    public static void SetupPL(){
        remove_players = new ArrayList<>();
        disable_worlds = new ArrayList<>();
        liquors = new LinkedHashMap<>();
        shops = new HashMap<>();
        prefix = mgbar.getConfig().getString("prefix") + "§r";
        system = mgbar.getConfig().getBoolean("system");
        disable_worlds = mgbar.getConfig().getStringList("disable_worlds");
        boolean make_shop = true;
        boolean make_liquor = true;
        // フォルダを取得
        if (mgbar.getDataFolder().listFiles() != null) {
            for (File file : Objects.requireNonNull(mgbar.getDataFolder().listFiles())) {
                if (make_shop && file.getName().equals("shops")) {
                    shop_folder = file;
                    make_shop = false;
                    GetShops();
                }
                if (make_liquor && file.getName().equals("liquors")) {
                    liquor_folder = file;
                    make_liquor = false;
                    GetLiquors(liquor_folder.listFiles());
                }
            }
        }
        // ショップフォルダがなければ作成
        if (make_shop){
            File folder = new File(mgbar.getDataFolder().getAbsolutePath() + File.separator + "shops");
            if (folder.mkdir()) {
                Bukkit.broadcast(Component.text(prefix + "ショップフォルダを作成しました"), "mgbar.op");
                shop_folder = folder;
            }
            else {
                Bukkit.broadcast(Component.text(prefix + "ショップフォルダの作成に失敗しました"), "mgbar.op");
            }
        }
        // お酒フォルダがなければ作成
        if (make_liquor){
            File folder = new File(mgbar.getDataFolder().getAbsolutePath() + File.separator + "liquors");
            if (folder.mkdir()) {
                Bukkit.broadcast(Component.text(prefix + "お酒フォルダを作成しました"), "mgbar.op");
                shop_folder = folder;
            }
            else {
                Bukkit.broadcast(Component.text(prefix + "お酒フォルダの作成に失敗しました"), "mgbar.op");
            }
        }
    }

    private static void GetShops(){
        if (shop_folder.listFiles() == null) return;
        load_file: for (File file : shop_folder.listFiles()){
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            // 値チェック
            if (!config.isString("name") || !config.isString("permission") || !config.isInt("size") || !config.isString("permission_error")){
                Bukkit.broadcast(Component.text(prefix + file.getName() + "の読み込みに失敗しました"), "mgbar.op");
                Bukkit.broadcast(Component.text(prefix + "不足している項目があります"), "mgbar.op");
            }
            // 値取得
            String name = config.getString("name");

            // すでに存在する内部名ならエラー
            if (shops.containsKey(name)){
                Bukkit.broadcast(Component.text(prefix + file.getName() + "の読み込みに失敗しました"), "mgbar.op");
                Bukkit.broadcast(Component.text(prefix + "内部名がすでに存在します"), "mgbar.op");
                continue;
            }

            String permission = config.getString("permission");
            String permission_error = config.getString("permission_error");
            int size = config.getInt("size");
            if (size > 54) size = 54;
            if (size % 9 == 0) size = size / 9 * 9;
            List<ShopItem> items = new ArrayList<>();
            List<Map<?, ?>> rawItems = config.getMapList("items");
            for (Map<?, ?> map : rawItems) {
                int x = (int) map.get("x");
                int y = (int) map.get("y");
                if (map.containsKey("liquor_name")){
                    String liquor_name = (String) map.get("liquor_name");
                    items.add(new ShopItem(x, y, liquor_name));
                    shops.put(name, new Shop(name, permission, size, items, permission_error));
                }
                else if (map.containsKey("material")) {
                    Material material = Material.getMaterial((String) map.get("material"));
                    if (material == null) material = Material.DIRT;
                    int cmd = 0;
                    if (map.containsKey("cmd")) cmd = (int) map.get("cmd");
                    ItemStack item = Helper.GetItem(material, "", cmd);
                    items.add(new ShopItem(x, y, item));
                    shops.put(name, new Shop(name, permission, size, items, permission_error));
                }
                else{
                    Bukkit.broadcast(Component.text(prefix + file.getName() + "の読み込みに失敗しました"), "mgbar.op");
                    Bukkit.broadcast(Component.text(prefix + "ショップ内のアイテムに不足している項目があります"), "mgbar.op");
                    break load_file;
                }
            }
            shops.put(name, new Shop(name, permission, size, items, permission_error));
        }
    }

    public static void GetLiquors(File[] files){
        if (liquor_folder.listFiles() == null) return;
        load_file: for (File file : files) {
            if (file.isDirectory()) {
                GetLiquors(file.listFiles());
                continue;
            }
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            // 値チェック
            if (!config.isString("name") || !config.isString("permission") || !config.isInt("price") || !config.isString("display_name") || !config.isList("lore") || !config.isList("lose_commands") || !config.isList("lose_messages") || !config.isString("potion_color") || !config.isString("permission_error")) {
                Bukkit.broadcast(Component.text(prefix + file.getName() + "の読み込みに失敗しました"), "mgbar.op");
                Bukkit.broadcast(Component.text(prefix + "不足している項目があります"), "mgbar.op");
                continue;
            }
            // 値取得
            String name = config.getString("name");
            if (name.length() > 20){
                Bukkit.broadcast(Component.text(prefix + file.getName() + "の読み込みに失敗しました"), "mgbar.op");
                Bukkit.broadcast(Component.text(prefix + "内部名が長すぎます"), "mgbar.op");
            }
            String display_name = config.getString("display_name");
            List<String> lore = config.getStringList("lore");
            String permission = config.getString("permission");
            String permission_error = config.getString("permission_error");
            int price = config.getInt("price");
            List<String> lose_commands = config.getStringList("lose_commands");
            List<String> lose_messages = config.getStringList("lose_messages");
            Color color = Helper.ColorFromString(config.getString("potion_color"));
            boolean verify_id = true;
            if (config.isBoolean("verify_id")) verify_id = config.getBoolean("verify_id");
            boolean record = true;
            if (config.isBoolean("record")) record = config.getBoolean("record");
            boolean hide_win_count = false;

            // すでに存在する内部名ならエラー
            if (liquors.containsKey(name)){
                Bukkit.broadcast(Component.text(prefix + file.getName() + "の読み込みに失敗しました"), "mgbar.op");
                Bukkit.broadcast(Component.text(prefix + "内部名がすでに存在します"), "mgbar.op");
                continue;
            }

            // 当選一覧取得
            List<LiquorWin> wins = new ArrayList<>();
            List<Map<?, ?>> rawItems = config.getMapList("wins");
            BigDecimal odds_sum = new BigDecimal(0);
            for (Map<?, ?> map : rawItems) {
                // 必須チェック
                if (!map.containsKey("name") || !map.containsKey("price") || !map.containsKey("odds") || !map.containsKey("win_commands") || !map.containsKey("win_messages")){
                    Bukkit.broadcast(Component.text(prefix + file.getName() + "の読み込みに失敗しました"), "mgbar.op");
                    Bukkit.broadcast(Component.text(prefix + "お酒の当選リストに不足している項目があります"), "mgbar.op");
                    continue load_file;
                }
                String win_name = (String) map.get("name");
                int win_price = (int) map.get("price");
                BigDecimal odds = new BigDecimal((String) map.get("odds"));
                odds = odds.setScale(8, RoundingMode.DOWN);
                if (odds.compareTo(new BigDecimal(1)) > 0){
                    Bukkit.broadcast(Component.text(prefix + file.getName() + "の読み込みに失敗しました"), "mgbar.op");
                    Bukkit.broadcast(Component.text(prefix + "確率の合計が1を越えています"), "mgbar.op");
                    continue;
                }
                int odds_val = odds.multiply(new BigDecimal(100000000)).intValue();
                odds_sum = odds_sum.add(odds);
                List<String> win_commands = (List<String>) map.get("win_commands");
                List<String> win_messages = (List<String>) map.get("win_messages");
                wins.add(new LiquorWin(win_name, odds_val, win_price, win_commands, win_messages));
            }
            // 確率の合計が１以上ならエラー
            if (odds_sum.compareTo(new BigDecimal(1)) > 0){
                Bukkit.broadcast(Component.text(prefix + file.getName() + "の読み込みに失敗しました"), "mgbar.op");
                Bukkit.broadcast(Component.text(prefix + "確率の合計が1を越えています"), "mgbar.op");
                continue;
            }
            // 当選一覧がなければエラー
            if (wins.isEmpty()){
                Bukkit.broadcast(Component.text(prefix + file.getName() + "の読み込みに失敗しました"), "mgbar.op");
                Bukkit.broadcast(Component.text(prefix + "お酒の当選リストがありません"), "mgbar.op");
                continue;
            }
            liquors.put(name, new Liquor(name, display_name, permission, lore, price, lose_commands, lose_messages, wins, permission_error, color, verify_id, record, hide_win_count));
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public static class Shop{
        public String name;
        public String permission;
        public String permission_error;
        public Integer size;
        public List<ShopItem> items;

        public Shop(String Name, String Permission, Integer Size, List<ShopItem> Items, String PermissionError){
            name = Name;
            permission = Permission;
            size = Size;
            items = Items;
            permission_error = PermissionError;
        }
    }

    public static class ShopItem{
        public Integer x;
        public Integer y;
        public String liquor_name;
        public ItemStack item;

        public ShopItem(Integer X, Integer Y, String LiquorName){
            x = X;
            y = Y;
            liquor_name = LiquorName;
        }

        public ShopItem(Integer X, Integer Y, ItemStack Item){
            x = X;
            y = Y;
            item = Item;
        }
    }

    public static class Liquor{
        public String name;
        public String displayName;
        public String permission;
        public String permission_error;
        public List<Component> lore;
        public Integer price;
        public List<String> lose_commands;
        public List<String> lose_messages;
        public List<LiquorWin> wins;
        public Color color;
        public Boolean verify_id;
        public Boolean record;
        public Boolean hide_win_count;

        public Liquor(String Name, String DisplayName, String Permission, List<String> Lore, Integer Price, List<String> LoseCommands, List<String> LoseMessages, List<LiquorWin> Wins, String PermissionError, Color Col, Boolean VerifyId, Boolean Record, Boolean HideWinCount){
            name = Name;
            displayName = DisplayName.replace("&", "§");
            permission = Permission;
            List<Component> lore_component = new ArrayList<>();
            for (String s: Lore) lore_component.add(Component.text(s.replace("&", "§")));
            lore = lore_component;
            price = Price;
            lose_commands = LoseCommands;
            lose_messages = LoseMessages;
            wins = Wins;
            permission_error = PermissionError;
            color = Col;
            verify_id = VerifyId;
            record = Record;
            hide_win_count = HideWinCount;
        }

        public ItemStack GenLiquor(UUID buy_id){
            ItemStack item = Helper.GetItem(Material.POTION, displayName, 0);
            PotionMeta meta = (PotionMeta) item.getItemMeta();
            meta.setColor(color);
            meta.lore(lore);
            meta.getPersistentDataContainer().set(new NamespacedKey(mgbar, "Man10GambleBar"), PersistentDataType.STRING, name);
            meta.getPersistentDataContainer().set(new NamespacedKey(mgbar, "MGBarID"), PersistentDataType.STRING, buy_id.toString());
            meta.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS);
            item.setItemMeta(meta);
            return item;
        }

        public ItemStack GenDisplay(){
            ItemStack item = Helper.GetItem(Material.POTION, displayName, 0);
            PotionMeta meta = (PotionMeta) item.getItemMeta();
            meta.setColor(color);
            meta.lore(lore);
            meta.getPersistentDataContainer().set(new NamespacedKey(mgbar, "Man10GambleBar"), PersistentDataType.STRING, name);
            meta.addCustomEffect(new PotionEffect(PotionEffectType.BLINDNESS, 1, 1), false);
            meta.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS);
            item.setItemMeta(meta);
            return item;
        }
    }

    public static class LiquorWin{
        public String name;
        public Integer chance;
        public Integer price;
        public List<String> commands;
        public List<String> messages;

        public LiquorWin(String Name, Integer Chance, Integer Price, List<String> Commands, List<String> Messages){
            name = Name;
            chance = Chance;
            price = Price;
            commands = Commands;
            messages = Messages;
        }
    }
}
