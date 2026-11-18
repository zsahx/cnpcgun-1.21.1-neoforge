package bincnpc.gun.mixin;

import bincnpc.gun.CNPCGun;
import bincnpc.gun.common.persistence.GunPersistence;
import net.minecraft.nbt.CompoundTag;
import noppes.npcs.controllers.ServerCloneController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   value = {ServerCloneController.class},
   remap = false
)
public abstract class ClonePortableMixin {
   @Inject(
      method = {"saveClone"},
      at = {@At("HEAD")},
      cancellable = true
   ,
      require = 0)
   private void cnpcgun$portableClone(int tab, String name, CompoundTag data, CallbackInfo ci) {
      if (!GunPersistence.portable(data)) {
         CNPCGun.LOGGER.warn("CNPC Gun prevented incomplete clone export: tab {}, name {}", tab, name);
         ci.cancel();
      }
   }
}
