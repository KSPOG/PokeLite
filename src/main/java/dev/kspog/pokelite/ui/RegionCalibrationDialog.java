package dev.kspog.pokelite.ui;

import dev.kspog.pokelite.gamedata.CaptureRegion;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class RegionCalibrationDialog {
    private RegionCalibrationDialog() {
    }

    public static Optional<CaptureRegion> select(
        Window owner,
        BufferedImage screenshot,
        String fieldName
    ) {
        JDialog dialog = new JDialog(owner, "Calibrate " + fieldName, JDialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout(0, 8));

        JLabel instructions = new JLabel(
            "Drag a tight rectangle around the visible " + fieldName + " text, then click Save."
        );
        instructions.setForeground(UiTheme.TEXT);
        instructions.setBorder(BorderFactory.createEmptyBorder(10, 12, 0, 12));

        SelectionCanvas selectionCanvas = new SelectionCanvas(screenshot);
        JScrollPane scrollPane = new JScrollPane(selectionCanvas);
        scrollPane.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));

        AtomicReference<CaptureRegion> result = new AtomicReference<>();

        JButton cancel = UiTheme.button("Cancel");
        cancel.addActionListener(event -> dialog.dispose());

        JButton save = UiTheme.button("Save region");
        save.setEnabled(false);
        save.addActionListener(event -> {
            Rectangle selection = selectionCanvas.getSelection();
            if (selection != null && selection.width >= 4 && selection.height >= 4) {
                result.set(CaptureRegion.fromPixels(
                    selection,
                    screenshot.getWidth(),
                    screenshot.getHeight()
                ));
                dialog.dispose();
            }
        });

        selectionCanvas.setSelectionListener(selection ->
            save.setEnabled(selection != null && selection.width >= 4 && selection.height >= 4)
        );

        JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
        actions.setBackground(UiTheme.PANEL);
        actions.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
        actions.add(cancel);
        actions.add(save);

        dialog.getContentPane().setBackground(UiTheme.PANEL);
        dialog.add(instructions, BorderLayout.NORTH);
        dialog.add(scrollPane, BorderLayout.CENTER);
        dialog.add(actions, BorderLayout.SOUTH);
        dialog.setSize(
            Math.min(1100, screenshot.getWidth() + 40),
            Math.min(820, screenshot.getHeight() + 120)
        );
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return Optional.ofNullable(result.get());
    }

    private static final class SelectionCanvas extends JPanel {
        private final BufferedImage image;
        private Point dragStart;
        private Rectangle selection;
        private java.util.function.Consumer<Rectangle> selectionListener = ignored -> {
        };

        private SelectionCanvas(BufferedImage image) {
            this.image = image;
            setPreferredSize(new Dimension(image.getWidth(), image.getHeight()));
            setBackground(Color.BLACK);

            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    dragStart = clamp(event.getPoint());
                    selection = new Rectangle(dragStart);
                    repaint();
                }

                @Override
                public void mouseDragged(MouseEvent event) {
                    if (dragStart == null) {
                        return;
                    }
                    Point current = clamp(event.getPoint());
                    selection = rectangleBetween(dragStart, current);
                    selectionListener.accept(selection);
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    if (dragStart == null) {
                        return;
                    }
                    Point current = clamp(event.getPoint());
                    selection = rectangleBetween(dragStart, current);
                    dragStart = null;
                    selectionListener.accept(selection);
                    repaint();
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        private void setSelectionListener(java.util.function.Consumer<Rectangle> listener) {
            this.selectionListener = listener;
        }

        private Rectangle getSelection() {
            return selection == null ? null : new Rectangle(selection);
        }

        private Point clamp(Point point) {
            return new Point(
                Math.max(0, Math.min(image.getWidth() - 1, point.x)),
                Math.max(0, Math.min(image.getHeight() - 1, point.y))
            );
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            graphics.drawImage(image, 0, 0, null);

            if (selection != null) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setColor(new Color(238, 153, 34, 60));
                    g.fill(selection);
                    g.setColor(UiTheme.ACCENT);
                    g.setStroke(new java.awt.BasicStroke(2f));
                    g.draw(selection);
                } finally {
                    g.dispose();
                }
            }
        }

        private static Rectangle rectangleBetween(Point first, Point second) {
            int x = Math.min(first.x, second.x);
            int y = Math.min(first.y, second.y);
            int width = Math.max(1, Math.abs(first.x - second.x));
            int height = Math.max(1, Math.abs(first.y - second.y));
            return new Rectangle(x, y, width, height);
        }
    }
}
