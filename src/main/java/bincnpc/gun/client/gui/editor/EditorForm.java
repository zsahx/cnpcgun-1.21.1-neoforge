package bincnpc.gun.client.gui.editor;

import bincnpc.gun.client.gui.GuiSupport;
import bincnpc.gun.common.api.SafeJson;
import bincnpc.gun.common.config.ConfigSchema;
import bincnpc.gun.common.config.Parameter;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

public final class EditorForm extends AbstractWidget {
   private final Font font;
   private final EditorForm.Picker picker;
   private final List<EditorForm.Field> fields = new ArrayList<>();
   private AbstractWidget focusedControl;
   private int scroll;
   private int totalHeight;
   private boolean locked;
   private boolean compactSlots;
   private static final int ROW = 30;

   public EditorForm(Font font, EditorLayout.Box box, EditorForm.Picker picker) {
      super(box.x(), box.y(), box.width(), box.height(), Component.translatable("cnpcgun.gui.parameters"));
      this.font = font;
      this.picker = picker;
   }

   public EditorForm heading(String title) {
      this.fields.add(new EditorForm.Field(title));
      this.layout();
      return this;
   }

   public EditorForm compactSlots() {
      this.compactSlots = true;
      this.layout();
      return this;
   }

   public EditorForm field(Parameter parameter, JsonElement initial, BooleanSupplier available, String reason, Consumer<JsonElement> changed) {
      if (initial == null || initial.isJsonNull()) {
         initial = SafeJson.copy(parameter.defaultValue);
      }

      this.fields.add(new EditorForm.Field(parameter, initial, available, reason, changed));
      this.layout();
      return this;
   }

   public EditorForm field(Parameter parameter, JsonElement initial, Consumer<JsonElement> changed) {
      return this.field(parameter, initial, () -> true, "", changed);
   }

   public EditorForm segments(Parameter parameter, JsonElement initial, BooleanSupplier available, String reason, Consumer<JsonElement> changed) {
      EditorForm.Field field = new EditorForm.Field(parameter, initial, available, reason, changed);
      field.segmented = true;
      field.controls.clear();

      for (String id : parameter.values) {
         EditorButton button = new EditorButton(0, 0, 40, 20, Component.empty(), EditorButton.Kind.SEGMENT, () -> {
            if (!field.value.getAsString().equals(id)) {
               field.value = parameter.validate(new JsonPrimitive(id));
               changed.accept(field.value.deepCopy());
               field.updateLabel();
            }
         });
         button.setTooltip(
            Tooltip.create(
               Component.literal(
                  GuiSupport.tr((parameter.id.startsWith("throw.") ? "cnpcgun.editor.throw.option." : "cnpcgun.editor.option.") + id.toLowerCase(Locale.ROOT))
               )
            )
         );
         field.controls.add(button);
      }

      field.updateLabel();
      this.fields.add(field);
      this.layout();
      return this;
   }

   public EditorForm toggleNumber(
      Parameter flag,
      JsonElement enabled,
      Parameter number,
      JsonElement initial,
      BooleanSupplier available,
      String reason,
      Consumer<JsonElement> flagChanged,
      Consumer<JsonElement> numberChanged
   ) {
      EditorForm.Field field = new EditorForm.Field(number, initial, available, reason, numberChanged);
      field.combined = true;
      field.flagValue = enabled.getAsBoolean();
      EditorButton toggle = new EditorButton(0, 0, 28, 20, Component.empty(), EditorButton.Kind.NORMAL, () -> {
         field.flagValue = !field.flagValue;
         flagChanged.accept(new JsonPrimitive(field.flagValue));
         field.updateLabel();
      });
      toggle.setTooltip(Tooltip.create(Component.translatable(flag.label)));
      field.controls.add(0, toggle);
      field.updateLabel();
      this.fields.add(field);
      this.layout();
      return this;
   }

   private boolean numberEditable(EditorForm.Field field) {
      return !field.combined || field.flagValue || ConfigSchema.isGunSetting(field.parameter.id) || field.parameter.id.startsWith("weapon.");
   }

