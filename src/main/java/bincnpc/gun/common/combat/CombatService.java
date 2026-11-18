package bincnpc.gun.common.combat;

import bincnpc.gun.common.adapter.AdapterRegistry;
import bincnpc.gun.common.adapter.WeaponAdapter;
import bincnpc.gun.common.api.Result;
import bincnpc.gun.common.api.SafeJson;
import bincnpc.gun.common.api.Views;
import bincnpc.gun.common.config.ConfigSchema;
import bincnpc.gun.common.config.NpcConfig;
import bincnpc.gun.common.config.Parameter;
import bincnpc.gun.common.network.EditorWeapons;
import bincnpc.gun.common.npc.NpcAccess;
import bincnpc.gun.common.npc.NpcData;
import bincnpc.gun.common.npc.RuntimeState;
import bincnpc.gun.common.script.EventBridge;
import bincnpc.gun.gun.adapter.GunAdapter;
import bincnpc.gun.gun.ammo.AmmoInventory;
import bincnpc.gun.gun.ammo.AmmoSupply;
import bincnpc.gun.gun.controller.ReloadPolicy;
import bincnpc.gun.slashblade.adapter.BladeAdapter;
import bincnpc.gun.slashblade.strategy.Presets;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.registries.BuiltInRegistries;
import noppes.npcs.api.wrapper.ItemStackWrapper;
import noppes.npcs.entity.EntityNPCInterface;

public final class CombatService {
   private CombatService() {
   }

   public static NpcData data(EntityNPCInterface npc) {
      return ((NpcAccess)npc).cnpcgun$data();
   }

   public static boolean validNpc(EntityNPCInterface npc) {
      return npc != null
         && npc.isAlive()
         && !npc.isRemoved()
         && !npc.isKilled()
         && npc.level() instanceof ServerLevel level
         && level.getEntity(npc.getUUID()) == npc;
   }

   public static boolean serverThread(EntityNPCInterface npc) {
      return npc != null && npc.getServer() != null && npc.getServer().isSameThread() && !npc.level().isClientSide;
   }

   public static boolean validTarget(EntityNPCInterface npc, LivingEntity target) {
      return npc != null
         && target != null
         && target != npc
         && target.isAlive()
         && !target.isRemoved()
         && !target.isSpectator()
         && target.level() == npc.level()
         && !npc.isAlliedTo(target)
         && npc.canAttack(target)
         && !(target instanceof Player player && player.isCreative())
         && !(npc.level() instanceof ServerLevel level && level.getEntity(target.getUUID()) != target);
   }

   public static boolean isControlled(EntityNPCInterface npc) {
      if (npc instanceof NpcAccess && !npc.level().isClientSide) {
         RuntimeState runtime = data(npc).runtime;
         return runtime.adapter != null && AdapterRegistry.loaded(runtime.adapter.id())
            || runtime.movement.meleeFallback
            || data(npc).throwing.blocksAttack(npc.level().getGameTime());
      } else {
         return false;
      }
   }

   public static boolean blocksNative(EntityNPCInterface npc) {
      if (npc == null || npc.level().isClientSide || !(npc instanceof NpcAccess)) {
         return false;
      } else {
         return isControlled(npc) ? true : AdapterRegistry.find(ItemStackWrapper.MCItem(npc.inventory.getRightHand())) != null;
      }
   }

