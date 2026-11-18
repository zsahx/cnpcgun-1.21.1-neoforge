package bincnpc.gun.throwing;

import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

public interface ThrowAdapter {
   default List<ItemStack> catalogue() {
      return List.of();
   }

   boolean matches(ItemStack var1);

   Projectile create(ServerLevel var1, EntityNPCInterface var2, ItemStack var3);

   ThrowAdapter.Profile profile(Projectile var1);

   void detonate(Projectile var1);

   default void straight(Projectile projectile) {
      projectile.setNoGravity(true);
   }

   default void launch(Projectile projectile, Vec3 velocity) {
      projectile.shoot(velocity.x, velocity.y, velocity.z, (float)velocity.length(), 0.0F);
   }

   default void guide(Projectile projectile, Vec3 velocity) {
      projectile.setDeltaMovement(velocity);
   }

   default void sync(Projectile projectile) {
      projectile.hasImpulse = true;
      projectile.hurtMarked = true;
   }

   default void restore(Projectile projectile, ThrowAdapter.Profile profile) {
      projectile.setNoGravity(false);
   }

   default boolean settleAtLanding(Projectile projectile) {
      return false;
   }

   public record Profile(double speed, double gravity, double drag, double radius, double blastRadius, int life, boolean customGravity) {
      public Profile(double speed, double gravity, double drag, double radius, double blastRadius, int life, boolean customGravity) {
         if (Double.isFinite(speed)
            && !(speed <= 0.0)
            && !(speed > 40.0)
            && Double.isFinite(gravity)
            && !(gravity < 0.0)
            && !(gravity > 2.0)
            && Double.isFinite(drag)
            && !(drag <= 0.0)
            && !(drag > 1.0)
            && Double.isFinite(radius)
            && !(radius < 0.0)
            && !(radius > 2.0)
            && Double.isFinite(blastRadius)
            && !(blastRadius < 0.0)
            && !(blastRadius > 32.0)
            && life >= 1
            && life <= 1200) {
            this.speed = speed;
            this.gravity = gravity;
            this.drag = drag;
            this.radius = radius;
            this.blastRadius = blastRadius;
            this.life = life;
            this.customGravity = customGravity;
         } else {
            throw new IllegalArgumentException("throw profile");
         }
      }
   }
}
