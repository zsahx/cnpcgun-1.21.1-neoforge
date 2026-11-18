package bincnpc.gun.common.combat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import java.util.concurrent.atomic.AtomicInteger;

public final class CombatDecisions {
   public static final int MAX_AGE = 8;
   public static final int MAX_QUEUE = 128;
   private static final Object LIFECYCLE = new Object();
   private static volatile ThreadPoolExecutor executor;
   private static volatile long generation;

   private CombatDecisions() {
   }

   private static boolean unit(double value) {
      return Double.isFinite(value) && value >= 0.0 && value <= 1.0;
   }

   public static int meleeFallback(List<CombatDecisions.Weapon> weapons, int activeSlot) {
      if (weapons != null && weapons.size() <= 6 && activeSlot >= 0 && activeSlot < 6) {
         CombatDecisions.Weapon best = null;

         for (CombatDecisions.Weapon weapon : weapons) {
            if (weapon == null) {
               throw new IllegalArgumentException("melee candidate");
            }

            if (weapon.melee() && (best == null || weapon.priority() > best.priority() || weapon.priority() == best.priority() && weapon.slot() == activeSlot)) {
               best = weapon;
            }
         }

         return best == null ? -1 : best.slot();
      } else {
         throw new IllegalArgumentException("melee fallback");
      }
   }

   public static CombatDecisions.Advice calculate(CombatDecisions.Snapshot snapshot) {
      int best = -1;
      String key = "";
      double bestScore = Double.NEGATIVE_INFINITY;
      double pressure = Math.min(
         1.0,
         (snapshot.recentHurt() ? 0.35 : 0.0)
            + Math.min(0.45, snapshot.enemies() * 0.12)
            + (snapshot.health() <= snapshot.lowHealth() ? 0.45 : 0.0)
            + (snapshot.distance() <= 6.0 ? 0.15 : 0.0)
      );

      for (CombatDecisions.Weapon weapon : snapshot.weapons()) {
         if ((!snapshot.onlyMelee() || weapon.melee())
            && (!snapshot.requireLoaded() || !weapon.gun() || snapshot.infinite() || weapon.loaded() != 0)
            && (!snapshot.requireLoaded() || !weapon.melee() || !(snapshot.distance() > Math.max(4.0, snapshot.meleeDistance())))) {
            double score = weapon.priority() * 0.05;
            if (weapon.melee()) {
               score += snapshot.distance() <= snapshot.meleeDistance() ? 140.0 : (snapshot.distance() <= 6.0 ? 15.0 : -50.0);
               if (snapshot.distance() <= 4.0 && snapshot.targetHealth() < 0.3) {
                  score += 24.0;
               }

               if (snapshot.distance() <= 4.0 && snapshot.enemies() >= 3) {
                  score += 16.0;
               }
            } else {
               boolean sidearm = weapon.slot() >= 2 && weapon.slot() < 4;
               score += sidearm ? (snapshot.distance() < 18.0 ? 20.0 : 0.0) : (snapshot.distance() >= 20.0 ? 24.0 : (snapshot.distance() >= 12.0 ? 6.0 : 0.0));
               if (snapshot.infinite() || weapon.loaded() < 0) {
                  score += 32.0;
               } else if (weapon.loaded() == 0) {
                  score -= 160.0;
               } else {
                  double fraction = weapon.capacity() > 0 ? Math.min(1.0, (double)weapon.loaded() / weapon.capacity()) : Math.min(1.0, weapon.loaded() / 20.0);
                  score += 18.0 + fraction * 32.0;
                  if (snapshot.targetHealth() < 0.3) {
                     score += 8.0;
                  }

                  score += pressure * fraction * 12.0;
               }

               if (snapshot.health() <= snapshot.lowHealth()) {
                  score += 12.0;
               }
            }

            if (weapon.slot() == snapshot.activeSlot()) {
               score += 6.0;
            }

            if (score > bestScore) {
               best = weapon.slot();
               key = weapon.key();
               bestScore = score;
            }
         }
      }

      return new CombatDecisions.Advice(snapshot, best, key, bestScore, pressure, snapshot.targetHealth() < 0.3, snapshot.distance() > 4.0, -1L);
   }

   public static CombatDecisions.Ticket submit(CombatDecisions.Snapshot snapshot) {
      synchronized (LIFECYCLE) {
         if (executor != null && !executor.isShutdown()) {
            CombatDecisions.Ticket ticket = new CombatDecisions.Ticket(snapshot, generation);

            CombatDecisions.Ticket var10000;
            try {
               executor.execute(ticket);
               var10000 = ticket;
            } catch (RejectedExecutionException var5) {
               return null;
            }

            return var10000;
         } else {
            return null;
         }
      }
   }

