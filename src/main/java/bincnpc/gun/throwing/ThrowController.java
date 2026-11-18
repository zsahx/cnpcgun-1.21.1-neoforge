package bincnpc.gun.throwing;

import bincnpc.gun.CNPCGun;
import bincnpc.gun.common.adapter.AdapterRegistry;
import bincnpc.gun.common.api.Result;
import bincnpc.gun.common.api.Views;
import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.common.combat.CombatService;
import bincnpc.gun.common.combat.Scheduler;
import bincnpc.gun.common.network.GunNetwork;
import bincnpc.gun.common.script.EventBridge;
import bincnpc.gun.gun.adapter.GunAdapter;
import bincnpc.gun.gun.controller.ReloadPolicy;
import bincnpc.gun.slashblade.adapter.BladeAdapter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

public final class ThrowController {
   private static final int PLAN_TIMEOUT = 28;
   private ThrowController.Pending pending;
   private long nextThrow;
   private long nextEvaluation;
   private long recoveryUntil;
   private long lastBackstep = Long.MIN_VALUE;
   private long nextWarning;
   private long nextDiagnostic;
   private final long[] retryAt = new long[3];

   public boolean blocksAttack(long now) {
      return this.pending != null && (this.pending.reserved || this.pending.winding()) || now < this.recoveryUntil;
   }

   public boolean movementLocked() {
      return this.pending != null && (this.pending.reserved || this.pending.winding());
   }

   public boolean pausesCharge() {
      return this.pending != null && this.pending.reserved && !this.pending.winding();
   }

   public boolean planning() {
      return this.pending != null;
   }

   public void cancel() {
      if (this.pending != null) {
         if (this.pending.winding()
            && this.pending.projectile.getOwner() instanceof EntityNPCInterface npc
            && CombatService.serverThread(npc)
            && CombatService.validNpc(npc)) {
            GunNetwork.animation(new CombatContext(npc), "THROW_END", 1);
         }

         this.pending.clearPlan();
         if (!this.pending.projectile.isRemoved()) {
            this.pending.projectile.discard();
         }

         this.pending = null;
      }

      this.recoveryUntil = 0L;
      this.lastBackstep = Long.MIN_VALUE;
   }

   public void reset() {
      this.cancel();
      this.nextThrow = 0L;
      this.nextEvaluation = 0L;
      Arrays.fill(this.retryAt, 0L);
   }

   private void diagnostic(CombatContext ctx, int slot, String reason) {
      if (ctx.now >= this.nextDiagnostic) {
         this.nextDiagnostic = ctx.now + 200L;
         CNPCGun.LOGGER.debug("NPC {} throwable slot {}: {}", new Object[]{ctx.npc.getUUID(), slot, reason});
      }
   }

   private void abort(CombatContext ctx, String reason) {
      if (this.pending != null) {
         this.retryAt[this.pending.slot] = ctx.now + 40L;
         this.diagnostic(ctx, this.pending.slot, reason);
      }

      this.cancel();
      this.nextEvaluation = ctx.now + 2L;
   }

   public void tick(CombatContext ctx) {
      if (ctx != null && CombatService.serverThread(ctx.npc)) {
         try {
            this.tickMain(ctx);
         } catch (LinkageError | RuntimeException var3) {
            this.abort(ctx, "adapter exception");
            if (ctx.now >= this.nextWarning) {
               this.nextWarning = ctx.now + 1200L;
               CNPCGun.LOGGER.warn("NPC {} throwable planning cancelled", ctx.npc.getUUID(), var3);
            }
         }
      }
   }

