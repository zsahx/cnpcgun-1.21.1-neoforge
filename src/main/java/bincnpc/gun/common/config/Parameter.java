package bincnpc.gun.common.config;

import com.google.gson.JsonElement;
import java.util.Arrays;
import java.util.Set;

public final class Parameter {
   private static final Set<String> NUMERIC_TYPES = Set.of("integer", "number", "range", "vector");
   public final String id;
   public final String type;
   public final Object defaultValue;
   public final Double min;
   public final Double max;
   public final String[] values;
   public final String capability;
   public final String label;

   public Parameter(String id, String type, Object defaultValue, Double min, Double max, String[] values, String capability) {
      this.id = id;
      this.type = type;
      this.defaultValue = defaultValue;
      this.min = min;
      this.max = max;
      this.values = values == null ? new String[0] : (String[])values.clone();
      this.capability = capability == null ? "" : capability;
      this.label = "cnpcgun.config." + id;
   }

   public static Parameter number(String id, double def, double min, double max) {
      return new Parameter(id, "number", def, min, max, null, "");
   }

   public static Parameter integer(String id, int def, int min, int max) {
      return new Parameter(id, "integer", def, (double)min, (double)max, null, "");
   }

   public static Parameter bool(String id, boolean def) {
      return new Parameter(id, "boolean", def, null, null, null, "");
   }

   public static Parameter choice(String id, String def, String... values) {
      return new Parameter(id, "enum", def, null, null, values, "");
   }

   public Parameter requires(String cap) {
      return new Parameter(this.id, this.type, this.defaultValue, this.min, this.max, this.values, cap);
   }

   public JsonElement validate(JsonElement input) {
      if (input != null && !input.isJsonNull() && this.type != null) {
         if (!NUMERIC_TYPES.contains(this.type)
            || this.min != null && this.max != null && Double.isFinite(this.min) && Double.isFinite(this.max) && !(this.min > this.max)) {
            String var2 = this.type;
            switch (var2) {
               case "boolean":
                  if (!input.isJsonPrimitive() || !input.getAsJsonPrimitive().isBoolean()) {
                     throw new IllegalArgumentException(this.id);
                  }
                  break;
               case "integer":
               case "number":
                  if (!input.isJsonPrimitive() || !input.getAsJsonPrimitive().isNumber()) {
                     throw new IllegalArgumentException(this.id);
                  }

                  double n = input.getAsDouble();
                  if (!Double.isFinite(n) || n < this.min || n > this.max || "integer".equals(this.type) && n != Math.rint(n)) {
                     throw new IllegalArgumentException(this.id);
                  }
                  break;
               case "enum":
               case "string":
               case "target":
                  if (!input.isJsonPrimitive() || !input.getAsJsonPrimitive().isString() || input.getAsString().length() > 128) {
                     throw new IllegalArgumentException(this.id);
                  }

                  if ("enum".equals(this.type) && (this.values == null || !Arrays.asList(this.values).contains(input.getAsString()))) {
                     throw new IllegalArgumentException(this.id);
                  }
                  break;
               case "range":
                  if (!input.isJsonArray() || input.getAsJsonArray().size() != 2) {
                     throw new IllegalArgumentException(this.id);
                  }

                  for (JsonElement component : input.getAsJsonArray()) {
                     if (!component.isJsonPrimitive() || !component.getAsJsonPrimitive().isNumber()) {
                        throw new IllegalArgumentException(this.id);
                     }
                  }

                  double a = input.getAsJsonArray().get(0).getAsDouble();
                  double b = input.getAsJsonArray().get(1).getAsDouble();
                  if (!Double.isFinite(a) || !Double.isFinite(b) || a < this.min || b > this.max || a > b) {
                     throw new IllegalArgumentException(this.id);
                  }
                  break;
               case "vector":
                  if (!input.isJsonArray() || input.getAsJsonArray().size() != 3) {
                     throw new IllegalArgumentException(this.id);
                  }

                  for (JsonElement componentx : input.getAsJsonArray()) {
                     if (!componentx.isJsonPrimitive() || !componentx.getAsJsonPrimitive().isNumber()) {
                        throw new IllegalArgumentException(this.id);
                     }

                     double component = componentx.getAsDouble();
                     if (!Double.isFinite(component) || component < this.min || component > this.max) {
                        throw new IllegalArgumentException(this.id);
                     }
                  }
                  break;
               default:
                  throw new IllegalArgumentException("unknown parameter type");
            }

            return input.deepCopy();
         } else {
            throw new IllegalArgumentException("invalid parameter bounds: " + this.id);
         }
      } else {
         throw new IllegalArgumentException(this.id);
      }
   }
}
