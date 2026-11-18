package bincnpc.gun.mixin;

import bincnpc.gun.common.combat.FriendlyFire;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Projectile.class})
public abstract class ProjectileFriendlyFireMixin {
   @Inject(
      method = {"canHitEntity(Lnet/minecraft/world/entity/Entity;)Z"},
      at = {@At("HEAD")},
      cancellable = true
   ,
      require = 0)
   private void cnpcgun$skipProtectedTarget(Entity victim, CallbackInfoReturnable<Boolean> cir) {
      if (FriendlyFire.isProtected((Projectile)(Object)this, victim)) {
         cir.setReturnValue(false);
      }
   }
}