   public static void tick(EntityNPCInterface npc) {
      if (validNpc(npc) && AdapterRegistry.ready() && !npc.isPassenger()) {
         NpcData state = data(npc);
         state.throwItems.onAlive();
         if (state.runtime.editorUntil <= npc.level().getGameTime()) {
            if (state.runtime.editorPaused) {
               endEditing(new CombatContext(npc));
            } else if (state.runtime.restoreSupplies) {
               restoreDefaults(new CombatContext(npc));
            }

            if (!state.config.bool("combat.allowTargetChange")
               && state.runtime.targetId != null
               && (npc.getTarget() == null || !state.runtime.targetId.equals(npc.getTarget().getUUID()))
               && ((ServerLevel)npc.level()).getEntity(state.runtime.targetId) instanceof LivingEntity living
               && validTarget(npc, living)) {
               npc.setTarget(living);
            }

            CombatContext ctx = new CombatContext(npc);
            if (ctx.runtime.lastTick != ctx.now) {
               ctx.runtime.lastTick = ctx.now;
               refresh(ctx);
               ctx.runtime
                  .recoil
                  .advance(
                     ctx.now,
                     ctx.runtime.adapter instanceof GunAdapter
                        && ctx.config.bool("combat.enabled")
                        && ctx.config.bool("gun.enabled")
                        && ctx.config.bool("gun.applyRecoil")
                        && !ctx.runtime.editorPaused
                  );
               ctx.runtime.scheduler.expire(ctx.now);
               AmmoSupply.pickup(ctx);
               CombatMovement.prepare(ctx);
               if (!ctx.config.bool("combat.enabled")) {
                  ctx.data.throwing.cancel();
                  if (!ctx.runtime.combatState.equals("DISABLED")) {
                     if (ctx.runtime.adapter != null) {
                        AdapterRegistry.run(ctx.runtime.adapter, () -> ctx.runtime.adapter.cancel(ctx));
                     }

                     ctx.data.blade.reset();
                     ctx.runtime.scheduler.clear();
                     ctx.npc.getNavigation().stop();
                     ctx.runtime.reloading = false;
                     ctx.runtime.reloadTargetTicks = 0;
                     ctx.runtime.reloadObserved = false;
                  }

                  setState(ctx, "DISABLED");
                  EventBridge.flush(ctx);
               } else {
                  if (ctx.runtime.adapter != null) {
                     AdapterRegistry.run(ctx.runtime.adapter, () -> ctx.runtime.adapter.tick(ctx));
                  }

                  if (ctx.runtime.adapter != null && !AdapterRegistry.loaded(ctx.runtime.adapter.id())) {
                     refresh(ctx);
                  }

                  UUID target = ctx.validTarget() ? ctx.target.getUUID() : null;
                  if (!Objects.equals(target, ctx.runtime.targetId)) {
                     ctx.runtime.targetId = target;
                     ctx.runtime.firstTargetTick = ctx.now;
                     ctx.runtime.burstRemaining = 0;
                     ctx.runtime.shotStreak = 0;
                     ctx.runtime.lastFired = -1000L;
                     ctx.runtime.recoil.reset();
                     ctx.runtime.pendingShotTiming = null;
                     ctx.runtime.nextFireAttempt = 0L;
                     ctx.runtime.lastShotInterval = 0;
                     if (target != null) {
                        ctx.runtime.fireStopped = false;
                     }

                     ctx.runtime.nextShot = ctx.now + ctx.config.integer("gun.firstDelay") + ctx.config.integer("gun.windup");
                     EventBridge.emit(ctx, "combat.target_changed", "", Result.success());
                  }

                  if (ctx.target != null && !ctx.validTarget()) {
                     npc.setTarget(null);
                  }

                  if (!ctx.validTarget()) {
                     setState(ctx, "IDLE");
                     if (ctx.config.bool("combat.stopOnInvalidTarget") && ctx.runtime.scheduler.interruptible && ctx.runtime.scheduler.channel.equals("GUN")) {
                        ctx.data.gun.stop(ctx);
                     }
                  }

                  if (ctx.now >= ctx.runtime.noAmmoRetry && ctx.runtime.noAmmoEvent) {
                     long fingerprint = new AmmoInventory(npc).fingerprint();
                     if (fingerprint != ctx.runtime.inventoryFingerprint) {
                        ctx.runtime.noAmmoRetry = 0L;
                        ctx.runtime.inventoryFingerprint = fingerprint;
                     }
                  }

                  if (!ctx.runtime.command.equals("AUTO") || !ctx.config.bool("weapon.autoSwitch")) {
                     ctx.runtime.movement.ammoFallback = false;
                     ctx.runtime.movement.pursueSlot = -1;
                  }

                  if (ctx.runtime.command.equals("AUTO")
                     && ctx.config.bool("weapon.autoSwitch")
                     && ctx.validTarget()
                     && (
                        ctx.runtime.movement.ammoFallback
                           || ctx.runtime.movement.pursueSlot >= 0
                           || ctx.runtime.adapter instanceof GunAdapter && !currentGunUsable(ctx)
                     )) {
                     int before = ctx.data.armory.active;
                     pursueMelee(ctx);
                     if (before != ctx.data.armory.active) {
                        invalidateDecision(ctx);
                        EventBridge.flush(ctx);
                        return;
                     }
                  }

                  ctx.data.throwing.tick(ctx);
                  pollDecision(ctx);
                  queueDecision(ctx);
                  boolean decisionDue = ctx.now >= ctx.runtime.nextDecision;
                  if (decisionDue
                     && ctx.runtime.command.equals("AUTO")
                     && ctx.config.bool("weapon.autoSwitch")
                     && ctx.validTarget()
                     && !ctx.runtime.movement.ammoFallback
                     && !ctx.runtime.scheduler.busy(ctx.now)
                     && !ctx.runtime.reloading
                     && !ctx.data.throwing.blocksAttack(ctx.now)) {
                     int before = ctx.data.armory.active;
                     switchBest(ctx, false);
                     if (before != ctx.data.armory.active) {
                        invalidateDecision(ctx);
                        EventBridge.flush(ctx);
                        return;
                     }
                  }

                  int beforeAction = ctx.data.armory.active;
                  if (ctx.runtime.adapter instanceof GunAdapter) {
                     ctx.data.gun.tick(ctx);
                  } else if (ctx.runtime.adapter instanceof BladeAdapter) {
                     ctx.data.blade.tick(ctx);
                  } else {
                     CombatMovement.melee(ctx);
                  }

                  if (beforeAction != ctx.data.armory.active) {
                     invalidateDecision(ctx);
                     EventBridge.flush(ctx);
                  } else {
                     if (ctx.data.throwing.blocksAttack(ctx.now)) {
                        setState(ctx, "THROWING");
                     }

                     if (!ctx.runtime.command.equals("AUTO")) {
                        if (ctx.runtime.command.equals("RETREAT")) {
                           if (ctx.now >= ctx.runtime.nextDecision) {
                              retreat(ctx);
                           }
                        } else {
                           npc.getNavigation().stop();
                           setState(ctx, "WAITING");
                        }

                        ctx.runtime.nextDecision = Math.max(ctx.runtime.nextDecision, ctx.now + ctx.config.integer("combat.decisionInterval"));
                        EventBridge.flush(ctx);
                     } else {
                        if (decisionDue) {
                           ctx.runtime.nextDecision = ctx.now + decisionInterval(ctx);
                        }

                        CombatMovement.tick(ctx);
                        CombatMovement.face(ctx.npc);
                        EventBridge.flush(ctx);
                     }
                  }
               }
            }
         }
      }
   }

