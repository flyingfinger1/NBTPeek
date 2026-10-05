package net.flyingfinger.nbtpeek;

import java.util.ArrayList;
import java.util.List;

import net.flyingfinger.nbtpeek.config.NbtPeekConfig.Style;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/** Turns a CompoundTag into tooltip lines in one of several styles. */
public final class NbtRenderer {

	private static final String INDENT = "  ";
	private static final int COMPACT_WRAP = 64;

	private NbtRenderer() {
	}

	/** Multi-line, indented plain-text form for the clipboard (no colour codes). */
	public static String toPrettyString(CompoundTag tag) {
		StringBuilder sb = new StringBuilder();
		for (Component line : toLines(tag, Style.PLAIN)) {
			if (sb.length() > 0) {
				sb.append('\n');
			}
			sb.append(line.getString());
		}
		return sb.toString();
	}

	public static List<Component> toLines(CompoundTag tag, Style style) {
		List<Component> out = new ArrayList<>();
		if (style == Style.COMPACT) {
			String snbt = tag.toString();
			for (int i = 0; i < snbt.length(); i += COMPACT_WRAP) {
				out.add(Component.literal(snbt.substring(i, Math.min(snbt.length(), i + COMPACT_WRAP))));
			}
		} else {
			append(out, "", null, tag, style == Style.FRIENDLY);
		}
		return out;
	}

	private static void append(List<Component> out, String indent, String key, Tag tag, boolean colored) {
		String label = key == null ? "" :
				colored ? ChatFormatting.AQUA + key + ChatFormatting.DARK_GRAY + ": " : key + ": ";
		String brace = colored ? ChatFormatting.GRAY.toString() : "";
		if (tag instanceof CompoundTag compound) {
			out.add(Component.literal(indent + label + brace + "{"));
			for (String childKey : compound.keySet()) {
				append(out, indent + INDENT, childKey, compound.get(childKey), colored);
			}
			out.add(Component.literal(indent + brace + "}"));
		} else if (tag instanceof CollectionTag collection) {
			out.add(Component.literal(indent + label + brace + "["));
			for (Tag element : collection) {
				append(out, indent + INDENT, null, element, colored);
			}
			out.add(Component.literal(indent + brace + "]"));
		} else {
			String value = colored ? ChatFormatting.WHITE + String.valueOf(tag) : String.valueOf(tag);
			out.add(Component.literal(indent + label + value));
		}
	}
}
