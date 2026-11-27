package com.distribuidos.stop.client.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public final class StopTheme {
    // Paleta moderna estilo Game Show / Dark Slate
    public static final Color FUNDO = new Color(11, 15, 34);
    public static final Color CARD = new Color(22, 29, 58);
    public static final Color CARD_ELEVADO = new Color(32, 41, 79);
    public static final Color INPUT_BG = new Color(16, 21, 44);
    public static final Color TEXTO = new Color(245, 247, 255);
    public static final Color TEXTO_SUAVE = new Color(160, 172, 208);

    public static final Color VERMELHO_STOP = new Color(239, 55, 75);
    public static final Color VERMELHO_HOVER = new Color(255, 75, 95);
    public static final Color DOURADO = new Color(255, 196, 75);
    public static final Color DOURADO_HOVER = new Color(255, 212, 115);
    public static final Color VERDE_SUCESSO = new Color(46, 204, 113);
    public static final Color TURQUESA = new Color(64, 210, 200);
    public static final Color BORDA = new Color(56, 69, 112);

    private StopTheme() {
    }

    public static void configurar() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        UIManager.put("Panel.background", FUNDO);
        UIManager.put("Label.foreground", TEXTO);
        UIManager.put("OptionPane.background", CARD);
        UIManager.put("OptionPane.messageForeground", TEXTO);
        UIManager.put("TextField.font", new Font("Segoe UI", Font.PLAIN, 14));
        UIManager.put("ComboBox.font", new Font("Segoe UI", Font.PLAIN, 14));
        UIManager.put("CheckBox.foreground", TEXTO);
        UIManager.put("CheckBox.background", CARD);
    }

    /**
     * Painel com cantos arredondados e borda sutil (efeito Card).
     */
    public static class RoundedPanel extends JPanel {
        private final int radius;
        private final Color bgColor;
        private final Color borderColor;

        public RoundedPanel(int radius, Color bgColor, Color borderColor) {
            this.radius = radius;
            this.bgColor = bgColor;
            this.borderColor = borderColor;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bgColor);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            if (borderColor != null) {
                g2.setColor(borderColor);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /**
     * Botão arredondado moderno com animação de hover e estado desabilitado elegante.
     */
    public static class ModernButton extends JButton {
        private Color baseColor;
        private Color hoverColor;
        private Color textColor;
        private Color borderColor;
        private boolean hovered = false;

        public ModernButton(String text, Color baseColor, Color hoverColor, Color textColor, Color borderColor) {
            super(text);
            this.baseColor = baseColor;
            this.hoverColor = hoverColor;
            this.textColor = textColor;
            this.borderColor = borderColor;

            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setForeground(textColor);
            setFont(new Font("Segoe UI", Font.BOLD, 14));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(11, 18, 11, 18));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    if (isEnabled()) {
                        hovered = true;
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovered = false;
                    repaint();
                }
            });
        }

        public void setColors(Color base, Color hover, Color text) {
            this.baseColor = base;
            this.hoverColor = hover;
            this.textColor = text;
            setForeground(text);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color fill;
            if (!isEnabled()) {
                fill = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 80);
                setForeground(new Color(textColor.getRed(), textColor.getGreen(), textColor.getBlue(), 120));
            } else {
                fill = hovered ? hoverColor : baseColor;
                setForeground(textColor);
            }

            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);

            if (borderColor != null) {
                g2.setColor(isEnabled() ? borderColor : new Color(BORDA.getRed(), BORDA.getGreen(), BORDA.getBlue(), 80));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            }

            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static ModernButton criarBotaoPrincipal(String texto) {
        ModernButton btn = new ModernButton(texto, VERMELHO_STOP, VERMELHO_HOVER, Color.WHITE, null);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 15));
        btn.setBorder(new EmptyBorder(12, 20, 12, 20));
        return btn;
    }

    public static ModernButton criarBotaoDestaque(String texto) {
        ModernButton btn = new ModernButton(texto, DOURADO, DOURADO_HOVER, FUNDO, null);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 15));
        btn.setBorder(new EmptyBorder(12, 20, 12, 20));
        return btn;
    }

    public static ModernButton criarBotaoSecundario(String texto) {
        ModernButton btn = new ModernButton(texto, CARD_ELEVADO, new Color(44, 56, 105), TEXTO, BORDA);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setBorder(new EmptyBorder(9, 14, 9, 14));
        return btn;
    }

    public static void estilizarCampo(JComponent campo) {
        campo.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        campo.setBackground(new Color(38, 48, 92));
        campo.setForeground(TEXTO);
        campo.setOpaque(true);
        if (campo instanceof JTextField tf) {
            tf.setCaretColor(DOURADO);
        }
        campo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(TURQUESA, 1, true),
                new EmptyBorder(8, 12, 8, 12)
        ));
    }

    public static JLabel criarLabelCampo(String texto) {
        JLabel lbl = new JLabel(texto.toUpperCase());
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lbl.setForeground(TEXTO_SUAVE);
        return lbl;
    }
}