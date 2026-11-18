package bincnpc.gun.client.gui.editor;

import bincnpc.gun.client.gui.GuiSupport;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public final class GraphCanvas extends AbstractWidget {
   private static final int NODE_W = 128;
   private static final int NODE_H = 54;
   private final Font font;
   private final GraphDraft draft;
   private final Function<String, String> actionName;
   private final Consumer<String> select;
   private final Consumer<String> edit;
   private String selection;
   private String dragging = "";
   private double panX = 12.0;
   private double panY = 12.0;
   private double zoom = 1.0;
   private boolean panning;
   private boolean moved;
   private long lastClick;
   private String lastNode = "";

   public GraphCanvas(
      Font font, EditorLayout.Box box, GraphDraft draft, String initial, Function<String, String> actionName, Consumer<String> select, Consumer<String> edit
   ) {
      super(box.x(), box.y(), box.width(), box.height(), Component.translatable("cnpcgun.gui.comboEditor"));
      this.font = font;
      this.draft = draft;
      this.selection = initial;
      this.actionName = actionName;
      this.select = select;
      this.edit = edit;
      int i = 0;

      for (String id : draft.ids()) {
         draft.positions.putIfAbsent(id, new GraphDraft.Point(i % 3 * 164, i / 3 * 90));
         i++;
      }
   }

   public GraphCanvas.View view() {
      return new GraphCanvas.View(this.panX, this.panY, this.zoom);
   }

   public void view(GraphCanvas.View view) {
      this.panX = view.x;
      this.panY = view.y;
      this.zoom = view.zoom;
   }

   public void arrange() {
      this.draft.positions.clear();
      int columns = Math.max(1, Math.min(4, (this.width - 20) / 164));
      int i = 0;

      for (String id : this.draft.ids()) {
         this.draft.positions.put(id, new GraphDraft.Point(i % columns * 164, i / columns * 90));
         i++;
      }

      this.panX = 12.0;
      this.panY = 12.0;
      this.zoom = 1.0;
   }

   private String hit(double mouseX, double mouseY) {
      double x = (mouseX - this.getX() - this.panX) / this.zoom;
      double y = (mouseY - this.getY() - this.panY) / this.zoom;

      for (String id : this.draft.ids()) {
         GraphDraft.Point p = this.draft.positions.get(id);
         if (x >= p.x() && x < p.x() + 128.0 && y >= p.y() && y < p.y() + 54.0) {
            return id;
         }
      }

      return "";
   }

   protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
      EditorStyle.panel(g, new EditorLayout.Box(this.getX(), this.getY(), this.width, this.height), -1907752);
      g.enableScissor(this.getX() + 1, this.getY() + 1, this.getX() + this.width - 1, this.getY() + this.height - 1);

      for (int x = this.getX() + 8; x < this.getX() + this.width; x += 20) {
         for (int y = this.getY() + 8; y < this.getY() + this.height; y += 20) {
            g.fill(x, y, x + 1, y + 1, -4012102);
         }
      }

      g.pose().pushPose();
      g.pose().translate(this.getX() + this.panX, this.getY() + this.panY, 0.0);
      g.pose().scale((float)this.zoom, (float)this.zoom, 1.0F);
      String[] edges = new String[]{"onSuccess", "onFailure", "onTimeout"};
      int[] colors = new int[]{-11047869, -6074553, -5733307};

      for (JsonElement raw : this.draft.nodes()) {
         JsonObject node = raw.getAsJsonObject();
         GraphDraft.Point from = this.draft.positions.get(node.get("id").getAsString());

         for (int channel = 0; channel < edges.length; channel++) {
            String target = node.get(edges[channel]).getAsString();
            GraphDraft.Point to = this.draft.positions.get(target);
            if (to != null) {
               int sx = (int)from.x() + 128;
               int sy = (int)from.y() + 19 + channel * 9;
               int tx = (int)to.x();
               int ty = (int)to.y() + 22 + channel * 5;
               int turn = sx + 12 + channel * 4;
               g.hLine(sx, turn, sy, colors[channel]);
               g.vLine(turn, Math.min(sy, ty), Math.max(sy, ty), colors[channel]);
               g.hLine(Math.min(turn, tx), Math.max(turn, tx), ty, colors[channel]);
               g.fill(tx - 3, ty - 1, tx, ty + 2, colors[channel]);
            }
         }
      }

      for (JsonElement raw : this.draft.nodes()) {
         JsonObject node = raw.getAsJsonObject();
         String id = node.get("id").getAsString();
         GraphDraft.Point p = this.draft.positions.get(id);
         int x = (int)Math.round(p.x());
         int y = (int)Math.round(p.y());
         boolean chosen = id.equals(this.selection);
         EditorStyle.panel(g, new EditorLayout.Box(x, y, 128, 54), chosen ? -2301487 : -1119006);
         if (chosen) {
            g.renderOutline(x, y, 128, 54, -11047869);
         }

         boolean entry = id.equals(this.draft.graph.get("entryNode").getAsString());
         EditorStyle.text(g, this.font, id, x + 7, y + 6, 128 - (entry ? 48 : 12), -9932693);
         if (entry) {
            EditorStyle.text(g, this.font, GuiSupport.tr("cnpcgun.editor.entry"), x + 128 - 38, y + 6, 32, -11047869);
         }

         EditorStyle.text(g, this.font, this.actionName.apply(node.get("action").getAsString()), x + 7, y + 21, 112, -14077902);
         int count = node.getAsJsonArray("conditions").size();
         EditorStyle.text(g, this.font, GuiSupport.tr("cnpcgun.editor.nodeSummary", count, node.get("timeout").getAsInt()), x + 7, y + 37, 112, -9932693);

         for (int i = 0; i < 3; i++) {
            g.fill(x + 128 - 2, y + 18 + i * 9, x + 128 + 2, y + 22 + i * 9, colors[i]);
         }
      }

      g.pose().popPose();
      g.disableScissor();
      if (this.draft.nodes().isEmpty()) {
         EditorStyle.text(g, this.font, GuiSupport.tr("cnpcgun.editor.emptyGraph"), this.getX() + 14, this.getY() + 18, this.width - 28, -14077902);
         EditorStyle.text(
            g, this.font, GuiSupport.tr("cnpcgun.editor.emptyGraphHint"), this.getX() + 14, this.getY() + 34, this.width - 28, -9932693
         );
      }

      if (this.height >= 72) {
         int xx = this.getX() + 8;
         int yx = this.getY() + this.height - 14;
         g.fill(this.getX() + 3, yx - 4, this.getX() + this.width - 3, this.getY() + this.height - 3, -1119006);
         String[] labels = new String[]{"success", "failure", "timeout"};

         for (int i = 0; i < labels.length; i++) {
            String label = GuiSupport.tr("cnpcgun.editor." + labels[i]);
            g.fill(xx, yx + 2, xx + 4, yx + 6, colors[i]);
            EditorStyle.text(g, this.font, label, xx + 8, yx, this.width - (xx - this.getX()) - 12, -9932693);
            xx += this.font.width(label) + 18;
         }

         String scale = Math.round(this.zoom * 100.0) + "%";
         if (this.width - (xx - this.getX()) >= this.font.width(scale) + 14) {
            EditorStyle.text(g, this.font, scale, this.getX() + this.width - this.font.width(scale) - 8, yx, this.font.width(scale), -9932693);
         }
      }
   }

   public boolean mouseClicked(double x, double y, int button) {
      if (!new EditorLayout.Box(this.getX(), this.getY(), this.width, this.height).contains(x, y)) {
         return false;
      } else if (button == 2) {
         this.panning = true;
         this.dragging = "";
         this.moved = false;
         return true;
      } else if (button != 0 && button != 1) {
         return false;
      } else {
         String id = this.hit(x, y);
         this.selection = id;
         this.select.accept(id);
         if (button == 1 && !id.isEmpty()) {
            this.edit.accept(id);
            return true;
         } else {
            this.dragging = id;
            this.panning = id.isEmpty();
            this.moved = false;
            long now = System.currentTimeMillis();
            if (!id.isEmpty() && id.equals(this.lastNode) && now - this.lastClick < 280L) {
               this.dragging = "";
               this.edit.accept(id);
            }

            this.lastClick = now;
            this.lastNode = id;
            return true;
         }
      }
   }

   public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
      if (this.panning) {
         this.panX += dx;
         this.panY += dy;
         this.moved = true;
         return true;
      } else if (!this.dragging.isEmpty() && this.active) {
         GraphDraft.Point p = this.draft.positions.get(this.dragging);
         this.draft
            .positions
            .put(
               this.dragging,
               new GraphDraft.Point(
                  Math.max(-10000.0, Math.min(10000.0, p.x() + dx / this.zoom)), Math.max(-10000.0, Math.min(10000.0, p.y() + dy / this.zoom))
               )
            );
         this.moved = true;
         return true;
      } else {
         return false;
      }
   }

   public boolean mouseReleased(double x, double y, int button) {
      this.panning = false;
      this.dragging = "";
      if (this.moved) {
         this.lastNode = "";
      }

      return true;
   }

   public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
      double delta = scrollY;
      if (!new EditorLayout.Box(this.getX(), this.getY(), this.width, this.height).contains(x, y)) {
         return false;
      } else {
         double before = this.zoom;
         this.zoom = Math.max(0.5, Math.min(1.75, this.zoom + delta * 0.1));
         this.panX = x - this.getX() - (x - this.getX() - this.panX) * this.zoom / before;
         this.panY = y - this.getY() - (y - this.getY() - this.panY) * this.zoom / before;
         return true;
      }
   }

   protected void updateWidgetNarration(NarrationElementOutput output) {
      this.defaultButtonNarrationText(output);
   }

   public record View(double x, double y, double zoom) {
   }
}
