package dev.kspog.pokelite.ui;

import dev.kspog.pokelite.api.ClientCapability;
import dev.kspog.pokelite.api.ClientConnectionState;
import dev.kspog.pokelite.api.ClientEvents;
import dev.kspog.pokelite.api.EventBus;
import dev.kspog.pokelite.api.ExperienceSnapshot;
import dev.kspog.pokelite.api.GameSnapshot;
import dev.kspog.pokelite.core.DefaultClientApi;
import dev.kspog.pokelite.game.PokeMmoLauncher;
import dev.kspog.pokelite.game.WindowsGameEmbedder;
import dev.kspog.pokelite.gamedata.GameDataField;
import dev.kspog.pokelite.gamedata.GameDataService;
import dev.kspog.pokelite.gamedata.OcrGameDataProvider;
import dev.kspog.pokelite.plugin.PokeLitePlugin;
import dev.kspog.pokelite.plugin.PluginManager;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class PokeLiteFrame extends JFrame {
    private static final Logger LOGGER = Logger.getLogger(PokeLiteFrame.class.getName());

    private static final String PLUGINS_CARD = "plugins";
    private static final String DATA_CARD = "data";
    private static final String SETTINGS_CARD = "settings";
    private static final String LOGS_CARD = "logs";

    private final PluginManager pluginManager;
    private final DefaultClientApi clientApi;
    private final EventBus eventBus;
    private final PokeMmoLauncher launcher;
    private final WindowsGameEmbedder embedder = new WindowsGameEmbedder();
    private final ExecutorService backgroundExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "pokelite-background");
        thread.setDaemon(true);
        return thread;
    });

    private final GameHostPanel gameHostPanel = new GameHostPanel();
    private final OcrGameDataProvider ocrProvider;
    private final JPanel toolPanel = new JPanel(new CardLayout());
    private final JTextArea logArea = new JTextArea();
    private final Map<String, JButton> navigationButtons = new java.util.LinkedHashMap<>();
    private final List<EventBus.Subscription> eventSubscriptions = new ArrayList<>();

    private final JLabel connectionValue = valueLabel();
    private final JLabel providerValue = valueLabel();
    private final JLabel capabilityValue = valueLabel();
    private final JLabel moneyValue = valueLabel();
    private final JLabel moneyChangeValue = valueLabel();
    private final JLabel experienceValue = valueLabel();
    private final JLabel experienceGainedValue = valueLabel();
    private final JLabel gameDataStatusValue = valueLabel();

    private String visibleTool;
    private volatile Process gameProcess;

    public PokeLiteFrame(
        Path projectRoot,
        PluginManager pluginManager,
        DefaultClientApi clientApi,
        EventBus eventBus
    ) {
        super("PokeLite");
        this.pluginManager = Objects.requireNonNull(pluginManager);
        this.clientApi = Objects.requireNonNull(clientApi);
        this.eventBus = Objects.requireNonNull(eventBus);
        this.launcher = new PokeMmoLauncher(projectRoot.resolve("poke"));
        this.ocrProvider = new OcrGameDataProvider(gameHostPanel.getCanvas());

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(960, 640));
        setSize(1280, 800);
        setLocationRelativeTo(null);
        getContentPane().setBackground(UiTheme.BACKGROUND);
        getContentPane().setLayout(new BorderLayout());

        toolPanel.setBackground(UiTheme.PANEL);
        toolPanel.setPreferredSize(new Dimension(340, 100));
        toolPanel.add(createPluginsCard(), PLUGINS_CARD);
        toolPanel.add(createGameDataCard(), DATA_CARD);
        toolPanel.add(createSettingsCard(), SETTINGS_CARD);
        toolPanel.add(createLogsCard(), LOGS_CARD);
        toolPanel.setVisible(false);

        JPanel rightSide = new JPanel(new BorderLayout());
        rightSide.add(toolPanel, BorderLayout.CENTER);
        rightSide.add(createNavigationBar(), BorderLayout.EAST);

        getContentPane().add(gameHostPanel, BorderLayout.CENTER);
        getContentPane().add(rightSide, BorderLayout.EAST);

        gameHostPanel.getLaunchButton().addActionListener(event -> startClient());
        installLogHandler();
        installClientApiListeners();
        refreshGameDataPanel();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                shutdown();
            }
        });
    }

    public void startClient() {
        if (launcher.isRunning()) {
            gameHostPanel.setStatus("PokeMMO is already running");
            return;
        }

        clientApi.setConnectionState(ClientConnectionState.STARTING);
        gameHostPanel.setLaunchEnabled(false);
        gameHostPanel.setStatus("Launching PokeMMO...");

        backgroundExecutor.submit(() -> {
            try {
                Process process = launcher.launch();
                gameProcess = process;
                clientApi.setConnectionState(ClientConnectionState.RUNNING);
                updateStatus("Waiting for the PokeMMO window...");

                boolean embedded = embedder.embed(process, gameHostPanel.getCanvas(), Duration.ofSeconds(35));
                if (embedded) {
                    clientApi.setConnectionState(ClientConnectionState.EMBEDDED);
                    updateStatus("PokeMMO embedded in PokeLite");
                } else {
                    clientApi.setConnectionState(ClientConnectionState.RUNNING);
                    updateStatus("PokeMMO is running in a separate window");
                }

                int exitCode = process.waitFor();
                embedder.clear();
                clientApi.setConnectionState(ClientConnectionState.STOPPED);
                updateStatus("PokeMMO stopped (exit code " + exitCode + ")");
                SwingUtilities.invokeLater(() -> gameHostPanel.setLaunchEnabled(true));
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                clientApi.setConnectionState(ClientConnectionState.STOPPED);
                updateStatus("PokeMMO launch interrupted");
                SwingUtilities.invokeLater(() -> gameHostPanel.setLaunchEnabled(true));
            } catch (Exception error) {
                clientApi.setConnectionState(ClientConnectionState.STOPPED);
                LOGGER.log(Level.SEVERE, "Unable to launch or embed PokeMMO", error);
                updateStatus("Launch failed: " + error.getMessage());
                SwingUtilities.invokeLater(() -> gameHostPanel.setLaunchEnabled(true));
            }
        });
    }

    private JPanel createNavigationBar() {
        JPanel navigation = new JPanel();
        navigation.setBackground(UiTheme.NAVIGATION);
        navigation.setLayout(new BoxLayout(navigation, BoxLayout.Y_AXIS));
        navigation.setPreferredSize(new Dimension(52, 100));
        navigation.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, UiTheme.BORDER));

        JLabel logo = new JLabel(new NavigationIcon(NavigationIcon.Kind.POKEBALL, 26));
        logo.setForeground(UiTheme.ACCENT);
        logo.setToolTipText("PokeLite");
        logo.setAlignmentX(JComponent.CENTER_ALIGNMENT);
        logo.setBorder(BorderFactory.createEmptyBorder(13, 0, 13, 0));

        JButton plugins = navigationButton(
            PLUGINS_CARD,
            NavigationIcon.Kind.PLUGINS,
            "Plugins"
        );
        JButton data = navigationButton(
            DATA_CARD,
            NavigationIcon.Kind.DATA,
            "Game Data"
        );
        JButton settings = navigationButton(
            SETTINGS_CARD,
            NavigationIcon.Kind.SETTINGS,
            "Settings"
        );
        JButton logs = navigationButton(
            LOGS_CARD,
            NavigationIcon.Kind.LOGS,
            "Logs"
        );

        navigation.add(logo);
        navigation.add(plugins);
        navigation.add(data);
        navigation.add(settings);
        navigation.add(logs);
        navigation.add(Box.createVerticalGlue());
        return navigation;
    }

    private JButton navigationButton(String card, NavigationIcon.Kind kind, String tooltip) {
        JButton button = UiTheme.navigationButton(new NavigationIcon(kind), tooltip);
        button.addActionListener(event -> toggleTool(card));
        navigationButtons.put(card, button);
        return button;
    }

    private JComponent createPluginsCard() {
        JPanel list = new JPanel();
        list.setBackground(UiTheme.PANEL);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));

        JPanel detailCards = new JPanel(new CardLayout());
        detailCards.setBackground(UiTheme.PANEL);

        for (PokeLitePlugin plugin : pluginManager.getPlugins()) {
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setBackground(UiTheme.PANEL_ALT);
            row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UiTheme.BORDER),
                BorderFactory.createEmptyBorder(7, 8, 7, 8)
            ));

            JCheckBox enabled = new JCheckBox();
            enabled.setSelected(pluginManager.isEnabled(plugin));
            enabled.setToolTipText("Enable " + plugin.getName());
            enabled.addActionListener(event -> {
                try {
                    pluginManager.setEnabled(plugin, enabled.isSelected());
                } catch (RuntimeException error) {
                    enabled.setSelected(!enabled.isSelected());
                }
            });

            JButton select = UiTheme.button(plugin.getName());
            select.setToolTipText(plugin.getDescription());
            select.addActionListener(event ->
                ((CardLayout) detailCards.getLayout()).show(detailCards, plugin.getId())
            );

            row.add(enabled, BorderLayout.WEST);
            row.add(select, BorderLayout.CENTER);
            list.add(row);
            detailCards.add(plugin.createPanel(), plugin.getId());
        }

        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setPreferredSize(new Dimension(340, 220));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, listScroll, detailCards);
        split.setResizeWeight(0.35);
        split.setDividerSize(5);
        split.setBorder(BorderFactory.createEmptyBorder());
        return wrapToolCard("Plugins", split);
    }

    private JComponent createGameDataCard() {
        JPanel content = new JPanel();
        content.setBackground(UiTheme.PANEL);
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JPanel values = new JPanel(new GridLayout(0, 2, 8, 8));
        values.setBackground(UiTheme.PANEL);
        addValueRow(values, "Client", connectionValue);
        addValueRow(values, "Provider", providerValue);
        addValueRow(values, "Capabilities", capabilityValue);
        addValueRow(values, "Money", moneyValue);
        addValueRow(values, "Session money", moneyChangeValue);
        addValueRow(values, "Experience", experienceValue);
        addValueRow(values, "Session EXP", experienceGainedValue);
        addValueRow(values, "Status", gameDataStatusValue);
        values.setAlignmentX(JComponent.LEFT_ALIGNMENT);

        JPanel captureActions = new JPanel(new GridLayout(0, 2, 8, 8));
        captureActions.setBackground(UiTheme.PANEL);
        captureActions.setAlignmentX(JComponent.LEFT_ALIGNMENT);

        JButton start = UiTheme.button("Start OCR");
        start.addActionListener(event -> {
            try {
                ocrProvider.start(clientApi);
            } catch (Exception error) {
                showError("Unable to start game data provider", error);
            }
        });

        JButton stop = UiTheme.button("Stop OCR");
        stop.addActionListener(event -> {
            try {
                ocrProvider.stop();
            } catch (Exception error) {
                showError("Unable to stop game data provider", error);
            }
        });

        JButton scan = UiTheme.button("Scan now");
        scan.addActionListener(event -> ocrProvider.getService().scanNow());

        JButton reset = UiTheme.button("Reset session");
        reset.addActionListener(event -> ocrProvider.getService().resetSession());

        JButton calibrateMoney = UiTheme.button("Calibrate Money");
        calibrateMoney.addActionListener(event -> calibrate(GameDataField.MONEY));

        JButton calibrateExperience = UiTheme.button("Calibrate EXP");
        calibrateExperience.addActionListener(event -> calibrate(GameDataField.EXPERIENCE));

        captureActions.add(start);
        captureActions.add(stop);
        captureActions.add(scan);
        captureActions.add(reset);
        captureActions.add(calibrateMoney);
        captureActions.add(calibrateExperience);

        GameDataService service = ocrProvider.getService();
        JTextField tesseractPath = new JTextField(service.getTesseractExecutable());
        tesseractPath.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        tesseractPath.setAlignmentX(JComponent.LEFT_ALIGNMENT);

        JButton saveTesseract = UiTheme.button("Save Tesseract path");
        saveTesseract.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        saveTesseract.addActionListener(event -> {
            service.setTesseractExecutable(tesseractPath.getText());
            refreshGameDataPanel();
        });

        JLabel note = new JLabel("Providers are replaceable; plugins only use ClientApi.");
        note.setForeground(UiTheme.MUTED_TEXT);
        note.setAlignmentX(JComponent.LEFT_ALIGNMENT);

        content.add(values);
        content.add(Box.createVerticalStrut(14));
        content.add(captureActions);
        content.add(Box.createVerticalStrut(14));
        content.add(new JLabel("Tesseract executable"));
        content.add(Box.createVerticalStrut(5));
        content.add(tesseractPath);
        content.add(Box.createVerticalStrut(7));
        content.add(saveTesseract);
        content.add(Box.createVerticalStrut(14));
        content.add(note);
        content.add(Box.createVerticalGlue());

        return wrapToolCard("Game Data", new JScrollPane(content));
    }

    private void calibrate(GameDataField field) {
        try {
            BufferedImage screenshot = ocrProvider.getService().captureGameCanvas();
            RegionCalibrationDialog.select(this, screenshot, field.getDisplayName())
                .ifPresent(region -> ocrProvider.getService().setRegion(field, region));
        } catch (Exception error) {
            showError("Unable to calibrate " + field.getDisplayName(), error);
        }
    }

    private JComponent createSettingsCard() {
        JPanel settings = new JPanel();
        settings.setBackground(UiTheme.PANEL);
        settings.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        settings.setLayout(new BoxLayout(settings, BoxLayout.Y_AXIS));

        JLabel installation = new JLabel("PokeMMO: " + launcher.getInstallationDirectory());
        installation.setForeground(UiTheme.MUTED_TEXT);
        installation.setAlignmentX(JComponent.LEFT_ALIGNMENT);

        JButton launch = UiTheme.button("Launch PokeMMO");
        launch.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        launch.addActionListener(event -> startClient());

        JButton resize = UiTheme.button("Fit game to window");
        resize.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        resize.addActionListener(event -> embedder.resizeEmbeddedWindow());

        settings.add(installation);
        settings.add(Box.createVerticalStrut(14));
        settings.add(launch);
        settings.add(Box.createVerticalStrut(8));
        settings.add(resize);
        settings.add(Box.createVerticalGlue());
        return wrapToolCard("Settings", settings);
    }

    private JComponent createLogsCard() {
        logArea.setEditable(false);
        logArea.setLineWrap(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        JScrollPane scrollPane = new JScrollPane(logArea);
        return wrapToolCard("Logs", scrollPane);
    }

    private JComponent wrapToolCard(String title, JComponent content) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(UiTheme.PANEL);
        wrapper.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, UiTheme.BORDER));

        JLabel heading = new JLabel(title);
        heading.setForeground(UiTheme.TEXT);
        heading.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, UiTheme.BORDER),
            BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));
        wrapper.add(heading, BorderLayout.NORTH);
        wrapper.add(content, BorderLayout.CENTER);
        return wrapper;
    }

    private void toggleTool(String card) {
        boolean hide = toolPanel.isVisible() && card.equals(visibleTool);
        if (hide) {
            toolPanel.setVisible(false);
            visibleTool = null;
        } else {
            ((CardLayout) toolPanel.getLayout()).show(toolPanel, card);
            toolPanel.setVisible(true);
            visibleTool = card;
        }

        navigationButtons.forEach((name, button) ->
            UiTheme.setNavigationSelected(button, !hide && name.equals(visibleTool))
        );
        revalidate();
        repaint();
    }

    private void installClientApiListeners() {
        eventSubscriptions.add(eventBus.subscribe(
            ClientEvents.ConnectionStateChanged.class,
            event -> refreshGameDataPanelLater()
        ));
        eventSubscriptions.add(eventBus.subscribe(
            ClientEvents.SnapshotUpdated.class,
            event -> refreshGameDataPanelLater()
        ));
        eventSubscriptions.add(eventBus.subscribe(
            ClientEvents.CapabilitiesChanged.class,
            event -> refreshGameDataPanelLater()
        ));
    }

    private void refreshGameDataPanelLater() {
        SwingUtilities.invokeLater(this::refreshGameDataPanel);
    }

    private void refreshGameDataPanel() {
        GameSnapshot snapshot = clientApi.getSnapshot();
        connectionValue.setText(clientApi.getConnectionState().name());
        providerValue.setText(snapshot.providerId());
        capabilityValue.setText(clientApi.getCapabilities().stream()
            .map(ClientCapability::name)
            .sorted()
            .collect(Collectors.joining(", ")));
        moneyValue.setText(formatNumber(snapshot.money()));
        moneyChangeValue.setText(formatSigned(snapshot.sessionMoneyChange()));

        ExperienceSnapshot experience = snapshot.experience();
        if (experience == null) {
            experienceValue.setText("Unavailable");
            experienceGainedValue.setText("Unavailable");
        } else {
            String required = experience.required() == null
                ? "?"
                : NumberFormat.getIntegerInstance().format(experience.required());
            experienceValue.setText(
                NumberFormat.getIntegerInstance().format(experience.current()) + " / " + required
            );
            experienceGainedValue.setText(
                NumberFormat.getIntegerInstance().format(experience.sessionGained())
            );
        }
        gameDataStatusValue.setText(snapshot.status());
    }

    private void installLogHandler() {
        Logger root = Logger.getLogger("");
        root.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (!isLoggable(record)) {
                    return;
                }
                String message = String.format(
                    "%1$tT %-7s %s%n",
                    record.getMillis(),
                    record.getLevel().getName(),
                    record.getMessage()
                );
                SwingUtilities.invokeLater(() -> {
                    logArea.append(message);
                    logArea.setCaretPosition(logArea.getDocument().getLength());
                });
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
    }

    private void updateStatus(String status) {
        SwingUtilities.invokeLater(() -> gameHostPanel.setStatus(status));
    }

    private void showError(String title, Exception error) {
        LOGGER.log(Level.WARNING, title, error);
        JOptionPane.showMessageDialog(
            this,
            error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(),
            title,
            JOptionPane.ERROR_MESSAGE
        );
    }

    private void shutdown() {
        gameHostPanel.setStatus("Closing PokeLite...");
        clientApi.setConnectionState(ClientConnectionState.STOPPED);

        for (EventBus.Subscription subscription : eventSubscriptions) {
            subscription.close();
        }
        ocrProvider.close();

        Process process = gameProcess;
        if (process != null && process.isAlive()) {
            process.destroy();
        }
        launcher.close();
        pluginManager.close();
        backgroundExecutor.shutdownNow();
        dispose();
    }

    private static JLabel valueLabel() {
        JLabel label = new JLabel("Unavailable");
        label.setForeground(UiTheme.TEXT);
        return label;
    }

    private static void addValueRow(JPanel panel, String name, JLabel value) {
        JLabel label = new JLabel(name);
        label.setForeground(UiTheme.MUTED_TEXT);
        panel.add(label);
        panel.add(value);
    }

    private static String formatNumber(Long value) {
        return value == null ? "Unavailable" : NumberFormat.getIntegerInstance().format(value);
    }

    private static String formatSigned(Long value) {
        if (value == null) {
            return "Unavailable";
        }
        String formatted = NumberFormat.getIntegerInstance().format(Math.abs(value));
        return value > 0 ? "+" + formatted : value < 0 ? "-" + formatted : "0";
    }
}
