package xyz.withmilo.welcomer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;

public class WelcomerClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, timestamp) -> {
            String msg = message.getString();
            System.out.println("DEBUG: Received chat message: [" + msg + "]");
            if (msg.contains("[+]")) { // More flexible matching
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.networkHandler != null) {
                    client.player.networkHandler.sendChatMessage("hi");
                }
            }
        });
    }
}