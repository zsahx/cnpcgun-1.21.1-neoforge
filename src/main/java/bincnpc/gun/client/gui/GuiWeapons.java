package bincnpc.gun.client.gui;

import bincnpc.gun.client.EditorSession;
import bincnpc.gun.client.gui.editor.ActionPicker;
import bincnpc.gun.client.gui.editor.EditorButton;
import bincnpc.gun.client.gui.editor.EditorDialogs;
import bincnpc.gun.client.gui.editor.EditorDraft;
import bincnpc.gun.client.gui.editor.EditorForm;
import bincnpc.gun.client.gui.editor.EditorLayout;
import bincnpc.gun.client.gui.editor.EditorList;
import bincnpc.gun.client.gui.editor.EditorStyle;
import bincnpc.gun.client.gui.editor.EditorTextField;
import bincnpc.gun.client.gui.editor.EditorToast;
import bincnpc.gun.client.gui.editor.GraphCanvas;
import bincnpc.gun.client.gui.editor.GraphDraft;
import bincnpc.gun.client.gui.editor.InventoryDialog;
import bincnpc.gun.client.gui.editor.NodeDialog;
import bincnpc.gun.client.gui.editor.NpcPreview;
import bincnpc.gun.client.gui.editor.WeaponSlotButton;
import bincnpc.gun.common.api.SafeJson;
import bincnpc.gun.common.config.ConfigSchema;
import bincnpc.gun.common.config.Parameter;
import bincnpc.gun.common.network.GunNetwork;
import bincnpc.gun.slashblade.action.ActionConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import noppes.npcs.client.gui.mainmenu.GuiNpcDisplay;
import noppes.npcs.entity.EntityNPCInterface;

public final class GuiWeapons extends Screen implements EditorDialogs.Host {
   public static final int TAB_ID = 87;
   private final EntityNPCInterface npc;
   private final EditorSession session;
   private final EditorDraft draft = new EditorDraft();
   private final Map<String, GuiWeapons.ActionEdit> actionEdits = new LinkedHashMap<>();
   private final Map<String, Integer> scrollPositions = new HashMap<>();
   private final Map<String, Map<String, List<String>>> invalidText = new HashMap<>();
   private final Map<GuiWeapons.Page, String> searches = new EnumMap<>(GuiWeapons.Page.class);
   private final Deque<Screen> dialogs = new ArrayDeque<>();
   private final List<GuiWeapons.Guard> guards = new ArrayList<>();
   private final List<GuiWeapons.Text> texts = new ArrayList<>();
   private final List<WeaponSlotButton> weaponButtons = new ArrayList<>();
   private final EditorToast toast = new EditorToast();
   private final Map<AbstractWidget, Tooltip> enabledTooltips = new HashMap<>();
   private List<FormattedCharSequence> queuedTooltip;
   private ClientTooltipPositioner tooltipPositioner = DefaultTooltipPositioner.INSTANCE;
   private EditorLayout layout;
   private EditorForm form;
   private EditorList masterList;
   private GraphCanvas canvas;
   private GraphDraft graph;
   private EditorButton save;
   private EditorButton discard;
   private EditorButton previewButton;
   private NpcPreview preview;
   private EditorTextField searchField;
   private GuiWeapons.Group openGroup = GuiWeapons.Group.INVENTORY;
   private boolean saving;
   private Runnable saveContinuation;
   private int saveDelay;
   private GuiWeapons.Page page = GuiWeapons.Page.WEAPONS;
   private String formKey = "";
   private String masterKey = "";
   private String selectedAction = "";
   private String selectedPreset = "";
   private String selectedNode = "";
   private String selectedGraph = "";
   private String pageTitle = "";
   private String pageHint = "";
   private String localError = "";
   private String actionCategory = "all";
   private int titleOffset;
   private int titleReserved;
   private int weaponSlot;
   private int actionTab;
   private int selectedStage;
   private int throwSlot;
   private boolean opened;
   private boolean closed;
   private boolean actionDetail;
   private boolean canvasExpanded;
   private boolean rebuildPending;
   private GraphCanvas.View canvasView;

   public GuiWeapons(EntityNPCInterface npc) {
      super(Component.translatable("cnpcgun.editor.title"));
      this.npc = npc;
      this.session = new EditorSession(npc.getUUID());
   }

   public void receive(GunNetwork.Response packet) {
      if (this.session.receive(packet)) {
         this.localError = packet.code().equals("SUCCESS") ? "" : GuiSupport.tr("cnpcgun.error." + packet.code().toLowerCase(Locale.ROOT));
         if (this.session.ready()) {
            this.draft.reconcile(this.session.values());
            if (packet.code().equals("SUCCESS") && this.session.completedOperation == GunNetwork.Operation.PRESET) {
               this.actionEdits.clear();
               this.graph = null;
               this.selectedGraph = "";
               this.selectedNode = "";
               this.canvas = null;
               this.canvasView = null;
               if (this.preview != null) {
                  this.preview.stop();
               }
            }

            if (!this.saving) {
               this.actionEdits.entrySet().removeIf(entry -> !entry.getValue().dirty() && this.session.actionInfo(entry.getKey()) == null);
               this.actionEdits
                  .replaceAll((id, edited) -> (GuiWeapons.ActionEdit)(edited.dirty() ? edited : new GuiWeapons.ActionEdit(this.session.action(id))));
               JsonElement current = this.session.snapshot.get("graph");
               if (this.graph != null && !this.graph.dirty() && current != null && current.isJsonObject() && !this.graph.graph.equals(current)) {
                  if (this.canvas != null) {
                     this.canvasView = this.canvas.view();
                  }

                  this.loadGraph(current.getAsJsonObject(), "");
               }

               if (this.graph != null && !this.graph.dirty() && (current == null || current.isJsonNull())) {
                  this.graph = null;
                  this.selectedGraph = "";
                  this.canvas = null;
                  this.canvasView = null;
               }
            }
         }

         if (packet.code().equals("SUCCESS")) {
            this.operationToast(this.session.completedOperation);
         }

         if (this.minecraft != null && !this.closed) {
            this.rebuild();
         }
      }
   }

   private EditorLayout.Box body() {
      return this.canvasExpanded ? new EditorLayout.Box(8, 42, this.width - 16, this.height - 76) : this.layout.body;
   }

   private static Component label(String key) {
      return Component.translatable("cnpcgun.editor." + key);
   }

   private static String tr(String key, Object... args) {
      return GuiSupport.tr("cnpcgun.editor." + key, args);
   }

   private static String name(String id) {
      return Language.getInstance().has("cnpcgun.value." + id.toLowerCase(Locale.ROOT))
         ? GuiSupport.value(new JsonPrimitive(id))
         : id.replaceFirst("^(user:|custom:)", "").replace('_', ' ');
   }

   private JsonObject object(String key) {
      JsonElement value = this.session.snapshot.get(key);
      return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
   }

   private static boolean bool(JsonObject object, String key) {
      return SafeJson.bool(object, key, false);
   }

   private boolean blade() {
      return this.session.has("slashblade.attack");
   }

   private boolean gun() {
      return this.session.has("gun.fire");
   }

   private boolean running() {
      return bool(this.object("comboState"), "running");
   }

   private boolean canSwitch() {
      return bool(this.object("capabilities"), "canSwitchAway");
   }

   private JsonElement value(String key) {
      return this.draft.value(this.session.values(), key);
   }

   private boolean dirty() {
      return this.draft.dirty() || this.actionEdits.values().stream().anyMatch(GuiWeapons.ActionEdit::dirty) || this.graph != null && this.graph.dirty();
   }

   private boolean busy() {
      return this.session.pending || this.saving;
   }

   private void text(String text, EditorLayout.Box box, int y, int color) {
      this.texts.add(new GuiWeapons.Text(text, box.x() + 8, box.y() + y, box.width() - 16, color));
   }

   private EditorButton button(EditorLayout.Box box, Component title, EditorButton.Kind kind, Runnable clicked) {
      return (EditorButton)this.addRenderableWidget(new EditorButton(box, title, kind, clicked));
   }

   private EditorButton serverButton(EditorLayout.Box box, Component title, BooleanSupplier allowed, String reason, Runnable clicked) {
      return this.guardedButton(box, title, EditorButton.Kind.NORMAL, allowed, reason, clicked);
   }

   private EditorButton guardedButton(EditorLayout.Box box, Component title, EditorButton.Kind kind, BooleanSupplier allowed, String reason, Runnable clicked) {
      EditorButton button = this.button(box, title, kind, clicked);
      this.guards.add(new GuiWeapons.Guard(button, allowed, reason));
      return button;
   }

   private void rebuild() {
      this.rebuildPending = true;
   }

   private void rebuildNow() {
      if (this.rebuildPending) {
         this.rebuildPending = false;
         this.clearWidgets();
         this.init();
      }
   }

