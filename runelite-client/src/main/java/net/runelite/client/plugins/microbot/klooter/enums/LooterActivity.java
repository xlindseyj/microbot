package net.runelite.client.plugins.microbot.klooter.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LooterActivity {
    DEFAULT("Default"),
    FORESTRY("Forestry"),
    GRAND_EXCHANGE("Grand Exchange");

    private final String name;
}