   public boolean invalid() {
      return this.fields.stream().anyMatch(field -> field.invalid && this.numberEditable(field));
   }

   public boolean editing() {
      return this.isFocused() && this.focusedControl instanceof EditorTextField;
   }

   public int scrollOffset() {
      return this.scroll;
   }

   public Map<String, List<String>> invalidText() {
      Map<String, List<String>> raw = new HashMap<>();

      for (EditorForm.Field field : this.fields) {
         if (field.parameter != null && field.invalid && this.numberEditable(field)) {
            raw.put(
               field.parameter.id,
               field.controls.stream().filter(control -> control instanceof EditorTextField).map(control -> ((EditorTextField)control).getValue()).toList()
            );
         }
      }

      return raw;
   }

   public void restoreInvalidText(Map<String, List<String>> raw) {
      for (EditorForm.Field field : this.fields) {
         if (field.parameter != null && raw.containsKey(field.parameter.id)) {
            List<String> text = raw.get(field.parameter.id);
            int i = 0;

            for (AbstractWidget control : field.controls) {
               if (control instanceof EditorTextField input && i < text.size()) {
                  input.setValue(text.get(i++));
               }
            }
         }
      }
   }

   public void scrollTo(int position) {
      this.scroll = EditorLayout.clampScroll(position, this.totalHeight, this.height - 8);
      this.layout();
   }

   public void locked(boolean locked) {
      this.locked = locked;
   }

   public void tick() {
      for (EditorForm.Field field : this.fields) {
         for (AbstractWidget control : field.controls) {
            if (control instanceof EditorTextField input) {
               input.tick();
            }
         }
      }
   }

   private static String valueLabel(Parameter p, JsonElement value) {
      String key = "cnpcgun.editor.throw.value." + value.getAsString().toLowerCase(Locale.ROOT);
      return p.id.startsWith("throw.") && Language.getInstance().has(key) ? GuiSupport.tr(key) : GuiSupport.value(value);
   }

   private static boolean percentage(Parameter p) {
      return p.max != null && p.max == 1.0 && p.id.toLowerCase(Locale.ROOT).contains("health");
   }