   public static void refresh(CombatContext ctx) {
      WeaponAdapter current = AdapterRegistry.find(ctx.weapon);
      String id = current == null ? "" : AdapterRegistry.query(current, () -> current.weaponId(ctx.weapon), "");
      if (current == ctx.runtime.adapter && id.equals(ctx.runtime.weaponId) && ctx.weapon == ctx.runtime.lastStack) {
         if (ctx.now >= ctx.runtime.nextCapabilityCheck) {
            String prior = SafeJson.GSON.toJson(capabilities(ctx));
            updateCapabilities(ctx, current);
            ctx.runtime.nextCapabilityCheck = ctx.now + ctx.config.integer("combat.decisionInterval");
            if (!prior.equals(SafeJson.GSON.toJson(capabilities(ctx)))) {
               EventBridge.emit(ctx, "weapon.adapter_changed", "", Result.success());
            }
         }
      } else {
         WeaponAdapter old = ctx.runtime.adapter;
         ctx.data.throwing.cancel();
         ctx.runtime.weaponEpoch++;
         invalidateDecision(ctx);
         if (old != null) {
            AdapterRegistry.cancel(old, ctx.withWeapon(ctx.runtime.lastStack));
         }

         ctx.data.blade.reset();
         ctx.runtime.adapterState.clear();
         ctx.runtime.recoil.reset();
         ctx.runtime.burstRemaining = 0;
         ctx.runtime.shotInterval = 0;
         ctx.runtime.pendingShotTiming = null;
         ctx.runtime.nextFireAttempt = 0L;
         ctx.runtime.lastShotInterval = 0;
         ctx.runtime.firingSettings = null;
         ctx.runtime.firingRevision = -1L;
         ctx.runtime.lastFired = -1000L;
         ctx.runtime.nextShot = ctx.now + ctx.config.integer("gun.firstDelay") + ctx.config.integer("gun.windup");
         if (!ctx.runtime.scheduler.channel.equals("SWITCH") || !ctx.runtime.scheduler.busy(ctx.now)) {
            ctx.runtime.scheduler.clear();
         }

         ctx.runtime.actionId = "";
         ctx.runtime.comboId = "";
         ctx.runtime.comboNode = "";
         ctx.runtime.lastBladeAction = "";
         ctx.runtime.gunAnimationUntil = 0L;
         ctx.runtime.adapter = current;
         ctx.runtime.weaponId = id;
         ctx.runtime.lastStack = ctx.weapon;
         updateCapabilities(ctx, current);
         ctx.runtime.nextCapabilityCheck = ctx.now + ctx.config.integer("combat.decisionInterval");
         ctx.runtime.domain = current == null ? (melee(ctx.weapon) ? "MELEE" : "UNKNOWN") : current.domain();
         if (current != null || !melee(ctx.weapon)) {
            ctx.runtime.movement.meleeFallback = false;
         }

         ctx.runtime.reloading = false;
         ctx.runtime.reloadTargetTicks = 0;
         ctx.runtime.reloadObserved = false;
         ctx.runtime.ammoEmpty = false;
         ctx.runtime.noAmmoEvent = false;
         ctx.runtime.noAmmoRetry = 0L;
         ctx.runtime.fireStopped = false;
         ctx.runtime.shotStreak = 0;
         if (current != null) {
            AdapterRegistry.run(current, () -> current.onEquip(ctx));
         }

         EventBridge.emit(ctx, "weapon.changed", "", Result.success());
         if (old != current) {
            EventBridge.emit(ctx, "weapon.adapter_changed", "", Result.success());
         }
      }
   }

   private static void updateCapabilities(CombatContext ctx, WeaponAdapter current) {
      ctx.runtime.gunCaps = current instanceof GunAdapter gun ? AdapterRegistry.query(gun, () -> gun.capabilities(ctx.weapon), null) : null;
      ctx.runtime.bladeCaps = current instanceof BladeAdapter blade ? AdapterRegistry.query(blade, () -> blade.capabilities(ctx.weapon), null) : null;
   }

   public static boolean melee(ItemStack stack) {
      return stack != null && (stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem || stack.getItem() instanceof TridentItem);
   }

   public static Views.WeaponInfo weaponInfo(ItemStack stack, String forcedId) {
      if (stack != null && !stack.isEmpty()) {
         WeaponAdapter adapter = AdapterRegistry.find(stack);
         String registry = String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
         String id = forcedId != null ? forcedId : (adapter == null ? registry : AdapterRegistry.query(adapter, () -> adapter.weaponId(stack), registry));
         return new Views.WeaponInfo(
            id,
            adapter == null ? (melee(stack) ? "MELEE" : "UNKNOWN") : adapter.domain(),
            registry,
            stack.getHoverName().getString(),
            adapter == null ? "" : adapter.id(),
            adapter != null || melee(stack)
         );
      } else {
         return null;
      }
   }

