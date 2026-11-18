package bincnpc.gun.mixin;

import bincnpc.gun.client.animation.BladePreviewEffects;
import bincnpc.gun.client.animation.NpcAnimations;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LivingEntityRenderer.class})
public abstract class NpcAnimationMixin {
   @Shadow
   protected EntityModel<LivingEntity> model;
   @Unique
   private boolean cnpcgun$rootPushed;

   @Inject(
      method = {"render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"},
      at = {@At("HEAD")}
     ,
      require = 0
   )
   private void cnpcgun$restoreBefore(LivingEntity living, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
      NpcAnimations.restore(this.model);
      this.cnpcgun$rootPushed = false;
   }

   @Inject(
      method = {"render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V",
         shift = Shift.AFTER
      )}
     ,
      require = 0
   )
   private void cnpcgun$pose(LivingEntity living, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
      if (living instanceof EntityNPCInterface npc && this.model instanceof HumanoidModel<?> humanoid) {
         NpcAnimations.apply(npc, humanoid, partialTick);
         if (NpcAnimations.nativeRoot(npc)) {
            pose.pushPose();
            this.cnpcgun$rootPushed = true;
            NpcAnimations.root(npc, pose, partialTick);
         }
      }
   }

   @Inject(
      method = {"render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V",
         ordinal = 0
      )}
     ,
      require = 0
   )
   private void cnpcgun$popRoot(LivingEntity living, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
      if (living instanceof EntityNPCInterface npc && ModList.get().isLoaded("slashblade")) {
         BladePreviewEffects.render(npc, pose, buffers, light, partialTick);
      }

      if (this.cnpcgun$rootPushed) {
         pose.popPose();
         this.cnpcgun$rootPushed = false;
      }
   }

   @Inject(
      method = {"render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"},
      at = {@At("RETURN")}
     ,
      require = 0
   )
   private void cnpcgun$restoreAfter(LivingEntity living, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
      NpcAnimations.restore(this.model);
   }
}
