package bincnpc.gun.mixin;

import bincnpc.gun.common.script.ScriptApi;
import java.util.HashMap;
import noppes.npcs.controllers.ScriptContainer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   value = {ScriptContainer.class},
   remap = false
)
public abstract class ScriptGlobalsMixin {
   @Shadow
   @Final
   public static HashMap<String, Object> Data;

   @Inject(
      method = {"<clinit>"},
      at = {@At("TAIL")}
   ,
      require = 0)
   private static void cnpcgun$register(CallbackInfo ci) {
      Data.put("CNPCGun", new ScriptApi());
   }
}
