package bincnpc.gun.mixin.compat;

import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.common.combat.CombatService;
import bincnpc.gun.compat.tacz.TaczAdapter;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.item.ModernKineticGunItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(
   value = {ModernKineticGunItem.class},
   remap = false
)
public abstract class TaczSpreadMixin {
   @ModifyVariable(
      method = {"doBulletSpread(Lcom/tacz/guns/entity/shooter/ShooterDataHolder;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/projectile/Projectile;IFFFF)V"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 1,
      require = 0
   )
   private float cnpcgun$configuredSpread(
      float spread,
      ShooterDataHolder holder,
      ItemStack stack,
      LivingEntity shooter,
      Projectile projectile,
      int count,
      float speed,
      float originalSpread,
      float pitch,
      float yaw
   ) {
      if (shooter instanceof EntityNPCInterface npc
         && CombatService.serverThread(npc)
         && CombatService.validNpc(npc)
         && holder != null
         && holder.currentGunItem != null) {
         CombatContext ctx = new CombatContext(npc);
         if (ctx.runtime.adapter instanceof TaczAdapter
            && !ctx.runtime.editorPaused
            && ctx.config.bool("combat.enabled")
            && ctx.config.bool("gun.enabled")
            && stack == ctx.weapon
            && stack == npc.getMainHandItem()
            && holder.currentGunItem.get() == stack) {
            double configured = Math.max(0.0F, spread) * ctx.spreadMultiplier();
            return Double.isFinite(configured) ? (float)Math.min(ctx.config.number("gun.maxSpread"), configured) : spread;
         } else {
            return spread;
         }
      } else {
         return spread;
      }
   }
}
