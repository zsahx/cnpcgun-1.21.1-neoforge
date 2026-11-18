package bincnpc.gun.throwing;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class ThrowPlanner {
   private static final Object LIFECYCLE = new Object();
   private static volatile ThreadPoolExecutor executor;
   private static volatile long generation;
   public static final int MAX_QUEUE = 64;

   private ThrowPlanner() {
   }

   public static ThrowPlanner.Ticket<List<Ballistics.Candidate>> plan(Ballistics.Request request) {
      return submit(() -> Ballistics.candidates(request));
   }

   public static ThrowPlanner.Ticket<Ballistics.Candidate> assess(Ballistics.World snapshot) {
      return submit(() -> Ballistics.choose(snapshot));
   }

   public static ThrowPlanner.Ticket<List<Ballistics.Vec>> route(Ballistics.Grid snapshot) {
      return submit(() -> Ballistics.route(snapshot));
   }

   private static <T> ThrowPlanner.Ticket<T> submit(Supplier<T> job) {
      synchronized (LIFECYCLE) {
         if (executor != null && !executor.isShutdown()) {
            ThrowPlanner.Ticket<T> ticket = new ThrowPlanner.Ticket<>(job, generation);

            ThrowPlanner.Ticket var10000;
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
         AtomicInteger names = new AtomicInteger();
         executor = new ThreadPoolExecutor(2, 2, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(64), task -> {
            Thread thread = new Thread(task, "cnpcgun-throw-" + names.incrementAndGet());
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
            for (Runnable waiting : previous.shutdownNow()) {
               if (waiting instanceof ThrowPlanner.Ticket<?> ticket) {
                  ticket.cancel();
               }
            }
         }
      }
   }

   public static final class Ticket<T> implements Runnable {
      private final Supplier<T> calculation;
      private final long epoch;
      private volatile T result;
      private volatile boolean finished;
      private volatile boolean cancelled;

      private Ticket(Supplier<T> calculation, long epoch) {
         this.calculation = calculation;
         this.epoch = epoch;
      }

      public boolean finished() {
         return this.finished || this.cancelled || this.epoch != ThrowPlanner.generation;
      }

      public T result() {
         return this.finished && !this.cancelled && this.epoch == ThrowPlanner.generation ? this.result : null;
      }

      public boolean current() {
         return !this.cancelled && this.epoch == ThrowPlanner.generation;
      }

      public void cancel() {
         this.cancelled = true;
         this.result = null;
         ThreadPoolExecutor pool = ThrowPlanner.executor;
         if (pool != null) {
            pool.remove(this);
         }
      }

      @Override
      public void run() {
         try {
            if (!this.cancelled && this.epoch == ThrowPlanner.generation && !Thread.currentThread().isInterrupted()) {
               T value = this.calculation.get();
               if (!this.cancelled && this.epoch == ThrowPlanner.generation && !Thread.currentThread().isInterrupted()) {
                  this.result = value;
               }

               return;
            }
         } catch (RuntimeException var5) {
            this.result = null;
            return;
         } finally {
            this.finished = true;
         }
      }
   }
}