   private static String displayNumber(JsonElement value, boolean percent) {
      if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
         double number = value.getAsDouble() * (percent ? 100 : 1);
         return BigDecimal.valueOf(number).stripTrailingZeros().toPlainString();
      } else {
         return value.getAsString();
      }
   }

   private static String unit(Parameter p) {
      String id = p.id.toLowerCase(Locale.ROOT);
      if (percentage(p)) {
         return "%";
      } else if (id.startsWith("throw.") && id.endsWith("speed")) {
         return GuiSupport.tr("cnpcgun.editor.multiplier");
      } else if (id.endsWith("elevation") || id.endsWith("depression")) {
         return GuiSupport.tr("cnpcgun.editor.degrees");
      } else if (id.endsWith("deviation")) {
         return GuiSupport.tr("cnpcgun.editor.blocks");
      } else if (id.contains("distance") || id.contains("range")) {
         return GuiSupport.tr("cnpcgun.editor.blocks");
      } else if (id.contains("interval")
         || id.contains("cooldown")
         || id.contains("delay")
         || id.contains("timeout")
         || id.contains("maxticks")
         || id.endsWith("windup")
         || id.endsWith("recovery")) {
         return GuiSupport.tr("cnpcgun.editor.ticks");
      } else {
         return id.contains("multiplier") ? GuiSupport.tr("cnpcgun.editor.multiplier") : "";
      }
   }

   private String hint(EditorForm.Field field) {
      if (!field.available.getAsBoolean()) {
         return field.reason;
      } else {
         Parameter p = field.parameter;
         if (p.id.equals("combat.disableFriendlyFire")) {
            return GuiSupport.tr("cnpcgun.editor.friendlyFireHint");
         } else if (p.id.equals("gun.prefire")) {
            return GuiSupport.tr("cnpcgun.editor.prefireHint");
         } else if (p.id.equals("gun.overrideNativeRate")) {
            return GuiSupport.tr("cnpcgun.editor.nativeRateHint");
         } else if (p.id.equals("gun.disableIdleAnimation")) {
            return GuiSupport.tr("cnpcgun.editor.idleGunAnimationHint");
         } else if (p.min != null) {
            double scale = percentage(p) ? 100.0 : 1.0;
            return displayNumber(new JsonPrimitive(p.min * scale), false) + " – " + displayNumber(new JsonPrimitive(p.max * scale), false) + " " + unit(p);
         } else {
            return unit(p);
         }
      }
   }

   private String fieldLabel(EditorForm.Field field) {
      String id = field.parameter.id;
      return this.compactSlots && id.startsWith("weapon.slot")
         ? GuiSupport.tr("cnpcgun.editor.slot." + id.substring(id.lastIndexOf(46) + 1))
         : GuiSupport.tr(field.parameter.label);
   }

   private void layout() {
      List<EditorLayout.FormField> entries = this.fields
         .stream()
         .map(
            fieldx -> new EditorLayout.FormField(
               fieldx.combined ? 1 : fieldx.controls.size(), fieldx.combined || !fieldx.controls.isEmpty() && fieldx.controls.get(0) instanceof EditorTextField
            )
         )
         .toList();
      EditorLayout.FormGrid grid = EditorLayout.formGrid(
         new EditorLayout.Box(this.getX(), this.getY(), this.width, this.height), entries, this.compactSlots, this.scroll
      );

      for (int n = 0; n < this.fields.size(); n++) {
         EditorForm.Field field = this.fields.get(n);
         EditorLayout.FormCell cell = grid.cells().get(n);
         field.box = cell.row();
         if (field.segmented) {
            int count = field.controls.size();
            int w = (field.box.width() - 8) / count;

            for (int i = 0; i < count; i++) {
               AbstractWidget control = field.controls.get(i);
               control.setX(field.box.x() + 4 + i * w);
               control.setY(field.box.y() + 22);
               control.setWidth(i == count - 1 ? field.box.right() - 4 - control.getX() : w);
            }
         } else if (field.combined) {
            int w = Math.max(82, Math.min(126, field.box.width() / 2));
            int x = field.box.right() - w - 4;
            AbstractWidget toggle = field.controls.get(0);
            AbstractWidget input = field.controls.get(1);
            toggle.setX(x);
            toggle.setY(field.box.y() + (this.compactSlots ? 3 : 5));
            toggle.setWidth(26);
            input.setX(x + 34);
            input.setY(field.box.y() + (this.compactSlots ? 10 : 12));
            input.setWidth(w - 38);
         } else {
            for (int i = 0; i < field.controls.size(); i++) {
               AbstractWidget control = field.controls.get(i);
               EditorLayout.Box box = cell.controls().get(i);
               control.setX(box.x());
               control.setY(box.y());
               control.setWidth(box.width());
            }
         }
      }

      this.totalHeight = grid.totalHeight();
   }

   private boolean inViewport(EditorForm.Field field) {
      return field.box.bottom() > this.getY() + 1 && field.box.y() < this.getY() + this.height - 1;
   }

   protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
      EditorStyle.panel(g, new EditorLayout.Box(this.getX(), this.getY(), this.width, this.height), -1119006);
      this.layout();
      g.enableScissor(this.getX() + 1, this.getY() + 1, this.getX() + this.width - 1, this.getY() + this.height - 1);

      for (EditorForm.Field field : this.fields) {
         if (this.inViewport(field)) {
            if (field.parameter == null) {
               g.fill(field.box.x(), field.box.y() + 6, field.box.x() + 3, field.box.y() + 16, -11047869);
               EditorStyle.text(g, this.font, field.heading, field.box.x() + 8, field.box.y() + 7, field.box.width() - 12, -14077902);
            } else {
               boolean enabled = !this.locked && field.available.getAsBoolean();
               boolean components = field.controls.size() > 1 && !field.combined && !field.segmented;
               int labelWidth = field.segmented
                  ? field.box.width() - 8
                  : (components ? field.box.width() / 2 - 8 : field.controls.get(0).getX() - field.box.x() - 10);
               EditorStyle.text(
                  g,
                  this.font,
                  this.fieldLabel(field),
                  field.box.x() + 2,
                  field.box.y() + (this.compactSlots ? 4 : 5),
                  labelWidth,
                  enabled ? -14077902 : -9932693
               );
               if (!field.segmented) {
                  EditorStyle.text(
                     g,
                     this.font,
                     field.invalid && this.numberEditable(field) ? GuiSupport.tr("cnpcgun.editor.invalidField") : this.hint(field),
                     field.box.x() + (components ? field.box.width() / 2 : 2),
                     field.box.y() + (components ? 5 : (this.compactSlots ? 15 : 18)),
                     components ? field.box.width() / 2 - 4 : labelWidth,
                     field.invalid ? -6074553 : -9932693
                  );
               }

               if (components) {
                  for (int i = 0; i < field.controls.size(); i++) {
                     AbstractWidget control = field.controls.get(i);
                     String part = field.controls.size() == 2
                        ? GuiSupport.tr("cnpcgun.editor." + (i == 0 ? "minimum" : "maximum"))
                        : new String[]{"X", "Y", "Z"}[i];
                     EditorStyle.text(
                        g,
                        this.font,
                        part,
                        control.getX() - (field.controls.size() == 2 ? 28 : 16),
                        control.getY(),
                        field.controls.size() == 2 ? 22 : 10,
                        -9932693
                     );
                  }
               }

               g.hLine(field.box.x(), field.box.right() - 6, field.box.bottom() - 1, -3092283);

               for (AbstractWidget control : field.controls) {
                  boolean controlEnabled = enabled
                     && (!field.segmented || !((EditorButton)control).selected)
                     && (control == field.controls.get(0) || this.numberEditable(field));
                  control.active = controlEnabled;
                  if (control instanceof EditorTextField input) {
                     input.setEditable(controlEnabled);
                  }

                  control.render(g, mouseX, mouseY, partialTick);
               }
            }
         }
      }

      g.disableScissor();
      if (this.totalHeight > this.height - 8) {
         int track = Math.max(1, this.height - 8);
         int thumb = Math.max(12, track * track / this.totalHeight);
         int top = this.getY() + 4 + (track - thumb) * this.scroll / Math.max(1, this.totalHeight - track);
         g.fill(this.getX() + this.width - 4, top, this.getX() + this.width - 2, top + thumb, -9932693);
      }

      this.setTooltip(null);
      if (this.isHovered) {
         for (EditorForm.Field fieldx : this.fields) {
            if (fieldx.parameter != null
               && this.inViewport(fieldx)
               && fieldx.box.contains(mouseX, mouseY)
               && (
                  fieldx.controls.size() > 1 && mouseY < fieldx.box.y() + 20
                     || mouseX < fieldx.controls.get(0).getX() - 4
                     || !fieldx.available.getAsBoolean()
               )) {
               String label = this.fieldLabel(fieldx);
               String description = fieldx.invalid && this.numberEditable(fieldx) ? GuiSupport.tr("cnpcgun.editor.invalidField") : this.hint(fieldx);
               boolean componentsx = fieldx.controls.size() > 1 && !fieldx.combined && !fieldx.segmented;
               int labelWidthx = fieldx.segmented
                  ? fieldx.box.width() - 8
                  : (componentsx ? fieldx.box.width() / 2 - 8 : fieldx.controls.get(0).getX() - fieldx.box.x() - 10);
               int hintWidth = componentsx ? fieldx.box.width() / 2 - 4 : labelWidthx;
               if (fieldx.parameter.id.equals("gun.prefire")
                  || this.font.width(label) > labelWidthx
                  || !fieldx.segmented && this.font.width(description) > hintWidth
                  || !fieldx.available.getAsBoolean()) {
                  this.setTooltip(Tooltip.create(Component.literal(label + (description.isEmpty() ? "" : "\n" + description))));
               }
            }
         }
      }
   }

   private void focus(AbstractWidget chosen) {
      if (this.focusedControl != null) {
         this.focusedControl.setFocused(false);
      }

      this.focusedControl = chosen;
      if (chosen != null) {
         chosen.setFocused(true);
      }
   }

   public boolean mouseClicked(double x, double y, int button) {
      if (this.visible && new EditorLayout.Box(this.getX(), this.getY(), this.width, this.height).contains(x, y)) {
         this.layout();

         for (EditorForm.Field field : this.fields) {
            if (field.parameter != null && this.inViewport(field) && !this.locked && field.available.getAsBoolean()) {
               for (AbstractWidget control : field.controls) {
                  if ((!field.segmented || !((EditorButton)control).selected)
                     && (control == field.controls.get(0) || this.numberEditable(field))
                     && control.mouseClicked(x, y, button)) {
                     this.focus(control);
                     return true;
                  }
               }
            }
         }

         this.focus(null);
         return true;
      } else {
         return false;
      }
   }

   public boolean mouseReleased(double x, double y, int button) {
      return this.focusedControl != null && this.focusedControl.mouseReleased(x, y, button);
   }

   public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
      return this.focusedControl != null && this.focusedControl.mouseDragged(x, y, button, dx, dy);
   }

   public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
      double delta = scrollY;
      if (!new EditorLayout.Box(this.getX(), this.getY(), this.width, this.height).contains(x, y)) {
         return false;
      } else {
         this.scrollTo(this.scroll - (int)Math.round(delta * 30.0));
         if (this.focusedControl != null
            && (this.focusedControl.getY() < this.getY() || this.focusedControl.getY() >= this.getY() + this.height)) {
            this.focus(null);
         }

         return true;
      }
   }

   public boolean keyPressed(int key, int scan, int modifiers) {
      if (this.locked) {
         return false;
      } else if (key == 258 && this.isFocused()) {
         List<AbstractWidget> controls = new ArrayList<>();

         for (EditorForm.Field field : this.fields) {
            if (field.parameter != null && field.available.getAsBoolean()) {
               controls.addAll(field.controls);
            }
         }

         int index = controls.indexOf(this.focusedControl);
         int next = index + ((modifiers & 1) != 0 ? -1 : 1);
         if (next >= 0 && next < controls.size()) {
            AbstractWidget chosen = controls.get(next);
            this.focus(chosen);

            for (EditorForm.Field fieldx : this.fields) {
               if (fieldx.controls.contains(chosen) && (fieldx.box.y() < this.getY() || fieldx.box.bottom() > this.getY() + this.height)) {
                  this.scrollTo(this.scroll + fieldx.box.y() - this.getY() - 3);
               }
            }

            return true;
         } else {
            this.focus(null);
            return false;
         }
      } else {
         return this.focusedControl != null && this.focusedControl.keyPressed(key, scan, modifiers);
      }
   }

   public boolean charTyped(char letter, int modifiers) {
      return !this.locked && this.focusedControl != null && this.focusedControl.charTyped(letter, modifiers);
   }

   public void setFocused(boolean focused) {
      super.setFocused(focused);
      if (this.focusedControl != null) {
         this.focusedControl.setFocused(focused);
      }
   }

   protected void updateWidgetNarration(NarrationElementOutput output) {
      this.defaultButtonNarrationText(output);
   }

   private final class Field {
      final Parameter parameter;
      final String heading;
      final List<AbstractWidget> controls = new ArrayList<>();
      final BooleanSupplier available;
      final String reason;
      EditorLayout.Box box;
      JsonElement value;
      boolean invalid;
      boolean segmented;
      boolean combined;
      boolean flagValue;

      Field(String heading) {
         this.heading = heading;
         this.parameter = null;
         this.available = () -> true;
         this.reason = "";
      }

      Field(Parameter parameter, JsonElement value, BooleanSupplier available, String reason, Consumer<JsonElement> changed) {
         this.heading = "";
         this.parameter = parameter;
         this.value = value.deepCopy();
         this.available = available;
         this.reason = reason;
         if (parameter.type.equals("boolean")) {
            EditorButton button = new EditorButton(0, 0, 80, 20, Component.empty(), EditorButton.Kind.NORMAL, () -> {
               this.value = new JsonPrimitive(!this.value.getAsBoolean());
               changed.accept(this.value.deepCopy());
               this.updateLabel();
            });
            this.controls.add(button);
            this.updateLabel();
         } else if (parameter.type.equals("enum")) {
            EditorButton button = new EditorButton(0, 0, 100, 20, Component.empty(), EditorButton.Kind.NORMAL, () -> {
               Map<String, String> options = new LinkedHashMap<>();

               for (String id : parameter.values) {
                  options.put(EditorForm.valueLabel(parameter, new JsonPrimitive(id)), id);
               }

               EditorForm.this.picker.choose(Component.translatable(parameter.label), options, idx -> {
                  this.value = parameter.validate(new JsonPrimitive(idx));
                  changed.accept(this.value.deepCopy());
                  this.updateLabel();
               });
            });
            this.controls.add(button);
            this.updateLabel();
         } else {
            int count = parameter.type.equals("range") ? 2 : (parameter.type.equals("vector") ? 3 : 1);

            for (int i = 0; i < count; i++) {
               EditorTextField input = new EditorTextField(EditorForm.this.font, 0, 0, 80, 8, Component.translatable(parameter.label));
               JsonElement component = count == 1 ? value : value.getAsJsonArray().get(i);
               input.setValue(EditorForm.displayNumber(component, EditorForm.percentage(parameter)));
               this.controls.add(input);
            }

            List<EditorTextField> inputs = this.controls.stream().map(controlx -> (EditorTextField)controlx).toList();

            for (EditorTextField control : inputs) {
               control.setResponder(ignored -> {
                  try {
                     JsonElement next;
                     if (count <= 1) {
                        if (!parameter.type.equals("integer") && !parameter.type.equals("number")) {
                           next = new JsonPrimitive(inputs.get(0).getValue());
                        } else {
                           double number = Double.parseDouble(inputs.get(0).getValue().trim());
                           next = new JsonPrimitive(EditorForm.percentage(parameter) ? number / 100.0 : number);
                        }
                     } else {
                        JsonArray array = new JsonArray();

                        for (EditorTextField part : inputs) {
                           array.add(Double.parseDouble(part.getValue().trim()));
                        }

                        next = array;
                     }

                     this.value = parameter.validate(next);
                     this.invalid = false;
                     changed.accept(this.value.deepCopy());
                  } catch (IllegalStateException | IllegalArgumentException var10x) {
                     this.invalid = true;
                  }

                  for (EditorTextField part : inputs) {
                     part.invalid = this.invalid;
                  }
               });
            }
         }
      }

      void updateLabel() {
         if (!this.controls.isEmpty()) {
            if (this.segmented) {
               for (int i = 0; i < this.controls.size(); i++) {
                  EditorButton button = (EditorButton)this.controls.get(i);
                  button.setMessage(Component.literal(EditorForm.valueLabel(this.parameter, new JsonPrimitive(this.parameter.values[i]))));
                  button.selected = this.value.getAsString().equals(this.parameter.values[i]);
               }
            } else if (this.combined) {
               EditorButton button = (EditorButton)this.controls.get(0);
               button.setMessage(Component.literal(this.flagValue ? "✓" : "—"));
               button.selected = this.flagValue;
            } else {
               EditorButton button = (EditorButton)this.controls.get(0);
               button.setMessage(Component.literal(EditorForm.valueLabel(this.parameter, this.value)));
               button.selected = this.parameter.type.equals("boolean") && this.value.getAsBoolean();
               button.setTooltip(Tooltip.create(Component.literal(EditorForm.valueLabel(this.parameter, this.value))));
            }
         }
      }
   }

   public interface Picker {
      void choose(Component var1, Map<String, String> var2, Consumer<String> var3);
   }
}
