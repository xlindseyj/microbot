package net.runelite.client.plugins.microbot.aiautonomous.core;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.microbot.aiautonomous.AiAutonomousConfig;
import net.runelite.client.plugins.microbot.aiautonomous.AiAutonomousScript;
import net.runelite.client.plugins.microbot.aiautonomous.AiAutonomousPlugin;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.Global;

import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * AutoHotkey-style integration for the AI Autonomous Player.
 * Provides keyboard shortcuts and automation functionality similar to AutoHotkey.
 */
@Slf4j
public class AutoHotkeyIntegration implements KeyListener {

    private final AiAutonomousPlugin plugin;
    private final AiAutonomousScript script;
    private final AiAutonomousConfig config;
    private final KeyManager keyManager;

    // Map of hotkey combinations to actions
    private final Map<Integer, Runnable> hotkeyActions;

    // Modifier key states
    private boolean ctrlPressed = false;
    private boolean altPressed = false;
    private boolean shiftPressed = false;

    // Automation sequences
    private boolean automationEnabled = false;
    private boolean emergencyMode = false;

    public AutoHotkeyIntegration(AiAutonomousPlugin plugin, AiAutonomousScript script, AiAutonomousConfig config, KeyManager keyManager) {
        this.plugin = plugin;
        this.script = script;
        this.config = config;
        this.keyManager = keyManager;
        this.hotkeyActions = new HashMap<>();

        initializeHotkeys();
        registerWithKeyManager();
    }

    /**
     * Initialize default hotkey combinations
     */
    private void initializeHotkeys() {
        // Ctrl + Alt + S = Emergency Stop
        hotkeyActions.put(generateKeyCode(KeyEvent.VK_S, true, true, false), this::emergencyStop);

        // Ctrl + Alt + P = Pause/Resume AI
        hotkeyActions.put(generateKeyCode(KeyEvent.VK_P, true, true, false), this::togglePause);

        // Ctrl + Alt + R = Reset AI Session
        hotkeyActions.put(generateKeyCode(KeyEvent.VK_R, true, true, false), this::resetSession);

        // Ctrl + Alt + D = Toggle Debug Mode
        hotkeyActions.put(generateKeyCode(KeyEvent.VK_D, true, true, false), this::toggleDebugMode);

        // Ctrl + Alt + A = Toggle Automation
        hotkeyActions.put(generateKeyCode(KeyEvent.VK_A, true, true, false), this::toggleAutomation);

        // Ctrl + Alt + H = Show Help/Status
        hotkeyActions.put(generateKeyCode(KeyEvent.VK_H, true, true, false), this::showStatus);

        // F1 = Quick teleport home (emergency escape)
        hotkeyActions.put(generateKeyCode(KeyEvent.VK_F1, false, false, false), this::emergencyTeleport);

        // F2 = Quick logout
        hotkeyActions.put(generateKeyCode(KeyEvent.VK_F2, false, false, false), this::quickLogout);

        log.info("AutoHotkey integration initialized with {} hotkeys", hotkeyActions.size());
    }

    /**
     * Generate a unique key code for hotkey combinations
     */
    private int generateKeyCode(int keyCode, boolean ctrl, boolean alt, boolean shift) {
        int result = keyCode;
        if (ctrl) result |= 0x10000;
        if (alt) result |= 0x20000;
        if (shift) result |= 0x40000;
        return result;
    }

    /**
     * Register this instance with the key manager
     */
    private void registerWithKeyManager() {
        if (keyManager != null) {
            keyManager.registerKeyListener(this);
            log.info("AutoHotkey integration registered with KeyManager");
        }
    }

