package net.runelite.client.plugins.microbot.GeoffPlugins.construction2;

import net.runelite.api.*;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.GeoffPlugins.construction2.enums.Construction2State;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;
import java.util.Random;

public class Construction2Script extends Script {

    private static final int DEFAULT_DELAY = 600;
    private static final int HUMAN_IDLE_BREAK_CHANCE = 8; // ~12.5% chance
    private static final int MOUSE_OFFSCREEN_CHANCE = 5;  // ~20% chance

    private long startTime;
    private long endTime;
    private int currentXp;

    private final Random random = new Random();
    private Construction2State state = Construction2State.Idle;

    public TileObject getOakDungeonDoorSpace() {
        return Rs2GameObject.findObjectById(15328); // ID for oak dungeon door space
    }

    public TileObject getOakDungeonDoor() {
        return Rs2GameObject.findObjectById(13344); // ID for oak dungeon door
    }

    public TileObject getOakLarderSpace() {
        return Rs2GameObject.findObjectById(15403); // ID for oak larder space
    }

    public TileObject getOakLarder() {
        return Rs2GameObject.findObjectById(13566); // ID for oak larder
    }

    public TileObject getMahoganyTableSpace() {
        return Rs2GameObject.findObjectById(15298); // ID for mahogany table space
    }

    public TileObject getMahoganyTable() {
        return Rs2GameObject.findObjectById(13298); // ID for mahogany table
    }

    public TileObject getGuildTrophySpace() {
        return Rs2GameObject.findObjectById(31986); // ID for guild trophy space (Mythical Cape Mount space)
    }

    public TileObject getMythicalCapeMount() {
        return Rs2GameObject.findObjectById(15394); // ID for mythical cape mount
    }

    public Rs2NpcModel getButler() {
        return Rs2Npc.getNpc("Demon butler");
    }

    public boolean hasDialogueOptionToUnnote() {
        return Rs2Widget.findWidget("Un-note", null) != null;
    }

    public boolean hasDialogueOptionToFetch() {
        return Rs2Widget.findWidget("Fetch from bank: 25 x Mahogany plank", null) != null;
    }

    public boolean hasPayButlerDialogue() {
        return Rs2Widget.findWidget("must render unto me the 10,000 coins that are due", null) != null;
    }

    public boolean hasDialogueOptionToPay() {
        return Rs2Widget.findWidget("Okay, here's 10,000 coins.", null) != null;
    }

    public boolean hasFurnitureInterfaceOpen() {
        Widget furnitureWidget = Rs2Widget.findWidget("Furniture", null);
        if (furnitureWidget != null) {
            System.out.println("Furniture interface is open.");
            return true;
        }
        System.out.println("Furniture interface is not open.");
        return false;
    }

    public boolean hasRemoveDoorInterfaceOpen() {
        return Rs2Widget.findWidget("Really remove it?", null) != null;
    }

    public boolean hasRemoveLarderInterfaceOpen() {
        return Rs2Widget.findWidget("Really remove it?", null) != null;
    }

    public boolean hasRemoveTableInterfaceOpen() {
        return Rs2Widget.findWidget("Really remove it?", null) != null;
    }

    public boolean hasRemoveCapeMountInterfaceOpen() {
        return Rs2Widget.findWidget("Really remove it?", null) != null;
    }

//    public String getExperiencePerHour() {
//        if (endTime == 0) {
//            endTime = System.currentTimeMillis();
//        }
//        long elapsedTime = endTime - startTime;
//        int xpGained = currentXp - Rs2GameObject.getExperience(Skill.CONSTRUCTION);
//        return String.format("%,d", (int) ((xpGained * 3600000L) / elapsedTime));
//    }

