package net.runelite.client.plugins.KromitePlugins.kpoh.scripts;

import net.runelite.api.MenuAction;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectID;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.KromitePlugins.kpoh.KPOHConfig;
import net.runelite.client.plugins.KromitePlugins.kpoh.enums.GildedAltarPlayerState;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
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
import java.util.stream.IntStream;

import static net.runelite.client.plugins.microbot.util.npc.Rs2Npc.getMenuAction;
import static net.runelite.client.plugins.microbot.util.npc.Rs2Npc.hasLineOfSight;

public class DefaultScript extends Script {

    public Widget targetWidget;
    public String houseOwner;

    public WorldPoint portalCoords;
    public WorldPoint altarCoords;
    public Boolean usePortal;
    public boolean visitedOnce;
    List<String> blacklistNames = new ArrayList<>();

    public static GildedAltarPlayerState state = GildedAltarPlayerState.IDLE;

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
            System.out.println("Phials not found, retrying...");
        }
    }

    private void rightClick(Rs2NpcModel npc, String action) {
        Microbot.status = action + " " + npc.getName();
        try {
            if (Microbot.isCantReachTargetDetectionEnabled && Microbot.cantReachTarget) {
                if (!hasLineOfSight(npc)) {
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

            MenuAction menuAction = getMenuAction(index);
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
            System.out.println("Phials not found, retrying...");
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

        // If we have unNoted bones:
        // If we're in the house, use bones on altar. Else, enter the portal
        // If we don't have unNoted bones:
        // If we're in the house, leave house. Else, talk to Phials
        if (hasUnNotedBones) {
            state = inHouse ? GildedAltarPlayerState.BONES_ON_ALTAR : GildedAltarPlayerState.ENTER_HOUSE;
        } else {
            state = inHouse ? GildedAltarPlayerState.LEAVE_HOUSE : GildedAltarPlayerState.UNNOTE_BONES;
        }
    }


    public boolean run(KPOHConfig config) {
        blacklistNames = new ArrayList<>();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) {
                    return;
                }
                if (!Rs2Inventory.hasItem(995)) {
                    Microbot.showMessage("No gp found in your inventory");
                    shutdown();
                    return;
                }
                if (!hasNotedBones() && !hasUnNotedBones()) {
                    Microbot.showMessage("No bones found in your inventory");
                    shutdown();
                    return;
                }

                if (Microbot.isGainingExp) return;

                calculateState();

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
                }
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    public void leaveHouse() {
        System.out.println("Attempting to leave house...");

        // We should only rely on using the settings menu if the portal is several rooms away from the portal. Bringing up 3 different interfaces when we can see the portal on screen is unnecessary.
        if (usePortal) {
            int HOUSE_PORTAL_OBJECT = 4525;
            TileObject portalObject = Rs2GameObject.findObjectById(HOUSE_PORTAL_OBJECT);
            if (portalObject == null) {
                System.out.println("Not in house, HOUSE_PORTAL_OBJECT not found.");
                return;
            }
            Rs2GameObject.interact(portalObject);
            Rs2Player.waitForWalking();
            return;
        }

        // Switch to Settings tab
        Rs2Tab.switchToSettingsTab();
        sleep(1200);


        //If the house options button is not visible, player is on Display or Sound settings, need to click Controls.
        if (!(Rs2Widget.isWidgetVisible(7602207))){
            Rs2Widget.clickWidget(7602243);
            sleep(600);
        }

        // Click House Options
        if (Rs2Widget.clickWidget(7602207)) {
            sleep(1200);
        } else {
            System.out.println("House Options button not found.");
            return;
        }

        // Click Leave House
        if (Rs2Widget.clickWidget(24248341)) {
            sleep(3000);
        } else {
            System.out.println("Leave House button not found.");
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
                    System.out.println("Phials not found, retrying...");
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
            Rs2GameObject.interact(ObjectID.HOUSE_ADVERTISEMENT, "Visit-Last");
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

        //Sort house advertisements by Gilded Altar availability
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
                if (child.getText() == null || child.getText().isEmpty()|| child.getText() == ""){
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
        if(portalCoords == null){
            portalCoords = Rs2Player.getWorldLocation();
        }

        if (Rs2Player.isAnimating())  {
            return;
        }

        TileObject altar;

        altar = Rs2GameObject.findObjectById(ObjectID.ALTAR_40878);
        if (altar == null) {
            altar = Rs2GameObject.findObjectById(ObjectID.ALTAR_13197);
        }

        Rs2Inventory.useUnNotedItemOnObject("bones", altar);
        Rs2Player.waitForAnimation();

        // Use bones on the altar if it's valid
        if(altarCoords == null){
            altarCoords = Rs2Player.getWorldLocation();
        }
        // If portal is more than 10 tiles from altar, use settings menu to leave. Else, just walk back to portal.
        if(usePortal == null){
            usePortal = altarCoords.distanceTo(portalCoords) <= 10;
        }
    }

    public void addNameToBlackList() {
        blacklistNames.add(houseOwner);
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
        Rs2AntibanSettings.microBreakDurationLow = 3;
        Rs2AntibanSettings.microBreakDurationHigh = 15;
        Rs2AntibanSettings.actionCooldownChance = 0.4;
        Rs2AntibanSettings.microBreakChance = 0.15;
        Rs2AntibanSettings.moveMouseRandomlyChance = 0.1;
    }
}
