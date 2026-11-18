package bincnpc.gun.mixin.compat;

import bincnpc.gun.client.animation.NpcAnimations;
import com.tacz.guns.client.resource.GunDisplayInstance;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   targets = {"com.tacz.guns.compat.playeranimator.PlayerAnimatorCompat"},
   remap = false
)
public abstract class TaczPoseMixin {
   @Inject(
      method = {"hasPlayerAnimator3rd(Lnet/minecraft/world/entity/LivingEntity;Lcom/tacz/guns/client/resource/GunDisplayInstance;)Z"},
      at = {@At("HEAD")},
      cancellable = true
     ,
      require = 0
   )
   private static void cnpcgun$nativeNpcClip(LivingEntity entity, GunDisplayInstance display, CallbackInfoReturnable<Boolean> cir) {
      if (entity instanceof EntityNPCInterface npc && (NpcAnimations.nativeGun(npc) || NpcAnimations.idleGun(npc))) {
         cir.setReturnValue(true);
      }
   }
}
