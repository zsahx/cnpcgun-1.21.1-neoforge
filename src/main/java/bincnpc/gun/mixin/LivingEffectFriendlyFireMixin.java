package bincnpc.gun.mixin;

import bincnpc.gun.common.combat.FriendlyFire;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({LivingEntity.class})
public abstract class LivingEffectFriendlyFireMixin {
   @Inject(
      method = {"addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z"},
      at = {@At("HEAD")},
      cancellable = true
   ,
      require = 0)
   private void cnpcgun$protectFromHarmfulEffect(MobEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> cir) {
      if (effect != null && !effect.getEffect().value().isBeneficial() && FriendlyFire.isProtected(source, (Entity)(Object)this)) {
         cir.setReturnValue(false);
      }
   }
}