   private void tickMain(CombatContext ctx) {
      if (CombatService.serverThread(ctx.npc)) {
         if (!CombatService.validNpc(ctx.npc)
            || ctx.runtime.editorPaused
            || !ctx.config.bool("combat.enabled")
            || !ctx.config.bool("throw.enabled")
            || !ctx.runtime.command.equals("AUTO")
            || ctx.npc.isPassenger()) {
            this.cancel();
         } else if (ctx.runtime.movement.ammoFallback && ctx.runtime.movement.pursueSlot >= 0) {
            this.cancel();
         } else if (this.pending == null) {
            if (this.recoveryUntil > 0L && ctx.now >= this.recoveryUntil) {
               this.recoveryUntil = 0L;
               if (ctx.runtime.combatState.equals("THROWING")) {
                  CombatService.setState(ctx, ctx.validTarget() ? "WAITING" : "IDLE");
               }
            }

            if (ctx.now >= this.nextEvaluation && ctx.now >= this.nextThrow && ctx.now >= this.recoveryUntil && ctx.validTarget() && ThrowFlights.hasRoom()) {
               this.nextEvaluation = ctx.now + 4L;
               this.select(ctx);
            }
         } else {
            ThrowController.Pending p = this.pending;
            if (p.revision != ctx.config.revision
               || p.inventoryEpoch != ctx.data.throwItems.epoch
               || ctx.data.throwItems.remaining(ctx, p.slot) == 0
               || !p.dimension.equals(ctx.npc.level().dimension().location().toString())
               || !p.key.equals(ctx.data.throwItems.get(p.slot).getItem().toString())) {
               this.abort(ctx, "equipment, configuration or quota changed");
            } else if (!this.target(ctx, p)) {
               this.abort(ctx, "target lost");
            } else {
               if (p.reserved && !p.winding()) {
                  this.hold(ctx);
               }

               if (p.winding()) {
                  this.hold(ctx);
                  if (ctx.now > p.releaseAt + 28L) {
                     this.abort(ctx, "release planning wait expired");
                     return;
                  }

                  if (!p.finalPlan && ctx.now >= p.releaseAt - Math.min(10, ctx.config.integer(p.prefix() + "windup"))) {
                     p.finalPlan = true;
                     this.startPlan(ctx, p, (int)Math.max(0L, p.releaseAt - ctx.now));
                  }
               }

               advancePlan(ctx, p);
               if (p.failed || ThrowPolicy.planExpired(ctx.now, p.planStarted) && p.candidate == null) {
                  this.abort(ctx, p.failed ? p.failure : "planning timeout");
               } else if (p.candidate == null) {
                  if (p.winding() && ctx.now >= p.releaseAt && !p.holdingPose) {
                     p.holdingPose = true;
                     GunNetwork.animation(ctx, "THROW_HOLD", Math.max(1, 28 - (int)(ctx.now - p.planStarted) + 1));
                  }
               } else if (!p.winding()) {
                  if (ThrowPolicy.planExpired(ctx.now, p.planStarted)) {
                     this.abort(ctx, "combat action remained locked until prediction expired");
                  } else if (!this.fresh(ctx, p, false)) {
                     this.abort(ctx, "initial prediction became stale");
                  } else if (beginWindup(ctx)) {
                     p.reserved = true;
                     int windup = ctx.config.integer(p.prefix() + "windup");
                     p.releaseAt = ctx.now + windup;
                     GunNetwork.animation(ctx, "THROW", Math.max(1, windup));
                     this.hold(ctx);
                     if (windup == 0) {
                        if (this.fresh(ctx, p, true)) {
                           this.release(ctx, p);
                        } else {
                           this.abort(ctx, "release prediction became stale");
                        }
                     } else {
                        if (windup <= 10) {
                           p.finalPlan = true;
                           if (windup >= 2) {
                              this.startPlan(ctx, p, windup);
                           }
                        }
                     }
                  }
               } else if (ctx.now >= p.releaseAt && p.finalPlan) {
                  if (!this.fresh(ctx, p, true)) {
                     this.abort(ctx, "release prediction became stale");
                  } else {
                     this.release(ctx, p);
                  }
               }
            }
         }
      }
   }

   private static boolean beginWindup(CombatContext ctx) {
      Scheduler scheduler = ctx.runtime.scheduler;
      if (ctx.runtime.adapter instanceof BladeAdapter) {
         return ctx.data.blade.suspendForThrow(ctx).ok;
      } else {
         if (scheduler.busy(ctx.now) && !scheduler.channel.equals("RELOAD")) {
            if (!scheduler.channel.equals("GUN") || !scheduler.interruptible) {
               return false;
            }

            scheduler.release();
         }

         if (ctx.runtime.adapter instanceof GunAdapter gun) {
            AdapterRegistry.run(gun, () -> gun.stopFire(ctx));
         }

         return true;
      }
   }

