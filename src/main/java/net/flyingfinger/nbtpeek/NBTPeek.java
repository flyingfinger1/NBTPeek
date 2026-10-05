package net.flyingfinger.nbtpeek;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.serialization.DataResult;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.flyingfinger.nbtpeek.config.ConfigManager;
import net.flyingfinger.nbtpeek.config.NbtPeekConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * NBT Peek — shows an item's data components (its "NBT") in the tooltip and
 * copies them to the clipboard. Client-only, no mixins.
 */
public class NBTPeek implements ClientModInitializer {

	public static final String MOD_ID = "nbtpeek";

	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "general"));

	private static final KeyMapping COPY_KEY = key("copy", InputConstants.KEY_C);
	private static final KeyMapping TOGGLE_KEY = key("toggle", -1); // unbound by default
	private static final KeyMapping SCROLL_UP_KEY = key("scroll_up", InputConstants.KEY_UP);
	private static final KeyMapping SCROLL_DOWN_KEY = key("scroll_down", InputConstants.KEY_DOWN);

	/** The stack whose tooltip was drawn most recently — i.e. the one under the cursor. */
	private static ItemStack lastHovered = ItemStack.EMPTY;
	/** The stack the current scroll offset belongs to (reset scrolling when it changes). */
	private static ItemStack scrolledStack = ItemStack.EMPTY;
	private static int scroll = 0;

	// Edge-detection / repeat state for polled keys.
	private static boolean copyHeld;
	private static boolean toggleHeld;
	private static boolean scrollUpHeld;
	private static boolean scrollDownHeld;
	private static int scrollRepeat;
	private static boolean toggledOn;

	private static KeyMapping key(String name, int defaultCode) {
		return new KeyMapping("key.nbtpeek." + name, InputConstants.Type.KEYBOARD, defaultCode, CATEGORY);
	}

	@Override
	public void onInitializeClient() {
		ConfigManager.load();
		for (KeyMapping mapping : new KeyMapping[] { COPY_KEY, TOGGLE_KEY, SCROLL_UP_KEY, SCROLL_DOWN_KEY }) {
			KeyMappingHelper.registerKeyMapping(mapping);
		}
		ItemTooltipCallback.EVENT.register(NBTPeek::onTooltip);
		ClientTickEvents.END_CLIENT_TICK.register(NBTPeek::onEndTick);
	}

	private static void onTooltip(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines) {
		lastHovered = stack;
		NbtPeekConfig cfg = ConfigManager.get();
		if (!shouldShow(cfg, flag)) {
			return;
		}
		CompoundTag nbt = encode(stack, context.registries());
		if (nbt == null) {
			return;
		}
		nbt = applyHides(nbt, cfg);
		if (nbt.isEmpty()) {
			return;
		}

		List<Component> body = NbtRenderer.toLines(nbt, cfg.style);
		if (body.isEmpty()) {
			return;
		}

		// Reset the scroll offset whenever a different item is hovered.
		if (stack != scrolledStack) {
			scroll = 0;
			scrolledStack = stack;
		}
		int total = body.size();
		int window = Math.max(1, cfg.maxLines);
		int offset = Math.min(Math.max(scroll, 0), Math.max(0, total - window));
		scroll = offset;

		if (cfg.showHeader) {
			lines.add(Component.translatable("nbtpeek.header").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		}
		if (offset > 0) {
			lines.add(hint("nbtpeek.more.above", offset));
		}
		int end = Math.min(total, offset + window);
		for (int i = offset; i < end; i++) {
			lines.add(body.get(i));
		}
		if (end < total) {
			lines.add(hint("nbtpeek.more.below", total - end));
		}
	}

	private static void onEndTick(Minecraft mc) {
		boolean copy = isDown(COPY_KEY);
		if (copy && !copyHeld && mc.player != null && !lastHovered.isEmpty()) {
			copyToClipboard(mc, lastHovered); // the item whose tooltip is currently shown
		}
		copyHeld = copy;

		boolean toggle = isDown(TOGGLE_KEY);
		if (toggle && !toggleHeld) {
			toggledOn = !toggledOn;
		}
		toggleHeld = toggle;

		// Scroll: one line on a fresh press, then repeat while held.
		boolean up = isDown(SCROLL_UP_KEY);
		boolean down = isDown(SCROLL_DOWN_KEY);
		int delta = 0;
		if (up && !scrollUpHeld) {
			delta -= 1;
		}
		if (down && !scrollDownHeld) {
			delta += 1;
		}
		if (up ^ down) {
			if (++scrollRepeat >= 6) {
				scrollRepeat = 0;
				delta += down ? 1 : -1;
			}
		} else {
			scrollRepeat = 0;
		}
		scroll += delta; // clamped against the actual line count in onTooltip
		scrollUpHeld = up;
		scrollDownHeld = down;

		// Reset each tick; the tooltip callback re-sets it every frame while hovering,
		// so a copy only ever takes the item currently under the cursor.
		lastHovered = ItemStack.EMPTY;
	}

	private static boolean shouldShow(NbtPeekConfig cfg, TooltipFlag flag) {
		return switch (cfg.trigger) {
			case ADVANCED -> flag.isAdvanced();
			case ALWAYS -> true;
			case HOLD_KEY -> isDown(TOGGLE_KEY);
			case TOGGLE_KEY -> toggledOn;
		};
	}

	private static CompoundTag applyHides(CompoundTag nbt, NbtPeekConfig cfg) {
		if (!cfg.hideLore && !cfg.hideCustomName) {
			return nbt;
		}
		CompoundTag copy = nbt.copy();
		if (cfg.hideLore) {
			copy.remove("minecraft:lore");
		}
		if (cfg.hideCustomName) {
			copy.remove("minecraft:custom_name");
		}
		return copy;
	}

	private static void copyToClipboard(Minecraft mc, ItemStack stack) {
		Component name = stack.getHoverName();
		CompoundTag nbt = encode(stack, mc.player.registryAccess());
		if (nbt == null || nbt.isEmpty()) {
			toast(mc, Component.translatable("nbtpeek.copied.empty"), name);
			return;
		}
		String text = (ConfigManager.get().copyFormat == NbtPeekConfig.CopyFormat.PRETTY)
				? NbtRenderer.toPrettyString(nbt)   // indented, multi-line
				: nbt.toString();                   // compact SNBT, ready to paste into commands
		mc.keyboardHandler.setClipboard(text);
		toast(mc, Component.translatable("nbtpeek.copied"), name);
	}

	private static void toast(Minecraft mc, Component title, Component description) {
		SystemToast.add(mc.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, description);
	}

	/**
	 * Encode the stack's data-component patch to a CompoundTag — the modern "NBT"
	 * of an item (only the components that differ from the item's defaults).
	 * Returns {@code null} if there is no component data.
	 */
	private static CompoundTag encode(ItemStack stack, HolderLookup.Provider registries) {
		DataResult<Tag> result = DataComponentPatch.CODEC.encodeStart(
				registries.createSerializationContext(NbtOps.INSTANCE), stack.getComponentsPatch());
		Tag tag = result.result().orElse(null);
		return (tag instanceof CompoundTag compound) ? compound : null;
	}

	private static Component hint(String key, int count) {
		return Component.literal(ChatFormatting.DARK_GRAY + "… ")
				.append(Component.translatable(key, count).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}

	private static boolean isDown(KeyMapping mapping) {
		return !mapping.isUnbound()
				&& InputConstants.isKeyDown(KeyMappingHelper.getBoundKeyOf(mapping).getValue());
	}
}
