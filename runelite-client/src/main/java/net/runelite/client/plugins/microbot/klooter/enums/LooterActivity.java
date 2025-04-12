package net.runelite.client.plugins.microbot.klooter.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LooterActivity {
    DEFAULT("Default"),
    EDGEVILLE("Edgeville"),
    FORESTRY("Forestry"),
    GRAND_EXCHANGE("Grand Exchange"),
    WINTERTODT("Wintertodt");

    private final String name;
}
