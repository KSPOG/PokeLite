package dev.kspog.pokelite.plugin.builtin;

import dev.kspog.pokelite.plugin.PokeLitePlugin;
import dev.kspog.pokelite.ui.UiTheme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import java.nio.file.Path;

public final class ClientStatusPlugin implements PokeLitePlugin {
    private final Path installationDirectory;

    public ClientStatusPlugin(Path installationDirectory) {
        this.installationDirectory = installationDirectory.toAbsolutePath().normalize();
    }

    @Override
    public String getId() {
        return "client-status";
    }

    @Override
    public String getName() {
        return "Client Status";
    }

    @Override
    public String getDescription() {
        return "Shows the configured PokeMMO installation and current PokeLite integration status.";
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public JComponent createPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(true);
        panel.setBackground(UiTheme.PANEL);
        panel.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Client Status");
        title.setForeground(UiTheme.TEXT);
        title.setFont(title.getFont().deriveFont(18.0f));
        title.setAlignmentX(JComponent.LEFT_ALIGNMENT);

        JTextArea details = new JTextArea(
            "PokeMMO directory:\n" + installationDirectory +
                "\n\nPokeLite launches the official client and embeds its native window on Windows."
        );
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setOpaque(false);
        details.setForeground(UiTheme.MUTED_TEXT);
        details.setAlignmentX(JComponent.LEFT_ALIGNMENT);

        panel.add(title);
        panel.add(Box.createVerticalStrut(12));
        panel.add(details);
        panel.add(Box.createVerticalGlue());
        return panel;
    }
}
