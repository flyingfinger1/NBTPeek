package net.flyingfinger.nbtpeek;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.serialization.DataResult;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * NBT Peek — shows an item's data components (its "NBT") directly in the tooltip
 * and copies them to the clipboard. Client-only, no mixins.
 */
public class NBTPeek implements ClientModInitializer {

	public static final String MOD_ID = "nbtpeek";

	/** Safety cap so a huge component map can't fill the whole screen. */
	private static final int MAX_LINES = 100;
	private static final String INDENT = "  ";

	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "general"));

	private static final KeyMapping COPY_KEY = new KeyMapping(
			"key.nbtpeek.copy", InputConstants.Type.KEYBOARD, InputConstants.KEY_C, CATEGORY);

	/** The stack whose tooltip was drawn most recently — i.e. the one under the cursor. */
	private static ItemStack lastHovered = ItemStack.EMPTY;

	@Override
	public void onInitializeClient() {
		KeyMappingHelper.registerKeyMapping(COPY_KEY);
		ItemTooltipCallback.EVENT.register(NBTPeek::onTooltip);
		ClientTickEvents.END_CLIENT_TICK.register(NBTPeek::onEndTick);
	}

	private static void onTooltip(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines) {
		lastHovered = stack;
		// Only add lines when advanced tooltips are on (toggle in-game with F3 + H).
		if (!flag.isAdvanced()) {
			return;
		}
		CompoundTag nbt = encode(stack, context.registries());
		if (nbt == null || nbt.isEmpty()) {
			return;
		}
		lines.add(Component.translatable("nbtpeek.header").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		append(lines, "", null, nbt, new int[] { MAX_LINES });
	}

	private static void onEndTick(Minecraft mc) {
		boolean handled = false;
		// consumeClick() drains queued key presses; copy at most once per tick.
		while (COPY_KEY.consumeClick()) {
			if (!handled) {
				handled = true;
				if (mc.player != null && !lastHovered.isEmpty()) {
					copyToClipboard(mc, lastHovered);
				}
			}
		}
	}

	private static void copyToClipboard(Minecraft mc, ItemStack stack) {
		Component name = stack.getHoverName();
		CompoundTag nbt = encode(stack, mc.player.registryAccess());
		if (nbt == null || nbt.isEmpty()) {
			toast(mc, Component.translatable("nbtpeek.copied.empty"), name);
			return;
		}
		mc.keyboardHandler.setClipboard(nbt.toString()); // compact SNBT, ready to paste into commands
		toast(mc, Component.translatable("nbtpeek.copied"), name);
	}

	private static void toast(Minecraft mc, Component title, Component description) {
		SystemToast.add(mc.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, description);
	}

	/**
	 * Encode the stack's data-component patch to a CompoundTag — the modern "NBT"
	 * of an item, i.e. only the components that differ from the item's defaults.
	 * Returns {@code null} if there is no component data to show.
	 */
	private static CompoundTag encode(ItemStack stack, HolderLookup.Provider registries) {
		DataResult<Tag> result = DataComponentPatch.CODEC.encodeStart(
				registries.createSerializationContext(NbtOps.INSTANCE), stack.getComponentsPatch());
		Tag tag = result.result().orElse(null);
		return (tag instanceof CompoundTag compound) ? compound : null;
	}

	// --- display formatting: a small, indented, colored pretty-printer ---

	private static void append(List<Component> out, String indent, String key, Tag tag, int[] budget) {
		if (budget[0] <= 0) {
			return;
		}
		String label = (key == null) ? "" : ChatFormatting.AQUA + key + ChatFormatting.DARK_GRAY + ": ";
		if (tag instanceof CompoundTag compound) {
			emit(out, budget, indent + label + ChatFormatting.GRAY + "{");
			for (String childKey : compound.keySet()) {
				append(out, indent + INDENT, childKey, compound.get(childKey), budget);
			}
			emit(out, budget, indent + ChatFormatting.GRAY + "}");
		} else if (tag instanceof CollectionTag collection) {
			emit(out, budget, indent + label + ChatFormatting.GRAY + "[");
			for (Tag element : collection) {
				append(out, indent + INDENT, null, element, budget);
			}
			emit(out, budget, indent + ChatFormatting.GRAY + "]");
		} else {
			emit(out, budget, indent + label + ChatFormatting.WHITE + String.valueOf(tag));
		}
	}

	private static void emit(List<Component> out, int[] budget, String text) {
		if (budget[0] == 1) {
			out.add(Component.literal(ChatFormatting.DARK_GRAY + "…")); // truncation marker
			budget[0] = 0;
		} else if (budget[0] > 0) {
			out.add(Component.literal(text));
			budget[0]--;
		}
	}
}
