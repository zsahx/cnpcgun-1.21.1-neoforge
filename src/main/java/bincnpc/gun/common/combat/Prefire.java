package bincnpc.gun.common.combat;

public final class Prefire {
   private Prefire() {
   }

   public static boolean active(String mode, boolean locked, boolean visible) {
      return locked && !visible && ("TRACK".equals(mode) || "SWEEP".equals(mode));
   }
}
