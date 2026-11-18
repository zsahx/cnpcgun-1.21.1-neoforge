package bincnpc.gun.client.gui.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

public final class EditorToast {
   private String message = "";
   private long started;

   public void show(String text) {
      this.message = text;
      this.started = System.currentTimeMillis();
      Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.1F, 0.45F));
   }

   public void render(GuiGraphics g, int width) {
      long age = System.currentTimeMillis() - this.started;
      if (!this.message.isEmpty() && age < 1700L) {
         Font font = Minecraft.getInstance().font;
         int w = Math.min(width - 16, Math.max(120, font.width(this.message) + 28));
         float slide = age < 180L ? (float)age / 180.0F : (age > 1500L ? (float)(1700L - age) / 200.0F : 1.0F);
         int x = width - 8 - (int)(w * Mth.clamp(slide, 0.0F, 1.0F));
         EditorStyle.panel(g, new EditorLayout.Box(x, 39, w, 26), -11047869);
         g.fill(x + 5, 46, x + 8, 54, -5258092);
         EditorStyle.text(g, font, this.message, x + 15, 48, w - 22, -395024);
      }
   }
}
