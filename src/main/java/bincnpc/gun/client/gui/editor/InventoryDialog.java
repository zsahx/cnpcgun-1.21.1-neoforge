package bincnpc.gun.client.gui.editor;

import bincnpc.gun.client.EditorSession;
import bincnpc.gun.client.gui.GuiSupport;
import bincnpc.gun.common.api.SafeJson;
import bincnpc.gun.common.config.Parameter;
import bincnpc.gun.common.network.CreativeCatalogue;
import bincnpc.gun.common.network.GunNetwork;
import bincnpc.gun.throwing.ThrowAdapters;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.CustomItems;

public final class InventoryDialog extends EditorDialogs.Base {
   private final EditorSession session;
   private final BiConsumer<GunNetwork.Operation, JsonObject> send;
   private final List<WeaponSlotButton> slots = new ArrayList<>();
   private List<CreativeCatalogue.Tab> catalogue = List.of();
   private int targetSlot;
   private int itemPage;
   private int tabPage;
   private int presetHash;
   private int creativeCount = 1;
   private final int slotBase;
   private final int slotCount;
   private String tabId = "";
   private String query = "";
   private String presetQuery = "";
   private String presetId = "";
   private EditorTextField search;
   private EditorTextField presetSearch;
   private EditorList presets;
   private InventoryDialog.Browser browser;
   private EditorLayout.Box playerArea;
   private EditorButton create;
   private EditorButton remove;
   private EditorButton previous;
   private EditorButton next;
   private EditorButton unload;
   private InventoryDialog.TabStrip tabStrip;
   private EditorLayout.Box browseArea;
   private int categoryWidth;
   private int cell;

   public InventoryDialog(EditorDialogs.Host host, EditorSession session, int slot, BiConsumer<GunNetwork.Operation, JsonObject> send) {
      super(host, Component.translatable("cnpcgun.editor.exchangeTitle"));
      this.session = session;
      this.targetSlot = slot;
      this.send = send;
      this.slotBase = slot >= 6 ? 6 : 0;
      this.slotCount = this.slotBase == 6 ? 3 : 6;
   }

   private static Component label(String key) {
      return Component.translatable("cnpcgun.editor." + key);
   }

   private static boolean throwable(ItemStack item) {
      return item != null && (item.isEmpty() || ThrowAdapters.find(item) != null);
   }

