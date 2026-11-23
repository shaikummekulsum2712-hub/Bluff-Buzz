package com.fibbage.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * UITheme defines the sleek, modern dark cyberpunk party-game aesthetic for Swing.
 * Features vibrant neon accents, anti-aliased rounded cards, glowing buttons, and polished typography.
 */
public final class UITheme {
    private UITheme() {}

    // Colors
    public static final Color BG_DARK = new Color(0x0D, 0x0B, 0x18);
    public static final Color BG_CARD = new Color(0x18, 0x15, 0x2B);
    public static final Color BG_CARD_LIGHT = new Color(0x25, 0x21, 0x3F);
    public static final Color BORDER_CARD = new Color(0x35, 0x2E, 0x58);

    public static final Color ACCENT_CYAN = new Color(0x00, 0xF5, 0xD4);
    public static final Color ACCENT_YELLOW = new Color(0xFF, 0xD1, 0x66);
    public static final Color ACCENT_RED = new Color(0xFF, 0x33, 0x66);
    public static final Color ACCENT_PURPLE = new Color(0x83, 0x38, 0xEC);
    public static final Color ACCENT_GREEN = new Color(0x06, 0xD6, 0xA0);

    public static final Color TEXT_WHITE = new Color(0xF4, 0xF4, 0xFD);
    public static final Color TEXT_MUTED = new Color(0xA3, 0x9F, 0xB8);
    public static final Color TEXT_DIM = new Color(0x6E, 0x68, 0x8C);

    // Fonts
    public static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 26);
    public static final Font FONT_HEADER = new Font("Segoe UI", Font.BOLD, 18);
    public static final Font FONT_BODY = new Font("Segoe UI", Font.PLAIN, 15);
    public static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 15);
    public static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 12);
    public static final Font FONT_BUZZER = new Font("Segoe UI", Font.BOLD, 34);

    public static JButton createStyledButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Color currentBg = bg;
                if (!isEnabled()) {
                    currentBg = new Color(0x33, 0x2E, 0x4E);
                } else if (getModel().isPressed()) {
                    currentBg = bg.darker();
                } else if (getModel().isRollover()) {
                    currentBg = bg.brighter();
                }

                g2.setColor(currentBg);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 16, 16));

                // Glowing border
                if (isEnabled()) {
                    g2.setColor(new Color(255, 255, 255, 60));
                    g2.draw(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 16, 16));
                }

                g2.dispose();
                super.paintComponent(g);
            }
        };

        btn.setFont(FONT_BOLD);
        btn.setForeground(fg);
        btn.setBackground(bg);
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(10, 22, 10, 22));
        return btn;
    }

    public static JTextField createStyledTextField(int columns) {
        JTextField tf = new JTextField(columns) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_CARD_LIGHT);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));
                g2.setColor(hasFocus() ? ACCENT_CYAN : BORDER_CARD);
                g2.setStroke(new BasicStroke(hasFocus() ? 2f : 1f));
                g2.draw(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 12, 12));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        tf.setFont(FONT_BODY);
        tf.setForeground(TEXT_WHITE);
        tf.setCaretColor(ACCENT_CYAN);
        tf.setOpaque(false);
        tf.setBorder(new EmptyBorder(8, 14, 8, 14));
        return tf;
    }

    public static JPanel createCardPanel() {
        JPanel p = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_CARD);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 20, 20));
                g2.setColor(BORDER_CARD);
                g2.setStroke(new BasicStroke(1.2f));
                g2.draw(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 20, 20));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(18, 18, 18, 18));
        return p;
    }
}
