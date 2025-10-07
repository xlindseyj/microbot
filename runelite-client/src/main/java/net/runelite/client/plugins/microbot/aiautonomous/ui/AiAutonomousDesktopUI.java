package net.runelite.client.plugins.microbot.aiautonomous.ui;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.aiautonomous.AiAutonomousPlugin;
import net.runelite.client.plugins.microbot.aiautonomous.strategy.RealTimeStrategyAdjuster;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
public class AiAutonomousDesktopUI extends JFrame {

    private final AiAutonomousPlugin plugin;
    private Timer updateTimer;

    // UI Components
    private JLabel statusLabel;
    private JLabel strategyLabel;
    private JLabel sessionTimeLabel;
    private JLabel xpGainedLabel;
    private JProgressBar healthBar;
    private JProgressBar prayerBar;
    private JTable skillTable;
    private JTable eventTable;
    private JTextArea logArea;
    private JSlider riskToleranceSlider;
    private JSlider efficiencySlider;
    private JCheckBox enableCombatCheck;
    private JCheckBox enableTradingCheck;
    private JButton pauseButton;
    private JButton emergencyStopButton;

    // Data models
    private DefaultTableModel skillTableModel;
    private DefaultTableModel eventTableModel;

    public AiAutonomousDesktopUI(AiAutonomousPlugin plugin) {
        this.plugin = plugin;

        initializeUI();
        setupUpdateTimer();

        log.info("AI Autonomous Desktop UI initialized");
    }

    private void initializeUI() {
        setTitle("AI Autonomous Player - Control Panel");
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);