   @Override
   protected void init() {
      boolean itemFocus = this.search != null && this.search.isFocused();
      boolean presetFocus = this.presetSearch != null && this.presetSearch.isFocused();
      super.init();
      this.slots.clear();
      int w = Math.min(760, this.width - 12);
      int h = Math.min(500, this.height - 12);
      this.box = new EditorLayout.Box((this.width - w) / 2, (this.height - h) / 2, w, h);
      this.content = new EditorLayout.Box(this.box.x() + 8, this.box.y() + 40, w - 16, h - 74);
      this.subtitle = GuiSupport.tr("cnpcgun.editor.equipmentTarget", GuiSupport.tr("cnpcgun.gui.slot" + this.targetSlot));
      int leftWidth = Math.max(76, Math.min(152, this.content.width() / 4));
      EditorLayout.Box left = new EditorLayout.Box(this.content.x(), this.content.y(), leftWidth, this.content.height());
      EditorLayout.Box main = new EditorLayout.Box(left.right() + 6, this.content.y(), this.content.right() - left.right() - 6, this.content.height());
      this.cell = this.height < 300 ? 18 : Math.max(18, Math.min(26, (main.width() - 32) / 9));
      int playerHeight = 14 + 4 * (this.cell + 2);
      this.playerArea = new EditorLayout.Box(main.x(), this.content.bottom() - playerHeight, main.width(), playerHeight);
      int browserHeight = this.playerArea.y() - this.content.y() - 2;
      int reserveWidth = Math.max(60, Math.min(156, main.width() / 4));
      EditorLayout.Box browse = new EditorLayout.Box(main.x(), main.y(), main.width() - reserveWidth - 6, browserHeight);
      this.browseArea = browse;
      EditorLayout.Box reserve = new EditorLayout.Box(browse.right() + 6, this.content.y(), reserveWidth, browserHeight);
      boolean openedCatalogue = this.catalogue.isEmpty();
      if (openedCatalogue && this.minecraft.player != null && this.minecraft.level != null) {
         try {
            this.catalogue = this.slotBase == 6
               ? CreativeCatalogue.throwingTabs(this.minecraft.level.enabledFeatures(), this.minecraft.player.hasPermissions(2), this.minecraft.level.registryAccess())
               : CreativeCatalogue.tabs(this.minecraft.level.enabledFeatures(), this.minecraft.player.hasPermissions(2), this.minecraft.level.registryAccess());
            if (this.tabId.isEmpty()) {
               this.tabId = this.catalogue
                  .stream()
                  .filter(tab -> !tab.id().startsWith("minecraft:"))
                  .map(CreativeCatalogue.Tab::id)
                  .findFirst()
                  .orElse(this.catalogue.isEmpty() ? "" : this.catalogue.get(0).id());
            }
         } catch (LinkageError | RuntimeException var25) {
            this.error = GuiSupport.tr("cnpcgun.editor.catalogueUnavailable");
         }
      }

      this.categoryWidth = Math.max(44, Math.min(156, browse.width() / 2 - 6));
      this.search = (EditorTextField)this.addRenderableWidget(
         new EditorTextField(
            this.font, browse.x() + this.categoryWidth + 8, browse.y() + 6, browse.width() - this.categoryWidth - 12, 8, label("searchItems")
         )
      );
      this.search.setHint(label("searchItems"));
      this.search.setValue(this.query);
      this.create = (EditorButton)this.addRenderableWidget(
         new EditorButton(left.x(), left.y(), left.width(), 20, label("createItemPreset"), EditorButton.Kind.PRIMARY, this::createPreset)
      );
      this.presetSearch = (EditorTextField)this.addRenderableWidget(
         new EditorTextField(this.font, left.x() + 4, left.y() + 28, left.width() - 8, 8, label("searchPresets"))
      );
      this.presetSearch.setHint(label("searchPresets"));
      this.presetSearch.setValue(this.presetQuery);
      this.presets = (EditorList)this.addRenderableWidget(
         new EditorList(
            this.font,
            new EditorLayout.Box(left.x(), left.y() + 44, left.width(), Math.max(18, left.height() - 64)),
            this.presetRows(),
            this.presetId,
            id -> {
               this.presetId = id;
               if (this.session.creativeAllowed() && !this.session.pending) {
                  JsonObject input = this.source();
                  input.addProperty("id", id);
                  int limit = 64;

                  for (JsonElement raw : this.itemPresets()) {
                     if (raw.isJsonObject()) {
                        JsonObject item = raw.getAsJsonObject();
                        if (id.equals(SafeJson.string(item, "id", ""))) {
                           limit = SafeJson.integer(item, "stackLimit", 1);
                           break;
                        }
                     }
                  }

                  this.equipTemplate(GunNetwork.Operation.EQUIP_ITEM_PRESET, input, limit);
               }
            }
         )
      );
      this.presets.filter(this.presetQuery);
      this.remove = (EditorButton)this.addRenderableWidget(
         new EditorButton(
            left.x(),
            left.bottom() - 17,
            left.width(),
            16,
            label("deleteItemPreset"),
            EditorButton.Kind.DANGER,
            () -> {
               if (!this.presetId.isEmpty() && !this.session.pending) {
                  EditorDialogs.confirm(
                     this.host,
                     label("deleteTitle"),
                     GuiSupport.tr("cnpcgun.editor.deleteItemPresetMessage"),
                     List.of(new EditorDialogs.Decision(label("deleteItemPreset"), EditorButton.Kind.DANGER, () -> {
                        JsonObject input = new JsonObject();
                        input.addProperty("id", this.presetId);
                        this.send.accept(GunNetwork.Operation.DELETE_ITEM_PRESET, input);
                        this.presetId = "";
                     }), new EditorDialogs.Decision(Component.translatable("gui.cancel"), EditorButton.Kind.NORMAL, () -> {}))
                  );
               }
            }
         )
      );
      int headerHeight = browserHeight < 70 ? 18 : 22;
      int stripHeight = browserHeight < 70 ? 20 : 24;
      this.browser = (InventoryDialog.Browser)this.addRenderableWidget(
         new InventoryDialog.Browser(
            new EditorLayout.Box(browse.x(), browse.y() + headerHeight, browse.width(), Math.max(18, browse.height() - headerHeight - stripHeight - 2))
         )
      );
      this.tabStrip = (InventoryDialog.TabStrip)this.addRenderableWidget(
         new InventoryDialog.TabStrip(
            new EditorLayout.Box(browse.x() + 21, browse.bottom() - stripHeight, Math.max(stripHeight, browse.width() - 42), stripHeight)
         )
      );
      if (openedCatalogue) {
         for (int i = 0; i < this.catalogue.size(); i++) {
            if (this.catalogue.get(i).id().equals(this.tabId)) {
               this.tabPage = i / this.tabStrip.capacity;
            }
         }
      }

      this.previous = (EditorButton)this.addRenderableWidget(
         new EditorButton(
            browse.x(),
            browse.bottom() - stripHeight + 1,
            18,
            stripHeight - 2,
            Component.literal("‹"),
            EditorButton.Kind.TOOL,
            () -> this.tabPage = Math.max(0, this.tabPage - 1)
         )
      );
      this.next = (EditorButton)this.addRenderableWidget(
         new EditorButton(
            browse.right() - 18,
            browse.bottom() - stripHeight + 1,
            18,
            stripHeight - 2,
            Component.literal("›"),
            EditorButton.Kind.TOOL,
            () -> this.tabPage = Math.min(this.tabStrip.pageCount() - 1, this.tabPage + 1)
         )
      );
      this.search.setResponder(text -> {
         this.query = text;
         this.itemPage = 0;
         this.browser.refresh();
      });
      this.presetSearch.setResponder(text -> {
         this.presetQuery = text;
         this.presets.filter(text);
      });
      boolean reserveButton = browserHeight >= 128;
      int compact = Math.max(18, Math.min(64, (reserve.height() - (reserveButton ? 28 : 0) - 4) / 3));
      int columns = this.slotBase == 6 ? 1 : 2;

      for (int ix = 0; ix < this.slotCount; ix++) {
         int slot = this.slotBase + ix;
         int sw = (reserve.width() - (columns - 1) * 4) / columns;
         EditorLayout.Box spot = new EditorLayout.Box(reserve.x() + ix % columns * (sw + 4), reserve.y() + 1 + ix / columns * (compact + 1), sw, compact);
         Component role = compact < 34 ? Component.empty() : Component.translatable("cnpcgun.editor.shortSlot" + slot);
         WeaponSlotButton button = (WeaponSlotButton)this.addRenderableWidget(
            new WeaponSlotButton(
               spot,
               role,
               () -> this.session.slot(slot),
               () -> this.slotBase == 0 && SafeJson.integer(this.session.values(), "weapon.primarySlot", 0) == slot,
               () -> {
                  this.targetSlot = slot;
                  this.subtitle = GuiSupport.tr("cnpcgun.editor.equipmentTarget", GuiSupport.tr("cnpcgun.gui.slot" + slot));
                  this.slots.forEach(item -> item.selected = false);
               }
            )
         );
         button.selected = this.targetSlot == slot;
         this.slots.add(button);
      }

      int start = main.x() + 2;

      for (int row = 0; row < 3; row++) {
         for (int column = 0; column < 9; column++) {
            this.inventory(
               9 + row * 9 + column,
               new EditorLayout.Box(start + column * (this.cell + 2), this.playerArea.y() + 10 + row * (this.cell + 2), this.cell, this.cell)
            );
         }
      }

      int hotbar = this.playerArea.y() + 12 + 3 * (this.cell + 2);

      for (int ix = 0; ix < 9; ix++) {
         this.inventory(ix, new EditorLayout.Box(start + ix * (this.cell + 2), hotbar, this.cell, this.cell));
      }

      this.inventory(40, new EditorLayout.Box(Math.min(this.playerArea.right() - this.cell, start + 9 * (this.cell + 2) + 2), hotbar, this.cell, this.cell));
      EditorLayout.Box unloadBox = reserveButton
         ? new EditorLayout.Box(reserve.x(), reserve.bottom() - 24, reserve.width(), 22)
         : new EditorLayout.Box(this.box.x() + 10, this.box.bottom() - 28, (this.box.width() - 26) / 2, 20);
      this.unload = (EditorButton)this.addRenderableWidget(new EditorButton(unloadBox, label("unequipItem"), EditorButton.Kind.DANGER, () -> {
         if (!this.session.pending && this.session.inventoryAllowed() && !this.session.slot(this.targetSlot).isEmpty()) {
            this.send.accept(GunNetwork.Operation.UNEQUIP, this.source());
         }
      }));
      if (reserveButton) {
         this.button(0, 1, Component.translatable("gui.done"), EditorButton.Kind.NORMAL, this.host::closeDialog);
      } else {
         this.button(1, 2, Component.translatable("gui.done"), EditorButton.Kind.NORMAL, this.host::closeDialog);
      }

      if (itemFocus) {
         this.setFocused(this.search);
      } else if (presetFocus) {
         this.setFocused(this.presetSearch);
      }

      this.browser.refresh();
      this.presetHash = this.itemPresets().hashCode();
      this.update();
   }

