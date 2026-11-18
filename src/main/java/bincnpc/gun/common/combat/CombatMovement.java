package bincnpc.gun.common.combat;

import bincnpc.gun.common.npc.NpcAccess;
import bincnpc.gun.common.npc.NpcData;
import bincnpc.gun.compat.pathfinding.PathfindingBridge;
import bincnpc.gun.gun.adapter.GunAdapter;
import bincnpc.gun.slashblade.adapter.BladeAdapter;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import noppes.npcs.entity.EntityNPCInterface;

public final class CombatMovement {
   private CombatMovement() {
   }

   private static boolean owns(CombatContext ctx) {
      return ctx.runtime.adapter instanceof GunAdapter
         || ctx.runtime.adapter instanceof BladeAdapter
         || ctx.runtime.movement.meleeFallback
         || ctx.data.throwing.movementLocked();
   }

   public static boolean enhanced(EntityNPCInterface npc) {
      if (CombatService.serverThread(npc) && npc instanceof NpcAccess) {
         NpcData state = CombatService.data(npc);
         return state.config.bool("combat.pathfinding")
            && state.config.bool("combat.enabled")
            && !state.runtime.editorPaused
            && CombatService.validTarget(npc, npc.getTarget())
            && (
               state.runtime.adapter instanceof GunAdapter
                  || state.runtime.adapter instanceof BladeAdapter
                  || state.runtime.movement.meleeFallback
                  || state.throwing.movementLocked()
            );
      } else {
         return false;
      }
   }

   public static void prepare(CombatContext ctx) {
      if (ModList.get().isLoaded("npcpathfindinglegacy")) {
         PathfindingBridge.prepare(ctx, enhanced(ctx.npc));
      }
   }

   public static void release(CombatContext ctx) {
      if (ModList.get().isLoaded("npcpathfindinglegacy")) {
         PathfindingBridge.prepare(ctx, false);
      }
   }

   public static double meleeReach(CombatContext ctx) {
      return Mth.clamp(
         Math.max(ctx.config.number("weapon.meleeDistance"), (double)ctx.npc.stats.melee.getRange()),
         0.5,
         Math.min(32.0, ctx.config.number("combat.attackRange"))
      );
   }

   public static void chase(CombatContext ctx) {
      if (!ctx.data.throwing.movementLocked() && ctx.validTarget()) {
         ctx.npc.getNavigation().moveTo(ctx.target, 1.15);
      }
   }

   public static void tick(CombatContext ctx) {
      if (owns(ctx) && ctx.validTarget() && ctx.runtime.command.equals("AUTO") && !ctx.data.throwing.movementLocked()) {
         MovementState move = ctx.runtime.movement;
         boolean gun = ctx.runtime.adapter instanceof GunAdapter;
         if (gun && !ctx.runtime.reloading && !CombatService.currentGunUsable(ctx)) {
            if (move.pursueSlot >= 0) {
               if (ctx.now >= move.nextMove) {
                  chase(ctx);
                  move.nextMove = ctx.now + 6L;
               }
            } else {
               ctx.npc.getNavigation().stop();
            }
         } else {
            double preferred = gun ? Math.max(8.0, Math.min(18.0, ctx.config.number("gun.range") * 0.6)) : Math.max(1.5, meleeReach(ctx) * 0.65);
            if (move.pursueSlot < 0
               && !(ctx.distance > (gun ? Math.min(ctx.config.number("gun.range"), ctx.config.number("combat.attackRange")) : meleeReach(ctx)))) {
               if (ctx.config.bool("combat.movement")
                  && (!gun || ctx.config.bool("gun.moveFire"))
                  && ctx.now >= ctx.runtime.maid.escapeUntil
                  && ctx.now >= move.nextMove
                  && ctx.npc.onGround()) {
                  move.nextMove = ctx.now + 10L;
                  if ((ctx.now / 40L + ctx.npc.getId()) % 3L == 0L) {
                     move.side = -move.side;
                  }

                  Vec3 away = ctx.npc.position().subtract(ctx.target.position());
                  double length = away.horizontalDistance();
                  if (length < 0.01) {
                     away = new Vec3(1.0, 0.0, 0.0);
                  } else {
                     away = new Vec3(away.x / length, 0.0, away.z / length);
                  }

                  Vec3 side = new Vec3(-away.z * move.side, 0.0, away.x * move.side);
                  double radial = gun ? Mth.clamp((preferred - ctx.distance) * 0.22, -1.5, 2.0) : Mth.clamp((preferred - ctx.distance) * 0.2, -0.8, 0.8);
                  Vec3 destination = ctx.npc.position().add(side.scale(gun ? 2.5 : 1.3)).add(away.scale(radial));
                  if (!ctx.npc.getNavigation().moveTo(destination.x, destination.y, destination.z, gun ? 1.05 : 1.1)) {
                     move.side = -move.side;
                     chase(ctx);
                  }
               }
            } else {
               if (ctx.now >= move.nextMove) {
                  chase(ctx);
                  move.nextMove = ctx.now + 6L;
               }
            }
         }
      }
   }

   public static void melee(CombatContext ctx) {
      if (ctx.runtime.movement.meleeFallback
         && ctx.validTarget()
         && ctx.runtime.command.equals("AUTO")
         && !ctx.runtime.scheduler.busy(ctx.now)
         && !ctx.data.throwing.blocksAttack(ctx.now)) {
         if (ctx.distance > meleeReach(ctx)) {
            chase(ctx);
         } else if (ctx.now >= ctx.runtime.movement.nextMelee && ctx.npc.getSensing().hasLineOfSight(ctx.target)) {
            ctx.runtime.movement.nextMelee = ctx.now + Math.max(4, ctx.npc.stats.melee.getDelay());
            face(ctx.npc);
            ctx.npc.swing(InteractionHand.MAIN_HAND);
            ctx.npc.doHurtTarget(ctx.target);
            CombatService.setState(ctx, "ATTACKING");
         }
      }
   }

   public static void face(EntityNPCInterface npc) {
      if (CombatService.serverThread(npc) && npc instanceof NpcAccess) {
         CombatContext ctx = new CombatContext(npc);
         if (owns(ctx) && ctx.validTarget() && ctx.config.bool("combat.enabled") && !ctx.runtime.editorPaused && ctx.runtime.command.equals("AUTO")) {
            float yaw = ctx.yaw();
            float pitch = ctx.pitch();
            npc.getLookControl().setLookAt(ctx.target.getX(), ctx.target.getEyeY(), ctx.target.getZ(), 90.0F, 90.0F);
            npc.setYRot(yaw);
            npc.yHeadRot = yaw;
            npc.yBodyRot = yaw;
            npc.setXRot(pitch);
         }
      }
   }
}
