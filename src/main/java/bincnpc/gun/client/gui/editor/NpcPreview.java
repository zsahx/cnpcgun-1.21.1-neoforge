package bincnpc.gun.client.gui.editor;

import bincnpc.gun.client.animation.BladePreviewEffects;
import bincnpc.gun.client.animation.NpcAnimations;
import bincnpc.gun.client.gui.GuiSupport;
import bincnpc.gun.common.api.SafeJson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.entity.EntityNPCInterface;

public final class NpcPreview extends AbstractWidget {
   private final EntityNPCInterface source;
   private final Deque<NpcPreview.Step> timeline = new ArrayDeque<>();
   private EntityNPCInterface model;
   private ItemStack weapon = ItemStack.EMPTY;
   private int left;
   private String action = "";
   private boolean unavailable;

   public NpcPreview(EntityNPCInterface source, EditorLayout.Box box, ItemStack weapon) {
      super(box.x(), box.y(), box.width(), box.height(), Component.translatable("cnpcgun.editor.npcPreview"));
      this.source = source;
      this.weapon = weapon == null ? ItemStack.EMPTY : weapon.copy();
      this.unavailable = source == null;
   }

   public void bounds(EditorLayout.Box box) {
      this.setX(box.x());
      this.setY(box.y());
      this.setWidth(box.width());
      this.height = box.height();
   }

   private EntityNPCInterface model() {
      if (this.model == null && !this.unavailable) {
         try {
            if (!(this.source.getType().create(this.source.level()) instanceof EntityNPCInterface copy)) {
               this.unavailable = true;
               return null;
            }

            copy.readSpawnData(this.source.writeSpawnData().copy());
            copy.setUUID(UUID.randomUUID());
            copy.setId(-1);
            copy.setNoAi(true);
            copy.setPos(this.source.getX(), this.source.getY(), this.source.getZ());
            copy.display.setShowName(1);
            copy.display.setName("");
            copy.textureLocation = this.source.textureLocation;
            copy.textureGlowLocation = this.source.textureGlowLocation;
            copy.textureCloakLocation = this.source.textureCloakLocation;
            copy.setHealth(Math.max(1.0F, copy.getMaxHealth()));
            copy.inventory.setRightHand(NpcAPI.Instance().getIItemStack(this.weapon.copy()));
            this.model = copy;
         } catch (LinkageError | RuntimeException var3) {
            this.unavailable = true;
         }

         return this.model;
      } else {
         return this.model;
      }
   }

   public void dummy() {
      EntityNPCInterface copy = this.model();
      if (copy != null) {
         for (EquipmentSlot slot : EquipmentSlot.values()) {
            copy.setItemSlot(slot, ItemStack.EMPTY);
         }

         copy.display.setName("");
      }
   }

   public boolean playing() {
      return this.left > 0 || !this.timeline.isEmpty();
   }

   public void stop() {
      this.timeline.clear();
      this.left = 0;
      this.action = "";
      if (this.model != null) {
         NpcAnimations.clearPreview(this.model);
         if (ModList.get().isLoaded("slashblade")) {
            try {
               BladePreviewEffects.clear(this.model);
               BladePreview.clear(this.model.getMainHandItem(), this.model.level().getGameTime());
            } catch (LinkageError | RuntimeException var2) {
            }
         }
      }
   }

   public void dispose() {
      this.stop();
      this.model = null;
   }

   public void fire(int duration, boolean aim) {
      EntityNPCInterface copy = this.model();
      if (copy != null) {
         NpcAnimations.preview(copy, "GUN", "FIRE", "preview", duration, aim);
         this.action = "";
      }
   }

   public void hit() {
      EntityNPCInterface copy = this.model();
      if (copy != null) {
         copy.hurtTime = 6;
      }
   }

   public void playAction(JsonObject info, JsonObject parameters) {
      this.stop();
      if (info != null) {
         if (parameters == null) {
            parameters = new JsonObject();
         }

         int ticks = duration(info, parameters);
         int delay = Mth.clamp(SafeJson.integer(parameters, "delay", 0), 0, 100);
         this.timeline.add(new NpcPreview.Step(info.deepCopy(), parameters.deepCopy(), Mth.clamp(ticks + delay, 1, 2400)));
         this.advance();
      }
   }

   public void playGraph(JsonObject graph, Function<String, JsonObject> actions) {
      this.stop();
      Map<String, JsonObject> nodes = new LinkedHashMap<>();
      if (graph != null && actions != null) {
         for (JsonElement raw : SafeJson.arrayField(graph, "nodes")) {
            if (raw.isJsonObject()) {
               JsonObject node = raw.getAsJsonObject();
               String nodeId = SafeJson.string(node, "id", "");
               if (!nodeId.isEmpty()) {
                  nodes.put(nodeId, node);
               }
            }
         }

         String id = SafeJson.string(graph, "entryNode", "end");
         Set<String> seen = new HashSet<>();
         int duration = 0;

         while (!id.equals("end") && seen.size() < 128 && seen.add(id)) {
            JsonObject node = nodes.get(id);
            if (node == null) {
               break;
            }

            JsonObject info = actions.apply(SafeJson.string(node, "action", ""));
            if (info != null && SafeJson.bool(info, "available", false)) {
               JsonObject params = SafeJson.objectField(node, "parameters").deepCopy();
               int ticks = duration(info, params);
               if (duration + ticks > 2400) {
                  break;
               }

               this.timeline.add(new NpcPreview.Step(info.deepCopy(), params, ticks));
               duration += ticks;
               id = SafeJson.string(node, "onSuccess", "end");
            } else {
               id = SafeJson.string(node, "onFailure", "end");
            }
         }

         this.advance();
      }
   }

