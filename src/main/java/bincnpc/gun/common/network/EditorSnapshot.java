package bincnpc.gun.common.network;

import bincnpc.gun.common.adapter.AdapterRegistry;
import bincnpc.gun.common.adapter.WeaponAdapter;
import bincnpc.gun.common.api.SafeJson;
import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.common.combat.CombatService;
import bincnpc.gun.common.config.ConfigSchema;
import bincnpc.gun.common.config.Parameter;
import bincnpc.gun.common.persistence.PresetLibrary;
import bincnpc.gun.gun.adapter.GunAdapter;
import bincnpc.gun.slashblade.action.ActionRegistry;
import bincnpc.gun.slashblade.combo.Conditions;
import bincnpc.gun.slashblade.strategy.Presets;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.world.item.ItemStack;

public final class EditorSnapshot {
   private EditorSnapshot() {
   }

   public static JsonObject create(CombatContext ctx) {
      JsonObject out = new JsonObject();
      JsonObject values = new JsonObject();

      for (Parameter p : ConfigSchema.all()) {
         values.add(p.id, ctx.config.get(p.id));
      }

      out.add("values", values);
      out.add("schema", SafeJson.GSON.toJsonTree(ConfigSchema.all()));
      out.add("weapon", SafeJson.GSON.toJsonTree(CombatService.weaponInfo(ctx.weapon, null)));
      out.add("capabilities", SafeJson.GSON.toJsonTree(CombatService.capabilities(ctx)));
      out.add("editableWeapons", EditorWeapons.snapshot(ctx));
      out.add("ammo", SafeJson.GSON.toJsonTree(ctx.data.gun.ammo(ctx)));
      out.addProperty("activeSlot", ctx.data.armory.active);
      out.addProperty("state", ctx.runtime.combatState);
      out.addProperty("command", ctx.runtime.command);
      out.add("adapterStatus", AdapterRegistry.status());
      out.add("bladeState", ctx.data.blade.state(ctx));
      out.add("comboState", ctx.data.blade.comboState(ctx));
      JsonObject actionConfig = ctx.config.actions();
      JsonArray actions = new JsonArray();

      for (ActionRegistry.Definition def : ActionRegistry.INSTANCE.all()) {
         if (EditorWeapons.adapter(ctx, def.adapterId)) {
            actionConfig.add(def.info.id, Presets.action(ctx, def));
            JsonObject action = SafeJson.GSON.toJsonTree(def.info).getAsJsonObject();
            action.addProperty("backend", def.backend);
            EditorWeapons.presentation(ctx, def).entrySet().forEach(entry -> action.add((String)entry.getKey(), (JsonElement)entry.getValue()));
            action.add("tags", SafeJson.GSON.toJsonTree(def.tags));
            actions.add(action);
         }
      }

      out.add("actions", actions);
      out.add("actionConfig", actionConfig);
      JsonArray reserves = new JsonArray();

      for (int slot = 0; slot < 6; slot++) {
         ItemStack weapon = ctx.data.armory.get(ctx.npc, slot);
         WeaponAdapter adapter = AdapterRegistry.find(weapon);
         JsonObject ammo = new JsonObject();
         ammo.addProperty("slot", slot);
         if (adapter instanceof GunAdapter gun) {
            ammo.addProperty("reserve", ctx.data.ammo.independent(ctx, gun, weapon));
            ammo.addProperty("loaded", AdapterRegistry.query(gun, () -> gun.loadedRounds(weapon), -1));
         }

         reserves.add(ammo);
      }

      out.add("slotAmmo", reserves);
      JsonArray throwRemaining = new JsonArray();

      for (int slot = 0; slot < 3; slot++) {
         throwRemaining.add(ctx.data.throwItems.remaining(ctx, slot));
      }

      out.add("throwRemaining", throwRemaining);
      JsonArray graphs = new JsonArray();

      for (Entry<String, JsonElement> entry : ctx.config.graphs().entrySet()) {
         JsonObject item = new JsonObject();
         item.addProperty("id", entry.getKey());
         JsonObject graph = entry.getValue().isJsonObject() ? entry.getValue().getAsJsonObject() : new JsonObject();
         item.addProperty("nodeCount", graph.has("nodes") && graph.get("nodes").isJsonArray() ? graph.getAsJsonArray("nodes").size() : 0);
         graphs.add(item);
      }

      out.add("graphs", graphs);
      out.addProperty("selectedGraph", ctx.config.selectedGraph());
      JsonObject selected = ctx.config.graph(ctx.config.selectedGraph());
      out.add("graph", (JsonElement)(selected == null ? JsonNull.INSTANCE : selected));
      Set<String> presets = new LinkedHashSet<>(Arrays.asList(Presets.BUILTINS));
      presets.addAll(Arrays.asList(PresetLibrary.get(ctx).ids()));
      presets.addAll(ctx.config.snapshot().getAsJsonObject("presetCopies").keySet());
      out.add("presets", SafeJson.GSON.toJsonTree(presets));
      out.add("conditions", SafeJson.GSON.toJsonTree(Conditions.ids()));
      return out;
   }
}
