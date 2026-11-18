package bincnpc.gun.mixin;

import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.common.combat.CombatMovement;
import bincnpc.gun.common.combat.CombatService;
import bincnpc.gun.common.npc.NpcAccess;
import bincnpc.gun.common.npc.NpcData;
import bincnpc.gun.common.persistence.GunPersistence;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {EntityNPCInterface.class},
   remap = false
)
public abstract class NpcDataMixin implements NpcAccess {
   @Unique
   private NpcData cnpcgun$data;

   @Override
   public NpcData cnpcgun$data() {
      if (this.cnpcgun$data == null) {
         this.cnpcgun$data = new NpcData();
      }

      return this.cnpcgun$data;
   }

   @Inject(
      method = {"readAdditionalSaveData", "readAdditionalSaveData"},
      at = {@At(
         value = "INVOKE",
         target = "Lnoppes/npcs/entity/data/DataDisplay;readToNBT(Lnet/minecraft/nbt/CompoundTag;)V"
      )},
      require = 0
   )
   private void cnpcgun$load(CompoundTag tag, CallbackInfo ci) {
      GunPersistence.load((EntityNPCInterface)(Object)this, tag);
   }

   @Inject(
      method = {"addAdditionalSaveData", "addAdditionalSaveData"},
      at = {@At("TAIL")},
      require = 0
   )
   private void cnpcgun$save(CompoundTag tag, CallbackInfo ci) {
      GunPersistence.save((EntityNPCInterface)(Object)this, tag);
   }

   @Inject(
      method = {"writeSpawnData()Lnet/minecraft/nbt/CompoundTag;"},
      at = {@At("RETURN")}
   ,
      require = 0)
   private void cnpcgun$summary(CallbackInfoReturnable<CompoundTag> cir) {
      ((CompoundTag)cir.getReturnValue()).putInt("CNPCGunActiveSlot", this.cnpcgun$data().armory.active);
   }

   @Inject(
      method = {"reset"},
      at = {@At("TAIL")}
   ,
      require = 0)
   private void cnpcgun$reset(CallbackInfo ci) {
      EntityNPCInterface npc = (EntityNPCInterface)(Object)this;
      if (CombatService.serverThread(npc)) {
         this.cnpcgun$data().throwing.reset();
         this.cnpcgun$data().throwItems.reset();
         this.cnpcgun$data().runtime.restoreSupplies = true;
         this.cnpcgun$data().runtime.editorUntil = 0L;
         this.cnpcgun$data().runtime.editorPaused = false;
      }
   }

   @Inject(
      method = {"aiStep", "aiStep"},
      at = {@At("TAIL")},
      require = 0
   )
   private void cnpcgun$faceAfterAi(CallbackInfo ci) {
      EntityNPCInterface npc = (EntityNPCInterface)(Object)this;
      if (CombatService.serverThread(npc)) {
         this.cnpcgun$data().throwing.afterAi(new CombatContext(npc));
      }

      CombatMovement.face(npc);
   }

   @Inject(
      method = {"die", "die"},
      at = {@At("TAIL")},
      require = 0
   )
   private void cnpcgun$kill(DamageSource source, CallbackInfo ci) {
      EntityNPCInterface npc = (EntityNPCInterface)(Object)this;
      if (!npc.level().isClientSide && (npc.isKilled() || !npc.isAlive())) {
         this.cnpcgun$data().throwing.cancel();
         this.cnpcgun$data().throwItems.died();
         this.cnpcgun$data().runtime.restoreSupplies = true;
         if (source.getEntity() instanceof EntityNPCInterface owner) {
            CombatService.rewardKill(owner, npc);
         }
      }
   }
}
