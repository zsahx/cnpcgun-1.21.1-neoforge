package bincnpc.gun.slashblade.strategy;

public final class MaidCombatState {
   public long nextPhantom;
   public long specialCooldown;
   public long superCooldown;
   public long guardStart;
   public long guardEnd;
   public long guardCooldown;
   public long escapeAt;
   public long lastSlash = -1000L;
   public long lastSpecial = -1000L;
   public long lastTick = Long.MIN_VALUE;
   public int specialBurst;
   public int specialCursor;
   public int comboCycle;
   public long spiralReleaseAt;
   public long guardJustEnd;
   public long escapeUntil;
   public double pressure;
   public double accumulatedDamage;
   public double guardBudget;
   public String specialAction = "";

   public void reset() {
      this.nextPhantom = this.specialCooldown = this.superCooldown = this.guardStart = this.guardEnd = this.guardCooldown = this.escapeAt = 0L;
      this.lastSlash = this.lastSpecial = -1000L;
      this.lastTick = Long.MIN_VALUE;
      this.specialBurst = this.specialCursor = this.comboCycle = 0;
      this.spiralReleaseAt = this.guardJustEnd = this.escapeUntil = 0L;
      this.pressure = this.accumulatedDamage = this.guardBudget = 0.0;
      this.specialAction = "";
   }
}
