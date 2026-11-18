package bincnpc.gun.common.combat;

public final class MovementState {
   public long nextMove;
   public long nextMelee;
   public int side = 1;
   public int priorNavigationMode = -1;
   public int pursueSlot = -1;
   public boolean meleeFallback;
   public boolean ammoFallback;
}