   protected void init() {
      boolean listFocused = this.masterList != null && this.getFocused() == this.masterList;
      boolean searchFocused = this.searchField != null && this.searchField.isFocused();
      if (this.form != null) {
         this.scrollPositions.put(this.formKey, this.form.scrollOffset());
         this.invalidText.put(this.formKey, this.form.invalidText());
      }

      if (this.masterList != null) {
         this.scrollPositions.put(this.masterKey, this.masterList.scrollOffset());
      }

      if (this.canvas != null) {
         this.canvasView = this.canvas.view();
      }

      this.form = null;
      this.masterList = null;
      this.canvas = null;
      this.searchField = null;
      this.previewButton = null;
      this.guards.clear();
      this.texts.clear();
      this.weaponButtons.clear();
      this.enabledTooltips.clear();
      this.layout = new EditorLayout(this.width, this.height);
      this.button(new EditorLayout.Box(8, 7, 84, 20), Component.translatable("menu.display"), EditorButton.Kind.BACK, this::onClose)
         .setTooltip(Tooltip.create(label("returnAppearance")));
      this.button(new EditorLayout.Box(this.width - 30, 7, 22, 20), Component.translatable("cnpcgun.gui.refresh"), EditorButton.Kind.REFRESH, this::refresh)
         .setTooltip(Tooltip.create(Component.translatable("cnpcgun.gui.refresh")));
      if (!this.canvasExpanded) {
         this.navigation();
      }

      this.discard = this.button(this.layout.discardButton(), label("discard"), EditorButton.Kind.NORMAL, this::discardChanges);
      this.save = this.button(this.layout.saveButton(), label("saveChanges"), EditorButton.Kind.PRIMARY, () -> this.saveChanges(null));
      this.pageTitle = tr(this.page.key);
      this.pageHint = tr(this.page.key + "Hint");
      this.titleOffset = 0;
      this.titleReserved = 0;
      if (!this.session.ready()) {
         this.empty(tr("loadingTitle"), GuiSupport.tr(this.session.status), false);
         if (!this.opened) {
            this.opened = true;
            this.session.send(GunNetwork.Operation.OPEN, new JsonObject());
         }
      } else {
         switch (this.page) {
            case WEAPONS:
               this.weapons();
               break;
            case SHOOTING:
               this.shooting();
               break;
            case THROWING:
               this.throwing();
               break;
            case ACTIONS:
               this.actions();
               break;
            case COMBOS:
               this.combos();
               break;
            case PRESETS:
               this.presets();
               break;
            case ADVANCED:
               this.advanced();
               break;
            case BLADE_SETTINGS:
               this.bladeSettings();
         }
      }

      if (this.form != null) {
         this.form.scrollTo(this.scrollPositions.getOrDefault(this.formKey, 0));
         this.form.restoreInvalidText(this.invalidText.getOrDefault(this.formKey, Map.of()));
      }

      if (listFocused && this.masterList != null) {
         this.setFocused(this.masterList);
      }

      if (searchFocused && this.searchField != null) {
         this.setFocused(this.searchField);
      }

      this.updateControls();

      for (Screen dialog : this.dialogs) {
         if (dialog.width != this.width || dialog.height != this.height) {
            dialog.resize(this.minecraft, this.width, this.height);
         }
      }
   }

   private void navigation() {
      int y = this.layout.sidebar.y();

      for (GuiWeapons.Group group : GuiWeapons.Group.values()) {
         boolean expanded = this.openGroup == group;
         EditorButton drawer = this.button(
            new EditorLayout.Box(this.layout.sidebar.x(), y, this.layout.sidebar.width(), 22),
            Component.literal((expanded ? "▾ " : "▸ ") + tr(group.key)),
            EditorButton.Kind.TOOL,
            () -> {
               this.openGroup = this.openGroup == group ? null : group;
               this.rebuild();
            }
         );
         drawer.setTooltip(Tooltip.create(label(group.key)));
         y += 24;
         if (expanded) {
            for (GuiWeapons.Page chosen : group.pages) {
               EditorButton tab = this.button(
                  new EditorLayout.Box(this.layout.sidebar.x() + 6, y, this.layout.sidebar.width() - 6, 18),
                  label(chosen.key),
                  EditorButton.Kind.NAVIGATION,
                  () -> {
                     if (this.form != null && this.form.invalid()) {
                        this.localError = tr("invalidField");
                     } else {
                        if (this.preview != null && chosen != GuiWeapons.Page.ACTIONS && chosen != GuiWeapons.Page.COMBOS) {
                           this.preview.dispose();
                           this.preview = null;
                        }

                        this.page = chosen;
                        this.canvasExpanded = false;
                        this.localError = "";
                        this.rebuild();
                     }
                  }
               );
               tab.selected = this.page == chosen;
               y += 20;
            }
         }
      }
   }

   private EditorForm newForm(EditorLayout.Box box, String key) {
      this.formKey = key;
      this.form = (EditorForm)this.addRenderableWidget(new EditorForm(this.font, box, (title, options, chosen) -> EditorDialogs.choose(this, title, options, chosen)));
      return this.form;
   }

   private void field(Parameter p) {
      this.form.field(p, this.value(p.id), () -> this.editable(p), this.fieldReason(p), checked -> this.change(p.id, checked));
   }

   private String fieldReason(Parameter p) {
      return tr((p.id.startsWith("ammo.") || p.id.startsWith("throw.")) && !this.session.inventoryAllowed() ? "inventoryDenied" : "weaponCannotControl");
   }

   private boolean editable(Parameter p) {
      return (p.capability.isEmpty() || this.session.has(p.capability))
         && (!p.id.startsWith("ammo.") && !p.id.startsWith("throw.") || this.session.inventoryAllowed());
   }

   private void change(String key, JsonElement checked) {
      this.draft.set(this.session.values(), key, checked);
      this.localError = "";
      if (key.equals("ammo.defaultReserve") && checked.getAsInt() > this.value("ammo.reserveLimit").getAsInt()) {
         this.draft.set(this.session.values(), "ammo.reserveLimit", checked);
      }

      if (key.equals("ammo.infinite") || key.equals("gun.reloadSpeedEnabled")) {
         this.rebuild();
      }
   }

   private void segments(String key) {
      Parameter p = ConfigSchema.get(key);
      this.form.segments(p, this.value(key), () -> this.editable(p), this.fieldReason(p), checked -> this.change(key, checked));
   }

   private void booleanSegments(String key) {
      Parameter p = ConfigSchema.get(key);
      Parameter display = Parameter.choice(key, "RELOAD_NATIVE", "RELOAD_NATIVE", "RELOAD_CUSTOM").requires(p.capability);
      this.form
         .segments(
            display,
            new JsonPrimitive(this.value(key).getAsBoolean() ? "RELOAD_CUSTOM" : "RELOAD_NATIVE"),
            () -> this.editable(p),
            this.fieldReason(p),
            checked -> this.change(key, new JsonPrimitive(checked.getAsString().equals("RELOAD_CUSTOM")))
         );
   }

   private void toggleNumber(String flag, String number) {
      Parameter p = ConfigSchema.get(number);
      this.form
         .toggleNumber(
            ConfigSchema.get(flag),
            this.value(flag),
            p,
            this.value(number),
            () -> this.editable(p),
            tr("weaponCannotControl"),
            checked -> this.change(flag, checked),
            checked -> this.change(number, checked)
         );
   }

   private void fields(String title, Predicate<Parameter> include) {
      this.form.heading(title);

      for (Parameter p : ConfigSchema.all()) {
         if (include.test(p)) {
            this.field(p);
         }
      }
   }

   private void empty(String title, String description, boolean goWeapons) {
      EditorLayout.Box area = this.body();
      this.text(title, area, 16, -14077902);
      this.text(description, area, 34, -9932693);
      if (goWeapons) {
         this.button(
            new EditorLayout.Box(area.x() + 8, area.y() + 57, Math.min(160, area.width() - 16), 20), label("openWeapons"), EditorButton.Kind.NORMAL, () -> {
               this.page = GuiWeapons.Page.WEAPONS;
               this.rebuild();
            }
         );
      }
   }

   private void weapons() {
      EditorLayout.Box area = this.body();

      for (int i = 0; i < 6; i++) {
         int slot = i;
         EditorLayout.Box box = this.layout.weaponSlot(i);
         String role = "cnpcgun." + (box.width() < 48 ? "editor.shortSlot" : "gui.slot") + i;
         WeaponSlotButton button = (WeaponSlotButton)this.addRenderableWidget(
            new WeaponSlotButton(
               box, Component.translatable(role), () -> this.session.slot(slot), () -> this.value("weapon.primarySlot").getAsInt() == slot, () -> {
                  if (!this.busy() && (this.form == null || !this.form.invalid())) {
                     this.weaponSlot = slot;
                     this.rebuild();
                  }
               }, () -> {
                  if (!this.busy() && (this.form == null || !this.form.invalid())) {
                     this.weaponSlot = slot;
                     this.rebuild();
                     this.saveChanges(() -> this.openDialog(new InventoryDialog(this, this.session, slot, this::mutate)));
                  }
               }
            )
         );
         button.selected = this.weaponSlot == i;
         this.weaponButtons.add(button);
      }

      this.titleReserved = Math.min(172, area.width() - 48);
      this.serverButton(
         new EditorLayout.Box(this.layout.workspace.right() - this.titleReserved, this.layout.workspace.y() - 2, this.titleReserved, 20),
         label("equipWeapon"),
         () -> this.session.inventoryAllowed()
            && !this.session.slot(this.weaponSlot).isEmpty()
            && this.weaponSlot != this.value("weapon.primarySlot").getAsInt(),
         tr("equipHint"),
         () -> {
            JsonObject input = new JsonObject();
            input.addProperty("slot", this.weaponSlot);
            this.mutate(GunNetwork.Operation.PRIMARY_WEAPON, input);
         }
      );
      this.newForm(this.layout.weaponForm(), "weapon:" + this.weaponSlot).compactSlots();
      String slotKey = "weapon.slot" + this.weaponSlot + ".";
      this.field(ConfigSchema.get(slotKey + "priority"));

      for (String key : List.of("minRange", "maxRange", "maxHealth")) {
         this.toggleNumber(slotKey + key + "Enabled", slotKey + key);
      }

      this.pageHint = tr(
         "selectedWeapon",
         GuiSupport.tr("cnpcgun.gui.slot" + this.weaponSlot),
         this.session.slot(this.weaponSlot).isEmpty() ? tr("emptySlot") : this.session.slot(this.weaponSlot).getHoverName().getString()
      );
      this.fields(tr("automaticSwitch"), p -> p.id.startsWith("weapon.") && !p.id.startsWith("weapon.slot") && !p.id.equals("weapon.primarySlot"));
   }

