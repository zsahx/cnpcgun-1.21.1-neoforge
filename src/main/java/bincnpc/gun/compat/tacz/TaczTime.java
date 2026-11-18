package bincnpc.gun.compat.tacz;

import bincnpc.gun.CNPCGun;
import java.lang.reflect.Method;
import java.util.function.LongSupplier;

public final class TaczTime {
   private static final LongSupplier CLOCK = findClock();
   private static boolean reported;

   private TaczTime() {
   }

   public static long nowMillis() {
      return CLOCK.getAsLong();
   }

   private static LongSupplier findClock() {
      try {
         Method method = Class.forName("com.tacz.guns.util.time.GunTime", false, TaczTime.class.getClassLoader()).getMethod("nowMillis");
         if (method.getReturnType() != long.class) {
            throw new NoSuchMethodException("GunTime.nowMillis return type");
         } else {
            return () -> {
               try {
                  return (Long)method.invoke(null);
               } catch (RuntimeException | LinkageError | ReflectiveOperationException var2x) {
                  report(var2x);
                  return -1L;
               }
            };
         }
      } catch (ClassNotFoundException var1) {
         return System::currentTimeMillis;
      } catch (RuntimeException | LinkageError | ReflectiveOperationException var2) {
         report(var2);
         return () -> -1L;
      }
   }

   private static void report(Throwable error) {
      if (!reported) {
         reported = true;
         CNPCGun.LOGGER.warn("TaCZ native clock unavailable; native reload timing retained", error);
      }
   }
}
