package bincnpc.gun.common.adapter;

import bincnpc.gun.common.combat.CombatContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

public interface WeaponAdapter {
   String id();

   String domain();

   boolean matches(ItemStack var1);

   default String weaponId(ItemStack stack) {
      return this.id() + ":" + BuiltInRegistries.ITEM.getKey(stack.getItem());
   }

   default void onEquip(CombatContext context) {
   }

   default void tick(CombatContext context) {
   }

   default void cancel(CombatContext context) {
   }

   default void close() {
   }
}
