package bincnpc.gun.common.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

public final class SafeJson {
   public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
   public static final int MAX_BYTES = 65536;

   private SafeJson() {
   }

   public static JsonObject parseObject(String raw) {
      return object(parseBounded(raw, 65536, 14));
   }

   public static JsonObject persistedObject(String raw) {
      JsonElement parsed = parseBounded(raw, 4194304, 28);
      if (!parsed.isJsonObject()) {
         throw new IllegalArgumentException("expected stored object");
      } else {
         return parsed.getAsJsonObject();
      }
   }

   private static JsonElement parseBounded(String raw, int maxBytes, int maxDepth) {
      if (raw != null && raw.length() <= maxBytes && raw.getBytes(StandardCharsets.UTF_8).length <= maxBytes) {
         int depth = 0;
         boolean quoted = false;
         boolean escaped = false;

         for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (quoted) {
               if (escaped) {
                  escaped = false;
               } else if (c == '\\') {
                  escaped = true;
               } else if (c == '"') {
                  quoted = false;
               }
            } else if (c == '"') {
               quoted = true;
            } else if (c == '{' || c == '[') {
               if (++depth > maxDepth) {
                  throw new IllegalArgumentException("data nesting limit");
               }
            } else if (c == '}' || c == ']') {
               if (--depth < 0) {
                  throw new IllegalArgumentException("unbalanced JSON");
               }
            }
         }

         if (!quoted && depth == 0) {
            try {
               return JsonParser.parseString(raw);
            } catch (JsonParseException var8) {
               throw new IllegalArgumentException("invalid JSON", var8);
            }
         } else {
            throw new IllegalArgumentException("unbalanced JSON");
         }
      } else {
         throw new IllegalArgumentException("configuration too large");
      }
   }

   public static JsonElement copy(Object value) {
      JsonElement out = copy(value, 0, new int[]{8192});
      if (GSON.toJson(out).getBytes(StandardCharsets.UTF_8).length > 65536) {
         throw new IllegalArgumentException("configuration too large");
      } else {
         return out;
      }
   }

   private static JsonElement copy(Object value, int depth, int[] budget) {
      if (depth > 14 || --budget[0] < 0) {
         throw new IllegalArgumentException("data nesting/size limit");
      } else if (value == null) {
         return JsonNull.INSTANCE;
      } else if (value instanceof JsonElement json) {
         if (json.isJsonNull()) {
            return JsonNull.INSTANCE;
         } else if (json.isJsonPrimitive()) {
            JsonPrimitive p = json.getAsJsonPrimitive();
            return copy(p.isBoolean() ? p.getAsBoolean() : (p.isNumber() ? p.getAsNumber() : p.getAsString()), depth, budget);
         } else if (json.isJsonArray()) {
            return copy(json.getAsJsonArray().asList(), depth, budget);
         } else {
            Map<String, Object> map = new LinkedHashMap<>();
            json.getAsJsonObject().entrySet().forEach(ex -> map.put((String)ex.getKey(), ex.getValue()));
            return copy(map, depth, budget);
         }
      } else if (value instanceof Boolean b) {
         return new JsonPrimitive(b);
      } else if (value instanceof Number n) {
         if (!Double.isFinite(n.doubleValue())) {
            throw new IllegalArgumentException("nonfinite number");
         } else {
            return new JsonPrimitive(n);
         }
      } else if (value instanceof CharSequence s) {
         if (s.length() > 4096) {
            throw new IllegalArgumentException("string too long");
         } else {
            return new JsonPrimitive(s.toString());
         }
      } else if (value instanceof Map<?, ?> map) {
         if (map.size() > 256) {
            throw new IllegalArgumentException("too many fields");
         } else {
            boolean indexed = !map.isEmpty();

            for (Object key : map.keySet()) {
               if (!(key instanceof String) || !((String)key).matches("0|[1-9][0-9]*")) {
                  indexed = false;
               }
            }

            if (indexed) {
               JsonArray array = new JsonArray();

               for (int i = 0; i < map.size(); i++) {
                  if (!map.containsKey(Integer.toString(i))) {
                     throw new IllegalArgumentException("sparse array");
                  }

                  array.add(copy(map.get(Integer.toString(i)), depth + 1, budget));
               }

               return array;
            } else {
               JsonObject obj = new JsonObject();

               for (Entry<?, ?> e : map.entrySet()) {
                  if (!(e.getKey() instanceof String keyx) || keyx.length() > 128) {
                     throw new IllegalArgumentException("invalid field");
                  }

                  obj.add(keyx, copy(e.getValue(), depth + 1, budget));
               }

               return obj;
            }
         }
      } else if (value instanceof Iterable<?> values) {
         JsonArray array = new JsonArray();

         for (Object v : values) {
            array.add(copy(v, depth + 1, budget));
         }

         return array;
      } else if (!value.getClass().isArray()) {
         throw new IllegalArgumentException("expected primitive, map, list or array");
      } else {
         JsonArray array = new JsonArray();

         for (int i = 0; i < Array.getLength(value); i++) {
            array.add(copy(Array.get(value, i), depth + 1, budget));
         }

         return array;
      }
   }

   public static JsonObject object(Object value) {
      JsonElement json = copy(value);
      if (!json.isJsonObject()) {
         throw new IllegalArgumentException("expected object");
      } else {
         return json.getAsJsonObject();
      }
   }

   public static Object plain(JsonElement value) {
      if (value == null || value.isJsonNull()) {
         return null;
      } else if (value.isJsonArray()) {
         Object[] out = new Object[value.getAsJsonArray().size()];

         for (int i = 0; i < out.length; i++) {
            out[i] = plain(value.getAsJsonArray().get(i));
         }

         return out;
      } else if (value.isJsonObject()) {
         Map<String, Object> out = new LinkedHashMap<>();
         value.getAsJsonObject().entrySet().forEach(e -> out.put((String)e.getKey(), plain((JsonElement)e.getValue())));
         return out;
      } else {
         JsonPrimitive p = value.getAsJsonPrimitive();
         return p.isBoolean() ? p.getAsBoolean() : (p.isNumber() ? p.getAsDouble() : p.getAsString());
      }
   }

   public static String string(JsonObject obj, String key, String fallback) {
      JsonElement e = obj == null ? null : obj.get(key);
      return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : fallback;
   }

   public static JsonObject objectField(JsonObject obj, String key) {
      JsonElement value = obj == null ? null : obj.get(key);
      return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
   }

   public static JsonArray arrayField(JsonObject obj, String key) {
      JsonElement value = obj == null ? null : obj.get(key);
      return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
   }

   public static boolean bool(JsonObject obj, String key, boolean fallback) {
      JsonElement value = obj == null ? null : obj.get(key);
      return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() ? value.getAsBoolean() : fallback;
   }

   public static int integer(JsonObject obj, String key, int fallback) {
      JsonElement value = obj == null ? null : obj.get(key);
      if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
         double number = value.getAsDouble();
         return Double.isFinite(number) && number == Math.rint(number) && number >= -2.1474836E9F && number <= 2.147483647E9 ? (int)number : fallback;
      } else {
         return fallback;
      }
   }
}
