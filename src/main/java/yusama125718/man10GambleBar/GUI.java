package yusama125718.man10GambleBar;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

import static yusama125718.man10GambleBar.Man10GambleBar.liquors;

public class GUI {

    public static void OpenRecordMenu(Player p, int page){
        p.closeInventory();
        Inventory inv = Bukkit.createInventory(null,54, Component.text("[Man10GambleBar] 記録メニュー"));
        for (int i = 51; i < 54; i++){
            inv.setItem(i,Helper.GetItem(Material.BLUE_STAINED_GLASS_PANE, "次のページへ",1));
            inv.setItem(i - 3,Helper.GetItem(Material.WHITE_STAINED_GLASS_PANE, "",1));
            inv.setItem(i - 6,Helper.GetItem(Material.RED_STAINED_GLASS_PANE, "前のページへ",1));
        }
        int cnt = 0;
        List<Man10GambleBar.Liquor> list = new ArrayList<>(liquors.values());
        for (int i = (page - 1) * 45; i < list.size(); i++){
            // 次のページに行ったら処理を終了
            if (i >= page * 45) break;
            if (!list.get(i).record) continue;
            inv.setItem(cnt, list.get(i).GenDisplay());
            cnt++;
        }
        p.openInventory(inv);
    }

    public static void OpenShopMenu(Player p, Man10GambleBar.Shop shop){
        p.closeInventory();
        Inventory inv = Bukkit.createInventory(null, shop.size, Component.text("[Man10GambleBar] バーカウンター"));
        for (Man10GambleBar.ShopItem item: shop.items){
            int slot = item.x + item.y * 9;
            if (item.liquor_name == null) inv.setItem(slot, item.item.clone());
            else inv.setItem(slot, liquors.get(item.liquor_name).GenDisplay());
        }
        p.openInventory(inv);
    }

    public static void OpenRecordChoice(Player p, Man10GambleBar.Liquor liq){
        p.closeInventory();
        Inventory inv = Bukkit.createInventory(null, 9, Component.text("[Man10GambleBar] 記録種別選択"));
        inv.setItem(4, liq.GenDisplay());
        inv.setItem(2, Helper.GetItem(Material.PAPER, "自分の記録を見る", 0));
        inv.setItem(6, Helper.GetItem(Material.WRITABLE_BOOK, "ランキングを見る", 0));
        p.openInventory(inv);
    }
}
