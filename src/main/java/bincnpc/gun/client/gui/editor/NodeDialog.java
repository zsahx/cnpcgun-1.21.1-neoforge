package bincnpc.gun.client.gui.editor;

import bincnpc.gun.client.EditorSession;
import bincnpc.gun.client.gui.GuiSupport;
import bincnpc.gun.common.api.SafeJson;
import bincnpc.gun.common.config.Parameter;
import bincnpc.gun.slashblade.combo.GraphValidator;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;

public final class NodeDialog extends EditorDialogs.Base {
   private final EditorSession session;
   private final JsonObject node;
   private final List<String> nodeIds;
   private final Consumer<JsonObject> applied;
   private EditorForm form;
   private int tab;

   public NodeDialog(EditorDialogs.Host host, EditorSession session, JsonObject initial, List<String> nodeIds, Consumer<JsonObject> applied) {
      super(host, Component.translatable("cnpcgun.gui.node"));
      this.session = session;
      this.node = initial.deepCopy();
      this.nodeIds = List.copyOf(nodeIds);
      this.applied = applied;
   }

   private JsonObject parameters() {
      if (!this.node.has("parameters") || !this.node.get("parameters").isJsonObject()) {
         this.node.add("parameters", new JsonObject());
      }

      return this.node.getAsJsonObject("parameters");
   }

   private JsonArray array(String key) {
      if (!this.node.has(key) || !this.node.get(key).isJsonArray()) {
         this.node.add(key, new JsonArray());
      }

      return this.node.getAsJsonArray(key);
   }

   @Override
   protected void init() {
      super.init();
      this.subtitle = GuiSupport.tr("cnpcgun.editor.nodeHint");
      String[] names = new String[]{"nodeGeneral", "nodeParameters", "nodeConditions", "nodeInterrupts", "nodeEdges"};
      int w = (this.content.width() - 16) / 5;

      for (int i = 0; i < 5; i++) {
         final int tabIndex = i;
         EditorButton button = (EditorButton)this.addRenderableWidget(
            new EditorButton(
               this.content.x() + i * (w + 4), this.content.y(), w, 20, Component.translatable("cnpcgun.editor." + names[i]), EditorButton.Kind.NORMAL, () -> {
                  if (this.form != null && this.form.invalid()) {
                     this.failure();
                  } else {
                     this.tab = tabIndex;
                     this.rebuild();
                  }
               }
            )
         );
         button.selected = this.tab == i;
      }

      EditorLayout.Box area = this.content.below(26);
      this.form = null;
      if (this.tab == 0) {
         JsonObject action = this.session.actionInfo(this.node.get("action").getAsString());
         EditorButton actionButton = (EditorButton)this.addRenderableWidget(
            new EditorButton(
               area.x(),
               area.y(),
               area.width(),
               20,
               Component.literal(action == null ? GuiSupport.tr("cnpcgun.gui.unavailable") : GuiSupport.actionName(action)),
               EditorButton.Kind.NORMAL,
               () -> EditorDialogs.actions(this.host, this.session, Component.translatable("cnpcgun.gui.actions"), idx -> {
                  if (!idx.equals(this.node.get("action").getAsString())) {
                     this.node.addProperty("action", idx);
                     this.node.add("parameters", new JsonObject());
                  }
               })
            )
         );
         actionButton.active = !this.session.pending;
         this.form = (EditorForm)this.addRenderableWidget(new EditorForm(this.font, area.below(26), this.picker()));
         this.form.field(new Parameter("id", "string", "n1", null, null, null, ""), this.node.get("id"), value -> this.node.add("id", value));
         this.form.field(Parameter.integer("timeout", 200, 1, 2400), this.node.get("timeout"), value -> this.node.add("timeout", value));
      } else if (this.tab == 1) {
         this.form = (EditorForm)this.addRenderableWidget(new EditorForm(this.font, area, this.picker()));
         JsonObject info = this.session.actionInfo(this.node.get("action").getAsString());
         if (info != null) {
            for (Parameter p : EditorSession.parameters(info)) {
               this.form
                  .field(
                     p, this.parameters().has(p.id) ? this.parameters().get(p.id) : SafeJson.copy(p.defaultValue), value -> this.parameters().add(p.id, value)
                  );
            }
         }
      } else if (this.tab != 2 && this.tab != 3) {
         this.form = (EditorForm)this.addRenderableWidget(new EditorForm(this.font, area, this.picker()));
         List<String> targets = new ArrayList<>();
         targets.add("end");

         for (String id : this.nodeIds) {
            if (!id.equals(this.node.get("id").getAsString())) {
               targets.add(id);
            }
         }

         for (String key : List.of("onSuccess", "onFailure", "onTimeout")) {
            this.form.field(Parameter.choice(key, "end", targets.toArray(String[]::new)), this.node.get(key), value -> this.node.add(key, value));
         }
      } else {
         String key = this.tab == 2 ? "conditions" : "interrupts";
         String name = GuiSupport.tr("cnpcgun.gui." + key);
         this.addRenderableWidget(
            new EditorButton(
               area.x(),
               area.y(),
               area.width(),
               20,
               Component.literal(GuiSupport.tr("cnpcgun.editor.editClauseCount", name, this.array(key).size())),
               EditorButton.Kind.NORMAL,
               () -> EditorDialogs.conditions(this.host, name, this.array(key), values -> this.node.add(key, values))
            )
         );
         List<EditorList.Row> rows = new ArrayList<>();

         for (int i = 0; i < this.array(key).size(); i++) {
            JsonElement raw = this.array(key).get(i);
            String idx = raw.isJsonObject() ? raw.getAsJsonObject().get("id").getAsString() : raw.getAsString();
            rows.add(new EditorList.Row(String.valueOf(i), GuiSupport.tr("cnpcgun.condition." + idx), "", true));
         }

         this.addRenderableWidget(new EditorList(this.font, area.below(26), rows, "", ignored -> {}));
      }

      this.button(0, 2, Component.translatable("cnpcgun.gui.apply"), EditorButton.Kind.PRIMARY, () -> {
         try {
            if (this.form != null && this.form.invalid()) {
               throw new IllegalArgumentException();
            }

            String idx = this.node.get("id").getAsString();
            if (!idx.matches("[A-Za-z0-9_.:-]{1,64}") || idx.equals("end")) {
               throw new IllegalArgumentException();
            }

            Parameter.integer("timeout", 200, 1, 2400).validate(this.node.get("timeout"));
            GraphValidator.validateConditions(this.array("conditions"));
            GraphValidator.validateConditions(this.array("interrupts"));
            JsonObject info = this.session.actionInfo(this.node.get("action").getAsString());
            if (info != null) {
               GraphValidator.parameters(this.parameters(), EditorSession.parameters(info));
            }

            this.applied.accept(this.node.deepCopy());
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
      if (this.form != null) {
         this.form.tick();
      }
   }
}