   public static boolean hasCapability(CombatContext ctx, String id) {
      return ctx.runtime.gunCaps != null && ctx.runtime.gunCaps.has(id) || ctx.runtime.bladeCaps != null && ctx.runtime.bladeCaps.has(id);
   }

   public static Views.Capabilities capabilities(CombatContext ctx) {
      return new Views.Capabilities(
         ctx.runtime.domain,
         ctx.runtime.gunCaps == null ? null : ctx.runtime.gunCaps.copy(),
         ctx.runtime.bladeCaps == null ? null : (Views.BladeCaps)SafeJson.GSON.fromJson(SafeJson.GSON.toJson(ctx.runtime.bladeCaps), Views.BladeCaps.class),
         !ctx.runtime.scheduler.busy(ctx.now)
      );
   }

   public static Result setConfig(CombatContext ctx, String key, Object value) {
      Parameter parameter = ConfigSchema.get(key);
      if (parameter == null) {
         return Result.fail("NOT_FOUND");
      } else if (!parameter.capability.isEmpty() && !EditorWeapons.has(ctx, parameter.capability)) {
         return Result.fail("UNSUPPORTED");
      } else {
         try {
            JsonElement checked = parameter.validate(SafeJson.copy(value));
            if (key.equals("slashblade.preset")) {
               return Presets.apply(ctx, checked.getAsString());
            } else if (key.equals("gun.firingMode") && !EditorWeapons.firingMode(ctx, checked.getAsString())) {
               return Result.fail("UNSUPPORTED");
            } else {
               NpcConfig prospective = new NpcConfig(ctx.config.snapshot());
               prospective.set(key, checked);
               validateRanges(prospective);
               ctx.config.set(key, checked);
               if (key.equals("gun.enabled") || key.equals("gun.autoAttack")) {
                  ctx.runtime.fireStopped = false;
               }

               ctx.runtime.noAmmoRetry = 0L;
               return Result.success(SafeJson.plain(checked));
            }
         } catch (IllegalStateException | IllegalArgumentException var6) {
            return Result.fail("INVALID_ARGUMENT", var6.getMessage());
         }
      }
   }

   public static Result setConfigs(CombatContext ctx, JsonObject values) {
      if (values != null && values.size() <= 32 && !values.has("slashblade.preset")) {
         NpcConfig candidate = new NpcConfig(ctx.config.snapshot());

         try {
            for (Entry<String, JsonElement> entry : values.entrySet()) {
               Parameter p = ConfigSchema.get(entry.getKey());
               if (p == null) {
                  return Result.fail("NOT_FOUND");
               }

               if (!p.capability.isEmpty() && !EditorWeapons.has(ctx, p.capability)) {
                  return Result.fail("UNSUPPORTED");
               }

               JsonElement value = p.validate(entry.getValue());
               if (p.id.equals("gun.firingMode") && !EditorWeapons.firingMode(ctx, value.getAsString())) {
                  return Result.fail("UNSUPPORTED");
               }

               candidate.set(p.id, value);
            }

            validateRanges(candidate);
            ctx.config.replace(candidate.snapshot());
            if (values.has("gun.enabled") || values.has("gun.autoAttack")) {
               ctx.runtime.fireStopped = false;
            }

            ctx.runtime.noAmmoRetry = 0L;
            return Result.success();
         } catch (IllegalStateException | IllegalArgumentException var7) {
            return Result.fail("INVALID_ARGUMENT");
         }
      } else {
         return Result.fail("INVALID_ARGUMENT");
      }
   }

   public static void validateRanges(NpcConfig config) {
      if (!(config.number("gun.minRange") > config.number("gun.range"))
         && config.integer("gun.minInterval") <= config.integer("gun.maxInterval")
         && config.integer("gun.reloadMinTicks") <= config.integer("gun.reloadMaxTicks")
         && config.integer("ammo.minimumReserve") <= config.integer("ammo.reserveLimit")) {
         for (int i = 0; i < 6; i++) {
            if (config.bool("weapon.slot" + i + ".minRangeEnabled")
               && config.bool("weapon.slot" + i + ".maxRangeEnabled")
               && config.number("weapon.slot" + i + ".minRange") > config.number("weapon.slot" + i + ".maxRange")) {
               throw new IllegalArgumentException("slot range");
            }
         }

         for (int ix = 0; ix < 3; ix++) {
            if (config.number("throw.slot" + ix + ".minRange") > config.number("throw.slot" + ix + ".maxRange")) {
               throw new IllegalArgumentException("throw range");
            }
         }
      } else {
         throw new IllegalArgumentException("invalid range");
      }
   }

   public static Result switchBest(CombatContext ctx, boolean onlyMelee) {
      return switchBest(ctx, onlyMelee, false);
   }

   public static boolean usableGun(CombatContext ctx) {
      for (int slot = 0; slot < 6; slot++) {
         ItemStack stack = ctx.data.armory.get(ctx.npc, slot);
         if (AdapterRegistry.find(stack) instanceof GunAdapter gun && usableGun(ctx, gun, stack)) {
            return true;
         }
      }

      return false;
   }

   public static boolean currentGunUsable(CombatContext ctx) {
      return ctx.runtime.adapter instanceof GunAdapter gun && usableGun(ctx, gun, ctx.weapon);
   }

