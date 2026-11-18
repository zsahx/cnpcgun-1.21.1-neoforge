package bincnpc.gun.common.script;

import bincnpc.gun.common.adapter.AdapterRegistry;
import bincnpc.gun.common.api.Result;
import bincnpc.gun.common.api.SafeJson;
import bincnpc.gun.common.api.Views;
import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.common.combat.CombatService;
import bincnpc.gun.common.config.ConfigSchema;
import bincnpc.gun.common.persistence.PresetLibrary;
import bincnpc.gun.gun.adapter.GunAdapter;
import bincnpc.gun.slashblade.action.ActionConfig;
import bincnpc.gun.slashblade.action.ActionRegistry;
import bincnpc.gun.slashblade.adapter.BladeAdapter;
import bincnpc.gun.slashblade.combo.Conditions;
import bincnpc.gun.slashblade.strategy.Presets;
import com.google.gson.JsonObject;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.entity.EntityNPCInterface;

public final class ScriptApi {
   public String getApiVersion() {
      return "1";
   }

   public String[] getSupportedDomains() {
      Set<String> domains = new LinkedHashSet<>();

      for (String id : AdapterRegistry.ids()) {
         if (id.equals("slashblade")) {
            domains.add("SLASHBLADE");
         } else {
            domains.add("GUN");
         }
      }

      return domains.toArray(String[]::new);
   }

   public boolean isAdapterLoaded(String id) {
      return AdapterRegistry.loaded(id);
   }

   public String[] getLoadedAdapters() {
      return AdapterRegistry.ids();
   }

   public String[] getConditions() {
      return Conditions.ids();
   }

   public ScriptApi.NpcProxy get(Object npc) {
      Entity entity = unwrap(npc);
      return new ScriptApi.NpcProxy(entity instanceof EntityNPCInterface cnpc ? cnpc : null);
   }

