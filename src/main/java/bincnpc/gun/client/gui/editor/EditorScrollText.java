package bincnpc.gun.client.gui.editor;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class EditorScrollText extends AbstractWidget {
   private final Font font;
   private final List<FormattedCharSequence> lines = new ArrayList<>();
   private int scroll;

   public EditorScrollText(Font font, EditorLayout.Box box, List<String> text) {
      super(box.x(), box.y(), box.width(), box.height(), Component.empty());
      this.font = font;

      for (String line : text) {
         this.lines.addAll(font.split(Component.literal(line), Math.max(12, box.width() - 16)));
      }
   }

   protected void renderWidget(GuiGraphics g, int mx, int my, float partialTick) {
      EditorStyle.panel(g, new EditorLayout.Box(this.getX(), this.getY(), this.width, this.height), -1119006);
      g.enableScissor(this.getX() + 2, this.getY() + 2, this.getX() + this.width - 4, this.getY() + this.height - 2);

      for (int i = 0; i < this.lines.size(); i++) {
         int y = this.getY() + 5 + i * 12 - this.scroll;
         if (y + 10 >= this.getY() && y < this.getY() + this.height) {
            g.drawString(this.font, this.lines.get(i), this.getX() + 6, y, -14077902, false);
         }
      }

      g.disableScissor();
      int total = this.lines.size() * 12 + 10;
      if (total > this.height) {
         int thumb = Math.max(8, this.height * this.height / total);
         int top = this.getY() + (this.height - thumb) * this.scroll / Math.max(1, total - this.height);
         g.fill(this.getX() + this.width - 4, top, this.getX() + this.width - 2, top + thumb, -9932693);
      }
   }

   public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
      double delta = scrollY;
      if (!this.isMouseOver(x, y)) {
         return false;
      } else {
         this.scroll = EditorLayout.clampScroll(this.scroll - (int)(delta * 24.0), this.lines.size() * 12 + 10, this.height);
         return true;
      }
   }

   public boolean keyPressed(int key, int scan, int modifiers) {
      int step = key == 264 ? 12 : (key == 265 ? -12 : (key == 267 ? this.height - 12 : (key == 266 ? 12 - this.height : 0)));
      if (step == 0) {
         return false;
      } else {
         this.scroll = EditorLayout.clampScroll(this.scroll + step, this.lines.size() * 12 + 10, this.height);
         return true;
      }
   }

   protected void updateWidgetNarration(NarrationElementOutput output) {
      this.defaultButtonNarrationText(output);
   }
}
