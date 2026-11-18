package bincnpc.gun.compat.tacz;

import bincnpc.gun.common.api.Result;
import bincnpc.gun.common.api.Views;
import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.common.combat.CombatService;
import bincnpc.gun.gun.adapter.GunAdapter;
import bincnpc.gun.gun.ammo.AmmoController;
import bincnpc.gun.gun.ammo.AmmoInventory;
import bincnpc.gun.gun.controller.ReloadPolicy;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.ChargeData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.GunRecoil;
import com.tacz.guns.resource.pojo.data.gun.GunRecoilKeyFrame;
import com.tacz.guns.resource.pojo.data.gun.GunReloadData;
import com.tacz.guns.util.AttachmentDataUtils;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.entity.EntityNPCInterface;

public final class TaczAdapter implements GunAdapter {
   @Override
   public String id() {
      return "tacz";
   }

   @Override
   public boolean matches(ItemStack stack) {
      if (stack != null && !stack.isEmpty() && stack.getItem() instanceof AbstractGunItem) {
         ResourceLocation gunId = ((IGun)stack.getItem()).getGunId(stack);
         return gunId != null && TimelessAPI.getCommonGunIndex(gunId).filter(index -> index.getGunData() != null).isPresent();
      } else {
         return false;
      }
   }

   private IGun gun(ItemStack stack) {
      return (IGun)stack.getItem();
   }

   private GunData data(ItemStack stack) {
      return ((CommonGunIndex)TimelessAPI.getCommonGunIndex(this.gun(stack).getGunId(stack)).orElseThrow()).getGunData();
   }

   @Override
   public String weaponId(ItemStack stack) {
      return "tacz:" + this.gun(stack).getGunId(stack);
   }

   @Override
   public String ammoKey(ItemStack stack) {
      return "tacz:" + this.data(stack).getAmmoId();
   }

   @Override
   public boolean matchesAmmo(ItemStack weapon, ItemStack ammo) {
      return weapon != null
         && !weapon.isEmpty()
         && ammo != null
         && !ammo.isEmpty()
         && ammo.getItem() instanceof IAmmo item
         && item.isAmmoOfGun(weapon, ammo);
   }

   @Override
   public int ammoUnits(ItemStack weapon, ItemStack ammo) {
      if (weapon != null && !weapon.isEmpty() && ammo != null && !ammo.isEmpty()) {
         if (ammo.getItem() instanceof IAmmoBox box && box.isAmmoBoxOfGun(weapon, ammo)) {
            return box.isCreative(ammo) ? 0 : (int)Math.min(100001L, (long)Math.max(0, box.getAmmoCount(ammo)) * ammo.getCount());
         } else {
            return GunAdapter.super.ammoUnits(weapon, ammo);
         }
      } else {
         return 0;
      }
   }

   @Override
   public int extractAmmo(ItemStack weapon, ItemStack ammo, int requested) {
      if (requested > 0 && weapon != null && !weapon.isEmpty() && ammo != null && !ammo.isEmpty()) {
         if (ammo.getItem() instanceof IAmmoBox box && box.isAmmoBoxOfGun(weapon, ammo) && !box.isCreative(ammo)) {
            int count = Math.max(0, Math.min(requested, box.getAmmoCount(ammo)));
            box.setAmmoCount(ammo, box.getAmmoCount(ammo) - count);
            return count;
         } else {
            return GunAdapter.super.extractAmmo(weapon, ammo, requested);
         }
      } else {
         return 0;
      }
   }

   private int chambered(ItemStack stack) {
      return this.data(stack).getBolt() != Bolt.OPEN_BOLT && this.gun(stack).hasBulletInBarrel(stack) ? 1 : 0;
   }

   private int rawCapacity(ItemStack stack) {
      return AttachmentDataUtils.getAmmoCountWithAttachment(stack, this.data(stack));
   }

   private boolean freeReload(ItemStack stack) {
      GunReloadData reload = this.data(stack).getReloadData();
      return !this.gun(stack).useInventoryAmmo(stack) && reload != null && reload.isInfinite();
   }

   @Override
   public int loadedRounds(ItemStack stack) {
      return Math.max(0, this.gun(stack).getCurrentAmmoCount(stack)) + this.chambered(stack);
   }

   @Override
   public int magazineCapacity(ItemStack stack) {
      return this.rawCapacity(stack) + this.chambered(stack);
   }

