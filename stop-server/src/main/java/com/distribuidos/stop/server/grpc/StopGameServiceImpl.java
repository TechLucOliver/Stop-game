package com.distribuidos.stop.server.grpc;

import com.distribuidos.stop.*;
import com.distribuidos.stop.StopGameServiceGrpc;
import com.distribuidos.stop.server.domain.Player;
import com.distribuidos.stop.server.domain.Room;
import com.distribuidos.stop.server.domain.Round;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import com.distribuidos.stop.server.domain.GameConfig;
import com.distribuidos.stop.server.domain.RoomManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ponte entre o transporte de rede gRPC (HTTP/2) e a lógica de
 * negócios centralizada na classe {@link Room}.
 *
 * Padrão Observer (Room.RoomListener):
 * A camada de domínio (Room) não tem conhecimento sobre gRPC ou Protobuf.
 * Esta classe implementa os métodos de callback para capturar os eventos
 * disparados pelo coordenador da sala e transmiti-los em broadcast para
 * os observadores de streaming dos clientes.
 */
public class StopGameServiceImpl extends StopGameServiceGrpc.StopGameServiceImplBase implements Room.RoomListener {

    private final Room room;
    private final RoomManager roomManager;

    /**
     * Registro concorrente dos canais de Server Streaming ativos:
     * Chave: idJogador | Valor: StreamObserver para envio de eventos push
     */
    private final Map<String, StreamObserver<EventoJogo>> canaisStreaming = new ConcurrentHashMap<>();

    public StopGameServiceImpl(Room room, RoomManager roomManager) {
        this.room = room;
        this.roomManager = roomManager;
        this.room.adicionarListener(this);
    }

    // MÉTODOS RPC RECEBIDOS DOS CLIENTES (REQUEST / RESPONSE)

    /**
     * Handshake inicial do nó cliente.
     */
    @Override
    public void conectar(RequisicaoEntrada request, StreamObserver<RespostaEntrada> responseObserver) {
        String idJogador = UUID.randomUUID().toString();
        String nome = request.getNome().trim().isEmpty() ? "Jogador_" + idJogador.substring(0, 4) : request.getNome();

        Player novoJogador = new Player(idJogador, nome);
        boolean aceito = room.adicionarJogador(novoJogador);

        RespostaEntrada resposta;
        if (aceito){
            System.out.printf("[LOBBY] Jogador conectado: %s (ID: %s)%n", nome, idJogador);
            resposta = RespostaEntrada.newBuilder()
                    .setIdJogador(idJogador)
                    .setSucesso(true)
                    .setMensagem("Conectado com sucesso ao lobby central.")
                    .build();
        }else{
            resposta = RespostaEntrada.newBuilder()
                    .setSucesso(false)
                    .setMensagem("Não foi possível entrar: partida já em andamento.")
                    .build();
        }

        responseObserver.onNext(resposta);
        responseObserver.onCompleted();
    }

    @Override
    public void criarSala(
            CriarSalaRequest request,
            StreamObserver<CriarSalaResponse> responseObserver
    ) {
        try {
            GameConfig config = new GameConfig(
                    request.getCategoriasList(),
                    request.getTotalRodadas(),
                    request.getLetrasHabilitadasList()
            );

            RoomManager.Sala sala = roomManager.criarSala(
                    request.getNomeSala(),
                    config
            );

            responseObserver.onNext(CriarSalaResponse.newBuilder()
                    .setSucesso(true)
                    .setIdSala(sala.getId())
                    .setMensagem("Sala criada com sucesso.")
                    .build());
        } catch (IllegalArgumentException e) {
            responseObserver.onNext(CriarSalaResponse.newBuilder()
                    .setSucesso(false)
                    .setMensagem(e.getMessage())
                    .build());
        }

        responseObserver.onCompleted();
    }

    @Override
    public void listarSalas(
            ListarSalasRequest request,
            StreamObserver<ListarSalasResponse> responseObserver
    ) {
        ListarSalasResponse.Builder resposta = ListarSalasResponse.newBuilder();

        for (RoomManager.Sala sala : roomManager.listarSalas()) {
            resposta.addSalas(SalaDisponivel.newBuilder()
                    .setIdSala(sala.getId())
                    .setNomeSala(sala.getNome())
                    .setJogadoresConectados(sala.getJogadoresConectados())
                    .setEstado(sala.getEstado())
                    .build());
        }

        responseObserver.onNext(resposta.build());
        responseObserver.onCompleted();
    }