   private JsonObject source() {
      JsonObject input = new JsonObject();
      input.addProperty("slot", this.targetSlot);
      return input;
   }

   private void equipTemplate(GunNetwork.Operation operation, JsonObject input, int limit) {
      if (this.slotBase != 6) {
         this.send.accept(operation, input);
      } else {
         int maximum = Math.max(1, Math.min(64, limit));
         JsonObject initial = new JsonObject();
         initial.addProperty("throw.count", Math.min(this.creativeCount, maximum));
         EditorDialogs.form(this.host, label("throwStackCount"), new Parameter[]{Parameter.integer("throw.count", 1, 1, maximum)}, initial, values -> {
            if (this.session.creativeAllowed() && !this.session.pending) {
               this.creativeCount = values.get("throw.count").getAsInt();
               input.addProperty("count", this.creativeCount);
               this.send.accept(operation, input);
            }
         });
      }
   }

   private JsonArray itemPresets() {
      return this.session.array("itemPresets");
   }

   private List<EditorList.Row> presetRows() {
      List<EditorList.Row> rows = new ArrayList<>();

      for (JsonElement raw : this.itemPresets()) {
         if (raw.isJsonObject()) {
            JsonObject item = raw.getAsJsonObject();
            String id = SafeJson.string(item, "id", "");
            if (!id.isEmpty() && (this.slotBase != 6 || SafeJson.bool(item, "throwable", false))) {
               rows.add(new EditorList.Row(id, SafeJson.string(item, "title", id), SafeJson.string(item, "itemName", ""), true));
            }
         }
      }

      return rows;
   }

