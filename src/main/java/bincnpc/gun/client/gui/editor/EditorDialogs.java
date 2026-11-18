package bincnpc.gun.client.gui.editor;

import bincnpc.gun.client.EditorSession;
import bincnpc.gun.client.gui.GuiSupport;
import bincnpc.gun.common.api.SafeJson;
import bincnpc.gun.common.config.Parameter;
import bincnpc.gun.slashblade.combo.Conditions;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class EditorDialogs {
   private EditorDialogs() {
   }

   public static void choose(EditorDialogs.Host host, Component title, Map<String, String> options, Consumer<String> chosen) {
      host.openDialog(new EditorDialogs.Choice(host, title, options, chosen));
   }

   public static void chooseRows(EditorDialogs.Host host, Component title, List<EditorList.Row> options, Consumer<String> chosen) {
      host.openDialog(new EditorDialogs.Choice(host, title, options, chosen));
   }

   public static void actions(EditorDialogs.Host host, EditorSession session, Component title, Consumer<String> chosen) {
      host.openDialog(new ActionPicker(host, session, title, chosen));
   }

   public static void form(EditorDialogs.Host host, Component title, Parameter[] schema, JsonObject initial, Consumer<JsonObject> applied) {
      host.openDialog(new EditorDialogs.Form(host, title, schema, initial, applied));
   }

   public static void conditions(EditorDialogs.Host host, String title, JsonArray initial, Consumer<JsonArray> applied) {
      host.openDialog(new EditorDialogs.Clauses(host, Component.literal(title), initial, applied));
   }

   public static void confirm(EditorDialogs.Host host, Component title, String message, List<EditorDialogs.Decision> choices) {
      confirm(host, title, message, List.of(), choices);
   }

   public static void confirm(EditorDialogs.Host host, Component title, String message, List<String> details, List<EditorDialogs.Decision> choices) {
      host.openDialog(new EditorDialogs.Confirm(host, title, message, details, choices));
   }

   public abstract static class Base extends Screen {
      protected final EditorDialogs.Host host;
      protected EditorLayout.Box box;
      protected EditorLayout.Box content;
      protected String error = "";
      protected String subtitle = "";
      private boolean rebuildPending;

      protected Base(EditorDialogs.Host host, Component title) {
         super(title);
         this.host = host;
      }

      protected void init() {
         this.box = EditorLayout.dialog(this.width, this.height);
         this.content = EditorLayout.dialogContent(this.box);
      }

      public final void rebuild() {
         this.rebuildPending = true;
      }

      private void rebuildNow() {
         if (this.rebuildPending) {
            this.rebuildPending = false;
            List<EditorDialogs.Base.FormMemory> memory = new ArrayList<>();

            for (GuiEventListener child : this.children()) {
               if (child instanceof EditorForm form) {
                  memory.add(new EditorDialogs.Base.FormMemory(form.scrollOffset(), form.invalidText()));
               }
            }

            this.clearWidgets();
            this.init();
            int index = 0;

            for (GuiEventListener childx : this.children()) {
               if (childx instanceof EditorForm form && index < memory.size()) {
                  EditorDialogs.Base.FormMemory saved = memory.get(index++);
                  form.scrollTo(saved.scroll);
                  form.restoreInvalidText(saved.invalid);
               }
            }
         }
      }

      protected EditorButton button(int index, int count, Component label, EditorButton.Kind kind, Runnable clicked) {
         int w = (this.box.width() - 20 - (count - 1) * 6) / count;
         EditorButton button = (EditorButton)this.addRenderableWidget(
            new EditorButton(this.box.x() + 10 + index * (w + 6), this.box.bottom() - 28, w, 20, label, kind, clicked)
         );
         button.setTooltip(Tooltip.create(label));
         return button;
      }

      public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
         this.rebuildNow();
         g.flush();
         g.fill(0, 0, this.width, this.height, -1608374493);
         EditorStyle.panel(g, this.box, this.panelFill());
         g.fill(this.box.x() + 2, this.box.y() + 2, this.box.right() - 2, this.box.y() + 36, -12828349);
         EditorStyle.text(g, this.font, this.title.getString(), this.box.x() + 12, this.box.y() + 9, this.box.width() - 24, -395024);
         EditorStyle.text(
            g,
            this.font,
            this.error.isEmpty() ? this.subtitle : this.error,
            this.box.x() + 12,
            this.box.y() + 23,
            this.box.width() - 24,
            this.error.isEmpty() ? -3551552 : -936281
         );
         super.render(g, mouseX, mouseY, partialTick);
      }

      public void onClose() {
         this.host.closeDialog();
      }

      public void tick() {
         this.rebuildNow();
      }

      public boolean isPauseScreen() {
         return false;
      }

      protected void failure() {
         this.error = GuiSupport.tr("cnpcgun.error.invalid_argument");
      }

      protected int panelFill() {
         return -1119006;
      }

      protected EditorForm.Picker picker() {
         return (title, options, chosen) -> EditorDialogs.choose(this.host, title, options, chosen);
      }

      private record FormMemory(int scroll, Map<String, List<String>> invalid) {
      }
   }

   private static final class Choice extends EditorDialogs.Base {
      private final List<EditorList.Row> choices;
      private final Consumer<String> chosen;
      private String query = "";

      Choice(EditorDialogs.Host host, Component title, Map<String, String> options, Consumer<String> chosen) {
         super(host, title);
         List<EditorList.Row> rows = new ArrayList<>();
         options.forEach((label, id) -> rows.add(new EditorList.Row(id, label, "", true)));
         this.choices = List.copyOf(rows);
         this.chosen = chosen;
      }

      Choice(EditorDialogs.Host host, Component title, List<EditorList.Row> options, Consumer<String> chosen) {
         super(host, title);
         this.choices = List.copyOf(options);
         this.chosen = chosen;
      }

      @Override
      protected void init() {
         super.init();
         this.subtitle = GuiSupport.tr("cnpcgun.editor.chooseHint");
         EditorTextField search = (EditorTextField)this.addRenderableWidget(
            new EditorTextField(
               this.font, this.content.x() + 4, this.content.y() + 4, this.content.width() - 8, 8, Component.translatable("cnpcgun.editor.search")
            )
         );
         search.setValue(this.query);
         EditorList list = (EditorList)this.addRenderableWidget(new EditorList(this.font, this.content.below(23), this.choices, "", id -> {
            this.host.closeDialog();
            this.chosen.accept(id);
         }));
         list.filter(this.query);
         search.setResponder(text -> {
            this.query = text;
            list.filter(text);
         });
         this.button(0, 1, Component.translatable("gui.cancel"), EditorButton.Kind.NORMAL, this.host::closeDialog);
      }
   }

   private static final class Clauses extends EditorDialogs.Base {
      private final JsonArray data;
      private final Consumer<JsonArray> applied;
      private int selection;
      private EditorForm form;

      Clauses(EditorDialogs.Host host, Component title, JsonArray source, Consumer<JsonArray> applied) {
         super(host, title);
         this.data = source.deepCopy();
         this.applied = applied;
      }

      @Override
      protected void init() {
         super.init();
         this.subtitle = GuiSupport.tr("cnpcgun.editor.conditionsHint");
         int listWidth = Math.max(96, Math.min(150, this.content.width() / 3));
         EditorButton add = (EditorButton)this.addRenderableWidget(
            new EditorButton(
               this.content.x(), this.content.y(), listWidth, 20, Component.translatable("cnpcgun.gui.addCondition"), EditorButton.Kind.NORMAL, () -> {
                  Map<String, String> options = new LinkedHashMap<>();

                  for (String idx : Conditions.ids()) {
                     options.put(GuiSupport.tr("cnpcgun.condition." + idx), idx);
                  }

                  EditorDialogs.choose(this.host, Component.translatable("cnpcgun.gui.addCondition"), options, idxx -> {
                     JsonObject itemx = new JsonObject();
                     itemx.addProperty("id", idxx);
                     if (idxx.equals("state")) {
                        itemx.addProperty("value", "ATTACKING");
                     }

                     if (idxx.equals("weapon_type")) {
                        itemx.addProperty("value", "SLASHBLADE");
                     }

                     this.data.add(itemx);
                     this.selection = this.data.size() - 1;
                  });
               }
            )
         );
         add.active = this.data.size() < 16;
         List<EditorList.Row> rows = new ArrayList<>();

         for (int i = 0; i < this.data.size(); i++) {
            JsonElement raw = this.data.get(i);
            String id = raw.isJsonObject() ? raw.getAsJsonObject().get("id").getAsString() : raw.getAsString();
            rows.add(
               new EditorList.Row(String.valueOf(i), GuiSupport.tr("cnpcgun.condition." + id), GuiSupport.tr("cnpcgun.editor.conditionNumber", i + 1), true)
            );
         }

         this.addRenderableWidget(
            new EditorList(
               this.font,
               new EditorLayout.Box(this.content.x(), this.content.y() + 24, listWidth, this.content.height() - 24),
               rows,
               String.valueOf(this.selection),
               idx -> {
                  this.selection = Integer.parseInt(idx);
                  this.rebuild();
               }
            )
         );
         EditorLayout.Box detail = new EditorLayout.Box(
            this.content.x() + listWidth + 8, this.content.y(), this.content.width() - listWidth - 8, this.content.height() - 24
         );
         this.form = (EditorForm)this.addRenderableWidget(new EditorForm(this.font, detail, this.picker()));
         if (this.selection >= 0 && this.selection < this.data.size()) {
            JsonElement raw = this.data.get(this.selection);
            JsonObject item;
            if (raw.isJsonObject()) {
               item = raw.getAsJsonObject();
            } else {
               item = new JsonObject();
               item.addProperty("id", raw.getAsString());
               this.data.set(this.selection, item);
            }

            String id = item.get("id").getAsString();
            List<Parameter> fields = new ArrayList<>();
            fields.add(Parameter.bool("not", false));
            if (Set.of("distance", "health", "target_count", "resource", "combat_time").contains(id)) {
               fields.add(Parameter.number("min", 0.0, 0.0, id.equals("health") ? 1.0 : 100000.0));
               fields.add(Parameter.number("max", id.equals("health") ? 1.0 : 100000.0, 0.0, id.equals("health") ? 1.0 : 100000.0));
            }

            if (id.equals("weapon_type")) {
               fields.add(Parameter.choice("value", "SLASHBLADE", "SLASHBLADE", "GUN", "MELEE", "UNKNOWN"));
            }

            if (id.equals("state")) {
               fields.add(
                  Parameter.choice("value", "ATTACKING", "IDLE", "ATTACKING", "RELOADING", "EXECUTING_ACTION", "RETREATING", "WAITING", "NO_AMMO", "DISABLED")
               );
            }

            for (Parameter p : fields) {
               this.form.field(p, item.has(p.id) ? item.get(p.id) : SafeJson.copy(p.defaultValue), value -> item.add(p.id, value));
            }
         }

         EditorButton remove = (EditorButton)this.addRenderableWidget(
            new EditorButton(
               detail.x(),
               this.content.bottom() - 20,
               detail.width(),
               20,
               Component.translatable("cnpcgun.editor.removeCondition"),
               EditorButton.Kind.NORMAL,
               () -> {
                  if (this.selection >= 0 && this.selection < this.data.size()) {
                     this.data.remove(this.selection);
                  }

                  this.selection = Math.max(0, this.selection - 1);
                  this.rebuild();
               }
            )
         );
         remove.active = !this.data.isEmpty();
         this.button(0, 2, Component.translatable("cnpcgun.gui.apply"), EditorButton.Kind.PRIMARY, () -> {
            try {
               if (this.form.invalid()) {
                  throw new IllegalArgumentException();
               }

               for (JsonElement itemx : this.data) {
                  Conditions.validate(itemx);
               }

               this.applied.accept(this.data.deepCopy());
               this.host.closeDialog();
            } catch (IllegalStateException | IllegalArgumentException var3x) {
               this.failure();
            }
         });
         this.button(1, 2, Component.translatable("gui.cancel"), EditorButton.Kind.NORMAL, this.host::closeDialog);
      }

      @Override
      public void tick() {
         super.tick();
         this.form.tick();
      }
   }

   private static final class Confirm extends EditorDialogs.Base {
      private final String message;
      private final List<String> details;
      private final List<EditorDialogs.Decision> decisions;

      Confirm(EditorDialogs.Host host, Component title, String message, List<String> details, List<EditorDialogs.Decision> decisions) {
         super(host, title);
         this.message = message;
         this.details = List.copyOf(details);
         this.decisions = List.copyOf(decisions);
      }

      @Override
      protected void init() {
         super.init();
         this.subtitle = GuiSupport.tr("cnpcgun.editor.confirmHint");
         int w = Math.min(340, (int)(this.box.width() * 0.85));
         int h = Math.min(174, (int)(this.box.height() * 0.56));
         this.box = new EditorLayout.Box((this.width - w) / 2, (this.height - h) / 2, w, h);
         this.content = EditorLayout.dialogContent(this.box);
         List<String> lines = new ArrayList<>();
         lines.add(this.message);
         lines.addAll(this.details);
         EditorScrollText scroll = (EditorScrollText)this.addRenderableWidget(new EditorScrollText(this.font, this.content, lines));
         this.setFocused(scroll);

         for (int i = 0; i < this.decisions.size(); i++) {
            EditorDialogs.Decision decision = this.decisions.get(i);
            this.button(i, this.decisions.size(), decision.label, decision.kind, () -> {
               this.host.closeDialog();
               decision.action.run();
            });
         }
      }
   }

   public record Decision(Component label, EditorButton.Kind kind, Runnable action) {
   }

   private static final class Form extends EditorDialogs.Base {
      private final Parameter[] schema;
      private final JsonObject data;
      private final Consumer<JsonObject> applied;
      private EditorForm form;
      private int scroll;

      Form(EditorDialogs.Host host, Component title, Parameter[] schema, JsonObject initial, Consumer<JsonObject> applied) {
         super(host, title);
         this.schema = (Parameter[])schema.clone();
         this.data = initial.deepCopy();
         this.applied = applied;
      }

      @Override
      protected void init() {
         if (this.form != null) {
            this.scroll = this.form.scrollOffset();
         }

         super.init();
         this.subtitle = GuiSupport.tr("cnpcgun.editor.formHint");
         this.form = (EditorForm)this.addRenderableWidget(new EditorForm(this.font, this.content, this.picker()));

         for (Parameter p : this.schema) {
            this.form.field(p, this.data.has(p.id) ? this.data.get(p.id) : SafeJson.copy(p.defaultValue), value -> this.data.add(p.id, value));
         }

         this.form.scrollTo(this.scroll);
         this.button(0, 2, Component.translatable("cnpcgun.gui.apply"), EditorButton.Kind.PRIMARY, () -> {
            if (this.form.invalid()) {
               this.failure();
            } else {
               try {
                  for (Parameter px : this.schema) {
                     this.data.add(px.id, px.validate(this.data.has(px.id) ? this.data.get(px.id) : SafeJson.copy(px.defaultValue)));
                  }

                  this.applied.accept(this.data.deepCopy());
                  this.host.closeDialog();
               } catch (IllegalStateException | IllegalArgumentException var5) {
                  this.failure();
               }
            }
         });
         this.button(1, 2, Component.translatable("gui.cancel"), EditorButton.Kind.NORMAL, this.host::closeDialog);
      }

      @Override
      public void tick() {
         super.tick();
         this.form.tick();
      }
   }

   public interface Host {
      void openDialog(Screen var1);

      void closeDialog();
   }
}
