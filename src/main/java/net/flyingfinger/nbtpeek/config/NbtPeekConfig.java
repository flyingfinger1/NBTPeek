package net.flyingfinger.nbtpeek.config;

/** Plain settings holder, (de)serialised as JSON by {@link ConfigManager}. */
public class NbtPeekConfig {

	/** When the NBT lines are shown in a tooltip. */
	public enum Trigger { ADVANCED, ALWAYS, HOLD_KEY, TOGGLE_KEY }

	/** How the NBT is rendered. */
	public enum Style { FRIENDLY, PLAIN, COMPACT }

	public Trigger trigger = Trigger.ADVANCED;
	public Style style = Style.FRIENDLY;
	public boolean hideLore = false;
	public boolean hideCustomName = false;
	public boolean showHeader = true;
	public int maxLines = 12;
}
