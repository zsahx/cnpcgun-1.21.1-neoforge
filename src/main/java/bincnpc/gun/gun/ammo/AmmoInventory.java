package bincnpc.gun.gun.ammo;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.item.IItemStack;
import noppes.npcs.api.wrapper.ItemStackWrapper;
import noppes.npcs.entity.EntityNPCInterface;

public final class AmmoInventory implements IItemHandler {
   private final EntityNPCInterface npc;
   private final boolean enabled;

   public AmmoInventory(EntityNPCInterface npc) {
      this(npc, true);
   }

   public AmmoInventory(EntityNPCInterface npc, boolean enabled) {
      if (npc == null) {
         throw new IllegalArgumentException("NPC ammunition inventory");
      } else {
         this.npc = npc;
         this.enabled = enabled;
      }
   }

   public int getSlots() {
      return this.enabled && this.npc.inventory != null ? 23 : 0;
   }

   public ItemStack getStackInSlot(int slot) {
      if (slot >= 0 && slot < this.getSlots()) {
         IItemStack wrapper = slot < 21
            ? (IItemStack)this.npc.inventory.drops.get(slot)
            : (slot == 21 ? this.npc.inventory.getLeftHand() : this.npc.inventory.getProjectile());
         if (wrapper == null) {
            return ItemStack.EMPTY;
         } else {
            ItemStack stack = ItemStackWrapper.MCItem(wrapper);
            return stack == null ? ItemStack.EMPTY : stack;
         }
      } else {
         throw new IndexOutOfBoundsException(slot);
      }
   }

   public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
      return stack;
   }

   public ItemStack extractItem(int slot, int amount, boolean simulate) {
      ItemStack existing = this.getStackInSlot(slot);
      if (!existing.isEmpty() && amount > 0) {
         int count = Math.min(amount, existing.getCount());
         ItemStack result = existing.copy();
         result.setCount(count);
         if (!simulate) {
            existing.shrink(count);
            if (slot < 21) {
               if (existing.isEmpty()) {
                  this.npc.inventory.drops.remove(slot);
               } else {
                  this.npc.inventory.drops.put(slot, NpcAPI.Instance().getIItemStack(existing));
               }
            } else if (slot == 21) {
               this.npc.inventory.setLeftHand(NpcAPI.Instance().getIItemStack(existing));
            } else {
               this.npc.inventory.setProjectile(NpcAPI.Instance().getIItemStack(existing));
            }

            this.npc.updateClient = true;
         }

         return result;
      } else {
         return ItemStack.EMPTY;
      }
   }

   public int getSlotLimit(int slot) {
      return 64;
   }

   public boolean isItemValid(int slot, ItemStack stack) {
      return false;
   }

   public void changed(int slot) {
      ItemStack stack = this.getStackInSlot(slot);
      if (slot < 21) {
         if (stack.isEmpty()) {
            this.npc.inventory.drops.remove(slot);
         } else {
            this.npc.inventory.drops.put(slot, NpcAPI.Instance().getIItemStack(stack));
         }
      } else if (slot == 21) {
         this.npc.inventory.setLeftHand(NpcAPI.Instance().getIItemStack(stack));
      } else {
         this.npc.inventory.setProjectile(NpcAPI.Instance().getIItemStack(stack));
      }

      this.npc.updateClient = true;
   }

   public long fingerprint() {
      long hash = 1L;

      for (int i = 0; i < this.getSlots(); i++) {
         ItemStack s = this.getStackInSlot(i);
         hash = hash * 31L + s.getCount();
         hash = hash * 31L + (s.isEmpty() ? 0 : BuiltInRegistries.ITEM.getKey(s.getItem()).hashCode());
         hash = hash * 31L + (s.has(DataComponents.CUSTOM_DATA) ? s.get(DataComponents.CUSTOM_DATA).copyTag().hashCode() : 0);
      }

      return hash;
   }
}

