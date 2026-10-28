package com.distribuidos.stop.client.ui;

import com.distribuidos.stop.EventoJogo;
import com.distribuidos.stop.RespostaConsolidada;
import com.distribuidos.stop.TipoEvento;
import com.distribuidos.stop.VotoItem;
import com.distribuidos.stop.client.network.GameGrpcClient;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.*;
import java.util.List;

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

    // Componentes da Tela de Jogo (RODADA)
    private JLabel lblLetraSorteada;
    private JLabel lblStatusRodada;
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

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2; painel.add(lblTitulo, gbc);
        gbc.gridy = 1; gbc.gridwidth = 1; painel.add(new JLabel("Nome do Jogador:"), gbc);
        gbc.gridx = 1; painel.add(txtNome, gbc);
        gbc.gridx = 0; gbc.gridy = 2; painel.add(new JLabel("IP do Servidor:"), gbc);
        gbc.gridx = 1; painel.add(txtHost, gbc);
        gbc.gridx = 0; gbc.gridy = 3; painel.add(new JLabel("Porta TCP:"), gbc);
        gbc.gridx = 1; painel.add(txtPorta, gbc);
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2; painel.add(btnConectar, gbc);
        gbc.gridy = 5; painel.add(btnPronto, gbc);
        gbc.gridy = 6; painel.add(lblStatusLobby, gbc);

        return painel;
    }

    // ========================================================================
    // 2. TELA DE JOGO ATIVO
    // ========================================================================
    private JPanel criarTelaJogo() {
        JPanel painel = new JPanel(new BorderLayout(15, 15));
        painel.setBorder(new EmptyBorder(20, 30, 20, 30));

        JPanel topo = new JPanel(new GridLayout(2, 1));
        lblLetraSorteada = new JLabel("LETRA: ?", SwingConstants.CENTER);
        lblLetraSorteada.setFont(new Font("Arial", Font.BOLD, 42));
        lblLetraSorteada.setForeground(new Color(220, 53, 69));

        lblStatusRodada = new JLabel("Preencha todas as categorias e clique em STOP!", SwingConstants.CENTER);
        lblStatusRodada.setFont(new Font("Arial", Font.PLAIN, 14));
        topo.add(lblLetraSorteada);
        topo.add(lblStatusRodada);

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

        btnEnviarVotos = new JButton("Confirmar Votos");
        btnEnviarVotos.setFont(new Font("Arial", Font.BOLD, 16));
        btnEnviarVotos.setBackground(new Color(26, 115, 232));
        btnEnviarVotos.setForeground(Color.WHITE);
        btnEnviarVotos.addActionListener(e -> acaoEnviarVotos());

        painel.add(lblTitulo, BorderLayout.NORTH);
        painel.add(scroll, BorderLayout.CENTER);
        painel.add(btnEnviarVotos, BorderLayout.SOUTH);

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

        new Thread(() -> {
            rede = new GameGrpcClient(host, porta);
            boolean conectado = rede.conectar(nome);

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
            rede.submeterVotos(votos);
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this, "Votos computados! Aguardando apuração do servidor.", "Votação Concluída", JOptionPane.INFORMATION_MESSAGE);
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
                    cardLayout.show(painelPrincipal, "JOGO");
                    break;

                case STOP_ACIONADO:
                    lblStatusRodada.setText("STOP acionado por: " + evento.getJogadorQueParou() + "! Finalizando...");
                    btnStop.setEnabled(false);
                    // Garante submissão das palavras que já foram digitadas
                    new Thread(() -> rede.submeterPalavras(coletarRespostasLocais())).start();
                    break;

                case INICIO_VALIDACAO:
                    montarTelaValidacao(evento.getTodasRespostasList());
                    cardLayout.show(painelPrincipal, "VALIDACAO");
                    break;

                case FIM_RODADA:
                case FIM_JOGO:
                    montarTelaPlacar(evento.getPontuacaoGeralMap(), evento.getTipo() == TipoEvento.FIM_JOGO);
                    cardLayout.show(painelPrincipal, "PLACAR");
                    break;

                default:
                    break;
            }
        });
    }

    private void montarTelaDeJogo(String letra, List<String> categorias) {
        lblLetraSorteada.setText("LETRA: [ " + letra + " ]");
        lblStatusRodada.setText("Preencha as palavras e aperte STOP quando terminar!");
        btnStop.setEnabled(true);

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
            painelCategorias.add(txt, gbc);
            row++;
        }

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

    private void montarTelaPlacar(Map<String, Integer> placar, boolean fimDeJogo) {
        painelPlacar.removeAll();
        lblTituloPlacar.setText(fimDeJogo ? "🏆 FIM DE JOGO - PLACAR FINAL 🏆" : "Classificação Parcial da Partida");

        List<Map.Entry<String, Integer>> ordenado = new ArrayList<>(placar.entrySet());
        ordenado.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        int posicao = 1;
        for (Map.Entry<String, Integer> entry : ordenado) {
            JPanel cardLinha = new JPanel(new BorderLayout());
            cardLinha.setBorder(BorderFactory.createCompoundBorder(
                    new EmptyBorder(5, 5, 5, 5),
                    BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1)
            ));
            cardLinha.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));

            String rotulo = posicao + "º Lugar: Jogador [" + entry.getKey().substring(0, 4) + "]";
            JLabel lblNome = new JLabel(rotulo);
            lblNome.setFont(new Font("Arial", Font.BOLD, 15));

            JLabel lblPontos = new JLabel(entry.getValue() + " Pontos   ");
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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            StopGameUI tela = new StopGameUI();
            tela.setVisible(true);
        });
    }
}