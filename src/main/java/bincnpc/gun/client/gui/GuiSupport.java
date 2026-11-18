package bincnpc.gun.client.gui;

import bincnpc.gun.common.api.SafeJson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

public final class GuiSupport {
   private GuiSupport() {
   }

   public static String tr(String key, Object... args) {
      return Component.translatable(key, args).getString();
   }

   public static String actionName(JsonObject info) {
      String label = SafeJson.string(info, "displayName", "");
      if (Language.getInstance().has(label)) {
         return tr(label);
      } else {
         String id = SafeJson.string(info, "id", "");
         return id.substring(id.lastIndexOf(47) + 1).replace('_', ' ');
      }
   }

   public static String value(JsonElement value) {
      if (value == null || value.isJsonNull()) {
         return tr("cnpcgun.gui.unsupported");
      } else if (value.isJsonPrimitive()) {
         JsonPrimitive primitive = value.getAsJsonPrimitive();
         if (primitive.isBoolean()) {
            return tr(primitive.getAsBoolean() ? "cnpcgun.gui.on" : "cnpcgun.gui.off");
         } else {
            String text = primitive.getAsString();
            String key = "cnpcgun.value." + text.toLowerCase(Locale.ROOT);
            return primitive.isString() && Language.getInstance().has(key) ? tr(key) : text;
         }
      } else if (value.isJsonArray()) {
         List<String> parts = new ArrayList<>();
         value.getAsJsonArray().forEach(p -> parts.add(value(p)));
         return String.join(", ", parts);
      } else {
         return "…";
      }
   }

   public static JsonObject with(String key, String value) {
      JsonObject out = new JsonObject();
      out.addProperty(key, value);
      return out;
   }
}
