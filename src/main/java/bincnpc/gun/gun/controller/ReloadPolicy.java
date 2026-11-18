package bincnpc.gun.gun.controller;

public final class ReloadPolicy {
   private ReloadPolicy() {
   }

   public static boolean hasLoadedShot(boolean magazine, Integer rounds, int cost, boolean infinite) {
      return infinite || !magazine || rounds == null || rounds >= Math.max(1, cost);
   }

   public static boolean requested(String timing, boolean target, boolean loadedShot, Integer rounds, int threshold) {
      return !loadedShot || "IDLE".equals(timing) && !target || "THRESHOLD".equals(timing) && rounds != null && rounds <= threshold;
   }

   public static int bufferNeed(int requested, int buffered, int capacity) {
      return (int)Math.max(0L, Math.min((long)Math.max(0, requested) - Math.max(0, buffered), (long)Math.max(0, capacity) - Math.max(0, buffered)));
   }
}