   private static boolean firingTask(CombatContext ctx) {
      if (ctx.runtime.adapter instanceof GunAdapter gun
         && ctx.runtime.gunCaps != null
         && ctx.runtime.gunCaps.canFire
         && !ctx.runtime.reloading
         && !ctx.runtime.fireStopped
         && ctx.config.bool("gun.enabled")) {
         Views.AmmoState ammo = ctx.data.gun.ammo(ctx);
         int cost = AdapterRegistry.query(gun, () -> gun.ammoPerShot(ctx.weapon), 1);
         if (ammo == null
            ? !ctx.runtime.ammoEmpty
            : !ammo.isReloading && ReloadPolicy.hasLoadedShot(ammo.hasMagazine, ammo.magazineAmmo, cost, ctx.config.bool("ammo.infinite"))) {
            boolean firingAction = ctx.runtime.scheduler.busy(ctx.now) && ctx.runtime.scheduler.channel.equals("GUN");
            return ThrowPolicy.shootingTask(ctx.now, ctx.runtime.firstTargetTick, ctx.runtime.lastFired, ctx.config.bool("gun.autoAttack"), firingAction);
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private void select(CombatContext ctx) {
      String unavailable = "no equipped compatible throwable with remaining quota";
      Integer[] order = new Integer[]{0, 1, 2};
      Arrays.sort(order, Comparator.<Integer>comparingInt(slotx -> ctx.config.integer("throw.slot" + slotx + ".priority")).reversed());
      Integer[] var4 = order;
      int var5 = order.length;

      for (int var6 = 0; var6 < var5; var6++) {
         int slot = var4[var6];
         String prefix = "throw.slot" + slot + ".";
         if (ctx.now >= this.retryAt[slot] && ctx.data.throwItems.remaining(ctx, slot) != 0) {
            if (!(ctx.distance < ctx.config.number(prefix + "minRange"))
               && !(ctx.distance > ctx.config.number(prefix + "maxRange"))
               && !(ctx.distance > ctx.config.number(prefix + "calculationRange"))) {
               String timing = ctx.config.text(prefix + "timing");
               if ((!timing.equals("RELOAD") || ctx.runtime.reloading)
                  && (!timing.equals("SHOOT") || firingTask(ctx))
                  && (!timing.equals("RANDOM") || ctx.npc.getRandom().nextInt(12) == 0)) {
                  ItemStack item = ctx.data.throwItems.get(slot);
                  ThrowAdapter adapter = ThrowAdapters.find(item);
                  if (adapter != null) {
                     Projectile projectile = null;

                     try {
                        projectile = adapter.create((ServerLevel)ctx.npc.level(), ctx.npc, item.copyWithCount(1));
                        if (projectile != null && !projectile.isRemoved() && projectile.level() == ctx.npc.level()) {
                           projectile.setOwner(ctx.npc);
                           if (((ServerLevel)ctx.npc.level()).getEntity(projectile.getUUID()) == projectile) {
                              this.diagnostic(ctx, slot, "native factory spawned the projectile before validation");
                              this.retryAt[slot] = ctx.now + 40L;
                              projectile.discard();
                           } else {
                              ThrowAdapter.Profile profile = adapter.profile(projectile);
                              if (profile == null) {
                                 this.diagnostic(ctx, slot, "native projectile has no physics profile");
                                 projectile.discard();
                                 this.retryAt[slot] = ctx.now + 40L;
                              } else {
                                 Ballistics.Vec offset = new Ballistics.Vec(0.0, 0.0, 0.0);
                                 if (ctx.config.text(prefix + "area").equals("AREA")) {
                                    double angle = ctx.npc.getRandom().nextDouble() * Math.PI * 2.0;
                                    double distance = Math.sqrt(ctx.npc.getRandom().nextDouble()) * ctx.config.number(prefix + "deviation");
                                    offset = new Ballistics.Vec(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance);
                                 }

                                 ThrowController.Pending p = new ThrowController.Pending(ctx, slot, adapter, profile, projectile, offset);
                                 updateTarget(p, ctx.target);
                                 if (p.safeDistance && !CollisionSnapshot.safeArea((ServerLevel)ctx.npc.level(), ctx.npc, p.aim, profile.blastRadius())) {
                                    this.diagnostic(ctx, slot, "unsafe landing area");
                                    this.retryAt[slot] = ctx.now + 40L;
                                    projectile.discard();
                                 } else {
                                    if (timing.equals("SHOOT")) {
                                       Scheduler scheduler = ctx.runtime.scheduler;
                                       if (ctx.runtime.shotInterval > 0
                                          || scheduler.busy(ctx.now) && (!scheduler.channel.equals("GUN") || !scheduler.interruptible)) {
                                          unavailable = "firing action cannot be interrupted";
                                          projectile.discard();
                                          this.retryAt[slot] = ctx.now + 2L;
                                          continue;
                                       }

                                       p.reserved = true;
                                    }

                                    this.pending = p;
                                    if (p.reserved) {
                                       this.hold(ctx);
                                    }

                                    this.startPlan(ctx, p, ctx.config.integer(prefix + "windup"));
                                    return;
                                 }
                              }
                           }
                        } else {
                           this.diagnostic(ctx, slot, "native factory did not return a valid projectile for this world");
                           this.retryAt[slot] = ctx.now + 40L;
                           if (projectile != null && !projectile.isRemoved()) {
                              projectile.discard();
                           }
                        }
                     } catch (LinkageError | RuntimeException var19) {
                        if (projectile != null && !projectile.isRemoved()) {
                           projectile.discard();
                        }

                        this.retryAt[slot] = ctx.now + 40L;
                        if (ctx.now >= this.nextWarning) {
                           this.nextWarning = ctx.now + 1200L;
                           CNPCGun.LOGGER.warn("Unable to prepare NPC {} throwable slot {}", new Object[]{ctx.npc.getUUID(), slot, var19});
                        }
                     }
                  }
               } else {
                  unavailable = "configured timing " + timing + " not active";
               }
            } else {
               unavailable = "target outside throwing or calculation range";
            }
         }
      }

      this.diagnostic(ctx, -1, unavailable);
   }

   private static void updateTarget(ThrowController.Pending p, LivingEntity target) {
      p.aim = CollisionSnapshot.vector(target.getBoundingBox().getCenter()).add(p.offset);
      boolean verticallyStationary = Math.abs(target.getY() - target.yo) < 0.001 && target.getDeltaMovement().y <= 0.0;
      p.movement = Ballistics.targetMovement(CollisionSnapshot.vector(target.getDeltaMovement()), target.onGround() || verticallyStationary);
      p.targetRadius = Math.min(8.0, target.getBbWidth() * 0.5);
   }

   private boolean target(CombatContext ctx, ThrowController.Pending p) {
      if (p.target == null) {
         return true;
      } else if (((ServerLevel)ctx.npc.level()).getEntity(p.target) instanceof LivingEntity living && CombatService.validTarget(ctx.npc, living)) {
         updateTarget(p, living);
         return true;
      } else {
         String var7 = ctx.config.text(p.prefix() + "lostTarget");
         switch (var7) {
            case "CONTINUE":
               p.target = null;
               p.movement = new Ballistics.Vec(0.0, 0.0, 0.0);
               break;
            case "RETARGET":
               LivingEntity selected = ctx.targets()
                  .stream()
                  .filter(target -> ctx.npc.distanceTo(target) <= ctx.config.number(p.prefix() + "maxRange"))
                  .min(Comparator.comparingDouble(target -> ctx.npc.distanceToSqr(target)))
                  .orElse(null);
               if (selected == null) {
                  return false;
               }

               p.target = selected.getUUID();
               updateTarget(p, selected);
               break;
            default:
               return false;
         }

         this.startPlan(ctx, p, p.winding() ? (int)Math.max(0L, p.releaseAt - ctx.now) : ctx.config.integer(p.prefix() + "windup"));
         return true;
      }
   }

   private Ballistics.Request request(CombatContext ctx, ThrowController.Pending p, int lead) {
      Vec3 aim = CollisionSnapshot.vector(p.aim);
      Vec3 forward = aim.subtract(ctx.npc.getEyePosition()).normalize();
      Vec3 origin = ctx.npc.getEyePosition().add(0.0, -0.18, 0.0).add(forward.scale(0.3));
      ThrowAdapter.Profile profile = p.profile;
      String prefix = p.prefix();
      Ballistics.Physics physics = new Ballistics.Physics(
         profile.speed() * ctx.config.number(prefix + "speed"), profile.gravity(), profile.drag(), profile.radius(), profile.blastRadius(), profile.life()
      );
      return new Ballistics.Request(
         CollisionSnapshot.vector(origin),
         p.aim,
         p.movement,
         physics,
         Ballistics.Trajectory.valueOf(ctx.config.text(prefix + "trajectory")),
         ctx.config.number(prefix + "maxElevation"),
         ctx.config.number(prefix + "maxDepression"),
         ctx.config.number(prefix + "calculationRange"),
         ctx.config.text(prefix + "area").equals("AREA") ? 0.0 : ctx.config.number(prefix + "deviation"),
         p.targetRadius,
         Math.min(200, lead),
         ctx.config.bool(prefix + "prediction")
      );
   }

   private void startPlan(CombatContext ctx, ThrowController.Pending p, int lead) {
      p.clearPlan();
      p.planStarted = ctx.now;

      try {
         p.request = this.request(ctx, p, lead);
         p.solving = ThrowPlanner.plan(p.request);
         p.failed = p.solving == null;
         p.failure = p.failed ? "planner queue unavailable" : "";
      } catch (IllegalArgumentException var5) {
         p.failed = true;
         p.failure = "invalid native projectile physics or parameters";
      }
   }

   private static void advancePlan(CombatContext ctx, ThrowController.Pending p) {
      if (p.solving != null && p.solving.finished()) {
         List<Ballistics.Candidate> result = p.solving.result();
         p.solving = null;
         if (result == null || result.isEmpty()) {
            p.failed = true;
            p.failure = result == null ? "numeric planner cancelled" : "no ballistic solution for speed, range and angle";
            return;
         }

         p.candidates = result.stream()
            .sorted(Comparator.comparingDouble(candidatex -> candidatex.ticks() + (candidatex.mode() == Ballistics.Trajectory.CURVE ? 0.25 : 0.0)))
            .toList();
      }

      if (p.capture == null && p.assessing == null && p.candidate == null && !p.candidates.isEmpty()) {
         while (p.capture == null && p.nextCandidate < p.candidates.size()) {
            Ballistics.Candidate candidate = p.candidates.get(p.nextCandidate++);

            try {
               p.capture = new CollisionSnapshot((ServerLevel)ctx.npc.level(), ctx.npc, p.request, List.of(candidate), p.safeDistance);
            } catch (IllegalArgumentException var4) {
               p.failure = "trajectory exceeded terrain capture budget";
            }
         }

         if (p.capture == null) {
            p.failed = true;
            p.failure = "all trajectories blocked, unsafe or beyond terrain capture budget";
            return;
         }
      }

      if (p.capture != null && p.capture.capture()) {
         p.world = p.capture.result();
         p.capture = null;
         if (p.world == null) {
            p.failed = p.nextCandidate >= p.candidates.size();
            p.failure = "terrain or blast safety capture failed";
            return;
         }

         p.capturedAt = ctx.now;
         p.assessing = ThrowPlanner.assess(p.world);
         if (p.assessing == null) {
            p.failed = true;
            p.failure = "collision planner queue unavailable";
            return;
         }
      }

      if (p.assessing != null && p.assessing.finished()) {
         p.candidate = p.assessing.result();
         p.assessing = null;
         if (p.candidate == null) {
            p.world = null;
            p.failed = p.nextCandidate >= p.candidates.size();
            p.failure = "all trajectories blocked or unsafe";
         }
      }
   }

   private boolean fresh(CombatContext ctx, ThrowController.Pending p, boolean release) {
      if (!ThrowPolicy.predictionFresh(ctx.now, p.planStarted, p.capturedAt, release)) {
         return false;
      } else {
         Ballistics.Request current = this.request(ctx, p, 0);
         if (current.origin().distance(p.request.origin()) > (release ? 0.9 : 2.0)) {
            return false;
         } else {
            Ballistics.Vec expected = p.request
               .target()
               .add(p.request.prediction() ? p.request.movement().scale(ctx.now - p.planStarted) : new Ballistics.Vec(0.0, 0.0, 0.0));
            return current.target().distance(expected) <= Math.max(0.75, p.targetRadius + ctx.config.number(p.prefix() + "deviation"));
         }
      }
   }

   private void release(CombatContext ctx, ThrowController.Pending p) {
      try {
         Ballistics.Request current = this.request(ctx, p, 0);
         Ballistics.Candidate candidate = Ballistics.retarget(current, p.candidate);
         if (candidate == null || p.world == null || !ThrowFlights.hasRoom()) {
            this.abort(ctx, "release correction failed or flight table full");
            return;
         }

         candidate = Ballistics.choose(new Ballistics.World(current, List.of(candidate), p.world.walls(), p.world.danger()));
         if (candidate == null
            || p.safeDistance && !CollisionSnapshot.safeArea((ServerLevel)ctx.npc.level(), ctx.npc, candidate.goal(), p.profile.blastRadius())) {
            this.abort(ctx, "corrected trajectory blocked or unsafe");
            return;
         }

         if (p.target != null) {
            if (!(
               ((ServerLevel)ctx.npc.level()).getEntity(p.target) instanceof LivingEntity living
                  && CombatService.validTarget(ctx.npc, living)
                  && !(ctx.npc.distanceTo(living) < ctx.config.number(p.prefix() + "minRange"))
                  && !(ctx.npc.distanceTo(living) > ctx.config.number(p.prefix() + "maxRange"))
            )) {
               this.abort(ctx, "target invalid or outside throwing range");
               return;
            }
         } else if (current.origin().distance(current.target()) < ctx.config.number(p.prefix() + "minRange")
            || current.origin().distance(current.target()) > ctx.config.number(p.prefix() + "maxRange")) {
            this.abort(ctx, "frozen landing area outside throwing range");
            return;
         }

         Projectile projectile = p.projectile;
         projectile.setPos(CollisionSnapshot.vector(candidate.origin()));
         if (!CollisionSnapshot.volumeLoaded((ServerLevel)ctx.npc.level(), projectile.getBoundingBox()) || !ctx.npc.level().noCollision(projectile)) {
            this.abort(ctx, "release point obstructed or terrain unloaded");
            return;
         }

         if (candidate.mode() != Ballistics.Trajectory.CURVE) {
            p.adapter.straight(projectile);
         } else {
            projectile.setNoGravity(p.profile.customGravity());
         }

         Ballistics.Vec velocity = candidate.velocity();
         p.adapter.launch(projectile, CollisionSnapshot.vector(velocity));
         if (candidate.mode() == Ballistics.Trajectory.TRACKING) {
            projectile.setDeltaMovement(Vec3.ZERO);
         } else {
            Vec3 next = projectile.position().add(CollisionSnapshot.vector(candidate.velocity()).scale(Math.min(1.0, candidate.ticks())));
            if (!CollisionSnapshot.clear((ServerLevel)ctx.npc.level(), projectile, projectile.position(), next)
               && projectile.position().distanceToSqr(CollisionSnapshot.vector(candidate.goal())) > current.tolerance() * current.tolerance()) {
               this.abort(ctx, "first flight segment obstructed");
               return;
            }
         }

         ThrowFlights.mark(ctx, projectile);
         if (!((ServerLevel)ctx.npc.level()).addFreshEntity(projectile)) {
            this.abort(ctx, "native projectile spawn rejected");
            return;
         }

         if (this.pending != p
            || ctx.config.revision != p.revision
            || ctx.data.throwItems.epoch != p.inventoryEpoch
            || !CombatService.validNpc(ctx.npc)
            || !ThrowFlights.register(ctx, projectile, p.adapter, p.profile, candidate, p.target)) {
            if (!projectile.isRemoved()) {
               projectile.discard();
            }

            if (this.pending == p) {
               this.cancel();
            }

            return;
         }

         ctx.data.throwItems.used(ctx, p.slot);
         this.nextThrow = ctx.now + ctx.config.integer("throw.cooldown");
         this.recoveryUntil = ctx.now + ctx.config.integer(p.prefix() + "recovery");
         p.clearPlan();
         this.pending = null;
         ctx.npc.updateClient = true;
         GunNetwork.animation(ctx, "THROW_RELEASE", Math.max(1, ctx.config.integer(p.prefix() + "recovery")));
         CombatService.setState(ctx, "THROWING");
         EventBridge.emit(ctx, "throw.released", "slot:" + p.slot, Result.success());
      } catch (LinkageError | RuntimeException var8) {
         this.abort(ctx, "native release exception");
         if (ctx.now >= this.nextWarning) {
            this.nextWarning = ctx.now + 1200L;
            CNPCGun.LOGGER.warn("Unable to release NPC {} throwable", ctx.npc.getUUID(), var8);
         }
      }
   }

   private void hold(CombatContext ctx) {
      ctx.npc.getNavigation().stop();
      ctx.npc.getMoveControl().strafe(0.0F, 0.0F);
      ctx.npc.xxa = 0.0F;
      ctx.npc.zza = 0.0F;
      Vec3 movement = ctx.npc.getDeltaMovement();
      ctx.npc.setDeltaMovement(0.0, movement.y, 0.0);
      CombatService.setState(ctx, "THROWING");
   }

   public void afterAi(CombatContext ctx) {
      if (CombatService.serverThread(ctx.npc) && this.movementLocked() && CombatService.validNpc(ctx.npc) && this.lastBackstep != ctx.now) {
         this.lastBackstep = ctx.now;
         this.hold(ctx);
         if (this.pending != null && this.pending.winding()) {
            Vec3 away = ctx.npc.position().subtract(CollisionSnapshot.vector(this.pending.aim));
            double horizontal = away.horizontalDistance();
            if (horizontal < 1.0E-6) {
               away = Vec3.directionFromRotation(0.0F, ctx.npc.getYRot()).scale(-1.0);
            } else {
               away = new Vec3(away.x / horizontal, 0.0, away.z / horizontal);
            }

            AABB destination = ctx.npc.getBoundingBox().move(away.scale(0.02));
            ServerLevel level = (ServerLevel)ctx.npc.level();
            if (CollisionSnapshot.volumeLoaded(level, destination)) {
               ctx.npc.move(MoverType.SELF, away.scale(0.02));
            }
         }
      }
   }

   private static final class Pending {
      final int slot;
      final long revision;
      final long inventoryEpoch;
      final String dimension;
      final String key;
      final ThrowAdapter adapter;
      final ThrowAdapter.Profile profile;
      final Projectile projectile;
      final Ballistics.Vec offset;
      final boolean safeDistance;
      UUID target;
      Ballistics.Vec aim;
      Ballistics.Vec movement;
      double targetRadius;
      long releaseAt = -1L;
      long planStarted;
      long capturedAt = -1L;
      boolean finalPlan;
      boolean failed;
      boolean reserved;
      boolean holdingPose;
      String failure = "";
      Ballistics.Request request;
      Ballistics.Candidate candidate;
      Ballistics.World world;
      List<Ballistics.Candidate> candidates = List.of();
      int nextCandidate;
      ThrowPlanner.Ticket<List<Ballistics.Candidate>> solving;
      ThrowPlanner.Ticket<Ballistics.Candidate> assessing;
      CollisionSnapshot capture;

      Pending(CombatContext ctx, int slot, ThrowAdapter adapter, ThrowAdapter.Profile profile, Projectile projectile, Ballistics.Vec offset) {
         this.slot = slot;
         this.adapter = adapter;
         this.profile = profile;
         this.projectile = projectile;
         this.offset = offset;
         this.revision = ctx.config.revision;
         this.inventoryEpoch = ctx.data.throwItems.epoch;
         this.dimension = ctx.npc.level().dimension().location().toString();
         this.target = ctx.target.getUUID();
         this.key = ctx.data.throwItems.get(slot).getItem().toString();
         this.safeDistance = ThrowPolicy.requiresSafeDistance(ctx.config.text("throw.slot" + slot + ".timing"));
      }

      String prefix() {
         return "throw.slot" + this.slot + ".";
      }

      boolean winding() {
         return this.releaseAt >= 0L;
      }

      void clearPlan() {
         if (this.solving != null) {
            this.solving.cancel();
         }

         if (this.assessing != null) {
            this.assessing.cancel();
         }

         this.solving = null;
         this.assessing = null;
         this.capture = null;
         this.candidate = null;
         this.world = null;
         this.failed = false;
         this.failure = "";
         this.candidates = List.of();
         this.nextCandidate = 0;
         this.capturedAt = -1L;
      }
   }
}
