package bincnpc.gun.gun.ammo;

public final class AmmoPolicy {
   private AmmoPolicy() {
   }

   public static boolean allowsItems(String source, boolean infinite) {
      return !infinite && !"INDEPENDENT".equals(source);
   }

   public static boolean allowsReserve(String source) {
      return !"NPC_ITEMS".equals(source);
   }

   public static boolean reserveFirst(String priority) {
      return "RESERVE_FIRST".equals(priority);
   }
}
