package net.flyingfinger.nbtpeek.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.network.chat.Component;

/** Mod Menu entry point that builds the Cloth Config screen. */
public class ConfigScreen implements ModMenuApi {

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> {
			NbtPeekConfig cfg = ConfigManager.get();

			ConfigBuilder builder = ConfigBuilder.create()
					.setParentScreen(parent)
					.setTitle(Component.translatable("nbtpeek.config.title"))
					.setSavingRunnable(ConfigManager::save);

			ConfigEntryBuilder eb = builder.entryBuilder();
			ConfigCategory general = builder.getOrCreateCategory(Component.translatable("nbtpeek.config.general"));

			general.addEntry(eb.startEnumSelector(Component.translatable("nbtpeek.config.trigger"),
							NbtPeekConfig.Trigger.class, cfg.trigger)
					.setDefaultValue(NbtPeekConfig.Trigger.ADVANCED)
					.setTooltip(Component.translatable("nbtpeek.config.trigger.tooltip"))
					.setSaveConsumer(value -> cfg.trigger = value)
					.build());

			general.addEntry(eb.startEnumSelector(Component.translatable("nbtpeek.config.style"),
							NbtPeekConfig.Style.class, cfg.style)
					.setDefaultValue(NbtPeekConfig.Style.FRIENDLY)
					.setTooltip(Component.translatable("nbtpeek.config.style.tooltip"))
					.setSaveConsumer(value -> cfg.style = value)
					.build());

			general.addEntry(eb.startEnumSelector(Component.translatable("nbtpeek.config.copyFormat"),
							NbtPeekConfig.CopyFormat.class, cfg.copyFormat)
					.setDefaultValue(NbtPeekConfig.CopyFormat.SNBT)
					.setTooltip(Component.translatable("nbtpeek.config.copyFormat.tooltip"))
					.setSaveConsumer(value -> cfg.copyFormat = value)
					.build());

			general.addEntry(eb.startBooleanToggle(Component.translatable("nbtpeek.config.hideLore"), cfg.hideLore)
					.setDefaultValue(false)
					.setSaveConsumer(value -> cfg.hideLore = value)
					.build());

			general.addEntry(eb.startBooleanToggle(Component.translatable("nbtpeek.config.hideCustomName"), cfg.hideCustomName)
					.setDefaultValue(false)
					.setSaveConsumer(value -> cfg.hideCustomName = value)
					.build());

			general.addEntry(eb.startBooleanToggle(Component.translatable("nbtpeek.config.showHeader"), cfg.showHeader)
					.setDefaultValue(true)
					.setSaveConsumer(value -> cfg.showHeader = value)
					.build());

			general.addEntry(eb.startIntField(Component.translatable("nbtpeek.config.maxLines"), cfg.maxLines)
					.setDefaultValue(12)
					.setMin(1)
					.setMax(200)
					.setTooltip(Component.translatable("nbtpeek.config.maxLines.tooltip"))
					.setSaveConsumer(value -> cfg.maxLines = value)
					.build());

			return builder.build();
		};
	}
}