   private CreativeCatalogue.Tab currentTab() {
      return this.catalogue.stream().filter(tab -> tab.id().equals(this.tabId)).findFirst().orElse(this.catalogue.isEmpty() ? null : this.catalogue.get(0));
   }

   private String tabTitle() {
      CreativeCatalogue.Tab tab = this.currentTab();
      return tab == null ? GuiSupport.tr("cnpcgun.editor.creativeTabs") : tab.title();
   }

   private void createPreset() {
      JsonObject initial = new JsonObject();
      initial.addProperty("name", this.session.slot(this.targetSlot).getHoverName().getString());
      EditorDialogs.form(this.host, label("createItemPreset"), new Parameter[]{new Parameter("name", "string", "", null, null, null, "")}, initial, values -> {
         JsonObject input = new JsonObject();
         input.add("name", values.get("name"));
         input.addProperty("npcSlot", this.targetSlot);
         this.send.accept(GunNetwork.Operation.SAVE_ITEM_PRESET, input);
      });
   }

   private void inventory(int index, EditorLayout.Box spot) {
      WeaponSlotButton button = (WeaponSlotButton)this.addRenderableWidget(
         new WeaponSlotButton(
            spot,
            Component.empty(),
            () -> this.minecraft.player == null ? ItemStack.EMPTY : this.minecraft.player.getInventory().getItem(index),
            () -> false,
            () -> {
               if (this.minecraft.player != null
                  && !this.session.pending
                  && this.session.inventoryAllowed()
                  && (this.slotBase != 6 || throwable(this.minecraft.player.getInventory().getItem(index)))) {
                  JsonObject input = this.source();
                  input.addProperty("inventorySlot", index);
                  this.send.accept(GunNetwork.Operation.EXCHANGE, input);
               }
            }
         )
      );
      this.slots.add(button);
   }