   private static boolean usableGun(CombatContext ctx, GunAdapter gun, ItemStack stack) {
      Views.GunCaps caps = AdapterRegistry.query(gun, () -> gun.capabilities(stack), null);
      if (caps != null && caps.canFire) {
         CombatContext view = ctx.withWeapon(stack);
         Views.AmmoState ammo = AdapterRegistry.query(gun, () -> gun.ammo(view), null);
         if (stack == ctx.weapon && ammo != null && ammo.isReloading) {
            return true;
         } else if (!AdapterRegistry.query(gun, () -> gun.canUse(view, stack), false)) {
            return false;
         } else {
            int cost = AdapterRegistry.query(gun, () -> gun.ammoPerShot(stack), 1);
            return ammo != null && !ReloadPolicy.hasLoadedShot(ammo.hasMagazine, ammo.magazineAmmo, cost, ctx.config.bool("ammo.infinite"))
               ? ammo.canReload
                  && (
                     ctx.config.bool("ammo.partialReload")
                        || ammo.reserveAmmo == null
                        || ammo.magazineCapacity == null
                        || ammo.reserveAmmo >= ammo.magazineCapacity - ammo.magazineAmmo
                  )
               : true;
         }
      } else {
         return false;
      }
   }

   public static Result pursueMelee(CombatContext ctx) {
      if (ctx != null
         && serverThread(ctx.npc)
         && validNpc(ctx.npc)
         && ctx.validTarget()
         && !ctx.runtime.editorPaused
         && ctx.runtime.command.equals("AUTO")
         && ctx.config.bool("weapon.autoSwitch")) {
         MovementState movement = ctx.runtime.movement;
         if (usableGun(ctx)) {
            if (movement.ammoFallback) {
               invalidateDecision(ctx);
            }

            movement.ammoFallback = false;
            movement.pursueSlot = -1;
            return Result.fail("CONDITION_NOT_MET");
         } else {
            List<CombatDecisions.Weapon> choices = new ArrayList<>();

            for (int slot = 0; slot < 6; slot++) {
               ItemStack stack = ctx.data.armory.get(ctx.npc, slot);
               WeaponAdapter adapter = AdapterRegistry.find(stack);
               if (!stack.isEmpty() && !(adapter instanceof GunAdapter) && (adapter instanceof BladeAdapter || melee(stack))) {
                  if (adapter instanceof BladeAdapter blade) {
                     Views.BladeCaps caps = AdapterRegistry.query(blade, () -> blade.capabilities(stack), null);
                     if (!ctx.config.bool("slashblade.enabled") || caps == null || !caps.canAttack) {
                        continue;
                     }
                  }

                  String key = weaponKey(stack, adapter);
                  if (key.length() <= 256) {
                     choices.add(new CombatDecisions.Weapon(slot, key, true, false, -1, -1, ctx.config.integer("weapon.slot" + slot + ".priority")));
                  }
               }
            }

            int best = CombatDecisions.meleeFallback(choices, ctx.data.armory.active);
            if (best < 0) {
               movement.ammoFallback = false;
               movement.pursueSlot = -1;
               return Result.fail("NO_WEAPON");
            } else {
               if (!movement.ammoFallback) {
                  invalidateDecision(ctx);
                  movement.nextMove = ctx.now;
                  if (ctx.runtime.adapter instanceof GunAdapter) {
                     Views.AmmoState ammo = ctx.data.gun.ammo(ctx);
                     GunAdapter gun = (GunAdapter)ctx.runtime.adapter;
                     int cost = AdapterRegistry.query(gun, () -> gun.ammoPerShot(ctx.weapon), 1);
                     if (!ctx.runtime.ammoEmpty
                        && ammo != null
                        && !ReloadPolicy.hasLoadedShot(ammo.hasMagazine, ammo.magazineAmmo, cost, ctx.config.bool("ammo.infinite"))) {
                        ctx.runtime.ammoEmpty = true;
                        EventBridge.emit(ctx, "gun.ammo_empty", "", Result.success());
                     }

                     if (!ctx.runtime.noAmmoEvent) {
                        ctx.runtime.noAmmoEvent = true;
                        EventBridge.emit(ctx, "gun.no_ammo", "", Result.fail("NO_AMMO"));
                     }
                  }
               }

               movement.ammoFallback = true;
               boolean approaching = ctx.distance > CombatMovement.meleeReach(ctx);
               movement.pursueSlot = !approaching && best == ctx.data.armory.active ? -1 : best;
               if (best != ctx.data.armory.active || approaching) {
                  ctx.data.throwing.cancel();
               }

               Result switched = Result.success();
               if (best != ctx.data.armory.active) {
                  if (ctx.runtime.scheduler.busy(ctx.now)) {
                     if (!ctx.runtime.scheduler.channel.equals("GUN") || !ctx.runtime.scheduler.interruptible) {
                        return Result.fail("BUSY");
                     }

                     ctx.runtime.scheduler.release();
                  }

                  ctx.runtime.nextSwitch = ctx.now;
                  ctx.npc.getNavigation().stop();
                  ctx.npc.getMoveControl().strafe(0.0F, 0.0F);
                  ctx.npc.xxa = 0.0F;
                  ctx.npc.zza = 0.0F;
                  switched = ctx.data.armory.switchTo(ctx, best);
                  if (!switched.ok) {
                     return switched;
                  }

                  movement.meleeFallback = !(AdapterRegistry.find(ctx.data.armory.get(ctx.npc, best)) instanceof BladeAdapter);
                  movement.pursueSlot = approaching ? best : -1;
               }

               if (approaching) {
                  if (ctx.now >= movement.nextMove) {
                     CombatMovement.chase(ctx);
                     movement.nextMove = ctx.now + 6L;
                  }

                  setState(ctx, "APPROACHING");
               } else if (!ctx.data.throwing.blocksAttack(ctx.now) && ctx.runtime.combatState.equals("THROWING")) {
                  setState(ctx, "WAITING");
               }

               return switched;
            }
         }
      } else {
         return Result.fail("PERMISSION_DENIED");
      }
   }

