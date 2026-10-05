package net.flyingfinger.nbtpeek.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/** Loads/saves {@link NbtPeekConfig} as {@code config/nbtpeek.json}. */
public final class ConfigManager {

	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("nbtpeek.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static NbtPeekConfig config = new NbtPeekConfig();

	private ConfigManager() {
	}

	public static NbtPeekConfig get() {
		return config;
	}

	public static void load() {
		if (Files.isRegularFile(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH)) {
				NbtPeekConfig loaded = GSON.fromJson(reader, NbtPeekConfig.class);
				if (loaded != null) {
					config = loaded;
				}
			} catch (Exception e) {
				System.err.println("[NBT Peek] Could not read config, using defaults: " + e.getMessage());
			}
		}
		sanitize();
		save(); // normalise / write defaults on first run
	}

	public static void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(config, writer);
			}
		} catch (IOException e) {
			System.err.println("[NBT Peek] Could not write config: " + e.getMessage());
		}
	}

	private static void sanitize() {
		if (config.trigger == null) {
			config.trigger = NbtPeekConfig.Trigger.ADVANCED;
		}
		if (config.style == null) {
			config.style = NbtPeekConfig.Style.FRIENDLY;
		}
		config.maxLines = Math.max(1, Math.min(200, config.maxLines));
	}
}