   private void update() {
      boolean available = this.minecraft.player != null && this.minecraft.level != null && !this.session.pending;

      for (int i = 0; i < this.slots.size(); i++) {
         this.slots.get(i).active = available
            && (
               i < this.slotCount
                  || this.session.inventoryAllowed()
                     && !this.slots.get(i).item().is(CustomItems.wand)
                     && (this.slotBase == 0 || throwable(this.slots.get(i).item()))
            );
         if (i < this.slotCount) {
            this.slots.get(i).selected = this.slotBase + i == this.targetSlot;
         }
      }

      this.create.active = available && this.session.creativeAllowed() && !this.session.slot(this.targetSlot).isEmpty();
      this.create.setTooltip(!this.session.creativeAllowed() ? Tooltip.create(label("creativePermission")) : null);
      this.remove.active = available && !this.presetId.isEmpty() && this.session.inventoryAllowed();
      this.presets.active = available;
      this.tabStrip.active = available && !this.catalogue.isEmpty();
      this.browser.active = available && this.session.creativeAllowed();
      this.unload.active = available && this.session.inventoryAllowed() && !this.session.slot(this.targetSlot).isEmpty();
      this.previous.active = this.tabPage > 0;
      this.next.active = this.tabPage + 1 < this.tabStrip.pageCount();
   }

   @Override
   public void tick() {
      super.tick();
      this.search.tick();
      this.presetSearch.tick();
      if (this.session.status.startsWith("cnpcgun.error")) {
         this.error = GuiSupport.tr(this.session.status);
      } else if (!this.catalogue.isEmpty()) {
         this.error = "";
      }

      if (this.presetHash != this.itemPresets().hashCode()) {
         this.presetHash = this.itemPresets().hashCode();
         this.presets.replaceRows(this.presetRows());
      }

      this.update();
   }

   @Override
   public void render(GuiGraphics g, int mx, int my, float partialTick) {
      super.render(g, mx, my, partialTick);
      EditorStyle.text(g, this.font, this.tabTitle(), this.browseArea.x() + 2, this.browseArea.y() + 6, this.categoryWidth, -14077902);
      EditorStyle.text(
         g, this.font, GuiSupport.tr("cnpcgun.editor.playerInventory"), this.playerArea.x(), this.playerArea.y() + 1, this.playerArea.width(), -9932693
      );
      if (this.unload.isMouseOver(mx, my)) {
         g.renderTooltip(this.font, label("unequipHint"), mx, my);
      }

      for (int i = 0; i < this.slots.size(); i++) {
         WeaponSlotButton slot = this.slots.get(i);
         if (slot.isMouseOver(mx, my)) {
            if (i < this.slotCount) {
               String role = GuiSupport.tr("cnpcgun.gui.slot" + (this.slotBase + i));
               String item = slot.item().isEmpty() ? GuiSupport.tr("cnpcgun.editor.emptySlot") : slot.item().getHoverName().getString();
               g.renderTooltip(this.font, Component.literal(role + " · " + item), mx, my);
            } else if (!slot.item().isEmpty()) {
               g.renderTooltip(this.font, slot.item(), mx, my);
            } else {
               g.renderTooltip(this.font, label("exchangeEmpty"), mx, my);
            }
         }
      }

      this.browser.tooltip(g, mx, my);
      this.tabStrip.tooltip(g, mx, my);
   }

