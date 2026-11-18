package bincnpc.gun.common.network;

import bincnpc.gun.common.api.Result;
import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.common.combat.CombatService;
import bincnpc.gun.throwing.ThrowAdapters;
import java.util.Arrays;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.CustomItems;

public final class Equipment {
   public static final int SIZE = 9;

   private Equipment() {
   }

   public static ItemStack[] empty() {
      ItemStack[] items = new ItemStack[9];
      Arrays.fill(items, ItemStack.EMPTY);
      return items;
   }

   public static ItemStack get(CombatContext ctx, int slot) {
      return ctx != null && slot >= 0 && slot < 9 ? (slot < 6 ? ctx.data.armory.get(ctx.npc, slot) : ctx.data.throwItems.get(slot - 6)) : ItemStack.EMPTY;
   }

   private static boolean valid(CombatContext ctx, ServerPlayer player, int slot) {
      return ctx != null
         && player != null
         && CombatService.serverThread(ctx.npc)
         && CombatService.validNpc(ctx.npc)
         && player.level() == ctx.npc.level()
         && slot >= 6
         && slot < 9
         && !ctx.runtime.scheduler.busy(ctx.now)
         && !ctx.data.throwing.blocksAttack(ctx.now);
   }

   private static boolean supported(ItemStack item) {
      return item != null
         && (item.isEmpty() || !item.is(CustomItems.wand) && item.getCount() <= Math.min(64, item.getMaxStackSize()) && ThrowAdapters.find(item) != null);
   }

   public static Result exchange(CombatContext ctx, ServerPlayer player, int slot, int inventorySlot) {
      if (ctx == null || player == null || slot < 0 || slot >= 9) {
         return Result.fail("INVALID_ARGUMENT");
      } else if (slot < 6) {
         return ctx.data.armory.exchange(ctx, player, slot, inventorySlot);
      } else if (valid(ctx, player, slot) && inventorySlot >= 0 && (inventorySlot <= 35 || inventorySlot == 40)) {
         ItemStack incoming = player.getInventory().getItem(inventorySlot);
         if (!supported(incoming)) {
            return Result.fail("UNSUPPORTED_THROWABLE");
         } else {
            ItemStack old = ctx.data.throwItems.take(ctx, slot - 6);
            player.getInventory().setItem(inventorySlot, old);
            ctx.data.throwItems.put(slot - 6, incoming);
            changed(ctx, player);
            return Result.success();
         }
      } else {
         return Result.fail("INVALID_ARGUMENT");
      }
   }

   public static Result creative(CombatContext ctx, ServerPlayer player, int slot, ItemStack canonical) {
      return creative(ctx, player, slot, canonical, 1);
   }

   public static Result creative(CombatContext ctx, ServerPlayer player, int slot, ItemStack canonical, int count) {
      if (ctx == null || player == null || slot < 0 || slot >= 9) {
         return Result.fail("INVALID_ARGUMENT");
      } else if (slot < 6) {
         return ctx.data.armory.equipCreative(ctx, player, slot, canonical);
      } else if (!valid(ctx, player, slot) || !player.isCreative()) {
         return Result.fail("PERMISSION_DENIED");
      } else if (canonical == null || canonical.isEmpty() || !supported(canonical)) {
         return Result.fail("UNSUPPORTED_THROWABLE");
      } else if (count >= 1 && count <= Math.min(64, canonical.getMaxStackSize())) {
         ItemStack item = canonical.copyWithCount(count);
         ItemStack old = ctx.data.throwItems.take(ctx, slot - 6);
         ctx.data.throwItems.put(slot - 6, item);
         if (!old.isEmpty() && !player.getInventory().add(old)) {
            player.drop(old, false);
         }

         changed(ctx, player);
         return Result.success();
      } else {
         return Result.fail("INVALID_ARGUMENT");
      }
   }

   public static Result unequip(CombatContext ctx, ServerPlayer player, int slot) {
      if (ctx != null && player != null && slot >= 0 && slot < 9) {
         if (slot < 6) {
            return ctx.data.armory.unequip(ctx, player, slot);
         } else if (!valid(ctx, player, slot)) {
            return Result.fail("INVALID_ARGUMENT");
         } else if (get(ctx, slot).isEmpty()) {
            return Result.fail("NO_WEAPON");
         } else {
            for (int i = 0; i < 36; i++) {
               if (player.getInventory().getItem(i).isEmpty()) {
                  return exchange(ctx, player, slot, i);
               }
            }

            return player.getInventory().getItem(40).isEmpty() ? exchange(ctx, player, slot, 40) : Result.fail("INVENTORY_FULL");
         }
      } else {
         return Result.fail("INVALID_ARGUMENT");
      }
   }

   private static void changed(CombatContext ctx, ServerPlayer player) {
      player.getInventory().setChanged();
      player.inventoryMenu.broadcastChanges();
      ctx.config.revision++;
      ctx.npc.updateClient = true;
   }
}
