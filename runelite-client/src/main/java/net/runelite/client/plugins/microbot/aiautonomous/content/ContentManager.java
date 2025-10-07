package net.runelite.client.plugins.microbot.aiautonomous.content;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.aiautonomous.AiAutonomousConfig;
import net.runelite.client.plugins.microbot.aiautonomous.core.GameStateAnalyzer;

import java.util.*;

@Slf4j
public class ContentManager {

    private final AiAutonomousConfig config;
    private final Map<String, LocationData> f2pLocations;
    private final Map<String, LocationData> p2pLocations;
    private final Map<String, ItemData> f2pItems;
    private final Map<String, ItemData> p2pItems;
    private final Map<String, QuestData> f2pQuests;
    private final Map<String, QuestData> p2pQuests;

    public ContentManager(AiAutonomousConfig config) {
        this.config = config;
        this.f2pLocations = new HashMap<>();
        this.p2pLocations = new HashMap<>();
        this.f2pItems = new HashMap<>();
        this.p2pItems = new HashMap<>();
        this.f2pQuests = new HashMap<>();
        this.p2pQuests = new HashMap<>();

        initializeF2PContent();
        initializeP2PContent();

        log.info("Content manager initialized for {} account - F2P: {} locations, {} quests | P2P: {} locations, {} quests",
                config.accountType(),
                f2pLocations.size(), f2pQuests.size(),
                p2pLocations.size(), p2pQuests.size());
    }

    public boolean isLocationAccessible(String locationName) {
        LocationData location = getLocationData(locationName);
        return location != null && isContentAccessible(location.isMembersOnly());
    }

    public boolean isItemAccessible(String itemName) {
        ItemData item = getItemData(itemName);
        return item != null && isContentAccessible(item.isMembersOnly());
    }

    public boolean isQuestAccessible(String questName) {
        QuestData quest = getQuestData(questName);
        return quest != null && isContentAccessible(quest.isMembersOnly());
    }

    public List<String> getAccessibleSkills() {
        List<String> skills = new ArrayList<>();

        // F2P skills
        skills.addAll(Arrays.asList(
            "Attack", "Strength", "Defence", "Ranged", "Prayer", "Magic",
            "Runecrafting", "Crafting", "Mining", "Smithing", "Fishing",
            "Cooking", "Firemaking", "Woodcutting"
        ));

        // Add P2P skills if members
        if (config.accountType() == AiAutonomousConfig.AccountType.P2P) {
            skills.addAll(Arrays.asList(
                "Agility", "Herblore", "Thieving", "Fletching", "Slayer",
                "Farming", "Construction", "Hunter"
            ));
        }

        return skills;
    }

    public List<String> getAccessibleLocations() {
        List<String> locations = new ArrayList<>(f2pLocations.keySet());

        if (config.accountType() == AiAutonomousConfig.AccountType.P2P) {
            locations.addAll(p2pLocations.keySet());
        }

        return locations;
    }

    public List<String> getAccessibleQuests() {
        List<String> quests = new ArrayList<>(f2pQuests.keySet());

        if (config.accountType() == AiAutonomousConfig.AccountType.P2P) {
            quests.addAll(p2pQuests.keySet());
        }

        return quests;
    }

    public List<String> getTrainingLocationsForSkill(String skill) {
        List<String> locations = new ArrayList<>();

        for (LocationData location : getAccessibleLocationData()) {
            if (location.getTrainingSkills().contains(skill)) {
                locations.add(location.getName());
            }
        }

        return locations;
    }

    public List<String> getQuestsForSkill(String skill) {
        List<String> quests = new ArrayList<>();

        for (QuestData quest : getAccessibleQuestData()) {
            if (quest.getSkillRewards().containsKey(skill)) {
                quests.add(quest.getName());
            }
        }

        return quests;
    }

    public List<String> getBestTrainingLocations(String skill, int currentLevel) {
        return getTrainingLocationsForSkill(skill).stream()
                .filter(location -> {
                    LocationData data = getLocationData(location);
                    return data != null &&
                           currentLevel >= data.getMinSkillLevel() &&
                           currentLevel <= data.getMaxSkillLevel();
                })
                .sorted((l1, l2) -> {
                    LocationData d1 = getLocationData(l1);
                    LocationData d2 = getLocationData(l2);
                    return Integer.compare(d2.getEfficiency(), d1.getEfficiency());
                })
                .collect(java.util.stream.Collectors.toList());
    }

