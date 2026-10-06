package net.flyingfinger.nbtpeek.config;

/** Plain settings holder, (de)serialised as JSON by {@link ConfigManager}. */
public class NbtPeekConfig {

	/** When the NBT lines are shown in a tooltip. */
	public enum Trigger { ADVANCED, ALWAYS, HOLD_KEY, TOGGLE_KEY }

	/** How the NBT is rendered in the tooltip. */
	public enum Style { PRETTY, PLAIN, COMPACT }

	/** How the NBT is written to the clipboard when copying. */
	public enum CopyFormat { SNBT, PRETTY }

	public Trigger trigger = Trigger.ADVANCED;
	public Style style = Style.PRETTY;
	public CopyFormat copyFormat = CopyFormat.SNBT;
	public boolean hideLore = false;
	public boolean hideCustomName = false;
	public boolean showHeader = true;
	public int maxLines = 12;
}
