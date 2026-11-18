package bincnpc.gun.gun.switching;


import bincnpc.gun.common.api.ItemData;
import bincnpc.gun.common.adapter.AdapterRegistry;
import bincnpc.gun.common.api.Result;
import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.common.combat.CombatService;
import java.util.Arrays;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.CustomItems;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.wrapper.ItemStackWrapper;
import noppes.npcs.entity.EntityNPCInterface;

public final class Armory {
   public static final int SIZE = 6;
   private final ItemStack[] slots = new ItemStack[]{
      ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY
   };
   public int active;

   public ItemStack get(EntityNPCInterface npc, int index) {
      if (index < 0 || index >= 6) {
         return ItemStack.EMPTY;
      } else if (index != this.active) {
         return this.slots[index];
      } else if (npc != null && npc.inventory != null) {
         ItemStack item = ItemStackWrapper.MCItem(npc.inventory.getRightHand());
         return item == null ? ItemStack.EMPTY : item;
      } else {
         return ItemStack.EMPTY;
      }
   }

   public Result switchTo(CombatContext ctx, int index) {
      if (ctx == null || !CombatService.serverThread(ctx.npc) || !CombatService.validNpc(ctx.npc)) {
         return Result.fail("INVALID_NPC");
      } else if (index < 0 || index >= 6) {
         return Result.fail("INVALID_ARGUMENT");
      } else if (index == this.active) {
         return Result.success();
      } else if (ctx.runtime.scheduler.busy(ctx.now) || ctx.data.throwing.blocksAttack(ctx.now)) {
         return Result.fail("BUSY");
      } else if (ctx.now < ctx.runtime.nextSwitch) {
         return Result.fail("COOLDOWN");
      } else {
         ItemStack next = this.slots[index];
         if (next.isEmpty()) {
            return Result.fail("NO_WEAPON");
         } else {
            Result lock = ctx.runtime.scheduler.acquire("SWITCH", "slot:" + index, ctx.now, 4, false, false);
            if (!lock.ok) {
               return lock;
            } else {
               ItemStack current = this.get(ctx.npc, this.active);
               this.slots[this.active] = current;
               this.slots[index] = ItemStack.EMPTY;
               this.active = index;
               ctx.runtime.movement.meleeFallback = AdapterRegistry.find(next) == null
                  && CombatService.melee(next)
                  && (ctx.runtime.adapter != null || ctx.runtime.movement.meleeFallback);
               ctx.npc.inventory.setRightHand(NpcAPI.Instance().getIItemStack(next));
               ctx.npc.updateAI = true;
               ctx.npc.updateClient = true;
               ctx.data.config.revision++;
               ctx.runtime.nextSwitch = ctx.now + ctx.config.integer("weapon.switchCooldown");
               ctx.runtime.fireStopped = false;
               ctx.runtime.noAmmoRetry = 0L;
               ctx.runtime.command = "AUTO";
               return Result.success("slot:" + index);
            }
         }
      }
   }

   public Result exchange(CombatContext ctx, ServerPlayer player, int slot, int inventorySlot) {
      if (ctx != null && player != null && CombatService.serverThread(ctx.npc) && player.level() == ctx.npc.level()) {
         if (slot >= 0 && slot < 6 && (inventorySlot >= 0 && inventorySlot <= 35 || inventorySlot == 40)) {
            if (!ctx.runtime.scheduler.busy(ctx.now) && !ctx.data.throwing.blocksAttack(ctx.now)) {
               ItemStack incoming = player.getInventory().getItem(inventorySlot);
               if (incoming.is(CustomItems.wand)) {
                  return Result.fail("INVALID_ARGUMENT");
               } else {
                  if (slot == this.active) {
                     Result lock = ctx.runtime.scheduler.acquire("SWITCH", "exchange", ctx.now, 4, false, false);
                     if (!lock.ok) {
                        return lock;
                     }
                  }

                  ItemStack old = this.get(ctx.npc, slot);
                  player.getInventory().setItem(inventorySlot, old);
                  if (slot == this.active) {
                     ctx.npc.inventory.setRightHand(NpcAPI.Instance().getIItemStack(incoming));
                  } else {
                     this.slots[slot] = incoming;
                  }

                  player.getInventory().setChanged();
                  player.inventoryMenu.broadcastChanges();
                  ctx.npc.updateClient = true;
                  ctx.npc.updateAI = true;
                  ctx.config.revision++;
                  ctx.runtime.noAmmoRetry = 0L;
                  return Result.success();
               }
            } else {
               return Result.fail("BUSY");
            }
         } else {
            return Result.fail("INVALID_ARGUMENT");
         }
      } else {
         return Result.fail("PERMISSION_DENIED");
      }
   }