        // Set look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            log.warn("Failed to set system look and feel", e);
        }

        // Create main layout
        setLayout(new BorderLayout());

        // Create components
        createTopPanel();
        createCenterPanel();
        createBottomPanel();

        // Add window listener
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                setVisible(false);
            }
        });

        // Initial update
        updateDisplay();
    }

    private void createTopPanel() {
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));

        // Status section
        JPanel statusPanel = new JPanel(new GridLayout(2, 4, 10, 5));
        statusPanel.setBorder(new TitledBorder("System Status"));

        statusLabel = new JLabel("Status: Initializing...");
        strategyLabel = new JLabel("Strategy: Balanced");
        sessionTimeLabel = new JLabel("Session: 00:00:00");
        xpGainedLabel = new JLabel("XP Gained: 0");

        healthBar = new JProgressBar(0, 100);
        healthBar.setStringPainted(true);
        healthBar.setString("Health: 100%");
        healthBar.setForeground(Color.GREEN);

        prayerBar = new JProgressBar(0, 100);
        prayerBar.setStringPainted(true);
        prayerBar.setString("Prayer: 100%");
        prayerBar.setForeground(Color.CYAN);

        statusPanel.add(new JLabel("Status:"));
        statusPanel.add(statusLabel);
        statusPanel.add(new JLabel("Strategy:"));
        statusPanel.add(strategyLabel);
        statusPanel.add(new JLabel("Session Time:"));
        statusPanel.add(sessionTimeLabel);
        statusPanel.add(new JLabel("XP Gained:"));
        statusPanel.add(xpGainedLabel);

        // Health/Prayer bars
        JPanel barsPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        barsPanel.add(healthBar);
        barsPanel.add(prayerBar);

        topPanel.add(statusPanel, BorderLayout.CENTER);
        topPanel.add(barsPanel, BorderLayout.SOUTH);

        add(topPanel, BorderLayout.NORTH);
    }

    private void createCenterPanel() {
        JTabbedPane tabbedPane = new JTabbedPane();

        // Skills tab
        tabbedPane.addTab("Skills", createSkillsPanel());

        // Strategy tab
        tabbedPane.addTab("Strategy", createStrategyPanel());

        // Events tab
        tabbedPane.addTab("Events", createEventsPanel());

        // Logs tab
        tabbedPane.addTab("Logs", createLogsPanel());

        // Settings tab
        tabbedPane.addTab("Settings", createSettingsPanel());

        add(tabbedPane, BorderLayout.CENTER);
    }

    private JPanel createSkillsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Skills table
        String[] skillColumns = {"Skill", "Level", "XP", "Target", "Progress", "Efficiency"};
        skillTableModel = new DefaultTableModel(skillColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        skillTable = new JTable(skillTableModel);
        skillTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        skillTable.getTableHeader().setReorderingAllowed(false);

        // Set column widths
        skillTable.getColumnModel().getColumn(0).setPreferredWidth(100);
        skillTable.getColumnModel().getColumn(1).setPreferredWidth(60);
        skillTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        skillTable.getColumnModel().getColumn(3).setPreferredWidth(60);
        skillTable.getColumnModel().getColumn(4).setPreferredWidth(100);
        skillTable.getColumnModel().getColumn(5).setPreferredWidth(80);

        JScrollPane skillScrollPane = new JScrollPane(skillTable);
        skillScrollPane.setPreferredSize(new Dimension(500, 300));

        panel.add(skillScrollPane, BorderLayout.CENTER);

        // Base mode info
        if (isBaseModeActive()) {
            JPanel baseModePanel = new JPanel(new FlowLayout());
            baseModePanel.setBorder(new TitledBorder("Base Mode Progress"));

            JLabel baseModeLabel = new JLabel("Training skills to base 10 levels");
            baseModeLabel.setFont(baseModeLabel.getFont().deriveFont(Font.BOLD));
            baseModePanel.add(baseModeLabel);

            panel.add(baseModePanel, BorderLayout.SOUTH);
        }

        return panel;
    }

    private JPanel createStrategyPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Current strategy info
        JPanel currentStrategyPanel = new JPanel(new GridLayout(4, 2, 10, 5));
        currentStrategyPanel.setBorder(new TitledBorder("Current Strategy"));

        JLabel currentStrategyLabel = new JLabel("Balanced");
        JLabel adjustmentReasonLabel = new JLabel("Initial strategy");
        JLabel lastAdjustmentLabel = new JLabel("Never");
        JLabel performanceLabel = new JLabel("N/A");

        currentStrategyPanel.add(new JLabel("Active Strategy:"));
        currentStrategyPanel.add(currentStrategyLabel);
        currentStrategyPanel.add(new JLabel("Last Adjustment:"));
        currentStrategyPanel.add(lastAdjustmentLabel);
        currentStrategyPanel.add(new JLabel("Reason:"));
        currentStrategyPanel.add(adjustmentReasonLabel);
        currentStrategyPanel.add(new JLabel("Performance:"));
        currentStrategyPanel.add(performanceLabel);

        // Strategy parameters
        JPanel parametersPanel = new JPanel(new GridLayout(3, 2, 10, 5));
        parametersPanel.setBorder(new TitledBorder("Dynamic Parameters"));

        riskToleranceSlider = new JSlider(0, 100, 50);
        riskToleranceSlider.setMajorTickSpacing(25);
        riskToleranceSlider.setPaintTicks(true);
        riskToleranceSlider.setPaintLabels(true);

        efficiencySlider = new JSlider(0, 100, 75);
        efficiencySlider.setMajorTickSpacing(25);
        efficiencySlider.setPaintTicks(true);
        efficiencySlider.setPaintLabels(true);

        JSlider socialSlider = new JSlider(0, 100, 50);
        socialSlider.setMajorTickSpacing(25);
        socialSlider.setPaintTicks(true);
        socialSlider.setPaintLabels(true);

        parametersPanel.add(new JLabel("Risk Tolerance:"));
        parametersPanel.add(riskToleranceSlider);
        parametersPanel.add(new JLabel("Efficiency Focus:"));
        parametersPanel.add(efficiencySlider);
        parametersPanel.add(new JLabel("Social Interaction:"));
        parametersPanel.add(socialSlider);

        panel.add(currentStrategyPanel, BorderLayout.NORTH);
        panel.add(parametersPanel, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createEventsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Events table
        String[] eventColumns = {"Time", "Type", "Description", "Impact"};
        eventTableModel = new DefaultTableModel(eventColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        eventTable = new JTable(eventTableModel);
        eventTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        eventTable.getTableHeader().setReorderingAllowed(false);

        JScrollPane eventScrollPane = new JScrollPane(eventTable);
        eventScrollPane.setPreferredSize(new Dimension(500, 300));

        panel.add(eventScrollPane, BorderLayout.CENTER);

        // Clear events button
        JButton clearEventsButton = new JButton("Clear Events");
        clearEventsButton.addActionListener(e -> eventTableModel.setRowCount(0));

        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(clearEventsButton);

        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createLogsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setBackground(Color.BLACK);
        logArea.setForeground(Color.GREEN);

        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setPreferredSize(new Dimension(500, 300));
        logScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        panel.add(logScrollPane, BorderLayout.CENTER);

        // Clear logs button
        JButton clearLogsButton = new JButton("Clear Logs");
        clearLogsButton.addActionListener(e -> logArea.setText(""));

        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(clearLogsButton);

        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createSettingsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Feature toggles
        JPanel featuresPanel = new JPanel(new GridLayout(4, 2, 10, 5));
        featuresPanel.setBorder(new TitledBorder("Feature Controls"));

        enableCombatCheck = new JCheckBox("Enable Combat", plugin.getConfig().enableCombatActions());
        enableTradingCheck = new JCheckBox("Enable Trading", plugin.getConfig().enableTradingActions());
        JCheckBox enableQuestsCheck = new JCheckBox("Enable Quests", plugin.getConfig().enableQuestActions());
        JCheckBox enableBankingCheck = new JCheckBox("Enable Banking", plugin.getConfig().enableBankingActions());

        featuresPanel.add(enableCombatCheck);
        featuresPanel.add(enableTradingCheck);
        featuresPanel.add(enableQuestsCheck);
        featuresPanel.add(enableBankingCheck);

        // Account type
        JPanel accountPanel = new JPanel(new FlowLayout());
        accountPanel.setBorder(new TitledBorder("Account Type"));

        ButtonGroup accountGroup = new ButtonGroup();
        JRadioButton f2pRadio = new JRadioButton("F2P", plugin.getConfig().accountType().toString().equals("F2P"));
        JRadioButton p2pRadio = new JRadioButton("Members", plugin.getConfig().accountType().toString().equals("Members"));

        accountGroup.add(f2pRadio);
        accountGroup.add(p2pRadio);
        accountPanel.add(f2pRadio);
        accountPanel.add(p2pRadio);

        gbc.gridx = 0; gbc.gridy = 0; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(featuresPanel, gbc);

        gbc.gridy = 1;
        panel.add(accountPanel, gbc);

        return panel;
    }

    private void createBottomPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));

        // Control buttons
        JPanel buttonPanel = new JPanel(new FlowLayout());

        pauseButton = new JButton("Pause");
        pauseButton.setPreferredSize(new Dimension(100, 30));
        pauseButton.addActionListener(e -> togglePause());

        emergencyStopButton = new JButton("EMERGENCY STOP");
        emergencyStopButton.setPreferredSize(new Dimension(150, 30));
        emergencyStopButton.setBackground(Color.RED);
        emergencyStopButton.setForeground(Color.WHITE);
        emergencyStopButton.addActionListener(e -> emergencyStop());

        JButton settingsButton = new JButton("Open Settings");
        settingsButton.setPreferredSize(new Dimension(120, 30));

        JButton testConnectionButton = new JButton("Test Connections");
        testConnectionButton.setPreferredSize(new Dimension(130, 30));
        testConnectionButton.addActionListener(e -> testConnections());

        buttonPanel.add(pauseButton);
        buttonPanel.add(settingsButton);
        buttonPanel.add(testConnectionButton);
        buttonPanel.add(Box.createHorizontalStrut(20));
        buttonPanel.add(emergencyStopButton);

        bottomPanel.add(buttonPanel, BorderLayout.CENTER);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void setupUpdateTimer() {
        updateTimer = new Timer(2000, e -> updateDisplay()); // Update every 2 seconds
        updateTimer.start();
    }

    private void updateDisplay() {
        SwingUtilities.invokeLater(() -> {
            try {
                updateStatusLabels();
                updateHealthBars();
                updateSkillsTable();
                updateEventsTable();
                updateStrategyInfo();
                updateLogs();
            } catch (Exception e) {
                log.error("Error updating UI display", e);
            }
        });
    }

    private void updateStatusLabels() {
        if (plugin.isInitialized()) {
            statusLabel.setText("Status: " + plugin.getStatus());
            statusLabel.setForeground(Color.GREEN);
        } else {
            statusLabel.setText("Status: Initializing...");
            statusLabel.setForeground(Color.ORANGE);
        }

        // Update session time
        if (plugin.getGameStateManager() != null) {
            sessionTimeLabel.setText("Session: " + formatDuration(plugin.getGameStateManager().getSessionDuration()));
        }

        // Update XP gained (placeholder)
        xpGainedLabel.setText("XP Gained: " + (plugin.getActionsPerformed() * 100));
    }

    private void updateHealthBars() {
        // This would get actual HP/Prayer from game state
        healthBar.setValue(85); // Placeholder
        healthBar.setString("Health: 85%");

        prayerBar.setValue(60); // Placeholder
        prayerBar.setString("Prayer: 60%");
    }

    private void updateSkillsTable() {
        skillTableModel.setRowCount(0);

        // Get skill data from content manager
        if (plugin.getContentManager() != null) {
            for (String skill : plugin.getContentManager().getAccessibleSkills()) {
                Object[] row = {
                    skill,
                    "1", // Level placeholder
                    "0", // XP placeholder
                    "10", // Target placeholder
                    "0%", // Progress placeholder
                    "75%" // Efficiency placeholder
                };
                skillTableModel.addRow(row);
            }
        }
    }

    private void updateEventsTable() {
        // This would get events from the strategy adjuster
        // For now, just maintain current events
    }

    private void updateStrategyInfo() {
        if (plugin.getConfig() != null) {
            strategyLabel.setText("Strategy: " + plugin.getConfig().primaryGoal().toString());
        }
    }

    private void updateLogs() {
        // Add new log entries (this would come from a log capture system)
        if (logArea.getLineCount() > 1000) {
            // Clear old logs to prevent memory issues
            String text = logArea.getText();
            String[] lines = text.split("\n");
            StringBuilder newText = new StringBuilder();
            for (int i = Math.max(0, lines.length - 800); i < lines.length; i++) {
                newText.append(lines[i]).append("\n");
            }
            logArea.setText(newText.toString());
        }
    }

    private void togglePause() {
        // This would pause/resume the AI
        if (pauseButton.getText().equals("Pause")) {
            pauseButton.setText("Resume");
            pauseButton.setBackground(Color.ORANGE);
            addLogEntry("AI PAUSED by user");
        } else {
            pauseButton.setText("Pause");
            pauseButton.setBackground(null);
            addLogEntry("AI RESUMED by user");
        }
    }

    private void emergencyStop() {
        int result = JOptionPane.showConfirmDialog(
            this,
            "Are you sure you want to emergency stop the AI?",
            "Emergency Stop Confirmation",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );

        if (result == JOptionPane.YES_OPTION) {
            addLogEntry("EMERGENCY STOP ACTIVATED!");
            // This would trigger emergency stop in the plugin
        }
    }

    private void testConnections() {
        addLogEntry("Testing connections...");

        // This would run the connection test
        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                publish("Testing Ollama connection...");
                Thread.sleep(1000);
                publish("✓ Ollama: Connected");

                publish("Testing RAG system...");
                Thread.sleep(1000);
                publish("✓ RAG: Connected");

                publish("Testing Wiki integration...");
                Thread.sleep(500);
                publish("✓ Wiki: Connected");

                publish("All connections successful!");
                return null;
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                for (String message : chunks) {
                    addLogEntry(message);
                }
            }
        };

        worker.execute();
    }

    private void addLogEntry(String message) {
        String timestamp = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        logArea.append(String.format("[%s] %s\n", timestamp, message));
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private String formatDuration(java.time.Duration duration) {
        if (duration == null) return "00:00:00";

        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();

        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private boolean isBaseModeActive() {
        return plugin.getConfig().primaryGoal().toString().contains("Base Mode") ||
               plugin.getConfig().trainingMode().toString().contains("Base Mode");
    }

    public void showUI() {
        SwingUtilities.invokeLater(() -> {
            if (!isVisible()) {
                setVisible(true);
                toFront();
                addLogEntry("AI Autonomous Desktop UI launched");
                addLogEntry("Account Type: " + plugin.getConfig().accountType());
                addLogEntry("Training Mode: " + plugin.getConfig().trainingMode());
                if (isBaseModeActive()) {
                    addLogEntry("Base Mode active - Training skills in 10-level increments");
                }
            } else {
                toFront(); // Just bring to front if already visible
            }
        });
    }

    public void hideUI() {
        SwingUtilities.invokeLater(() -> {
            setVisible(false);
            if (updateTimer != null) {
                updateTimer.stop();
            }
        });
    }

    @Override
    public void dispose() {
        if (updateTimer != null) {
            updateTimer.stop();
        }
        super.dispose();
        log.info("AI Autonomous Desktop UI disposed");
    }
}