   public Result registerCondition(String id, Object callback) {
      return id != null && id.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,64}")
         ? Result.fail("UNSUPPORTED", "cnpcgun.error.cross_script_condition")
         : Result.fail("INVALID_ARGUMENT");
   }

   private static Entity unwrap(Object object) {
      return object instanceof IEntity<?> wrapper ? wrapper.getMCEntity() : null;
   }

   public static final class AmmoProxy {
      private final ScriptApi.NpcProxy parent;

      private AmmoProxy(ScriptApi.NpcProxy parent) {
         this.parent = parent;
      }

      public Views.AmmoState getState() {
         CombatContext ctx = this.parent.context();
         return ctx == null ? null : ctx.data.gun.ammo(ctx);
      }

      public Integer getMagazineAmmo() {
         Views.AmmoState state = this.getState();
         return state == null ? null : state.magazineAmmo;
      }

      public Integer getReserveAmmo() {
         Views.AmmoState state = this.getState();
         return state == null ? null : state.reserveAmmo;
      }

      public boolean canReload() {
         Views.AmmoState state = this.getState();
         return state != null && state.canReload;
      }

      public Result reload() {
         CombatContext ctx = this.parent.context();
         return ctx == null ? this.parent.unavailable() : ctx.data.gun.reload(ctx);
      }

      private Result supply(int count, boolean replace) {
         CombatContext ctx = this.parent.context();
         if (ctx == null) {
            return this.parent.unavailable();
         } else if (ctx.runtime.adapter instanceof GunAdapter adapter) {
            return ctx.runtime.gunCaps != null && ctx.runtime.gunCaps.hasReserveAmmo
               ? ctx.data.ammo.supply(ctx, adapter, count, replace, true)
               : Result.fail("UNSUPPORTED");
         } else {
            return Result.fail("NO_COMPATIBLE_WEAPON");
         }
      }

      public Result addReserveAmmo(int count) {
         return this.supply(count, false);
      }

      public Result setReserveAmmo(int count) {
         return this.supply(count, true);
      }

      public Result setReserveAmmoLimit(int count) {
         return this.parent.setConfig("ammo.reserveLimit", count);
      }

      public Result setNoAmmoBehavior(String behavior) {
         return this.parent.setConfig("ammo.noAmmoBehavior", behavior);
      }
   }

   public static final class BladeProxy {
      private final ScriptApi.NpcProxy parent;

      private BladeProxy(ScriptApi.NpcProxy parent) {
         this.parent = parent;
      }

      public boolean isSlashBlade() {
         return this.parent.getWeaponType().equals("SLASHBLADE");
      }

      public Views.WeaponInfo getWeaponInfo() {
         return this.isSlashBlade() ? this.parent.getCurrentWeapon() : null;
      }

      public Views.BladeCaps getCapabilities() {
         return this.parent.getCapabilities().slashBlade;
      }

      public String getPreset() {
         CombatContext ctx = this.parent.context();
         return ctx == null ? null : ctx.config.text("slashblade.preset");
      }

      public Object getCombatState() {
         CombatContext ctx = this.parent.context();
         return ctx == null ? null : SafeJson.plain(ctx.data.blade.state(ctx));
      }

      private Views.ActionInfo[] actions(String filter, boolean enabledOnly) {
         CombatContext ctx = this.parent.context();
         if (ctx != null && ctx.runtime.adapter instanceof BladeAdapter adapter) {
            ArrayList<Views.ActionInfo> var10 = new ArrayList<>();

            for (ActionRegistry.Definition def : ActionRegistry.INSTANCE.all()) {
               if (def.adapterId.equals(adapter.id())
                  && (filter == null || filter.equals(def.info.type))
                  && (!enabledOnly || ActionConfig.value(ctx.config.action(def.info.id), "enabled").getAsBoolean())) {
                  var10.add((Views.ActionInfo)SafeJson.GSON.fromJson(SafeJson.GSON.toJson(def.info), Views.ActionInfo.class));
               }
            }

            return var10.toArray(Views.ActionInfo[]::new);
         } else {
            return new Views.ActionInfo[0];
         }
      }

      public Views.ActionInfo[] getAvailableActions() {
         return this.actions(null, false);
      }

      public Views.ActionInfo[] getAvailableSpecialAttacks() {
         return this.actions("SPECIAL_ATTACK", false);
      }

      public Views.ActionInfo[] getAvailablePhantomActions() {
         return this.actions("PHANTOM_SWORD", false);
      }

      public Views.ActionInfo[] getEnabledSpecialAttacks() {
         return this.actions("SPECIAL_ATTACK", true);
      }

      public Views.ActionInfo[] getEnabledPhantomActions() {
         return this.actions("PHANTOM_SWORD", true);
      }

      public Views.ActionInfo[] getAvailableUtilityActions() {
         return Arrays.stream(this.actions(null, false))
            .filter(a -> !Set.of("NORMAL_ATTACK", "COMBO_ATTACK", "SPECIAL_ATTACK", "PHANTOM_SWORD").contains(a.type))
            .toArray(Views.ActionInfo[]::new);
      }

      public Views.ActionInfo getAction(String id) {
         CombatContext ctx = this.parent.context();
         ActionRegistry.Definition def = ActionRegistry.INSTANCE.get(id);
         return ctx != null && def != null && ctx.runtime.adapter != null && def.adapterId.equals(ctx.runtime.adapter.id())
            ? (Views.ActionInfo)SafeJson.GSON.fromJson(SafeJson.GSON.toJson(def.info), Views.ActionInfo.class)
            : null;
      }

      public Result executeAction(String id) {
         return this.executeAction(id, Collections.emptyMap());
      }

      public Result executeAction(String id, Object data) {
         CombatContext ctx = this.parent.context();
         if (ctx == null) {
            return this.parent.unavailable();
         } else {
            try {
               Object target = data instanceof Map<?, ?> map ? map.get("target") : null;
               if (target != null) {
                  if (!(ScriptApi.unwrap(target) instanceof LivingEntity living) || !CombatService.validTarget(ctx.npc, living)) {
                     return Result.fail("INVALID_TARGET");
                  }

                  ctx = new CombatContext(ctx.npc, living);
               }

               JsonObject parameters;
               if (data instanceof Map<?, ?> mapx && mapx.containsKey("target")) {
                  Map<String, Object> withoutTarget = new LinkedHashMap<>();
                  mapx.forEach((k, v) -> {
                     if (!"target".equals(k)) {
                        withoutTarget.put(String.valueOf(k), v);
                     }
                  });
                  parameters = SafeJson.object(withoutTarget);
               } else {
                  parameters = SafeJson.object(data);
               }

               return ctx.data.blade.execute(ctx, id, parameters);
            } catch (IllegalStateException | IllegalArgumentException var8) {
               return Result.fail("INVALID_ARGUMENT", var8.getMessage());
            }
         }
      }

      private Result requireType(String id, String type, Object parameters) {
         Views.ActionInfo info = this.getAction(id);
         if (info == null) {
            return Result.fail("NOT_FOUND");
         } else {
            return info.type.equals(type) ? this.executeAction(id, parameters) : Result.fail("INVALID_ARGUMENT");
         }
      }

      public Result useSpecialAttack(String id) {
         return this.requireType(id, "SPECIAL_ATTACK", Collections.emptyMap());
      }

      public Result executePhantomAction(String id, Object parameters) {
         return this.requireType(id, "PHANTOM_SWORD", parameters);
      }

      public Result executePhantomAction(String id) {
         return this.executePhantomAction(id, Collections.emptyMap());
      }

      public Result configureAction(String id, Object data) {
         CombatContext ctx = this.parent.context();
         if (ctx == null) {
            return this.parent.unavailable();
         } else {
            ActionRegistry.Definition def = ActionRegistry.INSTANCE.get(id);
            if (def == null) {
               return Result.fail("NOT_FOUND");
            } else if (ctx.runtime.adapter != null && def.adapterId.equals(ctx.runtime.adapter.id())) {
               try {
                  ctx.config.putAction(id, ActionConfig.validate(SafeJson.object(data), def));
                  return Result.success();
               } catch (IllegalStateException | IllegalArgumentException var6) {
                  return Result.fail("INVALID_ARGUMENT", var6.getMessage());
               }
            } else {
               return Result.fail("UNSUPPORTED");
            }
         }
      }

      private Result edit(String id, String key, Object value, String type) {
         CombatContext ctx = this.parent.context();
         if (ctx == null) {
            return this.parent.unavailable();
         } else {
            Views.ActionInfo info = this.getAction(id);
            if (info == null) {
               return Result.fail("NOT_FOUND");
            } else if (type != null && !info.type.equals(type)) {
               return Result.fail("INVALID_ARGUMENT");
            } else {
               JsonObject action = ctx.config.action(id);
               action.add(key, SafeJson.copy(value));
               return this.configureAction(id, action);
            }
         }
      }

      public Result enableSpecialAttack(String id, boolean enabled) {
         return this.edit(id, "enabled", enabled, "SPECIAL_ATTACK");
      }

      public Result setSpecialAttackPriority(String id, int priority) {
         return this.edit(id, "priority", priority, "SPECIAL_ATTACK");
      }

      public Result setSpecialAttackCondition(String id, String condition) {
         return !Conditions.IDS.contains(condition) ? Result.fail("NOT_FOUND") : this.edit(id, "conditions", new String[]{condition}, "SPECIAL_ATTACK");
      }

      public Result enablePhantomAction(String id, boolean enabled) {
         return this.edit(id, "enabled", enabled, "PHANTOM_SWORD");
      }

      public Result setPreset(String id) {
         CombatContext ctx = this.parent.context();
         return ctx == null ? this.parent.unavailable() : Presets.apply(ctx, id);
      }

      public Result copyPreset(String source, String destination) {
         CombatContext ctx = this.parent.context();
         return ctx == null ? this.parent.unavailable() : Presets.copy(ctx, source, destination);
      }

      public Result resetPreset(String id) {
         return this.setPreset(id);
      }

      public Result savePreset(String id) {
         CombatContext ctx = this.parent.context();
         return ctx == null ? this.parent.unavailable() : PresetLibrary.get(ctx).saveAs(id, ctx.config.snapshot());
      }

      public String[] getPresets() {
         CombatContext ctx = this.parent.context();
         if (ctx == null) {
            return (String[])Presets.BUILTINS.clone();
         } else {
            List<String> ids = new ArrayList<>(Arrays.asList(Presets.BUILTINS));
            ids.addAll(Arrays.asList(PresetLibrary.get(ctx).ids()));
            return ids.toArray(String[]::new);
         }
      }

      public Object getCombo() {
         CombatContext ctx = this.parent.context();
         return ctx == null ? null : SafeJson.plain(ctx.config.graph(ctx.config.selectedGraph()));
      }

      public Result setCombo(Object data) {
         CombatContext ctx = this.parent.context();
         if (ctx == null) {
            return this.parent.unavailable();
         } else {
            try {
               return ctx.data.blade.setCombo(ctx, SafeJson.object(data));
            } catch (IllegalStateException | IllegalArgumentException var4) {
               return Result.fail("INVALID_ARGUMENT", var4.getMessage());
            }
         }
      }

      public Result startCombo(String id) {
         CombatContext ctx = this.parent.context();
         return ctx == null ? this.parent.unavailable() : ctx.data.blade.startCombo(ctx, id);
      }

      public Result stopCombo() {
         CombatContext ctx = this.parent.context();
         return ctx == null ? this.parent.unavailable() : ctx.data.blade.stopCombo(ctx, "script");
      }

      public Object getComboState() {
         CombatContext ctx = this.parent.context();
         return ctx == null ? null : SafeJson.plain(ctx.data.blade.comboState(ctx));
      }
   }

   public static final class CombatProxy {
      private final ScriptApi.NpcProxy parent;

      private CombatProxy(ScriptApi.NpcProxy parent) {
         this.parent = parent;
      }

      public Object getTarget() {
         CombatContext ctx = this.parent.context();
         return ctx != null && ctx.validTarget() ? NpcAPI.Instance().getIEntity(ctx.target) : null;
      }

      public Result setTarget(Object target) {
         CombatContext ctx = this.parent.context();
         if (ctx == null) {
            return this.parent.unavailable();
         } else if (!(ScriptApi.unwrap(target) instanceof LivingEntity living && CombatService.validTarget(ctx.npc, living))) {
            return Result.fail("INVALID_TARGET");
         } else if (!ctx.config.bool("combat.allowTargetChange") && ctx.validTarget() && ctx.target != living) {
            return Result.fail("CONFLICT");
         } else {
            ctx.npc.setTarget(living);
            return Result.success();
         }
      }

      public Result clearTarget() {
         CombatContext ctx = this.parent.context();
         if (ctx == null) {
            return this.parent.unavailable();
         } else {
            ctx.npc.setTarget(null);
            return Result.success();
         }
      }

      public Double getAttackRange() {
         CombatContext ctx = this.parent.context();
         return ctx == null ? null : ctx.config.number("combat.attackRange");
      }

      public Result setAttackRange(double range) {
         return this.parent.setConfig("combat.attackRange", range);
      }

      public boolean isInCombat() {
         CombatContext ctx = this.parent.context();
         return ctx != null && ctx.validTarget();
      }

      public String getCombatState() {
         CombatContext ctx = this.parent.context();
         return ctx == null ? "DISABLED" : ctx.runtime.combatState;
      }

      public Result setCombatEnabled(boolean enabled) {
         return this.parent.setConfig("combat.enabled", enabled);
      }

      public Result requestRetreat() {
         CombatContext ctx = this.parent.context();
         return ctx == null ? this.parent.unavailable() : CombatService.command(ctx, "RETREAT");
      }

      public Result requestHoldPosition() {
         CombatContext ctx = this.parent.context();
         return ctx == null ? this.parent.unavailable() : CombatService.command(ctx, "HOLD");
      }

      public Result requestAttack() {
         CombatContext ctx = this.parent.context();
         if (ctx == null) {
            return this.parent.unavailable();
         } else {
            ctx.runtime.command = "AUTO";
            ctx.runtime.fireStopped = false;
            if (ctx.runtime.adapter instanceof GunAdapter) {
               return ctx.data.gun.fire(ctx);
            } else {
               return ctx.runtime.adapter instanceof BladeAdapter blade
                  ? ctx.data.blade.execute(ctx, blade.nextNormal(ctx), new JsonObject())
                  : Result.fail("NO_COMPATIBLE_WEAPON");
            }
         }
      }
   }

   public static final class GunProxy {
      private final ScriptApi.NpcProxy parent;

      private GunProxy(ScriptApi.NpcProxy parent) {
         this.parent = parent;
      }

      public boolean isGun() {
         return this.parent.getWeaponType().equals("GUN");
      }

      public Views.WeaponInfo getWeaponInfo() {
         return this.isGun() ? this.parent.getCurrentWeapon() : null;
      }

      public Views.GunCaps getCapabilities() {
         return this.parent.getCapabilities().gun;
      }

      public String getFiringMode() {
         return this.isGun() ? (String)this.parent.getConfig("gun.firingMode") : null;
      }

      public Object getFireRate() {
         CombatContext ctx = this.parent.context();
         if (ctx != null && ctx.runtime.adapter instanceof GunAdapter adapter) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("interval", ctx.config.integer("gun.fireInterval"));
            out.put("multiplier", ctx.config.number("gun.fireRateMultiplier"));
            out.put("nativeInterval", AdapterRegistry.query(adapter, () -> adapter.baseInterval(ctx.weapon), 4));
            out.put("overrideNativeRate", ctx.config.bool("gun.overrideNativeRate"));
            return out;
         } else {
            return null;
         }
      }

      public Views.AmmoState getAmmoState() {
         return this.parent.getAmmo().getState();
      }

      public boolean isReloading() {
         Views.AmmoState ammo = this.getAmmoState();
         return ammo != null && ammo.isReloading;
      }

      public boolean isFiring() {
         CombatContext ctx = this.parent.context();
         return ctx != null && ctx.runtime.scheduler.channel.equals("GUN");
      }

      public Object getAimState() {
         CombatContext ctx = this.parent.context();
         return ctx != null && ctx.runtime.adapter instanceof GunAdapter adapter ? AdapterRegistry.query(adapter, () -> adapter.aimState(ctx), null) : null;
      }

      public Result fire() {
         CombatContext ctx = this.parent.context();
         return ctx == null ? this.parent.unavailable() : ctx.data.gun.fire(ctx);
      }

      public Result stopFire() {
         CombatContext ctx = this.parent.context();
         return ctx == null ? this.parent.unavailable() : ctx.data.gun.stop(ctx);
      }

      public Result reload() {
         return this.parent.getAmmo().reload();
      }

      public Result setFiringEnabled(boolean enabled) {
         return this.parent.setConfig("gun.autoAttack", enabled);
      }

      public Result setFiringMode(String mode) {
         return this.parent.setConfig("gun.firingMode", mode);
      }

      public Result setFireInterval(int ticks) {
         return this.parent.setConfig("gun.fireInterval", ticks);
      }

      public Result setFireRateMultiplier(double multiplier) {
         return this.parent.setConfig("gun.fireRateMultiplier", multiplier);
      }

      public Result setBurstCount(int count) {
         return this.parent.setConfig("gun.burstCount", count);
      }

      public Result setBurstInterval(int ticks) {
         return this.parent.setConfig("gun.burstInterval", ticks);
      }

      public Result setBurstCooldown(int ticks) {
         return this.parent.setConfig("gun.burstCooldown", ticks);
      }

      public Result setAiming(boolean aim) {
         CombatContext ctx = this.parent.context();
         if (ctx == null) {
            return this.parent.unavailable();
         } else if (ctx.runtime.adapter instanceof GunAdapter adapter) {
            Result result = AdapterRegistry.call(adapter, () -> adapter.aim(ctx, aim));
            if (result.ok) {
               ctx.runtime.aiming = aim;
            }

            return result;
         } else {
            return Result.fail("NO_COMPATIBLE_WEAPON");
         }
      }

      public Result setSpreadMultiplier(double multiplier) {
         return this.parent.setConfig("gun.spreadMultiplier", multiplier);
      }

      public Double getSpread() {
         CombatContext ctx = this.parent.context();
         return ctx != null && ctx.runtime.adapter instanceof GunAdapter adapter
            ? AdapterRegistry.query(adapter, () -> adapter.spread(ctx.weapon), null)
            : null;
      }

      public boolean hasRecoil() {
         Views.GunCaps caps = this.getCapabilities();
         return caps != null && caps.hasRecoil;
      }

      public Double getRecoil() {
         CombatContext ctx = this.parent.context();
         return ctx != null && ctx.runtime.gunCaps != null && ctx.runtime.gunCaps.hasRecoil && ctx.runtime.adapter instanceof GunAdapter adapter
            ? AdapterRegistry.query(adapter, () -> adapter.recoil(ctx.weapon), null)
            : null;
      }

      public Result setRecoilMultiplier(double multiplier) {
         return this.parent.setConfig("gun.recoilMultiplier", multiplier);
      }
   }

   public static final class NpcProxy {
      private final WeakReference<EntityNPCInterface> npc;

      private NpcProxy(EntityNPCInterface npc) {
         this.npc = new WeakReference<>(npc);
      }

      private CombatContext context() {
         EntityNPCInterface entity = this.npc.get();
         if (CombatService.serverThread(entity) && CombatService.validNpc(entity)) {
            CombatContext ctx = new CombatContext(entity);
            CombatService.refresh(ctx);
            return ctx;
         } else {
            return null;
         }
      }

      private Result unavailable() {
         EntityNPCInterface entity = this.npc.get();
         return Result.fail(entity != null && !CombatService.serverThread(entity) ? "PERMISSION_DENIED" : "INVALID_NPC");
      }

      public String getVersion() {
         return "1";
      }

      public boolean isAvailable() {
         return this.context() != null && AdapterRegistry.ready();
      }

      public Views.WeaponInfo getCurrentWeapon() {
         CombatContext ctx = this.context();
         return ctx == null ? null : CombatService.weaponInfo(ctx.weapon, null);
      }

      public String getWeaponType() {
         CombatContext ctx = this.context();
         return ctx == null ? "UNKNOWN" : ctx.runtime.domain;
      }

      public Views.Capabilities getCapabilities() {
         CombatContext ctx = this.context();
         return ctx == null ? new Views.Capabilities("UNKNOWN", null, null, false) : CombatService.capabilities(ctx);
      }

      public boolean hasCapability(String id) {
         CombatContext ctx = this.context();
         return ctx != null && CombatService.hasCapability(ctx, id);
      }

      public ScriptApi.GunProxy getGun() {
         return new ScriptApi.GunProxy(this);
      }

      public ScriptApi.BladeProxy getSlashBlade() {
         return new ScriptApi.BladeProxy(this);
      }

      public ScriptApi.CombatProxy getCombat() {
         return new ScriptApi.CombatProxy(this);
      }

      public ScriptApi.AmmoProxy getAmmo() {
         return new ScriptApi.AmmoProxy(this);
      }

      public Object getState() {
         CombatContext ctx = this.context();
         if (ctx == null) {
            return null;
         } else {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("weaponType", ctx.runtime.domain);
            out.put("combatState", ctx.runtime.combatState);
            out.put("actionId", ctx.runtime.actionId);
            out.put("comboId", ctx.runtime.comboId);
            out.put("nodeId", ctx.runtime.comboNode);
            out.put("reloading", ctx.runtime.reloading);
            out.put("lastResult", ctx.runtime.lastResult);
            out.put("targetId", ctx.runtime.targetId == null ? null : ctx.runtime.targetId.toString());
            return out;
         }
      }

      public Object getConfig(String key) {
         CombatContext ctx = this.context();
         if (ctx == null) {
            return this.unavailable();
         } else {
            return ConfigSchema.get(key) == null ? Result.fail("NOT_FOUND") : SafeJson.plain(ctx.config.get(key));
         }
      }

      public Result setConfig(String key, Object value) {
         CombatContext ctx = this.context();
         if (ctx == null) {
            return this.unavailable();
         } else {
            return "slashblade.preset".equals(key) && value instanceof String id ? Presets.apply(ctx, id) : CombatService.setConfig(ctx, key, value);
         }
      }

      public Views.WeaponInfo[] getAvailableWeapons() {
         CombatContext ctx = this.context();
         if (ctx == null) {
            return new Views.WeaponInfo[0];
         } else {
            List<Views.WeaponInfo> weapons = new ArrayList<>();

            for (int i = 0; i < 6; i++) {
               Views.WeaponInfo info = CombatService.weaponInfo(ctx.data.armory.get(ctx.npc, i), "slot:" + i);
               if (info != null) {
                  weapons.add(info);
               }
            }

            return weapons.toArray(Views.WeaponInfo[]::new);
         }
      }

      public Result switchWeapon(String id) {
         CombatContext ctx = this.context();
         if (ctx == null) {
            return this.unavailable();
         } else {
            for (int i = 0; i < 6; i++) {
               Views.WeaponInfo info = CombatService.weaponInfo(ctx.data.armory.get(ctx.npc, i), null);
               if (id != null && (id.equals("slot:" + i) || info != null && id.equals(info.id))) {
                  return ctx.data.armory.switchTo(ctx, i);
               }
            }

            return Result.fail("NOT_FOUND");
         }
      }

      public Result switchToBestWeapon() {
         CombatContext ctx = this.context();
         return ctx == null ? this.unavailable() : CombatService.switchBest(ctx, false);
      }
   }
}
