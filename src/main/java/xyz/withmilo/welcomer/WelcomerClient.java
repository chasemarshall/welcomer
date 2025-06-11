package xyz.withmilo.welcomer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.concurrent.CompletableFuture;

public class WelcomerClient implements ClientModInitializer {
    // Suggestions for /welcomer <key>
    private static final SuggestionProvider<ServerCommandSource> WELCOMER_KEY_SUGGESTION = (context, builder) -> {
        builder.suggest("trigger");
        builder.suggest("response");
        return CompletableFuture.completedFuture(builder.build());
    };

    // Suggestions for /welcomer trigger <action>
    private static final SuggestionProvider<ServerCommandSource> WELCOMER_TRIGGER_ACTION_SUGGESTION = (context, builder) -> {
        builder.suggest("add");
        builder.suggest("remove");
        builder.suggest("delay");
        builder.suggest("list");
        return CompletableFuture.completedFuture(builder.build());
    };

    @Override
    public void onInitializeClient() {
        // Chat listener for auto-welcome functionality (supports per-trigger delay)
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, timestamp) -> {
            String msg = message.getString();
            WelcomerConfig config = WelcomerConfig.INSTANCE;
            for (Trigger trig : config.triggers) {
                if (msg.contains(trig.text)) {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.player != null && client.player.networkHandler != null) {
                        final int delayCopy = trig.delayMs;
                        final String responseCopy = config.response;
                        if (delayCopy > 0) {
                            new Thread(() -> {
                                try {
                                    Thread.sleep(delayCopy);
                                } catch (InterruptedException ignored) {}
                                client.execute(() -> client.player.networkHandler.sendChatMessage(responseCopy));
                            }).start();
                        } else {
                            client.player.networkHandler.sendChatMessage(responseCopy);
                        }
                    }
                    break; // Only trigger on the first match
                }
            }
        });

        // Command registration
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerWelcomerCommand(dispatcher);
        });
    }

    private static void registerWelcomerCommand(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("welcomer")
                .then(CommandManager.argument("key", StringArgumentType.word())
                        .suggests(WELCOMER_KEY_SUGGESTION)
                        // ---- trigger subcommands ----
                        .then(CommandManager.argument("action", StringArgumentType.word())
                                .suggests(WELCOMER_TRIGGER_ACTION_SUGGESTION)
                                // /welcomer trigger add <trigger> [delayMs]
                                .then(CommandManager.argument("value", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String action = StringArgumentType.getString(ctx, "action").toLowerCase();
                                            String value = StringArgumentType.getString(ctx, "value");
                                            WelcomerConfig config = WelcomerConfig.INSTANCE;
                                            switch (action) {
                                                case "add": {
                                                    String[] parts = value.split(" ", 2);
                                                    final String trigText = parts[0];
                                                    int d = 0;
                                                    if (parts.length == 2) {
                                                        try {
                                                            d = Integer.parseInt(parts[1]);
                                                        } catch (NumberFormatException e) {
                                                            ctx.getSource().sendError(Text.literal("✗ Delay must be integer (ms)!").formatted(Formatting.RED));
                                                            return 0;
                                                        }
                                                    }
                                                    final int delay = d;
                                                    if (config.findTrigger(trigText) == null) {
                                                        config.triggers.add(new Trigger(trigText, delay));
                                                        ctx.getSource().sendFeedback(
                                                                () -> Text.literal("✔ Added trigger: ").formatted(Formatting.GREEN)
                                                                        .append(Text.literal(trigText + " (" + delay + " ms)").formatted(Formatting.AQUA)),
                                                                false
                                                        );
                                                    } else {
                                                        ctx.getSource().sendError(
                                                                Text.literal("✗ Trigger already exists.").formatted(Formatting.RED)
                                                        );
                                                    }
                                                    break;
                                                }
                                                case "remove": {
                                                    Trigger t = config.findTrigger(value);
                                                    if (t != null) {
                                                        config.triggers.remove(t);
                                                        ctx.getSource().sendFeedback(
                                                                () -> Text.literal("✔ Removed trigger: ").formatted(Formatting.GREEN)
                                                                        .append(Text.literal(value).formatted(Formatting.AQUA)),
                                                                false
                                                        );
                                                    } else {
                                                        ctx.getSource().sendError(
                                                                Text.literal("✗ Trigger not found.").formatted(Formatting.RED)
                                                        );
                                                    }
                                                    break;
                                                }
                                                case "delay": {
                                                    String[] parts = value.split(" ", 2);
                                                    if (parts.length != 2) {
                                                        ctx.getSource().sendError(Text.literal("✗ Usage: /welcomer trigger delay <trigger> <delayMs>").formatted(Formatting.RED));
                                                        return 0;
                                                    }
                                                    String trigText = parts[0];
                                                    int delay;
                                                    try {
                                                        delay = Integer.parseInt(parts[1]);
                                                    } catch (NumberFormatException e) {
                                                        ctx.getSource().sendError(Text.literal("✗ Delay must be integer (ms)!").formatted(Formatting.RED));
                                                        return 0;
                                                    }
                                                    Trigger t = config.findTrigger(trigText);
                                                    if (t != null) {
                                                        t.delayMs = delay;
                                                        ctx.getSource().sendFeedback(
                                                                () -> Text.literal("✔ Set delay for trigger ").formatted(Formatting.GREEN)
                                                                        .append(Text.literal(trigText).formatted(Formatting.AQUA))
                                                                        .append(Text.literal(": " + delay + " ms").formatted(Formatting.AQUA)),
                                                                false
                                                        );
                                                    } else {
                                                        ctx.getSource().sendError(
                                                                Text.literal("✗ Trigger not found.").formatted(Formatting.RED)
                                                        );
                                                    }
                                                    break;
                                                }
                                                case "list": {
                                                    if (config.triggers.isEmpty()) {
                                                        ctx.getSource().sendFeedback(
                                                                () -> Text.literal("ℹ No triggers set.").formatted(Formatting.GRAY),
                                                                false
                                                        );
                                                    } else {
                                                        StringBuilder sb = new StringBuilder();
                                                        for (Trigger t : config.triggers) {
                                                            if (sb.length() > 0) sb.append(", ");
                                                            sb.append(t.text).append(" (").append(t.delayMs).append(" ms)");
                                                        }
                                                        ctx.getSource().sendFeedback(
                                                                () -> Text.literal("✔ Triggers: ").formatted(Formatting.GREEN)
                                                                        .append(Text.literal(sb.toString()).formatted(Formatting.AQUA)),
                                                                false
                                                        );
                                                    }
                                                    break;
                                                }
                                                default:
                                                    ctx.getSource().sendError(
                                                            Text.literal("✗ Unknown action for trigger. Use add, remove, delay, or list.").formatted(Formatting.RED)
                                                    );
                                                    return 0;
                                            }
                                            return 1;
                                        })
                                )
                        )
                        // ---- response command ----
                        .then(CommandManager.argument("value", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String key = StringArgumentType.getString(ctx, "key").toLowerCase();
                                    String value = StringArgumentType.getString(ctx, "value");
                                    WelcomerConfig config = WelcomerConfig.INSTANCE;
                                    if ("response".equals(key)) {
                                        config.response = value;
                                        ctx.getSource().sendFeedback(
                                                () -> Text.literal("✔ Response set to: ").formatted(Formatting.GREEN)
                                                        .append(Text.literal(value).formatted(Formatting.AQUA)),
                                                false
                                        );
                                    } else {
                                        ctx.getSource().sendError(
                                                Text.literal("✗ Unknown welcomer key. Use trigger or response.").formatted(Formatting.RED)
                                        );
                                    }
                                    return 1;
                                })
                        )
                )
        );
    }
}