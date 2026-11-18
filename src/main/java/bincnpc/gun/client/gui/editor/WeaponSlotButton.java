package bincnpc.gun.client.gui.editor;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class WeaponSlotButton extends EditorButton {
   private final Supplier<ItemStack> item;
   private final BooleanSupplier equipped;
   private final Runnable equip;

   public WeaponSlotButton(EditorLayout.Box box, Component role, Supplier<ItemStack> item, BooleanSupplier equipped, Runnable clicked) {
      this(box, role, item, equipped, clicked, null);
   }

   public WeaponSlotButton(EditorLayout.Box box, Component role, Supplier<ItemStack> item, BooleanSupplier equipped, Runnable selected, Runnable equip) {
      super(box, role, EditorButton.Kind.NORMAL, selected);
      this.item = item;
      this.equipped = equipped;
      this.equip = equip;
   }

   public ItemStack item() {
      return this.item.get();
   }

   public EditorLayout.Box itemBounds() {
      boolean labelled = !this.getMessage().getString().isEmpty();
      int size = Math.max(14, Math.min(labelled ? 30 : 34, Math.min(this.width - 4, this.height - (labelled ? 17 : 4))));
      return new EditorLayout.Box(this.getX() + (this.width - size) / 2, this.getY() + (labelled ? 3 : (this.height - size) / 2), size, size);
   }

   @Override
   public void onClick(double x, double y) {
      if (this.equip != null && this.itemBounds().contains(x, y)) {
         this.equip.run();
      } else {
         super.onClick(x, y);
      }
   }

   @Override
   protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
      Font font = Minecraft.getInstance().font;
      EditorStyle.panel(
         g,
         new EditorLayout.Box(this.getX(), this.getY(), this.width, this.height),
         this.selected ? -2301487 : (this.isHoveredOrFocused() ? -1906986 : -1119006)
      );
      if (this.selected || this.isFocused()) {
         g.renderOutline(this.getX(), this.getY(), this.width, this.height, this.selected ? -11047869 : -5733307);
      }

      boolean labelled = !this.getMessage().getString().isEmpty();
      EditorLayout.Box inner = this.itemBounds();
      int size = inner.width();
      int slotX = inner.x();
      int slotY = inner.y();
      EditorStyle.slot(g, slotX, slotY, size, this.selected);
      ItemStack stack = this.item.get();
      int iconSize = Math.min(28, size - 4);
      int itemX = slotX + (size - iconSize) / 2;
      int itemY = slotY + (size - iconSize) / 2;
      if (!stack.isEmpty()) {
         g.pose().pushPose();
         g.pose().translate(itemX, itemY, 0.0F);
         g.pose().scale(iconSize / 16.0F, iconSize / 16.0F, 1.0F);
         g.renderItem(stack, 0, 0);
         g.renderItemDecorations(font, stack, 0, 0);
         g.pose().popPose();
      } else {
         g.hLine(slotX + size / 2 - 3, slotX + size / 2 + 3, slotY + size / 2, -9932693);
         g.vLine(slotX + size / 2, slotY + size / 2 - 3, slotY + size / 2 + 3, -9932693);
      }

      if (this.equipped.getAsBoolean()) {
         g.fill(this.getX() + this.width - 7, this.getY() + 3, this.getX() + this.width - 3, this.getY() + 7, -11047869);
      }

      if (labelled) {
         String text = EditorStyle.clipped(font, this.getMessage().getString(), this.width - 6);
         g.drawString(font, text, this.getX() + (this.width - font.width(text)) / 2, this.getY() + this.height - 12, -14077902, false);
      }
   }
}
