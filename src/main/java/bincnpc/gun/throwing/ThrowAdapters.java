package bincnpc.gun.throwing;

import bincnpc.gun.CNPCGun;
import bincnpc.gun.mixin.ProjectileHitAccess;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.minecraft.core.registries.BuiltInRegistries;
import noppes.npcs.entity.EntityNPCInterface;

public final class ThrowAdapters {
   private static final ThrowAdapter VANILLA = new ThrowAdapter() {
      @Override
      public boolean matches(ItemStack item) {
         return item.is(Items.SNOWBALL)
            || item.is(Items.FIRE_CHARGE)
            || item.is(Items.EGG)
            || item.getItem() instanceof ThrowablePotionItem;
      }

      @Override
      public Projectile create(ServerLevel level, EntityNPCInterface owner, ItemStack item) {
         if (item.is(Items.FIRE_CHARGE)) {
            SmallFireball fire = new SmallFireball(level, owner, Vec3.ZERO);
            fire.setItem(item.copyWithCount(1));
            return fire;
         } else {
            ThrowableItemProjectile projectile = (ThrowableItemProjectile)(item.is(Items.SNOWBALL)
               ? new Snowball(level, owner)
               : (item.is(Items.EGG) ? new ThrownEgg(level, owner) : new ThrownPotion(level, owner)));
            projectile.setItem(item.copyWithCount(1));
            return projectile;
         }
      }

      @Override
      public ThrowAdapter.Profile profile(Projectile projectile) {
         return new ThrowAdapter.Profile(
            1.5,
            projectile instanceof ThrownPotion ? 0.05 : 0.03,
            projectile instanceof SmallFireball ? 0.95 : 0.99,
            Math.min(2.0, projectile.getBbWidth() * 0.5),
            projectile instanceof SmallFireball ? 1.0 : (projectile instanceof ThrownPotion ? 4.0 : 0.0),
            600,
            projectile instanceof SmallFireball
         );
      }

      @Override
      public void detonate(Projectile projectile) {
         if (!projectile.isRemoved() && projectile.level() instanceof ServerLevel) {
            ((ProjectileHitAccess)projectile).cnpcgun$impact(new BlockHitResult(projectile.position(), Direction.UP, projectile.blockPosition().below(), false));
            if (!projectile.isRemoved()) {
               projectile.discard();
            }
         }
      }
   };
   private static final Map<String, ThrowAdapter> optional = new HashMap<>();
   private static final Set<String> reported = ConcurrentHashMap.newKeySet();

   private ThrowAdapters() {
   }

   public static ThrowAdapter find(ItemStack item) {
      if (item == null || item.isEmpty()) {
         return null;
      } else if (VANILLA.matches(item)) {
         return VANILLA;
      } else {
         for (String mod : new String[0]) {
            try {
               ThrowAdapter adapter = optional(mod);
               if (adapter != null && adapter.matches(item)) {
                  return adapter;
               }
            } catch (LinkageError | RuntimeException var6) {
               report(mod, var6);
            }
         }

         return null;
      }
   }

   private static synchronized ThrowAdapter optional(String mod) {
      // 1.21.1 build: no optional throwable-compat mods are available yet.
      return null;
   }

   private static void report(String mod, Throwable error) {
      String key = mod + ":" + error.getClass().getName();
      if (reported.size() < 8 && reported.add(key)) {
         CNPCGun.LOGGER.warn("Optional throwable adapter {} unavailable", mod, error);
      }
   }

   public static List<ItemStack> catalogue() {
      List<ItemStack> items = new ArrayList<>();

      for (Item item : BuiltInRegistries.ITEM) {
         ItemStack stack = new ItemStack(item);
         if (find(stack) != null && items.size() < 4096) {
            items.add(stack);
         }
      }

      for (String mod : new String[]{"lrtactical", "cgm", "superbwarfare"}) {
         try {
            ThrowAdapter adapter = optional(mod);
            if (adapter != null) {
               for (ItemStack stack : adapter.catalogue()) {
                  if (stack != null && !stack.isEmpty() && items.size() < 4096) {
                     items.add(stack.copyWithCount(1));
                  }
               }
            }
         } catch (LinkageError | RuntimeException var8) {
            report(mod, var8);
         }
      }

      return List.copyOf(items);
   }
}
