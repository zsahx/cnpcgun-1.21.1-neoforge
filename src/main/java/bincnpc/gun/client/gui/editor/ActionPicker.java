package bincnpc.gun.client.gui.editor;

import bincnpc.gun.client.EditorSession;
import bincnpc.gun.client.gui.GuiSupport;
import bincnpc.gun.common.api.SafeJson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

public final class ActionPicker extends EditorDialogs.Base {
   public static final String[] CATEGORIES = new String[]{"all", "blade", "sa", "phantom", "movement", "defense"};
   private final EditorSession session;
   private final Consumer<String> chosen;
   private String category = "all";
   private String query = "";
   private EditorTextField search;

   public ActionPicker(EditorDialogs.Host host, EditorSession session, Component title, Consumer<String> chosen) {
      super(host, title);
      this.session = session;
      this.chosen = chosen;
   }

   public static Component categoryLabel(String category) {
      return Component.translatable("cnpcgun.editor.actionCategory." + category);
   }

   public static boolean matches(JsonObject info, String category) {
      if (info == null) {
         return false;
      } else {
         String type = SafeJson.string(info, "type", "");
         String var3 = category == null ? "all" : category;

         return switch (var3) {
            case "blade" -> type.equals("NORMAL_ATTACK") || type.equals("COMBO_ATTACK");
            case "sa" -> type.equals("SPECIAL_ATTACK");
            case "phantom" -> type.equals("PHANTOM_SWORD");
            case "movement" -> type.equals("MOVEMENT");
            case "defense" -> Set.of("DEFENSE", "COUNTER", "LOCK_ON").contains(type);
            default -> true;
         };
      }
   }

   public static Map<String, String> categories() {
      Map<String, String> rows = new LinkedHashMap<>();

      for (String id : CATEGORIES) {
         rows.put(categoryLabel(id).getString(), id);
      }

      return rows;
   }

   @Override
   protected void init() {
      super.init();
      this.subtitle = GuiSupport.tr("cnpcgun.editor.actionPickerHint");
      int sideWidth = Math.max(66, Math.min(106, this.content.width() / 3));
      int rowHeight = Math.max(18, Math.min(24, this.content.height() / CATEGORIES.length));

      for (int i = 0; i < CATEGORIES.length; i++) {
         String id = CATEGORIES[i];
         EditorButton button = (EditorButton)this.addRenderableWidget(
            new EditorButton(
               this.content.x(), this.content.y() + i * rowHeight, sideWidth, rowHeight - 2, categoryLabel(id), EditorButton.Kind.NAVIGATION, () -> {
                  this.category = id;
                  this.rebuild();
               }
            )
         );
         button.selected = this.category.equals(id);
         button.setTooltip(Tooltip.create(categoryLabel(id)));
      }

      EditorLayout.Box main = new EditorLayout.Box(
         this.content.x() + sideWidth + 6, this.content.y(), this.content.width() - sideWidth - 6, this.content.height()
      );
      this.search = (EditorTextField)this.addRenderableWidget(
         new EditorTextField(this.font, main.x() + 4, main.y() + 4, main.width() - 8, 8, Component.translatable("cnpcgun.editor.searchActions"))
      );
      this.search.setHint(Component.translatable("cnpcgun.editor.searchActions"));
      this.search.setValue(this.query);
      List<EditorList.Row> rows = new ArrayList<>();

      for (JsonElement raw : this.session.actions()) {
         if (raw.isJsonObject()) {
            JsonObject info = raw.getAsJsonObject();
            if (matches(info, this.category)) {
               String type = SafeJson.string(info, "type", "");
               String id = SafeJson.string(info, "id", "");
               if (!id.isEmpty()) {
                  rows.add(
                     new EditorList.Row(
                        id,
                        GuiSupport.actionName(info),
                        GuiSupport.tr("cnpcgun.type." + type.toLowerCase(Locale.ROOT)),
                        SafeJson.bool(info, "available", false)
                     )
                  );
               }
            }
         }
      }

      EditorList list = (EditorList)this.addRenderableWidget(new EditorList(this.font, main.below(23), rows, "", idx -> {
         this.host.closeDialog();
         this.chosen.accept(idx);
      }));
      list.filter(this.query);
      this.search.setResponder(text -> {
         this.query = text;
         list.filter(text);
      });
      this.button(0, 1, Component.translatable("gui.cancel"), EditorButton.Kind.NORMAL, this.host::closeDialog);
   }

   @Override
   public void tick() {
      super.tick();
      if (this.search != null) {
         this.search.tick();
      }
   }
}