   private void shooting() {
      this.newForm(this.body(), "shooting");
      Set<String> basic = Set.of(
         "gun.enabled",
         "gun.autoAttack",
         "gun.minRange",
         "gun.range",
         "gun.requireAim",
         "gun.moveFire",
         "gun.jumpFire",
         "gun.blindFire",
         "gun.lostSight",
         "gun.prefire",
         "gun.accuracy"
      );
      this.fields(tr("engagement"), px -> basic.contains(px.id) && !Set.of("gun.lostSight", "gun.prefire").contains(px.id));
      this.segments("gun.prefire");
      this.segments("gun.lostSight");
      this.form.heading(tr("firePattern"));

      for (Parameter p : ConfigSchema.all()) {
         if (p.id.startsWith("gun.") && !p.id.startsWith("gun.reload") && !p.id.equals("gun.disableIdleAnimation") && !basic.contains(p.id) && !accuracy(p)) {
            this.field(p);
         }
      }

      this.fields(tr("accuracy"), GuiWeapons::accuracy);
      this.ammunition();
   }

   private static boolean accuracy(Parameter p) {
      String key = p.id.toLowerCase(Locale.ROOT);
      return key.startsWith("gun.") && (key.contains("spread") || key.contains("recoil"));
   }

   private void throwing() {
      EditorLayout.Box area = this.body();
      int cardHeight = Math.max(38, Math.min(72, area.height() / 3));
      int cardWidth = (area.width() - 8) / 3;

      for (int i = 0; i < 3; i++) {
         int selected = i;
         int wireSlot = 6 + i;
         EditorLayout.Box card = new EditorLayout.Box(area.x() + i * (cardWidth + 4), area.y(), cardWidth, cardHeight);
         WeaponSlotButton button = (WeaponSlotButton)this.addRenderableWidget(
            new WeaponSlotButton(card, Component.translatable("cnpcgun.gui.slot" + wireSlot), () -> this.session.slot(wireSlot), () -> false, () -> {
               if (!this.busy() && (this.form == null || !this.form.invalid())) {
                  this.throwSlot = selected;
                  this.rebuild();
               }
            }, () -> {
               if (!this.busy() && (this.form == null || !this.form.invalid())) {
                  this.throwSlot = selected;
                  this.saveChanges(() -> this.openDialog(new InventoryDialog(this, this.session, wireSlot, this::mutate)));
               }
            })
         );
         button.selected = this.throwSlot == i;
         this.weaponButtons.add(button);
      }

      this.newForm(area.below(cardHeight + 4), "throw:" + this.throwSlot);
      this.form.heading(tr("throwGlobal"));
      this.field(ConfigSchema.get("throw.enabled"));
      this.field(ConfigSchema.get("throw.cooldown"));
      String key = "throw.slot" + this.throwSlot + ".";
      JsonArray quotas = this.session.array("throwRemaining");
      JsonElement quota = (JsonElement)(this.throwSlot < quotas.size() ? quotas.get(this.throwSlot) : JsonNull.INSTANCE);
      int remaining = quota.isJsonPrimitive() && quota.getAsJsonPrimitive().isNumber() ? quota.getAsInt() : 0;
      this.form
         .heading(GuiSupport.tr("cnpcgun.gui.slot" + (6 + this.throwSlot)) + " · " + (remaining < 0 ? tr("throwUnlimited") : tr("throwRemaining", remaining)));
      this.field(ConfigSchema.get(key + "once"));
      this.segments(key + "timing");
      this.segments(key + "trajectory");

      for (String id : List.of("windup", "recovery", "speed", "priority", "minRange", "maxRange")) {
         this.field(ConfigSchema.get(key + id));
      }

      this.segments(key + "area");

      for (String id : List.of("deviation", "lostTarget", "maxElevation", "maxDepression", "calculationRange", "prediction")) {
         this.field(ConfigSchema.get(key + id));
      }

      this.pageHint = tr("throwingHint");
   }

   private void ammunition() {
      this.form.heading(tr("reloadSpeed"));
      this.booleanSegments("gun.reloadSpeedEnabled");
      this.field(ConfigSchema.get("gun.reloadMinTicks"));
      this.field(ConfigSchema.get("gun.reloadMaxTicks"));
      this.form.heading(tr("reloadPolicy"));
      this.field(ConfigSchema.get("ammo.autoReload"));
      this.field(ConfigSchema.get("ammo.combatReload"));
      this.segments("ammo.reloadTiming");
      this.field(ConfigSchema.get("ammo.partialReload"));
      this.field(ConfigSchema.get("ammo.reloadThreshold"));
      this.field(ConfigSchema.get("ammo.noAmmoBehavior"));
      this.form.heading(tr("ammo"));
      this.field(ConfigSchema.get("ammo.infinite"));
      this.field(ConfigSchema.get("gun.disableIdleAnimation"));
      this.field(ConfigSchema.get("ammo.defaultReserve"));
      this.form.heading(tr("finiteSources"));
      this.segments("ammo.source");
      this.segments("ammo.priority");
      this.toggleNumber("ammo.killSupply", "ammo.killReward");
      this.field(ConfigSchema.get("ammo.dropSupply"));
      this.form.heading(tr("reservePolicy"));
      this.field(ConfigSchema.get("ammo.reserveLimit"));
      this.field(ConfigSchema.get("ammo.minimumReserve"));

      for (JsonElement raw : this.session.array("slotAmmo")) {
         if (raw.isJsonObject()) {
            JsonObject ammo = raw.getAsJsonObject();
            int reserve = SafeJson.integer(ammo, "reserve", -1);
            int slot = SafeJson.integer(ammo, "slot", -1);
            if (reserve >= 0 && slot >= 0 && slot < 6) {
               this.form.heading(GuiSupport.tr("cnpcgun.gui.slot" + slot) + " · " + tr("rounds", reserve));
            }
         }
      }
   }

   private GuiWeapons.ActionEdit actionEdit(String id) {
      return this.actionEdits.computeIfAbsent(id, key -> new GuiWeapons.ActionEdit(this.session.action(key)));
   }

   private EditorLayout.Box editingPane(EditorLayout.Box area, Runnable play) {
      int previewWidth = Math.max(82, Math.min(176, area.width() / 3));
      EditorLayout.Box side = new EditorLayout.Box(area.right() - previewWidth, area.y(), previewWidth, area.height() - 26);
      if (this.preview == null) {
         this.preview = new NpcPreview(this.npc, side, this.session.previewWeapon("SLASHBLADE"));
      }

      this.preview.bounds(side);
      this.addRenderableWidget(this.preview);
      this.previewButton = this.guardedButton(
         new EditorLayout.Box(side.x(), area.bottom() - 22, side.width(), 20),
         label("playOnce"),
         EditorButton.Kind.ADD,
         () -> this.blade() && (this.form == null || !this.form.invalid()),
         tr("invalidField"),
         () -> {
            if (this.preview.playing()) {
               this.preview.stop();
            } else {
               play.run();
            }
         }
      );
      this.previewButton.setTooltip(Tooltip.create(label("playOnceHint")));
      this.enabledTooltips.put(this.previewButton, Tooltip.create(label("playOnceHint")));
      return new EditorLayout.Box(area.x(), area.y(), area.width() - previewWidth - 8, area.height());
   }

   private void actions() {
      if (this.blade() && !this.session.actions().isEmpty()) {
         List<EditorList.Row> rows = new ArrayList<>();

         for (JsonElement raw : this.session.actions()) {
            JsonObject info = raw.getAsJsonObject();
            String id = info.get("id").getAsString();
            if (ActionPicker.matches(info, this.actionCategory)) {
               JsonObject config = this.actionEdits.containsKey(id) ? this.actionEdits.get(id).data : this.session.action(id);
               rows.add(
                  new EditorList.Row(
                     id,
                     GuiSupport.actionName(info),
                     GuiSupport.tr("cnpcgun.type." + info.get("type").getAsString().toLowerCase(Locale.ROOT))
                        + " · "
                        + GuiSupport.tr(bool(config, "enabled") ? "cnpcgun.gui.on" : "cnpcgun.gui.off"),
                     bool(info, "available")
                  )
               );
            }
         }

         EditorLayout.Box editPane = this.editingPane(this.body(), () -> {
            JsonObject info = this.session.actionInfo(this.selectedAction);
            if (info != null) {
               this.preview.playAction(info, this.actionEdit(this.selectedAction).parameters());
            }
         });
         boolean split = editPane.width() >= 360;
         if (rows.stream().noneMatch(row -> row.id().equals(this.selectedAction))) {
            this.selectedAction = rows.isEmpty() ? "" : rows.get(0).id();
         }

         if (rows.isEmpty()) {
            this.actionDetail = false;
         }

         if (!split && this.actionDetail) {
            JsonObject info = this.session.actionInfo(this.selectedAction);
            this.pageTitle = GuiSupport.actionName(info);
            this.titleOffset = 76;
            this.button(
               new EditorLayout.Box(this.layout.workspace.x(), this.layout.workspace.y() - 2, 70, 16), label("backToActions"), EditorButton.Kind.BACK, () -> {
                  this.actionDetail = false;
                  this.rebuild();
               }
            );
            this.actionDetails(info, editPane);
         } else {
            int listWidth = Math.max(92, Math.min(160, editPane.width() / 3));
            EditorLayout.Box column = split ? new EditorLayout.Box(editPane.x(), editPane.y(), listWidth, editPane.height()) : editPane;
            this.button(
               new EditorLayout.Box(column.x(), column.y(), column.width(), 20),
               ActionPicker.categoryLabel(this.actionCategory),
               EditorButton.Kind.TOOL,
               () -> EditorDialogs.choose(this, label("actionCategories"), ActionPicker.categories(), id -> {
                  this.actionCategory = id;
                  this.actionDetail = false;
                  this.rebuild();
               })
            );
            EditorButton edit = this.button(
               new EditorLayout.Box(column.x(), column.y() + 24, Math.min(144, column.width()), 28), label("editAction"), EditorButton.Kind.EDIT, () -> {
                  if (this.form != null && this.form.invalid()) {
                     this.localError = tr("invalidField");
                  } else {
                     this.actionDetail = true;
                     this.rebuild();
                  }
               }
            );
            edit.active = !this.busy() && !this.selectedAction.isEmpty();
            EditorLayout.Box listBox = column.below(58);
            this.list(rows, listBox, this.selectedAction, id -> {
               if (this.form != null && this.form.invalid()) {
                  this.localError = tr("invalidField");
               } else {
                  this.selectedAction = id;
                  this.actionDetail = false;
                  JsonObject info = this.session.actionInfo(id);
                  if (info != null && this.preview != null) {
                     this.preview.playAction(info, this.actionEdit(id).parameters());
                  }

                  this.rebuild();
               }
            });
            if (split) {
               this.actionDetails(
                  this.session.actionInfo(this.selectedAction),
                  new EditorLayout.Box(listBox.right() + 8, editPane.y(), editPane.right() - listBox.right() - 8, editPane.height())
               );
            }
         }
      } else {
         this.empty(tr("noBladeActions"), tr("noBladeActionsHint"), true);
      }
   }

