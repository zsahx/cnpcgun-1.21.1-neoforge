package bincnpc.gun.client.gui.editor;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class EditorTextField extends EditBox {
   public boolean invalid;

   public EditorTextField(Font font, int x, int y, int width, int height, Component label) {
      super(font, x, y, width, height, label);
      this.setBordered(false);
      this.setTextColor(-14077902);
      this.setTextColorUneditable(-9932693);
      this.setMaxLength(128);
   }

   public void tick() {
      // 1.21.1 EditBox blinks its cursor from renderWidget using Util.getMillis().
   }

   public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
      int edge = this.invalid ? -6074553 : (this.isFocused() ? -11047869 : -9932693);
      g.fill(this.getX() - 4, this.getY() - 4, this.getX() + this.getWidth() + 4, this.getY() + this.getHeight() + 4, edge);
      g.fill(
         this.getX() - 3,
         this.getY() - 3,
         this.getX() + this.getWidth() + 3,
         this.getY() + this.getHeight() + 3,
         this.active ? -395024 : -3750202
      );
      super.renderWidget(g, mouseX, mouseY, partialTick);
   }
}