   private void advance() {
      NpcPreview.Step next = this.timeline.poll();
      if (next == null) {
         this.left = 0;
         this.action = "";
      } else {
         EntityNPCInterface copy = this.model();
         if (copy == null) {
            this.stop();
         } else {
            this.left = next.ticks;
            this.action = GuiSupport.actionName(next.info);
            String animation = SafeJson.string(next.info, "id", "");
            if (ModList.get().isLoaded("slashblade")) {
               try {
                  String stage = BladePreview.pose(copy.getMainHandItem(), next.info, next.parameters, copy.level().getGameTime());
                  if (!stage.isEmpty()) {
                     animation = stage;
                  }

                  BladePreviewEffects.start(copy, next.info, next.parameters, this.left);
               } catch (LinkageError | RuntimeException var5) {
               }
            }

            NpcAnimations.preview(copy, "SLASHBLADE", SafeJson.string(next.info, "type", ""), animation, this.left, false);
         }
      }
   }

   private static int duration(JsonObject info, JsonObject parameters) {
      int ticks = SafeJson.integer(info, "duration", 12);
      String type = SafeJson.string(parameters, "artsType", SafeJson.string(info, "defaultArtsType", "Success"));
      ticks = SafeJson.integer(SafeJson.objectField(info, "previewDurations"), type, ticks);
      ticks = SafeJson.integer(parameters, "duration", ticks);
      if (SafeJson.string(info, "type", "").equals("PHANTOM_SWORD")) {
         ticks = Math.max(24, ticks);
      }

      return Mth.clamp(ticks, 1, 2400);
   }

   public void tick() {
      if (this.model != null && this.model.hurtTime > 0) {
         this.model.hurtTime--;
      }

      if (this.model != null && ModList.get().isLoaded("slashblade")) {
         try {
            BladePreviewEffects.tick(this.model);
         } catch (LinkageError | RuntimeException var2) {
         }
      }

      if (this.left > 0 && --this.left == 0) {
         this.advance();
      }
   }

   private int modelScale(EntityNPCInterface copy) {
      int scale = Math.max(12, Math.min(this.width / 3, (this.height - 40) / 2));
      float npcSize = Math.max(0.5F, copy.getBbHeight() / 1.8F);
      return Math.max(8, (int)(scale / npcSize));
   }

   public EditorLayout.Box hitArea() {
      EntityNPCInterface copy = this.model();
      int scale = copy == null ? Math.max(8, this.width / 3) : this.modelScale(copy);
      int bodyWidth = Math.max(8, Math.min(this.width - 16, (int)Math.ceil(scale * (copy == null ? 0.6F : copy.getBbWidth()))));
      int bodyHeight = Math.max(12, Math.min(this.height - 42, (int)Math.ceil(scale * (copy == null ? 1.8F : copy.getBbHeight()))));
      return new EditorLayout.Box(this.getX() + (this.width - bodyWidth) / 2, this.getY() + this.height - 22 - bodyHeight, bodyWidth, bodyHeight);
   }

   protected void renderWidget(GuiGraphics g, int mx, int my, float partialTick) {
      Font font = Minecraft.getInstance().font;
      EditorStyle.panel(g, new EditorLayout.Box(this.getX(), this.getY(), this.width, this.height), -15657711);
      g.fill(
         this.getX() + 3, this.getY() + this.height - 18, this.getX() + this.width - 3, this.getY() + this.height - 3, -14669792
      );
      EditorStyle.text(g, font, this.getMessage().getString(), this.getX() + 7, this.getY() + 7, this.width - 14, -3353403);
      EntityNPCInterface copy = this.model();
      if (copy == null) {
         EditorStyle.text(
            g,
            font,
            Component.translatable("cnpcgun.editor.previewUnavailable").getString(),
            this.getX() + 6,
            this.getY() + 28,
            this.width - 12,
            -395024
         );
      } else {
         int scale = this.modelScale(copy);
         int x = this.getX() + this.width / 2;
         int y = this.getY() + this.height - 22;
         g.flush();
         g.enableScissor(this.getX() + 2, this.getY() + 19, this.getX() + this.width - 2, this.getY() + this.height - 19);
         InventoryScreen.renderEntityInInventoryFollowsMouse(
            g, x - 30, y - 60, x + 30, y + 20, scale, 0.0F, (float)mx, (float)my, copy
         );
         g.disableScissor();
         g.flush();
         EditorStyle.text(
            g,
            font,
            this.action.isEmpty()
               ? Component.translatable(this.playing() ? "cnpcgun.editor.previewPlaying" : "cnpcgun.editor.previewReady").getString()
               : this.action,
            this.getX() + 6,
            this.getY() + this.height - 13,
            this.width - 12,
            -4798559
         );
      }
   }

   protected void updateWidgetNarration(NarrationElementOutput output) {
      this.defaultButtonNarrationText(output);
   }

   private record Step(JsonObject info, JsonObject parameters, int ticks) {
   }
}
