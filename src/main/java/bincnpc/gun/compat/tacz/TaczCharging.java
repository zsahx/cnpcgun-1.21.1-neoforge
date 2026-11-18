package bincnpc.gun.compat.tacz;

import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.common.combat.CombatService;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.pojo.data.gun.ChargeData;
import com.tacz.guns.resource.pojo.data.gun.ChargeType;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;

final class TaczCharging {
   private static final String KEY = "tacz.charge";

   private TaczCharging() {
   }

   static void cancel(CombatContext ctx) {
      ctx.runtime.adapterState.remove("tacz.charge");
   }

   static boolean valid(ChargeData data) {
      return data != null
         && data.getChargeType() != null
         && Float.isFinite(data.getMaxCharge())
         && data.getMaxCharge() > 0.0F
         && Float.isFinite(data.getIncreasePerTick())
         && data.getIncreasePerTick() > 0.0F
         && Float.isFinite(data.getDecreasePerTick())
         && data.getDecreasePerTick() >= 0.0F
         && Float.isFinite(data.getDecreaseOnFire())
         && data.getDecreaseOnFire() >= 0.0F
         && Float.isFinite(data.getFireThreshold())
         && data.getFireThreshold() >= 0.0F
         && data.getFireThreshold() <= data.getMaxCharge();
   }

   private static boolean allowed(CombatContext ctx) {
      return CombatService.serverThread(ctx.npc)
         && CombatService.validNpc(ctx.npc)
         && !ctx.runtime.editorPaused
         && ctx.config.bool("combat.enabled")
         && ctx.config.bool("gun.enabled")
         && !ctx.runtime.fireStopped
         && ctx.runtime.command.equals("AUTO")
         && ctx.validTarget()
         && ctx.weapon == ctx.npc.getMainHandItem()
         && !ctx.runtime.reloading
         && ctx.distance >= ctx.config.number("gun.minRange")
         && ctx.distance <= Math.min(ctx.config.number("gun.range"), ctx.config.number("combat.attackRange"))
         && (ctx.npc.getSensing().hasLineOfSight(ctx.target) || ctx.canFireWithoutSight());
   }

   private static boolean matches(TaczCharging.State state, CombatContext ctx, FireMode mode, ChargeData data) {
      return state.weapon == ctx.weapon
         && state.target.equals(ctx.target.getUUID())
         && state.revision == ctx.config.revision
         && state.weaponEpoch == ctx.runtime.weaponEpoch
         && state.mode == mode
         && state.data == data;
   }

   private static boolean nativeLocked(IGunOperator operator) {
      ReloadState reload = operator.getSynReloadState();
      return reload != null && reload.getStateType() != null && reload.getStateType().isReloading()
         || operator.getSynDrawCoolDown() > 0L
         || operator.getSynIsBolting()
         || operator.getSynMeleeCoolDown() > 0L
         || operator.getSynSprintTime() > 0.0F;
   }

   static void tick(CombatContext ctx) {
      if (CombatService.serverThread(ctx.npc) && ctx.runtime.adapterState.get("tacz.charge") instanceof TaczCharging.State state) {
         if (allowed(ctx) && ctx.weapon.getItem() instanceof IGun gun) {
            GunData nativeData = TimelessAPI.getCommonGunIndex(gun.getGunId(ctx.weapon)).map(index -> index.getGunData()).orElse(null);
            ChargeData data = nativeData == null ? null : nativeData.getChargeData(gun.getFireMode(ctx.weapon));
            IGunOperator operator = IGunOperator.fromLivingEntity(ctx.npc);
            if (!valid(data)
               || !matches(state, ctx, gun.getFireMode(ctx.weapon), data)
               || nativeLocked(operator)
               || ctx.now < state.tick
               || ctx.now - state.tick > 1L) {
               cancel(ctx);
            } else if (ctx.data.throwing.blocksAttack(ctx.now)) {
               if (ctx.data.throwing.pausesCharge()) {
                  state.tick = ctx.now;
               } else {
                  cancel(ctx);
               }
            } else {
               advance(state, ctx.now, operator);
            }
         } else {
            cancel(ctx);
         }
      }
   }

   private static void advance(TaczCharging.State state, long now, IGunOperator operator) {
      if (now != state.tick) {
         if (!state.data.isChargeDuringCooldown() && operator.getSynShootCoolDown() >= 50L) {
            state.progress = Math.max(0.0F, state.progress - state.data.getDecreasePerTick());
         } else {
            state.progress = (float)Math.min((double)state.data.getMaxCharge(), (double)state.progress + state.data.getIncreasePerTick());
         }

         state.tick = now;
      }
   }

   static Float ready(CombatContext ctx, FireMode mode, ChargeData data, IGunOperator operator) {
      if (!allowed(ctx) || nativeLocked(operator)) {
         cancel(ctx);
         return null;
      } else if (ctx.data.throwing.blocksAttack(ctx.now)) {
         if (!ctx.data.throwing.pausesCharge()) {
            cancel(ctx);
         }

         return null;
      } else {
         TaczCharging.State state = ctx.runtime.adapterState.get("tacz.charge") instanceof TaczCharging.State previous ? previous : null;
         if (state == null || !matches(state, ctx, mode, data) || ctx.now < state.tick || ctx.now - state.tick > 1L) {
            state = new TaczCharging.State(ctx, mode, data);
            ctx.runtime.adapterState.put("tacz.charge", state);
         }

         advance(state, ctx.now, operator);
         return state.progress >= data.getMaxCharge() ? state.progress : null;
      }
   }

   static void fired(CombatContext ctx, ChargeData data) {
      if (ctx.runtime.adapterState.get("tacz.charge") instanceof TaczCharging.State state && state.weapon == ctx.weapon && state.data == data) {
         state.progress = data.getChargeType() == ChargeType.DELAY ? 0.0F : Math.max(0.0F, state.progress - data.getDecreaseOnFire());
      }
   }

   private static final class State {
      final ItemStack weapon;
      final UUID target;
      final long revision;
      final long weaponEpoch;
      final FireMode mode;
      final ChargeData data;
      long tick;
      float progress;

      State(CombatContext ctx, FireMode mode, ChargeData data) {
         this.weapon = ctx.weapon;
         this.target = ctx.target.getUUID();
         this.revision = ctx.config.revision;
         this.weaponEpoch = ctx.runtime.weaponEpoch;
         this.tick = ctx.now;
         this.mode = mode;
         this.data = data;
      }
   }
}
