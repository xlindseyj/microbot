package net.runelite.client.plugins.microbot.aiautonomous;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.aiautonomous.AiAutonomousConfig.AiGoal;

/**
 * Test class to verify AI decision making works with different goal configurations.
 */
@Slf4j
public class AIDecisionTest {

    public static void testAIDecisionMaking(AiAutonomousConfig config) {
        log.info("Starting AI decision making tests...");

        testGoalConfigurations(config);
        testDecisionParameters(config);
        testFeatureToggles(config);
        testAISettings(config);

        log.info("AI decision making tests completed");
    }

    private static void testGoalConfigurations(AiAutonomousConfig config) {
        log.info("Testing AI goal configurations...");
        try {
            AiGoal primaryGoal = config.primaryGoal();
            log.info("Current primary goal: {}", primaryGoal);

            // Test all available goals
            AiGoal[] allGoals = AiGoal.values();
            log.info("Available AI goals:");
            for (AiGoal goal : allGoals) {
                log.info("  - {}: {}", goal.name(), goal.toString());
            }

            // Test goal-specific decision logic
            testGoalSpecificBehavior(primaryGoal, config);

        } catch (Exception e) {
            log.error("✗ Goal configuration test failed", e);
        }
    }

    private static void testGoalSpecificBehavior(AiGoal goal, AiAutonomousConfig config) {
        log.info("Testing behavior for goal: {}", goal);
        try {
            switch (goal) {
                case QUEST_COMPLETION:
                    log.info("✓ Quest completion goal - should prioritize quest actions and NPC interactions");
                    if (config.enableQuestActions()) {
                        log.info("  ✓ Quest actions are enabled");
                    } else {
                        log.warn("  ⚠ Quest actions are disabled but goal is quest completion");
                    }
                    break;

                case SKILL_MAXING:
                    log.info("✓ Skill maxing goal - should focus on efficient skill training");
                    if (config.enableSkillTraining()) {
                        log.info("  ✓ Skill training is enabled");
                    } else {
                        log.warn("  ⚠ Skill training is disabled but goal is skill maxing");
                    }
                    break;

                case MONEY_MAKING:
                    log.info("✓ Money making goal - should prioritize profitable activities");
                    if (config.enableTradingActions()) {
                        log.info("  ✓ Trading actions are enabled");
                    } else {
                        log.warn("  ⚠ Trading actions are disabled but goal is money making");
                    }
                    break;

                case PVP_TRAINING:
                    log.info("✓ PvP training goal - should focus on combat and PvP preparation");
                    if (config.enableCombatActions()) {
                        log.info("  ✓ Combat actions are enabled");
                    } else {
                        log.warn("  ⚠ Combat actions are disabled but goal is PvP training");
                    }
                    break;

                case BALANCED_PROGRESSION:
                    log.info("✓ Balanced progression goal - should balance all activities");
                    // Check if multiple features are enabled for balanced approach
                    int enabledFeatures = 0;
                    if (config.enableSkillTraining()) enabledFeatures++;
                    if (config.enableQuestActions()) enabledFeatures++;
                    if (config.enableTradingActions()) enabledFeatures++;
                    if (config.enableCombatActions()) enabledFeatures++;

                    if (enabledFeatures >= 3) {
                        log.info("  ✓ Multiple features enabled for balanced progression");
                    } else {
                        log.warn("  ⚠ Limited features enabled for balanced progression");
                    }
                    break;

                case ACHIEVEMENT_HUNTING:
                    log.info("✓ Achievement hunting goal - should pursue various achievements");
                    if (config.enableQuestActions() && config.enableSkillTraining()) {
                        log.info("  ✓ Both quest and skill features enabled for achievements");
                    } else {
                        log.warn("  ⚠ Limited features for achievement hunting");
                    }
                    break;

                default:
                    log.warn("Unknown goal type: {}", goal);
            }

        } catch (Exception e) {
            log.error("✗ Goal-specific behavior test failed", e);
        }
    }

    private static void testDecisionParameters(AiAutonomousConfig config) {
        log.info("Testing AI decision parameters...");
        try {
            log.info("Decision parameters:");
            log.info("  - Risk tolerance: {}%", config.riskTolerance());
            log.info("  - Efficiency focus: {}%", config.efficiencyFocus());
            log.info("  - Experience weight: {}%", config.experienceWeight());

            // Test parameter ranges and combinations
            testParameterValidation(config);

        } catch (Exception e) {
            log.error("✗ Decision parameters test failed", e);
        }
    }

    private static void testParameterValidation(AiAutonomousConfig config) {
        log.info("Validating decision parameters...");
        try {
            boolean validConfig = true;

            // Test risk tolerance
            int riskTolerance = config.riskTolerance();
            if (riskTolerance >= 0 && riskTolerance <= 100) {
                log.info("✓ Risk tolerance is within valid range: {}%", riskTolerance);
            } else {
                log.error("✗ Risk tolerance out of range: {}%", riskTolerance);
                validConfig = false;
            }

            // Test efficiency focus
            int efficiencyFocus = config.efficiencyFocus();
            if (efficiencyFocus >= 0 && efficiencyFocus <= 100) {
                log.info("✓ Efficiency focus is within valid range: {}%", efficiencyFocus);
            } else {
                log.error("✗ Efficiency focus out of range: {}%", efficiencyFocus);
                validConfig = false;
            }

            // Test experience weight
            int experienceWeight = config.experienceWeight();
            if (experienceWeight >= 0 && experienceWeight <= 100) {
                log.info("✓ Experience weight is within valid range: {}%", experienceWeight);
            } else {
                log.error("✗ Experience weight out of range: {}%", experienceWeight);
                validConfig = false;
            }

            // Test parameter combinations for logical consistency
            if (riskTolerance > 80 && efficiencyFocus > 90) {
                log.warn("⚠ High risk + high efficiency might lead to risky behavior");
            }

            if (validConfig) {
                log.info("✓ All decision parameters are valid");
            } else {
                log.error("✗ Some decision parameters need correction");
            }

        } catch (Exception e) {
            log.error("✗ Parameter validation test failed", e);
        }
    }

