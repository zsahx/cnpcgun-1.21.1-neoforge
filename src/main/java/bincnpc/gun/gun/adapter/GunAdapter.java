package bincnpc.gun.gun.adapter;

import bincnpc.gun.common.adapter.WeaponAdapter;
import bincnpc.gun.common.api.Result;
import bincnpc.gun.common.api.Views;
import bincnpc.gun.common.combat.CombatContext;
import net.minecraft.world.item.ItemStack;

public interface GunAdapter extends WeaponAdapter {
   @Override
   default String domain() {
      return "GUN";
   }

   Views.GunCaps capabilities(ItemStack var1);

   Views.AmmoState ammo(CombatContext var1);

   Result fire(CombatContext var1);

   default Result prepareFire(CombatContext context) {
      return Result.success();
   }

   default void stopFire(CombatContext context) {
   }

   Result reload(CombatContext var1);

   default boolean canUse(CombatContext context, ItemStack candidate) {
      return !candidate.isEmpty();
   }

   default String ammoKey(ItemStack stack) {
      return this.weaponId(stack);
   }

   default boolean matchesAmmo(ItemStack weapon, ItemStack item) {
      return false;
   }

   default int ammoUnits(ItemStack weapon, ItemStack item) {
      return this.matchesAmmo(weapon, item) ? item.getCount() : 0;
   }

   default int extractAmmo(ItemStack weapon, ItemStack item, int requested) {
      int taken = Math.max(0, Math.min(requested, this.ammoUnits(weapon, item)));
      item.shrink(taken);
      return taken;
   }

   default int loadedRounds(ItemStack stack) {
      return -1;
   }

   default int ammoPerShot(ItemStack stack) {
      return 1;
   }

   default int magazineCapacity(ItemStack stack) {
      return -1;
   }

   default void resetReserve(CombatContext context, ItemStack weapon) {
   }

   default int baseInterval(ItemStack stack) {
      return 4;
   }

   default int nativeInterval(CombatContext context) {
      return this.baseInterval(context.weapon);
   }

   default Double spread(ItemStack stack) {
      return null;
   }

   default Double recoil(ItemStack stack) {
      return null;
   }

   default double configuredRecoil(CombatContext ctx) {
      Double nativeRecoil = this.recoil(ctx.weapon);
      return ctx.config.bool("gun.applyRecoil") && nativeRecoil != null && Double.isFinite(nativeRecoil)
         ? Math.abs(nativeRecoil) * ctx.config.number("gun.recoilMultiplier")
         : 0.0;
   }

   default Object aimState(CombatContext context) {
      return null;
   }

   default Result aim(CombatContext context, boolean aiming) {
      return Result.fail("UNSUPPORTED");
   }
}
