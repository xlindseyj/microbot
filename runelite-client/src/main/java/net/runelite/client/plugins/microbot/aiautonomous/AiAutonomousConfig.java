package net.runelite.client.plugins.microbot.aiautonomous;

import net.runelite.client.config.*;

@ConfigGroup("aiautonomous")
public interface AiAutonomousConfig extends Config {

    // Main Control
    @ConfigItem(
            keyName = "enablePlugin",
            name = "Enable AI Autonomous Player",
            description = "Enable the AI autonomous player to take control"
    )
    default boolean enablePlugin() {
        return false;
    }

    @ConfigItem(
            keyName = "debugMode",
            name = "Debug Mode",
            description = "Show detailed debug information and AI decisions"
    )
    default boolean debugMode() {
        return false;
    }

    @ConfigItem(
            keyName = "showOverlay",
            name = "Show Overlay",
            description = "Show the AI status overlay on screen"
    )
    default boolean showOverlay() {
        return true;
    }

    // Feature Toggles
    @ConfigSection(
            name = "Feature Controls",
            description = "Enable or disable specific AI features",
            position = 0
    )
    String featuresSection = "features";

    @ConfigItem(
            keyName = "enableDecisionMaking",
            name = "Enable AI Decision Making",
            description = "Allow the AI to make autonomous decisions",
            section = featuresSection
    )
    default boolean enableDecisionMaking() {
        return true;
    }

    @ConfigItem(
            keyName = "enableActionExecution",
            name = "Enable Action Execution",
            description = "Allow the AI to execute actions in the game",
            section = featuresSection
    )
    default boolean enableActionExecution() {
        return true;
    }

    @ConfigItem(
            keyName = "enableRAGSystem",
            name = "Enable RAG Knowledge System",
            description = "Use the RAG system for knowledge retrieval",
            section = featuresSection
    )
    default boolean enableRAGSystem() {
        return true;
    }

    @ConfigItem(
            keyName = "enableMemorySystem",
            name = "Enable Memory System",
            description = "Allow the AI to store and recall memories",
            section = featuresSection
    )
    default boolean enableMemorySystem() {
        return true;
    }

    @ConfigItem(
            keyName = "enableCombatActions",
            name = "Enable Combat Actions",
            description = "Allow the AI to engage in combat",
            section = featuresSection
    )
    default boolean enableCombatActions() {
        return false;
    }

    @ConfigItem(
            keyName = "enableTradingActions",
            name = "Enable Trading Actions",
            description = "Allow the AI to trade items and use shops",
            section = featuresSection
    )
    default boolean enableTradingActions() {
        return true;
    }

    @ConfigItem(
            keyName = "enableSkillTraining",
            name = "Enable Skill Training",
            description = "Allow the AI to train skills automatically",
            section = featuresSection
    )
    default boolean enableSkillTraining() {
        return true;
    }

    @ConfigItem(
            keyName = "enableQuestActions",
            name = "Enable Quest Actions",
            description = "Allow the AI to interact with NPCs and complete quests",
            section = featuresSection
    )
    default boolean enableQuestActions() {
        return true;
    }

    @ConfigItem(
            keyName = "enableMovement",
            name = "Enable Movement",
            description = "Allow the AI to move around the game world",
            section = featuresSection
    )
    default boolean enableMovement() {
        return true;
    }

    @ConfigItem(
            keyName = "enableBankingActions",
            name = "Enable Banking Actions",
            description = "Allow the AI to use banks for item management",
            section = featuresSection
    )
    default boolean enableBankingActions() {
        return true;
    }

    @ConfigItem(
            keyName = "enableInventoryManagement",
            name = "Enable Inventory Management",
            description = "Allow the AI to manage inventory items",
            section = featuresSection
    )
    default boolean enableInventoryManagement() {
        return true;
    }

    @ConfigItem(
            keyName = "enableEmergencyActions",
            name = "Enable Emergency Actions",
            description = "Allow the AI to perform emergency actions (teleporting, eating, etc.)",
            section = featuresSection
    )
    default boolean enableEmergencyActions() {
        return true;
    }

    @ConfigItem(
            keyName = "enableKnowledgeSharing",
            name = "Enable Knowledge Sharing",
            description = "Allow the AI to save and share learned knowledge",
            section = featuresSection
    )
    default boolean enableKnowledgeSharing() {
        return true;
    }

    @ConfigItem(
            keyName = "enablePerformanceOptimization",
            name = "Enable Performance Optimization",
            description = "Use performance optimizations that may reduce AI capabilities",
            section = featuresSection
    )
    default boolean enablePerformanceOptimization() {
        return false;
    }

    // Ollama Configuration
    @ConfigSection(
            name = "Ollama Settings",
            description = "Configuration for Ollama AI integration",
            position = 1
    )
    String ollamaSection = "ollama";

