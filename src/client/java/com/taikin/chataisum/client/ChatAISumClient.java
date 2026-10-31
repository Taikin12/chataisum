package com.taikin.chataisum.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

public class ChatAISumClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ChatAISumConfig.get();
		ModelCatalog.refreshAll();
		ClientCommandRegistrationCallback.EVENT.register(ChatAISumCommand::register);
	}
}
