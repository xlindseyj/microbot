package net.runelite.client.plugins.microbot.KromitePlugins.kfalconry;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.KromitePlugins.kfalconry.scripts.DefaultScript;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.input.KeyManager;
import net.runelite.client.util.HotkeyListener;

import javax.inject.Inject;
import java.util.logging.Logger;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@PluginDescriptor(
        name = "Kromite's Falconry",
        description = "Automates hunting kebbits with falcons in Piscatoris",
        tags = {"hunter", "falconry", "kebbit", "kromite"},
        enabledByDefault = false
)
public class KFalconryPlugin extends Plugin {
    @Inject
    private KFalconryConfig config;

    @Inject
    private KFalconryOverlay overlay;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private KeyManager keyManager;

    private DefaultScript script;
    private Logger log = Logger.getLogger(KFalconryPlugin.class.getName());
    private boolean running = false;
    private boolean initialized = false;
    private boolean scriptInitialized = false;

    @Provides
    KFalconryConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(KFalconryConfig.class);
    }

    private final HotkeyListener hotkeyListener = new HotkeyListener(() -> config.hotkey()) {
        @Override
        public void hotkeyPressed() {
            toggleScript();
        }
    };

    @Override
    protected void startUp() {
        overlayManager.add(overlay);
        keyManager.registerKeyListener(hotkeyListener);
        script = new DefaultScript();

        if (script == null) {
            log.info("Failed to initialize Kromite's Falconry script");
            stopScript();
            return;
        }

        initialized = true;
        scriptInitialized = script.onStart();

        if (!scriptInitialized) {
            log.info("Failed to start Kromite's Falconry plugin");
            stopScript();
        } else {
            startScript();
            log.info("Kromite's Falconry plugin started");
        }
    }

    @Override
    protected void shutDown() {
        overlayManager.remove(overlay);
        keyManager.unregisterKeyListener(hotkeyListener);
        stopScript();
        script = null;
        initialized = false;
        log.info("Kromite's Falconry plugin stopped");
    }

    public void startScript() {
        if (!initialized || running) return;

        running = true;
        Microbot.status = "Starting Falconry...";
        script.run(config);
        log.info("Falconry script started");
    }

    public void stopScript() {
        if (!running) return;

        running = false;
        Microbot.status = "Stopped";
        if (script != null) {
            script.shutdown();
        }
        log.info("Falconry script stopped");
    }

    public void toggleScript() {
        if (running) {
            stopScript();
        } else {
            startScript();
        }
    }

    // This method can be used by menu option entries to toggle the script
    public void onMenuOptionClicked() {
        toggleScript();
    }
}