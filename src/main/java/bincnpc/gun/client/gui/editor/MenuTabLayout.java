package bincnpc.gun.client.gui.editor;

import java.util.ArrayList;
import java.util.List;

public final class MenuTabLayout {
   private MenuTabLayout() {
   }

   public static List<MenuTabLayout.Position> arrange(List<MenuTabLayout.Tab> input, int weaponId, int left, int top, int width) {
      List<MenuTabLayout.Tab> tabs = new ArrayList<>();
      List<MenuTabLayout.Tab> actions = new ArrayList<>();
      MenuTabLayout.Tab weapon = null;

      for (MenuTabLayout.Tab tab : input) {
         if (tab.id == weaponId) {
            weapon = tab;
         } else if (tab.id != 0 && tab.id != 66) {
            tabs.add(tab);
         } else {
            actions.add(tab);
         }
      }

      if (weapon != null) {
         tabs.add(weapon);
      }

      int actionWidth = actions.stream().mapToInt(MenuTabLayout.Tab::width).sum() + (actions.size() > 1 ? 2 : 0);
      int room = width - 8 - actionWidth - 4;
      List<List<MenuTabLayout.Tab>> rows = new ArrayList<>();
      List<MenuTabLayout.Tab> row = new ArrayList<>();
      int used = 0;

      for (MenuTabLayout.Tab tabx : tabs) {
         if (!row.isEmpty() && used + tabx.width > room) {
            rows.add(row);
            row = new ArrayList<>();
            used = 0;
         }

         row.add(tabx);
         used += tabx.width;
      }

      if (!row.isEmpty()) {
         rows.add(row);
      }

      List<MenuTabLayout.Position> result = new ArrayList<>();

      for (int i = 0; i < rows.size(); i++) {
         int x = left + 4;
         int y = top - 17 - (rows.size() - 1 - i) * 26;

         for (MenuTabLayout.Tab tabx : rows.get(i)) {
            result.add(new MenuTabLayout.Position(tabx.id, x, y));
            x += tabx.width;
         }
      }

      int right = left + width - 4;

      for (int id : new int[]{0, 66}) {
         for (MenuTabLayout.Tab tabx : actions) {
            if (tabx.id == id) {
               right -= tabx.width;
               result.add(new MenuTabLayout.Position(id, right, top - 17));
               right -= 2;
            }
         }
      }

      return result;
   }

   public record Position(int id, int x, int y) {
   }

   public record Tab(int id, int width) {
   }
}
