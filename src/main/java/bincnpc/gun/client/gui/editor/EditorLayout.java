package bincnpc.gun.client.gui.editor;

import java.util.ArrayList;
import java.util.List;

public final class EditorLayout {
   public final int width;
   public final int height;
   public final EditorLayout.Box header;
   public final EditorLayout.Box sidebar;
   public final EditorLayout.Box workspace;
   public final EditorLayout.Box footer;
   public final EditorLayout.Box body;

   public EditorLayout(int width, int height) {
      this.width = width;
      this.height = height;
      this.header = new EditorLayout.Box(0, 0, width, 34);
      this.footer = new EditorLayout.Box(0, height - 26, width, 26);
      int sideWidth = Math.max(84, Math.min(128, width / 6));
      this.sidebar = new EditorLayout.Box(8, 42, sideWidth, Math.max(0, height - 76));
      this.workspace = new EditorLayout.Box(this.sidebar.right() + 8, 42, Math.max(0, width - this.sidebar.right() - 16), Math.max(0, height - 76));
      this.body = this.workspace.below(28);
   }

   public EditorLayout.Box navigation(int index) {
      return new EditorLayout.Box(this.sidebar.x(), this.sidebar.y() + index * 22, this.sidebar.width(), 20);
   }

   public EditorLayout.Box saveButton() {
      return new EditorLayout.Box(this.width - 96, this.height - 22, 88, 18);
   }

   public EditorLayout.Box discardButton() {
      return new EditorLayout.Box(this.width - 184, this.height - 22, 82, 18);
   }

   public int statusWidth() {
      return this.width - 200;
   }

   public EditorLayout.Box weaponSlot(int index) {
      int gap = this.body.width() >= 300 ? 6 : 4;
      int cellWidth = (this.body.width() - 16 - gap * 5) / 6;
      return new EditorLayout.Box(this.body.x() + 8 + index * (cellWidth + gap), this.body.y() + 5, cellWidth, 45);
   }

   public EditorLayout.Box weaponForm() {
      return this.body.below(56);
   }

   public EditorLayout.Box listPane() {
      int listWidth = Math.max(92, Math.min(200, this.body.width() / 3));
      return new EditorLayout.Box(this.body.x(), this.body.y(), listWidth, this.body.height());
   }

   public EditorLayout.Box detailPane() {
      EditorLayout.Box list = this.listPane();
      return new EditorLayout.Box(list.right() + 8, this.body.y(), Math.max(0, this.body.right() - list.right() - 8), this.body.height());
   }

   public static EditorLayout.Box dialog(int width, int height) {
      return new EditorLayout.Box(Math.max(8, (width - 460) / 2), 12, Math.min(460, width - 16), height - 24);
   }

   public static EditorLayout.Box dialogContent(EditorLayout.Box box) {
      return new EditorLayout.Box(box.x() + 10, box.y() + 42, box.width() - 20, box.height() - 78);
   }

   public static List<EditorLayout.InventorySlot> inventorySlots(EditorLayout.Box content) {
      int cell = Math.min(26, (content.width() - 32) / 9);
      int gap = 2;
      int left = content.x() + Math.max(0, (content.width() - (cell + gap) * 9 - 4 - cell) / 2);
      List<EditorLayout.InventorySlot> result = new ArrayList<>();

      for (int row = 0; row < 3; row++) {
         for (int column = 0; column < 9; column++) {
            result.add(
               new EditorLayout.InventorySlot(
                  9 + row * 9 + column, new EditorLayout.Box(left + column * (cell + gap), content.y() + 11 + row * (cell + gap), cell, cell)
               )
            );
         }
      }

      int hotbarY = content.y() + 18 + 3 * (cell + gap);

      for (int i = 0; i < 9; i++) {
         result.add(new EditorLayout.InventorySlot(i, new EditorLayout.Box(left + i * (cell + gap), hotbarY, cell, cell)));
      }

      result.add(new EditorLayout.InventorySlot(40, new EditorLayout.Box(Math.min(content.right() - cell, left + 9 * (cell + gap) + 4), hotbarY, cell, cell)));
      return result;
   }

   public static EditorLayout.FormGrid formGrid(EditorLayout.Box view, List<EditorLayout.FormField> fields, boolean compact, int scroll) {
      int rowHeight = compact ? 26 : 30;
      int columns = view.width() >= (compact ? 280 : 500) ? 2 : 1;
      int cellWidth = (view.width() - 12 - (columns - 1) * 8) / columns;
      int column = 0;
      int y = compact ? 3 : 4;
      List<EditorLayout.FormCell> cells = new ArrayList<>();

      for (EditorLayout.FormField field : fields) {
         boolean full = field.parts != 1;
         if (full && column != 0) {
            y += rowHeight;
            column = 0;
         }

         int h = field.parts == 0 ? 22 : (full ? 46 : rowHeight);
         EditorLayout.Box row = new EditorLayout.Box(view.x() + 6 + column * (cellWidth + 8), view.y() + y - scroll, full ? view.width() - 12 : cellWidth, h);
         List<EditorLayout.Box> controls = new ArrayList<>();
         if (field.parts == 1) {
            int controlWidth = Math.max(52, Math.min(126, cellWidth / 3));
            controls.add(
               new EditorLayout.Box(
                  row.right() - controlWidth - 4, row.y() + (field.text ? (compact ? 10 : 12) : (compact ? 3 : 5)), controlWidth, field.text ? 8 : 20
               )
            );
         } else if (field.parts > 1) {
            int partWidth = (row.width() - 8 - (field.parts - 1) * 8) / field.parts;
            int labelWidth = field.parts == 2 ? 24 : 12;

            for (int i = 0; i < field.parts; i++) {
               controls.add(new EditorLayout.Box(row.x() + 8 + i * (partWidth + 8) + labelWidth, row.y() + 28, partWidth - labelWidth - 8, 8));
            }
         }

         cells.add(new EditorLayout.FormCell(row, controls));
         if (full) {
            y += h;
         } else if (++column == columns) {
            y += rowHeight;
            column = 0;
         }
      }

      return new EditorLayout.FormGrid(cells, y + (column == 0 ? 0 : rowHeight) + (compact ? 3 : 4));
   }

   public static int clampScroll(int scroll, int totalHeight, int viewHeight) {
      return Math.max(0, Math.min(scroll, Math.max(0, totalHeight - viewHeight)));
   }

   public record Box(int x, int y, int width, int height) {
      public int right() {
         return this.x + this.width;
      }

      public int bottom() {
         return this.y + this.height;
      }

      public boolean contains(double px, double py) {
         return px >= this.x && py >= this.y && px < this.right() && py < this.bottom();
      }

      public boolean intersects(EditorLayout.Box other) {
         return this.x < other.right() && this.right() > other.x && this.y < other.bottom() && this.bottom() > other.y;
      }

      public EditorLayout.Box inset(int n) {
         return new EditorLayout.Box(this.x + n, this.y + n, Math.max(0, this.width - n * 2), Math.max(0, this.height - n * 2));
      }

      public EditorLayout.Box below(int offset) {
         return new EditorLayout.Box(this.x, this.y + offset, this.width, Math.max(0, this.height - offset));
      }
   }

   public record FormCell(EditorLayout.Box row, List<EditorLayout.Box> controls) {
   }

   public record FormField(int parts, boolean text) {
   }

   public record FormGrid(List<EditorLayout.FormCell> cells, int totalHeight) {
   }

   public record InventorySlot(int index, EditorLayout.Box box) {
   }
}
