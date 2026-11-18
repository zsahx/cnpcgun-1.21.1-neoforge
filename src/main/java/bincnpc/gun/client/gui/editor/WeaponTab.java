package bincnpc.gun.client.gui.editor;

import bincnpc.gun.client.gui.GuiWeapons;
import net.minecraft.client.Minecraft;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiMenuTopButton;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;

public final class WeaponTab {
   private WeaponTab() {
   }

   public static GuiMenuTopButton create(final IGuiInterface parent, final EntityNPCInterface npc, int x, int y) {
      return new GuiMenuTopButton(parent, 87, x, y, "cnpcgun.gui.tab") {
         public void onClick(double mouseX, double mouseY) {
            GuiTextFieldNop.unfocus();
            parent.save();
            Minecraft.getInstance().setScreen(new GuiWeapons(npc));
         }
      };
   }
}
