package bincnpc.gun.mixin.compat;

import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.compat.tacz.TaczAdapter;
import com.tacz.guns.item.ModernKineticGunScriptAPI;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {ModernKineticGunScriptAPI.class},
   remap = false
)
public abstract class TaczBurstMixin {
   @Shadow
   private LivingEntity shooter;
   @Shadow
   private ItemStack itemStack;

   @Inject(
      method = {"modifyProperty(Ljava/lang/String;Ljava/lang/Class;Ljava/lang/Object;)Ljava/lang/Object;"},
      at = {@At("RETURN")},
      cancellable = true
     ,
      require = 0
   )
   private void cnpcgun$singleTrigger(String id, Class<?> type, Object value, CallbackInfoReturnable<Object> cir) {
      if ("burst_count".equals(id) && type == Integer.class) {
         CombatContext ctx = TaczAdapter.fireContext(this.shooter, this.itemStack);
         if (ctx != null && !ctx.config.text("gun.firingMode").equals("NATIVE")) {
            cir.setReturnValue(1);
         }
      }
   }
}