    private boolean isContentAccessible(boolean membersOnly) {
        return !membersOnly || config.accountType() == AiAutonomousConfig.AccountType.P2P;
    }

    private LocationData getLocationData(String locationName) {
        LocationData location = f2pLocations.get(locationName);
        if (location == null) {
            location = p2pLocations.get(locationName);
        }
        return location;
    }

    private ItemData getItemData(String itemName) {
        ItemData item = f2pItems.get(itemName);
        if (item == null) {
            item = p2pItems.get(itemName);
        }
        return item;
    }

    private QuestData getQuestData(String questName) {
        QuestData quest = f2pQuests.get(questName);
        if (quest == null) {
            quest = p2pQuests.get(questName);
        }
        return quest;
    }

    private Collection<LocationData> getAccessibleLocationData() {
        List<LocationData> locations = new ArrayList<>(f2pLocations.values());
        if (config.accountType() == AiAutonomousConfig.AccountType.P2P) {
            locations.addAll(p2pLocations.values());
        }
        return locations;
    }

    private Collection<QuestData> getAccessibleQuestData() {
        List<QuestData> quests = new ArrayList<>(f2pQuests.values());
        if (config.accountType() == AiAutonomousConfig.AccountType.P2P) {
            quests.addAll(p2pQuests.values());
        }
        return quests;
    }

    private void initializeF2PContent() {
        // F2P Locations
        f2pLocations.put("Lumbridge", new LocationData("Lumbridge", false)
                .addTrainingSkill("Fishing", 1, 20, 60)
                .addTrainingSkill("Cooking", 1, 99, 70)
                .addTrainingSkill("Woodcutting", 1, 15, 50));

        f2pLocations.put("Varrock", new LocationData("Varrock", false)
                .addTrainingSkill("Mining", 15, 99, 80)
                .addTrainingSkill("Smithing", 1, 99, 85)
                .addTrainingSkill("Magic", 55, 99, 90));

        f2pLocations.put("Al Kharid", new LocationData("Al Kharid", false)
                .addTrainingSkill("Crafting", 1, 99, 75)
                .addTrainingSkill("Mining", 1, 15, 60));

        f2pLocations.put("Barbarian Village", new LocationData("Barbarian Village", false)
                .addTrainingSkill("Fishing", 20, 99, 80)
                .addTrainingSkill("Attack", 1, 40, 70)
                .addTrainingSkill("Strength", 1, 40, 70)
                .addTrainingSkill("Defence", 1, 40, 70));

        f2pLocations.put("Edgeville", new LocationData("Edgeville", false)
                .addTrainingSkill("Attack", 40, 99, 85)
                .addTrainingSkill("Strength", 40, 99, 85)
                .addTrainingSkill("Defence", 40, 99, 85));

        f2pLocations.put("Falador", new LocationData("Falador", false)
                .addTrainingSkill("Mining", 1, 99, 70));

        f2pLocations.put("Port Sarim", new LocationData("Port Sarim", false)
                .addTrainingSkill("Fishing", 1, 99, 65));

        f2pLocations.put("Draynor Village", new LocationData("Draynor Village", false)
                .addTrainingSkill("Fishing", 1, 20, 55));

        f2pLocations.put("Lumbridge Swamp", new LocationData("Lumbridge Swamp", false)
                .addTrainingSkill("Runecrafting", 1, 99, 60));

        // F2P Quests
        f2pQuests.put("Cook's Assistant", new QuestData("Cook's Assistant", false)
                .addSkillReward("Cooking", 300));

        f2pQuests.put("Sheep Shearer", new QuestData("Sheep Shearer", false)
                .addSkillReward("Crafting", 150));

        f2pQuests.put("The Restless Ghost", new QuestData("The Restless Ghost", false)
                .addSkillReward("Prayer", 1125));

        f2pQuests.put("Romeo & Juliet", new QuestData("Romeo & Juliet", false));

        f2pQuests.put("Doric's Quest", new QuestData("Doric's Quest", false)
                .addSkillReward("Mining", 1300));

        f2pQuests.put("Goblin Diplomacy", new QuestData("Goblin Diplomacy", false)
                .addSkillReward("Crafting", 200));

        f2pQuests.put("Ernest the Chicken", new QuestData("Ernest the Chicken", false));

        f2pQuests.put("Imp Catcher", new QuestData("Imp Catcher", false)
                .addSkillReward("Magic", 875));

        f2pQuests.put("Pirate's Treasure", new QuestData("Pirate's Treasure", false));

        f2pQuests.put("Prince Ali Rescue", new QuestData("Prince Ali Rescue", false));

        f2pQuests.put("Vampire Slayer", new QuestData("Vampire Slayer", false)
                .addSkillReward("Attack", 4825));

        f2pQuests.put("Demon Slayer", new QuestData("Demon Slayer", false)
                .addSkillReward("Attack", 2925));

        f2pQuests.put("Black Knights' Fortress", new QuestData("Black Knights' Fortress", false));

        f2pQuests.put("Knight's Sword", new QuestData("Knight's Sword", false)
                .addSkillReward("Smithing", 12725));

        f2pQuests.put("Witch's Potion", new QuestData("Witch's Potion", false)
                .addSkillReward("Magic", 325));

        f2pQuests.put("Dragon Slayer I", new QuestData("Dragon Slayer I", false)
                .addSkillReward("Strength", 18650)
                .addSkillReward("Defence", 18650));

        // F2P Items
        f2pItems.put("Bronze sword", new ItemData("Bronze sword", false, 1));
        f2pItems.put("Iron sword", new ItemData("Iron sword", false, 1));
        f2pItems.put("Steel sword", new ItemData("Steel sword", false, 5));
        f2pItems.put("Mithril sword", new ItemData("Mithril sword", false, 20));
        f2pItems.put("Adamant sword", new ItemData("Adamant sword", false, 30));
        f2pItems.put("Rune sword", new ItemData("Rune sword", false, 40));

        log.info("Initialized {} F2P locations, {} F2P quests, {} F2P items",
                f2pLocations.size(), f2pQuests.size(), f2pItems.size());
    }

