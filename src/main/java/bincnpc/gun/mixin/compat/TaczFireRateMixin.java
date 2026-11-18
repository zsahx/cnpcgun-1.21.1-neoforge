package bincnpc.gun.mixin.compat;

import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.common.combat.CombatService;
import bincnpc.gun.compat.tacz.TaczAdapter;
import bincnpc.gun.gun.controller.FireTiming;
import com.tacz.guns.entity.shooter.LivingEntityShoot;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {LivingEntityShoot.class},
   remap = false
)
public abstract class TaczFireRateMixin {
   @Shadow
   @Final
   private LivingEntity shooter;
   @Shadow
   @Final
   private ShooterDataHolder data;

   @Inject(
      method = {"getShootCoolDown(J)J"},
      at = {@At("HEAD")},
      cancellable = true
     ,
      require = 0
   )
   private void cnpcgun$configuredShotCooldown(long timestamp, CallbackInfoReturnable<Long> cir) {
      if (this.shooter instanceof EntityNPCInterface npc && CombatService.serverThread(npc) && CombatService.validNpc(npc) && this.data.currentGunItem != null) {
         ItemStack current = (ItemStack)this.data.currentGunItem.get();
         CombatContext ctx = TaczAdapter.rateContext(this.shooter, current);
         if (ctx != null) {
            cir.setReturnValue(FireTiming.remainingMillis(ctx.runtime.nextShot, ctx.now));
         }
      }
   }
}
