package dev.kspog.pokelite.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;

public final class UiTheme {
    public static final Color BACKGROUND = new Color(24, 24, 24);
    public static final Color PANEL = new Color(36, 36, 36);
    public static final Color PANEL_ALT = new Color(45, 45, 45);
    public static final Color NAVIGATION = new Color(19, 19, 19);
    public static final Color BORDER = new Color(58, 58, 58);
    public static final Color ACCENT = new Color(238, 153, 34);
    public static final Color TEXT = new Color(235, 235, 235);
    public static final Color MUTED_TEXT = new Color(165, 165, 165);

    private UiTheme() {
    }

    public static void install() {
        UIManager.put("Panel.background", PANEL);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Button.background", PANEL_ALT);
        UIManager.put("Button.foreground", TEXT);
        UIManager.put("CheckBox.background", PANEL);
        UIManager.put("CheckBox.foreground", TEXT);
        UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("TextArea.background", BACKGROUND);
        UIManager.put("TextArea.foreground", TEXT);
        UIManager.put("TextArea.caretForeground", TEXT);
        UIManager.put("ToolTip.background", PANEL_ALT);
        UIManager.put("ToolTip.foreground", TEXT);
    }

    public static JButton button(String text) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER),
            BorderFactory.createEmptyBorder(7, 10, 7, 10)
        ));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    public static JButton navigationButton(String text, String tooltip) {
        JButton button = button(text);
        button.setToolTipText(tooltip);
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        button.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
        button.setAlignmentX(JComponent.CENTER_ALIGNMENT);
        return button;
    }
}
