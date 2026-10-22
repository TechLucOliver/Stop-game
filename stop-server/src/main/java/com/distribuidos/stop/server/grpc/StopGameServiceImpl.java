package com.distribuidos.stop.server.grpc;

import com.distribuidos.stop.*;
import com.distribuidos.stop.StopGameServiceGrpc;
import com.distribuidos.stop.server.domain.Player;
import com.distribuidos.stop.server.domain.Room;
import com.distribuidos.stop.server.domain.Round;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;

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

    /**
     * Registro concorrente dos canais de Server Streaming ativos:
     * Chave: idJogador | Valor: StreamObserver para envio de eventos push
     */
    private final Map<String, StreamObserver<EventoJogo>> canaisStreaming = new ConcurrentHashMap<>();

    public StopGameServiceImpl(Room room){
        this.room = room;
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
        for (VotoItem voto : request.getVotosList()){
            room.registrarVoto(voto.getIdJogadorAlvo(), voto.getCategoria(), voto.getAprovado());
        }

        Confirmacao confirmacao = Confirmacao.newBuilder()
                .setSucesso(true)
                .setMensagem("Votos computados com sucesso.")
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
    public void onInicioRodada(Round rodada) {
        transmitir(EventoJogo.newBuilder()
                .setTipo(TipoEvento.INICIO_RODADA)
                .setLetraSorteada(rodada.getLetra())
                .addAllCategorias(rodada.getCategorias())
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
    public void onInicioValidacao(Round rodada) {
        // Constrói a lista com as respostas submetidas por todos os jogadores
        List<RespostaConsolidada> respostasConsolidadas = new ArrayList<>();

        for (Map.Entry<String, Map<String, String>> entry : rodada.getRespostasPorJogador().entrySet()){
            String id = entry.getKey();
            Player player = room.getJogadores().get(id);
            String nome = (player != null) ? player.getNome() : id;

            respostasConsolidadas.add(RespostaConsolidada.newBuilder()
                            .setIdJogador(id)
                            .setNomeJogador(nome)
                            .putAllRespostas(entry.getValue())
                            .build());
        }

        transmitir(EventoJogo.newBuilder()
                .setTipo(TipoEvento.INICIO_VALIDACAO)
                .addAllTodasRespostas(respostasConsolidadas)
                .setMensagem("Inicio da fase de validação das respostas.")
                .build());
    }

    @Override
    public void onFimRodada(Round rodada, Map<String, Integer> pontuacaoRodada, Map<String, Integer> placarGeral) {
        transmitir(EventoJogo.newBuilder()
                .setTipo(TipoEvento.FIM_RODADA)
                .putAllPontuacaoGeral(placarGeral)
                .setMensagem("Fim da rodada " + rodada.getNumeroRodadas() + "! Placar atualizado.")
                .build());
    }

    @Override
    public void onFimJogo(Map<String, Integer> placarFinal) {
        transmitir(EventoJogo.newBuilder()
                .setTipo(TipoEvento.FIM_JOGO)
                .putAllPontuacaoGeral(placarFinal)
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
}