   private void list(List<EditorList.Row> rows, EditorLayout.Box area, String initial, Consumer<String> selected) {
      EditorTextField search = (EditorTextField)this.addRenderableWidget(
         new EditorTextField(this.font, area.x() + 4, area.y() + 4, area.width() - 8, 8, label("search"))
      );
      this.searchField = search;
      search.setHint(label("search"));
      String query = this.searches.getOrDefault(this.page, "");
      search.setValue(query);
      this.masterList = (EditorList)this.addRenderableWidget(new EditorList(this.font, area.below(23), rows, initial, selected));
      this.masterList.filter(query);
      this.masterKey = "list:" + this.page.key;
      this.masterList.scrollTo(this.scrollPositions.getOrDefault(this.masterKey, 0));
      EditorList list = this.masterList;
      search.setResponder(text -> {
         this.searches.put(this.page, text);
         list.filter(text);
      });
   }

   private void actionDetails(JsonObject info, EditorLayout.Box area) {
      if (info != null) {
         GuiWeapons.ActionEdit edit = this.actionEdit(this.selectedAction);
         int columns = area.width() < 250 ? 2 : 4;
         int w = (area.width() - (columns - 1) * 4) / columns;
         String[] tabs = new String[]{"actionGeneral", "actionParameters", "actionConditions", "actionStages"};

         for (int i = 0; i < 4; i++) {
            final int tabIndex = i;
            EditorButton button = this.button(
               new EditorLayout.Box(area.x() + i % columns * (w + 4), area.y() + i / columns * 24, w, 20), label(tabs[i]), EditorButton.Kind.NORMAL, () -> {
                  if (this.form != null && this.form.invalid()) {
                     this.localError = tr("invalidField");
                  } else {
                     this.actionTab = tabIndex;
                     this.rebuild();
                  }
               }
            );
            button.selected = this.actionTab == i;
            button.setTooltip(Tooltip.create(label(tabs[i])));
         }

         EditorLayout.Box fields = area.below(4 / columns * 24 + 2);
         if (this.actionTab <= 1) {
            this.newForm(fields, "action:" + this.selectedAction + ":" + this.actionTab);
            JsonObject data = this.actionTab == 0 ? edit.data : edit.parameters();
            Parameter[] schema = this.actionTab == 0 ? ActionConfig.SCHEMA : EditorSession.parameters(info);

            for (Parameter p : schema) {
               this.form.field(p, data.has(p.id) ? data.get(p.id) : SafeJson.copy(p.defaultValue), checked -> data.add(p.id, checked));
            }
         } else if (this.actionTab == 2) {
            this.button(
               new EditorLayout.Box(fields.x(), fields.y(), fields.width(), 20),
               Component.literal(tr("editClauseCount", GuiSupport.tr("cnpcgun.gui.conditions"), edit.array("conditions").size())),
               EditorButton.Kind.NORMAL,
               () -> EditorDialogs.conditions(
                  this, GuiSupport.tr("cnpcgun.gui.conditions"), edit.array("conditions"), values -> edit.data.add("conditions", values)
               )
            );
            List<EditorList.Row> clauses = new ArrayList<>();

            for (int i = 0; i < edit.array("conditions").size(); i++) {
               JsonElement raw = edit.array("conditions").get(i);
               String id = raw.isJsonObject() ? raw.getAsJsonObject().get("id").getAsString() : raw.getAsString();
               clauses.add(new EditorList.Row(String.valueOf(i), GuiSupport.tr("cnpcgun.condition." + id), "", true));
            }

            this.addRenderableWidget(new EditorList(this.font, fields.below(26), clauses, "", ignored -> {}));
         } else {
            int half = (fields.width() - 6) / 2;
            EditorButton add = this.button(new EditorLayout.Box(fields.x(), fields.y(), half, 20), label("addStage"), EditorButton.Kind.NORMAL, () -> {
               List<EditorList.Row> options = new ArrayList<>();
               Set<String> seen = new HashSet<>();

               for (JsonElement raw : this.session.actions()) {
                  JsonObject action = raw.getAsJsonObject();
                  if (action.has("stageId") && seen.add(action.get("stageId").getAsString())) {
                     options.add(new EditorList.Row(action.get("stageId").getAsString(), GuiSupport.actionName(action), "", bool(action, "available")));
                  }
               }

               EditorDialogs.chooseRows(this, Component.translatable("cnpcgun.gui.stages"), options, id -> {
                  if (edit.array("stages").size() < 16 && !edit.array("stages").asList().contains(new JsonPrimitive(id))) {
                     edit.array("stages").add(id);
                  }
               });
            });
            add.active = edit.array("stages").size() < 16;
            EditorButton remove = this.button(
               new EditorLayout.Box(fields.x() + half + 6, fields.y(), fields.width() - half - 6, 20), label("removeStage"), EditorButton.Kind.NORMAL, () -> {
                  if (this.selectedStage >= 0 && this.selectedStage < edit.array("stages").size()) {
                     edit.array("stages").remove(this.selectedStage);
                  }

                  this.selectedStage = 0;
                  this.rebuild();
               }
            );
            remove.active = !edit.array("stages").isEmpty();
            List<EditorList.Row> stages = new ArrayList<>();

            for (int i = 0; i < edit.array("stages").size(); i++) {
               stages.add(new EditorList.Row(String.valueOf(i), this.stageName(edit.array("stages").get(i).getAsString()), tr("stageNumber", i + 1), true));
            }

            this.addRenderableWidget(
               new EditorList(this.font, fields.below(26), stages, String.valueOf(this.selectedStage), id -> this.selectedStage = Integer.parseInt(id))
            );
         }

         if (!bool(info, "available")) {
            this.pageHint = tr("actionUnavailable");
         } else if (info.has("description")) {
            this.pageHint = GuiSupport.tr(info.get("description").getAsString());
         }
      }
   }

   private String actionName(String id) {
      JsonObject info = this.session.actionInfo(id);
      return info == null ? GuiSupport.tr("cnpcgun.gui.unavailable") : GuiSupport.actionName(info);
   }

   private String stageName(String id) {
      for (JsonElement raw : this.session.actions()) {
         if (raw.isJsonObject()) {
            JsonObject info = raw.getAsJsonObject();
            if (SafeJson.string(info, "stageId", "").equals(id)) {
               return GuiSupport.actionName(info);
            }
         }
      }

      return name(id);
   }

   private boolean loadGraph(JsonObject source, String id) {
      try {
         GraphDraft latest = new GraphDraft(source, id);
         if (this.graph != null) {
            latest.positions.putAll(this.graph.positions);
         }

         this.graph = latest;
         this.selectedGraph = SafeJson.string(latest.graph, "id", id);
         this.canvas = null;
         if (this.graph.node(this.selectedNode) == null) {
            this.selectedNode = "";
         }

         return true;
      } catch (IllegalArgumentException var4) {
         this.localError = tr("invalidGraphData");
         return false;
      }
   }

   private List<EditorList.Row> graphRows() {
      List<EditorList.Row> rows = new ArrayList<>();

      for (JsonElement raw : this.session.array("graphs")) {
         if (raw.isJsonObject()) {
            JsonObject item = raw.getAsJsonObject();
            String id = SafeJson.string(item, "id", "");
            if (!id.isEmpty()) {
               rows.add(new EditorList.Row(id, name(id), GuiSupport.tr("cnpcgun.gui.nodeCount", SafeJson.integer(item, "nodeCount", 0)), true));
            }
         }
      }

      return rows;
   }

