package bincnpc.gun.common.api;

import bincnpc.gun.common.config.Parameter;

public final class Views {
   private Views() {
   }

   public static final class ActionInfo {
      public final String id;
      public final String type;
      public final String source;
      public final String displayName;
      public final String description;
      public final String channel;
      public final boolean available;
      public final boolean interruptible;
      public final boolean parallel;
      public final boolean targetRequired;
      public final int cooldown;
      public final int duration;
      public final Integer resourceCost;
      public final String unavailableReason;
      public final Parameter[] parameters;

      public ActionInfo(
         String id,
         String type,
         String source,
         String displayName,
         String description,
         int cooldown,
         int duration,
         Integer cost,
         Parameter[] parameters,
         boolean available,
         String reason,
         boolean interruptible,
         boolean parallel,
         boolean targetRequired
      ) {
         this.id = id;
         this.type = type;
         this.source = source;
         this.displayName = displayName;
         this.description = description;
         this.cooldown = cooldown;
         this.duration = duration;
         this.resourceCost = cost;
         this.parameters = (Parameter[])parameters.clone();
         this.available = available;
         this.unavailableReason = reason;
         this.interruptible = interruptible;
         this.parallel = parallel;
         this.channel = parallel ? "AUXILIARY" : "MAIN";
         this.targetRequired = targetRequired;
      }
   }

   public static final class AmmoState {
      public final boolean hasMagazine;
      public final boolean hasReserve;
      public final boolean isReloading;
      public final boolean canReload;
      public final Integer magazineAmmo;
      public final Integer magazineCapacity;
      public final Integer reserveAmmo;

      public AmmoState(boolean magazine, Integer count, Integer capacity, boolean reserve, Integer reserveCount, boolean reload, boolean canReload) {
         this.hasMagazine = magazine;
         this.magazineAmmo = count;
         this.magazineCapacity = capacity;
         this.hasReserve = reserve;
         this.reserveAmmo = reserveCount;
         this.isReloading = reload;
         this.canReload = canReload;
      }
   }

   public static final class BladeCaps {
      public boolean canAttack;
      public boolean specialAttack;
      public boolean phantomSword;
      public boolean lockOn;
      public boolean defense;
      public boolean counter;
      public boolean movement;
      public boolean canMoveAttack = true;
      public String[] actionTypes = new String[0];

      public boolean has(String id) {
         if (id == null) {
            return false;
         } else {
            return switch (id) {
               case "slashblade.attack" -> this.canAttack;
               case "slashblade.special_attack" -> this.specialAttack;
               case "slashblade.phantom_sword" -> this.phantomSword;
               case "slashblade.lock_on" -> this.lockOn;
               case "slashblade.defense" -> this.defense;
               case "slashblade.counter" -> this.counter;
               case "slashblade.movement" -> this.movement;
               default -> false;
            };
         }
      }
   }

   public static final class Capabilities {
      public final String domain;
      public final boolean canAttack;
      public final boolean canMoveAttack;
      public final boolean canSwitchAway;
      public final Views.GunCaps gun;
      public final Views.BladeCaps slashBlade;

      public Capabilities(String domain, Views.GunCaps gun, Views.BladeCaps blade, boolean canSwitchAway) {
         this.domain = domain;
         this.gun = gun;
         this.slashBlade = blade;
         this.canSwitchAway = canSwitchAway;
         this.canAttack = gun != null ? gun.canFire : blade != null && blade.canAttack;
         this.canMoveAttack = gun != null ? gun.canMoveAttack : blade != null && blade.canMoveAttack;
      }
   }

   public static final class GunCaps {
      public boolean canFire;
      public boolean canControlFireRate;
      public boolean canOverrideNativeRate;
      public boolean hasMagazine;
      public boolean canReload;
      public boolean canReadAmmo;
      public boolean canConsumeAmmo;
      public boolean hasReserveAmmo;
      public boolean hasRecoil;
      public boolean canControlRecoil;
      public boolean hasAimState;
      public boolean canAim;
      public boolean hasSpread;
      public boolean canControlSpread;
      public boolean canControlSpreadLimit;
      public boolean canControlRange;
      public boolean supportsBurst;
      public boolean supportsAutomaticFire;
      public boolean supportsSingleShot;
      public boolean supportsSpecialMechanics;
      public boolean canMoveAttack = true;
      public boolean canJumpFire = true;
      public boolean canBlindFire;
      public boolean supportsPartialReload;
      public String projectileModel = "UNKNOWN";
      public String[] firingModes = new String[]{"NATIVE"};
      public String[] specialMechanics = new String[0];

      public Views.GunCaps copy() {
         return (Views.GunCaps)SafeJson.GSON.fromJson(SafeJson.GSON.toJson(this), Views.GunCaps.class);
      }

      public Views.GunCaps withNpcControls() {
         this.canControlRecoil = true;
         this.canControlSpread = true;
         this.canControlSpreadLimit = true;
         this.canControlRange = true;
         this.canBlindFire = true;
         return this;
      }

      public boolean has(String key) {
         if (key == null) {
            return false;
         } else {
            return switch (key) {
               case "gun.fire" -> this.canFire;
               case "gun.rate" -> this.canControlFireRate;
               case "gun.override_rate" -> this.canOverrideNativeRate;
               case "gun.reload" -> this.canReload;
               case "gun.partial_reload" -> this.supportsPartialReload;
               case "gun.ammo" -> this.canReadAmmo;
               case "gun.reserve" -> this.hasReserveAmmo;
               case "gun.recoil" -> this.hasRecoil;
               case "gun.recoil_control" -> this.canControlRecoil;
               case "gun.spread" -> this.canControlSpread;
               case "gun.spread_limit" -> this.canControlSpreadLimit;
               case "gun.range" -> this.canControlRange;
               case "gun.aim" -> this.canAim;
               case "gun.burst" -> this.supportsBurst;
               case "gun.jump_fire" -> this.canJumpFire;
               case "gun.blind_fire" -> this.canBlindFire;
               default -> false;
            };
         }
      }
   }

   public static final class WeaponInfo {
      public final String id;
      public final String type;
      public final String registryName;
      public final String displayName;
      public final String adapterId;
      public final boolean available;

      public WeaponInfo(String id, String type, String registryName, String displayName, String adapterId, boolean available) {
         this.id = id;
         this.type = type;
         this.registryName = registryName;
         this.displayName = displayName;
         this.adapterId = adapterId;
         this.available = available;
      }
   }
}
