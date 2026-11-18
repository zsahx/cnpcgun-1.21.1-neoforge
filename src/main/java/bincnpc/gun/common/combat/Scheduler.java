package bincnpc.gun.common.combat;

import bincnpc.gun.common.api.Result;
import java.util.HashMap;
import java.util.Map;

public final class Scheduler {
   public String channel = "";
   public String action = "";
   public long until;
   public boolean interruptible = true;
   private final Map<String, Long> auxiliary = new HashMap<>();

   public boolean busy(long now) {
      return !this.channel.isEmpty() && now < this.until;
   }

   public Result acquire(String channel, String action, long now, int duration, boolean interruptible, boolean parallel) {
      this.expire(now);
      if (parallel) {
         if (this.busy(now) && !this.channel.equals("BLADE")) {
            return Result.fail("CONFLICT");
         } else if (this.auxiliary.containsKey(action)) {
            return Result.fail("COOLDOWN");
         } else if (this.auxiliary.size() >= 4) {
            return Result.fail("BUSY");
         } else {
            this.auxiliary.put(action, now + Math.max(1, duration));
            return Result.success();
         }
      } else if (this.busy(now)) {
         return Result.fail("BUSY");
      } else if (!channel.equals("BLADE") && !this.auxiliary.isEmpty()) {
         return Result.fail("CONFLICT");
      } else {
         this.channel = channel;
         this.action = action;
         this.until = now + Math.max(1, duration);
         this.interruptible = interruptible;
         return Result.success();
      }
   }

   public void expire(long now) {
      this.auxiliary.values().removeIf(t -> t <= now);
      if (!this.channel.isEmpty() && now >= this.until) {
         this.release();
      }
   }

   public void release() {
      this.channel = "";
      this.action = "";
      this.until = 0L;
      this.interruptible = true;
   }

   public void releaseAuxiliary(String action) {
      this.auxiliary.remove(action);
   }

   public void clear() {
      this.release();
      this.auxiliary.clear();
   }
}
