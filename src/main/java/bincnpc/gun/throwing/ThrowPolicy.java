package bincnpc.gun.throwing;

public final class ThrowPolicy {
   public static final int PLAN_TIMEOUT = 28;
   public static final int FRESH_TICKS = 10;

   private ThrowPolicy() {
   }

   public static boolean requiresSafeDistance(String timing) {
      return "SAFE".equals(timing);
   }

   public static boolean shootingTask(long now, long targetSince, long lastShot, boolean automatic, boolean firingAction) {
      return now < targetSince ? false : automatic || firingAction || lastShot >= targetSince && lastShot <= now && now - lastShot <= 20L;
   }

   public static boolean planExpired(long now, long started) {
      return now < started || now - started > 28L;
   }

   public static boolean predictionFresh(long now, long started, long captured, boolean release) {
      return !planExpired(now, started) && (!release || captured >= started && captured <= now && now - captured <= 10L);
   }
}
