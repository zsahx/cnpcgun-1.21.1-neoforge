package bincnpc.gun.client;

import bincnpc.gun.client.gui.GuiWeapons;
import bincnpc.gun.common.network.GunNetwork;
import net.minecraft.client.Minecraft;

public final class ClientPackets {
   private ClientPackets() {
   }

   public static void receive(GunNetwork.Response packet) {
      if (Minecraft.getInstance().screen instanceof GuiWeapons gui) {
         gui.receive(packet);
      }
   }
}
