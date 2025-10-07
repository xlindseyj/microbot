package net.runelite.client.plugins.microbot.aiautonomous.training;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.aiautonomous.AiAutonomousConfig;
import net.runelite.client.plugins.microbot.aiautonomous.ai.KnowledgeManager;
import net.runelite.client.plugins.microbot.aiautonomous.core.GameStateAnalyzer;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class BaseModeTrainer {

    private final AiAutonomousConfig config;
    private final KnowledgeManager knowledgeManager;

    // F2P and P2P skill definitions
    private final Set<String> f2pSkills;
    private final Set<String> p2pSkills;
    private final Map<String, SkillTrainingData> skillData;

    // Current training state
    private String currentTargetSkill;
    private int currentTargetLevel;
    private Map<String, Integer> skillProgress;
    private List<String> trainingQueue;

    public BaseModeTrainer(AiAutonomousConfig config, KnowledgeManager knowledgeManager) {
        this.config = config;
        this.knowledgeManager = knowledgeManager;
        this.skillData = new HashMap<>();
        this.skillProgress = new HashMap<>();
        this.trainingQueue = new ArrayList<>();

        // Initialize F2P and P2P skills
        this.f2pSkills = initializeF2PSkills();
        this.p2pSkills = initializeP2PSkills();

        initializeSkillTrainingData();
        log.info("Base Mode Trainer initialized for {} account", config.accountType());
    }

    public BaseModeTrainingDecision planNextTraining(GameStateAnalyzer.GameState currentState) {
        try {
            log.debug("Planning next base mode training session");

            // Update current skill levels
            updateSkillProgress(currentState.getAllSkillLevels());

            // Check if current target is still valid
            if (currentTargetSkill != null && hasReachedTargetLevel()) {
                completeCurrentTraining();
            }

            // Select next skill to train
            String nextSkill = selectNextSkillToTrain();
            if (nextSkill == null) {
                return new BaseModeTrainingDecision(
                    TrainingAction.NO_TRAINING,
                    "All available skills at target base levels",
                    null, 0, ""
                );
            }

            // Set new target
            currentTargetSkill = nextSkill;
            currentTargetLevel = getNextBaseLevel(currentState.getAllSkillLevels().getOrDefault(nextSkill, 1));

            // Get training method
            String trainingMethod = getBestTrainingMethod(nextSkill, currentState);

            return new BaseModeTrainingDecision(
                TrainingAction.TRAIN_SKILL,
                String.format("Training %s to level %d using %s",
                    nextSkill, currentTargetLevel, trainingMethod),
                nextSkill, currentTargetLevel, trainingMethod
            );

        } catch (Exception e) {
            log.error("Error planning base mode training", e);
            return new BaseModeTrainingDecision(
                TrainingAction.NO_TRAINING,
                "Error in training planning",
                null, 0, ""
            );
        }
    }

    private Set<String> initializeF2PSkills() {
        return Set.of(
            "Attack", "Strength", "Defence", "Ranged", "Prayer", "Magic",
            "Runecrafting", "Crafting", "Mining", "Smithing", "Fishing",
            "Cooking", "Firemaking", "Woodcutting"
        );
    }

    private Set<String> initializeP2PSkills() {
        Set<String> allSkills = new HashSet<>(f2pSkills);
        allSkills.addAll(Set.of(
            "Agility", "Herblore", "Thieving", "Fletching", "Slayer",
            "Farming", "Construction", "Hunter"
        ));
        return allSkills;
    }

    private void initializeSkillTrainingData() {
        // F2P Skills Training Data
        skillData.put("Attack", new SkillTrainingData("Attack")
            .addF2PMethod("Barbarian Village", "Minotaurs", 1, 40)
            .addF2PMethod("Edgeville Dungeon", "Hill Giants", 40, 99)
            .addP2PMethod("Nightmare Zone", "AFK Training", 70, 99));

        skillData.put("Strength", new SkillTrainingData("Strength")
            .addF2PMethod("Barbarian Village", "Minotaurs", 1, 40)
            .addF2PMethod("Edgeville Dungeon", "Hill Giants", 40, 99)
            .addP2PMethod("Nightmare Zone", "AFK Training", 70, 99));

        skillData.put("Defence", new SkillTrainingData("Defence")
            .addF2PMethod("Barbarian Village", "Minotaurs", 1, 40)
            .addF2PMethod("Edgeville Dungeon", "Hill Giants", 40, 99)
            .addP2PMethod("Nightmare Zone", "AFK Training", 70, 99));

        skillData.put("Ranged", new SkillTrainingData("Ranged")
            .addF2PMethod("Lumbridge", "Goblins", 1, 10)
            .addF2PMethod("Barbarian Village", "Minotaurs", 10, 40)
            .addF2PMethod("Edgeville Dungeon", "Hill Giants", 40, 99)
            .addP2PMethod("Nightmare Zone", "AFK Training", 70, 99));

        skillData.put("Prayer", new SkillTrainingData("Prayer")
            .addF2PMethod("Port Sarim", "Bury bones", 1, 99)
            .addP2PMethod("Yanille", "Gilded altar", 1, 99));

        skillData.put("Magic", new SkillTrainingData("Magic")
            .addF2PMethod("Lumbridge", "Wind Strike", 1, 13)
            .addF2PMethod("Varrock", "High Alch", 55, 99)
            .addP2PMethod("Grand Exchange", "Enchanting", 7, 99));

        skillData.put("Runecrafting", new SkillTrainingData("Runecrafting")
            .addF2PMethod("Lumbridge Swamp", "Air runes", 1, 99)
            .addP2PMethod("Abyss", "Efficient runes", 50, 99));

        skillData.put("Crafting", new SkillTrainingData("Crafting")
            .addF2PMethod("Al Kharid", "Leather", 1, 99)
            .addP2PMethod("Superglass Make", "Glass", 77, 99));

        skillData.put("Mining", new SkillTrainingData("Mining")
            .addF2PMethod("Lumbridge Swamp", "Copper/Tin", 1, 15)
            .addF2PMethod("Varrock Mine", "Iron", 15, 99)
            .addP2PMethod("Motherlode Mine", "AFK Mining", 30, 99));

        skillData.put("Smithing", new SkillTrainingData("Smithing")
            .addF2PMethod("Lumbridge", "Bronze items", 1, 30)
            .addF2PMethod("Varrock", "Iron items", 30, 99)
            .addP2PMethod("Blast Furnace", "Efficient bars", 60, 99));

        skillData.put("Fishing", new SkillTrainingData("Fishing")
            .addF2PMethod("Lumbridge", "Shrimp/Anchovies", 1, 20)
            .addF2PMethod("Barbarian Village", "Trout/Salmon", 20, 99)
            .addP2PMethod("Fishing Guild", "Lobsters", 40, 99));

        skillData.put("Cooking", new SkillTrainingData("Cooking")
            .addF2PMethod("Lumbridge", "Shrimp", 1, 15)
            .addF2PMethod("Al Kharid", "Trout", 15, 99)
            .addP2PMethod("Hosidius", "Wines", 35, 99));

        skillData.put("Firemaking", new SkillTrainingData("Firemaking")
            .addF2PMethod("Lumbridge", "Normal logs", 1, 15)
            .addF2PMethod("Varrock", "Oak logs", 15, 99)
            .addP2PMethod("Wintertodt", "Group firemaking", 50, 99));

        skillData.put("Woodcutting", new SkillTrainingData("Woodcutting")
            .addF2PMethod("Lumbridge", "Normal trees", 1, 15)
            .addF2PMethod("Varrock", "Oak trees", 15, 99)
            .addP2PMethod("Woodcutting Guild", "Redwoods", 90, 99));

        // P2P-only skills
        skillData.put("Agility", new SkillTrainingData("Agility")
            .addP2PMethod("Gnome Stronghold", "Gnome course", 1, 99));

        skillData.put("Herblore", new SkillTrainingData("Herblore")
            .addP2PMethod("Barbarian Herblore", "Barbarian potions", 17, 99));

        skillData.put("Thieving", new SkillTrainingData("Thieving")
            .addP2PMethod("Lumbridge", "Men/Women", 1, 99));

        skillData.put("Fletching", new SkillTrainingData("Fletching")
            .addP2PMethod("Grand Exchange", "Arrow shafts", 1, 99));

        skillData.put("Slayer", new SkillTrainingData("Slayer")
            .addP2PMethod("Various", "Slayer tasks", 1, 99));

        skillData.put("Farming", new SkillTrainingData("Farming")
            .addP2PMethod("Various", "Tree runs", 1, 99));

        skillData.put("Construction", new SkillTrainingData("Construction")
            .addP2PMethod("Rimmington", "Oak planks", 15, 99));

        skillData.put("Hunter", new SkillTrainingData("Hunter")
            .addP2PMethod("Varrock", "Bird snaring", 1, 99));

        log.info("Initialized training data for {} skills", skillData.size());
    }

    private void updateSkillProgress(Map<String, Integer> currentLevels) {
        this.skillProgress = new HashMap<>(currentLevels);
    }

    private boolean hasReachedTargetLevel() {
        if (currentTargetSkill == null) return false;

        int currentLevel = skillProgress.getOrDefault(currentTargetSkill, 1);
        return currentLevel >= currentTargetLevel;
    }

    private void completeCurrentTraining() {
        if (currentTargetSkill != null && knowledgeManager != null) {
            int startLevel = currentTargetLevel - 10;
            knowledgeManager.storeSkillProgressExperience(
                currentTargetSkill,
                startLevel,
                currentTargetLevel,
                "base_mode_training",
                System.currentTimeMillis()
            );

            log.info("Completed base mode training: {} to level {}",
                currentTargetSkill, currentTargetLevel);
        }

        currentTargetSkill = null;
        currentTargetLevel = 0;
    }

    private String selectNextSkillToTrain() {
        Set<String> availableSkills = config.accountType() == AiAutonomousConfig.AccountType.F2P ?
            f2pSkills : p2pSkills;

        // Find the skill with the lowest base level
        String lowestSkill = null;
        int lowestBaseLevel = Integer.MAX_VALUE;

        for (String skill : availableSkills) {
            int currentLevel = skillProgress.getOrDefault(skill, 1);
            int baseLevel = getBaseLevel(currentLevel);

            if (baseLevel < lowestBaseLevel) {
                lowestBaseLevel = baseLevel;
                lowestSkill = skill;
            }
        }

        // If all skills are at the same base level, pick the one closest to next base
        if (lowestSkill == null) {
            for (String skill : availableSkills) {
                int currentLevel = skillProgress.getOrDefault(skill, 1);
                int nextBase = getNextBaseLevel(currentLevel);

                if (currentLevel < nextBase) {
                    return skill;
                }
            }
        }

        return lowestSkill;
    }

    private int getBaseLevel(int currentLevel) {
        return (currentLevel / 10) * 10;
    }

    private int getNextBaseLevel(int currentLevel) {
        int baseLevel = getBaseLevel(currentLevel);
        return baseLevel + 10;
    }

    private String getBestTrainingMethod(String skill, GameStateAnalyzer.GameState currentState) {
        SkillTrainingData data = skillData.get(skill);
        if (data == null) {
            return "unknown_method";
        }

        int currentLevel = currentState.getAllSkillLevels().getOrDefault(skill, 1);
        boolean isP2P = config.accountType() == AiAutonomousConfig.AccountType.P2P;

        // Get appropriate training methods
        List<TrainingMethod> methods = isP2P ? data.getAllMethods() : data.getF2PMethods();

        // Find best method for current level
        for (TrainingMethod method : methods) {
            if (currentLevel >= method.getMinLevel() && currentLevel <= method.getMaxLevel()) {
                return method.getMethodName();
            }
        }

        // Fallback to first available method
        return methods.isEmpty() ? "unknown_method" : methods.get(0).getMethodName();
    }

    public String getCurrentTrainingStatus() {
        if (currentTargetSkill == null) {
            return "Selecting next skill to train";
        }

        int currentLevel = skillProgress.getOrDefault(currentTargetSkill, 1);
        return String.format("Training %s: %d → %d",
            currentTargetSkill, currentLevel, currentTargetLevel);
    }

    public Map<String, Integer> getSkillProgress() {
        return new HashMap<>(skillProgress);
    }

    public List<String> getTrainingQueue() {
        return new ArrayList<>(trainingQueue);
    }

    // Helper classes
    public static class BaseModeTrainingDecision {
        private final TrainingAction action;
        private final String reasoning;
        private final String targetSkill;
        private final int targetLevel;
        private final String method;

        public BaseModeTrainingDecision(TrainingAction action, String reasoning,
                                      String targetSkill, int targetLevel, String method) {
            this.action = action;
            this.reasoning = reasoning;
            this.targetSkill = targetSkill;
            this.targetLevel = targetLevel;
            this.method = method;
        }

        public TrainingAction getAction() { return action; }
        public String getReasoning() { return reasoning; }
        public String getTargetSkill() { return targetSkill; }
        public int getTargetLevel() { return targetLevel; }
        public String getMethod() { return method; }
    }

    public enum TrainingAction {
        TRAIN_SKILL, NO_TRAINING, SWITCH_SKILL, PREPARE_TRAINING
    }

    private static class SkillTrainingData {
        private final String skillName;
        private final List<TrainingMethod> f2pMethods;
        private final List<TrainingMethod> p2pMethods;

        public SkillTrainingData(String skillName) {
            this.skillName = skillName;
            this.f2pMethods = new ArrayList<>();
            this.p2pMethods = new ArrayList<>();
        }

        public SkillTrainingData addF2PMethod(String location, String method, int minLevel, int maxLevel) {
            f2pMethods.add(new TrainingMethod(location, method, minLevel, maxLevel));
            return this;
        }

        public SkillTrainingData addP2PMethod(String location, String method, int minLevel, int maxLevel) {
            p2pMethods.add(new TrainingMethod(location, method, minLevel, maxLevel));
            return this;
        }

        public List<TrainingMethod> getF2PMethods() { return new ArrayList<>(f2pMethods); }
        public List<TrainingMethod> getP2PMethods() { return new ArrayList<>(p2pMethods); }

        public List<TrainingMethod> getAllMethods() {
            List<TrainingMethod> all = new ArrayList<>(f2pMethods);
            all.addAll(p2pMethods);
            return all;
        }
    }

    private static class TrainingMethod {
        private final String location;
        private final String methodName;
        private final int minLevel;
        private final int maxLevel;

        public TrainingMethod(String location, String methodName, int minLevel, int maxLevel) {
            this.location = location;
            this.methodName = methodName;
            this.minLevel = minLevel;
            this.maxLevel = maxLevel;
        }

        public String getLocation() { return location; }
        public String getMethodName() { return methodName; }
        public int getMinLevel() { return minLevel; }
        public int getMaxLevel() { return maxLevel; }
    }
}