    /**
     * Registro do canal contínuo de Server Streaming.
     */
    @Override
    public void ouvirEventos(IdentificadorJogador request, StreamObserver<EventoJogo> responseObserver) {
        String idJogador = request.getId();
        canaisStreaming.put(idJogador, responseObserver);

        ServerCallStreamObserver<EventoJogo> serverObserver = (ServerCallStreamObserver<EventoJogo>) responseObserver;
        serverObserver.setOnCancelHandler(() -> {
            canaisStreaming.remove(idJogador);
            room.desconectarJogador(idJogador);
            System.out.printf("[DESCONEXÃO] Canal streaming encerrado para o nó: %s%n", idJogador);
        });
    }

    /**
     * Cliente sinaliza que está pronto para jogar.
     */
    @Override
    public void marcarPronto(IdentificadorJogador request, StreamObserver<Confirmacao> responseObserver) {
        room.marcarJogadorPronto(request.getId());

        Confirmacao confirmacao = Confirmacao.newBuilder()
                .setSucesso(true)
                .setMensagem("Status de prontidão confirmado")
                .build();

        responseObserver.onNext(confirmacao);
        responseObserver.onCompleted();
    }

    /**
     * Disputa atômica do STOP.
     */
    @Override
    public void declararStop(IdentificadorJogador request, StreamObserver<RespostaStop> responseObserver) {
        boolean venceu = room.processarStop(request.getId());

        RespostaStop resposta = RespostaStop.newBuilder()
                .setFoiOPrimeiro(venceu)
                .setMensagem(venceu ? "Você acionou o STOP com sucesso!" : "Outro jogador acionou o STOP primeiro!")
                .build();

        responseObserver.onNext(resposta);
        responseObserver.onCompleted();
    }

    /**
     * Envio das respostas da rodada pelo cliente.
     */
    @Override
    public void submeterPalavras(SubmissaoRespostas request, StreamObserver<Confirmacao> responseObserver) {
        boolean aceito = room.submeterPalavras(request.getIdJogador(), request.getRespostasPorCategoriaMap());

        System.out.printf(
                "[DEBUG] Submissão do jogador %s: aceita=%s, categorias=%s%n",
                request.getIdJogador(),
                aceito,
                request.getRespostasPorCategoriaMap().keySet()
        );

        Confirmacao confirmacao = Confirmacao.newBuilder()
                .setSucesso(aceito)
                .setMensagem(aceito ? "Respostas registradas com sucesso." : "Submissão rejeitada: rodada inativa.")
                .build();

        responseObserver.onNext(confirmacao);
        responseObserver.onCompleted();
    }

    /**
     * Envio dos votos comunitários de validação.
     */
    @Override
    public void submeterVotos(SubmissaoVotos request, StreamObserver<Confirmacao> responseObserver) {
        String idVotante = request.getIdJogadorVotante();
        boolean votosAceitos = true;

        for (VotoItem voto : request.getVotosList()){
            votosAceitos &= room.registrarVoto(
                    idVotante,
                    voto.getIdJogadorAlvo(),
                    voto.getCategoria(),
                    voto.getAprovado()
            );
        }

        boolean confirmou = votosAceitos && room.confirmarVotos(idVotante);

        Confirmacao confirmacao = Confirmacao.newBuilder()
                .setSucesso(confirmou)
                .setMensagem(confirmou
                        ? "Votos registrados e confirmação recebida."
                        : "Não foi possível registrar a confirmação dos votos.")
                .build();

        responseObserver.onNext(confirmacao);
        responseObserver.onCompleted();
    }

    // BROADCASTS DISPARADOS PELA CLASSE ROOM (OBSERVER PATTERN)

    @Override
    public void onJogadorEntou(Player jogador) {
        transmitir(EventoJogo.newBuilder()
                .setTipo(TipoEvento.JOGADOR_ENTROU)
                .setMensagem(jogador.getNome() + " entrou na sala.")
                .build());
    }

