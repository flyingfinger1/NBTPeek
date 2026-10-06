package net.flyingfinger.nbtpeek;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.serialization.DataResult;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.flyingfinger.nbtpeek.config.ConfigManager;
import net.flyingfinger.nbtpeek.config.NbtPeekConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.input.KeyEvent;
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
 *
 * <p>Key handling is event-based: copying/scrolling happen while a container
 * screen is open, so keys are read through {@link ScreenKeyboardEvents} rather
 * than polled. (Minecraft 26.x's SDL-backed {@code InputConstants.isKeyDown}
 * indexes a scancode buffer and is not safe to poll with arbitrary key values.)
 */
public class NBTPeek implements ClientModInitializer {

	public static final String MOD_ID = "nbtpeek";

	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "general"));

	// Minecraft 26.x (SDL input) stores a keybind's key as its SDL scancode — exactly
	// how vanilla constructs its own binds, e.g. new KeyMapping("key.forward", 26, …).
	// (Raw GLFW values like InputConstants.KEY_* are NOT scancodes and make vanilla's
	// KeyMapping.setAll() -> InputConstants.isKeyDown() go out of bounds.)
	private static final int SCANCODE_UNKNOWN = 0;
	private static final int SCANCODE_C = 6;
	private static final int SCANCODE_UP = 82;
	private static final int SCANCODE_DOWN = 81;

	private static final KeyMapping COPY_KEY = key("copy", SCANCODE_C);
	private static final KeyMapping TOGGLE_KEY = key("toggle", SCANCODE_UNKNOWN); // unbound by default
	private static final KeyMapping SCROLL_UP_KEY = key("scroll_up", SCANCODE_UP);
	private static final KeyMapping SCROLL_DOWN_KEY = key("scroll_down", SCANCODE_DOWN);

	/** The stack whose tooltip was drawn most recently — i.e. the one under the cursor. */
	private static ItemStack lastHovered = ItemStack.EMPTY;
	/** The stack the current scroll offset belongs to (reset scrolling when it changes). */
	private static ItemStack scrolledStack = ItemStack.EMPTY;
	private static int scroll = 0;

	private static boolean toggledOn = false;
	/** Show/hold key currently held while a screen is open (HOLD_KEY trigger; isDown() is dead in screens). */
	private static boolean showKeyHeld = false;
	private static int copyCooldown = 0;   // ticks, debounces copy against key-repeat
	private static int toggleCooldown = 0;

	private static KeyMapping key(String name, int sdlScancode) {
		// Same 3-arg constructor vanilla uses; the int is the SDL scancode.
		return new KeyMapping("key.nbtpeek." + name, sdlScancode, CATEGORY);
	}

	@Override
	public void onInitializeClient() {
		ConfigManager.load();
		for (KeyMapping mapping : new KeyMapping[] { COPY_KEY, TOGGLE_KEY, SCROLL_UP_KEY, SCROLL_DOWN_KEY }) {
			KeyMappingHelper.registerKeyMapping(mapping);
		}
		ItemTooltipCallback.EVENT.register(NBTPeek::onTooltip);
		ClientTickEvents.END_CLIENT_TICK.register(NBTPeek::onEndTick);
		// Keys while a screen (inventory/container) is open — the case we care about.
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			// Clear any stale hold state; a fresh press inside this screen re-establishes it.
			showKeyHeld = false;
			ScreenKeyboardEvents.afterKeyPress(screen).register((s, keyEvent) -> onScreenKey(keyEvent));
			ScreenKeyboardEvents.afterKeyRelease(screen).register((s, keyEvent) -> onScreenKeyRelease(keyEvent));
		});
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

		if (cfg.showHeader) {
			lines.add(Component.translatable("nbtpeek.header").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
		}

		if (total <= window) {
			scroll = 0;
			lines.addAll(body);
			return;
		}

		// Fixed-height window of exactly `window` lines. When content is hidden on a
		// side, that side's edge line becomes the "N more" indicator. Since the
		// indicator also stands in for the line it replaces, it always covers at least
		// two lines — so the pointless "1 more" step is skipped and the number of shown
		// lines never changes as you scroll.
		int v = Math.min(Math.max(scroll, 0), total - window);
		scroll = v;
		boolean moreAbove = v > 0;
		boolean moreBelow = v + window < total;
		int startContent = v + (moreAbove ? 1 : 0);
		int endContent = (v + window) - (moreBelow ? 1 : 0);

		if (moreAbove) {
			lines.add(hint("nbtpeek.more.above", startContent)); // body[0..startContent) hidden above
		}
		for (int i = startContent; i < endContent; i++) {
			lines.add(body.get(i));
		}
		if (moreBelow) {
			lines.add(hint("nbtpeek.more.below", total - endContent));
		}
	}

	/** Handles our keys while any screen is open (copy, scroll, toggle). */
	private static void onScreenKey(KeyEvent event) {
		InputConstants.Key pressed = InputConstants.getKey(event);
		Minecraft mc = Minecraft.getInstance();
		if (COPY_KEY.matches(pressed)) {
			if (copyCooldown == 0 && mc.player != null && !lastHovered.isEmpty()) {
				copyToClipboard(mc, lastHovered); // the item whose tooltip is currently shown
				copyCooldown = 5;
			}
		} else if (SCROLL_UP_KEY.matches(pressed)) {
			scroll = Math.max(0, scroll - 1);
		} else if (SCROLL_DOWN_KEY.matches(pressed)) {
			scroll += 1; // clamped against the line count in onTooltip
		} else if (TOGGLE_KEY.matches(pressed)) {
			showKeyHeld = true;            // HOLD_KEY trigger — stays true until the key is released
			if (toggleCooldown == 0) {     // TOGGLE_KEY trigger — flip once per press (debounced)
				toggledOn = !toggledOn;
				toggleCooldown = 5;
			}
		}
	}

	/** Clears the hold state when the show/hold key is released inside a screen. */
	private static void onScreenKeyRelease(KeyEvent event) {
		if (TOGGLE_KEY.matches(InputConstants.getKey(event))) {
			showKeyHeld = false;
		}
	}

	private static void onEndTick(Minecraft mc) {
		if (copyCooldown > 0) {
			copyCooldown--;
		}
		if (toggleCooldown > 0) {
			toggleCooldown--;
		}
		// In-world toggle (consumeClick only fires when no screen is open).
		while (TOGGLE_KEY.consumeClick()) {
			toggledOn = !toggledOn;
		}
		// Copy/scroll are screen-only; drain any queued in-world presses so they don't pile up.
		while (COPY_KEY.consumeClick()) {
		}
		while (SCROLL_UP_KEY.consumeClick()) {
		}
		while (SCROLL_DOWN_KEY.consumeClick()) {
		}

		// Reset each tick; the tooltip callback re-sets it every frame while hovering,
		// so a copy only ever takes the item currently under the cursor.
		lastHovered = ItemStack.EMPTY;
	}

	private static boolean shouldShow(NbtPeekConfig cfg, TooltipFlag flag) {
		return switch (cfg.trigger) {
			case ADVANCED -> flag.isAdvanced();
			case ALWAYS -> true;
			case HOLD_KEY -> showKeyHeld || TOGGLE_KEY.isDown(); // screen-tracked, plus in-world fallback
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
}
