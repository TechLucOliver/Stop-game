package com.distribuidos.stop.client.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public final class StopTheme {
    public static final Color FUNDO = new Color(15, 20, 43);
    public static final Color PAINEL = new Color(28, 36, 70);
    public static final Color TEXTO = new Color(245, 247, 255);
    public static final Color VERMELHO_STOP = new Color(239, 55, 75);
    public static final Color DOURADO = new Color(255, 196, 75);
    public static final Color TURQUESA = new Color(64, 210, 200);
    public static final Color BORDA = new Color(74, 88, 133);

    private StopTheme() {
    }

    public static void configurar() {
        UIManager.put("Panel.background", FUNDO);
        UIManager.put("Label.foreground", TEXTO);
        UIManager.put("OptionPane.background", PAINEL);
        UIManager.put("OptionPane.messageForeground", TEXTO);
        UIManager.put("TextField.font", new Font("SansSerif", Font.PLAIN, 15));
        UIManager.put("ComboBox.font", new Font("SansSerif", Font.PLAIN, 15));
    }

    public static void estilizarBotaoPrincipal(JButton botao) {
        botao.setFont(new Font("SansSerif", Font.BOLD, 16));
        botao.setBackground(VERMELHO_STOP);
        botao.setForeground(Color.WHITE);
        botao.setFocusPainted(false);
        botao.setOpaque(true);
        botao.setBorder(new EmptyBorder(12, 20, 12, 20));
        botao.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    public static void estilizarBotaoDestaque(JButton botao) {
        botao.setFont(new Font("SansSerif", Font.BOLD, 15));
        botao.setBackground(DOURADO);
        botao.setForeground(FUNDO);
        botao.setFocusPainted(false);
        botao.setOpaque(true);
        botao.setBorder(new EmptyBorder(10, 16, 10, 16));
        botao.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    public static void estilizarBotaoSecundario(JButton botao) {
        botao.setFont(new Font("SansSerif", Font.BOLD, 14));
        botao.setBackground(PAINEL);
        botao.setForeground(TEXTO);
        botao.setFocusPainted(false);
        botao.setOpaque(true);
        botao.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDA, 1),
                new EmptyBorder(9, 14, 9, 14)
        ));
        botao.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    public static void estilizarCampo(JComponent campo) {
        campo.setFont(new Font("SansSerif", Font.PLAIN, 15));
        campo.setBackground(Color.WHITE);
        campo.setForeground(FUNDO);
        campo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDA, 1),
                new EmptyBorder(7, 9, 7, 9)
        ));
    }
}
