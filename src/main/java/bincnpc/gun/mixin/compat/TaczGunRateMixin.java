package bincnpc.gun.mixin.compat;

import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.compat.tacz.TaczAdapter;
import bincnpc.gun.gun.controller.GunController;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {GunData.class},
   remap = false
)
public abstract class TaczGunRateMixin {
   @Inject(
      method = {"getShootInterval(Lnet/minecraft/world/entity/LivingEntity;Lcom/tacz/guns/api/item/gun/FireMode;Lnet/minecraft/world/item/ItemStack;)J"},
      at = {@At("HEAD")},
      cancellable = true
     ,
      require = 0
   )
   private void cnpcgun$effectiveInterval(LivingEntity shooter, FireMode mode, ItemStack stack, CallbackInfoReturnable<Long> cir) {
      CombatContext ctx = TaczAdapter.rateContext(shooter, stack);
      if (ctx != null
         && stack.getItem() instanceof IGun gun
         && TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).map(index -> index.getGunData()).orElse(null) == (GunData)(Object)this) {
         cir.setReturnValue(GunController.configuredNativeInterval(ctx) * 50L);
      }
   }
}
