package bincnpc.gun.common.config;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ConfigSchema {
   private static final Map<String, Parameter> FIELDS = new LinkedHashMap<>();

   private ConfigSchema() {
   }

   public static boolean isGunSetting(String key) {
      return key != null && (key.startsWith("gun.") || key.startsWith("ammo."));
   }

   private static void add(Parameter... parameters) {
      for (Parameter p : parameters) {
         FIELDS.put(p.id, isGunSetting(p.id) ? p.requires("") : p);
      }
   }

   public static Parameter get(String key) {
      return FIELDS.get(key);
   }

   public static Parameter[] all() {
      return FIELDS.values().toArray(Parameter[]::new);
   }

   static {
      add(
         Parameter.bool("combat.enabled", true),
         Parameter.bool("combat.disableFriendlyFire", true),
         Parameter.number("combat.attackRange", 32.0, 1.0, 128.0),
         Parameter.bool("combat.movement", true),
         Parameter.bool("combat.pathfinding", true),
         Parameter.bool("combat.stopOnInvalidTarget", true),
         Parameter.bool("combat.allowTargetChange", true),
         Parameter.integer("combat.decisionInterval", 10, 4, 100),
         Parameter.integer("weapon.primarySlot", 0, 0, 5),
         Parameter.bool("weapon.autoSwitch", true),
         Parameter.number("weapon.meleeDistance", 4.0, 0.0, 32.0),
         Parameter.number("weapon.lowHealth", 0.3, 0.0, 1.0),
         Parameter.integer("weapon.switchCooldown", 20, 1, 200),
         Parameter.bool("gun.enabled", true),
         Parameter.bool("gun.autoAttack", true),
         Parameter.number("gun.minRange", 0.0, 0.0, 128.0),
         Parameter.number("gun.range", 32.0, 1.0, 128.0),
         Parameter.bool("gun.requireAim", true).requires("gun.aim"),
         Parameter.bool("gun.moveFire", true),
         Parameter.bool("gun.jumpFire", false).requires("gun.jump_fire"),
         Parameter.bool("gun.blindFire", false).requires("gun.blind_fire"),
         Parameter.number("gun.accuracy", 85.0, 0.0, 100.0),
         Parameter.choice("gun.prefire", "NONE", "TRACK", "SWEEP", "NONE"),
         Parameter.choice("gun.lostSight", "WAIT", "WAIT", "APPROACH", "STOP"),
         Parameter.choice("gun.firingMode", "NATIVE", "NATIVE", "SINGLE", "SEMI", "AUTO", "BURST", "CUSTOM").requires("gun.fire"),
         Parameter.integer("gun.fireInterval", 4, 1, 1200).requires("gun.rate"),
         Parameter.number("gun.fireRateMultiplier", 1.0, 0.05, 20.0).requires("gun.rate"),
         Parameter.bool("gun.overrideNativeRate", false).requires("gun.override_rate"),
         Parameter.bool("gun.reloadSpeedEnabled", false).requires("gun.reload"),
         Parameter.integer("gun.reloadMinTicks", 30, 1, 1200).requires("gun.reload"),
         Parameter.integer("gun.reloadMaxTicks", 60, 1, 1200).requires("gun.reload"),
         Parameter.integer("gun.burstCount", 3, 1, 30).requires("gun.burst"),
         Parameter.integer("gun.burstInterval", 2, 1, 200).requires("gun.burst"),
         Parameter.integer("gun.burstCooldown", 12, 1, 1200).requires("gun.burst"),
         Parameter.integer("gun.firstDelay", 4, 0, 1200),
         Parameter.integer("gun.windup", 0, 0, 200),
         Parameter.integer("gun.recovery", 0, 0, 200),
         Parameter.bool("gun.randomInterval", false).requires("gun.rate"),
         Parameter.integer("gun.minInterval", 2, 1, 1200).requires("gun.rate"),
         Parameter.integer("gun.maxInterval", 8, 1, 1200).requires("gun.rate"),
         Parameter.bool("gun.applyRecoil", true).requires("gun.recoil_control"),
         Parameter.number("gun.recoilMultiplier", 1.0, 0.0, 10.0).requires("gun.recoil_control"),
         Parameter.number("gun.spreadMultiplier", 1.0, 0.0, 10.0).requires("gun.spread"),
         Parameter.number("gun.moveSpread", 1.0, 0.0, 10.0).requires("gun.spread"),
         Parameter.number("gun.jumpSpread", 1.0, 0.0, 10.0).requires("gun.spread"),
         Parameter.number("gun.continuousSpread", 1.0, 0.0, 5.0).requires("gun.spread"),
         Parameter.number("gun.aimSpread", 1.0, 0.0, 5.0).requires("gun.spread"),
         Parameter.number("gun.maxSpread", 180.0, 0.0, 180.0).requires("gun.spread_limit"),
         Parameter.bool("ammo.autoReload", true).requires("gun.reload"),
         Parameter.integer("ammo.reloadThreshold", 0, 0, 1000).requires("gun.reload"),
         Parameter.bool("ammo.combatReload", true).requires("gun.reload"),
         Parameter.bool("ammo.partialReload", true).requires("gun.partial_reload"),
         Parameter.choice("ammo.reloadTiming", "EMPTY", "EMPTY", "THRESHOLD", "IDLE").requires("gun.reload"),
         Parameter.choice("ammo.source", "MIXED", "INDEPENDENT", "NPC_ITEMS", "MIXED"),
         Parameter.choice("ammo.priority", "ITEMS_FIRST", "ITEMS_FIRST", "RESERVE_FIRST"),
         Parameter.choice("ammo.searchRule", "EXACT", "EXACT"),
         Parameter.integer("ammo.reserveLimit", 300, 0, 100000),
         Parameter.integer("ammo.minimumReserve", 0, 0, 100000),
         Parameter.integer("ammo.supplyCooldown", 20, 0, 1200),
         Parameter.bool("ammo.allowExternalSupply", true),
         Parameter.bool("ammo.allowScriptSupply", true),
         Parameter.bool("ammo.infinite", false),
         Parameter.bool("gun.disableIdleAnimation", false),
         Parameter.integer("ammo.defaultReserve", 300, 0, 100000),
         Parameter.bool("ammo.killSupply", false),
         Parameter.integer("ammo.killReward", 30, 0, 100000),
         Parameter.bool("ammo.dropSupply", false),
         Parameter.choice("ammo.noAmmoBehavior", "SWITCH_WEAPON", "SWITCH_WEAPON", "SWITCH_TO_MELEE", "RETREAT", "HOLD_POSITION", "DELEGATE"),
         Parameter.choice("slashblade.preset", "BEGINNER", "BEGINNER", "NORMAL", "CUSTOM", "MAID").requires("slashblade.attack"),
         Parameter.bool("slashblade.enabled", true),
         Parameter.bool("slashblade.protectDurability", true).requires("slashblade.attack"),
         Parameter.bool("slashblade.ignoreRequirements", true).requires("slashblade.attack"),
         Parameter.bool("slashblade.autoAttack", true),
         Parameter.integer("slashblade.attackInterval", 12, 4, 200),
         Parameter.bool("slashblade.useSpecialAttacks", true).requires("slashblade.special_attack"),
         Parameter.bool("slashblade.usePhantoms", true).requires("slashblade.phantom_sword"),
         Parameter.bool("slashblade.counter", true).requires("slashblade.defense"),
         Parameter.choice("slashblade.missingAction", "SKIP", "SKIP", "STOP"),
         Parameter.choice("slashblade.targetChange", "INTERRUPT", "INTERRUPT", "CONTINUE"),
         Parameter.integer("slashblade.resourceReserve", 0, 0, 100000),
         Parameter.integer("slashblade.combo.maxTicks", 600, 20, 2400)
      );

      for (int i = 0; i < 6; i++) {
         add(
            Parameter.integer("weapon.slot" + i + ".priority", 100 - i * 10, 0, 1000),
            Parameter.bool("weapon.slot" + i + ".minRangeEnabled", true),
            Parameter.number("weapon.slot" + i + ".minRange", i < 2 ? 12.0 : 0.0, 0.0, 128.0),
            Parameter.bool("weapon.slot" + i + ".maxRangeEnabled", true),
            Parameter.number("weapon.slot" + i + ".maxRange", i >= 4 ? 6.0 : (i >= 2 ? 20.0 : 64.0), 0.0, 128.0),
            Parameter.bool("weapon.slot" + i + ".maxHealthEnabled", false),
            Parameter.number("weapon.slot" + i + ".maxHealth", 1.0, 0.0, 1.0)
         );
      }

      add(Parameter.bool("throw.enabled", true), Parameter.integer("throw.cooldown", 30, 0, 1200));

      for (int i = 0; i < 3; i++) {
         String key = "throw.slot" + i + ".";
         add(
            Parameter.bool(key + "once", true),
            Parameter.choice(key + "timing", "SAFE", "RELOAD", "SAFE", "SHOOT", "RANDOM"),
            Parameter.choice(key + "trajectory", "AUTO", "STRAIGHT", "CURVE", "AUTO", "TRACKING"),
            Parameter.integer(key + "windup", 30, 0, 200),
            Parameter.integer(key + "recovery", 15, 0, 200),
            Parameter.number(key + "speed", 1.0, 0.01, 20.0),
            Parameter.integer(key + "priority", 100 - i * 10, 0, 1000),
            Parameter.number(key + "minRange", 6.0, 0.0, 128.0),
            Parameter.number(key + "maxRange", 32.0, 1.0, 128.0),
            Parameter.choice(key + "area", "SINGLE", "SINGLE", "AREA"),
            Parameter.number(key + "deviation", 1.5, 0.0, 16.0),
            Parameter.choice(key + "lostTarget", "CANCEL", "CANCEL", "CONTINUE", "RETARGET"),
            Parameter.number(key + "maxElevation", 80.0, 0.0, 89.0),
            Parameter.number(key + "maxDepression", 70.0, 0.0, 89.0),
            Parameter.number(key + "calculationRange", 64.0, 1.0, 128.0),
            Parameter.bool(key + "prediction", true)
         );
      }
   }
}
