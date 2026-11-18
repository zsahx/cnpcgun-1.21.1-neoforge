package bincnpc.gun.common.network;

import bincnpc.gun.common.adapter.AdapterRegistry;
import bincnpc.gun.common.adapter.WeaponAdapter;
import bincnpc.gun.common.api.SafeJson;
import bincnpc.gun.common.api.Views;
import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.common.config.ConfigSchema;
import bincnpc.gun.gun.adapter.GunAdapter;
import bincnpc.gun.slashblade.action.ActionRegistry;
import bincnpc.gun.slashblade.adapter.BladeAdapter;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Arrays;
import net.minecraft.world.item.ItemStack;

public final class EditorWeapons {
   private EditorWeapons() {
   }

   public static JsonObject presentation(CombatContext ctx, ActionRegistry.Definition action) {
      for (int i = 0; i < 6; i++) {
         ItemStack stack = ctx.data.armory.get(ctx.npc, i);
         WeaponAdapter adapter = AdapterRegistry.find(stack);
         if (adapter instanceof BladeAdapter blade && adapter.id().equals(action.adapterId)) {
            return AdapterRegistry.query(adapter, () -> blade.presentation(ctx.withWeapon(stack), action), new JsonObject());
         }
      }

      return new JsonObject();
   }

   public static boolean adapter(CombatContext ctx, String id) {
      for (int i = 0; i < 6; i++) {
         WeaponAdapter adapter = AdapterRegistry.find(ctx.data.armory.get(ctx.npc, i));
         if (adapter != null && adapter.id().equals(id)) {
            return true;
         }
      }

      return false;
   }

   public static boolean has(CombatContext ctx, String capability) {
      for (int i = 0; i < 6; i++) {
         ItemStack stack = ctx.data.armory.get(ctx.npc, i);
         WeaponAdapter adapter = AdapterRegistry.find(stack);
         if (adapter instanceof GunAdapter gun) {
            Views.GunCaps caps = AdapterRegistry.query(gun, () -> gun.capabilities(stack), null);
            if (caps != null && caps.has(capability)) {
               return true;
            }
         } else if (adapter instanceof BladeAdapter blade) {
            Views.BladeCaps caps = AdapterRegistry.query(blade, () -> blade.capabilities(stack), null);
            if (caps != null && caps.has(capability)) {
               return true;
            }
         }
      }

      return false;
   }

   public static boolean firingMode(CombatContext ctx, String mode) {
      return mode != null && Arrays.asList(ConfigSchema.get("gun.firingMode").values).contains(mode);
   }

   public static JsonArray snapshot(CombatContext ctx) {
      JsonArray slots = new JsonArray();

      for (int i = 0; i < 6; i++) {
         ItemStack stack = ctx.data.armory.get(ctx.npc, i);
         WeaponAdapter adapter = AdapterRegistry.find(stack);
         JsonObject item = new JsonObject();
         item.addProperty("slot", i);
         item.addProperty("adapter", adapter == null ? "" : adapter.id());
         if (adapter instanceof GunAdapter gun) {
            item.add("gun", SafeJson.GSON.toJsonTree(AdapterRegistry.query(gun, () -> gun.capabilities(stack), null)));
            item.addProperty("interval", AdapterRegistry.query(gun, () -> gun.baseInterval(stack), 4));
         }

         if (adapter instanceof BladeAdapter blade) {
            item.add("slashBlade", SafeJson.GSON.toJsonTree(AdapterRegistry.query(blade, () -> blade.capabilities(stack), null)));
         }

         slots.add(item);
      }

      return slots;
   }
}
