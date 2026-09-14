package es.buni.hcb.automation.modes;

import java.util.Locale;

public enum HouseMode {
    HOME("日常"), AWAY("离家"), SLEEP("睡眠"), MOVIE("观影"), CLEANING("清扫"), GUEST("访客");
    private final String label;
    HouseMode(String label) { this.label = label; }
    public String label() { return label; }
    public static HouseMode parse(String value) {
        try { return valueOf(value.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Unknown house mode: " + value); }
    }
}
