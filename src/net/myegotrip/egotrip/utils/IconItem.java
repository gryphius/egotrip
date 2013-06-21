package net.myegotrip.egotrip.utils;


public class IconItem {
	public final String text;
    public final int icon;
    public IconItem(String text, Integer icon) {
        this.text = text;
        this.icon = icon;
    }
    @Override
    public String toString() {
        return text;
    }
}