   private void combos() {
      if (!this.blade()) {
         this.empty(tr("noBladeActions"), tr("noCombosHint"), true);
      } else {
         EditorLayout.Box area = this.body();
         if (this.graph == null) {
            String id = SafeJson.string(this.session.snapshot, "selectedGraph", "");
            JsonElement source = this.session.snapshot.get("graph");
            if (!id.isEmpty() && source != null && source.isJsonObject()) {
               this.loadGraph(source.getAsJsonObject(), id);
            }
         }

         int selectorWidth = Math.min(148, Math.max(70, area.width() - 132));
         if (this.canvasExpanded) {
            selectorWidth = Math.max(60, Math.min(148, area.width() - Math.max(82, Math.min(176, area.width() / 3)) - 76));
         }

         int top = this.canvasExpanded ? area.y() : this.layout.workspace.y() - 2;
         int selectorX = area.x() + (this.canvasExpanded ? 0 : 60);
         this.titleReserved = area.width() - 54;
         String shownName = this.graph == null ? tr("chooseCombo") : name(this.graph.graph.get("id").getAsString());
         this.serverButton(
            new EditorLayout.Box(selectorX, top, selectorWidth, 20),
            Component.literal(shownName),
            () -> !this.graphRows().isEmpty(),
            tr("noSavedCombos"),
            () -> {
               Map<String, String> options = new LinkedHashMap<>();

               for (EditorList.Row row : this.graphRows()) {
                  options.put(row.title() + " · " + row.subtitle(), row.id());
               }

               EditorDialogs.choose(this, label("chooseCombo"), options, this::fetchGraph);
            }
         );
         this.serverButton(
            new EditorLayout.Box(selectorX + selectorWidth + 4, top, Math.min(64, area.right() - selectorX - selectorWidth - 4), 20),
            label("newCombo"),
            () -> !this.running(),
            tr("comboBusy"),
            () -> {
               if (this.graph != null && this.graph.dirty()) {
                  this.saveChanges(this::newGraph);
               } else {
                  this.newGraph();
               }
            }
         );
         EditorLayout.Box editPane = this.editingPane(area, this::playCombo);
         if (this.graph == null) {
            this.text(tr("emptyComboLibrary"), editPane, 18, -14077902);
            this.text(tr("emptyComboLibraryHint"), editPane, 36, -9932693);
         } else {
            boolean wideTools = editPane.width() >= 344;
            int toolbarHeight = wideTools ? 30 : 56;
            int toolbarY = area.bottom() - toolbarHeight;
            int canvasTop = this.canvasExpanded ? area.y() + 26 : this.layout.workspace.y() + 30;
            EditorLayout.Box canvasBox = new EditorLayout.Box(editPane.x(), canvasTop, editPane.width(), Math.max(30, toolbarY - canvasTop - 5));
            this.canvas = (GraphCanvas)this.addRenderableWidget(
               new GraphCanvas(this.font, canvasBox, this.graph, this.selectedNode, this::actionName, id -> this.selectedNode = id, this::editNode)
            );
            if (this.canvasView != null) {
               this.canvas.view(this.canvasView);
            } else if (editPane.width() < 200) {
               this.canvas.view(new GraphCanvas.View(5.0, 5.0, 0.65));
            }

            int editorWidth = wideTools ? Math.min(82, editPane.width() / 5) : (editPane.width() - 4) / 2;
            this.guardedButton(
               new EditorLayout.Box(editPane.x(), toolbarY, editorWidth, 28),
               label("addNode"),
               EditorButton.Kind.ADD,
               () -> this.graph.nodes().size() < 128 && !this.session.actions().isEmpty(),
               tr("nodeLimit"),
               this::addNode
            );
            this.guardedButton(
               new EditorLayout.Box(editPane.x() + editorWidth + 4, toolbarY, editorWidth, 28),
               label("nodeSettings"),
               EditorButton.Kind.EDIT,
               () -> this.graph.node(this.selectedNode) != null,
               tr("selectNode"),
               () -> this.editNode(this.selectedNode)
            );
            int toolX = wideTools ? editPane.x() + editorWidth * 2 + 12 : editPane.x();
            int toolY = wideTools ? toolbarY : toolbarY + 32;
            int toolWidth = (editPane.right() - toolX - 6) / 4;
            String[] labels = new String[]{"toolProperties", "toolArrange", "toolCopy", "toolMore"};
            String[] tools = new String[]{"properties", "arrange", "copy", "more"};

            for (int i = 0; i < 4; i++) {
               String tool = tools[i];
               EditorButton button = this.guardedButton(
                  new EditorLayout.Box(toolX + i * (toolWidth + 2), toolY, toolWidth, wideTools ? 28 : 22),
                  label(labels[i]),
                  EditorButton.Kind.TOOL,
                  () -> !tool.equals("copy") || this.graph.node(this.selectedNode) != null && this.graph.nodes().size() < 128,
                  tr("selectNode"),
                  () -> {
                     if (tool.equals("more")) {
                        this.graphTools();
                     } else {
                        this.graphTool(tool);
                     }
                  }
               );
               button.setTooltip(Tooltip.create(label(labels[i] + "Hint")));
               this.enabledTooltips.put(button, Tooltip.create(label(labels[i] + "Hint")));
            }

            this.pageHint = tr("canvasHint");
         }
      }
   }

   private void playCombo() {
      if (this.graph != null && !this.graph.nodes().isEmpty()) {
         try {
            this.graph.validateEdges();
            this.preview.playGraph(this.graph.graph.deepCopy(), this::previewActionInfo);
         } catch (IllegalStateException | IllegalArgumentException var2) {
            this.localError = tr("invalidEdges");
         }
      }
   }

   private JsonObject previewActionInfo(String id) {
      return this.session.actionInfo(id);
   }

   private void newGraph() {
      String id = "combo_1";
      Set<String> saved = new HashSet<>();

      for (EditorList.Row row : this.graphRows()) {
         saved.add(row.id());
      }

      for (int i = 2; saved.contains(id); i++) {
         id = "combo_" + i;
      }

      this.graph = new GraphDraft(null, id);
      this.selectedGraph = "";
      this.selectedNode = "";
      this.canvasView = null;
      this.canvas = null;
      this.rebuild();
   }

   private void fetchGraph(String id) {
      this.saveChanges(() -> {
         this.session.send(GunNetwork.Operation.FETCH_GRAPH, GuiSupport.with("id", id), response -> {
            JsonElement data = this.session.snapshot.get("graph");
            if (response.code().equals("SUCCESS") && data != null && data.isJsonObject() && this.loadGraph(data.getAsJsonObject(), id)) {
               this.selectedNode = "";
               this.canvasView = null;
            }
         });
         this.rebuild();
      });
   }

   private void addNode() {
      EditorDialogs.actions(this, this.session, label("addNode"), id -> {
         this.selectedNode = this.graph.add(id);
         this.rebuild();
      });
   }

   private void editNode(String id) {
      if (!this.session.pending && this.graph != null && this.graph.node(id) != null) {
         this.openDialog(new NodeDialog(this, this.session, this.graph.node(id), this.graph.ids(), changed -> {
            this.graph.update(id, changed);
            this.selectedNode = changed.get("id").getAsString();
         }));
      }
   }

   private void graphTools() {
      Map<String, String> options = new LinkedHashMap<>();
      options.put(tr("comboProperties"), "properties");
      options.put(tr(this.canvasExpanded ? "collapseCanvas" : "expandCanvas"), "expand");
      options.put(tr("arrangeNodes"), "arrange");
      if (this.graph.node(this.selectedNode) != null) {
         options.put(tr("copyNode"), "copy");
         options.put(tr("deleteNode"), "remove");
         options.put(tr("nodeEarlier"), "earlier");
         options.put(tr("nodeLater"), "later");
      }

      if (!this.graph.nodes().isEmpty()) {
         options.put(
            tr(this.preview != null && this.preview.playing() ? "stopPreview" : "playOnce"), this.preview != null && this.preview.playing() ? "stop" : "start"
         );
      }

      if (!this.selectedGraph.isEmpty()) {
         options.put(tr("deleteCombo"), "delete");
      }

      EditorDialogs.choose(this, label("comboTools"), options, this::graphTool);
   }

   private void graphTool(String chosen) {
      if (chosen.equals("properties")) {
         List<Parameter> schema = new ArrayList<>();
         schema.add(new Parameter("id", "string", this.graph.graph.get("id").getAsString(), null, null, null, ""));
         if (!this.graph.ids().isEmpty()) {
            schema.add(Parameter.choice("entryNode", this.graph.ids().get(0), this.graph.ids().toArray(String[]::new)));
         }

         EditorDialogs.form(this, label("comboProperties"), schema.toArray(Parameter[]::new), this.graph.graph, changed -> {
            if (!changed.get("id").getAsString().matches("[A-Za-z0-9_.:-]{1,64}")) {
               throw new IllegalArgumentException();
            } else {
               this.graph.graph.add("id", changed.get("id"));
               if (changed.has("entryNode")) {
                  this.graph.graph.add("entryNode", changed.get("entryNode"));
               }
            }
         });
      } else if (chosen.equals("expand")) {
         this.canvasExpanded = !this.canvasExpanded;
         this.canvas = null;
         this.rebuild();
      } else if (chosen.equals("arrange")) {
         this.canvas.arrange();
         this.canvasView = this.canvas.view();
      } else if (chosen.equals("copy")) {
         this.selectedNode = this.graph.copy(this.selectedNode);
         this.rebuild();
      } else if (chosen.equals("remove")) {
         this.confirmDelete(tr("deleteNodeMessage"), () -> {
            this.graph.remove(this.selectedNode);
            this.selectedNode = "";
            this.rebuild();
         });
      } else if (chosen.equals("earlier") || chosen.equals("later")) {
         this.graph.reorder(this.selectedNode, chosen.equals("earlier") ? -1 : 1);
         this.rebuild();
      } else if (chosen.equals("start")) {
         this.playCombo();
      } else if (chosen.equals("stop") && this.preview != null) {
         this.preview.stop();
      } else if (chosen.equals("delete") && !this.running()) {
         this.confirmDelete(tr("deleteComboMessage"), () -> {
            this.graph = null;
            this.mutate(GunNetwork.Operation.DELETE_GRAPH, GuiSupport.with("id", this.selectedGraph));
            this.selectedGraph = "";
            this.canvas = null;
            this.canvasView = null;
         });
      }
   }

