package bincnpc.gun.gun.ammo;

import bincnpc.gun.common.adapter.AdapterRegistry;
import bincnpc.gun.common.api.Views;
import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.common.combat.CombatService;
import bincnpc.gun.gun.adapter.GunAdapter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

public final class AmmoSupply {
   private AmmoSupply() {
   }

   private static List<AmmoSupply.Weapon> guns(CombatContext ctx) {
      List<AmmoSupply.Weapon> guns = new ArrayList<>();

      for (int slot = 0; slot < 6; slot++) {
         ItemStack stack = ctx.data.armory.get(ctx.npc, slot);
         if (AdapterRegistry.find(stack) instanceof GunAdapter gun) {
            Views.GunCaps caps = AdapterRegistry.query(gun, () -> gun.capabilities(stack), null);
            if (caps != null && caps.hasReserveAmmo) {
               guns.add(new AmmoSupply.Weapon(stack, gun));
            }
         }
      }

      return guns;
   }

   public static void defaults(CombatContext ctx) {
      if (CombatService.serverThread(ctx.npc)) {
         ctx.data.ammo.clearDefaultReserve();

         for (AmmoSupply.Weapon gun : guns(ctx)) {
            AdapterRegistry.run(gun.adapter, () -> gun.adapter.resetReserve(ctx, gun.stack));
            ctx.data.ammo.credit(ctx, gun.adapter, gun.stack, ctx.config.integer("ammo.defaultReserve"));
         }

         ctx.runtime.restoreSupplies = false;
         ctx.npc.updateClient = true;
      }
   }

   public static void kill(CombatContext ctx) {
      if (CombatService.serverThread(ctx.npc) && ctx.config.bool("ammo.killSupply") && !ctx.config.bool("ammo.infinite")) {
         List<AmmoSupply.Weapon> guns = guns(ctx);
         int reward = ctx.config.integer("ammo.killReward");
         if (!guns.isEmpty() && reward > 0) {
            if (reward % guns.size() == 0) {
               for (AmmoSupply.Weapon gun : guns) {
                  ctx.data.ammo.credit(ctx, gun.adapter, gun.stack, reward / guns.size());
               }
            } else {
               AmmoSupply.Weapon gun = guns.get(ctx.npc.getRandom().nextInt(guns.size()));
               ctx.data.ammo.credit(ctx, gun.adapter, gun.stack, reward);
            }

            ctx.npc.updateClient = true;
         }
      }
   }

   public static void pickup(CombatContext ctx) {
      if (CombatService.serverThread(ctx.npc)
         && ctx.config.bool("ammo.dropSupply")
         && !ctx.config.bool("ammo.infinite")
         && AmmoController.allowsIndependent(ctx)
         && ctx.now >= ctx.runtime.nextDropScan) {
         ctx.runtime.nextDropScan = ctx.now + 10L;
         List<AmmoSupply.Weapon> guns = guns(ctx);
         if (!guns.isEmpty()) {
            int scanned = 0;

            for (ItemEntity entity : ctx.npc.level().getEntitiesOfClass(ItemEntity.class, ctx.npc.getBoundingBox().inflate(2.0))) {
               if (++scanned > 32) {
                  break;
               }

               if (entity.isAlive() && !entity.hasPickUpDelay()) {
                  ItemStack item = entity.getItem();
                  List<String> matching = new ArrayList<>();
                  int units = 0;

                  for (AmmoSupply.Weapon gun : guns) {
                     int count = AdapterRegistry.query(gun.adapter, () -> gun.adapter.ammoUnits(gun.stack, item), 0);
                     if (count > 0 && count <= 100000 && (units == 0 || count == units)) {
                        String key = ctx.data.ammo.key(ctx, gun.adapter, gun.stack);
                        if (key != null) {
                           units = count;
                           matching.add(key);
                        }
                     }
                  }

                  if (!matching.isEmpty() && ctx.data.ammo.creditPickup(ctx, matching, units)) {
                     entity.discard();
                     ctx.npc.updateClient = true;
                  }
               }
            }
         }
      }
   }

   private record Weapon(ItemStack stack, GunAdapter adapter) {
   }
}