    private void initializeP2PContent() {
        // P2P Locations
        p2pLocations.put("Gnome Stronghold", new LocationData("Gnome Stronghold", true)
                .addTrainingSkill("Agility", 1, 99, 90));

        p2pLocations.put("Seers' Village", new LocationData("Seers' Village", true)
                .addTrainingSkill("Agility", 60, 99, 95)
                .addTrainingSkill("Woodcutting", 15, 99, 85));

        p2pLocations.put("Canifis", new LocationData("Canifis", true)
                .addTrainingSkill("Agility", 40, 99, 85));

        p2pLocations.put("Ardougne", new LocationData("Ardougne", true)
                .addTrainingSkill("Thieving", 1, 99, 90));

        p2pLocations.put("Catherby", new LocationData("Catherby", true)
                .addTrainingSkill("Fishing", 35, 99, 90)
                .addTrainingSkill("Woodcutting", 30, 99, 85));

        p2pLocations.put("Motherlode Mine", new LocationData("Motherlode Mine", true)
                .addTrainingSkill("Mining", 30, 99, 95));

        p2pLocations.put("Blast Furnace", new LocationData("Blast Furnace", true)
                .addTrainingSkill("Smithing", 60, 99, 95));

        p2pLocations.put("Wintertodt", new LocationData("Wintertodt", true)
                .addTrainingSkill("Firemaking", 50, 99, 95));

        p2pLocations.put("Fishing Guild", new LocationData("Fishing Guild", true)
                .addTrainingSkill("Fishing", 68, 99, 95));

        p2pLocations.put("Woodcutting Guild", new LocationData("Woodcutting Guild", true)
                .addTrainingSkill("Woodcutting", 60, 99, 95));

        p2pLocations.put("Nightmare Zone", new LocationData("Nightmare Zone", true)
                .addTrainingSkill("Attack", 70, 99, 95)
                .addTrainingSkill("Strength", 70, 99, 95)
                .addTrainingSkill("Defence", 70, 99, 95)
                .addTrainingSkill("Ranged", 70, 99, 95)
                .addTrainingSkill("Magic", 70, 99, 95));

        // P2P Quests (just a few examples)
        p2pQuests.put("Waterfall Quest", new QuestData("Waterfall Quest", true)
                .addSkillReward("Attack", 13750)
                .addSkillReward("Strength", 13750));

        p2pQuests.put("Tree Gnome Village", new QuestData("Tree Gnome Village", true)
                .addSkillReward("Attack", 11450));

        p2pQuests.put("The Grand Tree", new QuestData("The Grand Tree", true)
                .addSkillReward("Attack", 18400)
                .addSkillReward("Agility", 7900));

        p2pQuests.put("Fight Arena", new QuestData("Fight Arena", true)
                .addSkillReward("Attack", 12175)
                .addSkillReward("Thieving", 2175));

        p2pQuests.put("Animal Magnetism", new QuestData("Animal Magnetism", true)
                .addSkillReward("Slayer", 1000)
                .addSkillReward("Woodcutting", 1000));

        // P2P Items
        p2pItems.put("Dragon sword", new ItemData("Dragon sword", true, 60));
        p2pItems.put("Whip", new ItemData("Whip", true, 70));
        p2pItems.put("Dragon scimitar", new ItemData("Dragon scimitar", true, 60));

        log.info("Initialized {} P2P locations, {} P2P quests, {} P2P items",
                p2pLocations.size(), p2pQuests.size(), p2pItems.size());
    }