    @ConfigItem(
            keyName = "ollamaBaseUrl",
            name = "Ollama Base URL",
            description = "Base URL for the Ollama instance",
            section = ollamaSection
    )
    default String ollamaBaseUrl() {
        return "http://localhost:11434";
    }

    @ConfigItem(
            keyName = "ollamaModel",
            name = "Ollama Model",
            description = "AI model to use for decision making",
            section = ollamaSection
    )
    default String ollamaModel() {
        return "llama3";
    }

    @ConfigItem(
            keyName = "embeddingModel",
            name = "Embedding Model",
            description = "Model to use for text embeddings",
            section = ollamaSection
    )
    default String embeddingModel() {
        return "nomic-embed-text";
    }

    @ConfigItem(
            keyName = "maxTokens",
            name = "Max Tokens",
            description = "Maximum tokens for AI responses",
            section = ollamaSection
    )
    default int maxTokens() {
        return 1000;
    }

    @ConfigItem(
            keyName = "temperature",
            name = "Temperature",
            description = "AI creativity level (0.0-1.0)",
            section = ollamaSection
    )
    @Range(min = 0, max = 100)
    default int temperature() {
        return 70; // 0.7
    }

    // RAG System Configuration
    @ConfigSection(
            name = "RAG System",
            description = "Retrieval Augmented Generation configuration",
            position = 2
    )
    String ragSection = "rag";

    @ConfigItem(
            keyName = "ragSystemType",
            name = "RAG System Type",
            description = "Choose between Chroma vector database or Neo4j graph database",
            section = ragSection
    )
    default RagSystemType ragSystemType() {
        return RagSystemType.NEO4J;
    }

    @ConfigItem(
            keyName = "ragSystemUrl",
            name = "RAG System URL",
            description = "URL for the RAG system. Currently using LoadBalancer IP for Neo4j access",
            section = ragSection
    )
    default String ragSystemUrl() {
        return ragSystemType() == RagSystemType.NEO4J ?
            "http://100.105.178.55:7474" : "http://localhost:8000";
    }

    @ConfigItem(
            keyName = "chromaCollectionName",
            name = "Chroma Collection Name",
            description = "Chroma collection name for RuneScape knowledge",
            section = ragSection
    )
    default String chromaCollectionName() {
        return "runescape_knowledge";
    }

    @ConfigItem(
            keyName = "neo4jDatabase",
            name = "Neo4j Database",
            description = "Neo4j database name for RuneScape knowledge",
            section = ragSection
    )
    default String neo4jDatabase() {
        return "runescapeknowledge";
    }

    @ConfigItem(
            keyName = "neo4jUsername",
            name = "Neo4j Username",
            description = "Username for Neo4j authentication",
            section = ragSection
    )
    default String neo4jUsername() {
        return "neo4j";
    }

    @ConfigItem(
            keyName = "neo4jPassword",
            name = "Neo4j Password",
            description = "Password for Neo4j authentication",
            section = ragSection
    )
    default String neo4jPassword() {
        return "runescape2025";
    }

    @ConfigItem(
            keyName = "maxSearchResults",
            name = "Max Search Results",
            description = "Maximum number of knowledge entries to retrieve",
            section = ragSection
    )
    default int maxSearchResults() {
        return 10;
    }

    @ConfigItem(
            keyName = "similarityThreshold",
            name = "Similarity Threshold",
            description = "Minimum similarity score for knowledge retrieval (0-100)",
            section = ragSection
    )
    @Range(min = 0, max = 100)
    default int similarityThreshold() {
        return 60;
    }

    // Learning and Memory
    @ConfigSection(
            name = "Learning & Memory",
            description = "AI learning and memory configuration",
            position = 3
    )
    String learningSection = "learning";

    @ConfigItem(
            keyName = "enableLearning",
            name = "Enable Learning",
            description = "Allow the AI to learn from experiences and update knowledge",
            section = learningSection
    )
    default boolean enableLearning() {
        return true;
    }

    @ConfigItem(
            keyName = "memoryRetentionDays",
            name = "Memory Retention (Days)",
            description = "How many days to retain episodic memories",
            section = learningSection
    )
    default int memoryRetentionDays() {
        return 30;
    }

    @ConfigItem(
            keyName = "autoSaveInterval",
            name = "Auto-save Interval (Minutes)",
            description = "How often to auto-save learning progress",
            section = learningSection
    )
    default int autoSaveInterval() {
        return 10;
    }

    @ConfigItem(
            keyName = "experienceWeight",
            name = "Experience Weight",
            description = "How much to weight recent experiences in decision making (0-100)",
            section = learningSection
    )
    @Range(min = 0, max = 100)
    default int experienceWeight() {
        return 30;
    }

    // Game Strategy
    @ConfigSection(
            name = "Game Strategy",
            description = "AI gameplay strategy configuration",
            position = 4
    )
    String strategySection = "strategy";

