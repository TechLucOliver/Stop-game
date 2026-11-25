package com.distribuidos.stop.client.ui;

import com.distribuidos.stop.*;
import com.distribuidos.stop.client.network.GameGrpcClient;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.*;
import java.util.List;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

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
        setSize(700, 600);
        setMinimumSize(new Dimension(600, 500));
        setLocationRelativeTo(null); // Centraliza na tela
        setLayout(new BorderLayout());
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
        JPanel painel = new JPanel(new GridBagLayout());
        painel.setBorder(new EmptyBorder(30, 40, 30, 40));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel lblTitulo = new JLabel("STOP! MULTIPLAYER", SwingConstants.CENTER);
        lblTitulo.setFont(new Font("Arial", Font.BOLD, 26));
        lblTitulo.setForeground(new Color(26, 115, 232));

        txtNome = new JTextField("Jogador_" + (int)(Math.random() * 900 + 100));
        txtHost = new JTextField("localhost");
        txtPorta = new JTextField("50051");

        comboSalas = new JComboBox<>();
        comboSalas.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> lista,
                    Object valor,
                    int indice,
                    boolean selecionado,
                    boolean foco
            ) {
                super.getListCellRendererComponent(
                        lista, valor, indice, selecionado, foco
                );

                if (valor instanceof SalaDisponivel sala) {
                    setText(sala.getNomeSala()
                            + " — "
                            + sala.getJogadoresConectados()
                            + " jogador(es) — "
                            + sala.getEstado());
                }

                return this;
            }
        });

        btnAtualizarSalas = new JButton("Atualizar salas");
        btnAtualizarSalas.addActionListener(e -> acaoAtualizarSalas());

        btnCriarSala = new JButton("Criar sala");
        btnCriarSala.addActionListener(e -> acaoCriarSala());

        btnEditarSala = new JButton("Editar sala selecionada");
        btnEditarSala.setEnabled(false);
        btnEditarSala.addActionListener(e -> acaoEditarSala());

        comboSalas.addActionListener(e -> atualizarDisponibilidadeEdicaoSala());

        btnConectar = new JButton("Conectar ao Servidor");
        btnConectar.setFont(new Font("Arial", Font.BOLD, 14));
        btnConectar.setBackground(new Color(26, 115, 232));
        btnConectar.setForeground(Color.WHITE);

        btnPronto = new JButton("Marcar Estou Pronto!");
        btnPronto.setFont(new Font("Arial", Font.BOLD, 14));
        btnPronto.setEnabled(false);

        lblStatusLobby = new JLabel("Preencha seus dados para entrar na sala.", SwingConstants.CENTER);
        lblStatusLobby.setFont(new Font("Arial", Font.ITALIC, 13));

        btnConectar.addActionListener(e -> acaoConectar());
        btnPronto.addActionListener(e -> acaoMarcarPronto());

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        painel.add(lblTitulo, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        painel.add(new JLabel("Nome do Jogador:"), gbc);
        gbc.gridx = 1;
        painel.add(txtNome, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        painel.add(new JLabel("IP do Servidor:"), gbc);
        gbc.gridx = 1;
        painel.add(txtHost, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        painel.add(new JLabel("Porta TCP:"), gbc);
        gbc.gridx = 1;
        painel.add(txtPorta, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        painel.add(new JLabel("Sala:"), gbc);
        gbc.gridx = 1;
        painel.add(comboSalas, gbc);

        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        painel.add(btnAtualizarSalas, gbc);

        gbc.gridy = 5;
        gbc.gridwidth = 2;
        painel.add(btnAtualizarSalas, gbc);

        gbc.gridy = 6;
        painel.add(btnCriarSala, gbc);

        gbc.gridy = 7;
        painel.add(btnEditarSala, gbc);

        gbc.gridy = 8;
        painel.add(btnConectar, gbc);

        gbc.gridy = 9;
        painel.add(btnPronto, gbc);

        gbc.gridy = 10;
        painel.add(lblStatusLobby, gbc);

        return painel;
    }

    // ========================================================================
    // 2. TELA DE JOGO ATIVO
    // ========================================================================
    private JPanel criarTelaJogo() {
        JPanel painel = new JPanel(new BorderLayout(15, 15));
        painel.setBorder(new EmptyBorder(20, 30, 20, 30));

        JPanel topo = new JPanel(new GridLayout(3, 1));
        lblLetraSorteada = new JLabel("LETRA: ?", SwingConstants.CENTER);
        lblLetraSorteada.setFont(new Font("Arial", Font.BOLD, 42));
        lblLetraSorteada.setForeground(new Color(220, 53, 69));

        lblStatusRodada = new JLabel("Preencha todas as categorias e clique em STOP!", SwingConstants.CENTER);
        lblStatusRodada.setFont(new Font("Arial", Font.PLAIN, 14));
        topo.add(lblLetraSorteada);
        topo.add(lblStatusRodada);

        lblTemporizadorRodada = new JLabel("Tempo da rodada: --:--", SwingConstants.CENTER);
        lblTemporizadorRodada.setFont(new Font("Arial", Font.BOLD, 18));
        topo.add(lblLetraSorteada);
        topo.add(lblStatusRodada);
        topo.add(lblTemporizadorRodada);

        painelCategorias = new JPanel(new GridBagLayout());
        JScrollPane scroll = new JScrollPane(painelCategorias);
        scroll.setBorder(BorderFactory.createTitledBorder("Categorias da Rodada"));

        btnStop = new JButton("STOP!");
        btnStop.setFont(new Font("Arial", Font.BOLD, 22));
        btnStop.setBackground(new Color(40, 167, 69));
        btnStop.setForeground(Color.WHITE);
        btnStop.setPreferredSize(new Dimension(200, 60));
        btnStop.addActionListener(e -> acaoDispararStop());

        painel.add(topo, BorderLayout.NORTH);
        painel.add(scroll, BorderLayout.CENTER);
        painel.add(btnStop, BorderLayout.SOUTH);

        return painel;
    }

    // ========================================================================
    // 3. TELA DE VALIDAÇÃO COMUNITÁRIA
    // ========================================================================
    private JPanel criarTelaValidacao() {
        JPanel painel = new JPanel(new BorderLayout(15, 15));
        painel.setBorder(new EmptyBorder(20, 30, 20, 30));

        JLabel lblTitulo = new JLabel("Validação Coletiva de Palavras", SwingConstants.CENTER);
        lblTitulo.setFont(new Font("Arial", Font.BOLD, 20));

        painelItensValidacao = new JPanel();
        painelItensValidacao.setLayout(new BoxLayout(painelItensValidacao, BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(painelItensValidacao);

        lblTemporizadorVotacao = new JLabel("Tempo para votar: --:--", SwingConstants.CENTER);
        lblTemporizadorVotacao.setFont(new Font("Arial", Font.BOLD, 16));

        btnEnviarVotos = new JButton("Confirmar Votos");
        btnEnviarVotos.setFont(new Font("Arial", Font.BOLD, 16));
        btnEnviarVotos.setBackground(new Color(26, 115, 232));
        btnEnviarVotos.setForeground(Color.WHITE);
        btnEnviarVotos.addActionListener(e -> acaoEnviarVotos());

        JPanel rodapeValidacao = new JPanel(new BorderLayout(8, 8));
        rodapeValidacao.add(lblTemporizadorVotacao, BorderLayout.NORTH);
        rodapeValidacao.add(btnEnviarVotos, BorderLayout.SOUTH);

        painel.add(lblTitulo, BorderLayout.NORTH);
        painel.add(scroll, BorderLayout.CENTER);
        painel.add(rodapeValidacao, BorderLayout.SOUTH);

        return painel;
    }

    // ========================================================================
    // 4. TELA DE PLACAR
    // ========================================================================
    private JPanel criarTelaPlacar() {
        JPanel painel = new JPanel(new BorderLayout(15, 15));
        painel.setBorder(new EmptyBorder(30, 40, 30, 40));

        lblTituloPlacar = new JLabel("Classificação da Partida", SwingConstants.CENTER);
        lblTituloPlacar.setFont(new Font("Arial", Font.BOLD, 24));

        painelPlacar = new JPanel();
        painelPlacar.setLayout(new BoxLayout(painelPlacar, BoxLayout.Y_AXIS));

        painel.add(lblTituloPlacar, BorderLayout.NORTH);
        painel.add(new JScrollPane(painelPlacar), BorderLayout.CENTER);

        return painel;
    }

    // ========================================================================
    // AÇÕES DO CLIENTE (Despachadas fora da EDT para não travar a tela)
    // ========================================================================
    private void acaoConectar() {
        String host = txtHost.getText().trim();
        int porta = Integer.parseInt(txtPorta.getText().trim());
        String nome = txtNome.getText().trim();

        SalaDisponivel salaSelecionada =
                (SalaDisponivel) comboSalas.getSelectedItem();

        if (salaSelecionada == null) {
            JOptionPane.showMessageDialog(
                    this,
                    "Atualize a lista e selecione uma sala antes de conectar.",
                    "Sala não selecionada",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

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
                    .setAprovado(item.chkAprovado.isSelected())
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
                    // Garante submissão das palavras que já foram digitadas
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
            gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3;
            JLabel lblCat = new JLabel(cat + ":");
            lblCat.setFont(new Font("Arial", Font.BOLD, 14));
            painelCategorias.add(lblCat, gbc);

            gbc.gridx = 1; gbc.weightx = 0.7;
            JTextField txt = new JTextField();
            txt.setFont(new Font("Arial", Font.PLAIN, 14));
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

        for (RespostaConsolidada r : respostas) {
            // Não vota nas próprias palavras
            if (r.getIdJogador().equals(rede.getIdJogador())) {
                continue;
            }

            JPanel cardJogador = new JPanel();
            cardJogador.setLayout(new BoxLayout(cardJogador, BoxLayout.Y_AXIS));
            cardJogador.setBorder(BorderFactory.createTitledBorder("Respostas de: " + r.getNomeJogador()));

            r.getRespostasMap().forEach((categoria, palavra) -> {
                JPanel linha = new JPanel(new FlowLayout(FlowLayout.LEFT));
                JCheckBox chk = new JCheckBox("Aprovar (" + categoria + ": " + (palavra.isEmpty() ? "<Em branco>" : palavra) + ")", !palavra.isEmpty());
                linha.add(chk);
                cardJogador.add(linha);
                itensParaVotar.add(new ItemValidacaoUI(r.getIdJogador(), categoria, chk));
            });

            painelItensValidacao.add(cardJogador);
            painelItensValidacao.add(Box.createRigidArea(new Dimension(0, 10)));
        }

        painelItensValidacao.revalidate();
        painelItensValidacao.repaint();
    }

    private void montarTelaPlacar(List<JogadorPontuacao> placar, boolean fimDeJogo) {
        painelPlacar.removeAll();
        lblTituloPlacar.setText(fimDeJogo ? "🏆 FIM DE JOGO - PLACAR FINAL 🏆" : "Classificação Parcial da Partida");

        List<JogadorPontuacao> ordenado = new ArrayList<>(placar);
        ordenado.sort((a, b) -> Integer.compare(b.getPontuacao(), a.getPontuacao()));

        int posicao = 1;
        for (JogadorPontuacao jogador : ordenado) {
            JPanel cardLinha = new JPanel(new BorderLayout());
            cardLinha.setBorder(BorderFactory.createCompoundBorder(
                    new EmptyBorder(5, 5, 5, 5),
                    BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1)
            ));
            cardLinha.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));

            JLabel lblNome = new JLabel(posicao + "º Lugar: " + jogador.getNomeJogador());
            lblNome.setFont(new Font("Arial", Font.BOLD, 15));

            JLabel lblPontos = new JLabel(jogador.getPontuacao() + " Pontos   ");
            lblPontos.setFont(new Font("Arial", Font.BOLD, 15));
            lblPontos.setForeground(new Color(40, 167, 69));

            cardLinha.add(lblNome, BorderLayout.WEST);
            cardLinha.add(lblPontos, BorderLayout.EAST);
            painelPlacar.add(cardLinha);
            posicao++;
        }

        painelPlacar.revalidate();
        painelPlacar.repaint();
    }

    private static class ItemValidacaoUI {
        String idJogadorAlvo;
        String categoria;
        JCheckBox chkAprovado;

        ItemValidacaoUI(String idJogadorAlvo, String categoria, JCheckBox chkAprovado) {
            this.idJogadorAlvo = idJogadorAlvo;
            this.categoria = categoria;
            this.chkAprovado = chkAprovado;
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
        String nomeSala = JOptionPane.showInputDialog(
                this, "Nome da sala:"
        );
        if (nomeSala == null) return;

        String categoriasTexto = JOptionPane.showInputDialog(
                this, "Categorias separadas por vírgula:", "NOME, ANIMAL, COR"
        );
        if (categoriasTexto == null) return;

        String rodadasTexto = JOptionPane.showInputDialog(
                this, "Quantidade de rodadas:", "3"
        );
        if (rodadasTexto == null) return;

        JPanel painelLetras = new JPanel(new GridLayout(0, 7, 5, 5));
        JCheckBox[] caixasLetras = new JCheckBox[26];

        for (int i = 0; i < 26; i++) {
            String letra = String.valueOf((char) ('A' + i));
            caixasLetras[i] = new JCheckBox(letra, true);
            painelLetras.add(caixasLetras[i]);
        }

        int resultadoLetras = JOptionPane.showConfirmDialog(
                this,
                new JScrollPane(painelLetras),
                "Selecione as letras habilitadas",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (resultadoLetras != JOptionPane.OK_OPTION) {
            return;
        }

        List<String> letras = new ArrayList<>();
        for (JCheckBox caixa : caixasLetras) {
            if (caixa.isSelected()) {
                letras.add(caixa.getText());
            }
        }

        if (letras.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Selecione pelo menos uma letra.",
                    "Nenhuma letra selecionada",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        int totalRodadas;
        try {
            totalRodadas = Integer.parseInt(rodadasTexto.trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Informe um número válido de rodadas.");
            return;
        }

        List<String> categorias = Arrays.stream(categoriasTexto.split(","))
                .map(String::trim)
                .filter(valor -> !valor.isEmpty())
                .toList();

        String host = txtHost.getText().trim();
        int porta;

        try {
            porta = Integer.parseInt(txtPorta.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Informe uma porta válida.");
            return;
        }

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
                            ? "Nenhuma sala criada. Na próxima etapa adicionaremos a criação pela tela."
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
        JOptionPane.showMessageDialog(
                this,
                "O formulário de edição será adicionado na próxima etapa."
        );
    }

    private void atualizarDisponibilidadeEdicaoSala() {
        SalaDisponivel salaSelecionada =
                (SalaDisponivel) comboSalas.getSelectedItem();

        boolean possoEditar = salaSelecionada != null
                && "LOBBY".equalsIgnoreCase(salaSelecionada.getEstado())
                && tokensSalasCriadas.containsKey(salaSelecionada.getIdSala());

        btnEditarSala.setEnabled(possoEditar);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            StopGameUI tela = new StopGameUI();
            tela.setVisible(true);
        });
    }
}