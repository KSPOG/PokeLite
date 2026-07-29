package dev.kspog.pokelite.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;

public final class GameHostPanel extends JPanel {
    private final Canvas canvas = new Canvas();
    private final JLabel statusLabel = new JLabel("Ready");
    private final JButton launchButton = UiTheme.button("Launch PokeMMO");

    public GameHostPanel() {
        super(new BorderLayout());
        setBackground(Color.BLACK);

        canvas.setBackground(Color.BLACK);
        canvas.setMinimumSize(new Dimension(640, 480));

        JPanel statusBar = new JPanel(new BorderLayout(10, 0));
        statusBar.setBackground(UiTheme.PANEL);
        statusBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, UiTheme.BORDER),
            BorderFactory.createEmptyBorder(6, 10, 6, 8)
        ));
        statusLabel.setForeground(UiTheme.MUTED_TEXT);
        statusBar.add(statusLabel, BorderLayout.CENTER);
        statusBar.add(launchButton, BorderLayout.EAST);

        add(statusBar, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
    }

    public Canvas getCanvas() {
        return canvas;
    }

    public JButton getLaunchButton() {
        return launchButton;
    }

    public void setStatus(String status) {
        statusLabel.setText(status);
    }

    public void setLaunchEnabled(boolean enabled) {
        launchButton.setEnabled(enabled);
    }
}
