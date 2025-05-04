package me.nikl.gamebox.nms;

public enum WindowType_1_20_R4 {
    GENERIC_9_1("minecraft:generic_9x1"),
    GENERIC_9_2("minecraft:generic_9x2"),
    GENERIC_9_3("minecraft:generic_9x3"),
    GENERIC_9_4("minecraft:generic_9x4"),
    GENERIC_9_5("minecraft:generic_9x5"),
    GENERIC_9_6("minecraft:generic_9x6");

    private final String windowTypeKey;

    WindowType_1_20_R4(String windowTypeKey) {
        this.windowTypeKey = windowTypeKey;
    }

    public String getWindowTypeKey() {
        return windowTypeKey;
    }

    public static WindowType_1_20_R4 guessBySlots(int slots) {
        if (slots % 9 == 0) {
            switch (slots / 9) {
                case 1:
                    return GENERIC_9_1;
                case 2:
                    return GENERIC_9_2;
                case 3:
                    return GENERIC_9_3;
                case 4:
                    return GENERIC_9_4;
                case 5:
                    return GENERIC_9_5;
                case 6:
                    return GENERIC_9_6;
            }
        }
        return GENERIC_9_3;
    }
}