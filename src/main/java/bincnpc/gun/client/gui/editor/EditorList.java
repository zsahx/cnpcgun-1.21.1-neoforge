package bincnpc.gun.client.gui.editor;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public final class EditorList extends AbstractWidget {
   private final Font font;
   private List<EditorList.Row> all;
   private final Consumer<String> selected;
   private List<EditorList.Row> shown;
   private String selection = "";
   private int scroll;
   private String query = "";
   private static final int ROW = 34;

   public EditorList(Font font, EditorLayout.Box box, List<EditorList.Row> rows, String initial, Consumer<String> selected) {
      super(box.x(), box.y(), box.width(), box.height(), Component.translatable("cnpcgun.editor.selection"));
      this.font = font;
      this.all = List.copyOf(rows);
      this.shown = this.all;
      this.selection = initial;
      this.selected = selected;
   }

   public void filter(String search) {
      String needle = search.trim().toLowerCase(Locale.ROOT);
      this.query = search;
      this.shown = this.all.stream().filter(row -> (row.title + " " + row.subtitle + " " + row.id).toLowerCase(Locale.ROOT).contains(needle)).toList();
      this.scroll = 0;
   }

   public void replaceRows(List<EditorList.Row> rows) {
      int prior = this.scroll;
      this.all = List.copyOf(rows);
      this.filter(this.query);
      this.scrollTo(prior);
   }

   public void selection(String id) {
      this.selection = id;
   }

   public int size() {
      return this.shown.size();
   }

   public int scrollOffset() {
      return this.scroll;
   }

   public void scrollTo(int offset) {
      this.scroll = EditorLayout.clampScroll(offset, this.shown.size() * 34, this.height - 6);
   }

   protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
      EditorStyle.panel(g, new EditorLayout.Box(this.getX(), this.getY(), this.width, this.height), -1119006);
      g.enableScissor(this.getX() + 1, this.getY() + 1, this.getX() + this.width - 1, this.getY() + this.height - 1);

      for (int i = 0; i < this.shown.size(); i++) {
         EditorList.Row row = this.shown.get(i);
         int y = this.getY() + 3 + i * 34 - this.scroll;
         if (y + 34 > this.getY() && y < this.getY() + this.height) {
            boolean chosen = row.id.equals(this.selection);
            boolean hover = mouseX >= this.getX() && mouseX < this.getX() + this.width && mouseY >= y && mouseY < y + 34;
            if (chosen || hover) {
               g.fill(this.getX() + 2, y, this.getX() + this.width - 2, y + 34 - 1, chosen ? -2301487 : -1907752);
            }

            if (chosen) {
               g.fill(this.getX() + 2, y + 3, this.getX() + 5, y + 34 - 4, -11047869);
            }

            EditorStyle.text(g, this.font, row.title, this.getX() + 9, y + 6, this.width - 16, row.enabled ? -14077902 : -9932693);
            EditorStyle.text(g, this.font, row.subtitle, this.getX() + 9, y + 19, this.width - 16, -9932693);
            g.hLine(this.getX() + 7, this.getX() + this.width - 7, y + 34 - 1, -3092283);
         }
      }

      g.disableScissor();
      if (this.shown.isEmpty()) {
         EditorStyle.text(
            g,
            this.font,
            Component.translatable("cnpcgun.editor.noResults").getString(),
            this.getX() + 8,
            this.getY() + 10,
            this.width - 16,
            -9932693
         );
      }

      if (this.shown.size() * 34 > this.height - 6) {
         int track = this.height - 6;
         int thumb = Math.max(12, track * track / (this.shown.size() * 34));
         int y = this.getY() + 3 + (track - thumb) * this.scroll / Math.max(1, this.shown.size() * 34 - track);
         g.fill(this.getX() + this.width - 4, y, this.getX() + this.width - 2, y + thumb, -9932693);
      }
   }

   private void select(int index) {
      if (index >= 0 && index < this.shown.size()) {
         this.selection = this.shown.get(index).id;
         this.selected.accept(this.selection);
      }
   }

   public boolean mouseClicked(double x, double y, int button) {
      if (button == 0 && this.active && new EditorLayout.Box(this.getX(), this.getY(), this.width, this.height).contains(x, y)) {
         this.select((int)(y - this.getY() - 3.0 + this.scroll) / 34);
         return true;
      } else {
         return false;
      }
   }

   public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
      double delta = scrollY;
      if (!new EditorLayout.Box(this.getX(), this.getY(), this.width, this.height).contains(x, y)) {
         return false;
      } else {
         this.scroll = EditorLayout.clampScroll(this.scroll - (int)Math.round(delta * 34.0), this.shown.size() * 34, this.height - 6);
         return true;
      }
   }

   public boolean keyPressed(int key, int scan, int modifiers) {
      if (this.active && this.isFocused()) {
         int index = 0;

         for (int i = 0; i < this.shown.size(); i++) {
            if (this.shown.get(i).id.equals(this.selection)) {
               index = i;
            }
         }

         if (key != 264 && key != 265) {
            return false;
         } else {
            int next = Math.max(0, Math.min(this.shown.size() - 1, index + (key == 264 ? 1 : -1)));
            this.scroll = EditorLayout.clampScroll(Math.max(0, next * 34 - this.height / 2), this.shown.size() * 34, this.height - 6);
            this.select(next);
            return true;
         }
      } else {
         return false;
      }
   }

   protected void updateWidgetNarration(NarrationElementOutput output) {
      this.defaultButtonNarrationText(output);
   }

   public record Row(String id, String title, String subtitle, boolean enabled) {
   }
}