   @Override
   public void resetReserve(CombatContext ctx, ItemStack stack) {
      this.gun(stack).setDummyAmmoAmount(stack, 0);
      this.gun(stack).setMaxDummyAmmoAmount(stack, 100000);
   }

   @Override
   public Views.GunCaps capabilities(ItemStack stack) {
      Views.GunCaps c = new Views.GunCaps();
      if (!this.matches(stack)) {
         return c;
      } else {
         IGun gun = this.gun(stack);
         c.canFire = true;
         c.canControlFireRate = true;
         c.canOverrideNativeRate = true;
         c.hasMagazine = !gun.useInventoryAmmo(stack);
         c.canReload = c.hasMagazine;
         c.canReadAmmo = true;
         c.canConsumeAmmo = true;
         c.hasReserveAmmo = !this.freeReload(stack);
         c.hasAimState = true;
         c.canAim = true;
         c.hasSpread = true;
         c.hasRecoil = this.data(stack).getRecoil() != null;
         c.supportsSingleShot = true;
         c.supportsAutomaticFire = true;
         c.supportsBurst = true;
         c.firingModes = new String[]{"NATIVE", "SINGLE", "SEMI", "AUTO", "BURST", "CUSTOM"};
         c.projectileModel = "INTERNAL";
         c.supportsSpecialMechanics = true;
         c.specialMechanics = this.data(stack).getChargeData(gun.getFireMode(stack)) == null
            ? new String[]{"BOLT", "HEAT", "NATIVE_SCRIPTS"}
            : new String[]{"BOLT", "HEAT", "CHARGE", "NATIVE_SCRIPTS"};
         c.supportsPartialReload = true;
         return c.withNpcControls();
      }
   }

   @Override
   public void onEquip(CombatContext ctx) {
      IGun gun = this.gun(ctx.weapon);
      if (!gun.useDummyAmmo(ctx.weapon)) {
         gun.setDummyAmmoAmount(ctx.weapon, 0);
      }

      gun.setMaxDummyAmmoAmount(
         ctx.weapon,
         Math.max(
            gun.getDummyAmmoAmount(ctx.weapon), Math.max(1, Math.min(100000, Math.max(this.rawCapacity(ctx.weapon), ctx.config.integer("ammo.reserveLimit"))))
         )
      );
      IGunOperator.fromLivingEntity(ctx.npc).draw(() -> ctx.weapon);
   }

