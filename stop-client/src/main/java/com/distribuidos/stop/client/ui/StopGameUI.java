package com.distribuidos.stop.client.ui;

import com.distribuidos.stop.*;
import com.distribuidos.stop.client.network.GameGrpcClient;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * ============================================================================
 * INTERFACE GRÁFICA DESKTOP (JAVA SWING MULTIPLAYER)
 * ============================================================================
 *
 * Regra de Ouro em Sistemas Concorrentes com UI:
 * As mensagens do gRPC chegam nas threads de worker do Netty. Como os componentes
 * do Swing NÃO são thread-safe, todas as atualizações de tela OBRIGATORIAMENTE
 * devem ser despachadas na Event Dispatch Thread (EDT) via SwingUtilities.invokeLater.
 */
public class StopGameUI extends JFrame {

    private final Map<String, String> tokensSalasCriadas = new HashMap<>();
    private static final Preferences PREFERENCIAS =
            Preferences.userNodeForPackage(StopGameUI.class);

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel painelPrincipal = new JPanel(cardLayout);

    private GameGrpcClient rede;
    private final Map<String, JTextField> camposRespostas = new HashMap<>();
    private final List<String> categoriasAtuais = new ArrayList<>();

    // Componentes da Tela de Conexão (LOBBY)
    private JTextField txtNome;
    private JTextField txtHost;
    private JTextField txtPorta;
    private JButton btnConectar;
    private JButton btnPronto;
    private JLabel lblStatusLobby;
    private JComboBox<SalaDisponivel> comboSalas;
    private JButton btnAtualizarSalas;
    private JButton btnCriarSala;
    private JButton btnEditarSala;

    // Componentes da Tela de Jogo (RODADA)
    private JLabel lblLetraSorteada;
    private JLabel lblStatusRodada;
    private JLabel lblTemporizadorRodada;
    private JLabel lblTemporizadorVotacao;
    private javax.swing.Timer timerContagem;
    private long fimTemporizadorMs;
    private JPanel painelCategorias;
    private JButton btnStop;

    // Componentes da Tela de Validação
    private JPanel painelItensValidacao;
    private JButton btnEnviarVotos;
    private final List<ItemValidacaoUI> itensParaVotar = new ArrayList<>();

    // Componentes da Tela de Placar
    private JPanel painelPlacar;
    private JLabel lblTituloPlacar;

    public StopGameUI() {
        super("Stop! Distribuído (gRPC Multiplayer)");
        configurarJanela();
        criarTelas();
    }

