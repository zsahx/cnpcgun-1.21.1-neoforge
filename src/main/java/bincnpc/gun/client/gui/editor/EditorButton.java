package bincnpc.gun.client.gui.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public class EditorButton extends AbstractWidget {
   private final Runnable clicked;
   public final EditorButton.Kind kind;
   public boolean selected;

   public EditorButton(int x, int y, int width, int height, Component label, EditorButton.Kind kind, Runnable clicked) {
      super(x, y, width, height, label);
      this.kind = kind;
      this.clicked = clicked;
   }

   public EditorButton(EditorLayout.Box box, Component label, EditorButton.Kind kind, Runnable clicked) {
      this(box.x(), box.y(), box.width(), box.height(), label, kind, clicked);
   }

   protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
      boolean hover = this.isHoveredOrFocused();
      int fill = !this.active
         ? -3026490
         : (
            !this.selected && this.kind != EditorButton.Kind.PRIMARY
               ? (this.kind == EditorButton.Kind.DANGER ? -6074553 : (hover ? -2301487 : -1119006))
               : -11047869
         );
      if (this.kind == EditorButton.Kind.NAVIGATION) {
         fill = this.selected ? -1119006 : (hover ? -11182248 : -12828349);
      }

      if (this.active && this.kind == EditorButton.Kind.ADD) {
         fill = hover ? -8283788 : -10125740;
      }

      if (this.active && this.kind == EditorButton.Kind.EDIT) {
         fill = hover ? -4807816 : -6651048;
      }

      if (this.kind == EditorButton.Kind.TOOL) {
         fill = !this.active ? -9406861 : (hover ? -10984611 : -12828349);
      }

      if (this.kind == EditorButton.Kind.SEGMENT) {
         fill = this.selected ? -4538443 : (!this.active ? -3026490 : (hover ? -2301487 : -1119006));
      }

      int color = (this.kind != EditorButton.Kind.NAVIGATION || this.selected)
            && (!this.active || !this.selected && this.kind != EditorButton.Kind.PRIMARY && this.kind != EditorButton.Kind.DANGER)
         ? -14077902
         : -395024;
      if (this.kind == EditorButton.Kind.NAVIGATION) {
         color = this.selected ? -14077902 : -395024;
      }

      if (!this.active) {
         color = -9932693;
      }

      if (this.kind == EditorButton.Kind.TOOL || this.active && (this.kind == EditorButton.Kind.ADD || this.kind == EditorButton.Kind.EDIT)) {
         color = -395024;
      }

      if (this.kind == EditorButton.Kind.SEGMENT && this.selected) {
         color = -14077902;
      }

      EditorStyle.panel(g, new EditorLayout.Box(this.getX(), this.getY(), this.width, this.height), fill);
      if (this.selected && this.kind == EditorButton.Kind.NAVIGATION) {
         g.fill(this.getX() + 1, this.getY() + 2, this.getX() + 4, this.getY() + this.height - 2, -11047869);
      }

      if (this.isFocused()) {
         g.renderOutline(this.getX() + 2, this.getY() + 2, this.width - 4, this.height - 4, -5733307);
      }

      if (this.kind == EditorButton.Kind.REFRESH) {
         EditorStyle.refresh(g, this.getX() + (this.width - 10) / 2, this.getY() + (this.height - 10) / 2, color);
      } else {
         Font font = Minecraft.getInstance().font;
         int offset = this.kind == EditorButton.Kind.BACK ? 20 : (this.kind == EditorButton.Kind.NAVIGATION ? 10 : 6);
         if (this.kind == EditorButton.Kind.BACK) {
            EditorStyle.arrow(g, this.getX() + 6, this.getY() + (this.height - 8) / 2, color);
         }

         String text = EditorStyle.clipped(font, this.getMessage().getString(), this.width - (this.kind == EditorButton.Kind.TOOL ? 4 : offset + 6));
         int textX = this.kind != EditorButton.Kind.NAVIGATION && this.kind != EditorButton.Kind.BACK
            ? this.getX() + (this.width - font.width(text)) / 2
            : this.getX() + offset;
         g.drawString(font, text, textX, this.getY() + (this.height - 8) / 2, color, false);
      }
   }

   public void onClick(double x, double y) {
      if (this.active) {
         this.clicked.run();
      }
   }

   public boolean keyPressed(int key, int scan, int modifiers) {
      if (!this.active || !this.isFocused() || key != 257 && key != 335 && key != 32) {
         return false;
      } else {
         this.clicked.run();
         return true;
      }
   }

   protected void updateWidgetNarration(NarrationElementOutput output) {
      this.defaultButtonNarrationText(output);
   }

   public static enum Kind {
      NORMAL,
      PRIMARY,
      DANGER,
      NAVIGATION,
      BACK,
      REFRESH,
      SEGMENT,
      ADD,
      EDIT,
      TOOL;
   }
}
