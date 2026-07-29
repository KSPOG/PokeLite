package dev.kspog.pokelite.ui;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

public final class NavigationIcon implements Icon {
    public enum Kind {
        POKEBALL,
        PLUGINS,
        DATA,
        SETTINGS,
        LOGS
    }

    private final Kind kind;
    private final int size;

    public NavigationIcon(Kind kind) {
        this(kind, 22);
    }

    public NavigationIcon(Kind kind, int size) {
        this.kind = kind;
        this.size = size;
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.translate(x, y);
            double scale = size / 24.0;
            g.scale(scale, scale);
            g.setColor(component.getForeground());
            g.setStroke(new BasicStroke(1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            switch (kind) {
                case POKEBALL -> paintPokeball(g);
                case PLUGINS -> paintPlugins(g);
                case DATA -> paintData(g);
                case SETTINGS -> paintSettings(g);
                case LOGS -> paintLogs(g);
            }
        } finally {
            g.dispose();
        }
    }

    private static void paintPokeball(Graphics2D g) {
        g.draw(new Ellipse2D.Double(2.5, 2.5, 19, 19));
        g.draw(new Line2D.Double(3, 12, 8.5, 12));
        g.draw(new Line2D.Double(15.5, 12, 21, 12));
        g.draw(new Ellipse2D.Double(8.5, 8.5, 7, 7));
        g.fill(new Ellipse2D.Double(10.7, 10.7, 2.6, 2.6));
    }

    private static void paintPlugins(Graphics2D g) {
        Path2D path = new Path2D.Double();
        path.moveTo(4, 4);
        path.lineTo(9, 4);
        path.curveTo(9, 6.2, 10.2, 7.4, 12, 7.4);
        path.curveTo(13.8, 7.4, 15, 6.2, 15, 4);
        path.lineTo(20, 4);
        path.lineTo(20, 9);
        path.curveTo(17.8, 9, 16.6, 10.2, 16.6, 12);
        path.curveTo(16.6, 13.8, 17.8, 15, 20, 15);
        path.lineTo(20, 20);
        path.lineTo(15, 20);
        path.curveTo(15, 17.8, 13.8, 16.6, 12, 16.6);
        path.curveTo(10.2, 16.6, 9, 17.8, 9, 20);
        path.lineTo(4, 20);
        path.closePath();
        g.draw(path);
    }

    private static void paintData(Graphics2D g) {
        g.draw(new Rectangle2D.Double(3.5, 3.5, 17, 17));
        g.drawLine(7, 17, 7, 13);
        g.drawLine(11, 17, 11, 9);
        g.drawLine(15, 17, 15, 11);
        g.drawLine(19, 17, 19, 6);
        Path2D trend = new Path2D.Double();
        trend.moveTo(6, 10);
        trend.lineTo(10, 7);
        trend.lineTo(14, 8);
        trend.lineTo(18, 4.8);
        g.draw(trend);
    }

    private static void paintSettings(Graphics2D g) {
        AffineTransform old = g.getTransform();
        for (int i = 0; i < 8; i++) {
            g.rotate(Math.PI / 4, 12, 12);
            g.fill(new Rectangle2D.Double(10.8, 1.7, 2.4, 4.2));
        }
        g.setTransform(old);
        g.draw(new Ellipse2D.Double(5, 5, 14, 14));
        g.draw(new Ellipse2D.Double(9, 9, 6, 6));
    }

    private static void paintLogs(Graphics2D g) {
        g.draw(new Rectangle2D.Double(3.5, 4, 17, 16));
        Path2D prompt = new Path2D.Double();
        prompt.moveTo(7, 9);
        prompt.lineTo(10, 12);
        prompt.lineTo(7, 15);
        g.draw(prompt);
        g.draw(new Line2D.Double(12.5, 15, 17, 15));
    }
}