   private void presets() {
      List<EditorList.Row> rows = new ArrayList<>();

      for (JsonElement raw : this.session.array("presets")) {
         if (raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isString()) {
            String id = raw.getAsString();
            boolean builtin = Set.of("BEGINNER", "NORMAL", "CUSTOM", "MAID").contains(id);
            rows.add(
               new EditorList.Row(
                  id,
                  name(id),
                  builtin ? tr("preset." + id.toLowerCase(Locale.ROOT)) : tr(id.startsWith("user:") ? "sharedPreset" : "localPreset"),
                  this.blade()
               )
            );
         }
      }

      if (this.selectedPreset.isEmpty()) {
         this.selectedPreset = this.value("slashblade.preset").getAsString();
      }

      EditorLayout.Box area = this.body().below(26);
      int topWidth = (this.body().width() - 6) / 2;
      this.serverButton(
         new EditorLayout.Box(this.body().x(), this.body().y(), topWidth, 20),
         label("saveCurrentPreset"),
         () -> true,
         "",
         () -> this.saveChanges(this::savePreset)
      );
      this.guardedButton(
         new EditorLayout.Box(this.body().x() + topWidth + 6, this.body().y(), this.body().width() - topWidth - 6, 20),
         label("applyBattlePreset"),
         EditorButton.Kind.PRIMARY,
         () -> this.blade() && !this.running() && !this.selectedPreset.isEmpty(),
         tr("presetUnavailable"),
         () -> this.mutate(GunNetwork.Operation.PRESET, GuiSupport.with("id", this.selectedPreset))
      );
      boolean split = this.body().width() >= 400;
      EditorLayout.Box pane = split
         ? new EditorLayout.Box(area.x(), area.y(), this.layout.listPane().width(), area.height())
         : new EditorLayout.Box(area.x(), area.y(), area.width(), area.height() - 26);
      this.list(rows, pane, this.selectedPreset, id -> {
         this.selectedPreset = id;
         this.rebuild();
      });
      if (!split) {
         this.button(
            new EditorLayout.Box(area.x(), area.bottom() - 20, area.width(), 20),
            label("presetTools"),
            EditorButton.Kind.TOOL,
            () -> this.presetDialog(this.selectedPreset)
         );
      }

      if (split && !this.selectedPreset.isEmpty()) {
         EditorLayout.Box detail = new EditorLayout.Box(pane.right() + 8, area.y(), area.right() - pane.right() - 8, area.height());
         this.text(name(this.selectedPreset), detail, 10, -14077902);
         this.text(this.presetDescription(this.selectedPreset), detail, 29, -9932693);
         this.button(
            new EditorLayout.Box(detail.x() + 8, detail.y() + 53, detail.width() - 16, 20),
            label("copyPreset"),
            EditorButton.Kind.NORMAL,
            () -> this.copyPreset(this.selectedPreset)
         );
         if (!builtinPreset(this.selectedPreset)) {
            this.guardedButton(
               new EditorLayout.Box(detail.x() + 8, detail.y() + 79, detail.width() - 16, 20),
               label("deletePreset"),
               EditorButton.Kind.DANGER,
               () -> !this.running(),
               tr("comboBusy"),
               () -> this.deletePreset(this.selectedPreset)
            );
         }
      }
   }

   private String presetDescription(String id) {
      return Set.of("BEGINNER", "NORMAL", "CUSTOM", "MAID").contains(id)
         ? tr("preset." + id.toLowerCase(Locale.ROOT))
         : tr(id.startsWith("user:") ? "sharedPreset" : "localPreset");
   }

   private static boolean builtinPreset(String id) {
      return Set.of("BEGINNER", "NORMAL", "CUSTOM", "MAID").contains(id);
   }

   private void deletePreset(String id) {
      this.confirmDelete(tr("deletePresetMessage"), () -> {
         this.selectedPreset = "";
         this.mutate(GunNetwork.Operation.DELETE_PRESET, GuiSupport.with("id", id));
      });
   }

   private void presetDialog(String id) {
      List<EditorDialogs.Decision> choices = new ArrayList<>();
      if (this.blade() && !this.running()) {
         choices.add(
            new EditorDialogs.Decision(
               label("applyPreset"), EditorButton.Kind.PRIMARY, () -> this.mutate(GunNetwork.Operation.PRESET, GuiSupport.with("id", id))
            )
         );
      }

      choices.add(new EditorDialogs.Decision(label("copyPreset"), EditorButton.Kind.NORMAL, () -> this.copyPreset(id)));
      if (!builtinPreset(id)) {
         choices.add(new EditorDialogs.Decision(label("deletePreset"), EditorButton.Kind.DANGER, () -> this.deletePreset(id)));
      }

      choices.add(new EditorDialogs.Decision(Component.translatable("gui.cancel"), EditorButton.Kind.NORMAL, () -> {}));
      EditorDialogs.confirm(this, Component.literal(name(id)), this.presetDescription(id), choices);
   }

   private void promptName(String title, String initial, Consumer<String> applied) {
      JsonObject data = new JsonObject();
      data.addProperty("id", initial);
      EditorDialogs.form(this, label(title), new Parameter[]{new Parameter("id", "string", initial, null, null, null, "")}, data, values -> {
         String id = values.get("id").getAsString().trim();
         if (!id.matches("[A-Za-z0-9_.-]{1,48}")) {
            throw new IllegalArgumentException();
         } else {
            applied.accept(id);
         }
      });
   }

   private void copyPreset(String source) {
      this.promptName("copyPreset", "my_preset", id -> {
         JsonObject input = GuiSupport.with("id", "custom:" + id);
         input.addProperty("source", source);
         this.mutate(GunNetwork.Operation.COPY, input);
      });
   }

   private void savePreset() {
      this.promptName("saveCurrentPreset", "my_preset", id -> this.mutate(GunNetwork.Operation.SAVE_PRESET, GuiSupport.with("id", "user:" + id)));
   }

   private void advanced() {
      this.newForm(this.body(), "advanced");
      this.fields(tr("combatPolicy"), p -> p.id.startsWith("combat."));
   }

   private void bladeSettings() {
      if (!this.blade()) {
         this.empty(tr("noBladeActions"), tr("noBladeActionsHint"), true);
      } else {
         this.newForm(this.body(), "bladeSettings");
         this.fields(tr("bladePolicy"), p -> p.id.startsWith("slashblade.") && !p.id.equals("slashblade.preset"));
      }
   }

   private void refresh() {
      if (!this.busy()) {
         if (!this.dirty() && (this.form == null || !this.form.invalid())) {
            this.session.send(this.session.token.equals(GunNetwork.NO_TOKEN) ? GunNetwork.Operation.OPEN : GunNetwork.Operation.REFRESH, new JsonObject());
            this.rebuild();
         } else {
            this.localError = tr("saveBeforeRefresh");
         }
      }
   }

   private void mutate(GunNetwork.Operation operation, JsonObject input) {
      this.saveChanges(() -> {
         this.session.send(operation, input);
         this.rebuild();
      });
   }

   private void saveChanges(Runnable after) {
      if (!this.busy() && this.session.ready()) {
         if (this.form != null && this.form.invalid()) {
            this.localError = tr("invalidField");
         } else {
            List<GuiWeapons.SaveStep> queue = new ArrayList<>();

            for (JsonObject values : this.draft.batches(32)) {
               JsonObject input = new JsonObject();
               input.add("values", values);
               queue.add(new GuiWeapons.SaveStep(GunNetwork.Operation.BATCH, input, () -> this.draft.acknowledge(values)));
            }

            for (Entry<String, GuiWeapons.ActionEdit> entry : this.actionEdits.entrySet()) {
               if (entry.getValue().dirty()) {
                  GuiWeapons.ActionEdit action = entry.getValue();
                  JsonObject submitted = action.data.deepCopy();
                  JsonObject input = GuiSupport.with("id", entry.getKey());
                  input.add("config", submitted);
                  queue.add(new GuiWeapons.SaveStep(GunNetwork.Operation.ACTION_CONFIG, input, () -> action.original = submitted.deepCopy()));
               }
            }

            if (this.graph != null && this.graph.dirty()) {
               if (this.graph.nodes().isEmpty()) {
                  this.localError = tr("emptyGraphSave");
                  return;
               }

               try {
                  this.graph.validateEdges();
               } catch (IllegalArgumentException var8) {
                  this.localError = tr("invalidEdges");
                  return;
               }

               GraphDraft savedGraph = this.graph;
               JsonObject submitted = this.graph.graph.deepCopy();
               queue.add(new GuiWeapons.SaveStep(GunNetwork.Operation.GRAPH, submitted, () -> {
                  savedGraph.acknowledge(submitted);
                  this.selectedGraph = submitted.get("id").getAsString();
               }));
            }

            this.localError = "";
            if (queue.isEmpty()) {
               if (after != null) {
                  after.run();
               }
            } else {
               this.saving = true;
               this.saveNext(queue, 0, after);
            }
         }
      }
   }

   private void saveNext(List<GuiWeapons.SaveStep> queue, int index, Runnable after) {
      if (index >= queue.size()) {
         this.saving = false;
         this.saveContinuation = null;
         this.draft.reconcile(this.session.values());
         this.actionEdits.replaceAll((id, edited) -> (GuiWeapons.ActionEdit)(edited.dirty() ? edited : new GuiWeapons.ActionEdit(this.session.action(id))));
         JsonElement current = this.session.snapshot.get("graph");
         if (this.graph != null && !this.graph.dirty() && current != null && current.isJsonObject()) {
            this.loadGraph(current.getAsJsonObject(), "");
         }

         this.toast.show(tr("savedToast"));
         this.rebuild();
         if (after != null) {
            after.run();
         }
      } else {
         GuiWeapons.SaveStep step = queue.get(index);
         boolean sent = this.session.send(step.operation, step.payload, result -> {
            if (result.code().equals("SUCCESS")) {
               step.acknowledged.run();
               this.saveDelay = 3;
               this.saveContinuation = () -> this.saveNext(queue, index + 1, after);
            } else {
               this.saving = false;
               this.saveContinuation = null;
            }
         });
         if (!sent) {
            this.saving = false;
            this.localError = GuiSupport.tr(this.session.status);
         }

         this.rebuild();
      }
   }

