package bincnpc.gun.common.combat;

import bincnpc.gun.common.config.NpcConfig;
import bincnpc.gun.common.npc.NpcData;
import bincnpc.gun.common.npc.RuntimeState;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.api.wrapper.ItemStackWrapper;
import noppes.npcs.entity.EntityNPCInterface;

public final class CombatContext {
   public final EntityNPCInterface npc;
   public final NpcData data;
   public final RuntimeState runtime;
   public final NpcConfig config;
   public final ItemStack weapon;
   public final LivingEntity target;
   public final long now;
   public final double distance;

   public CombatContext(EntityNPCInterface npc) {
      this(npc, npc == null ? null : npc.getTarget());
   }

   public CombatContext(EntityNPCInterface npc, LivingEntity target) {
      this(npc, target, mainHand(npc));
   }

   private static ItemStack mainHand(EntityNPCInterface npc) {
      if (npc == null) {
         throw new IllegalArgumentException("NPC is required");
      } else {
         return npc.inventory == null ? ItemStack.EMPTY : ItemStackWrapper.MCItem(npc.inventory.getRightHand());
      }
   }

   private CombatContext(EntityNPCInterface npc, LivingEntity target, ItemStack weapon) {
      this.npc = npc;
      this.data = CombatService.data(npc);
      this.runtime = this.data.runtime;
      this.config = this.data.config;
      this.weapon = weapon == null ? ItemStack.EMPTY : weapon;
      this.target = target;
      this.now = npc.level().getGameTime();
      this.distance = target == null ? Double.POSITIVE_INFINITY : npc.distanceTo(target);
   }

   public CombatContext withWeapon(ItemStack stack) {
      return new CombatContext(this.npc, this.target, stack);
   }

   public boolean validTarget() {
      return CombatService.validTarget(this.npc, this.target);
   }

   public List<LivingEntity> targets() {
      if (this.now >= this.runtime.nextTargetScan) {
         this.runtime.nearbyTargets.clear();
         double range = Math.min(this.config.number("combat.attackRange"), 32.0);

         for (LivingEntity e : this.npc.level().getEntitiesOfClass(LivingEntity.class, this.npc.getBoundingBox().inflate(range), ex -> CombatService.hostile(this.npc, ex))) {
            if (this.runtime.nearbyTargets.size() >= 16) {
               break;
            }

            this.runtime.nearbyTargets.add(e);
         }

         this.runtime.nextTargetScan = this.now + this.config.integer("combat.decisionInterval");
      }

      return this.runtime.nearbyTargets.stream().filter(ex -> CombatService.validTarget(this.npc, ex)).toList();
   }

   public float yaw() {
      return this.target == null
         ? this.npc.getYRot()
         : (float)(Math.toDegrees(Math.atan2(this.target.getZ() - this.npc.getZ(), this.target.getX() - this.npc.getX())) - 90.0)
            + this.runtime.shotYawOffset;
   }

   public float pitch() {
      if (this.target == null) {
         return this.npc.getXRot();
      } else {
         double dx = this.target.getX() - this.npc.getX();
         double dz = this.target.getZ() - this.npc.getZ();
         return Mth.clamp(
            (float)(-Math.toDegrees(Math.atan2(this.target.getEyeY() - this.npc.getEyeY(), Math.sqrt(dx * dx + dz * dz))) + this.runtime.recoil.pitch())
               + this.runtime.shotPitchOffset,
            -90.0F,
            90.0F
         );
      }
   }

   public boolean isPrefiring() {
      String mode = this.config.text("gun.prefire");
      return (mode.equals("TRACK") || mode.equals("SWEEP"))
            && this.validTarget()
            && this.target == this.npc.getTarget()
            && !(this.npc.getEyePosition().distanceToSqr(this.target.getEyePosition()) > 16384.0)
         ? Prefire.active(mode, true, this.npc.getSensing().hasLineOfSight(this.target))
         : false;
   }

   public boolean canFireWithoutSight() {
      return this.config.bool("gun.blindFire") || this.isPrefiring();
   }

   public Vec3 shotDirection() {
      return Vec3.directionFromRotation(this.pitch(), this.yaw());
   }

   public double spreadMultiplier() {
      double spread = this.config.number("gun.spreadMultiplier") * (100.0 - this.config.number("gun.accuracy")) / 50.0;
      if (this.npc.getDeltaMovement().horizontalDistanceSqr() > 0.005) {
         spread *= this.config.number("gun.moveSpread");
      }

      if (!this.npc.onGround()) {
         spread *= this.config.number("gun.jumpSpread");
      }

      if (this.runtime.aiming) {
         spread *= this.config.number("gun.aimSpread");
      }

      if (this.isPrefiring() && this.config.text("gun.prefire").equals("SWEEP")) {
         spread *= 2.0;
      }

      return spread * Math.pow(this.config.number("gun.continuousSpread"), Math.min(this.runtime.shotStreak, 10));
   }
}
