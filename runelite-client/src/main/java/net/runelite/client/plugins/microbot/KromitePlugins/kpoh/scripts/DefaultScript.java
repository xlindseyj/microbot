package net.runelite.client.plugins.microbot.KromitePlugins.kpoh.scripts;

import net.runelite.api.MenuAction;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectID;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.KromitePlugins.kpoh.KPOHConfig;
import net.runelite.client.plugins.microbot.KromitePlugins.kpoh.enums.KPOHState;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class DefaultScript extends Script {

    public Widget targetWidget;
    public String houseOwner;

    public WorldPoint portalCoords;
    public WorldPoint altarCoords;
    public Boolean usePortal;
    public boolean visitedOnce;
    List<String> blacklistNames = new ArrayList<>();
    List<String> whitelistNames = new ArrayList<>();

    public static KPOHState state = KPOHState.IDLE;
    public static KPOHConfig config;

    public boolean run(KPOHConfig config) {
        blacklistNames.add("V 3");
        whitelistNames.add("xGrace");
        whitelistNames.add("RunePolitics");

        if (config.antiban()) {
            applyAntiBanSettings();
        }

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (
                    !Microbot.isLoggedIn()||
                    !super.run() ||
                    !validateInventory() ||
                    Microbot.isGainingExp
                ) {
                    return;
                } else {
                    Microbot.status = "Passed validation";
                }

                calculateState();
                executeCurrentState();
            } catch (Exception ex) {
                Microbot.log(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    private boolean validateInventory() {
        if (!Rs2Inventory.hasItem(995)) {
            Microbot.showMessage("No gp found in your inventory");
            shutdown();
            return false;
        }

        if (!hasNotedBones() && !hasUnNotedBones()) {
            Microbot.showMessage("No bones found in your inventory");
            shutdown();
            return false;
        }

        return true;
    }

    private void executeCurrentState() {
        Microbot.status = "State: " + state.name();

        switch (state) {
            case LEAVE_HOUSE:
                leaveHouse();
                break;
            case UNNOTE_BONES:
                unnoteBones();
                break;
            case ENTER_HOUSE:
                enterHouse();
                break;
            case BONES_ON_ALTAR:
                bonesOnAltar();
                break;
            case ANTIBAN:
                performAntiBan();
                break;
            case IDLE:
            default:
                // Do nothing in IDLE state
                break;
        }
    }

    private boolean inHouse() {
        return getPhials() == null;
    }

    private Rs2NpcModel getPhials() {
        return Rs2Npc.getNpc("Phials");
    }

    private String getBoneType() {
        if (Microbot.getClient().getWidget(14352385) == null) {
            if (!Rs2Inventory.isItemSelected()) {
                Rs2Inventory.use("bones");
                return Rs2Inventory.getSelectedItemName();
            } else {
                return Rs2Inventory.getSelectedItemName();
            }
        }
        return null;
    }

    private void interactWithPhials(String action) {
        Rs2NpcModel phials = getPhials();
        if (phials != null) {
            ArrayList<String> actions = new ArrayList<>();
            // interact with phials to get the available actions
            for (String actionName : phials.getActions()) {
                if (actionName != null && !actionName.isEmpty()) {
                    actions.add(actionName);
                }
            }

            String npcName = phials.getName();
            String boneType = getBoneType();
            String combinedAction = action + " -> " + boneType;
            Rs2Npc.interact(npcName, combinedAction);
            Rs2Player.waitForWalking();
        } else {
            Microbot.log("Phials not found, retrying...");
        }
    }

    private void rightClick(Rs2NpcModel npc, String action) {
        Microbot.status = action + " " + npc.getName();
        try {
            if (Microbot.isCantReachTargetDetectionEnabled && Microbot.cantReachTarget) {
                if (!Rs2Npc.hasLineOfSight(npc)) {
                    if (Microbot.cantReachTargetRetries >= Rs2Random.between(3, 5)) {
                        Microbot.pauseAllScripts = true;
                        Microbot.showMessage("Your bot tried to interact with an NPC for "
                                + Microbot.cantReachTargetRetries + " times but failed. Please take a look at what is happening.");
                        return;
                    }
                    Rs2Walker.walkTo(Rs2Tile.getNearestWalkableTileWithLineOfSight(npc.getWorldLocation()), 0);
                    Microbot.pauseAllScripts = false;
                    Microbot.cantReachTargetRetries++;
                    return;
                } else {
                    Microbot.pauseAllScripts = false;
                    Microbot.cantReachTarget = false;
                    Microbot.cantReachTargetRetries = 0;
                }
            }

            NPCComposition npcComposition = Microbot.getClientThread().runOnClientThreadOptional(
                    () -> Microbot.getClient().getNpcDefinition(npc.getId())).orElse(null);

            if (npcComposition == null || npcComposition.getActions() == null) {
                Microbot.log("Error: Could not get NPC composition or actions for NPC: " + npc.getName());
                return;
            }

            int index = -1;
            String[] actions = npcComposition.getActions();

            if (action == null || action.isEmpty()) {
                OptionalInt optionalIndex = IntStream.range(0, actions.length)
                        .filter(i -> actions[i] != null && !actions[i].isEmpty())
                        .findFirst();

                if (optionalIndex.isPresent()) {
                    index = optionalIndex.getAsInt();
                    action = actions[index];
                }
            }
            else {
                String finalAction = action;
                OptionalInt optionalIndex = IntStream.range(0, actions.length)
                        .filter(i -> actions[i] != null && actions[i].equalsIgnoreCase(finalAction))
                        .findFirst();

                if (optionalIndex.isPresent()) {
                    index = optionalIndex.getAsInt();
                }
            }

            if (index == -1) {
                Microbot.log("Error: Action '" + action + "' not found for NPC: " + npc.getName());
                return;
            }

            MenuAction menuAction = Rs2Npc.getMenuAction(index);
            if (menuAction == null) {
                Microbot.log("Error: Could not get menu action for action '" + action + "' on NPC: " + npc.getName());
                return;
            }

            Microbot.doInvoke(new NewMenuEntry(0, 0, menuAction.getId(), npc.getIndex(), -1, npc.getName(), npc),
                    Rs2UiHelper.getActorClickbox(npc));

        } catch (Exception ex) {
            Microbot.log("Error interacting with NPC '" + npc.getName() + "' for action '" + action + "': " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void rightClickPhials(String action) {
        Rs2NpcModel phials = getPhials();
        if (phials != null) {
            rightClick(phials, action);
            Rs2Player.waitForWalking();
        } else {
            Microbot.log("Phials not found, retrying...");
        }
    }

    private boolean hasUnNotedBones() {
        return Rs2Inventory.hasUnNotedItem("bones");
    }

    private boolean hasNotedBones() {
        return Rs2Inventory.hasNotedItem("bones");
    }

    private void calculateState() {
        boolean inHouse = inHouse();
        boolean hasUnNotedBones = hasUnNotedBones();

        // Randomly trigger antiban state
        if (Rs2Random.between(0, 100) < 5) {
            state = KPOHState.ANTIBAN;
            return;
        }

        if (hasUnNotedBones) {
            state = inHouse ? KPOHState.BONES_ON_ALTAR : KPOHState.ENTER_HOUSE;
        } else {
            state = inHouse ? KPOHState.LEAVE_HOUSE : KPOHState.UNNOTE_BONES;
        }
    }

    public void leaveHouse() {
        Microbot.log("Attempting to leave house...");

        // We should only rely on using the settings menu if the portal is several rooms away from the portal. Bringing up 3 different interfaces when we can see the portal on screen is unnecessary.
        if (usePortal != null && usePortal) {
            int HOUSE_PORTAL_OBJECT = 4525;
            TileObject portalObject = Rs2GameObject.findObjectById(HOUSE_PORTAL_OBJECT);
            if (portalObject == null) {
                Microbot.log("Not in house, HOUSE_PORTAL_OBJECT not found.");
                return;
            }
            Rs2GameObject.interact(portalObject);
            Rs2Player.waitForWalking();
            return;
        }

        // Switch to Settings tab
        Rs2Tab.switchToSettingsTab();
        sleep(1200);

        // If the house options button is not visible, player is on Display or Sound settings, need to click Controls.
        if (!(Rs2Widget.isWidgetVisible(7602207))){
            Rs2Widget.clickWidget(7602243);
            sleep(600);
        }

        // Click House Options
        if (Rs2Widget.clickWidget(7602207)) {
            sleep(1200);
        } else {
            Microbot.log("House options button not found.");
            return;
        }

        // Click Leave House
        if (Rs2Widget.clickWidget(24248341)) {
            sleep(3000);
        } else {
            Microbot.log("Leave House button not found.");
        }
    }

    public void unnoteBones() {
        if (Microbot.getClient().getWidget(14352385) == null) {
            if (!Rs2Inventory.isItemSelected()) {
                Rs2Inventory.use("bones");
            } else {
                try {
                    interactWithPhials("Use");
                    Rs2Player.waitForWalking();
                } catch (Exception e) {
                    Microbot.log("Phials not found, retrying...");
                    sleep(3000);
                }
            }
        } else if (Microbot.getClient().getWidget(14352385) != null) {
            Rs2Keyboard.keyPress('3');
            Rs2Inventory.waitForInventoryChanges(2000);
        }
    }

    private void enterHouse() {
        // If we've already visited a house this session, use 'Visit-Last' on advertisement board
        if (visitedOnce) {
            try {
                Rs2GameObject.interact(ObjectID.HOUSE_ADVERTISEMENT, "Visit-Last");
            } catch (Exception e) {
                Microbot.log("Error interacting with advertisement board: " + e.getMessage());
                addNameToBlackList();
                visitedOnce = false;
                houseOwner = null;
                return;
            }
            sleep(2400, 3000);
            return;
        }

        boolean isAdvertisementWidgetOpen = Rs2Widget.isWidgetVisible(3407875);

        if (!isAdvertisementWidgetOpen) {
            Rs2GameObject.interact(ObjectID.HOUSE_ADVERTISEMENT, "View");
            sleep(1200, 1800);
        }

        Widget containerNames = Rs2Widget.getWidget(52, 9);
        Widget containerEnter = Rs2Widget.getWidget(52, 19);
        if (containerNames == null || containerNames.getChildren() == null) return;

        // Sort house advertisements by Gilded Altar availability
        Widget toggleArrow = Rs2Widget.getWidget(3407877);
        if (toggleArrow.getSpriteId() == 1050) {
            Rs2Widget.clickWidget(3407877);
            sleep(600, 1200);
        }

        // Get all names on house board and find the one with the smallest Y value
        if (containerNames.getChildren() != null) {
            int smallestOriginalY = Integer.MAX_VALUE; // Track the smallest OriginalY

            Widget[] children = containerNames.getChildren();

            for (int i = 0; i < children.length; i++) {
                Widget child = children[i];
                if (child.getText() == null || child.getText().isEmpty() || child.getText() == ""){
                    continue;
                }
                if (child.getText() != null) {
                    if (child.getOriginalY() < smallestOriginalY && !blacklistNames.contains(child.getText())) {
                        houseOwner = child.getText();
                        smallestOriginalY = child.getOriginalY();
                    }
                }
            }

            // Use playername at top of advertisement board as search criteria and find their Enter button
            Widget[] children2 = containerEnter.getChildren();
            for (int i = 0; i < children2.length; i++) {
                Widget child = children2[i];
                if (child == null || child.getOnOpListener() == null) {
                    continue;
                }
                Object[] listenerArray = child.getOnOpListener();
                boolean containsHouseOwner = Arrays.stream(listenerArray)
                        .filter(Objects::nonNull) // Ensure no null elements
                        .anyMatch(obj -> obj.toString().replace("\u00A0", " ").contains(houseOwner)); // Check if houseOwner is part of any listener object
                if (containsHouseOwner) {
                    targetWidget = child;
                    break;
                }
            }
            sleep(600, 1200);
            Rs2Widget.clickChildWidget(3407891, targetWidget.getIndex());
            visitedOnce = true;
            sleep(2400, 3000);
        }
    }

    public void bonesOnAltar() {
        if (portalCoords == null) {
            portalCoords = Rs2Player.getWorldLocation();
        }

        if (Rs2Player.isAnimating()) {
            Microbot.log("Player is currently animating, waiting...");
            return;
        }

        // Check if we have unnoted bones in inventory
        if (!hasUnNotedBones()) {
            Microbot.log("No unnoted bones left in inventory");
            if (!hasNotedBones()) {
                Microbot.log("No bones left at all, logging out and stopping script");
                shutdown();
                return;
            }
            // If we have noted bones but not unnoted, we need to change state
            return;
        }

        // Find altar using our comprehensive finder method
        TileObject altar = findAltarInHouse();
        if (altar == null) {
            Microbot.log("Error: Altar not found in house.");
            return;
        }

        // Try several methods to use bones on altar
        boolean success = false;

        // Method 1: Use the utility method if available
        try {
            success = Rs2Inventory.useUnNotedItemOnObject("bones", altar);
            if (success) {
                Microbot.log("Successfully used bones on altar (Method 1)");
            }
        } catch (Exception e) {
            Microbot.log("Method 1 failed: " + e.getMessage());
        }

        // Method 2: Manual selection if first method failed
        if (!success) {
            try {
                // Find all unnoted bones
                List<Rs2ItemModel> unnotedBones = Rs2Inventory.getInventoryItems().stream()
                        .filter(item -> item.getName().toLowerCase().contains("bones") && !item.isNoted())
                        .collect(Collectors.toList());

                if (unnotedBones.isEmpty()) {
                    Microbot.log("No unnoted bones found in inventory");
                    return;
                }

                // Select a random bone
                int randomIndex = Rs2Random.between(0, unnotedBones.size() - 1);
                Rs2ItemModel selectedBone = unnotedBones.get(randomIndex);

                // Use the bone on the altar
                if (!Rs2Inventory.isItemSelected()) {
                    Microbot.log("Using " + selectedBone.getName() + " on altar (Method 2)");
                    Rs2Inventory.interact(selectedBone.id, "Use");
                    sleep(300, 600);
                }

                Rs2GameObject.interact(altar);
                success = true;
                Microbot.log("Successfully used bones on altar (Method 2)");
            } catch (Exception e) {
                Microbot.log("Method 2 failed: " + e.getMessage());
            }
        }

        // Method 3: Direct menu option as fallback
        if (!success) {
            try {
                Microbot.log("Trying direct menu option (Method 3)");
                if (Rs2Tab.switchToInventoryTab()) {
                    sleep(300, 500);
                    for (Rs2ItemModel item : Rs2Inventory.getInventoryItems()) {
                        if (item.getName().toLowerCase().contains("bones") && !item.isNoted()) {
                            // Click using direct menu entry
                            MenuAction action = MenuAction.ITEM_USE_ON_GAME_OBJECT;
                            int itemId = item.id;
                            Microbot.doInvoke(new NewMenuEntry(altar.getCanvasLocation().getX(), altar.getCanvasLocation().getY(),
                                            action.getId(), altar.getId(), itemId, "Use", "Use " + item.getName() + " -> " + altar.getName(), Rs2GameObject.getGameObject("Altar")),
                                    Rs2UiHelper.getObjectClickbox(altar));
                            Microbot.log("Successfully used bones on altar (Method 3)");
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                Microbot.log("Method 3 failed: " + e.getMessage());
            }
        }

        // Wait for animation regardless of method used
        Rs2Player.waitForAnimation();

        // Store altar location for future reference
        if (altarCoords == null) {
            altarCoords = Rs2Player.getWorldLocation();
        }

        // If portal is more than 10 tiles from altar, use settings menu to leave. Else, just walk back to portal.
        if (usePortal == null) {
            usePortal = altarCoords.distanceTo(portalCoords) <= 10;
        }
    }

    /**
     * Finds an altar in the player's house by checking multiple known altar IDs
     * @return The altar object if found, null otherwise
     */
    private TileObject findAltarInHouse() {
        // All possible altar IDs in player-owned houses
        int[] altarIds = {
                ObjectID.ALTAR_13196,
                ObjectID.ALTAR_13197,
                ObjectID.ALTAR_13198,
                ObjectID.ALTAR_13199,
                13179, 13180, 13181, 13182, 13183,
                13184, 13185, 13186, 13187, 13188,
                13189, 13190, 13191, 13192, 13193,
                13194, 13195, 13196, 13197, 13198,
                13199, 40872, 40873, 40874, 40875,
                40876, 40877, 40878
        };

        // Loop through all possible altar IDs
        for (int id : altarIds) {
            TileObject altar = Rs2GameObject.findObjectById(id);
            if (altar != null) {
                Microbot.log("Found altar with ID: " + id);
                return altar;
            }
        }

        // If no altar found by ID, try finding by name as a fallback
        TileObject namedAltar = Rs2GameObject.getGameObject("Altar");
        if (namedAltar != null) {
            Microbot.log("Found altar by name search");
        }

        return namedAltar;
    }

    private void performAntiBan() {
        // Simple antiban actions
        int randomAction = Rs2Random.between(0, 4);

        switch (randomAction) {
            case 0:
                // Move mouse randomly
                Rs2Walker.walkTo(new WorldPoint(
                        Rs2Player.getWorldLocation().getX() + Rs2Random.between(-3, 3),
                        Rs2Player.getWorldLocation().getY() + Rs2Random.between(-3, 3),
                        0
                ));
                break;
            case 1:
                // Open a tab
                Rs2Tab.switchToInventoryTab();
                sleep(600, 1200);
                break;
            case 2:
                // Check skills
                Rs2Tab.switchToSkillsTab();
                sleep(1000, 2000);
                Rs2Tab.switchToInventoryTab();
                break;
            case 3:
                // Short pause
                sleep(1500, 3500);
                break;
            default:
                // Do nothing special
                break;
        }

        // Return to normal state calculation
        calculateState();
    }

    public void addNameToBlackList() {
        blacklistNames.add(houseOwner);
        Microbot.log("Blacklisted house owner: " + houseOwner);
    }

    private void applyAntiBanSettings() {
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.simulateFatigue = true;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.moveMouseOffScreen = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.dynamicIntensity = true;
        Rs2AntibanSettings.devDebug = false;
        Rs2AntibanSettings.moveMouseRandomly = true;
        Rs2AntibanSettings.microBreakDurationLow = 1;
        Rs2AntibanSettings.microBreakDurationHigh = 3;
        Rs2AntibanSettings.actionCooldownChance = 0.4;
        Rs2AntibanSettings.microBreakChance = 0.15;
        Rs2AntibanSettings.moveMouseRandomlyChance = 0.1;
    }
}