   private int itemAmmo(CombatContext ctx) {
      if (!AmmoController.allowsItems(ctx)) {
         return 0;
      } else {
         AmmoInventory inventory = new AmmoInventory(ctx.npc);
         int amount = 0;

         for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            amount = (int)Math.min(100000L, (long)amount + Math.max(0, this.ammoUnits(ctx.weapon, stack)));
         }

         return Math.min(100000, amount);
      }
   }

   @Override
   public boolean canUse(CombatContext ctx, ItemStack stack) {
      if (!this.matches(stack)) {
         return false;
      } else {
         IGun item = this.gun(stack);
         return ctx.config.bool("ammo.infinite")
            || this.loadedRounds(stack) > 0
            || this.freeReload(stack)
            || item.getDummyAmmoAmount(stack) > 0
            || this.itemAmmo(ctx.withWeapon(stack)) > 0
            || AmmoController.allowsIndependent(ctx) && ctx.data.ammo.independent(ctx, this, stack) > ctx.config.integer("ammo.minimumReserve");
      }
   }

   private void feedReserve(CombatContext ctx, int requested) {
      IGun gun = this.gun(ctx.weapon);
      int need = ReloadPolicy.bufferNeed(requested, gun.getDummyAmmoAmount(ctx.weapon), gun.getMaxDummyAmmoAmount(ctx.weapon));
      int added = ctx.data.ammo.take(ctx, this, need);
      if (added > 0) {
         gun.addDummyAmmoAmount(ctx.weapon, added);
      }
   }

   @Override
   public Views.AmmoState ammo(CombatContext ctx) {
      if (!this.matches(ctx.weapon)) {
         return null;
      } else {
         IGun gun = this.gun(ctx.weapon);
         int capacity = this.rawCapacity(ctx.weapon);
         int chamber = this.chambered(ctx.weapon);
         int count = gun.useInventoryAmmo(ctx.weapon) ? gun.getDummyAmmoAmount(ctx.weapon) : gun.getCurrentAmmoCount(ctx.weapon);
         count = Math.max(0, count) + chamber;
         int reserve = (int)Math.min(
            100000L,
            (long)Math.max(0, gun.getDummyAmmoAmount(ctx.weapon))
               + this.itemAmmo(ctx)
               + (
                  AmmoController.allowsIndependent(ctx)
                     ? Math.max(0, ctx.data.ammo.independent(ctx, this, ctx.weapon) - ctx.config.integer("ammo.minimumReserve"))
                     : 0
               )
         );
         ShooterDataHolder holder = IGunOperator.fromLivingEntity(ctx.npc).getDataHolder();
         boolean reload = holder != null
            && holder.currentGunItem != null
            && holder.currentGunItem.get() == ctx.weapon
            && holder.reloadStateType != null
            && holder.reloadStateType.isReloading();
         boolean magazine = !gun.useInventoryAmmo(ctx.weapon);
         boolean free = this.freeReload(ctx.weapon);
         return new Views.AmmoState(
            magazine,
            count,
            magazine ? capacity + chamber : null,
            !free,
            free ? null : reserve,
            reload,
            magazine && !reload && gun.getCurrentAmmoCount(ctx.weapon) < capacity && (free || reserve > 0)
         );
      }
   }

   @Override
   public int baseInterval(ItemStack stack) {
      return Math.max(1, (int)Math.ceil(1200.0 / Math.max(1, this.data(stack).getRoundsPerMinute(this.gun(stack).getFireMode(stack)))));
   }

   @Override
   public int nativeInterval(CombatContext ctx) {
      GunData nativeData = this.data(ctx.weapon);
      FireMode mode = this.gun(ctx.weapon).getFireMode(ctx.weapon);
      double millis = mode == FireMode.BURST && nativeData.getBurstData() != null
         ? nativeData.getBurstData().getMinInterval() * 1000.0
         : nativeData.getShootInterval(ctx.npc, mode, ctx.weapon);
      return Double.isFinite(millis) ? Math.max(1, (int)Math.ceil(millis / 50.0)) : this.baseInterval(ctx.weapon);
   }

   public static CombatContext fireContext(LivingEntity shooter, ItemStack stack) {
      if (shooter instanceof EntityNPCInterface npc && CombatService.serverThread(npc) && CombatService.validNpc(npc)) {
         CombatContext ctx = new CombatContext(npc);
         if (ctx.runtime.adapter instanceof TaczAdapter adapter
            && !ctx.runtime.editorPaused
            && ctx.config.bool("combat.enabled")
            && ctx.config.bool("gun.enabled")
            && ctx.runtime.gunCaps != null
            && ctx.runtime.gunCaps.canFire
            && ctx.runtime.shotInterval > 0
            && stack != null
            && stack == ctx.weapon
            && stack == npc.getMainHandItem()
            && adapter.matches(stack)) {
            ShooterDataHolder holder = IGunOperator.fromLivingEntity(npc).getDataHolder();
            return holder != null && holder.currentGunItem != null && holder.currentGunItem.get() == stack ? ctx : null;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   public static CombatContext rateContext(LivingEntity shooter, ItemStack stack) {
      CombatContext ctx = fireContext(shooter, stack);
      return ctx != null && ctx.config.bool("gun.overrideNativeRate") && ctx.runtime.gunCaps.canOverrideNativeRate ? ctx : null;
   }

   @Override
   public Result prepareFire(CombatContext ctx) {
      if (!this.matches(ctx.weapon)) {
         return Result.fail("NO_COMPATIBLE_WEAPON");
      } else {
         IGun item = this.gun(ctx.weapon);
         String mode = ctx.config.text("gun.firingMode");
         if (mode.equals("NATIVE")) {
            return Result.success();
         } else {
            FireMode selected = mode.equals("AUTO") ? FireMode.AUTO : FireMode.SEMI;
            List<FireMode> nativeModes = this.data(ctx.weapon).getFireModeSet();
            if (nativeModes != null) {
               if (!nativeModes.contains(selected)) {
                  selected = nativeModes.contains(FireMode.SEMI) ? FireMode.SEMI : FireMode.AUTO;
               }

               if (nativeModes.contains(selected) && item.getFireMode(ctx.weapon) != selected) {
                  item.setFireMode(ctx.weapon, selected);
                  AttachmentPropertyManager.postChangeEvent(ctx.npc, ctx.weapon);
               }
            }

            return Result.success();
         }
      }
   }

   @Override
   public Double recoil(ItemStack stack) {
      GunRecoil recoil = this.data(stack).getRecoil();
      GunRecoilKeyFrame[] frames = recoil == null ? null : recoil.getPitch();
      double peak = 0.0;
      if (frames != null) {
         for (int i = 0; i < Math.min(64, frames.length); i++) {
            if (frames[i] != null) {
               float[] values = frames[i].getValue();
               if (values != null) {
                  for (int n = 0; n < Math.min(2, values.length); n++) {
                     if (Float.isFinite(values[n])) {
                        peak = Math.max(peak, (double)Math.abs(values[n]));
                     }
                  }
               }
            }
         }
      }

      return peak;
   }

   @Override
   public Result fire(CombatContext ctx) {
      if (!this.matches(ctx.weapon)) {
         TaczCharging.cancel(ctx);
         return Result.fail("NO_COMPATIBLE_WEAPON");
      } else {
         IGun gun = this.gun(ctx.weapon);
         if (ctx.config.bool("ammo.infinite")) {
            int capacity = AttachmentDataUtils.getAmmoCountWithAttachment(ctx.weapon, this.data(ctx.weapon));
            gun.setCurrentAmmoCount(ctx.weapon, Math.max(1, capacity));
            gun.setMaxDummyAmmoAmount(ctx.weapon, 100000);
            gun.setDummyAmmoAmount(ctx.weapon, 100000);
         }

         if (gun.useInventoryAmmo(ctx.weapon) && gun.getDummyAmmoAmount(ctx.weapon) == 0) {
            this.feedReserve(ctx, 1);
         }

         IGunOperator operator = IGunOperator.fromLivingEntity(ctx.npc);
         ShooterDataHolder current = operator.getDataHolder();
         if (current != null && current.currentGunItem != null && current.currentGunItem.get() == ctx.weapon) {
            float pitch = ctx.pitch();
            float yaw = ctx.yaw();
            ChargeData charge = this.data(ctx.weapon).getChargeData(gun.getFireMode(ctx.weapon));
            ShootResult result;
            if (charge == null) {
               TaczCharging.cancel(ctx);
               result = operator.shoot(() -> pitch, () -> yaw);
            } else {
               if (!TaczCharging.valid(charge)) {
                  TaczCharging.cancel(ctx);
                  return Result.fail("CONDITION_NOT_MET");
               }

               Float progress = TaczCharging.ready(ctx, gun.getFireMode(ctx.weapon), charge, operator);
               if (progress == null) {
                  return Result.fail("BUSY");
               }

               ShooterDataHolder holder = operator.getDataHolder();
               long nativeNow = TaczTime.nowMillis();
               if (holder == null || nativeNow < holder.baseTimestamp) {
                  return Result.fail("BUSY");
               }

               result = operator.shoot(() -> pitch, () -> yaw, nativeNow - holder.baseTimestamp, progress);
               if (result == ShootResult.SUCCESS) {
                  TaczCharging.fired(ctx, charge);
               }
            }

            if (result == null) {
               return Result.fail("INTERNAL_ERROR");
            } else {
               return switch (result) {
                  case SUCCESS -> Result.success();
                  case NO_AMMO -> Result.fail("NO_AMMO");
                  case NEED_BOLT -> {
                     operator.bolt();
                     yield Result.fail("BUSY");
                  }
                  case IS_RELOADING, IS_DRAWING, IS_BOLTING, IS_MELEE, IS_SPRINTING -> Result.fail("BUSY");
                  case COOL_DOWN, OVERHEATED, NETWORK_FAIL -> Result.fail("COOLDOWN");
                  case NOT_DRAW -> {
                     operator.draw(() -> ctx.weapon);
                     yield Result.fail("BUSY");
                  }
                  case NOT_GUN, ID_NOT_EXIST -> Result.fail("NO_COMPATIBLE_WEAPON");
                  case FORGE_EVENT_CANCEL -> Result.fail("CONDITION_NOT_MET");
                  default -> Result.fail("INTERNAL_ERROR");
               };
            }
         } else {
            TaczCharging.cancel(ctx);
            operator.draw(() -> ctx.weapon);
            return Result.fail("BUSY");
         }
      }
   }

   @Override
   public Result reload(CombatContext ctx) {
      Views.AmmoState state = this.ammo(ctx);
      if (state == null) {
         return Result.fail("NO_COMPATIBLE_WEAPON");
      } else if (!state.hasMagazine) {
         return Result.fail("UNSUPPORTED");
      } else if (state.magazineAmmo == null || state.magazineCapacity == null) {
         return Result.fail("NO_COMPATIBLE_WEAPON");
      } else if (state.isReloading) {
         return Result.fail("BUSY");
      } else if (state.magazineAmmo >= state.magazineCapacity) {
         return Result.fail("CONDITION_NOT_MET");
      } else {
         IGunOperator operator = IGunOperator.fromLivingEntity(ctx.npc);
         ShooterDataHolder current = operator.getDataHolder();
         if (current != null && current.currentGunItem != null && current.currentGunItem.get() == ctx.weapon) {
            if (!this.freeReload(ctx.weapon)) {
               this.feedReserve(ctx, Math.max(0, state.magazineCapacity - state.magazineAmmo));
               if (this.gun(ctx.weapon).getDummyAmmoAmount(ctx.weapon) <= 0) {
                  return Result.fail("NO_AMMO");
               }
            }

            if (ctx.runtime.reloadTargetTicks > 0) {
               ctx.runtime.adapterState.put("tacz.reloadNativeTicks", this.nativeReloadTicks(ctx));
            }

            TaczCharging.cancel(ctx);
            operator.reload();
            ShooterDataHolder holder = operator.getDataHolder();
            return holder != null && holder.reloadStateType != null && holder.reloadStateType.isReloading() ? Result.success() : Result.fail("BUSY");
         } else {
            operator.draw(() -> ctx.weapon);
            return Result.fail("BUSY");
         }
      }
   }

   private double nativeReloadTicks(CombatContext ctx) {
      GunData nativeData = this.data(ctx.weapon);
      GunReloadData reload = nativeData.getReloadData();
      boolean empty = this.loadedRounds(ctx.weapon) <= 0;
      double seconds = reload != null && reload.getCooldown() != null
         ? (empty ? reload.getCooldown().getEmptyTime() : reload.getCooldown().getTacticalTime())
         : 1.5;
      if (reload != null && reload.getFeed() != null) {
         seconds = Math.max(seconds, empty ? reload.getFeed().getEmptyTime() : reload.getFeed().getTacticalTime());
      }

      Map<String, Object> params = nativeData.getScriptParam();
      if (params != null) {
         double loop = timing(params, "loop");
         double intro = timing(params, empty ? "intro_empty" : "intro");
         double ending = timing(params, "ending");
         if (loop > 0.0 && intro >= 0.0 && ending >= 0.0) {
            int needed = Math.max(0, Math.min(100000, this.rawCapacity(ctx.weapon) - this.gun(ctx.weapon).getCurrentAmmoCount(ctx.weapon)));
            int missing = Math.max(1, this.freeReload(ctx.weapon) ? needed : Math.min(this.gun(ctx.weapon).getDummyAmmoAmount(ctx.weapon), needed));
            double doubleLoop = timing(params, "loop_2");
            seconds = intro + (doubleLoop > 0.0 ? missing / 2 * doubleLoop + missing % 2 * loop : missing * loop) + ending;
            double clip = timing(params, "clip_load");
            if (empty && clip > 0.0) {
               seconds = clip + ending;
            }
         } else {
            int extent = Math.max(0, Math.min(3, AttachmentDataUtils.getMagExtendLevel(ctx.weapon, nativeData)));
            String key = (empty ? "empty" : "reload") + (extent == 0 ? "" : "_xmag_" + extent) + "_cooldown";
            double scripted = timing(params, key);
            if (scripted > 0.0) {
               seconds = scripted;
            }
         }
      }

      return Double.isFinite(seconds) && seconds > 0.0 ? seconds * 20.0 : 30.0;
   }

   private static double timing(Map<String, Object> params, String key) {
      return params.get(key) instanceof Number number && Double.isFinite(number.doubleValue()) && number.doubleValue() >= 0.0 ? number.doubleValue() : -1.0;
   }

   @Override
   public Result aim(CombatContext ctx, boolean aim) {
      IGunOperator.fromLivingEntity(ctx.npc).aim(aim);
      return Result.success();
   }

   @Override
   public Object aimState(CombatContext ctx) {
      return IGunOperator.fromLivingEntity(ctx.npc).getSynIsAiming();
   }

   @Override
   public void tick(CombatContext ctx) {
      TaczCharging.tick(ctx);
   }

   @Override
   public void stopFire(CombatContext ctx) {
      TaczCharging.cancel(ctx);
   }

   @Override
   public void cancel(CombatContext ctx) {
      TaczCharging.cancel(ctx);
      IGunOperator.fromLivingEntity(ctx.npc).cancelReload();
      IGunOperator.fromLivingEntity(ctx.npc).aim(false);
   }
}