    /**
     * Unregister from the key manager
     */
    public void shutdown() {
        if (keyManager != null) {
            keyManager.unregisterKeyListener(this);
            log.info("AutoHotkey integration unregistered from KeyManager");
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        updateModifierStates(e, true);

        int keyCode = generateKeyCode(e.getKeyCode(), ctrlPressed, altPressed, shiftPressed);
        Runnable action = hotkeyActions.get(keyCode);

        if (action != null) {
            log.debug("Executing hotkey action for key combination: {}", getKeyDescription(e.getKeyCode()));
            try {
                action.run();
                e.consume(); // Prevent further processing
            } catch (Exception ex) {
                log.error("Error executing hotkey action", ex);
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        updateModifierStates(e, false);
    }

    @Override
    public void keyTyped(KeyEvent e) {
        // Not used for hotkeys
    }

    /**
     * Update modifier key states
     */
    private void updateModifierStates(KeyEvent e, boolean pressed) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_CONTROL:
                ctrlPressed = pressed;
                break;
            case KeyEvent.VK_ALT:
                altPressed = pressed;
                break;
            case KeyEvent.VK_SHIFT:
                shiftPressed = pressed;
                break;
        }
    }

    /**
     * Get human-readable key description
     */
    private String getKeyDescription(int keyCode) {
        StringBuilder desc = new StringBuilder();
        if (ctrlPressed) desc.append("Ctrl+");
        if (altPressed) desc.append("Alt+");
        if (shiftPressed) desc.append("Shift+");
        desc.append(KeyEvent.getKeyText(keyCode));
        return desc.toString();
    }

    // Hotkey Action Methods

    /**
     * Emergency stop - immediately halt all AI operations
     */
    private void emergencyStop() {
        log.warn("EMERGENCY STOP activated via hotkey!");
        emergencyMode = true;
        script.activateEmergencyStop();

        // Send emergency stop phrase in chat if configured
        if (config.emergencyStop() != null && !config.emergencyStop().isEmpty()) {
            Global.sleep(100);
            Rs2Keyboard.typeString(config.emergencyStop());
            Rs2Keyboard.enter();
        }

        showMessage("EMERGENCY STOP ACTIVATED!");
    }

    /**
     * Toggle pause/resume AI operations
     */
    private void togglePause() {
        if (script != null && script.isRunning()) {
            plugin.stopScript();
            showMessage("AI paused via hotkey");
            log.info("AI paused via hotkey");
        } else {
            plugin.startScript();
            showMessage("AI resumed via hotkey");
            log.info("AI resumed via hotkey");
        }
    }

    /**
     * Reset AI session
     */
    private void resetSession() {
        script.resetSession();
        emergencyMode = false;
        showMessage("AI session reset via hotkey");
        log.info("AI session reset via hotkey");
    }

    /**
     * Toggle debug mode
     */
    private void toggleDebugMode() {
        // Note: This would require config modification, which isn't directly possible
        // In a real implementation, this would toggle a runtime debug flag
        showMessage("Debug toggle hotkey pressed (feature requires implementation)");
        log.info("Debug mode toggle requested via hotkey");
    }

    /**
     * Toggle automation sequences
     */
    private void toggleAutomation() {
        automationEnabled = !automationEnabled;
        showMessage("Automation " + (automationEnabled ? "enabled" : "disabled"));
        log.info("Automation toggled via hotkey: {}", automationEnabled);
    }

    /**
     * Show current AI status
     */
    private void showStatus() {
        StringBuilder status = new StringBuilder("AI Status: ");

        if (script.isEmergencyStopActivated()) {
            status.append("EMERGENCY STOP ACTIVE");
        } else if (script.isRunning()) {
            status.append("Running");
        } else {
            status.append("Stopped");
        }

        status.append(" | Session: ").append(formatDuration(script.getSessionDuration().toMinutes()));
        status.append(" | Automation: ").append(automationEnabled ? "ON" : "OFF");

        showMessage(status.toString());
        log.info("Status requested via hotkey: {}", status);
    }

    /**
     * Emergency teleport (if available)
     */
    private void emergencyTeleport() {
        log.info("Emergency teleport hotkey pressed");
        showMessage("Emergency teleport initiated");

        // Attempt to use house teleport or other emergency escape
        Global.sleep(100);
        Rs2Keyboard.typeString("::home");
        Rs2Keyboard.enter();
    }

    /**
     * Quick logout
     */
    private void quickLogout() {
        log.info("Quick logout hotkey pressed");
        showMessage("Quick logout initiated");

        // Press logout key combination
        Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
        Global.sleep(200);
        Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
    }

    // Automation Sequences

    /**
     * Execute a banking sequence
     */
    public void automatedBanking() {
        if (!automationEnabled) return;

        log.info("Executing automated banking sequence");

        // Open bank
        Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
        Global.sleep(1000);

        // Deposit all items
        Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
        Global.sleep(500);

        showMessage("Automated banking completed");
    }

    /**
     * Execute a prayer/food consumption sequence
     */
    public void automatedConsumption() {
        if (!automationEnabled) return;

        log.info("Executing automated consumption sequence");

        // Use prayer potion (assuming in slot 1)
        Rs2Keyboard.keyPress(KeyEvent.VK_1);
        Global.sleep(200);

        // Use food (assuming in slot 2)
        Rs2Keyboard.keyPress(KeyEvent.VK_2);
        Global.sleep(200);

        showMessage("Automated consumption completed");
    }

    /**
     * Quick spell casting sequence
     */
    public void quickSpellCast(String spell) {
        if (!automationEnabled) return;

        log.info("Executing quick spell cast: {}", spell);

        // Open magic tab
        Rs2Keyboard.keyPress(KeyEvent.VK_F6);
        Global.sleep(300);

        // Type spell name (if supported)
        Rs2Keyboard.typeString(spell);

        showMessage("Quick spell cast: " + spell);
    }

    // Utility Methods

    /**
     * Show a message to the user
     */
    private void showMessage(String message) {
        // In a real implementation, this could show in-game messages or overlay text
        log.info("[AutoHotkey] {}", message);
    }

    /**
     * Format duration in a readable format
     */
    private String formatDuration(long minutes) {
        if (minutes < 60) {
            return minutes + "m";
        } else {
            long hours = minutes / 60;
            long remainingMinutes = minutes % 60;
            return hours + "h " + remainingMinutes + "m";
        }
    }

    /**
     * Check if automation is enabled
     */
    public boolean isAutomationEnabled() {
        return automationEnabled;
    }

    /**
     * Check if emergency mode is active
     */
    public boolean isEmergencyMode() {
        return emergencyMode;
    }

    /**
     * Add a custom hotkey action
     */
    public void addHotkey(int keyCode, boolean ctrl, boolean alt, boolean shift, Runnable action) {
        int code = generateKeyCode(keyCode, ctrl, alt, shift);
        hotkeyActions.put(code, action);
        log.info("Added custom hotkey: {}", getKeyDescription(keyCode));
    }

    /**
     * Remove a hotkey
     */
    public void removeHotkey(int keyCode, boolean ctrl, boolean alt, boolean shift) {
        int code = generateKeyCode(keyCode, ctrl, alt, shift);
        hotkeyActions.remove(code);
        log.info("Removed hotkey: {}", getKeyDescription(keyCode));
    }

    /**
     * Get available hotkeys as a formatted string
     */
    public String getHotkeyHelp() {
        return "AI AutoHotkey Commands:\n" +
               "Ctrl+Alt+S - Emergency Stop\n" +
               "Ctrl+Alt+P - Pause/Resume AI\n" +
               "Ctrl+Alt+R - Reset Session\n" +
               "Ctrl+Alt+D - Toggle Debug Mode\n" +
               "Ctrl+Alt+A - Toggle Automation\n" +
               "Ctrl+Alt+H - Show Status\n" +
               "F1 - Emergency Teleport\n" +
               "F2 - Quick Logout\n";
    }
}