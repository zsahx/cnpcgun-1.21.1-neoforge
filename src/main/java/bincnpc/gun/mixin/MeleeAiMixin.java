package bincnpc.gun.mixin;

import bincnpc.gun.common.combat.CombatService;
import noppes.npcs.ai.EntityAIAttackTarget;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {EntityAIAttackTarget.class},
   remap = false
)
public abstract class MeleeAiMixin {
   @Shadow
   private EntityNPCInterface npc;

   @Inject(
      method = {"canUse", "canUse", "canContinueToUse", "canContinueToUse"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void cnpcgun$ownsAttack(CallbackInfoReturnable<Boolean> cir) {
      if (CombatService.blocksNative(this.npc)) {
         cir.setReturnValue(false);
      }
   }

   @Inject(
      method = {"tick", "tick"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void cnpcgun$skip(CallbackInfo ci) {
      if (CombatService.blocksNative(this.npc)) {
         ci.cancel();
      }
   }
}