    @Override
    public void onInicioRodada(Round rodada, int duracaoSegundos) {
        transmitir(EventoJogo.newBuilder()
                .setTipo(TipoEvento.INICIO_RODADA)
                .setLetraSorteada(rodada.getLetra())
                .addAllCategorias(rodada.getCategorias())
                .setDuracaoSegundos(duracaoSegundos)
                .setMensagem("Rodada iniciada com a letra: "+ rodada.getLetra())
                .build());
    }

    @Override
    public void onStopAcionado(String nomeJogadorVencedor) {
        transmitir(EventoJogo.newBuilder()
                .setTipo(TipoEvento.STOP_ACIONADO)
                .setJogadorQueParou(nomeJogadorVencedor)
                .setMensagem("O jogador " + nomeJogadorVencedor + " deu STOP!")
                .build());
    }

    @Override
    public void onTempoRodadaEsgotado() {
        transmitir(EventoJogo.newBuilder()
                .setTipo(TipoEvento.TEMPO_RODADA_ESGOTADO)
                .setMensagem("O tempo para preencher as respostas acabou.")
                .build());
    }

    @Override
    public void onInicioValidacao(Round rodada, int duracaoSegundos) {
        // Constrói a lista com as respostas submetidas por todos os jogadores
        List<RespostaConsolidada> respostasConsolidadas = new ArrayList<>();

        for (Map.Entry<String, Map<String, String>> entry : rodada.getRespostasPorJogador().entrySet()){
            String id = entry.getKey();
            Player player = room.getJogadores().get(id);
            String nome = (player != null) ? player.getNome() : id;

            System.out.printf(
                    "[DEBUG] Início da validação: jogadores com respostas=%s%n",
                    rodada.getRespostasPorJogador().keySet()
            );

            respostasConsolidadas.add(RespostaConsolidada.newBuilder()
                            .setIdJogador(id)
                            .setNomeJogador(nome)
                            .putAllRespostas(entry.getValue())
                            .build());
        }

        transmitir(EventoJogo.newBuilder()
                .setTipo(TipoEvento.INICIO_VALIDACAO)
                .setDuracaoSegundos(duracaoSegundos)
                .addAllTodasRespostas(respostasConsolidadas)
                .setMensagem("Inicio da fase de validação das respostas.")
                .build());
    }

    @Override
    public void onFimRodada(Round rodada, Map<String, Integer> pontuacaoRodada, Map<String, Integer> placarGeral) {
        transmitir(EventoJogo.newBuilder()
                .setTipo(TipoEvento.FIM_RODADA)
                .putAllPontuacaoGeral(placarGeral)
                .addAllPlacarJogadores(criarPlacarJogadores(placarGeral))
                .setMensagem("Fim da rodada " + rodada.getNumeroRodadas() + "! Placar atualizado.")
                .build());
    }

    @Override
    public void onFimJogo(Map<String, Integer> placarFinal) {
        transmitir(EventoJogo.newBuilder()
                .setTipo(TipoEvento.FIM_JOGO)
                .putAllPontuacaoGeral(placarFinal)
                .addAllPlacarJogadores(criarPlacarJogadores(placarFinal))
                .setMensagem("Fim de jogo! Confira o placar final.")
                .build());
    }

    /**
     * Itera de forma segura sobre os canais HTTP/2 e despacha a mensagem de rede.
     */
    private void transmitir(EventoJogo evento){
        for (Map.Entry<String, StreamObserver<EventoJogo>> entry : canaisStreaming.entrySet()){
            try {
                entry.getValue().onNext(evento);
            }catch (Exception e){
                canaisStreaming.remove(entry.getKey());
            }
        }
    }

    private List<JogadorPontuacao> criarPlacarJogadores(Map<String, Integer> pontuacoes) {
        List<JogadorPontuacao> placar = new ArrayList<>();

        pontuacoes.forEach((id, pontos) -> {
            Player jogador = room.getJogadores().get(id);

            placar.add(JogadorPontuacao.newBuilder()
                    .setIdJogador(id)
                    .setNomeJogador(jogador != null ? jogador.getNome() : id)
                    .setPontuacao(pontos)
                    .build());
        });

        return placar;
    }
}
