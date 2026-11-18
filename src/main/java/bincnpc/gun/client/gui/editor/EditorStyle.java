package bincnpc.gun.client.gui.editor;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class EditorStyle {
   public static final int IRON = -3750202;
   public static final int PAPER = -1119006;
   public static final int SLATE = -12828349;
   public static final int EDGE = -13158601;
   public static final int INK = -14077902;
   public static final int MUTED = -9932693;
   public static final int GREEN = -11047869;
   public static final int GOLD = -5733307;
   public static final int RED = -6074553;
   public static final int WHITE = -395024;
   public static final int HOVER = -2301487;
   public static final int LINE = -3092283;

   private EditorStyle() {
   }

   public static void panel(GuiGraphics g, EditorLayout.Box box, int fill) {
      if (box.width() > 0 && box.height() > 0) {
         g.fill(box.x(), box.y(), box.right(), box.bottom(), -13158601);
         g.fill(box.x() + 1, box.y() + 1, box.right() - 1, box.bottom() - 1, fill);
         g.hLine(box.x() + 1, box.right() - 2, box.y() + 1, -395024);
         g.vLine(box.x() + 1, box.y() + 1, box.bottom() - 2, -395024);
      }
   }

   public static void slot(GuiGraphics g, int x, int y, int size, boolean selected) {
      g.fill(x, y, x + size, y + size, selected ? -11047869 : -13158601);
      g.fill(x + 1, y + 1, x + size - 1, y + size - 1, -7632000);
      g.hLine(x + 1, x + size - 2, y + 1, -11249329);
      g.vLine(x + 1, y + 1, y + size - 2, -11249329);
      g.hLine(x + 1, x + size - 2, y + size - 2, -395024);
      g.vLine(x + size - 2, y + 1, y + size - 2, -395024);
   }

   public static String clipped(Font font, String text, int width) {
      if (width <= 0) {
         return "";
      } else {
         return font.width(text) <= width ? text : font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
      }
   }

   public static void text(GuiGraphics g, Font font, String text, int x, int y, int width, int color) {
      g.drawString(font, clipped(font, text, width), x, y, color, false);
   }

   public static void arrow(GuiGraphics g, int x, int y, int color) {
      g.fill(x + 3, y + 3, x + 11, y + 5, color);
      g.fill(x + 1, y + 3, x + 3, y + 5, color);
      g.fill(x + 3, y + 1, x + 5, y + 3, color);
      g.fill(x + 3, y + 5, x + 5, y + 7, color);
   }

   public static void refresh(GuiGraphics g, int x, int y, int color) {
      g.fill(x + 3, y + 1, x + 8, y + 3, color);
      g.fill(x + 1, y + 3, x + 3, y + 7, color);
      g.fill(x + 3, y + 7, x + 7, y + 9, color);
      g.fill(x + 7, y + 5, x + 9, y + 7, color);
      g.fill(x + 8, y + 1, x + 10, y + 5, color);
      g.fill(x + 6, y + 3, x + 8, y + 5, color);
   }
}
