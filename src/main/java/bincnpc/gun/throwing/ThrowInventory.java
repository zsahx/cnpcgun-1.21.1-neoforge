package bincnpc.gun.throwing;


import bincnpc.gun.common.api.ItemData;
import bincnpc.gun.common.combat.CombatContext;
import java.util.Arrays;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

public final class ThrowInventory {
   public static final int SIZE = 3;
   private final ItemStack[] items = new ItemStack[]{ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
   private final int[] spent = new int[3];
   private boolean resetAfterDeath;
   public long epoch;

   public ItemStack get(int slot) {
      return slot >= 0 && slot < 3 ? this.items[slot] : ItemStack.EMPTY;
   }

   public int remaining(CombatContext ctx, int slot) {
      return this.get(slot).isEmpty() ? 0 : (ctx.config.bool("throw.slot" + slot + ".once") ? Math.max(0, this.get(slot).getCount() - this.spent[slot]) : -1);
   }

   public void put(int slot, ItemStack item) {
      if (slot >= 0 && slot < 3 && item != null) {
         this.items[slot] = item;
         this.spent[slot] = 0;
         this.epoch++;
      } else {
         throw new IllegalArgumentException("throw slot");
      }
   }

   public ItemStack take(CombatContext ctx, int slot) {
      if (slot >= 0 && slot < 3) {
         ItemStack item = this.get(slot);
         if (!item.isEmpty()) {
            item.setCount(Math.max(0, item.getCount() - this.spent[slot]));
         }

         this.items[slot] = ItemStack.EMPTY;
         this.spent[slot] = 0;
         this.epoch++;
         return item.isEmpty() ? ItemStack.EMPTY : item;
      } else {
         return ItemStack.EMPTY;
      }
   }

   public void used(CombatContext ctx, int slot) {
      if (ctx != null && slot >= 0 && slot < 3) {
         if (ctx.config.bool("throw.slot" + slot + ".once")) {
            this.spent[slot]++;
         }

         this.epoch++;
      }
   }

   public void reset() {
      Arrays.fill(this.spent, 0);
      this.resetAfterDeath = false;
      this.epoch++;
   }

   public void died() {
      this.resetAfterDeath = true;
   }

   public void onAlive() {
      if (this.resetAfterDeath) {
         this.reset();
      }
   }

   public CompoundTag save() {
      CompoundTag result = new CompoundTag();
      ListTag list = new ListTag();

      for (int i = 0; i < 3; i++) {
         if (!this.items[i].isEmpty()) {
            CompoundTag item = ItemData.save(this.items[i]);
            item.putInt("Slot", i);
            item.putInt("Spent", this.spent[i]);
            list.add(item);
         }
      }

      result.put("Items", list);
      result.putBoolean("ResetAfterDeath", this.resetAfterDeath);
      return result;
   }

   public void load(CompoundTag tag) {
      if (tag == null) {
         tag = new CompoundTag();
      }

      Arrays.fill(this.items, ItemStack.EMPTY);
      Arrays.fill(this.spent, 0);
      this.epoch++;
      this.resetAfterDeath = tag.getBoolean("ResetAfterDeath");

      for (Tag raw : tag.getList("Items", 10)) {
         CompoundTag item = (CompoundTag)raw;
         int slot = item.getInt("Slot");
         if (slot >= 0 && slot < 3 && this.items[slot].isEmpty()) {
            this.items[slot] = ItemData.parse(item);
            if (!this.items[slot].isEmpty()) {
               this.items[slot].setCount(Math.max(1, Math.min(64, Math.min(this.items[slot].getMaxStackSize(), this.items[slot].getCount()))));
            }

            this.spent[slot] = Math.max(0, Math.min(this.items[slot].getCount(), item.getInt("Spent")));
         }
      }
   }
}