    private void configurarJanela() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(860, 680);
        setMinimumSize(new Dimension(700, 580));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        StopTheme.configurar();
        add(painelPrincipal, BorderLayout.CENTER);
    }

    private void criarTelas() {
        painelPrincipal.add(criarTelaLobby(), "LOBBY");
        painelPrincipal.add(criarTelaJogo(), "JOGO");
        painelPrincipal.add(criarTelaValidacao(), "VALIDACAO");
        painelPrincipal.add(criarTelaPlacar(), "PLACAR");
        cardLayout.show(painelPrincipal, "LOBBY");
    }

    // ========================================================================
    // 1. TELA DE LOBBY / CONEXÃO
    // ========================================================================
    private JPanel criarTelaLobby() {
        JPanel fundo = new JPanel(new GridBagLayout());
        fundo.setBackground(StopTheme.FUNDO);
        fundo.setBorder(new EmptyBorder(24, 24, 24, 24));

        StopTheme.RoundedPanel card = new StopTheme.RoundedPanel(20, StopTheme.CARD, StopTheme.BORDA);
        card.setLayout(new GridBagLayout());
        card.setBorder(new EmptyBorder(32, 36, 32, 36));
        card.setPreferredSize(new Dimension(520, 560));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        JLabel lblBadge = new JLabel("MULTIPLAYER  •  gRPC SHOWDOWN", SwingConstants.CENTER);
        lblBadge.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblBadge.setForeground(StopTheme.TURQUESA);

        JLabel lblTitulo = new JLabel("STOP!", SwingConstants.CENTER);
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 42));
        lblTitulo.setForeground(StopTheme.DOURADO);

        txtNome = new JTextField("Jogador_" + (int) (Math.random() * 900 + 100));
        txtHost = new JTextField(PREFERENCIAS.get("servidor.host", "localhost"));
        txtPorta = new JTextField(Integer.toString(PREFERENCIAS.getInt("servidor.porta", 50051)));

        comboSalas = new JComboBox<>();
        comboSalas.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> lista, Object valor, int indice, boolean selecionado, boolean foco) {
                super.getListCellRendererComponent(lista, valor, indice, selecionado, foco);
                setBackground(selecionado ? StopTheme.CARD_ELEVADO : StopTheme.INPUT_BG);
                setForeground(StopTheme.TEXTO);
                setBorder(new EmptyBorder(6, 10, 6, 10));
                if (valor instanceof SalaDisponivel sala) {
                    setText(sala.getNomeSala() + "  •  " + sala.getJogadoresConectados() + " jogador(es)  [" + sala.getEstado() + "]");
                }
                return this;
            }
        });

        StopTheme.estilizarCampo(txtNome);
        StopTheme.estilizarCampo(txtHost);
        StopTheme.estilizarCampo(txtPorta);
        StopTheme.estilizarCampo(comboSalas);

        btnAtualizarSalas = StopTheme.criarBotaoSecundario("Atualizar");
        btnCriarSala = StopTheme.criarBotaoSecundario("+ Criar Sala");
        btnEditarSala = StopTheme.criarBotaoSecundario("Editar");
        btnEditarSala.setEnabled(false);

        btnAtualizarSalas.addActionListener(e -> acaoAtualizarSalas());
        btnCriarSala.addActionListener(e -> acaoCriarSala());
        btnEditarSala.addActionListener(e -> acaoEditarSala());
        comboSalas.addActionListener(e -> atualizarDisponibilidadeEdicaoSala());

        btnConectar = StopTheme.criarBotaoPrincipal("Conectar ao Servidor");
        btnPronto = StopTheme.criarBotaoDestaque("Marcar Estou Pronto!");
        btnPronto.setEnabled(false);

        btnConectar.addActionListener(e -> acaoConectar());
        btnPronto.addActionListener(e -> acaoMarcarPronto());

        lblStatusLobby = new JLabel("Preencha seus dados e escolha uma sala.", SwingConstants.CENTER);
        lblStatusLobby.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblStatusLobby.setForeground(StopTheme.TEXTO_SUAVE);

        int row = 0;
        gbc.insets = new Insets(0, 0, 2, 0);
        gbc.gridy = row++;
        card.add(lblBadge, gbc);

        gbc.insets = new Insets(0, 0, 20, 0);
        gbc.gridy = row++;
        card.add(lblTitulo, gbc);

        gbc.insets = new Insets(4, 0, 4, 0);
        gbc.gridy = row++;
        card.add(StopTheme.criarLabelCampo("Seu Apelido"), gbc);
        gbc.insets = new Insets(0, 0, 12, 0);
        gbc.gridy = row++;
        card.add(txtNome, gbc);

        JPanel painelRede = new JPanel(new GridBagLayout());
        painelRede.setOpaque(false);
        GridBagConstraints gRede = new GridBagConstraints();
        gRede.fill = GridBagConstraints.HORIZONTAL;

        gRede.gridx = 0;
        gRede.gridy = 0;
        gRede.weightx = 0.72;
        gRede.insets = new Insets(0, 0, 4, 8);
        painelRede.add(StopTheme.criarLabelCampo("Endereço IP do Servidor"), gRede);
        gRede.gridx = 1;
        gRede.weightx = 0.28;
        gRede.insets = new Insets(0, 0, 4, 0);
        painelRede.add(StopTheme.criarLabelCampo("Porta"), gRede);

        gRede.gridx = 0;
        gRede.gridy = 1;
        gRede.weightx = 0.72;
        gRede.insets = new Insets(0, 0, 0, 8);
        painelRede.add(txtHost, gRede);
        gRede.gridx = 1;
        gRede.weightx = 0.28;
        gRede.insets = new Insets(0, 0, 0, 0);
        painelRede.add(txtPorta, gRede);

        gbc.insets = new Insets(0, 0, 12, 0);
        gbc.gridy = row++;
        card.add(painelRede, gbc);

        gbc.insets = new Insets(0, 0, 4, 0);
        gbc.gridy = row++;
        card.add(StopTheme.criarLabelCampo("Sala da Partida"), gbc);
        gbc.insets = new Insets(0, 0, 8, 0);
        gbc.gridy = row++;
        card.add(comboSalas, gbc);

        JPanel barraSalas = new JPanel(new GridLayout(1, 3, 8, 0));
        barraSalas.setOpaque(false);
        barraSalas.add(btnAtualizarSalas);
        barraSalas.add(btnCriarSala);
        barraSalas.add(btnEditarSala);

        gbc.insets = new Insets(0, 0, 18, 0);
        gbc.gridy = row++;
        card.add(barraSalas, gbc);

        JPanel barraAcao = new JPanel(new GridLayout(1, 2, 10, 0));
        barraAcao.setOpaque(false);
        barraAcao.add(btnConectar);
        barraAcao.add(btnPronto);

        gbc.insets = new Insets(0, 0, 14, 0);
        gbc.gridy = row++;
        card.add(barraAcao, gbc);

        gbc.insets = new Insets(0, 0, 0, 0);
        gbc.gridy = row;
        card.add(lblStatusLobby, gbc);

        fundo.add(card);
        return fundo;
    }

    // ========================================================================
    // 2. TELA DE JOGO ATIVO
    // ========================================================================
    private JPanel criarTelaJogo() {
        JPanel painel = new JPanel(new BorderLayout(16, 16));
        painel.setBackground(StopTheme.FUNDO);
        painel.setBorder(new EmptyBorder(24, 36, 24, 36));

        StopTheme.RoundedPanel topoCard = new StopTheme.RoundedPanel(16, StopTheme.CARD, StopTheme.BORDA);
        topoCard.setLayout(new BorderLayout(12, 8));
        topoCard.setBorder(new EmptyBorder(16, 24, 16, 24));

        lblLetraSorteada = new JLabel("LETRA: ?", SwingConstants.CENTER);
        lblLetraSorteada.setFont(new Font("Segoe UI", Font.BOLD, 42));
        lblLetraSorteada.setForeground(StopTheme.DOURADO);

        lblTemporizadorRodada = new JLabel("00:00", SwingConstants.CENTER);
        lblTemporizadorRodada.setFont(new Font("Segoe UI", Font.BOLD, 20));
        lblTemporizadorRodada.setForeground(StopTheme.TURQUESA);

        lblStatusRodada = new JLabel("Preencha todas as categorias e aperte STOP!", SwingConstants.CENTER);
        lblStatusRodada.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lblStatusRodada.setForeground(StopTheme.TEXTO_SUAVE);

        topoCard.add(lblLetraSorteada, BorderLayout.NORTH);
        topoCard.add(lblTemporizadorRodada, BorderLayout.CENTER);
        topoCard.add(lblStatusRodada, BorderLayout.SOUTH);

        StopTheme.RoundedPanel cardCategorias = new StopTheme.RoundedPanel(16, StopTheme.CARD, StopTheme.BORDA);
        cardCategorias.setLayout(new BorderLayout());
        cardCategorias.setBorder(new EmptyBorder(16, 20, 16, 20));

        painelCategorias = new JPanel(new GridBagLayout());
        painelCategorias.setBackground(StopTheme.CARD);

        JScrollPane scroll = new JScrollPane(painelCategorias);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(StopTheme.CARD);
        cardCategorias.add(scroll, BorderLayout.CENTER);

        StopTheme.ModernButton stopModerno = new StopTheme.ModernButton(
                "STOP!", StopTheme.VERDE_SUCESSO, new Color(56, 224, 128), StopTheme.FUNDO, null
        );
        stopModerno.setFont(new Font("Segoe UI", Font.BOLD, 22));
        stopModerno.setPreferredSize(new Dimension(220, 58));
        stopModerno.addActionListener(e -> acaoDispararStop());
        btnStop = stopModerno;

        painel.add(topoCard, BorderLayout.NORTH);
        painel.add(cardCategorias, BorderLayout.CENTER);
        painel.add(btnStop, BorderLayout.SOUTH);

        return painel;
    }

    // ========================================================================
    // 3. TELA DE VALIDAÇÃO COMUNITÁRIA
    // ========================================================================
    private JPanel criarTelaValidacao() {
        JPanel painel = new JPanel(new BorderLayout(16, 16));
        painel.setBackground(StopTheme.FUNDO);
        painel.setBorder(new EmptyBorder(20, 28, 20, 28));

        JPanel cabecalho = new JPanel(new GridLayout(2, 1, 0, 4));
        cabecalho.setBackground(StopTheme.FUNDO);

        JLabel lblTitulo = new JLabel("VALIDAÇÃO COLETIVA DE PALAVRAS", SwingConstants.CENTER);
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblTitulo.setForeground(StopTheme.DOURADO);

        JLabel lblDica = new JLabel("Verde = Válida  •  Clique na palavra para marcar como INVÁLIDA (vermelho)", SwingConstants.CENTER);
        lblDica.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblDica.setForeground(StopTheme.TEXTO_SUAVE);

        cabecalho.add(lblTitulo);
        cabecalho.add(lblDica);

        painelItensValidacao = new JPanel(new GridLayout(0, 2, 16, 16));
        painelItensValidacao.setBackground(StopTheme.FUNDO);

        JPanel wrapperGrid = new JPanel(new BorderLayout());
        wrapperGrid.setBackground(StopTheme.FUNDO);
        wrapperGrid.add(painelItensValidacao, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(wrapperGrid);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(StopTheme.FUNDO);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        lblTemporizadorVotacao = new JLabel("Tempo para votar: --:--", SwingConstants.CENTER);
        lblTemporizadorVotacao.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblTemporizadorVotacao.setForeground(StopTheme.TURQUESA);

        btnEnviarVotos = StopTheme.criarBotaoDestaque("Confirmar Votos");
        btnEnviarVotos.addActionListener(e -> acaoEnviarVotos());

        JPanel rodapeValidacao = new JPanel(new BorderLayout(10, 10));
        rodapeValidacao.setBackground(StopTheme.FUNDO);
        rodapeValidacao.add(lblTemporizadorVotacao, BorderLayout.NORTH);
        rodapeValidacao.add(btnEnviarVotos, BorderLayout.SOUTH);

        painel.add(cabecalho, BorderLayout.NORTH);
        painel.add(scroll, BorderLayout.CENTER);
        painel.add(rodapeValidacao, BorderLayout.SOUTH);

        return painel;
    }

    // ========================================================================
    // 4. TELA DE PLACAR
    // ========================================================================
    private JPanel criarTelaPlacar() {
        JPanel painel = new JPanel(new BorderLayout(16, 16));
        painel.setBackground(StopTheme.FUNDO);
        painel.setBorder(new EmptyBorder(28, 40, 28, 40));

        lblTituloPlacar = new JLabel("CLASSIFICAÇÃO DA PARTIDA", SwingConstants.CENTER);
        lblTituloPlacar.setFont(new Font("Segoe UI", Font.BOLD, 26));
        lblTituloPlacar.setForeground(StopTheme.DOURADO);

        painelPlacar = new JPanel();
        painelPlacar.setLayout(new BoxLayout(painelPlacar, BoxLayout.Y_AXIS));
        painelPlacar.setBackground(StopTheme.FUNDO);

        JScrollPane scroll = new JScrollPane(painelPlacar);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(StopTheme.FUNDO);

        painel.add(lblTituloPlacar, BorderLayout.NORTH);
        painel.add(scroll, BorderLayout.CENTER);

        return painel;
    }

    // ========================================================================
    // DIÁLOGOS PADRONIZADOS E AMPLOS PARA CRIAÇÃO / EDIÇÃO DE SALA
    // ========================================================================
    private JDialog criarDialogoPadrao(String tituloJanela, Dimension tamanho) {
        JDialog dialog = new JDialog(this, tituloJanela, true);
        dialog.setSize(tamanho);
        dialog.setMinimumSize(new Dimension(580, 340));
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());

        JPanel fundo = new JPanel(new BorderLayout(0, 16));
        fundo.setBackground(StopTheme.FUNDO);
        fundo.setBorder(new EmptyBorder(20, 22, 20, 22));

        StopTheme.RoundedPanel card = new StopTheme.RoundedPanel(18, StopTheme.CARD, StopTheme.BORDA);
        card.setLayout(new BorderLayout(0, 16));
        card.setBorder(new EmptyBorder(22, 26, 22, 26));

        fundo.add(card, BorderLayout.CENTER);
        dialog.setContentPane(fundo);
        return dialog;
    }

    private JPanel criarCabecalhoEtapa(String seloEtapa, String tituloPrincipal, String subtitulo) {
        JPanel cabecalho = new JPanel();
        cabecalho.setLayout(new BoxLayout(cabecalho, BoxLayout.Y_AXIS));
        cabecalho.setOpaque(false);
        cabecalho.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, StopTheme.BORDA),
                new EmptyBorder(0, 0, 14, 0)
        ));

        StopTheme.RoundedPanel badgePanel = new StopTheme.RoundedPanel(8, StopTheme.CARD_ELEVADO, StopTheme.TURQUESA);
        badgePanel.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 3));
        badgePanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblBadge = new JLabel(seloEtapa.toUpperCase());
        lblBadge.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblBadge.setForeground(StopTheme.TURQUESA);
        badgePanel.add(lblBadge);
        badgePanel.setMaximumSize(badgePanel.getPreferredSize());

        JLabel lblTitulo = new JLabel(tituloPrincipal.toUpperCase());
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 24));
        lblTitulo.setForeground(StopTheme.DOURADO);
        lblTitulo.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblSub = new JLabel(subtitulo);
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblSub.setForeground(StopTheme.TEXTO_SUAVE);
        lblSub.setAlignmentX(Component.LEFT_ALIGNMENT);

        cabecalho.add(badgePanel);
        cabecalho.add(Box.createRigidArea(new Dimension(0, 8)));
        cabecalho.add(lblTitulo);
        cabecalho.add(Box.createRigidArea(new Dimension(0, 4)));
        cabecalho.add(lblSub);

        return cabecalho;
    }

    private String abrirModalTexto(
            String seloEtapa,
            String tituloEtapa,
            String subtitulo,
            String rotuloCampo,
            String valorInicial,
            String textoBotaoConfirmar
    ) {
        final String[] resultado = {null};
        JDialog dialog = criarDialogoPadrao(seloEtapa + " - " + tituloEtapa, new Dimension(650, 390));
        StopTheme.RoundedPanel card = (StopTheme.RoundedPanel) ((BorderLayout) dialog.getContentPane().getLayout())
                .getLayoutComponent(BorderLayout.CENTER);

        JPanel topo = criarCabecalhoEtapa(seloEtapa, tituloEtapa, subtitulo);

        JPanel centro = new JPanel();
        centro.setLayout(new BoxLayout(centro, BoxLayout.Y_AXIS));
        centro.setOpaque(false);
        centro.setBorder(new EmptyBorder(12, 0, 8, 0));

        JLabel lblCampo = StopTheme.criarLabelCampo(rotuloCampo);
        lblCampo.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextField campo = new JTextField(valorInicial == null ? "" : valorInicial);
        StopTheme.estilizarCampo(campo);
        campo.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        campo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        campo.setPreferredSize(new Dimension(500, 46));
        campo.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblErro = new JLabel(" ");
        lblErro.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblErro.setForeground(StopTheme.VERMELHO_STOP);
        lblErro.setAlignmentX(Component.LEFT_ALIGNMENT);

        centro.add(lblCampo);
        centro.add(Box.createRigidArea(new Dimension(0, 8)));
        centro.add(campo);
        centro.add(Box.createRigidArea(new Dimension(0, 6)));
        centro.add(lblErro);

        StopTheme.ModernButton btnCancelar = StopTheme.criarBotaoSecundario("Cancelar");
        StopTheme.ModernButton btnConfirmar = StopTheme.criarBotaoDestaque(textoBotaoConfirmar);

        Runnable confirmar = () -> {
            String texto = campo.getText().trim();
            if (texto.isEmpty()) {
                lblErro.setText("Preencha este campo antes de continuar.");
                campo.requestFocusInWindow();
                return;
            }
            resultado[0] = texto;
            dialog.dispose();
        };

        btnCancelar.addActionListener(e -> {
            resultado[0] = null;
            dialog.dispose();
        });
        btnConfirmar.addActionListener(e -> confirmar.run());
        campo.addActionListener(e -> confirmar.run());

        JPanel rodape = new JPanel(new GridLayout(1, 2, 12, 0));
        rodape.setOpaque(false);
        rodape.add(btnCancelar);
        rodape.add(btnConfirmar);

        card.add(topo, BorderLayout.NORTH);
        card.add(centro, BorderLayout.CENTER);
        card.add(rodape, BorderLayout.SOUTH);

        SwingUtilities.invokeLater(campo::requestFocusInWindow);
        dialog.setVisible(true);
        return resultado[0];
    }

    private List<String> abrirModalCategorias(
            String seloEtapa,
            String tituloEtapa,
            List<String> categoriasIniciais,
            String textoBotaoConfirmar
    ) {
        final List<String>[] resultado = new List[]{null};
        JDialog dialog = criarDialogoPadrao(seloEtapa + " - " + tituloEtapa, new Dimension(680, 520));
        StopTheme.RoundedPanel card = (StopTheme.RoundedPanel) ((BorderLayout) dialog.getContentPane().getLayout())
                .getLayoutComponent(BorderLayout.CENTER);

        JPanel topo = criarCabecalhoEtapa(
                seloEtapa,
                tituloEtapa,
                "Adicione novas categorias à lista ou remova as que você não quer."
        );

        List<String> listaCategorias = new ArrayList<>();
        if (categoriasIniciais != null) {
            for (String c : categoriasIniciais) {
                if (c != null && !c.isBlank()) {
                    String limpa = c.trim().toUpperCase();
                    if (!listaCategorias.contains(limpa)) {
                        listaCategorias.add(limpa);
                    }
                }
            }
        }

        JPanel listaItensPanel = new JPanel();
        listaItensPanel.setLayout(new BoxLayout(listaItensPanel, BoxLayout.Y_AXIS));
        listaItensPanel.setBackground(StopTheme.FUNDO);

        JScrollPane scrollLista = new JScrollPane(listaItensPanel);
        scrollLista.setBorder(BorderFactory.createLineBorder(StopTheme.BORDA, 1, true));
        scrollLista.getViewport().setBackground(StopTheme.FUNDO);
        scrollLista.getVerticalScrollBar().setUnitIncrement(14);

        JLabel lblErro = new JLabel(" ");
        lblErro.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblErro.setForeground(StopTheme.VERMELHO_STOP);

        Runnable renderizarLista = new Runnable() {
            @Override
            public void run() {
                listaItensPanel.removeAll();
                if (listaCategorias.isEmpty()) {
                    JLabel vazio = new JLabel("Nenhuma categoria na lista. Adicione uma acima.", SwingConstants.CENTER);
                    vazio.setFont(new Font("Segoe UI", Font.ITALIC, 13));
                    vazio.setForeground(StopTheme.TEXTO_SUAVE);
                    vazio.setAlignmentX(Component.CENTER_ALIGNMENT);
                    vazio.setBorder(new EmptyBorder(24, 12, 24, 12));
                    listaItensPanel.add(vazio);
                } else {
                    for (int i = 0; i < listaCategorias.size(); i++) {
                        final int idx = i;
                        String nomeCat = listaCategorias.get(i);

                        StopTheme.RoundedPanel linha = new StopTheme.RoundedPanel(10, StopTheme.CARD_ELEVADO, StopTheme.BORDA);
                        linha.setLayout(new BorderLayout(10, 0));
                        linha.setBorder(new EmptyBorder(8, 14, 8, 10));
                        linha.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));

                        JLabel lblNome = new JLabel(nomeCat);
                        lblNome.setFont(new Font("Segoe UI", Font.BOLD, 14));
                        lblNome.setForeground(StopTheme.TEXTO);

                        StopTheme.ModernButton btnRemover = new StopTheme.ModernButton(
                                "Remover",
                                StopTheme.VERMELHO_STOP,
                                new Color(192, 57, 43),
                                Color.WHITE,
                                null
                        );
                        btnRemover.setFont(new Font("Segoe UI", Font.BOLD, 12));
                        btnRemover.setBorder(new EmptyBorder(5, 12, 5, 12));
                        btnRemover.addActionListener(e -> {
                            listaCategorias.remove(idx);
                            lblErro.setText(" ");
                            this.run();
                        });

                        linha.add(lblNome, BorderLayout.CENTER);
                        linha.add(btnRemover, BorderLayout.EAST);

                        listaItensPanel.add(linha);
                        listaItensPanel.add(Box.createRigidArea(new Dimension(0, 8)));
                    }
                }
                listaItensPanel.revalidate();
                listaItensPanel.repaint();
            }
        };

        renderizarLista.run();

        JTextField campoNovaCategoria = new JTextField();
        StopTheme.estilizarCampo(campoNovaCategoria);
        campoNovaCategoria.setPreferredSize(new Dimension(300, 42));

        StopTheme.ModernButton btnAdicionar = new StopTheme.ModernButton(
                "+ Adicionar",
                StopTheme.VERDE_SUCESSO,
                new Color(39, 174, 96),
                StopTheme.FUNDO,
                null
        );
        btnAdicionar.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnAdicionar.setBorder(new EmptyBorder(8, 16, 8, 16));

        Runnable adicionarCategoria = () -> {
            String nova = campoNovaCategoria.getText().trim().toUpperCase();
            if (nova.isEmpty()) {
                lblErro.setText("Digite o nome da categoria antes de adicionar.");
                campoNovaCategoria.requestFocusInWindow();
                return;
            }
            if (listaCategorias.contains(nova)) {
                lblErro.setText("Essa categoria já está na lista.");
                campoNovaCategoria.requestFocusInWindow();
                return;
            }
            listaCategorias.add(nova);
            campoNovaCategoria.setText("");
            lblErro.setText(" ");
            renderizarLista.run();
            campoNovaCategoria.requestFocusInWindow();
        };

        btnAdicionar.addActionListener(e -> adicionarCategoria.run());
        campoNovaCategoria.addActionListener(e -> adicionarCategoria.run());

        JPanel barraAdicionar = new JPanel(new BorderLayout(10, 0));
        barraAdicionar.setOpaque(false);
        barraAdicionar.add(campoNovaCategoria, BorderLayout.CENTER);
        barraAdicionar.add(btnAdicionar, BorderLayout.EAST);

        JPanel centro = new JPanel(new BorderLayout(0, 10));
        centro.setOpaque(false);
        centro.add(barraAdicionar, BorderLayout.NORTH);
        centro.add(scrollLista, BorderLayout.CENTER);
        centro.add(lblErro, BorderLayout.SOUTH);

        StopTheme.ModernButton btnCancelar = StopTheme.criarBotaoSecundario("Cancelar");
        StopTheme.ModernButton btnConfirmar = StopTheme.criarBotaoDestaque(textoBotaoConfirmar);

        btnCancelar.addActionListener(e -> {
            resultado[0] = null;
            dialog.dispose();
        });

        btnConfirmar.addActionListener(e -> {
            if (listaCategorias.isEmpty()) {
                lblErro.setText("Adicione pelo menos uma categoria para continuar.");
                return;
            }
            resultado[0] = new ArrayList<>(listaCategorias);
            dialog.dispose();
        });

        JPanel rodape = new JPanel(new GridLayout(1, 2, 12, 0));
        rodape.setOpaque(false);
        rodape.add(btnCancelar);
        rodape.add(btnConfirmar);

        card.add(topo, BorderLayout.NORTH);
        card.add(centro, BorderLayout.CENTER);
        card.add(rodape, BorderLayout.SOUTH);

        SwingUtilities.invokeLater(campoNovaCategoria::requestFocusInWindow);
        dialog.setVisible(true);
        return resultado[0];
    }

    private Integer abrirModalRodadas(
            String seloEtapa,
            String tituloEtapa,
            int valorInicial,
            int maximoPermitido,
            String textoBotaoConfirmar
    ) {
        final Integer[] resultado = {null};
        JDialog dialog = criarDialogoPadrao(seloEtapa + " - " + tituloEtapa, new Dimension(650, 390));
        StopTheme.RoundedPanel card = (StopTheme.RoundedPanel) ((BorderLayout) dialog.getContentPane().getLayout())
                .getLayoutComponent(BorderLayout.CENTER);

        JPanel topo = criarCabecalhoEtapa(
                seloEtapa,
                tituloEtapa,
                "Escolha quantas rodadas a partida terá (1 a " + maximoPermitido + ")."
        );

        int inicialSeguro = Math.max(1, Math.min(valorInicial, maximoPermitido));
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(inicialSeguro, 1, maximoPermitido, 1));
        spinner.setFont(new Font("Segoe UI", Font.BOLD, 22));
        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor defaultEditor) {
            StopTheme.estilizarCampo(defaultEditor.getTextField());
            defaultEditor.getTextField().setHorizontalAlignment(JTextField.CENTER);
            defaultEditor.getTextField().setFont(new Font("Segoe UI", Font.BOLD, 22));
        }

        JPanel centro = new JPanel(new BorderLayout(0, 8));
        centro.setOpaque(false);
        centro.setBorder(new EmptyBorder(12, 0, 12, 0));
        centro.add(StopTheme.criarLabelCampo("Quantidade de Rodadas"), BorderLayout.NORTH);
        centro.add(spinner, BorderLayout.CENTER);

        StopTheme.ModernButton btnCancelar = StopTheme.criarBotaoSecundario("Cancelar");
        StopTheme.ModernButton btnConfirmar = StopTheme.criarBotaoDestaque(textoBotaoConfirmar);

        btnCancelar.addActionListener(e -> {
            resultado[0] = null;
            dialog.dispose();
        });
        btnConfirmar.addActionListener(e -> {
            resultado[0] = (Integer) spinner.getValue();
            dialog.dispose();
        });

        JPanel rodape = new JPanel(new GridLayout(1, 2, 12, 0));
        rodape.setOpaque(false);
        rodape.add(btnCancelar);
        rodape.add(btnConfirmar);

        card.add(topo, BorderLayout.NORTH);
        card.add(centro, BorderLayout.CENTER);
        card.add(rodape, BorderLayout.SOUTH);

        dialog.setVisible(true);
        return resultado[0];
    }

    private List<String> abrirModalLetrasBotoes(
            String seloEtapa,
            String tituloEtapa,
            Set<String> letrasAtivasIniciais,
            String textoBotaoConfirmar
    ) {
        final List<String>[] resultado = new List[]{null};
        JDialog dialog = criarDialogoPadrao(seloEtapa + " - " + tituloEtapa, new Dimension(740, 500));
        StopTheme.RoundedPanel card = (StopTheme.RoundedPanel) ((BorderLayout) dialog.getContentPane().getLayout())
                .getLayoutComponent(BorderLayout.CENTER);

        JPanel topo = criarCabecalhoEtapa(
                seloEtapa,
                tituloEtapa,
                "As letras em verde estão ativas. Clique em qualquer letra que você NÃO quer para desativar."
        );

        JPanel gridLetras = new JPanel(new GridLayout(4, 7, 10, 10));
        gridLetras.setOpaque(false);
        gridLetras.setBorder(new EmptyBorder(8, 0, 8, 0));

        Map<String, Boolean> estadoLetras = new LinkedHashMap<>();
        Map<String, StopTheme.ModernButton> botoesLetras = new LinkedHashMap<>();

        for (int i = 0; i < 26; i++) {
            String letra = String.valueOf((char) ('A' + i));
            boolean ativa = letrasAtivasIniciais == null || letrasAtivasIniciais.contains(letra);
            estadoLetras.put(letra, ativa);

            StopTheme.ModernButton btnLetra = criarBotaoLetra(letra, ativa);
            btnLetra.addActionListener(e -> {
                boolean novoEstado = !estadoLetras.get(letra);
                estadoLetras.put(letra, novoEstado);
                aplicarEstiloBotaoLetra(btnLetra, novoEstado);
            });

            botoesLetras.put(letra, btnLetra);
            gridLetras.add(btnLetra);
        }

        StopTheme.ModernButton btnMarcarTodas = StopTheme.criarBotaoSecundario("Todas");
        btnMarcarTodas.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnMarcarTodas.addActionListener(e -> botoesLetras.forEach((l, b) -> {
            estadoLetras.put(l, true);
            aplicarEstiloBotaoLetra(b, true);
        }));

        StopTheme.ModernButton btnLimparTodas = StopTheme.criarBotaoSecundario("Nenhuma");
        btnLimparTodas.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnLimparTodas.addActionListener(e -> botoesLetras.forEach((l, b) -> {
            estadoLetras.put(l, false);
            aplicarEstiloBotaoLetra(b, false);
        }));

        gridLetras.add(btnMarcarTodas);
        gridLetras.add(btnLimparTodas);

        JLabel lblAviso = new JLabel(" ");
        lblAviso.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblAviso.setForeground(StopTheme.VERMELHO_STOP);

        StopTheme.ModernButton btnCancelar = StopTheme.criarBotaoSecundario("Cancelar");
        StopTheme.ModernButton btnConfirmar = StopTheme.criarBotaoDestaque(textoBotaoConfirmar);

        btnCancelar.addActionListener(e -> {
            resultado[0] = null;
            dialog.dispose();
        });

        btnConfirmar.addActionListener(e -> {
            List<String> escolhidas = new ArrayList<>();
            estadoLetras.forEach((l, ativa) -> {
                if (ativa) {
                    escolhidas.add(l);
                }
            });
            if (escolhidas.isEmpty()) {
                lblAviso.setText("Deixe pelo menos uma letra ativa para jogar.");
                return;
            }
            resultado[0] = escolhidas;
            dialog.dispose();
        });

        JPanel sul = new JPanel(new BorderLayout(0, 8));
        sul.setOpaque(false);
        sul.add(lblAviso, BorderLayout.NORTH);

        JPanel botoesRodape = new JPanel(new GridLayout(1, 2, 12, 0));
        botoesRodape.setOpaque(false);
        botoesRodape.add(btnCancelar);
        botoesRodape.add(btnConfirmar);
        sul.add(botoesRodape, BorderLayout.SOUTH);

        card.add(topo, BorderLayout.NORTH);
        card.add(gridLetras, BorderLayout.CENTER);
        card.add(sul, BorderLayout.SOUTH);

        dialog.setVisible(true);
        return resultado[0];
    }

    /**
     * Painel central de edição da sala: o usuário escolhe diretamente qual configuração
     * deseja alterar sem precisar percorrer etapas sequenciais.
     */
    private SalaEditadaDados abrirHubEdicaoSala(SalaDisponivel sala) {
        final SalaEditadaDados[] resultado = {null};
        final String[] nomeAtual = {sala.getNomeSala()};
        final List<String>[] categoriasEditadas = new List[]{new ArrayList<>(sala.getCategoriasList())};
        final List<String>[] letrasEditadas = new List[]{new ArrayList<>(sala.getLetrasHabilitadasList())};
        final int[] rodadasEditadas = {sala.getTotalRodadas()};

        JDialog dialog = criarDialogoPadrao("Editar Sala • " + sala.getNomeSala(), new Dimension(720, 560));
        StopTheme.RoundedPanel card = (StopTheme.RoundedPanel) ((BorderLayout) dialog.getContentPane().getLayout())
                .getLayoutComponent(BorderLayout.CENTER);

        JPanel topo = criarCabecalhoEtapa(
                "PAINEL DE EDIÇÃO",
                "Configurações da Sala",
                "Escolha diretamente qual seção você deseja editar e depois clique em Salvar Alterações."
        );

        JLabel lblValorNome = new JLabel(nomeAtual[0]);
        JLabel lblValorCategorias = new JLabel(String.join(", ", categoriasEditadas[0]));
        JLabel lblValorLetras = new JLabel(formatarResumoLetras(letrasEditadas[0]));
        JLabel lblValorRodadas = new JLabel(rodadasEditadas[0] + " rodada(s)");

        JPanel listaSecoes = new JPanel();
        listaSecoes.setLayout(new BoxLayout(listaSecoes, BoxLayout.Y_AXIS));
        listaSecoes.setOpaque(false);

        listaSecoes.add(criarCartaoSecaoEdicao(
                "1. NOME DA SALA",
                lblValorNome,
                () -> {
                    String novoNome = abrirModalTexto(
                            "Editar Sala",
                            "Nome da Sala",
                            "Altere o nome exibido para esta sala no lobby.",
                            "Nome da sala",
                            nomeAtual[0],
                            "Aplicar"
                    );
                    if (novoNome != null) {
                        nomeAtual[0] = novoNome;
                        lblValorNome.setText(novoNome);
                    }
                }
        ));
        listaSecoes.add(Box.createRigidArea(new Dimension(0, 10)));

        listaSecoes.add(criarCartaoSecaoEdicao(
                "2. CATEGORIAS DA PARTIDA",
                lblValorCategorias,
                () -> {
                    List<String> novasCategorias = abrirModalCategorias(
                            "Editar Sala",
                            "Categorias da Partida",
                            categoriasEditadas[0],
                            "Aplicar"
                    );
                    if (novasCategorias != null) {
                        categoriasEditadas[0] = novasCategorias;
                        lblValorCategorias.setText(String.join(", ", novasCategorias));
                    }
                }
        ));
        listaSecoes.add(Box.createRigidArea(new Dimension(0, 10)));

        listaSecoes.add(criarCartaoSecaoEdicao(
                "3. LETRAS HABILITADAS",
                lblValorLetras,
                () -> {
                    List<String> novasLetras = abrirModalLetrasBotoes(
                            "Editar Sala",
                            "Letras Habilitadas",
                            new HashSet<>(letrasEditadas[0]),
                            "Aplicar"
                    );
                    if (novasLetras != null) {
                        letrasEditadas[0] = novasLetras;
                        lblValorLetras.setText(formatarResumoLetras(novasLetras));
                        if (rodadasEditadas[0] > novasLetras.size()) {
                            rodadasEditadas[0] = novasLetras.size();
                            lblValorRodadas.setText(rodadasEditadas[0] + " rodada(s) (ajustado ao limite de letras)");
                        }
                    }
                }
        ));
        listaSecoes.add(Box.createRigidArea(new Dimension(0, 10)));

        listaSecoes.add(criarCartaoSecaoEdicao(
                "4. QUANTIDADE DE RODADAS",
                lblValorRodadas,
                () -> {
                    Integer novasRodadas = abrirModalRodadas(
                            "Editar Sala",
                            "Quantidade de Rodadas",
                            rodadasEditadas[0],
                            letrasEditadas[0].size(),
                            "Aplicar"
                    );
                    if (novasRodadas != null) {
                        rodadasEditadas[0] = novasRodadas;
                        lblValorRodadas.setText(novasRodadas + " rodada(s)");
                    }
                }
        ));

        JScrollPane scroll = new JScrollPane(listaSecoes);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(StopTheme.CARD);
        scroll.setOpaque(false);

        StopTheme.ModernButton btnCancelar = StopTheme.criarBotaoSecundario("Cancelar");
        StopTheme.ModernButton btnSalvar = StopTheme.criarBotaoDestaque("Salvar Alterações");

        btnCancelar.addActionListener(e -> {
            resultado[0] = null;
            dialog.dispose();
        });

        btnSalvar.addActionListener(e -> {
            resultado[0] = new SalaEditadaDados(
                    nomeAtual[0],
                    categoriasEditadas[0],
                    rodadasEditadas[0],
                    letrasEditadas[0]
            );
            dialog.dispose();
        });

        JPanel rodape = new JPanel(new GridLayout(1, 2, 12, 0));
        rodape.setOpaque(false);
        rodape.add(btnCancelar);
        rodape.add(btnSalvar);

        card.add(topo, BorderLayout.NORTH);
        card.add(scroll, BorderLayout.CENTER);
        card.add(rodape, BorderLayout.SOUTH);

        dialog.setVisible(true);
        return resultado[0];
    }

    private JPanel criarCartaoSecaoEdicao(String tituloSecao, JLabel lblResumoValor, Runnable aoClicarEditar) {
        StopTheme.RoundedPanel bloco = new StopTheme.RoundedPanel(12, StopTheme.CARD_ELEVADO, StopTheme.BORDA);
        bloco.setLayout(new BorderLayout(14, 0));
        bloco.setBorder(new EmptyBorder(12, 16, 12, 14));
        bloco.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));

        JPanel textos = new JPanel(new GridLayout(2, 1, 0, 4));
        textos.setOpaque(false);

        JLabel lblTitulo = new JLabel(tituloSecao);
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblTitulo.setForeground(StopTheme.TURQUESA);

        lblResumoValor.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblResumoValor.setForeground(StopTheme.TEXTO);

        textos.add(lblTitulo);
        textos.add(lblResumoValor);

        StopTheme.ModernButton btnEditar = StopTheme.criarBotaoSecundario("Editar");
        btnEditar.setPreferredSize(new Dimension(105, 38));
        btnEditar.addActionListener(e -> aoClicarEditar.run());

        bloco.add(textos, BorderLayout.CENTER);
        bloco.add(btnEditar, BorderLayout.EAST);
        return bloco;
    }

    private String formatarResumoLetras(List<String> letras) {
        if (letras.size() == 26) {
            return "Todas as 26 letras (A – Z)";
        }
        return letras.size() + " letra(s): " + String.join(", ", letras);
    }

    private static class SalaEditadaDados {
        final String nomeSala;
        final List<String> categorias;
        final int totalRodadas;
        final List<String> letrasHabilitadas;

        SalaEditadaDados(String nomeSala, List<String> categorias, int totalRodadas, List<String> letrasHabilitadas) {
            this.nomeSala = nomeSala;
            this.categorias = categorias;
            this.totalRodadas = totalRodadas;
            this.letrasHabilitadas = letrasHabilitadas;
        }
    }

    private StopTheme.ModernButton criarBotaoLetra(String letra, boolean ativa) {
        StopTheme.ModernButton btn = new StopTheme.ModernButton(
                letra,
                StopTheme.VERDE_SUCESSO,
                new Color(39, 174, 96),
                StopTheme.FUNDO,
                null
        );
        btn.setFont(new Font("Segoe UI", Font.BOLD, 16));
        btn.setBorder(new EmptyBorder(8, 8, 8, 8));
        aplicarEstiloBotaoLetra(btn, ativa);
        return btn;
    }

    private void aplicarEstiloBotaoLetra(StopTheme.ModernButton btn, boolean ativa) {
        if (ativa) {
            btn.setColors(StopTheme.VERDE_SUCESSO, new Color(39, 174, 96), StopTheme.FUNDO);
        } else {
            btn.setColors(StopTheme.VERMELHO_STOP, new Color(192, 57, 43), Color.WHITE);
        }
    }

    // ========================================================================
    // AÇÕES DO CLIENTE (Despachadas fora da EDT para não travar a tela)
    // ========================================================================
    private void acaoConectar() {
        String host = txtHost.getText().trim();
        int porta = Integer.parseInt(txtPorta.getText().trim());
        String nome = txtNome.getText().trim();

        SalaDisponivel salaSelecionada = (SalaDisponivel) comboSalas.getSelectedItem();

        if (salaSelecionada == null) {
            JOptionPane.showMessageDialog(
                    this,
                    "Atualize a lista e selecione uma sala antes de conectar.",
                    "Sala não selecionada",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        salvarServidorPreferido(host, porta);

        new Thread(() -> {
            rede = new GameGrpcClient(host, porta);
            boolean conectado = rede.conectar(nome, salaSelecionada.getIdSala());

            SwingUtilities.invokeLater(() -> {
                if (conectado) {
                    btnConectar.setEnabled(false);
                    txtNome.setEditable(false);
                    btnPronto.setEnabled(true);
                    lblStatusLobby.setText("Conectado! Clique em 'Marcar Estou Pronto!'.");
                    rede.iniciarEscutaEventos(this::processarEventoServidor);
                } else {
                    JOptionPane.showMessageDialog(this, "Não foi possível conectar ao servidor.", "Erro de Rede", JOptionPane.ERROR_MESSAGE);
                }
            });
        }).start();
    }

    private void acaoMarcarPronto() {
        new Thread(() -> {
            rede.marcarPronto();
            SwingUtilities.invokeLater(() -> {
                btnPronto.setEnabled(false);
                lblStatusLobby.setText("Aguardando os outros jogadores ficarem prontos...");
            });
        }).start();
    }

    private void acaoDispararStop() {
        if (!respostasCompletas()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Preencha todas as categorias antes de acionar STOP.",
                    "Respostas incompletas",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        btnStop.setEnabled(false);
        lblStatusRodada.setText("Enviando STOP ao servidor...");

        new Thread(() -> {
            Map<String, String> respostas = coletarRespostasLocais();
            rede.submeterPalavras(respostas);
            boolean venceu = rede.declararStop();

            SwingUtilities.invokeLater(() -> {
                if (venceu) {
                    lblStatusRodada.setText("VOCÊ DEU STOP! Aguardando fim da rodada...");
                } else {
                    lblStatusRodada.setText("Outro jogador acionou o STOP primeiro!");
                }
            });
        }).start();
    }

    private void acaoEnviarVotos() {
        btnEnviarVotos.setEnabled(false);

        List<VotoItem> votos = new ArrayList<>();
        for (ItemValidacaoUI item : itensParaVotar) {
            votos.add(VotoItem.newBuilder()
                    .setIdJogadorAlvo(item.idJogadorAlvo)
                    .setCategoria(item.categoria)
                    .setAprovado(item.aprovado)
                    .build());
        }

        new Thread(() -> {
            boolean enviados = rede.submeterVotos(votos);

            SwingUtilities.invokeLater(() -> {
                btnEnviarVotos.setText(enviados
                        ? "Votos enviados — aguardando os demais jogadores"
                        : "Não foi possível enviar os votos");
            });
        }).start();
    }

    private Map<String, String> coletarRespostasLocais() {
        Map<String, String> mapa = new HashMap<>();
        camposRespostas.forEach((cat, txt) -> mapa.put(cat, txt.getText().trim()));
        return mapa;
    }

    // ========================================================================
    // PROCESSADOR DE EVENTOS gRPC (Garante execução na EDT com invokeLater)
    // ========================================================================
    private void processarEventoServidor(EventoJogo evento) {
        SwingUtilities.invokeLater(() -> {
            switch (evento.getTipo()) {
                case INICIO_RODADA:
                    montarTelaDeJogo(evento.getLetraSorteada(), evento.getCategoriasList());
                    iniciarTemporizador(
                            lblTemporizadorRodada,
                            "Tempo da rodada: ",
                            evento.getDuracaoSegundos()
                    );
                    cardLayout.show(painelPrincipal, "JOGO");
                    break;

                case STOP_ACIONADO:
                    pararTemporizador();
                    lblTemporizadorRodada.setText("Tempo pausado!");
                    lblStatusRodada.setText("STOP acionado por: " + evento.getJogadorQueParou() + "! Finalizando...");
                    btnStop.setEnabled(false);
                    new Thread(() -> rede.submeterPalavras(coletarRespostasLocais())).start();
                    break;

                case INICIO_VALIDACAO:
                    montarTelaValidacao(evento.getTodasRespostasList());
                    iniciarTemporizador(
                            lblTemporizadorVotacao,
                            "Tempo para votar: ",
                            evento.getDuracaoSegundos()
                    );
                    cardLayout.show(painelPrincipal, "VALIDACAO");
                    break;

                case FIM_RODADA:
                case FIM_JOGO:
                    pararTemporizador();
                    montarTelaPlacar(
                            evento.getPlacarJogadoresList(),
                            evento.getTipo() == TipoEvento.FIM_JOGO
                    );
                    cardLayout.show(painelPrincipal, "PLACAR");
                    break;

                case TEMPO_RODADA_ESGOTADO:
                    pararTemporizador();
                    lblTemporizadorRodada.setText("Tempo da rodada: 00:00");
                    lblStatusRodada.setText("Tempo esgotado! Preparando validação...");
                    btnStop.setEnabled(false);
                    new Thread(() -> rede.submeterPalavras(coletarRespostasLocais())).start();
                    break;

                default:
                    break;
            }
        });
    }

    private void montarTelaDeJogo(String letra, List<String> categorias) {
        lblLetraSorteada.setText("LETRA: [ " + letra + " ]");
        lblStatusRodada.setText("Preencha as palavras e aperte STOP quando terminar!");
        btnStop.setEnabled(false);

        painelCategorias.removeAll();
        camposRespostas.clear();
        categoriasAtuais.clear();
        categoriasAtuais.addAll(categorias);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        for (String cat : categorias) {
            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.weightx = 0.3;
            JLabel lblCat = new JLabel(cat + ":");
            lblCat.setFont(new Font("Segoe UI", Font.BOLD, 15));
            lblCat.setForeground(StopTheme.TEXTO);
            painelCategorias.add(lblCat, gbc);

            gbc.gridx = 1;
            gbc.weightx = 0.7;
            JTextField txt = new JTextField();
            StopTheme.estilizarCampo(txt);
            camposRespostas.put(cat, txt);
            txt.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    atualizarDisponibilidadeStop();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    atualizarDisponibilidadeStop();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    atualizarDisponibilidadeStop();
                }
            });
            painelCategorias.add(txt, gbc);
            row++;
        }

        atualizarDisponibilidadeStop();
        painelCategorias.revalidate();
        painelCategorias.repaint();
    }

    private void montarTelaValidacao(List<RespostaConsolidada> respostas) {
        painelItensValidacao.removeAll();
        itensParaVotar.clear();
        btnEnviarVotos.setEnabled(true);
        btnEnviarVotos.setText("Confirmar Votos");

        List<RespostaConsolidada> adversarios = new ArrayList<>();
        for (RespostaConsolidada r : respostas) {
            if (!r.getIdJogador().equals(rede.getIdJogador())) {
                adversarios.add(r);
            }
        }

        int colunas = Math.max(1, Math.min(2, adversarios.size()));
        painelItensValidacao.setLayout(new GridLayout(0, colunas, 16, 16));

        for (RespostaConsolidada r : adversarios) {
            StopTheme.RoundedPanel cardJogador = new StopTheme.RoundedPanel(14, StopTheme.CARD, StopTheme.BORDA);
            cardJogador.setLayout(new BorderLayout(0, 10));
            cardJogador.setBorder(new EmptyBorder(16, 18, 16, 18));

            JLabel lblJogador = new JLabel(r.getNomeJogador().toUpperCase());
            lblJogador.setFont(new Font("Segoe UI", Font.BOLD, 15));
            lblJogador.setForeground(StopTheme.TURQUESA);
            cardJogador.add(lblJogador, BorderLayout.NORTH);

            JPanel gridPalavras = new JPanel(new GridLayout(0, 2, 10, 10));
            gridPalavras.setOpaque(false);

            r.getRespostasMap().forEach((categoria, palavra) -> {
                boolean temPalavra = palavra != null && !palavra.isBlank();
                String textoPalavra = temPalavra ? palavra.trim().toUpperCase() : "EM BRANCO";

                ItemValidacaoUI item = new ItemValidacaoUI(r.getIdJogador(), categoria, temPalavra);
                itensParaVotar.add(item);

                StopTheme.ModernButton btnPalavra = new StopTheme.ModernButton(
                        "<html><center><span style='font-size:10px;opacity:0.9;'>" + categoria + "</span><br/>"
                                + "<span style='font-size:14px;'>" + textoPalavra + "</span></center></html>",
                        temPalavra ? StopTheme.VERDE_SUCESSO : StopTheme.VERMELHO_STOP,
                        temPalavra ? new Color(39, 174, 96) : new Color(192, 57, 43),
                        Color.WHITE,
                        null
                );
                btnPalavra.setPreferredSize(new Dimension(140, 56));

                if (!temPalavra) {
                    btnPalavra.setEnabled(false);
                } else {
                    btnPalavra.addActionListener(e -> {
                        item.aprovado = !item.aprovado;
                        if (item.aprovado) {
                            btnPalavra.setColors(StopTheme.VERDE_SUCESSO, new Color(39, 174, 96), Color.WHITE);
                        } else {
                            btnPalavra.setColors(StopTheme.VERMELHO_STOP, new Color(192, 57, 43), Color.WHITE);
                        }
                    });
                }

                gridPalavras.add(btnPalavra);
            });

            cardJogador.add(gridPalavras, BorderLayout.CENTER);
            painelItensValidacao.add(cardJogador);
        }

        painelItensValidacao.revalidate();
        painelItensValidacao.repaint();
    }

    private void montarTelaPlacar(List<JogadorPontuacao> placar, boolean fimDeJogo) {
        painelPlacar.removeAll();
        lblTituloPlacar.setText(fimDeJogo ? "★ PLACAR FINAL DA PARTIDA ★" : "CLASSIFICAÇÃO PARCIAL");

        List<JogadorPontuacao> ordenado = new ArrayList<>(placar);
        ordenado.sort((a, b) -> Integer.compare(b.getPontuacao(), a.getPontuacao()));

        int posicao = 1;
        for (JogadorPontuacao jogador : ordenado) {
            boolean lider = (posicao == 1);
            StopTheme.RoundedPanel cardLinha = new StopTheme.RoundedPanel(
                    12,
                    lider ? StopTheme.CARD_ELEVADO : StopTheme.CARD,
                    lider ? StopTheme.DOURADO : StopTheme.BORDA
            );
            cardLinha.setLayout(new BorderLayout());
            cardLinha.setBorder(new EmptyBorder(14, 20, 14, 20));
            cardLinha.setMaximumSize(new Dimension(640, 56));

            JLabel lblNome = new JLabel(posicao + "º Lugar  •  " + jogador.getNomeJogador());
            lblNome.setFont(new Font("Segoe UI", Font.BOLD, 16));
            lblNome.setForeground(lider ? StopTheme.DOURADO : StopTheme.TEXTO);

            JLabel lblPontos = new JLabel(jogador.getPontuacao() + " pts");
            lblPontos.setFont(new Font("Segoe UI", Font.BOLD, 17));
            lblPontos.setForeground(StopTheme.VERDE_SUCESSO);

            cardLinha.add(lblNome, BorderLayout.WEST);
            cardLinha.add(lblPontos, BorderLayout.EAST);

            painelPlacar.add(cardLinha);
            painelPlacar.add(Box.createRigidArea(new Dimension(0, 10)));
            posicao++;
        }

        painelPlacar.revalidate();
        painelPlacar.repaint();
    }

    private static class ItemValidacaoUI {
        final String idJogadorAlvo;
        final String categoria;
        boolean aprovado;

        ItemValidacaoUI(String idJogadorAlvo, String categoria, boolean aprovadoInicial) {
            this.idJogadorAlvo = idJogadorAlvo;
            this.categoria = categoria;
            this.aprovado = aprovadoInicial;
        }
    }

    private void iniciarTemporizador(JLabel label, String prefixo, int duracaoSegundos) {
        pararTemporizador();

        fimTemporizadorMs = System.currentTimeMillis() + duracaoSegundos * 1000L;
        atualizarTextoTemporizador(label, prefixo);

        timerContagem = new javax.swing.Timer(250, e -> {
            long restanteMs = fimTemporizadorMs - System.currentTimeMillis();
            atualizarTextoTemporizador(label, prefixo);

            if (restanteMs <= 0) {
                pararTemporizador();
            }
        });

        timerContagem.start();
    }

    private void atualizarTextoTemporizador(JLabel label, String prefixo) {
        long restanteSegundos = Math.max(
                0,
                (fimTemporizadorMs - System.currentTimeMillis() + 999) / 1000
        );
        long minutos = restanteSegundos / 60;
        long segundos = restanteSegundos % 60;

        label.setText(String.format("%s%02d:%02d", prefixo, minutos, segundos));
    }

    private void pararTemporizador() {
        if (timerContagem != null) {
            timerContagem.stop();
            timerContagem = null;
        }
    }

    private boolean respostasCompletas() {
        return !camposRespostas.isEmpty()
                && camposRespostas.values().stream()
                .allMatch(campo -> !campo.getText().isBlank());
    }

    private void atualizarDisponibilidadeStop() {
        btnStop.setEnabled(respostasCompletas());
    }

    private void acaoCriarSala() {
        String nomeSala = abrirModalTexto(
                "Criar Sala  •  Etapa 1 de 4",
                "Nome da Sala",
                "Defina um nome para identificar a sua sala no lobby.",
                "Nome da sala",
                "",
                "Próximo"
        );
        if (nomeSala == null) return;

        List<String> categorias = abrirModalCategorias(
                "Criar Sala  •  Etapa 2 de 4",
                "Categorias da Partida",
                List.of("NOME", "ANIMAL", "COR"),
                "Próximo"
        );
        if (categorias == null) return;

        List<String> letras = abrirModalLetrasBotoes(
                "Criar Sala  •  Etapa 3 de 4",
                "Letras Habilitadas",
                null,
                "Próximo"
        );
        if (letras == null) return;

        Integer totalRodadas = abrirModalRodadas(
                "Criar Sala  •  Etapa 4 de 4",
                "Quantidade de Rodadas",
                Math.min(3, letras.size()),
                letras.size(),
                "Concluir"
        );
        if (totalRodadas == null) return;

        String host = txtHost.getText().trim();
        int porta;

        try {
            porta = Integer.parseInt(txtPorta.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Informe uma porta válida.");
            return;
        }

        salvarServidorPreferido(host, porta);

        btnCriarSala.setEnabled(false);

        new Thread(() -> {
            GameGrpcClient clienteTemporario = new GameGrpcClient(host, porta);

            try {
                CriarSalaResponse resposta = clienteTemporario.criarSala(
                        nomeSala.trim(),
                        categorias,
                        totalRodadas,
                        letras
                );

                SwingUtilities.invokeLater(() -> {
                    btnCriarSala.setEnabled(true);

                    if (resposta.getSucesso()) {
                        tokensSalasCriadas.put(
                                resposta.getIdSala(),
                                resposta.getTokenCriador());
                        JOptionPane.showMessageDialog(this, "Sala criada com sucesso.");
                        acaoAtualizarSalas();
                    } else {
                        JOptionPane.showMessageDialog(
                                this,
                                resposta.getMensagem(),
                                "Não foi possível criar a sala",
                                JOptionPane.WARNING_MESSAGE
                        );
                    }
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    btnCriarSala.setEnabled(true);
                    JOptionPane.showMessageDialog(
                            this,
                            "Falha ao criar a sala: " + e.getMessage(),
                            "Erro de conexão",
                            JOptionPane.ERROR_MESSAGE
                    );
                });
            } finally {
                clienteTemporario.desconectar();
            }
        }).start();
    }

    private void acaoAtualizarSalas() {
        String host = txtHost.getText().trim();
        int porta;

        try {
            porta = Integer.parseInt(txtPorta.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(
                    this,
                    "Informe uma porta válida.",
                    "Porta inválida",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        salvarServidorPreferido(host, porta);

        btnAtualizarSalas.setEnabled(false);
        lblStatusLobby.setText("Buscando salas...");

        new Thread(() -> {
            GameGrpcClient clienteTemporario = new GameGrpcClient(host, porta);

            try {
                List<SalaDisponivel> salas = clienteTemporario.listarSalas();

                SwingUtilities.invokeLater(() -> {
                    comboSalas.removeAllItems();
                    salas.forEach(comboSalas::addItem);

                    lblStatusLobby.setText(salas.isEmpty()
                            ? "Nenhuma sala criada. Crie uma sala para começar."
                            : "Selecione uma sala e conecte-se.");

                    btnAtualizarSalas.setEnabled(true);
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    lblStatusLobby.setText("Não foi possível buscar as salas.");
                    btnAtualizarSalas.setEnabled(true);
                    JOptionPane.showMessageDialog(
                            this,
                            "Falha ao consultar o servidor: " + e.getMessage(),
                            "Erro de conexão",
                            JOptionPane.ERROR_MESSAGE
                    );
                });
            } finally {
                clienteTemporario.desconectar();
            }
        }).start();
    }

    private void acaoEditarSala() {
        SalaDisponivel sala = (SalaDisponivel) comboSalas.getSelectedItem();

        if (sala == null) {
            return;
        }

        String tokenCriador = tokensSalasCriadas.get(sala.getIdSala());
        if (tokenCriador == null) {
            JOptionPane.showMessageDialog(
                    this,
                    "Você não tem permissão para editar esta sala.",
                    "Edição não autorizada",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        SalaEditadaDados dadosEditados = abrirHubEdicaoSala(sala);
        if (dadosEditados == null) {
            return;
        }

        String host = txtHost.getText().trim();
        int porta;

        try {
            porta = Integer.parseInt(txtPorta.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Informe uma porta válida.");
            return;
        }

        salvarServidorPreferido(host, porta);

        btnEditarSala.setEnabled(false);

        new Thread(() -> {
            GameGrpcClient clienteTemporario = new GameGrpcClient(host, porta);

            try {
                EditarSalaResponse resposta = clienteTemporario.editarSala(
                        sala.getIdSala(),
                        tokenCriador,
                        dadosEditados.nomeSala,
                        dadosEditados.categorias,
                        dadosEditados.totalRodadas,
                        dadosEditados.letrasHabilitadas
                );

                SwingUtilities.invokeLater(() -> {
                    if (resposta.getSucesso()) {
                        JOptionPane.showMessageDialog(this, "Sala atualizada.");
                        acaoAtualizarSalas();
                    } else {
                        btnEditarSala.setEnabled(true);
                        JOptionPane.showMessageDialog(
                                this,
                                resposta.getMensagem(),
                                "Não foi possível editar a sala",
                                JOptionPane.WARNING_MESSAGE
                        );
                    }
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    btnEditarSala.setEnabled(true);
                    JOptionPane.showMessageDialog(
                            this,
                            "Falha ao editar a sala: " + e.getMessage(),
                            "Erro de conexão",
                            JOptionPane.ERROR_MESSAGE
                    );
                });
            } finally {
                clienteTemporario.desconectar();
            }
        }).start();
    }

    private void atualizarDisponibilidadeEdicaoSala() {
        SalaDisponivel salaSelecionada =
                (SalaDisponivel) comboSalas.getSelectedItem();

        boolean possoEditar = salaSelecionada != null
                && "LOBBY".equalsIgnoreCase(salaSelecionada.getEstado())
                && tokensSalasCriadas.containsKey(salaSelecionada.getIdSala());

        btnEditarSala.setEnabled(possoEditar);
    }

    private void salvarServidorPreferido(String host, int porta) {
        PREFERENCIAS.put("servidor.host", host.trim());
        PREFERENCIAS.putInt("servidor.porta", porta);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            StopGameUI tela = new StopGameUI();
            tela.setVisible(true);
        });
    }
}