   public Result unequip(CombatContext ctx, ServerPlayer player, int slot) {
      if (CombatService.serverThread(ctx.npc) && slot >= 0 && slot < 6) {
         if (this.get(ctx.npc, slot).isEmpty()) {
            return Result.fail("NO_WEAPON");
         } else {
            for (int i = 0; i < 36; i++) {
               if (player.getInventory().getItem(i).isEmpty()) {
                  return this.exchange(ctx, player, slot, i);
               }
            }

            return player.getInventory().getItem(40).isEmpty() ? this.exchange(ctx, player, slot, 40) : Result.fail("INVENTORY_FULL");
         }
      } else {
         return Result.fail("INVALID_ARGUMENT");
      }
   }

   public Result equipCreative(CombatContext ctx, ServerPlayer player, int slot, ItemStack canonical) {
      if (ctx == null || player == null || !CombatService.serverThread(ctx.npc) || player.level() != ctx.npc.level() || !player.isCreative()) {
         return Result.fail("PERMISSION_DENIED");
      } else if (slot < 0 || slot >= 6 || canonical == null || canonical.isEmpty() || canonical.is(CustomItems.wand)) {
         return Result.fail("INVALID_ARGUMENT");
      } else if (!ctx.runtime.scheduler.busy(ctx.now) && !ctx.data.throwing.blocksAttack(ctx.now)) {
         ItemStack incoming = canonical.copy();
         incoming.setCount(1);
         ItemStack old = this.get(ctx.npc, slot);
         if (slot == this.active) {
            ctx.npc.inventory.setRightHand(NpcAPI.Instance().getIItemStack(incoming));
         } else {
            this.slots[slot] = incoming;
         }

         if (!old.isEmpty() && !player.getInventory().add(old)) {
            player.drop(old, false);
         }

         player.getInventory().setChanged();
         player.inventoryMenu.broadcastChanges();
         ctx.npc.updateClient = true;
         ctx.npc.updateAI = true;
         ctx.config.revision++;
         return Result.success();
      } else {
         return Result.fail("BUSY");
      }
   }

   public void restorePrimary(CombatContext ctx) {
      int preferred = ctx.config.integer("weapon.primarySlot");
      if (preferred != this.active && !this.get(ctx.npc, preferred).isEmpty()) {
         this.slots[this.active] = this.get(ctx.npc, this.active);
         ItemStack next = this.slots[preferred];
         this.slots[preferred] = ItemStack.EMPTY;
         this.active = preferred;
         ctx.npc.inventory.setRightHand(NpcAPI.Instance().getIItemStack(next));
         ctx.npc.updateClient = true;
         ctx.npc.updateAI = true;
      }
   }

   public int slotOf(EntityNPCInterface npc, ItemStack weapon) {
      for (int i = 0; i < 6; i++) {
         if (this.get(npc, i) == weapon) {
            return i;
         }
      }

      return this.active;
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putInt("Active", this.active);
      ListTag list = new ListTag();

      for (int i = 0; i < 6; i++) {
         if (i != this.active && !this.slots[i].isEmpty()) {
            CompoundTag item = ItemData.save(this.slots[i]);
            item.putInt("Slot", i);
            list.add(item);
         }
      }

      tag.put("Items", list);
      return tag;
   }

   public void load(CompoundTag tag) {
      if (tag == null) {
         tag = new CompoundTag();
      }

      this.active = Math.max(0, Math.min(5, tag.getInt("Active")));
      Arrays.fill(this.slots, ItemStack.EMPTY);

      for (Tag raw : tag.getList("Items", 10)) {
         CompoundTag item = (CompoundTag)raw;
         int i = item.getInt("Slot");
         if (i >= 0 && i < 6 && i != this.active && this.slots[i].isEmpty()) {
            this.slots[i] = ItemData.parse(item);
         }
      }
   }
}
