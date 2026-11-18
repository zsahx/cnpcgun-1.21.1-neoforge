package bincnpc.gun.mixin.compat;

import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.common.combat.CombatService;
import bincnpc.gun.compat.tacz.TaczAdapter;
import bincnpc.gun.compat.tacz.TaczTime;
import bincnpc.gun.gun.controller.ReloadTiming;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.entity.shooter.LivingEntityReload;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {LivingEntityReload.class},
   remap = false
)
public abstract class TaczReloadMixin {
   @Shadow
   @Final
   private LivingEntity shooter;
   @Shadow
   @Final
   private ShooterDataHolder data;
   @Unique
   private ReloadTiming.Clock cnpcgun$clock;
   @Unique
   private ItemStack cnpcgun$stack;
   @Unique
   private long cnpcgun$weaponEpoch;
   @Unique
   private double cnpcgun$speed;

   @Inject(
      method = {"reload()V"},
      at = {@At("RETURN")}
     ,
      require = 0
   )
   private void cnpcgun$beginReload(CallbackInfo ci) {
      this.cnpcgun$clock = null;
      this.cnpcgun$stack = null;
      if (this.shooter instanceof EntityNPCInterface npc
         && CombatService.serverThread(npc)
         && CombatService.validNpc(npc)
         && this.data.reloadTimestamp != -1L
         && this.data.currentGunItem != null) {
         CombatContext ctx = new CombatContext(npc);
         if (ctx.runtime.adapter instanceof TaczAdapter && ctx.runtime.reloadTargetTicks > 0 && !ctx.runtime.editorPaused) {
            if (ctx.runtime.adapterState.get("tacz.reloadNativeTicks") instanceof Number number) {
               this.cnpcgun$stack = (ItemStack)this.data.currentGunItem.get();
               if (this.cnpcgun$stack != null && this.cnpcgun$stack == ctx.weapon) {
                  this.cnpcgun$weaponEpoch = ctx.runtime.weaponEpoch;
                  this.cnpcgun$speed = ReloadTiming.speed(number.doubleValue(), ctx.runtime.reloadTargetTicks);
                  long nativeNow = TaczTime.nowMillis();
                  if (nativeNow >= 0L) {
                     this.cnpcgun$clock = new ReloadTiming.Clock(ctx.now, nativeNow);
                  }
               } else {
                  this.cnpcgun$stack = null;
               }
            }
         }
      }
   }

   @Inject(
      method = {"tickReloadState()Lcom/tacz/guns/api/entity/ReloadState;"},
      at = {@At("HEAD")}
     ,
      require = 0
   )
   private void cnpcgun$advanceReload(CallbackInfoReturnable<ReloadState> cir) {
      if (this.cnpcgun$clock != null) {
         if (this.shooter instanceof EntityNPCInterface npc
            && CombatService.serverThread(npc)
            && CombatService.validNpc(npc)
            && this.data.reloadTimestamp != -1L
            && this.data.currentGunItem != null
            && this.data.currentGunItem.get() == this.cnpcgun$stack) {
            CombatContext ctx = new CombatContext(npc);
            if (ctx.runtime.adapter instanceof TaczAdapter && ctx.runtime.weaponEpoch == this.cnpcgun$weaponEpoch && !ctx.runtime.editorPaused) {
               long nativeNow = TaczTime.nowMillis();
               if (nativeNow < 0L) {
                  this.cnpcgun$clock = null;
                  this.cnpcgun$stack = null;
               } else {
                  this.data.reloadTimestamp = this.cnpcgun$clock.adjust(this.data.reloadTimestamp, ctx.now, nativeNow, this.cnpcgun$speed);
               }
            } else {
               this.cnpcgun$clock = null;
               this.cnpcgun$stack = null;
            }
         } else {
            this.cnpcgun$clock = null;
            this.cnpcgun$stack = null;
         }
      }
   }
}