    public boolean run(Construction2Config config) {
        startTime = System.currentTimeMillis();
        int actionDelay = config.useCustomDelay() ? config.actionDelay() : DEFAULT_DELAY;
        boolean useAntiban = config.useAntiban();

        if (useAntiban) {
            /**
             * Applies the antiban setup tailored for construction activities.
             * This setup focuses on mimicking human-like behaviors during construction tasks.
             */
            Rs2AntibanSettings.antibanEnabled = true;
            Rs2AntibanSettings.usePlayStyle = true;
            Rs2AntibanSettings.randomIntervals = false;
            Rs2AntibanSettings.simulateFatigue = true;
            Rs2AntibanSettings.simulateAttentionSpan = true;
            Rs2AntibanSettings.behavioralVariability = true;
            Rs2AntibanSettings.nonLinearIntervals = true;
            Rs2AntibanSettings.profileSwitching = true;
            Rs2AntibanSettings.timeOfDayAdjust = false;
            Rs2AntibanSettings.simulateMistakes = true;
            Rs2AntibanSettings.naturalMouse = true;
            Rs2AntibanSettings.contextualVariability = true;
            Rs2AntibanSettings.dynamicIntensity = false;
            Rs2AntibanSettings.dynamicActivity = false;
            Rs2AntibanSettings.devDebug = true;
            Rs2AntibanSettings.takeMicroBreaks = true;
            Rs2AntibanSettings.playSchedule = true;
            Rs2AntibanSettings.universalAntiban = false;
            Rs2AntibanSettings.microBreakDurationLow = 3;
            Rs2AntibanSettings.microBreakDurationHigh = 8;
            Rs2AntibanSettings.actionCooldownChance = 1.00;
            Rs2AntibanSettings.microBreakChance = 0.05;
            Rs2Antiban.setActivity(Activity.GENERAL_CONSTRUCTION);
        }

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                Rs2Tab.switchToInventoryTab();
                calculateState(config);
                switch (state) {
                    case Build:
                        build(config, actionDelay);
                        break;
                    case Remove:
                        remove(config, actionDelay);
                        break;
                    case Butler:
                        butler(config, actionDelay);
                        break;
                    default:
                        break;
                }
            } catch (Exception ex) {
                endTime = System.currentTimeMillis();
                System.out.println("Ran for: " + (endTime - startTime) / 1000 + " seconds");
                System.out.println("Error in scheduled task: " + ex.getMessage());
            }
        }, 0, actionDelay, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }

    private void calculateState(Construction2Config config) {
        boolean hasRequiredPlanks = Rs2Inventory.hasItemAmount(config.selectedMode().getPlankItemId(), Rs2Random.between(6, 18));

        TileObject space = null;
        TileObject builtObject = null;

        switch (config.selectedMode()) {
            case OAK_DUNGEON_DOOR:
                space = getOakDungeonDoorSpace();
                builtObject = getOakDungeonDoor();
                break;
            case OAK_LARDER:
                space = getOakLarderSpace();
                builtObject = getOakLarder();
                break;
            case MAHOGANY_TABLE:
                space = getMahoganyTableSpace();
                builtObject = getMahoganyTable();
                break;
            // case MYTHICAL_CAPE:
            //     space = getGuildTrophySpace();
            //     builtObject = getMythicalCapeMount();
            //     break;
            default:
                return;
        }

        NPC butler = getButler();
        if (space == null && builtObject != null) {
            state = Construction2State.Remove;
        } else if (space != null && builtObject == null && hasRequiredPlanks) {
            state = Construction2State.Build;
        } else if (space != null && builtObject == null && butler != null) {
            state = Construction2State.Butler;
        } else if (space == null && builtObject == null) {
            state = Construction2State.Idle;
            Microbot.getNotifier().notify("Looks like we are no longer in your house.");
            shutdown();
        }
    }

    private void build(Construction2Config config, int actionDelay) {
        TileObject space = null;
        char buildKey = '1';

        switch (config.selectedMode()) {
            case OAK_DUNGEON_DOOR:
                space = getOakDungeonDoorSpace();
                buildKey = '1';
                break;
            case OAK_LARDER:
                space = getOakLarderSpace();
                buildKey = '2';
                break;
            case MAHOGANY_TABLE:
                space = getMahoganyTableSpace();
                buildKey = '6';
                break;
            // case MYTHICAL_CAPE:
            //     space = getGuildTrophySpace();
            //     buildKey = '4';
            //     break;
            default:
                return;
        }

        if (space == null) return;
        if (Rs2GameObject.interact(space, "Build")) {
            System.out.println("Interacted with build space: " + space.getId());
            sleepUntilOnClientThread(this::hasFurnitureInterfaceOpen, 2500);
            System.out.println("Pressing key: " + buildKey);
            Rs2Keyboard.keyPress(buildKey);
            sleepUntilOnClientThread(() -> getBuiltObject(config) != null, 2500);
            System.out.println("Built object: " + config.selectedMode());
        } else {
            System.out.println("Failed to interact with build space: " + space.getId());
        }
    }

    private void remove(Construction2Config config, int actionDelay) {
        TileObject builtObject = null;

        switch (config.selectedMode()) {
            case OAK_DUNGEON_DOOR:
                builtObject = getOakDungeonDoor();
                break;
            case OAK_LARDER:
                builtObject = getOakLarder();
                break;
            case MAHOGANY_TABLE:
                builtObject = getMahoganyTable();
                break;
            // case MYTHICAL_CAPE:
            //     builtObject = getMythicalCapeMount();
            //     break;
            default:
                return;
        }

        if (builtObject == null) return;
        if (Rs2GameObject.interact(builtObject, "Remove")) {
            System.out.println("Interacted with remove option: " + builtObject.getId());
            sleepUntilOnClientThread(() -> hasRemoveInterfaceOpen(config), 2500);
            Rs2Keyboard.keyPress('1');
            sleepUntilOnClientThread(() -> getBuildSpace(config) != null, 2500);
            System.out.println("Removed object: " + config.selectedMode());
        } else {
            System.out.println("Failed to interact with remove option: " + builtObject.getId());
        }
    }

    private void butler(Construction2Config config, int actionDelay) {
        var butler = getButler();
        if (butler == null) return;
        boolean butlerIsTooFar = Microbot.getClientThread().runOnClientThreadOptional(() ->
                butler.getWorldLocation().distanceTo(Microbot.getClient().getLocalPlayer().getWorldLocation()) > 3
        ).orElse(false);
        if (butlerIsTooFar) {
            Rs2Tab.switchToSettingsTab();
            sleep(300, 900);
            Widget houseOptionWidget = Rs2Widget.findWidget(SpriteID.OPTIONS_HOUSE_OPTIONS, null);
            if (houseOptionWidget != null) Microbot.getMouse().click(houseOptionWidget.getCanvasLocation());
            sleep(300, 900);
            Widget callServantWidget = Rs2Widget.findWidget("Call Servant", null);
            if (callServantWidget != null) Microbot.getMouse().click(callServantWidget.getCanvasLocation());
        }

        if (Rs2Dialogue.isInDialogue() || Rs2Npc.interact(butler, "Talk-to")) {
            sleep(500);
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            sleep(400, 1000);
            if (Rs2Widget.findWidget("Go to the bank...", null) != null) {
                Rs2Inventory.useItemOnNpc(config.selectedMode().getPlankItemId() + 1, butler.getId()); // + 1 for noted item
                sleepUntilOnClientThread(() -> Rs2Widget.hasWidget("Dost thou wish me to exchange that certificate"));
                Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                sleepUntilOnClientThread(() -> Rs2Widget.hasWidget("Select an option"));
                Rs2Keyboard.typeString("1");
                sleepUntilOnClientThread(() -> Rs2Widget.hasWidget("Enter amount:"));
                Rs2Keyboard.typeString("28");
                Rs2Keyboard.enter();
            } else if (hasDialogueOptionToUnnote()) {
                Rs2Keyboard.keyPress('1');
                sleepUntilOnClientThread(() -> !hasDialogueOptionToUnnote());
            } else if (hasDialogueOptionToFetch()) {
                Rs2Keyboard.keyPress('1');
                sleepUntilOnClientThread(() -> !hasDialogueOptionToFetch());
            } else if (hasRemoveInterfaceOpen(config)) {
                Rs2Keyboard.keyPress('1');
                sleepUntilOnClientThread(() -> getBuildSpace(config) != null);
            } else if (hasPayButlerDialogue() || hasDialogueOptionToPay()) {
                Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                sleep(400, 1000);
                if (hasDialogueOptionToPay()) {
                    Rs2Keyboard.keyPress('1');
                }
            }
        }
    }

    private boolean hasRemoveInterfaceOpen(Construction2Config config) {
        switch (config.selectedMode()) {
            case OAK_DUNGEON_DOOR:
                return hasRemoveDoorInterfaceOpen();
            case OAK_LARDER:
                return hasRemoveLarderInterfaceOpen();
            case MAHOGANY_TABLE:
                return hasRemoveTableInterfaceOpen();
            // case MYTHICAL_CAPE:
            // return hasRemoveCapeMountInterfaceOpen();
            default:
                return false;
        }
    }

    private TileObject getBuiltObject(Construction2Config config) {
        switch (config.selectedMode()) {
            case OAK_DUNGEON_DOOR:
                return getOakDungeonDoor();
            case OAK_LARDER:
                return getOakLarder();
            case MAHOGANY_TABLE:
                return getMahoganyTable();
            // case MYTHICAL_CAPE:
            // return getMythicalCapeMount();
            default:
                return null;
        }
    }

    private TileObject getBuildSpace(Construction2Config config) {
        switch (config.selectedMode()) {
            case OAK_DUNGEON_DOOR:
                return getOakDungeonDoorSpace();
            case OAK_LARDER:
                return getOakLarderSpace();
            case MAHOGANY_TABLE:
                return getMahoganyTableSpace();
            // case MYTHICAL_CAPE:
            // return getGuildTrophySpace();
            default:
                return null;
        }
    }

    public Construction2State getState() {
        return state;
    }
}
