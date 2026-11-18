package bincnpc.gun.client.animation;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * Preview of SlashBlade's summoned-sword / slash-effect entities inside the NPC preview widget.
 *
 * <p>The 1.20.1 build spawned SlashBlade's entity classes directly to render a preview. Those classes
 * and their registry hooks changed in the 1.21.1 SlashBlade build, so this effect layer is disabled in
 * this port (the NPC still plays its own pose animations through {@link NpcAnimations}).</p>
 */
public final class BladePreviewEffects {
   private BladePreviewEffects() {
   }

   public static void start(EntityNPCInterface npc, JsonObject info, JsonObject parameters, int duration) {
   }

   public static void clear(EntityNPCInterface npc) {
   }

   public static void tick(EntityNPCInterface npc) {
   }

   public static void render(EntityNPCInterface npc, PoseStack pose, MultiBufferSource buffers, int light, float partialTick) {
   }
}