    // Helper classes
    public static class LocationData {
        private final String name;
        private final boolean membersOnly;
        private final Map<String, SkillTrainingInfo> trainingSkills;
        private int minSkillLevel = 1;
        private int maxSkillLevel = 99;
        private int efficiency = 50;

        public LocationData(String name, boolean membersOnly) {
            this.name = name;
            this.membersOnly = membersOnly;
            this.trainingSkills = new HashMap<>();
        }

        public LocationData addTrainingSkill(String skill, int minLevel, int maxLevel, int efficiency) {
            trainingSkills.put(skill, new SkillTrainingInfo(minLevel, maxLevel, efficiency));
            this.minSkillLevel = Math.min(this.minSkillLevel, minLevel);
            this.maxSkillLevel = Math.max(this.maxSkillLevel, maxLevel);
            this.efficiency = Math.max(this.efficiency, efficiency);
            return this;
        }

        public String getName() { return name; }
        public boolean isMembersOnly() { return membersOnly; }
        public Set<String> getTrainingSkills() { return trainingSkills.keySet(); }
        public int getMinSkillLevel() { return minSkillLevel; }
        public int getMaxSkillLevel() { return maxSkillLevel; }
        public int getEfficiency() { return efficiency; }
    }

    public static class ItemData {
        private final String name;
        private final boolean membersOnly;
        private final int requiredLevel;

        public ItemData(String name, boolean membersOnly, int requiredLevel) {
            this.name = name;
            this.membersOnly = membersOnly;
            this.requiredLevel = requiredLevel;
        }

        public String getName() { return name; }
        public boolean isMembersOnly() { return membersOnly; }
        public int getRequiredLevel() { return requiredLevel; }
    }

    public static class QuestData {
        private final String name;
        private final boolean membersOnly;
        private final Map<String, Integer> skillRewards;

        public QuestData(String name, boolean membersOnly) {
            this.name = name;
            this.membersOnly = membersOnly;
            this.skillRewards = new HashMap<>();
        }

        public QuestData addSkillReward(String skill, int xp) {
            skillRewards.put(skill, xp);
            return this;
        }

        public String getName() { return name; }
        public boolean isMembersOnly() { return membersOnly; }
        public Map<String, Integer> getSkillRewards() { return new HashMap<>(skillRewards); }
    }

    private static class SkillTrainingInfo {
        private final int minLevel;
        private final int maxLevel;
        private final int efficiency;

        public SkillTrainingInfo(int minLevel, int maxLevel, int efficiency) {
            this.minLevel = minLevel;
            this.maxLevel = maxLevel;
            this.efficiency = efficiency;
        }

        public int getMinLevel() { return minLevel; }
        public int getMaxLevel() { return maxLevel; }
        public int getEfficiency() { return efficiency; }
    }
}