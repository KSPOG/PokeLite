package dev.kspog.pokelite.gamedata;

public enum GameDataField {
    MONEY("Money"),
    EXPERIENCE("Experience");

    private final String displayName;

    GameDataField(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
