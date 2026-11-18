package bincnpc.gun.gun.controller;

public final class RecoilState {
   private long tick = Long.MIN_VALUE;
   private double pitch;

   public double pitch() {
      return this.pitch;
   }

   public void advance(long now, boolean enabled) {
      if (!enabled || this.tick == Long.MIN_VALUE || now < this.tick || now - this.tick > 200L) {
         this.pitch = 0.0;
      } else if (now > this.tick) {
         this.pitch = this.pitch * Math.pow(0.75, now - this.tick);
      }

      if (Math.abs(this.pitch) < 1.0E-4) {
         this.pitch = 0.0;
      }

      this.tick = now;
   }

   public void kick(long now, double degrees) {
      this.advance(now, true);
      if (Double.isFinite(degrees) && degrees > 0.0) {
         this.pitch = Math.max(-30.0, this.pitch - Math.min(30.0, degrees));
      }
   }

   public void reset() {
      this.pitch = 0.0;
      this.tick = Long.MIN_VALUE;
   }
}
