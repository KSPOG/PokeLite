package dev.kspog.pokelite.ui;

import dev.kspog.pokelite.game.PokeMmoLauncher;
import dev.kspog.pokelite.game.WindowsGameEmbedder;
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
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class PokeLiteFrame extends JFrame {
    private static final Logger LOGGER = Logger.getLogger(PokeLiteFrame.class.getName());

    private static final String PLUGINS_CARD = "plugins";
    private static final String SETTINGS_CARD = "settings";
    private static final String LOGS_CARD = "logs";

    private final PluginManager pluginManager;
    private final PokeMmoLauncher launcher;
    private final WindowsGameEmbedder embedder = new WindowsGameEmbedder();
    private final ExecutorService backgroundExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "pokelite-background");
        thread.setDaemon(true);
        return thread;
    });

    private final GameHostPanel gameHostPanel = new GameHostPanel();
    private final JPanel toolPanel = new JPanel(new CardLayout());
    private final JTextArea logArea = new JTextArea();

    private String visibleTool;
    private volatile Process gameProcess;

    public PokeLiteFrame(Path projectRoot, PluginManager pluginManager) {
        super("PokeLite");
        this.pluginManager = Objects.requireNonNull(pluginManager);
        this.launcher = new PokeMmoLauncher(projectRoot.resolve("poke"));

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(960, 640));
        setSize(1280, 800);
        setLocationRelativeTo(null);
        getContentPane().setBackground(UiTheme.BACKGROUND);
        getContentPane().setLayout(new BorderLayout());

        toolPanel.setBackground(UiTheme.PANEL);
        toolPanel.setPreferredSize(new Dimension(320, 100));
        toolPanel.add(createPluginsCard(), PLUGINS_CARD);
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

        gameHostPanel.setLaunchEnabled(false);
        gameHostPanel.setStatus("Launching PokeMMO...");

        backgroundExecutor.submit(() -> {
            try {
                Process process = launcher.launch();
                gameProcess = process;
                updateStatus("Waiting for the PokeMMO window...");

                boolean embedded = embedder.embed(process, gameHostPanel.getCanvas(), Duration.ofSeconds(35));
                if (embedded) {
                    updateStatus("PokeMMO embedded in PokeLite");
                } else {
                    updateStatus("PokeMMO is running in a separate window");
                }

                int exitCode = process.waitFor();
                embedder.clear();
                updateStatus("PokeMMO stopped (exit code " + exitCode + ")");
                SwingUtilities.invokeLater(() -> gameHostPanel.setLaunchEnabled(true));
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                updateStatus("PokeMMO launch interrupted");
                SwingUtilities.invokeLater(() -> gameHostPanel.setLaunchEnabled(true));
            } catch (Exception error) {
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

        JLabel logo = new JLabel("PL");
        logo.setForeground(UiTheme.ACCENT);
        logo.setAlignmentX(JComponent.CENTER_ALIGNMENT);
        logo.setBorder(BorderFactory.createEmptyBorder(14, 0, 14, 0));

        JButton plugins = UiTheme.navigationButton("P", "Plugins");
        JButton settings = UiTheme.navigationButton("S", "Settings");
        JButton logs = UiTheme.navigationButton("L", "Logs");
        plugins.addActionListener(event -> toggleTool(PLUGINS_CARD));
        settings.addActionListener(event -> toggleTool(SETTINGS_CARD));
        logs.addActionListener(event -> toggleTool(LOGS_CARD));

        navigation.add(logo);
        navigation.add(plugins);
        navigation.add(settings);
        navigation.add(logs);
        navigation.add(Box.createVerticalGlue());
        return navigation;
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
        listScroll.setPreferredSize(new Dimension(320, 220));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, listScroll, detailCards);
        split.setResizeWeight(0.35);
        split.setDividerSize(5);
        split.setBorder(BorderFactory.createEmptyBorder());
        return wrapToolCard("Plugins", split);
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
        logArea.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 11));
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
        if (toolPanel.isVisible() && card.equals(visibleTool)) {
            toolPanel.setVisible(false);
            visibleTool = null;
        } else {
            ((CardLayout) toolPanel.getLayout()).show(toolPanel, card);
            toolPanel.setVisible(true);
            visibleTool = card;
        }
        revalidate();
        repaint();
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

    private void shutdown() {
        gameHostPanel.setStatus("Closing PokeLite...");
        Process process = gameProcess;
        if (process != null && process.isAlive()) {
            process.destroy();
        }
        launcher.close();
        pluginManager.close();
        backgroundExecutor.shutdownNow();
        dispose();
    }
}