   private final class Browser extends AbstractWidget {
      private List<InventoryDialog.Browser.Entry> shown = List.of();
      private final int columns;
      private final int rows;
      private final int size;

      Browser(EditorLayout.Box spot) {
         super(spot.x(), spot.y(), spot.width(), spot.height(), InventoryDialog.label("creativeTabs"));
         this.size = Math.min(24, Math.max(18, spot.height()));
         this.columns = Math.max(1, (spot.width() - 4) / this.size);
         this.rows = Math.max(1, (spot.height() - 2) / this.size);
      }

      int pageCount() {
         return Math.max(1, (this.shown.size() + this.columns * this.rows - 1) / (this.columns * this.rows));
      }

      void refresh() {
         List<InventoryDialog.Browser.Entry> result = new ArrayList<>();
         CreativeCatalogue.Tab tab = InventoryDialog.this.currentTab();
         String needle = InventoryDialog.this.query.toLowerCase(Locale.ROOT).trim();
         if (tab != null) {
            for (int i = 0; i < tab.items().size(); i++) {
               ItemStack template = tab.items().get(i);
               int fingerprint = CreativeCatalogue.fingerprint(template);
               ItemStack item = template.copy();
               String text = item.getHoverName().getString() + " " + BuiltInRegistries.ITEM.getKey(item.getItem());
               if (!item.isEmpty()
                  && (InventoryDialog.this.slotBase == 0 || InventoryDialog.throwable(item))
                  && text.toLowerCase(Locale.ROOT).contains(needle)) {
                  result.add(new InventoryDialog.Browser.Entry(tab, i, fingerprint, item));
               }
            }
         }

         this.shown = List.copyOf(result);
         InventoryDialog.this.itemPage = Math.max(0, Math.min(InventoryDialog.this.itemPage, this.pageCount() - 1));
      }

      private InventoryDialog.Browser.Entry at(double x, double y) {
         if (!this.isMouseOver(x, y)) {
            return null;
         } else {
            int column = (int)(x - this.getX() - 2.0) / this.size;
            int row = (int)(y - this.getY() - 1.0) / this.size;
            if (column >= 0 && column < this.columns && row >= 0 && row < this.rows) {
               int index = InventoryDialog.this.itemPage * this.columns * this.rows + row * this.columns + column;
               return index < this.shown.size() ? this.shown.get(index) : null;
            } else {
               return null;
            }
         }
      }

      protected void renderWidget(GuiGraphics g, int mx, int my, float partialTick) {
         EditorStyle.panel(g, new EditorLayout.Box(this.getX(), this.getY(), this.width, this.height), -1119006);
         g.enableScissor(this.getX() + 1, this.getY() + 1, this.getX() + this.width - 1, this.getY() + this.height - 1);
         int first = InventoryDialog.this.itemPage * this.columns * this.rows;

         for (int i = 0; i < this.columns * this.rows && first + i < this.shown.size(); i++) {
            InventoryDialog.Browser.Entry entry = this.shown.get(first + i);
            int x = this.getX() + 2 + i % this.columns * this.size;
            int y = this.getY() + 1 + i / this.columns * this.size;
            EditorStyle.slot(g, x, y, this.size - 1, this.at(mx, my) == entry);
            g.renderItem(entry.item, x + (this.size - 16) / 2, y + (this.size - 16) / 2);
         }

         g.disableScissor();
      }

      public boolean mouseClicked(double x, double y, int button) {
         InventoryDialog.Browser.Entry entry = this.at(x, y);
         if (button == 0 && entry != null && this.active) {
            JsonObject input = InventoryDialog.this.source();
            input.addProperty("tab", entry.tab.id());
            input.addProperty("itemIndex", entry.index);
            input.addProperty("fingerprint", entry.fingerprint);
            InventoryDialog.this.equipTemplate(GunNetwork.Operation.EQUIP_CREATIVE, input, entry.item.getMaxStackSize());
            return true;
         } else {
            return false;
         }
      }

