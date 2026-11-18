package bincnpc.gun.mixin;

import bincnpc.gun.common.combat.FriendlyFire;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {EntityNPCInterface.class},
   remap = false
)
public abstract class NpcFriendlyFireMixin {
   @Inject(
      method = {"hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void cnpcgun$protectBeforeRetaliation(DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
      if (FriendlyFire.blocksDamage(source, (Entity)(Object)this)) {
         cir.setReturnValue(false);
      }
   }
}