   private void discardChanges() {
      if (this.dirty() || this.form != null && this.form.invalid()) {
         EditorDialogs.confirm(
            this,
            label("discardTitle"),
            tr("discardMessage"),
            this.unsavedDetails(),
            List.of(new EditorDialogs.Decision(label("discard"), EditorButton.Kind.DANGER, () -> {
               this.draft.clear();
               this.actionEdits.clear();
               this.invalidText.clear();
               this.form = null;
               this.graph = null;
               this.selectedNode = "";
               this.canvas = null;
               this.canvasView = null;
               this.localError = "";
               this.rebuild();
            }), new EditorDialogs.Decision(Component.translatable("gui.cancel"), EditorButton.Kind.NORMAL, () -> {}))
         );
      }
   }

   private void operationToast(GunNetwork.Operation operation) {
      if (operation != null) {
         String key = switch (operation) {
            case PRESET -> "presetAppliedToast";
            case COPY -> "presetCopiedToast";
            case SAVE_PRESET -> "presetSavedToast";
            case DELETE_PRESET -> "presetDeletedToast";
            case EXCHANGE, EQUIP_CREATIVE, EQUIP_ITEM_PRESET -> "equippedToast";
            case UNEQUIP -> "unequippedToast";
            case PRIMARY_WEAPON -> "primaryWeaponToast";
            case SAVE_ITEM_PRESET -> "itemPresetSavedToast";
            case DELETE_ITEM_PRESET -> "itemPresetDeletedToast";
            default -> "";
         };
         if (!key.isEmpty()) {
            this.toast.show(tr(key));
         }
      }
   }

   private static String difference(JsonElement before, JsonElement after) {
      return after != null && after.isJsonPrimitive() ? GuiSupport.value(before) + " → " + GuiSupport.value(after) : tr("changedEntry");
   }

   private List<String> unsavedDetails() {
      List<String> details = new ArrayList<>();

      for (Entry<String, JsonElement> entry : this.draft.snapshot().entrySet()) {
         Parameter p = ConfigSchema.get(entry.getKey());
         details.add((p == null ? entry.getKey() : GuiSupport.tr(p.label)) + " · " + difference(this.session.values().get(entry.getKey()), entry.getValue()));
      }

      for (Entry<String, GuiWeapons.ActionEdit> entry : this.actionEdits.entrySet()) {
         if (entry.getValue().dirty()) {
            GuiWeapons.ActionEdit edit = entry.getValue();

            for (Entry<String, JsonElement> field : edit.data.entrySet()) {
               if (!field.getValue().equals(edit.original.get(field.getKey()))) {
                  if (field.getKey().equals("parameters")) {
                     JsonObject before = edit.original.getAsJsonObject("parameters");

                     for (Entry<String, JsonElement> p : edit.parameters().entrySet()) {
                        if (!p.getValue().equals(before.get(p.getKey()))) {
                           details.add(
                              this.actionName(entry.getKey())
                                 + " / "
                                 + GuiSupport.tr("cnpcgun.config." + p.getKey())
                                 + " · "
                                 + difference(before.get(p.getKey()), p.getValue())
                           );
                        }
                     }
                  } else {
                     details.add(
                        this.actionName(entry.getKey())
                           + " / "
                           + GuiSupport.tr("cnpcgun.config." + field.getKey())
                           + " · "
                           + difference(edit.original.get(field.getKey()), field.getValue())
                     );
                  }
               }
            }
         }
      }

      if (this.graph != null && this.graph.dirty()) {
         JsonObject before = this.graph.original();
         Map<String, JsonObject> oldNodes = new LinkedHashMap<>();

         for (JsonElement raw : before.getAsJsonArray("nodes")) {
            JsonObject node = raw.getAsJsonObject();
            oldNodes.put(node.get("id").getAsString(), node);
         }

         String title = tr("combos") + " / " + this.graph.graph.get("id").getAsString();

         for (String key : List.of("id", "entryNode")) {
            if (!this.graph.graph.get(key).equals(before.get(key))) {
               details.add(title + " / " + GuiSupport.tr("cnpcgun.config." + key) + " · " + difference(before.get(key), this.graph.graph.get(key)));
            }
         }

         for (JsonElement raw : this.graph.nodes()) {
            JsonObject node = raw.getAsJsonObject();
            String id = node.get("id").getAsString();
            JsonObject old = oldNodes.remove(id);
            if (old == null) {
               details.add(title + " · " + tr("addedNodeDetail", id, this.actionName(node.get("action").getAsString())));
            } else {
               for (Entry<String, JsonElement> fieldx : node.entrySet()) {
                  if (!fieldx.getValue().equals(old.get(fieldx.getKey()))) {
                     details.add(
                        title
                           + " / "
                           + id
                           + " / "
                           + GuiSupport.tr("cnpcgun.config." + fieldx.getKey())
                           + " · "
                           + difference(old.get(fieldx.getKey()), fieldx.getValue())
                     );
                  }
               }
            }
         }

         for (String id : oldNodes.keySet()) {
            details.add(title + " · " + tr("deletedNodeDetail", id));
         }
      }

      if (this.form != null && this.form.invalid()) {
         details.add(tr("invalidField"));
      }

      return details;
   }

   private void confirmDelete(String message, Runnable action) {
      EditorDialogs.confirm(
         this,
         label("deleteTitle"),
         message,
         List.of(
            new EditorDialogs.Decision(Component.translatable("cnpcgun.gui.remove"), EditorButton.Kind.DANGER, action),
            new EditorDialogs.Decision(Component.translatable("gui.cancel"), EditorButton.Kind.NORMAL, () -> {})
         )
      );
   }

   private void updateControls() {
      for (GuiWeapons.Guard guard : this.guards) {
         guard.widget.active = !this.busy() && this.dialogs.isEmpty() && this.session.ready() && guard.allowed.getAsBoolean();
         guard.widget
            .setTooltip(
               !guard.widget.active && !guard.reason.isEmpty()
                  ? Tooltip.create(Component.literal(guard.reason))
                  : this.enabledTooltips.get(guard.widget)
            );
      }

      if (this.form != null) {
         this.form.locked(this.busy() || !this.dialogs.isEmpty());
      }

      for (WeaponSlotButton slot : this.weaponButtons) {
         slot.active = !this.busy() && this.dialogs.isEmpty();
      }

      if (this.save != null) {
         this.save.active = !this.busy() && this.dialogs.isEmpty() && this.session.ready() && this.dirty() && (this.form == null || !this.form.invalid());
      }

      if (this.discard != null) {
         this.discard.active = !this.busy() && this.dialogs.isEmpty() && (this.dirty() || this.form != null && this.form.invalid());
      }

      if (this.canvas != null) {
         this.canvas.active = !this.busy() && this.dialogs.isEmpty();
      }

      if (this.previewButton != null) {
         this.previewButton.setMessage(label(this.preview != null && this.preview.playing() ? "stopPreview" : "playOnce"));
      }
   }

   public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
      this.rebuildNow();
      this.queuedTooltip = null;
      this.updateControls();
      g.fill(0, 0, this.width, this.height, -3750202);
      g.fill(0, 0, this.width, 34, -12828349);
      g.hLine(0, this.width, 33, -13158601);
      if (!this.canvasExpanded) {
         EditorStyle.panel(g, this.layout.sidebar, -12828349);
      }

      if (!this.canvasExpanded) {
         EditorStyle.text(
            g,
            this.font,
            this.pageTitle,
            this.layout.workspace.x() + 2 + this.titleOffset,
            this.layout.workspace.y() + 1,
            this.layout.workspace.width() - 4 - this.titleOffset - this.titleReserved,
            -14077902
         );
         EditorStyle.text(
            g,
            this.font,
            this.pageHint,
            this.layout.workspace.x() + 2,
            this.layout.workspace.y() + (this.page == GuiWeapons.Page.COMBOS ? 18 : 14),
            this.layout.workspace.width() - 4,
            -9932693
         );
      }

      EditorStyle.text(g, this.font, this.title.getString(), 104, 12, 82, -395024);
      g.fill(0, this.height - 26, this.width, this.height, -12828349);
      g.hLine(0, this.width, this.height - 26, -13158601);
      super.render(g, this.dialogs.isEmpty() ? mouseX : -10000, this.dialogs.isEmpty() ? mouseY : -10000, partialTick);

      for (GuiWeapons.Text line : this.texts) {
         EditorStyle.text(g, this.font, line.text, line.x, line.y, line.width, line.color);
      }

