package bincnpc.gun.client.gui.editor;

import com.google.gson.JsonObject;
import net.minecraft.world.item.ItemStack;

/**
 * Blade stage preview helper. The 1.20.1 implementation pushed combo stages into the SlashBlade item
 * capability; that API does not exist in the 1.21.1 SlashBlade build, so previews are inert here.
 */
final class BladePreview {
   private BladePreview() {
   }

   static String pose(ItemStack blade, JsonObject action, JsonObject parameters, long now) {
      return "";
   }

   static void clear(ItemStack blade, long now) {
   }
}
