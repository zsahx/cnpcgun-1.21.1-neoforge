package bincnpc.gun.gun.controller;

public final class ReloadTiming {
   private ReloadTiming() {
   }

   public static double speed(double nativeTicks, int requestedTicks) {
      if (Double.isFinite(nativeTicks) && !(nativeTicks <= 0.0) && requestedTicks >= 1 && requestedTicks <= 1200) {
         return Math.max(8.333333333333334E-4, Math.min(100000.0, nativeTicks / requestedTicks));
      } else {
         throw new IllegalArgumentException("reload duration");
      }
   }

   public static int steps(int rounds, int amount) {
      return rounds > 0 && amount > 0 ? (int)(((long)rounds + amount - 1L) / amount) : 0;
   }

   public static long stepAt(long start, int duration, int step, int steps) {
      if (duration >= 1 && duration <= 1200 && steps >= 1 && step >= 1 && step <= steps) {
         return start + ((long)duration * step + steps - 1L) / steps;
      } else {
         throw new IllegalArgumentException("reload steps");
      }
   }

   public static final class Clock {
      private long lastTick;
      private long lastWall;
      private double remainder;

      public Clock(long tick, long wall) {
         this.lastTick = tick;
         this.lastWall = wall;
      }

      public long adjust(long nativeTimestamp, long tick, long wall, double speed) {
         if (!Double.isFinite(speed) || speed <= 0.0) {
            throw new IllegalArgumentException("reload speed");
         } else if (tick < this.lastTick) {
            return nativeTimestamp;
         } else if (tick == this.lastTick) {
            long result = nativeTimestamp + (wall - this.lastWall);
            this.lastWall = wall;
            return result;
         } else {
            double advance = Math.min(100000L, tick - this.lastTick) * 50.0 * speed + this.remainder;
            long whole = (long)Math.floor(advance);
            this.remainder = advance - whole;
            long result = nativeTimestamp + (wall - this.lastWall) - whole;
            this.lastTick = tick;
            this.lastWall = wall;
            return result;
         }
      }
   }
}
