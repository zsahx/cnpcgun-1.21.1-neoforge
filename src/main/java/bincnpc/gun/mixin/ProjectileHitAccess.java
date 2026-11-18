package bincnpc.gun.mixin;

import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({Projectile.class})
public interface ProjectileHitAccess {
   @Invoker("onHit")
   void cnpcgun$impact(HitResult var1);
}