   public static Result switchBest(CombatContext ctx, boolean onlyMelee, boolean requireLoaded) {
      if (!serverThread(ctx.npc) || !validNpc(ctx.npc) || ctx.runtime.editorPaused) {
         return Result.fail("PERMISSION_DENIED");
      } else if (ctx.runtime.scheduler.busy(ctx.now) || ctx.data.throwing.blocksAttack(ctx.now)) {
         return Result.fail("BUSY");
      } else if (!ctx.validTarget()) {
         return Result.fail("INVALID_TARGET");
      } else {
         CombatDecisions.Advice choice = !onlyMelee && !requireLoaded ? advice(ctx) : null;
         if (choice == null || !candidate(ctx, choice.slot(), onlyMelee, requireLoaded, choice.weapon())) {
            choice = CombatDecisions.calculate(snapshot(ctx, onlyMelee, requireLoaded));
         }

         return choice.slot() >= 0 && candidate(ctx, choice.slot(), onlyMelee, requireLoaded, choice.weapon())
            ? ctx.data.armory.switchTo(ctx, choice.slot())
            : Result.fail("NO_WEAPON");
      }
   }

   private static String weaponKey(ItemStack stack, WeaponAdapter adapter) {
      return adapter == null
         ? String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()))
         : adapter.id() + ":" + AdapterRegistry.query(adapter, () -> adapter.weaponId(stack), "");
   }

   private static boolean candidate(CombatContext ctx, int slot, boolean onlyMelee, boolean requireLoaded, String key) {
      if (slot >= 0 && slot < 6 && slotEligible(ctx, slot)) {
         ItemStack stack = ctx.data.armory.get(ctx.npc, slot);
         if (stack.isEmpty()) {
            return false;
         } else {
            WeaponAdapter adapter = AdapterRegistry.find(stack);
            boolean melee = !(adapter instanceof GunAdapter) && (adapter instanceof BladeAdapter || melee(stack));
            if ((!onlyMelee || melee) && (adapter != null || melee) && weaponKey(stack, adapter).equals(key)) {
               if (adapter instanceof GunAdapter gun) {
                  if (!usableGun(ctx, gun, stack)) {
                     return false;
                  }

                  Views.GunCaps caps = AdapterRegistry.query(gun, () -> gun.capabilities(stack), null);
                  if (caps == null || !caps.canFire) {
                     return false;
                  }

                  if (requireLoaded && caps.hasMagazine && caps.canReadAmmo && !ctx.config.bool("ammo.infinite")) {
                     int rounds = AdapterRegistry.query(gun, () -> gun.loadedRounds(stack), 0);
                     int cost = AdapterRegistry.query(gun, () -> gun.ammoPerShot(stack), 1);
                     if (!ReloadPolicy.hasLoadedShot(true, rounds, cost, false)) {
                        return false;
                     }
                  }
               }

               return !requireLoaded || !melee || ctx.distance <= Math.max(4.0, ctx.config.number("weapon.meleeDistance"));
            } else {
               return false;
            }
         }
      } else {
         return false;
      }
   }

   private static CombatDecisions.Snapshot snapshot(CombatContext ctx, boolean onlyMelee, boolean requireLoaded) {
      List<CombatDecisions.Weapon> choices = new ArrayList<>();

      for (int i = 0; i < 6; i++) {
         ItemStack stack = ctx.data.armory.get(ctx.npc, i);
         if (!stack.isEmpty()) {
            WeaponAdapter adapter = AdapterRegistry.find(stack);
            boolean melee = !(adapter instanceof GunAdapter) && (adapter instanceof BladeAdapter || melee(stack));
            if ((!onlyMelee || melee)
               && (adapter != null || melee)
               && !(adapter instanceof GunAdapter gun && !usableGun(ctx, gun, stack))
               && slotEligible(ctx, i)) {
               int loaded = -1;
               int capacity = -1;
               if (adapter instanceof GunAdapter gunx) {
                  Views.GunCaps caps = AdapterRegistry.query(gunx, () -> gunx.capabilities(stack), null);
                  if (caps == null || !caps.canFire) {
                     continue;
                  }

                  if (caps.hasMagazine && caps.canReadAmmo) {
                     loaded = Math.max(-1, Math.min(100000, AdapterRegistry.query(gunx, () -> gunx.loadedRounds(stack), -1)));
                  }

                  capacity = Math.max(-1, Math.min(100000, AdapterRegistry.query(gunx, () -> gunx.magazineCapacity(stack), -1)));
                  int cost = AdapterRegistry.query(gunx, () -> gunx.ammoPerShot(stack), 1);
                  if (loaded >= 0 && !ReloadPolicy.hasLoadedShot(true, loaded, cost, ctx.config.bool("ammo.infinite"))) {
                     loaded = 0;
                  }
               }

               String key = weaponKey(stack, adapter);
               if (key.length() <= 256) {
                  choices.add(
                     new CombatDecisions.Weapon(
                        i, key, melee, adapter instanceof GunAdapter, loaded, capacity, ctx.config.integer("weapon.slot" + i + ".priority")
                     )
                  );
               }
            }
         }
      }

      return new CombatDecisions.Snapshot(
         ctx.npc.getUUID(),
         ctx.target.getUUID(),
         ctx.npc.level().dimension().location().toString(),
         ctx.now,
         ctx.config.revision,
         ctx.runtime.weaponEpoch,
         ctx.data.armory.active,
         ctx.runtime.weaponId,
         ctx.distance,
         Math.max(0.0F, Math.min(1.0F, ctx.npc.getHealth() / Math.max(1.0F, ctx.npc.getMaxHealth()))),
         Math.max(0.0F, Math.min(1.0F, ctx.target.getHealth() / Math.max(1.0F, ctx.target.getMaxHealth()))),
         Math.min(16, Math.max(1, ctx.targets().size())),
         ctx.config.number("weapon.meleeDistance"),
         ctx.config.number("weapon.lowHealth"),
         ctx.now - ctx.runtime.lastHurt <= 8L,
         ctx.config.bool("ammo.infinite"),
         onlyMelee,
         requireLoaded,
         choices
      );
   }

   private static int decisionInterval(CombatContext ctx) {
      return ctx.runtime.adapter instanceof BladeAdapter && ctx.config.text("slashblade.preset").equals("MAID")
         ? Math.min(4, ctx.config.integer("combat.decisionInterval"))
         : ctx.config.integer("combat.decisionInterval");
   }

   public static CombatDecisions.Advice advice(CombatContext ctx) {
      CombatDecisions.Advice advice = ctx.runtime.decisionAdvice;
      return advice != null
            && advice.currentGeneration()
            && serverThread(ctx.npc)
            && validNpc(ctx.npc)
            && ctx.validTarget()
            && !ctx.runtime.editorPaused
            && ctx.runtime.command.equals("AUTO")
            && ctx.config.bool("combat.enabled")
            && advice.snapshot()
               .matches(
                  ctx.npc.getUUID(),
                  ctx.target.getUUID(),
                  ctx.npc.level().dimension().location().toString(),
                  ctx.now,
                  ctx.config.revision,
                  ctx.runtime.weaponEpoch,
                  ctx.data.armory.active,
                  ctx.runtime.weaponId
               )
            && !(Math.abs(advice.snapshot().distance() - ctx.distance) > Math.max(2.0, ctx.distance * 0.25))
         ? advice
         : null;
   }

   private static void pollDecision(CombatContext ctx) {
      if (ctx.runtime.decisionTicket != null && ctx.runtime.decisionTicket.finished()) {
         ctx.runtime.decisionAdvice = ctx.runtime.decisionTicket.advice();
         ctx.runtime.decisionTicket = null;
         if (advice(ctx) == null) {
            ctx.runtime.decisionAdvice = null;
         }
      }
   }

   private static void queueDecision(CombatContext ctx) {
      if (ctx.runtime.command.equals("AUTO")
         && ctx.validTarget()
         && ctx.runtime.decisionTicket == null
         && ctx.runtime.decisionCycle != ctx.runtime.nextDecision
         && ctx.now >= ctx.runtime.nextDecision - 2L) {
         ctx.runtime.decisionCycle = ctx.runtime.nextDecision;
         if (ctx.config.bool("weapon.autoSwitch") || ctx.runtime.adapter instanceof BladeAdapter && ctx.config.text("slashblade.preset").equals("MAID")) {
            CombatDecisions.Snapshot snapshot = snapshot(ctx, false, false);
            if (!snapshot.weapons().isEmpty()) {
               ctx.runtime.decisionTicket = CombatDecisions.submit(snapshot);
            }
         }
      }
   }

   private static void invalidateDecision(CombatContext ctx) {
      if (ctx.runtime.decisionTicket != null) {
         ctx.runtime.decisionTicket.cancel();
      }

      ctx.runtime.decisionTicket = null;
      ctx.runtime.decisionAdvice = null;
      ctx.runtime.decisionCycle = Long.MIN_VALUE;
   }

   public static boolean slotEligible(CombatContext ctx, int slot) {
      String key = "weapon.slot" + slot + ".";
      double health = ctx.npc.getHealth() / Math.max(1.0F, ctx.npc.getMaxHealth());
      return (!ctx.config.bool(key + "maxHealthEnabled") || health <= ctx.config.number(key + "maxHealth"))
         && (
            !ctx.validTarget()
               || (!ctx.config.bool(key + "minRangeEnabled") || ctx.distance >= ctx.config.number(key + "minRange"))
                  && (!ctx.config.bool(key + "maxRangeEnabled") || ctx.distance <= ctx.config.number(key + "maxRange"))
         );
   }

   public static void beginEditing(CombatContext ctx, long until) {
      invalidateDecision(ctx);
      ctx.data.throwing.cancel();
      CombatMovement.release(ctx);
      if (!ctx.runtime.editorPaused) {
         if (ctx.runtime.adapter != null) {
            AdapterRegistry.cancel(ctx.runtime.adapter, ctx);
         }

         ctx.data.blade.reset();
         ctx.runtime.scheduler.clear();
         ctx.npc.getNavigation().stop();
         ctx.runtime.reloading = false;
         ctx.runtime.reloadTargetTicks = 0;
         ctx.runtime.reloadObserved = false;
         ctx.runtime.aiming = false;
         ctx.runtime.editorPaused = true;
      }

      ctx.runtime.editorUntil = until;
   }

   public static void endEditing(CombatContext ctx) {
      if (serverThread(ctx.npc)) {
         ctx.runtime.editorUntil = 0L;
         ctx.runtime.editorPaused = false;
         restoreDefaults(ctx);
      }
   }

   private static void restoreDefaults(CombatContext ctx) {
      ctx.data.throwing.cancel();
      if (ctx.runtime.adapter != null) {
         AdapterRegistry.cancel(ctx.runtime.adapter, ctx);
      }

      ctx.data.blade.reset();
      ctx.runtime.scheduler.clear();
      ctx.runtime.nextSwitch = 0L;
      ctx.data.armory.restorePrimary(ctx);
      CombatContext current = new CombatContext(ctx.npc);
      refresh(current);
      AmmoSupply.defaults(current);
      ctx.runtime.fireStopped = false;
      ctx.runtime.command = "AUTO";
      ctx.runtime.noAmmoCommand = "";
      ctx.runtime.noAmmoRetry = 0L;
   }

   public static boolean hostile(EntityNPCInterface npc, LivingEntity target) {
      if (validTarget(npc, target) && (!target.isInvisible() || npc.ais.attackInvisible)) {
         if (target == npc.getTarget() || target instanceof Mob mob && mob.getTarget() == npc) {
            return true;
         } else if (npc.getFaction() == null) {
            return false;
         } else if (target instanceof EntityNPCInterface other) {
            return npc.getFaction().isAggressiveToNpc(other);
         } else {
            return target instanceof Player player ? npc.getFaction().isAggressiveToPlayer(player) : npc.getFaction().getsAttacked && target instanceof Enemy;
         }
      } else {
         return false;
      }
   }

   public static void rewardKill(EntityNPCInterface owner, LivingEntity victim) {
      if (serverThread(owner) && validNpc(owner) && victim != null && owner != victim && victim.level() == owner.level()) {
         RuntimeState runtime = data(owner).runtime;
         long now = owner.level().getGameTime();
         if (!Objects.equals(runtime.killAwards.get(victim.getUUID()), now)) {
            runtime.killAwards.put(victim.getUUID(), now);
            if (runtime.killAwards.size() > 128) {
               runtime.killAwards.remove(runtime.killAwards.keySet().iterator().next());
            }

            AmmoSupply.kill(new CombatContext(owner));
         }
      }
   }

   public static Result command(CombatContext ctx, String command) {
      if (!Set.of("AUTO", "HOLD", "RETREAT").contains(command)) {
         return Result.fail("INVALID_ARGUMENT");
      } else if (command.equals("RETREAT") && !ctx.validTarget()) {
         return Result.fail("INVALID_TARGET");
      } else if (ctx.runtime.scheduler.busy(ctx.now) && !ctx.runtime.scheduler.interruptible) {
         return Result.fail("BUSY");
      } else {
         if (ctx.runtime.scheduler.busy(ctx.now) && ctx.runtime.adapter instanceof BladeAdapter) {
            JsonObject phase = ctx.data.blade.state(ctx);
            if (phase.has("canInterrupt") && !phase.get("canInterrupt").getAsBoolean()) {
               return Result.fail("BUSY");
            }
         }

         ctx.runtime.noAmmoCommand = "";
         if (!command.equals("AUTO")) {
            ctx.data.throwing.cancel();
            ctx.data.blade.stopCombo(ctx, "command");
            if (ctx.runtime.adapter != null) {
               AdapterRegistry.run(ctx.runtime.adapter, () -> ctx.runtime.adapter.cancel(ctx));
            }

            ctx.runtime.scheduler.clear();
         }

         ctx.runtime.command = command;
         ctx.runtime.fireStopped = !command.equals("AUTO");
         if (command.equals("RETREAT")) {
            return retreat(ctx);
         } else {
            if (command.equals("HOLD")) {
               ctx.npc.getNavigation().stop();
               setState(ctx, "WAITING");
            }

            return Result.success();
         }
      }
   }

   public static Result retreat(CombatContext ctx) {
      if (!ctx.validTarget()) {
         return Result.fail("INVALID_TARGET");
      } else if (ctx.runtime.scheduler.busy(ctx.now) && !ctx.runtime.scheduler.interruptible) {
         return Result.fail("BUSY");
      } else {
         Vec3 direction = ctx.npc.position().subtract(ctx.target.position()).normalize();
         Vec3 position = ctx.npc.position().add(direction.scale(8.0));
         ctx.npc.getNavigation().moveTo(position.x, position.y, position.z, 1.1);
         setState(ctx, "RETREATING");
         return Result.success();
      }
   }

   public static void setState(CombatContext ctx, String state) {
      if (!ctx.runtime.combatState.equals(state)) {
         ctx.runtime.combatState = state;
         EventBridge.emit(ctx, "combat.state_changed", "", Result.success(state));
      }
   }
}