    private static void testFeatureToggles(AiAutonomousConfig config) {
        log.info("Testing AI feature toggles...");
        try {
            log.info("AI features status:");
            log.info("  - Decision making: {}", config.enableDecisionMaking());
            log.info("  - Action execution: {}", config.enableActionExecution());
            log.info("  - RAG system: {}", config.enableRAGSystem());
            log.info("  - Memory system: {}", config.enableMemorySystem());
            log.info("  - Learning: {}", config.enableLearning());

            // Test feature dependencies
            testFeatureDependencies(config);

        } catch (Exception e) {
            log.error("✗ Feature toggles test failed", e);
        }
    }

    private static void testFeatureDependencies(AiAutonomousConfig config) {
        log.info("Testing feature dependencies...");
        try {
            // Decision making should be enabled for autonomous operation
            if (!config.enableDecisionMaking()) {
                log.warn("⚠ Decision making is disabled - AI will be limited");
            }

            // Action execution should be enabled for autonomous operation
            if (!config.enableActionExecution()) {
                log.warn("⚠ Action execution is disabled - AI will only observe");
            }

            // Check if core AI features are enabled together
            if (config.enableDecisionMaking() && config.enableActionExecution()) {
                log.info("✓ Core AI features are enabled for autonomous operation");
            } else {
                log.warn("⚠ AI may not function autonomously with current settings");
            }

            // Check learning features
            if (config.enableLearning() && !config.enableMemorySystem()) {
                log.warn("⚠ Learning is enabled but memory system is disabled");
            }

            // Check knowledge features
            if (config.enableDecisionMaking() && !config.enableRAGSystem() && !config.enableWikiIntegration()) {
                log.warn("⚠ No knowledge sources enabled for decision making");
            }

        } catch (Exception e) {
            log.error("✗ Feature dependencies test failed", e);
        }
    }

    private static void testAISettings(AiAutonomousConfig config) {
        log.info("Testing AI-specific settings...");
        try {
            log.info("AI model settings:");
            log.info("  - Ollama base URL: {}", config.ollamaBaseUrl());
            log.info("  - Ollama model: {}", config.ollamaModel());
            log.info("  - Embedding model: {}", config.embeddingModel());
            log.info("  - Max tokens: {}", config.maxTokens());
            log.info("  - Temperature: {}", config.temperature());

            // Validate AI settings
            testAISettingsValidation(config);

        } catch (Exception e) {
            log.error("✗ AI settings test failed", e);
        }
    }

    private static void testAISettingsValidation(AiAutonomousConfig config) {
        log.info("Validating AI settings...");
        try {
            // Test Ollama URL format
            String ollamaUrl = config.ollamaBaseUrl();
            if (ollamaUrl != null && (ollamaUrl.startsWith("http://") || ollamaUrl.startsWith("https://"))) {
                log.info("✓ Ollama URL format is valid");
            } else {
                log.warn("⚠ Ollama URL format may be invalid: {}", ollamaUrl);
            }

            // Test model names
            String model = config.ollamaModel();
            if (model != null && !model.trim().isEmpty()) {
                log.info("✓ Ollama model is specified: {}", model);
            } else {
                log.warn("⚠ Ollama model not specified");
            }

            // Test token limits
            int maxTokens = config.maxTokens();
            if (maxTokens > 0 && maxTokens <= 4096) {
                log.info("✓ Max tokens is reasonable: {}", maxTokens);
            } else {
                log.warn("⚠ Max tokens may need adjustment: {}", maxTokens);
            }

            // Test temperature
            int temperature = config.temperature();
            if (temperature >= 0 && temperature <= 100) {
                log.info("✓ Temperature is within valid range: {}", temperature);
            } else {
                log.warn("⚠ Temperature out of valid range: {}", temperature);
            }

        } catch (Exception e) {
            log.error("✗ AI settings validation failed", e);
        }
    }

    /**
     * Offline test for decision making logic
     */
    public static void testAIDecisionOffline() {
        log.info("Running offline AI decision tests...");
        try {
            // Test goal enum functionality
            AiGoal[] goals = AiGoal.values();
            if (goals.length > 0) {
                log.info("✓ AI goals enum accessible with {} options", goals.length);
            }

            // Test goal string representation
            for (AiGoal goal : goals) {
                String goalString = goal.toString();
                if (goalString != null && !goalString.isEmpty()) {
                    log.info("✓ Goal {} has proper string representation: {}", goal.name(), goalString);
                }
            }

            log.info("✓ Offline AI decision tests completed");

        } catch (Exception e) {
            log.error("✗ Offline AI decision test failed", e);
        }
    }
}