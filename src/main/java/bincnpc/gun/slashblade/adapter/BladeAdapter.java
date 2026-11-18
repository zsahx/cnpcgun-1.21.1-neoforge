package bincnpc.gun.slashblade.adapter;

import bincnpc.gun.common.adapter.WeaponAdapter;
import bincnpc.gun.common.api.Result;
import bincnpc.gun.common.api.Views;
import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.slashblade.action.ActionRegistry;
import com.google.gson.JsonObject;
import net.minecraft.world.item.ItemStack;

public interface BladeAdapter extends WeaponAdapter {
   @Override
   default String domain() {
      return "SLASHBLADE";
   }

   Views.BladeCaps capabilities(ItemStack var1);

   void registerActions(ActionRegistry var1);

   Result execute(CombatContext var1, ActionRegistry.Definition var2, JsonObject var3);

   JsonObject state(CombatContext var1);

   default Result interruptForThrow(CombatContext context) {
      return Result.fail("UNSUPPORTED");
   }

   default String nextNormal(CombatContext context) {
      return "";
   }

   default JsonObject presentation(CombatContext context, ActionRegistry.Definition action) {
      JsonObject out = new JsonObject();
      out.addProperty("backend", action.backend);
      if (action.backend.startsWith("combo:")) {
         out.addProperty("stageId", action.backend.substring(6));
      }

      return out;
   }

   default boolean canChain(CombatContext context, ActionRegistry.Definition action, JsonObject parameters) {
      return false;
   }

   public record Execution(String stage, int duration) {
   }
}
