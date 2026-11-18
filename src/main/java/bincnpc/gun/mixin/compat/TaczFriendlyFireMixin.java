package bincnpc.gun.mixin.compat;

import bincnpc.gun.common.combat.FriendlyFire;
import com.tacz.guns.entity.EntityKineticBullet.EntityResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   targets = {"com.tacz.guns.util.EntityUtil"},
   remap = false
)
public abstract class TaczFriendlyFireMixin {
   @Inject(
      method = {"getHitResult(Lnet/minecraft/world/entity/projectile/Projectile;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Lcom/tacz/guns/entity/EntityKineticBullet$EntityResult;"},
      at = {@At("HEAD")},
      cancellable = true
     ,
      require = 0
   )
   private static void cnpcgun$skipProtectedHit(Projectile bullet, Entity victim, Vec3 start, Vec3 end, CallbackInfoReturnable<EntityResult> cir) {
      if (FriendlyFire.isProtected(bullet, victim)) {
         cir.setReturnValue(null);
      }
   }
}