      String status = !this.localError.isEmpty()
         ? this.localError
         : (
            this.session.status.startsWith("cnpcgun.error")
               ? GuiSupport.tr(this.session.status)
               : (this.busy() ? GuiSupport.tr("cnpcgun.gui.saving") : (this.dirty() ? tr("unsaved") : GuiSupport.tr(this.session.status)))
         );
      int color = this.localError.isEmpty() && !this.session.status.startsWith("cnpcgun.error")
         ? (!this.session.pending && !this.dirty() ? -2826297 : -1978731)
         : -936281;
      EditorStyle.text(g, this.font, status, 10, this.height - 16, this.layout.statusWidth(), color);
      if (this.dialogs.isEmpty()) {
         for (WeaponSlotButton slot : this.weaponButtons) {
            if (slot.isMouseOver(mouseX, mouseY)) {
               g.renderTooltip(
                  this.font,
                  Component.literal((slot.item().isEmpty() ? tr("emptySlot") : slot.item().getHoverName().getString()) + "\n" + tr("slotSelectHint")),
                  mouseX,
                  mouseY
               );
            }
         }
      } else {
         List<Screen> layers = new ArrayList<>(this.dialogs);
         Collections.reverse(layers);

         for (int i = 0; i < layers.size(); i++) {
            g.flush();
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 400 * (i + 1));
            layers.get(i).render(g, i == layers.size() - 1 ? mouseX : -10000, i == layers.size() - 1 ? mouseY : -10000, partialTick);
            g.flush();
            g.pose().popPose();
         }
      }

      g.flush();
      g.pose().pushPose();
      g.pose().translate(0.0F, 0.0F, 400 * this.dialogs.size() + 800);
      this.toast.render(g, this.width);
      if (this.queuedTooltip != null) {
         g.renderTooltip(this.font, this.queuedTooltip, this.tooltipPositioner, mouseX, mouseY);
      }

      g.flush();
      g.pose().popPose();
   }

   public void tick() {
      this.rebuildNow();
      this.session
         .tick(
            !this.busy()
               && !this.dirty()
               && this.dialogs.isEmpty()
               && (this.searchField == null || !this.searchField.isFocused())
               && (this.form == null || !this.form.editing() && !this.form.invalid())
         );
      if (this.saveContinuation != null && --this.saveDelay <= 0) {
         Runnable next = this.saveContinuation;
         this.saveContinuation = null;
         next.run();
      } else if (this.saving && !this.session.pending && this.saveContinuation == null) {
         this.saving = false;
         this.localError = GuiSupport.tr(this.session.status);
      }

      if (this.form != null) {
         this.form.tick();
      }

      if (this.searchField != null) {
         this.searchField.tick();
      }

      if (this.preview != null) {
         this.preview.tick();
      }

      if (!this.dialogs.isEmpty()) {
         this.dialogs.peek().tick();
      }

      this.updateControls();
   }

   @Override
   public void openDialog(Screen dialog) {
      this.setDragging(false);
      this.setFocused(null);
      if (!this.dialogs.isEmpty()) {
         this.dialogs.peek().setFocused(null);
      }

      this.dialogs.push(dialog);
      dialog.init(this.minecraft, this.width, this.height);
   }

   @Override
   public void closeDialog() {
      if (!this.dialogs.isEmpty()) {
         this.dialogs.pop().removed();
         if (this.dialogs.isEmpty()) {
            this.rebuild();
         } else if (this.dialogs.peek() instanceof EditorDialogs.Base dialog) {
            dialog.rebuild();
         }
      }
   }

   public boolean mouseClicked(double x, double y, int button) {
      if (!this.dialogs.isEmpty()) {
         this.dialogs.peek().mouseClicked(x, y, button);
         return true;
      } else {
         return this.busy() || super.mouseClicked(x, y, button);
      }
   }

   public boolean mouseReleased(double x, double y, int button) {
      if (!this.dialogs.isEmpty()) {
         this.dialogs.peek().mouseReleased(x, y, button);
         return true;
      } else {
         return super.mouseReleased(x, y, button);
      }
   }

   public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
      if (!this.dialogs.isEmpty()) {
         this.dialogs.peek().mouseDragged(x, y, button, dx, dy);
         return true;
      } else if (this.busy()) {
         return true;
      } else {
         return this.canvas != null && this.getFocused() == this.canvas ? this.canvas.mouseDragged(x, y, button, dx, dy) : super.mouseDragged(x, y, button, dx, dy);
      }
   }

   public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
      if (!this.dialogs.isEmpty()) {
         return this.dialogs.peek().mouseScrolled(x, y, scrollX, scrollY) || true;
      } else {
         return this.busy() || super.mouseScrolled(x, y, scrollX, scrollY);
      }
   }

   public boolean charTyped(char letter, int modifiers) {
      if (!this.dialogs.isEmpty()) {
         this.dialogs.peek().charTyped(letter, modifiers);
         return true;
      } else {
         return this.busy() || super.charTyped(letter, modifiers);
      }
   }

   public boolean keyPressed(int key, int scan, int modifiers) {
      if (!this.dialogs.isEmpty()) {
         this.dialogs.peek().keyPressed(key, scan, modifiers);
         return true;
      } else if (key == 83 && (modifiers & 2) != 0) {
         this.saveChanges(null);
         return true;
      } else {
         return super.keyPressed(key, scan, modifiers);
      }
   }

   public boolean keyReleased(int key, int scan, int modifiers) {
      if (!this.dialogs.isEmpty()) {
         this.dialogs.peek().keyReleased(key, scan, modifiers);
         return true;
      } else {
         return this.busy() || super.keyReleased(key, scan, modifiers);
      }
   }

   public void setTooltipForNextRenderPass(List<FormattedCharSequence> lines, ClientTooltipPositioner positioner, boolean force) {
      if (this.queuedTooltip == null || force) {
         this.queuedTooltip = List.copyOf(lines);
         this.tooltipPositioner = positioner;
      }
   }

   public void setTooltipForNextRenderPass(List<FormattedCharSequence> lines) {
      this.setTooltipForNextRenderPass(lines, DefaultTooltipPositioner.INSTANCE, false);
   }

   public void setTooltipForNextRenderPass(Tooltip tooltip, ClientTooltipPositioner positioner, boolean force) {
      this.setTooltipForNextRenderPass(tooltip.toCharSequence(this.minecraft), positioner, force);
   }

   public void setTooltipForNextRenderPass(Component text) {
      this.setTooltipForNextRenderPass(Tooltip.splitTooltip(this.minecraft, text));
   }

   private void returnAppearance() {
      if (!this.closed) {
         this.minecraft.setScreen(new GuiNpcDisplay(this.npc));
      }
   }

   public void onClose() {
      if (this.busy()) {
         this.localError = tr("waitForSave");
      } else if (this.dirty() || this.form != null && this.form.invalid()) {
         EditorDialogs.confirm(
            this,
            label("leaveTitle"),
            tr("leaveMessage"),
            this.unsavedDetails(),
            List.of(
               new EditorDialogs.Decision(label("saveAndReturn"), EditorButton.Kind.PRIMARY, () -> this.saveChanges(this::returnAppearance)),
               new EditorDialogs.Decision(label("discardAndReturn"), EditorButton.Kind.DANGER, this::returnAppearance),
               new EditorDialogs.Decision(label("continueEditing"), EditorButton.Kind.NORMAL, () -> {})
            )
         );
      } else {
         this.returnAppearance();
      }
   }

   public void removed() {
      if (!this.closed) {
         this.closed = true;
         if (this.preview != null) {
            this.preview.dispose();
         }

         for (Screen dialog : this.dialogs) {
            dialog.removed();
         }

         this.dialogs.clear();
         this.session.close();
      }

      super.removed();
   }

   public boolean isPauseScreen() {
      return false;
   }

   private static final class ActionEdit {
      JsonObject original;
      final JsonObject data;

      ActionEdit(JsonObject source) {
         this.data = source == null ? new JsonObject() : source.deepCopy();

         for (Parameter p : ActionConfig.SCHEMA) {
            try {
               this.data.add(p.id, p.validate(this.data.has(p.id) ? this.data.get(p.id) : SafeJson.copy(p.defaultValue)));
            } catch (IllegalStateException | IllegalArgumentException var7) {
               this.data.add(p.id, SafeJson.copy(p.defaultValue));
            }
         }

         if (!this.data.has("parameters") || !this.data.get("parameters").isJsonObject()) {
            this.data.add("parameters", new JsonObject());
         }

         if (!this.data.has("conditions") || !this.data.get("conditions").isJsonArray()) {
            this.data.add("conditions", new JsonArray());
         }

         if (!this.data.has("stages") || !this.data.get("stages").isJsonArray()) {
            this.data.add("stages", new JsonArray());
         }

         this.original = this.data.deepCopy();
      }

      boolean dirty() {
         return !this.data.equals(this.original);
      }

      JsonObject parameters() {
         return this.data.getAsJsonObject("parameters");
      }

      JsonArray array(String key) {
         return this.data.getAsJsonArray(key);
      }
   }

   private static enum Group {
      INVENTORY("inventoryGroup", GuiWeapons.Page.WEAPONS, GuiWeapons.Page.ADVANCED),
      GUN("gunGroup", GuiWeapons.Page.SHOOTING, GuiWeapons.Page.THROWING),
      BLADE("bladeGroup", GuiWeapons.Page.ACTIONS, GuiWeapons.Page.COMBOS, GuiWeapons.Page.PRESETS, GuiWeapons.Page.BLADE_SETTINGS);

      final String key;
      final GuiWeapons.Page[] pages;

      private Group(String key, GuiWeapons.Page... pages) {
         this.key = key;
         this.pages = pages;
      }
   }

   private record Guard(AbstractWidget widget, BooleanSupplier allowed, String reason) {
   }

   private static enum Page {
      WEAPONS("weapons"),
      SHOOTING("shooting"),
      THROWING("throwing"),
      ACTIONS("actions"),
      COMBOS("combos"),
      PRESETS("presets"),
      ADVANCED("settings"),
      BLADE_SETTINGS("bladeSettings");

      final String key;

      private Page(String key) {
         this.key = key;
      }
   }

   private record SaveStep(GunNetwork.Operation operation, JsonObject payload, Runnable acknowledged) {
   }

   private record Text(String text, int x, int y, int width, int color) {
   }
}