    @ConfigItem(
            keyName = "primaryGoal",
            name = "Primary Goal",
            description = "Main objective for the AI player",
            section = strategySection
    )
    default AiGoal primaryGoal() {
        return AiGoal.BALANCED_PROGRESSION;
    }

    @ConfigItem(
            keyName = "accountType",
            name = "Account Type",
            description = "Free-to-play or Members account",
            section = strategySection
    )
    default AccountType accountType() {
        return AccountType.F2P;
    }

    @ConfigItem(
            keyName = "trainingMode",
            name = "Training Mode",
            description = "How to approach skill training",
            section = strategySection
    )
    default TrainingMode trainingMode() {
        return TrainingMode.BALANCED;
    }

    @ConfigItem(
            keyName = "riskTolerance",
            name = "Risk Tolerance",
            description = "How much risk the AI should take (0-100)",
            section = strategySection
    )
    @Range(min = 0, max = 100)
    default int riskTolerance() {
        return 30;
    }

    @ConfigItem(
            keyName = "efficiencyFocus",
            name = "Efficiency Focus",
            description = "How much to prioritize efficiency over variety (0-100)",
            section = strategySection
    )
    @Range(min = 0, max = 100)
    default int efficiencyFocus() {
        return 70;
    }

    @ConfigItem(
            keyName = "maxSessionTime",
            name = "Max Session Time (Hours)",
            description = "Maximum continuous play time before forced break",
            section = strategySection
    )
    default int maxSessionTime() {
        return 4;
    }

    // Safety and Anti-ban
    @ConfigSection(
            name = "Safety Settings",
            description = "Anti-ban and safety configuration",
            position = 5
    )
    String safetySection = "safety";

    @ConfigItem(
            keyName = "useAntibanMeasures",
            name = "Use Anti-ban Measures",
            description = "Enable built-in anti-ban behaviors",
            section = safetySection
    )
    default boolean useAntibanMeasures() {
        return true;
    }

    @ConfigItem(
            keyName = "humanlikeBehavior",
            name = "Human-like Behavior Level",
            description = "How human-like the AI should behave (0-100)",
            section = safetySection
    )
    @Range(min = 0, max = 100)
    default int humanlikeBehavior() {
        return 80;
    }

    @ConfigItem(
            keyName = "randomActionChance",
            name = "Random Action Chance",
            description = "Chance of performing random human-like actions (0-100)",
            section = safetySection
    )
    @Range(min = 0, max = 100)
    default int randomActionChance() {
        return 15;
    }

    @ConfigItem(
            keyName = "emergencyStop",
            name = "Emergency Stop Phrase",
            description = "Chat phrase that will immediately stop the AI",
            section = safetySection
    )
    default String emergencyStop() {
        return "STOP AI NOW";
    }

    // Wiki Integration
    @ConfigSection(
            name = "Wiki Integration",
            description = "Old School RuneScape Wiki integration",
            position = 6
    )
    String wikiSection = "wiki";

    @ConfigItem(
            keyName = "enableWikiIntegration",
            name = "Enable Wiki Integration",
            description = "Enable fetching information from OSRS Wiki",
            section = wikiSection
    )
    default boolean enableWikiIntegration() {
        return true;
    }

    @ConfigItem(
            keyName = "wikiCacheTime",
            name = "Wiki Cache Time (Hours)",
            description = "How long to cache wiki information",
            section = wikiSection
    )
    default int wikiCacheTime() {
        return 24;
    }

    @ConfigItem(
            keyName = "maxWikiRequests",
            name = "Max Wiki Requests/Hour",
            description = "Rate limit for wiki API requests",
            section = wikiSection
    )
    default int maxWikiRequests() {
        return 100;
    }

    enum AiGoal {
        QUEST_COMPLETION("Complete Quests"),
        SKILL_MAXING("Max All Skills"),
        MONEY_MAKING("Make Money"),
        PVP_TRAINING("PvP Training"),
        BALANCED_PROGRESSION("Balanced Progression"),
        ACHIEVEMENT_HUNTING("Achievement Hunting"),
        BASE_MODE("Base Mode - 10 Levels Per Skill");

        private final String displayName;

        AiGoal(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    enum AccountType {
        F2P("Free-to-Play"),
        P2P("Members");

        private final String displayName;

        AccountType(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    enum TrainingMode {
        BALANCED("Balanced Training"),
        BASE_MODE("Base Mode (10 levels at a time)"),
        EFFICIENT("Most Efficient Methods"),
        AFK("AFK-Friendly Methods"),
        QUEST_FOCUSED("Quest Requirements Focus");

        private final String displayName;

        TrainingMode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    enum RagSystemType {
        CHROMA("Chroma Vector Database"),
        NEO4J("Neo4j Graph Database");

        private final String displayName;

        RagSystemType(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}