package bincnpc.gun.slashblade.action;

import bincnpc.gun.common.api.Views;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class ActionRegistry {
   public static final ActionRegistry INSTANCE = new ActionRegistry();
   private final Map<String, ActionRegistry.Definition> actions = new LinkedHashMap<>();

   public void register(ActionRegistry.Definition action) {
      if (!action.info.id.matches("slashblade:[a-z0-9_.-]+/[a-z0-9_./-]+")) {
         throw new IllegalArgumentException("invalid action ID");
      } else if (this.actions.putIfAbsent(action.info.id, action) != null) {
         throw new IllegalArgumentException("duplicate action ID");
      }
   }

   public ActionRegistry.Definition get(String id) {
      return this.actions.get(id);
   }

   public Map<String, ActionRegistry.Definition> snapshot() {
      return Collections.unmodifiableMap(new LinkedHashMap<>(this.actions));
   }

   public ActionRegistry.Definition[] all() {
      return this.actions.values().toArray(ActionRegistry.Definition[]::new);
   }

   public void removeAdapter(String id) {
      this.actions.values().removeIf(d -> d.adapterId.equals(id));
   }

   public void clear() {
      this.actions.clear();
   }

   public static final class Definition {
      public final Views.ActionInfo info;
      public final String adapterId;
      public final String backend;
      public final Set<String> tags;

      public Definition(Views.ActionInfo info, String adapterId, String backend, String... tags) {
         this.info = info;
         this.adapterId = adapterId;
         this.backend = backend;
         this.tags = Set.of(tags);
      }
   }
}
