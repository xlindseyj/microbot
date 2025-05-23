package net.runelite.client.plugins.microbot.KromitePlugins.kfalconry;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.KromitePlugins.kfalconry.scripts.DefaultScript;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;

import javax.inject.Inject;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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
    private boolean running = false;
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> scriptFuture;

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
        executor = Executors.newSingleThreadScheduledExecutor();
        Microbot.status = "Plugin started - press hotkey to begin";
    }

    @Override
    protected void shutDown() {
        overlayManager.remove(overlay);
        keyManager.unregisterKeyListener(hotkeyListener);
        stopScript();

        if (executor != null) {
            executor.shutdown();
        }
    }

    public void startScript() {
        if (running) return;

        running = true;
        Microbot.status = "Starting Falconry...";

        if (script.onStart()) {
            scriptFuture = executor.scheduleWithFixedDelay(() -> {
                if (Microbot.isLoggedIn()) {
                    script.run(config);
                }
            }, 0, 600, TimeUnit.MILLISECONDS);
            Microbot.log("Falconry script started");
        } else {
            running = false;
            Microbot.status = "Failed to start script";
        }
    }

    public void stopScript() {
        if (!running) return;

        running = false;
        Microbot.status = "Stopped";

        if (scriptFuture != null) {
            scriptFuture.cancel(false);
        }

        if (script != null) {
            script.shutdown();
        }

        Microbot.log("Falconry script stopped");
    }

    public void toggleScript() {
        if (running) {
            stopScript();
        } else {
            startScript();
        }
    }
}