   public static void start() {
      synchronized (LIFECYCLE) {
         stop();
         generation++;
         AtomicInteger names = new AtomicInteger();
         executor = new ThreadPoolExecutor(2, 2, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(128), runnable -> {
            Thread thread = new Thread(runnable, "cnpcgun-combat-" + names.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(4);
            return thread;
         }, new AbortPolicy());
         executor.allowCoreThreadTimeOut(true);
      }
   }

   public static void stop() {
      synchronized (LIFECYCLE) {
         generation++;
         ThreadPoolExecutor previous = executor;
         executor = null;
         if (previous != null) {
            for (Runnable pending : previous.shutdownNow()) {
               if (pending instanceof CombatDecisions.Ticket ticket) {
                  ticket.cancel();
               }
            }
         }
      }
   }

   public record Advice(
      CombatDecisions.Snapshot snapshot, int slot, String weapon, double score, double pressure, boolean finish, boolean pursue, long generation
   ) {
      public boolean currentGeneration() {
         return this.generation < 0L || this.generation == CombatDecisions.generation;
      }
   }

   public record Snapshot(
      UUID npc,
      UUID target,
      String dimension,
      long tick,
      long revision,
      long weaponEpoch,
      int activeSlot,
      String activeWeapon,
      double distance,
      double health,
      double targetHealth,
      int enemies,
      double meleeDistance,
      double lowHealth,
      boolean recentHurt,
      boolean infinite,
      boolean onlyMelee,
      boolean requireLoaded,
      List<CombatDecisions.Weapon> weapons
   ) {
      public Snapshot(
         UUID npc,
         UUID target,
         String dimension,
         long tick,
         long revision,
         long weaponEpoch,
         int activeSlot,
         String activeWeapon,
         double distance,
         double health,
         double targetHealth,
         int enemies,
         double meleeDistance,
         double lowHealth,
         boolean recentHurt,
         boolean infinite,
         boolean onlyMelee,
         boolean requireLoaded,
         List<CombatDecisions.Weapon> weapons
      ) {
         if (npc != null
            && target != null
            && dimension != null
            && dimension.length() <= 256
            && activeWeapon != null
            && activeSlot >= 0
            && activeSlot < 6
            && Double.isFinite(distance)
            && !(distance < 0.0)
            && CombatDecisions.unit(health)
            && CombatDecisions.unit(targetHealth)
            && CombatDecisions.unit(lowHealth)
            && Double.isFinite(meleeDistance)
            && !(meleeDistance < 0.0)
            && enemies >= 0
            && enemies <= 16
            && weapons != null
            && weapons.size() <= 6) {
            weapons = List.copyOf(weapons);
            int mask = 0;

            for (CombatDecisions.Weapon weapon : weapons) {
               int bit = 1 << weapon.slot();
               if ((mask & bit) != 0) {
                  throw new IllegalArgumentException("duplicate slot");
               }

               mask |= bit;
            }

            this.npc = npc;
            this.target = target;
            this.dimension = dimension;
            this.tick = tick;
            this.revision = revision;
            this.weaponEpoch = weaponEpoch;
            this.activeSlot = activeSlot;
            this.activeWeapon = activeWeapon;
            this.distance = distance;
            this.health = health;
            this.targetHealth = targetHealth;
            this.enemies = enemies;
            this.meleeDistance = meleeDistance;
            this.lowHealth = lowHealth;
            this.recentHurt = recentHurt;
            this.infinite = infinite;
            this.onlyMelee = onlyMelee;
            this.requireLoaded = requireLoaded;
            this.weapons = weapons;
         } else {
            throw new IllegalArgumentException("combat snapshot");
         }
      }

      public boolean matches(UUID npc, UUID target, String dimension, long now, long revision, long weaponEpoch, int activeSlot, String activeWeapon) {
         return this.npc.equals(npc)
            && this.target.equals(target)
            && this.dimension.equals(dimension)
            && now >= this.tick
            && now - this.tick <= 8L
            && this.revision == revision
            && this.weaponEpoch == weaponEpoch
            && this.activeSlot == activeSlot
            && this.activeWeapon.equals(activeWeapon);
      }
   }

   public static final class Ticket implements Runnable {
      private final CombatDecisions.Snapshot snapshot;
      private final long epoch;
      private volatile CombatDecisions.Advice advice;
      private volatile boolean finished;
      private volatile boolean cancelled;

      private Ticket(CombatDecisions.Snapshot snapshot, long epoch) {
         this.snapshot = snapshot;
         this.epoch = epoch;
      }

      public boolean finished() {
         return this.finished || this.cancelled || this.epoch != CombatDecisions.generation;
      }

      public CombatDecisions.Advice advice() {
         return this.finished && !this.cancelled && this.epoch == CombatDecisions.generation ? this.advice : null;
      }

      public void cancel() {
         this.cancelled = true;
         this.advice = null;
      }

      @Override
      public void run() {
         try {
            if (this.cancelled || this.epoch != CombatDecisions.generation || Thread.currentThread().isInterrupted()) {
               return;
            }

            CombatDecisions.Advice result = CombatDecisions.calculate(this.snapshot);
            if (!this.cancelled && this.epoch == CombatDecisions.generation && !Thread.currentThread().isInterrupted()) {
               this.advice = new CombatDecisions.Advice(
                  result.snapshot(), result.slot(), result.weapon(), result.score(), result.pressure(), result.finish(), result.pursue(), this.epoch
               );
            }
         } finally {
            this.finished = true;
         }
      }
   }

   public record Weapon(int slot, String key, boolean melee, boolean gun, int loaded, int capacity, int priority) {
      public Weapon(int slot, String key, boolean melee, boolean gun, int loaded, int capacity, int priority) {
         if (slot >= 0
            && slot < 6
            && key != null
            && !key.isEmpty()
            && key.length() <= 256
            && melee != gun
            && loaded >= -1
            && loaded <= 100000
            && capacity >= -1
            && capacity <= 100000
            && priority >= 0
            && priority <= 1000) {
            this.slot = slot;
            this.key = key;
            this.melee = melee;
            this.gun = gun;
            this.loaded = loaded;
            this.capacity = capacity;
            this.priority = priority;
         } else {
            throw new IllegalArgumentException("weapon snapshot");
         }
      }
   }
}
