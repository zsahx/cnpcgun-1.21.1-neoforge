package bincnpc.gun.gun.controller;

import bincnpc.gun.common.adapter.AdapterRegistry;
import bincnpc.gun.common.api.Result;
import bincnpc.gun.common.api.Views;
import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.common.combat.CombatService;
import bincnpc.gun.common.network.GunNetwork;
import bincnpc.gun.common.script.EventBridge;
import bincnpc.gun.gun.adapter.GunAdapter;

public final class GunController {
   public Views.AmmoState ammo(CombatContext ctx) {
      Views.AmmoState state = ctx.runtime.adapter instanceof GunAdapter adapter ? AdapterRegistry.query(adapter, () -> adapter.ammo(ctx), null) : null;
      return state != null && ctx.config.bool("ammo.infinite")
         ? new Views.AmmoState(
            state.hasMagazine,
            state.hasMagazine ? Math.max(1, state.magazineCapacity == null ? 1 : state.magazineCapacity) : null,
            state.magazineCapacity,
            false,
            null,
            state.isReloading,
            false
         )
         : state;
   }

   public void tick(CombatContext ctx) {
      if (CombatService.serverThread(ctx.npc) && CombatService.validNpc(ctx.npc)) {
         if (ctx.runtime.adapter instanceof GunAdapter adapter && ctx.runtime.gunCaps != null && ctx.config.bool("gun.enabled")) {
            this.syncTiming(ctx, adapter);
            Views.AmmoState ammo = this.ammo(ctx);
            if (ammo != null) {
               boolean wasReloading = ctx.runtime.reloading;
               if (ammo.isReloading) {
                  ctx.runtime.reloadObserved = true;
               }

               boolean awaitingNativeStart = wasReloading && !ctx.runtime.reloadObserved && ctx.now - ctx.runtime.reloadStarted < 3L;
               ctx.runtime.reloading = ammo.isReloading || awaitingNativeStart;
               if (wasReloading && !ctx.runtime.reloading) {
                  ctx.runtime.reloadTargetTicks = 0;
                  ctx.runtime.reloadObserved = false;
                  if (ctx.runtime.scheduler.channel.equals("RELOAD")) {
                     ctx.runtime.scheduler.release();
                  }

                  EventBridge.emit(ctx, "gun.reload_finished", "", Result.success());
                  GunNetwork.animation(ctx, "RELOAD_END", 1);
                  ctx.runtime.noAmmoRetry = 0L;
               }

               if (ctx.runtime.reloading) {
                  ctx.runtime.scheduler.channel = "RELOAD";
                  ctx.runtime.scheduler.until = ctx.now + 2L;
                  ctx.runtime.scheduler.interruptible = false;
                  CombatService.setState(ctx, "RELOADING");
                  return;
               }

               int cost = AdapterRegistry.query(adapter, () -> adapter.ammoPerShot(ctx.weapon), 1);
               boolean empty = !ReloadPolicy.hasLoadedShot(ammo.hasMagazine, ammo.magazineAmmo, cost, ctx.config.bool("ammo.infinite"));
               if (empty && !ctx.runtime.ammoEmpty) {
                  EventBridge.emit(ctx, "gun.ammo_empty", "", Result.success());
               }

               ctx.runtime.ammoEmpty = empty;
               if (ctx.runtime.movement.ammoFallback) {
                  return;
               }

               if (ammo.canReload
                  && ctx.config.bool("ammo.autoReload")
                  && (!ctx.validTarget() || ctx.config.bool("ammo.combatReload"))
                  && ReloadPolicy.requested(
                     ctx.config.text("ammo.reloadTiming"), ctx.validTarget(), !empty, ammo.magazineAmmo, ctx.config.integer("ammo.reloadThreshold")
                  )) {
                  boolean danger = this.reloadDanger(ctx);
                  if (empty && ctx.validTarget() && danger && this.switchLoaded(ctx)) {
                     return;
                  }

                  Result result = this.reload(ctx);
                  if (result.ok) {
                     return;
                  }

                  if (empty && result.code.equals("NO_AMMO")) {
                     this.noAmmo(ctx);
                     return;
                  }
               }

               if (empty) {
                  if (ammo.reserveAmmo != null && ammo.reserveAmmo <= 0) {
                     this.noAmmo(ctx);
                  } else {
                     CombatService.setState(ctx, "WAITING");
                  }

                  return;
               }
            }

            if (!ctx.data.throwing.blocksAttack(ctx.now)) {
               if (!ctx.validTarget()) {
                  if (ctx.runtime.aiming) {
                     AdapterRegistry.call(adapter, () -> adapter.aim(ctx, false));
                  }

                  ctx.runtime.aiming = false;
                  ctx.runtime.shotStreak = 0;
               } else if (ctx.runtime.command.equals("AUTO") && ctx.config.bool("gun.autoAttack") && !ctx.runtime.fireStopped) {
                  double range = Math.min(ctx.config.number("gun.range"), ctx.config.number("combat.attackRange"));
                  if (ctx.distance > range) {
                     if (ctx.now >= ctx.runtime.nextDecision) {
                        ctx.npc.getNavigation().moveTo(ctx.target, 1.0);
                     }

                     CombatService.setState(ctx, "APPROACHING");
                  } else if (!(ctx.distance < ctx.config.number("gun.minRange"))) {
                     boolean visible = ctx.npc.getSensing().hasLineOfSight(ctx.target);
                     String prefire = ctx.config.text("gun.prefire");
                     if (!visible && !ctx.canFireWithoutSight()) {
                        String var15 = ctx.config.text("gun.lostSight");
                        switch (var15) {
                           case "APPROACH":
                              if (ctx.now >= ctx.runtime.nextDecision) {
                                 ctx.npc.getNavigation().moveTo(ctx.target, 1.0);
                              }
                              break;
                           case "STOP":
                              ctx.runtime.fireStopped = true;
                        }
                     } else {
                        boolean trackingPrefire = ctx.isPrefiring() && prefire.equals("TRACK");
                        if (trackingPrefire && ctx.now >= ctx.runtime.nextDecision) {
                           ctx.npc.getNavigation().moveTo(ctx.target, 1.0);
                        }

                        if (!ctx.config.bool("gun.moveFire") && !trackingPrefire) {
                           ctx.npc.getNavigation().stop();
                        }

                        if (ctx.config.bool("gun.jumpFire") || ctx.npc.onGround()) {
                           if (ctx.now >= ctx.runtime.nextShot && ctx.now >= ctx.runtime.nextFireAttempt) {
                              this.fire(ctx);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public Result fire(CombatContext ctx) {
      if (!CombatService.serverThread(ctx.npc) || !CombatService.validNpc(ctx.npc) || ctx.runtime.editorPaused) {
         return Result.fail("PERMISSION_DENIED");
      } else if (ctx.data.throwing.blocksAttack(ctx.now)) {
         return Result.fail("BUSY");
      } else if (!(ctx.runtime.adapter instanceof GunAdapter adapter)) {
         return Result.fail("NO_COMPATIBLE_WEAPON");
      } else if (!ctx.config.bool("combat.enabled") || !ctx.config.bool("gun.enabled")) {
         return Result.fail("PERMISSION_DENIED");
      } else if (ctx.runtime.gunCaps == null || !ctx.runtime.gunCaps.canFire) {
         return Result.fail("UNSUPPORTED");
      } else if (!ctx.validTarget()) {
         return Result.fail("INVALID_TARGET");
      } else {
         this.syncTiming(ctx, adapter);
         if (ctx.distance > Math.min(ctx.config.number("gun.range"), ctx.config.number("combat.attackRange"))
            || ctx.distance < ctx.config.number("gun.minRange")) {
            return Result.fail("OUT_OF_RANGE");
         } else if (!ctx.npc.getSensing().hasLineOfSight(ctx.target) && !ctx.canFireWithoutSight()) {
            return Result.fail("CONDITION_NOT_MET");
         } else {
            if (!ctx.config.bool("gun.moveFire")
               && ctx.npc.getDeltaMovement().horizontalDistanceSqr() > 0.01
               && (!ctx.isPrefiring() || !ctx.config.text("gun.prefire").equals("TRACK"))) {
               ctx.npc.getNavigation().stop();
            }

            if (ctx.npc.onGround() || ctx.config.bool("gun.jumpFire") && ctx.runtime.gunCaps.canJumpFire) {
               if (ctx.now >= ctx.runtime.nextShot && ctx.now >= ctx.runtime.nextFireAttempt) {
                  Result lock = ctx.runtime.scheduler.acquire("GUN", "fire", ctx.now, Math.max(1, ctx.config.integer("gun.recovery")), true, false);
                  if (!lock.ok) {
                     return lock;
                  } else {
                     Result prepared = AdapterRegistry.call(adapter, () -> adapter.prepareFire(ctx));
                     if (!prepared.ok) {
                        ctx.runtime.scheduler.release();
                        ctx.runtime.nextFireAttempt = ctx.now + 1L;
                        ctx.runtime.lastResult = prepared;
                        return prepared;
                     } else {
                        boolean aiming = ctx.config.bool("gun.requireAim");
                        if (ctx.runtime.gunCaps.canAim && ctx.runtime.aiming != aiming) {
                           Result aim = AdapterRegistry.call(adapter, () -> adapter.aim(ctx, aiming));
                           if (aim.ok) {
                              ctx.runtime.aiming = aiming;
                           }
                        }

                        double error = ctx.isPrefiring() && ctx.config.text("gun.prefire").equals("SWEEP")
                           ? 10.0
                           : (100.0 - ctx.config.number("gun.accuracy")) * 0.06;
                        ctx.runtime.shotYawOffset = (float)(ctx.npc.getRandom().nextGaussian() * error);
                        ctx.runtime.shotPitchOffset = (float)(ctx.npc.getRandom().nextGaussian() * error * 0.6);
                        EventBridge.emit(ctx, "gun.fire_requested", "", Result.success());
                        ctx.npc.setYRot(ctx.yaw());
                        ctx.npc.setXRot(ctx.pitch());
                        ctx.npc.yHeadRot = ctx.yaw();
                        ctx.runtime.fireStopped = false;
                        FireTiming.Plan timing = ctx.runtime.pendingShotTiming;
                        if (timing == null) {
                           ctx.runtime.pendingShotTiming = timing = this.interval(ctx, adapter);
                        }

                        ctx.runtime.shotInterval = timing.ticks();

                        Result result;
                        try {
                           result = AdapterRegistry.call(adapter, () -> adapter.fire(ctx));
                        } finally {
                           ctx.runtime.shotInterval = 0;
                           ctx.runtime.shotYawOffset = 0.0F;
                           ctx.runtime.shotPitchOffset = 0.0F;
                        }

                        ctx.runtime.lastResult = result;
                        if (result.ok) {
                           ctx.runtime.movement.pursueSlot = -1;
                           ctx.runtime.shotStreak++;
                           ctx.runtime.noAmmoEvent = false;
                           ctx.runtime.nextShot = ctx.now + timing.ticks();
                           ctx.runtime.burstRemaining = timing.burstRemaining();
                           ctx.runtime.nextFireAttempt = 0L;
                           ctx.runtime.pendingShotTiming = null;
                           CombatService.setState(ctx, "ATTACKING");
                           if (!"PENDING".equals(result.data)) {
                              ctx.runtime.lastFired = ctx.now;
                              ctx.runtime.lastShotInterval = timing.ticks();
                              ctx.runtime.recoil.kick(ctx.now, AdapterRegistry.query(adapter, () -> adapter.configuredRecoil(ctx), 0.0));
                              EventBridge.emit(ctx, "gun.fired", "", result);
                              GunNetwork.animation(ctx, "FIRE", Math.max(2, adapter.baseInterval(ctx.weapon)));
                           }

                           ctx.npc.updateClient = true;
                        } else {
                           ctx.runtime.scheduler.release();
                           ctx.runtime.nextFireAttempt = ctx.now + (result.code.equals("NO_AMMO") ? 20 : 1);
                           if (result.code.equals("NO_AMMO")) {
                              ctx.runtime.pendingShotTiming = null;
                           }

                           if (result.code.equals("NO_AMMO")) {
                              this.noAmmo(ctx);
                           }
                        }

                        return result;
                     }
                  }
               } else {
                  return Result.fail("COOLDOWN");
               }
            } else {
               return Result.fail("UNSUPPORTED");
            }
         }
      }
   }

   public static int configuredNativeInterval(CombatContext ctx) {
      return ctx.runtime.shotInterval > 0
         ? ctx.runtime.shotInterval
         : (
            ctx.runtime.lastShotInterval > 0
               ? ctx.runtime.lastShotInterval
               : Math.max(
                  FireTiming.scaledTicks(ctx.config.integer("gun.fireInterval"), ctx.config.number("gun.fireRateMultiplier")),
                  ctx.config.integer("gun.recovery")
               )
         );
   }

   private FireTiming.Plan interval(CombatContext ctx, GunAdapter adapter) {
      FireTiming.Settings settings = ctx.runtime.firingSettings;
      if (settings == null) {
         settings = FireTiming.Settings.read(ctx.config);
      }

      int random = settings.interval();
      if (settings.random()
         && ctx.runtime.gunCaps.canControlFireRate
         && (!settings.mode().equals("NATIVE") || settings.overrideNative() && ctx.runtime.gunCaps.canOverrideNativeRate)) {
         random = ctx.npc.getRandom().nextInt(settings.maxInterval() - settings.minInterval() + 1) + settings.minInterval();
      }

      int nativeInterval = settings.overrideNative() && ctx.runtime.gunCaps.canControlFireRate && ctx.runtime.gunCaps.canOverrideNativeRate
         ? 1
         : AdapterRegistry.query(adapter, () -> adapter.nativeInterval(ctx), 4);
      return FireTiming.plan(
         settings, nativeInterval, ctx.runtime.gunCaps.canControlFireRate, ctx.runtime.gunCaps.canOverrideNativeRate, ctx.runtime.burstRemaining, random
      );
   }

   private void syncTiming(CombatContext ctx, GunAdapter adapter) {
      if (ctx.runtime.firingSettings == null || ctx.runtime.firingRevision != ctx.config.revision) {
         FireTiming.Settings next = FireTiming.Settings.read(ctx.config);
         FireTiming.Settings previous = ctx.runtime.firingSettings;
         boolean changed = previous != null && !next.equals(previous);
         ctx.runtime.firingSettings = next;
         ctx.runtime.firingRevision = ctx.config.revision;
         if (changed) {
            ctx.runtime.burstRemaining = 0;
            ctx.runtime.pendingShotTiming = null;
            ctx.runtime.nextFireAttempt = 0L;
            ctx.runtime.lastShotInterval = 0;
            if (ctx.runtime.scheduler.channel.equals("GUN") && ctx.runtime.scheduler.interruptible) {
               ctx.runtime.scheduler.release();
            }

            if (ctx.validTarget()) {
               if (!next.mode().equals(previous.mode())) {
                  AdapterRegistry.call(adapter, () -> adapter.prepareFire(ctx));
               }

               if (ctx.runtime.lastFired >= ctx.runtime.firstTargetTick && ctx.runtime.lastFired <= ctx.now) {
                  FireTiming.Plan timing = this.interval(ctx, adapter);
                  ctx.runtime.pendingShotTiming = timing;
                  ctx.runtime.nextShot = FireTiming.reschedule(ctx.now, ctx.runtime.lastFired, timing.ticks());
                  ctx.runtime.lastShotInterval = timing.ticks();
               } else {
                  ctx.runtime.nextShot = Math.max(ctx.now, ctx.runtime.firstTargetTick + next.firstDelay() + next.windup());
               }
            }
         }
      }
   }

   public Result reload(CombatContext ctx) {
      if (CombatService.serverThread(ctx.npc) && CombatService.validNpc(ctx.npc) && !ctx.runtime.editorPaused) {
         if (ctx.runtime.adapter instanceof GunAdapter adapter) {
            if (!ctx.config.bool("combat.enabled") || !ctx.config.bool("gun.enabled")) {
               return Result.fail("PERMISSION_DENIED");
            } else if (ctx.config.bool("ammo.infinite")) {
               return Result.fail("UNSUPPORTED");
            } else if (ctx.runtime.gunCaps == null || !ctx.runtime.gunCaps.canReload) {
               return Result.fail("UNSUPPORTED");
            } else if (!ctx.runtime.reloading && ctx.runtime.shotInterval <= 0) {
               Views.AmmoState state = this.ammo(ctx);
               if (state != null && state.isReloading) {
                  return Result.fail("BUSY");
               } else if (state != null
                  && !ctx.config.bool("ammo.partialReload")
                  && state.magazineCapacity != null
                  && state.magazineAmmo != null
                  && state.reserveAmmo != null
                  && state.reserveAmmo < state.magazineCapacity - state.magazineAmmo) {
                  return Result.fail("NO_AMMO");
               } else {
                  if (ctx.runtime.scheduler.busy(ctx.now) && ctx.runtime.scheduler.channel.equals("GUN") && ctx.runtime.scheduler.interruptible) {
                     AdapterRegistry.run(adapter, () -> adapter.stopFire(ctx));
                     ctx.runtime.scheduler.release();
                  }

                  Result lock = ctx.runtime.scheduler.acquire("RELOAD", "reload", ctx.now, 200, false, false);
                  if (!lock.ok) {
                     return lock;
                  } else {
                     int fastest = ctx.config.integer("gun.reloadMinTicks");
                     int slowest = ctx.config.integer("gun.reloadMaxTicks");
                     ctx.runtime.reloadTargetTicks = ctx.config.bool("gun.reloadSpeedEnabled")
                        ? fastest + ctx.npc.getRandom().nextInt(slowest - fastest + 1)
                        : 0;
                     ctx.runtime.reloadStarted = ctx.now;
                     ctx.runtime.reloadObserved = false;
                     Result result = AdapterRegistry.call(adapter, () -> adapter.reload(ctx));
                     if (result.ok) {
                        ctx.runtime.reloading = true;
                        CombatService.setState(ctx, "RELOADING");
                        EventBridge.emit(ctx, "gun.reload_started", "", result);
                        GunNetwork.animation(
                           ctx,
                           "RELOAD",
                           ctx.runtime.reloadTargetTicks > 0 ? ctx.runtime.reloadTargetTicks : 30,
                           ctx.runtime.reloadTargetTicks > 0 ? "reload_override" : ""
                        );
                     } else {
                        ctx.runtime.scheduler.release();
                        ctx.runtime.reloadTargetTicks = 0;
                        ctx.runtime.reloadObserved = false;
                     }

                     return result;
                  }
               }
            } else {
               return Result.fail("BUSY");
            }
         } else {
            return Result.fail("NO_COMPATIBLE_WEAPON");
         }
      } else {
         return Result.fail("PERMISSION_DENIED");
      }
   }

   private boolean reloadDanger(CombatContext ctx) {
      return !ctx.validTarget()
         ? false
         : ctx.npc.getHealth() / Math.max(1.0F, ctx.npc.getMaxHealth()) <= ctx.config.number("weapon.lowHealth")
            || ctx.distance <= Math.max(6.0, ctx.config.number("weapon.meleeDistance"))
            || ctx.targets().size() >= 3;
   }

   private boolean switchLoaded(CombatContext ctx) {
      if (ctx.runtime.command.equals("AUTO") && ctx.config.bool("weapon.autoSwitch")) {
         int old = ctx.data.armory.active;
         Result result = CombatService.switchBest(ctx, false, true);
         return result.ok && old != ctx.data.armory.active;
      } else {
         return false;
      }
   }

   private void noAmmo(CombatContext ctx) {
      if (ctx.now >= ctx.runtime.noAmmoRetry) {
         ctx.runtime.noAmmoRetry = ctx.now + 40L;
         if (!ctx.runtime.noAmmoEvent) {
            ctx.runtime.noAmmoEvent = true;
            EventBridge.emit(ctx, "gun.no_ammo", "", Result.fail("NO_AMMO"));
         }

         if (ctx.runtime.command.equals("AUTO") || !ctx.runtime.noAmmoCommand.isEmpty()) {
            String var2 = ctx.config.text("ammo.noAmmoBehavior");
            switch (var2) {
               case "SWITCH_WEAPON":
               case "DELEGATE":
                  int old = ctx.data.armory.active;
                  Result switched = CombatService.switchBest(ctx, false, true);
                  if (switched.ok && old != ctx.data.armory.active) {
                     return;
                  }

                  CombatService.pursueMelee(ctx);
                  break;
               case "SWITCH_TO_MELEE":
                  int previous = ctx.data.armory.active;
                  Result meleeSwitch = CombatService.switchBest(ctx, true, true);
                  if (meleeSwitch.ok && previous != ctx.data.armory.active) {
                     return;
                  }

                  CombatService.pursueMelee(ctx);
                  break;
               case "RETREAT":
                  this.noAmmoCommand(ctx, "RETREAT");
                  break;
               case "HOLD_POSITION":
                  this.noAmmoCommand(ctx, "HOLD");
            }

            if (ctx.runtime.scheduler.channel.isEmpty()) {
               CombatService.setState(ctx, "NO_AMMO");
            }
         }
      }
   }

   private void noAmmoCommand(CombatContext ctx, String command) {
      if (CombatService.command(ctx, command).ok) {
         ctx.runtime.noAmmoCommand = command;
      }
   }

   public Result stop(CombatContext ctx) {
      ctx.runtime.fireStopped = true;
      ctx.runtime.recoil.reset();
      ctx.runtime.burstRemaining = 0;
      ctx.runtime.pendingShotTiming = null;
      ctx.runtime.nextFireAttempt = 0L;
      ctx.runtime.lastShotInterval = 0;
      if (ctx.runtime.adapter instanceof GunAdapter adapter) {
         AdapterRegistry.run(adapter, () -> adapter.stopFire(ctx));
      }

      if (ctx.runtime.scheduler.channel.equals("GUN")) {
         ctx.runtime.scheduler.release();
      }

      return Result.success();
   }
}
