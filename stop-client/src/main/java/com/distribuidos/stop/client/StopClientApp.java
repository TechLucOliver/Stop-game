package com.distribuidos.stop.client;

import com.distribuidos.stop.EventoJogo;
import com.distribuidos.stop.RespostaConsolidada;
import com.distribuidos.stop.client.network.GameGrpcClient;

import java.util.*;

public class StopClientApp {

    private static final String HOST_PADRAO = "localhost";
    private static final int PORTA_PADRAO = 50051;

    private final GameGrpcClient rede;
    private final Scanner teclado;

    private volatile boolean rodadaAtiva = false;
    private volatile boolean emValidacao = false;
    private final List<String> categoriasAtuais = new ArrayList<>();
    private final Map<String, String> minhasRespostas = new HashMap<>();

    public StopClientApp(String host, int porta) {
        this.rede = new GameGrpcClient(host, porta);
        this.teclado = new Scanner(System.in);
    }

    public void iniciar(){
        System.out.println("**************************************************");
        System.out.println("*               BEM-VINDO AO STOP                *");
        System.out.println("**************************************************");

        System.out.print("Informe seu nome / apelido: ");
        String nome = teclado.nextLine().trim();

        boolean conectado = rede.conectar(nome);
        if (!conectado){
            System.err.println("Falha ao ligar ao servidor. Verifique se o StopServerApp está em execução.");
            return;
        }

        rede.iniciarEscutaEventos(this::processarEventoServidor);

        loopComando();

    }

    private void processarEventoServidor(EventoJogo evento){
        System.out.println("\n--------------------------------------------------");
        System.out.printf("[NOTIFICAÇÃO SERVIDOR] %s%n", evento.getMensagem());

        switch (evento.getTipo()){
            case JOGADOR_ENTROU:
                System.out.println(">> Um novo jogador entrou no lobby. Digite 'pronto' para jogar!");
                break;

            case INICIO_RODADA:
                this.rodadaAtiva = true;
                this.emValidacao = false;
                this.minhasRespostas.clear();
                this.categoriasAtuais.clear();
                this.categoriasAtuais.addAll(evento.getCategoriasList());

                System.out.printf(">>> LETRA SORTEADA: [ %s ] <<<%n", evento.getLetraSorteada());
                System.out.printf("Categorias: %s%n", evento.getCategoriasList());
                System.out.println(">> Digite 'jogar' para preencher as palavras ou 'stop' para parar!");
                break;

            case STOP_ACIONADO:
                this.rodadaAtiva = false;
                System.out.printf(">> STOP acionado por: %s! Janela de tolerância em curso...%n", evento.getJogadorQueParou());
                if (!minhasRespostas.isEmpty()){
                    rede.submeterPalavras(minhasRespostas);
                    System.out.println(">> As suas palavras foram submetidas ao servidor com sucesso.");
                }
                break;

            case INICIO_VALIDACAO:
                this.emValidacao = true;
                System.out.println(">>> FASE DE VALIDAÇÃO <<<");
                exibirRespostasParaValidacao(evento.getTodasRespostasList());
                System.out.println(">> Digite 'votar' para aprovar/rejeitar respostas dos jogadores.");
                break;

            case FIM_RODADA:
                this.rodadaAtiva = false;
                this.emValidacao = false;
                System.out.println(">>> PLACAR ATUALIZADO DA PARTIDA <<<");
                evento.getPontuacaoGeralMap().forEach((id, pontos) ->
                        System.out.printf(" - Jogador [%s]: %d pontos%n", id.substring(0, 4), pontos)
                );
                System.out.println(">> Aguarde o início da próxima rodada...");
                break;

            case FIM_JOGO:
                System.out.println("**************************************************");
                System.out.println("*      PARTIDA CONCLUÍDA! PLACAR FINAL:          *");
                evento.getPontuacaoGeralMap().forEach((id, pontos) ->
                        System.out.printf(" * Jogador [%s]: %d pontos%n", id.substring(0, 4), pontos)
                );
                System.out.println("**************************************************");
                System.out.println("Digite 'sair' para fechar o cliente.");
                break;

            default:
                break;
        }
        System.out.println("> ");
    }

    private void loopComando(){
        System.out.println("Comandos disponíveis: 'pronto', 'jogar', 'stop', 'votar', 'sair'");
        System.out.print("> ");

        while (true){
            String linha = teclado.nextLine().trim().toLowerCase();

            if (linha.equals("sair")){
                rede.desconectar();
                System.out.println("Cliente finalizado");
                break;
            }else if(linha.equals("pronto")){
                rede.marcarPronto();
                System.out.println("Status de prontidão enviado ao servidor. Aguardando o lobby...");
            } else if (linha.equals("jogar")) {
                if (!rodadaAtiva){
                    System.out.println("Nenhuma rodada ativa no momento");
                }else{
                    preencherPalavras();
                }
            } else if (linha.equals("stop")) {
                if (!rodadaAtiva){
                    System.out.println("Não é possível acionar STOP fora da rodada ativa.");
                }else{
                    rede.submeterPalavras(minhasRespostas);
                    boolean venceu = rede.declararStop();
                    if (venceu){
                        System.out.println("PARABÉNS! Foi o primeiro a acionar o STOP!");
                    }else{
                        System.out.println("Outro jogador acionou o STOP frações de segundo antes.");
                    }
                }
            } else if (linha.equals("votar")) {
                if (!emValidacao){
                    System.out.println("A partida não está na fase de validação.");
                }else {
                    System.out.println("Votos registados: todas as palavras válidas foram aprovadas.");
                }
            }
            System.out.println("> ");
        }
    }

    private void preencherPalavras(){
        System.out.println("\n--- Preenchimento das Categorias ---");
        for (String categoria : categoriasAtuais){
            System.out.printf("Digite para [%s]: ", categoria);
            String resposta = teclado.nextLine().trim();
            minhasRespostas.put(categoria, resposta);
        }
        System.out.println("Palavras gravadas localmente! Quando terminar, digite 'stop'.");
    }

    private void exibirRespostasParaValidacao(List<RespostaConsolidada> lista){
        for (RespostaConsolidada r : lista){
            System.out.printf("\nRespostas de %s (%s):%n", r.getNomeJogador(), r.getIdJogador().substring(0, 4));
            r.getRespostasMap().forEach((categoria, palavra) ->
                    System.out.printf("   [%s]: %s%n", categoria, palavra)
            );
        }
    }

    public static void main(String[] args) {
        StopClientApp app = new StopClientApp(HOST_PADRAO, PORTA_PADRAO);
        app.iniciar();
    }
}
