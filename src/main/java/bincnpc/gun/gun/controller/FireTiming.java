package bincnpc.gun.gun.controller;

import bincnpc.gun.common.config.NpcConfig;

public final class FireTiming {
   private FireTiming() {
   }

   public static int scaledTicks(int ticks, double multiplier) {
      double scale = Double.isFinite(multiplier) && multiplier > 0.0 ? multiplier : 1.0;
      return (int)Math.max(1.0, Math.min(24000.0, Math.ceil(Math.max(1, ticks) / scale)));
   }

   public static FireTiming.Plan plan(
      FireTiming.Settings settings, int nativeTicks, boolean controlsRate, boolean canOverride, int burstRemaining, int randomTicks
   ) {
      int nativeInterval = Math.max(1, nativeTicks);
      boolean override = controlsRate && canOverride && settings.overrideNative();
      boolean configured = controlsRate && (!settings.mode().equals("NATIVE") || override);
      int base = configured ? scaledTicks(settings.random() ? randomTicks : settings.interval(), settings.multiplier()) : nativeInterval;
      int remaining = 0;
      if (controlsRate && settings.mode().equals("BURST")) {
         remaining = Math.max(0, (burstRemaining <= 0 ? settings.burstCount() : burstRemaining) - 1);
         base = scaledTicks(remaining > 0 ? settings.burstInterval() : settings.burstCooldown(), settings.multiplier());
      }

      if (!override) {
         base = Math.max(base, nativeInterval);
      }

      return new FireTiming.Plan(Math.max(base, settings.recovery()), remaining);
   }

   public static long reschedule(long now, long lastShot, int ticks) {
      return lastShot >= 0L && lastShot <= now ? Math.max(now, lastShot + Math.max(1, ticks)) : now;
   }

   public static long remainingMillis(long nextShot, long now) {
      return Math.max(0L, Math.min(24000L, nextShot - now)) * 50L;
   }

   public record Plan(int ticks, int burstRemaining) {
   }

   public record Settings(
      String mode,
      int interval,
      double multiplier,
      boolean overrideNative,
      int burstCount,
      int burstInterval,
      int burstCooldown,
      int firstDelay,
      int windup,
      int recovery,
      boolean random,
      int minInterval,
      int maxInterval
   ) {
      public static FireTiming.Settings read(NpcConfig config) {
         return new FireTiming.Settings(
            config.text("gun.firingMode"),
            config.integer("gun.fireInterval"),
            config.number("gun.fireRateMultiplier"),
            config.bool("gun.overrideNativeRate"),
            config.integer("gun.burstCount"),
            config.integer("gun.burstInterval"),
            config.integer("gun.burstCooldown"),
            config.integer("gun.firstDelay"),
            config.integer("gun.windup"),
            config.integer("gun.recovery"),
            config.bool("gun.randomInterval"),
            config.integer("gun.minInterval"),
            config.integer("gun.maxInterval")
         );
      }
   }
}
