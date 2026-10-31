package com.taikin.chataisum.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;

public final class ChatAISumCommand {
	private ChatAISumCommand() {
	}

	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext context) {
		dispatcher.register(build("chataisum"));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> build(String name) {
		return ClientCommands.literal(name)
				.executes(ctx -> status(ctx.getSource()))
				.then(ClientCommands.literal("status").executes(ctx -> status(ctx.getSource())))
				.then(ClientCommands.literal("on").executes(ctx -> setEnabled(ctx.getSource(), true)))
				.then(ClientCommands.literal("off").executes(ctx -> setEnabled(ctx.getSource(), false)))
				.then(ClientCommands.literal("toggle")
						.executes(ctx -> setEnabled(ctx.getSource(), !ChatAISumConfig.get().enabled)))
				.then(ClientCommands.literal("reload").executes(ctx -> {
					ChatAISumConfig.reload();
					ctx.getSource().sendFeedback(Component.literal("[ChatAISum] 設定を再読み込みしました。"));
					return 1;
				}))
				.then(ClientCommands.literal("backend")
						.executes(ctx -> {
							ctx.getSource().sendFeedback(Component.literal("[ChatAISum] backend = "
									+ ChatAISumConfig.get().backend.id()));
							return 1;
						})
						.then(ClientCommands.argument("name", StringArgumentType.word()).executes(ctx -> {
							Backend backend = Backend.parse(StringArgumentType.getString(ctx, "name"));
							if (backend == null) {
								ctx.getSource().sendFeedback(Component.literal(
										"[ChatAISum] backend は openai / openrouter / opencode / compatible / gemini のいずれかです。"));
								return 0;
							}
							ChatAISumConfig config = ChatAISumConfig.get();
							config.backend = backend;
							config.save();
							ctx.getSource().sendFeedback(Component.literal("[ChatAISum] backend = " + backend.id()
									+ " model=" + displayModel(config.activeModel())));
							return 1;
						})))
				.then(ClientCommands.literal("url")
						.then(ClientCommands.argument("url", StringArgumentType.word()).executes(ctx -> {
							ChatAISumConfig config = ChatAISumConfig.get();
							config.setActiveUrl(StringArgumentType.getString(ctx, "url"));
							config.save();
							ctx.getSource().sendFeedback(Component.literal("[ChatAISum] "
									+ config.backend.id() + " url = " + config.activeUrl()));
							return 1;
						})))
				.then(ClientCommands.literal("model")
						.then(ClientCommands.argument("model", StringArgumentType.word()).executes(ctx -> {
							ChatAISumConfig config = ChatAISumConfig.get();
							config.setActiveModel(StringArgumentType.getString(ctx, "model"));
							config.save();
							ctx.getSource().sendFeedback(Component.literal("[ChatAISum] "
									+ config.backend.id() + " model = " + displayModel(config.activeModel())));
							return 1;
						})))
				.then(ClientCommands.literal("key")
						.then(ClientCommands.argument("key", StringArgumentType.word()).executes(ctx -> {
							ChatAISumConfig config = ChatAISumConfig.get();
							config.setActiveKey(StringArgumentType.getString(ctx, "key"));
							config.save();
							ctx.getSource().sendFeedback(Component.literal("[ChatAISum] "
									+ config.backend.id() + " のキーを更新しました。"));
							return 1;
						})))
				.then(ClientCommands.literal("test")
						.executes(ctx -> {
							ChatAISumService.testConnection(ctx.getSource());
							return 1;
						}))
				.then(ClientCommands.literal("models")
						.executes(ctx -> models(ctx.getSource(), ""))
						.then(ClientCommands.argument("query", StringArgumentType.greedyString())
								.executes(ctx -> models(ctx.getSource(),
										StringArgumentType.getString(ctx, "query")))))
				.then(ClientCommands.literal("prompt")
						.executes(ctx -> promptStatus(ctx.getSource()))
						.then(ClientCommands.literal("reset").executes(ctx -> {
							ChatAISumConfig config = ChatAISumConfig.get();
							config.systemPrompt = ChatPrompt.DEFAULT_SYSTEM;
							config.userPrompt = ChatPrompt.DEFAULT_USER;
							config.save();
							ctx.getSource().sendFeedback(Component.literal(
									"[ChatAISum] プロンプトを既定に戻しました。"));
							return 1;
						}))
						.then(ClientCommands.literal("system")
								.then(ClientCommands.argument("text", StringArgumentType.greedyString())
										.executes(ctx -> {
											ChatAISumConfig config = ChatAISumConfig.get();
											config.systemPrompt = StringArgumentType.getString(ctx, "text");
											config.save();
											ctx.getSource().sendFeedback(Component.literal(
													"[ChatAISum] システムプロンプトを更新しました。"));
											return 1;
										})))
						.then(ClientCommands.literal("user")
								.then(ClientCommands.argument("text", StringArgumentType.greedyString())
										.executes(ctx -> {
											ChatAISumConfig config = ChatAISumConfig.get();
											config.userPrompt = StringArgumentType.getString(ctx, "text");
											config.save();
											ctx.getSource().sendFeedback(Component.literal(
													"[ChatAISum] ユーザープロンプトを更新しました。"));
											return 1;
										}))))
				.then(ClientCommands.literal("language")
						.then(ClientCommands.argument("language", StringArgumentType.greedyString()).executes(ctx -> {
							ChatAISumConfig config = ChatAISumConfig.get();
							config.language = StringArgumentType.getString(ctx, "language");
							config.save();
							ctx.getSource().sendFeedback(Component.literal("[ChatAISum] language = " + config.language));
							return 1;
						})))
				.then(ClientCommands.literal("instruction")
						.then(ClientCommands.argument("instruction", StringArgumentType.greedyString()).executes(ctx -> {
							ChatAISumConfig config = ChatAISumConfig.get();
							config.instruction = StringArgumentType.getString(ctx, "instruction");
							config.save();
							ctx.getSource().sendFeedback(Component.literal("[ChatAISum] instruction = " + config.instruction));
							return 1;
						})))
				.then(ClientCommands.literal("system")
						.then(ClientCommands.argument("value", BoolArgumentType.bool()).executes(ctx -> {
							ChatAISumConfig config = ChatAISumConfig.get();
							config.includeSystem = BoolArgumentType.getBool(ctx, "value");
							config.save();
							ctx.getSource().sendFeedback(Component.literal("[ChatAISum] include system messages = "
									+ config.includeSystem));
							return 1;
						})))
				.then(ClientCommands.literal("timeout")
						.then(ClientCommands.argument("milliseconds", IntegerArgumentType.integer(5000, 600000))
								.executes(ctx -> {
									ChatAISumConfig config = ChatAISumConfig.get();
									config.timeoutMs = IntegerArgumentType.getInteger(ctx, "milliseconds");
									config.save();
									ctx.getSource().sendFeedback(Component.literal("[ChatAISum] timeout = "
											+ config.timeoutMs + " ms"));
									return 1;
								})))
				.then(ClientCommands.literal("maxlines")
						.then(ClientCommands.argument("lines", IntegerArgumentType.integer(1, 64))
								.executes(ctx -> {
									ChatAISumConfig config = ChatAISumConfig.get();
									config.maxLines = IntegerArgumentType.getInteger(ctx, "lines");
									config.save();
									ctx.getSource().sendFeedback(Component.literal("[ChatAISum] max lines = "
											+ config.maxLines));
									return 1;
								})))
				.then(ClientCommands.literal("maxtokens")
						.then(ClientCommands.argument("tokens", IntegerArgumentType.integer(100, 16000))
								.executes(ctx -> {
									ChatAISumConfig config = ChatAISumConfig.get();
									config.maxOutputTokens = IntegerArgumentType.getInteger(ctx, "tokens");
									config.save();
									ctx.getSource().sendFeedback(Component.literal("[ChatAISum] max output tokens = "
											+ config.maxOutputTokens));
									return 1;
								})))
				.then(ClientCommands.argument("minutes", IntegerArgumentType.integer(1, 1440))
						.executes(ctx -> summarize(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "minutes"), ""))
						.then(ClientCommands.argument("focus", StringArgumentType.greedyString())
								.executes(ctx -> summarize(ctx.getSource(),
										IntegerArgumentType.getInteger(ctx, "minutes"),
										StringArgumentType.getString(ctx, "focus")))));
	}

	private static int promptStatus(FabricClientCommandSource source) {
		ChatAISumConfig config = ChatAISumConfig.get();
		boolean systemDefault = config.systemPrompt == null || config.systemPrompt.isBlank()
				|| ChatPrompt.DEFAULT_SYSTEM.equals(config.systemPrompt);
		boolean userDefault = config.userPrompt == null || config.userPrompt.isBlank()
				|| ChatPrompt.DEFAULT_USER.equals(config.userPrompt);
		source.sendFeedback(Component.literal("[ChatAISum] system prompt: "
				+ (systemDefault ? "default" : config.systemPrompt.length() + " chars")));
		source.sendFeedback(Component.literal("[ChatAISum] user prompt: "
				+ (userDefault ? "default" : config.userPrompt.length() + " chars")));
		source.sendFeedback(Component.literal(
				"[ChatAISum] 編集は Mod Menu > プロンプト、または /chataisum prompt system|user <text>"));
		return 1;
	}

	private static int models(FabricClientCommandSource source, String query) {
		Backend backend = ChatAISumConfig.get().backend;
		source.sendFeedback(Component.literal("[ChatAISum] " + backend.id() + " のモデル一覧を取得中..."));
		ModelCatalog.refreshAsync(backend, list -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return;
			}
			client.execute(() -> {
				java.util.List<String> results = ModelCatalog.search(backend, query);
				if (results.isEmpty()) {
					chat("[ChatAISum] 候補がありません（キー未設定、通信失敗、またはクエリ不一致）。");
					return;
				}
				String suffix = query.isBlank() ? "" : " (" + query.strip() + ")";
				chat("[ChatAISum] " + backend.id() + " モデル " + results.size() + "件" + suffix + ":");
				int limit = Math.min(results.size(), 30);
				for (int i = 0; i < limit; i++) {
					chat("  " + results.get(i));
				}
				if (results.size() > limit) {
					chat("[ChatAISum] ...ほか " + (results.size() - limit) + "件");
				}
			});
		});
		return 1;
	}

	private static void chat(String text) {
		Minecraft client = Minecraft.getInstance();
		if (client != null && client.gui != null && client.gui.hud != null) {
			client.gui.hud.getChat().addClientSystemMessage(Component.literal(text));
		}
	}

	private static int summarize(FabricClientCommandSource source, int minutes, String focus) {
		ChatAISumService.summarize(minutes, focus, source);
		return 1;
	}

	private static int setEnabled(FabricClientCommandSource source, boolean enabled) {
		ChatAISumConfig config = ChatAISumConfig.get();
		config.enabled = enabled;
		config.save();
		source.sendFeedback(Component.literal("[ChatAISum] " + (enabled ? "有効" : "無効")));
		return 1;
	}

	private static String displayModel(String model) {
		return model == null || model.isBlank() ? "(auto)" : model;
	}

	private static int status(FabricClientCommandSource source) {
		ChatAISumConfig config = ChatAISumConfig.get();
		Component[] lines = {
				Component.literal("[ChatAISum] enabled=" + config.enabled
						+ " backend=" + config.backend.id()
						+ " source=latest.log"
						+ " system=" + config.includeSystem),
				Component.literal("[ChatAISum] url=" + config.activeUrl()
						+ " model=" + displayModel(config.activeModel())
						+ " key=" + (config.backend == Backend.COMPATIBLE
								? (config.resolveCompatibleKey().isBlank() ? "optional" : "set")
								: (config.activeKeyPresent() ? "set" : "missing"))),
				Component.literal("[ChatAISum] opencode: url=" + config.opencodeUrl
						+ " model=" + displayModel(config.opencodeModel)
						+ " key=" + (config.resolveOpenCodeKey().isBlank() ? "missing" : "set")),
				Component.literal("[ChatAISum] compatible: url=" + config.compatibleUrl
						+ " model=" + displayModel(config.compatibleModel)
						+ " key=" + (config.resolveCompatibleKey().isBlank() ? "optional" : "set")),
				Component.literal("[ChatAISum] gemini: url=" + config.geminiUrl
						+ " model=" + displayModel(config.geminiModel)
						+ " key=" + (config.resolveGeminiKey().isBlank() ? "missing" : "set")),
				Component.literal("[ChatAISum] language=" + config.language
						+ " maxLines=" + config.maxLines
						+ " maxTokens=" + config.maxOutputTokens
						+ " maxMessages=" + config.maxMessages
						+ " maxChars=" + config.maxChars),
				Component.literal("[ChatAISum] 使い方: /chataisum <分> [注目したい内容]")
		};
		for (Component line : lines) {
			source.sendFeedback(line);
		}
		return 1;
	}
}
