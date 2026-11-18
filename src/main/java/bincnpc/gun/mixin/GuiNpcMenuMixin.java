package bincnpc.gun.mixin;

import bincnpc.gun.client.gui.editor.MenuTabLayout;
import bincnpc.gun.client.gui.editor.WeaponTab;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import noppes.npcs.client.gui.util.GuiNpcMenu;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiMenuTopButton;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {GuiNpcMenu.class},
   remap = false,
   priority = 900
)
public abstract class GuiNpcMenuMixin {
   @Shadow
   private IGuiInterface parent;
   @Shadow
   private GuiMenuTopButton[] topButtons;
   @Shadow
   private EntityNPCInterface npc;
   @Unique
   private int cnpcgun$left;
   @Unique
   private int cnpcgun$top;
   @Unique
   private int cnpcgun$width;

   @Inject(
      method = {"initGui"},
      at = {@At("TAIL")}
     ,
      require = 0
   )
   private void cnpcgun$tab(int left, int top, int width, CallbackInfo ci) {
      this.cnpcgun$left = left;
      this.cnpcgun$top = top;
      this.cnpcgun$width = width;
      this.cnpcgun$ensureLayout();
   }

   @Inject(
      method = {"drawElements"},
      at = {@At("HEAD")}
     ,
      require = 0
   )
   private void cnpcgun$draw(GuiGraphics graphics, Font font, int x, int y, Minecraft minecraft, float partialTick, CallbackInfo ci) {
      this.cnpcgun$ensureLayout();
   }

   @Inject(
      method = {"mouseClicked"},
      at = {@At("HEAD")}
     ,
      require = 0
   )
   private void cnpcgun$click(double x, double y, int button, CallbackInfoReturnable<Boolean> ci) {
      this.cnpcgun$ensureLayout();

      for (GuiMenuTopButton tab : this.topButtons) {
         tab.hover = x >= tab.getX() && y >= tab.getY() && x < tab.getX() + tab.getWidth() && y < tab.getY() + (tab.active ? 25 : 18);
      }
   }

   @Unique
   private void cnpcgun$ensureLayout() {
      if (this.cnpcgun$width > 0 && this.topButtons.length != 0) {
         boolean present = false;

         for (GuiMenuTopButton button : this.topButtons) {
            if (button.id == 87) {
               present = true;
            }
         }

         if (!present) {
            GuiMenuTopButton[] extended = Arrays.copyOf(this.topButtons, this.topButtons.length + 1);
            extended[this.topButtons.length] = WeaponTab.create(this.parent, this.npc, this.cnpcgun$left + 4, this.cnpcgun$top - 17);
            this.topButtons = extended;
         }

         List<MenuTabLayout.Tab> tabs = new ArrayList<>();

         for (GuiMenuTopButton buttonx : this.topButtons) {
            tabs.add(new MenuTabLayout.Tab(buttonx.id, buttonx.getWidth()));
         }

         for (MenuTabLayout.Position position : MenuTabLayout.arrange(tabs, 87, this.cnpcgun$left, this.cnpcgun$top, this.cnpcgun$width)) {
            for (GuiMenuTopButton buttonx : this.topButtons) {
               if (buttonx.id == position.id()) {
                  buttonx.setX(position.x());
                  buttonx.setY(position.y());
               }
            }
         }
      }
   }
}