      public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
      double delta = scrollY;
         if (!this.isMouseOver(x, y)) {
            return false;
         } else {
            InventoryDialog.this.itemPage = Math.max(0, Math.min(this.pageCount() - 1, InventoryDialog.this.itemPage - (int)Math.signum(delta)));
            return true;
         }
      }

      void tooltip(GuiGraphics g, int x, int y) {
         InventoryDialog.Browser.Entry entry = this.at(x, y);
         if (entry != null) {
            g.renderTooltip(InventoryDialog.this.font, entry.item, x, y);
         }
      }

      protected void updateWidgetNarration(NarrationElementOutput output) {
         this.defaultButtonNarrationText(output);
      }

      private record Entry(CreativeCatalogue.Tab tab, int index, int fingerprint, ItemStack item) {
      }
   }

   private final class TabStrip extends AbstractWidget {
      private final int capacity;
      private final int size;
      private final Map<String, ItemStack> icons = new HashMap<>();

      TabStrip(EditorLayout.Box spot) {
         super(spot.x(), spot.y(), spot.width(), spot.height(), InventoryDialog.label("creativeTabs"));
         this.size = spot.height();
         this.capacity = Math.max(1, spot.width() / this.size);
      }

      int pageCount() {
         return Math.max(1, (InventoryDialog.this.catalogue.size() + this.capacity - 1) / this.capacity);
      }

      private CreativeCatalogue.Tab at(double x, double y) {
         if (!this.isMouseOver(x, y)) {
            return null;
         } else {
            int column = (int)(x - this.getX()) / this.size;
            int index = InventoryDialog.this.tabPage * this.capacity + column;
            return column >= 0 && column < this.capacity && index < InventoryDialog.this.catalogue.size() ? InventoryDialog.this.catalogue.get(index) : null;
         }
      }

      protected void renderWidget(GuiGraphics g, int mx, int my, float partialTick) {
         InventoryDialog.this.tabPage = Math.max(0, Math.min(InventoryDialog.this.tabPage, this.pageCount() - 1));

         for (int i = 0; i < this.capacity && InventoryDialog.this.tabPage * this.capacity + i < InventoryDialog.this.catalogue.size(); i++) {
            CreativeCatalogue.Tab tab = InventoryDialog.this.catalogue.get(InventoryDialog.this.tabPage * this.capacity + i);
            int x = this.getX() + i * this.size;
            EditorStyle.slot(g, x, this.getY(), this.size - 1, tab.id().equals(InventoryDialog.this.tabId) || this.at(mx, my) == tab);
            ItemStack icon = this.icons
               .computeIfAbsent(
                  tab.id(),
                  ignored -> InventoryDialog.this.slotBase == 6
                     ? tab.items().stream().map(ItemStack::copy).filter(InventoryDialog::throwable).findFirst().orElse(ItemStack.EMPTY)
                     : (tab.items().isEmpty() ? ItemStack.EMPTY : tab.items().get(0).copy())
               );
            if (!icon.isEmpty()) {
               g.renderItem(icon, x + (this.size - 16) / 2, this.getY() + (this.size - 16) / 2);
            }

            if (tab.id().equals(InventoryDialog.this.tabId)) {
               g.hLine(x + 2, x + this.size - 4, this.getY() + this.size - 3, -11047869);
            }
         }
      }

      public boolean mouseClicked(double x, double y, int button) {
         CreativeCatalogue.Tab tab = this.at(x, y);
         if (this.active && button == 0 && tab != null) {
            InventoryDialog.this.tabId = tab.id();
            InventoryDialog.this.itemPage = 0;
            InventoryDialog.this.query = "";
            InventoryDialog.this.search.setValue("");
            InventoryDialog.this.browser.refresh();
            return true;
         } else {
            return false;
         }
      }

      void tooltip(GuiGraphics g, int x, int y) {
         CreativeCatalogue.Tab tab = this.at(x, y);
         if (tab != null) {
            g.renderTooltip(InventoryDialog.this.font, Component.literal(tab.title()), x, y);
         }
      }

      protected void updateWidgetNarration(NarrationElementOutput output) {
         this.defaultButtonNarrationText(output);
      }
   }
}
