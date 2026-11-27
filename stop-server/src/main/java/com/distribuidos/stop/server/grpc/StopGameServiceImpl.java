package com.distribuidos.stop.server.grpc;

import com.distribuidos.stop.*;
import com.distribuidos.stop.Confirmacao;
import com.distribuidos.stop.EventoJogo;
import com.distribuidos.stop.IdentificadorJogador;
import com.distribuidos.stop.RequisicaoEntrada;
import com.distribuidos.stop.RespostaConsolidada;
import com.distribuidos.stop.RespostaEntrada;
import com.distribuidos.stop.RespostaStop;
import com.distribuidos.stop.StopGameServiceGrpc;
import com.distribuidos.stop.SubmissaoRespostas;
import com.distribuidos.stop.SubmissaoVotos;
import com.distribuidos.stop.TipoEvento;
import com.distribuidos.stop.VotoItem;
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
public class StopGameServiceImpl extends StopGameServiceGrpc.StopGameServiceImplBase {

    private final Room room;
    private final RoomManager roomManager;
    private final Map<String, Room> salasPorJogador = new ConcurrentHashMap<>();

    /**
     * Registro concorrente dos canais de Server Streaming ativos:
     * Chave: idJogador | Valor: StreamObserver para envio de eventos push
     */
    private final Map<String, StreamObserver<EventoJogo>> canaisStreaming = new ConcurrentHashMap<>();

    public StopGameServiceImpl(Room room, RoomManager roomManager) {
        this.room = room;
        this.roomManager = roomManager;
        registrarListenerDaSala(this.room);
    }

    // MÉTODOS RPC RECEBIDOS DOS CLIENTES (REQUEST / RESPONSE)

    /**
     * Handshake inicial do nó cliente.
     */
    @Override
    public void conectar(
            RequisicaoEntrada request,
            StreamObserver<RespostaEntrada> responseObserver
    ) {
        String idSala = request.getIdSala();
        Room salaSelecionada;

        if (idSala == null || idSala.isBlank()) {
            // Compatibilidade temporária com clientes que ainda não escolhem sala.
            salaSelecionada = room;
        } else {
            RoomManager.Sala sala = roomManager.buscarSala(idSala);

            if (sala == null) {
                responseObserver.onNext(RespostaEntrada.newBuilder()
                        .setSucesso(false)
                        .setMensagem("A sala informada não existe.")
                        .build());
                responseObserver.onCompleted();
                return;
            }

            salaSelecionada = sala.getRoom();
        }

        String idJogador = UUID.randomUUID().toString();
        String nome = request.getNome().trim().isEmpty()
                ? "Jogador_" + idJogador.substring(0, 4)
                : request.getNome().trim();

        Player novoJogador = new Player(idJogador, nome);
        boolean aceito = salaSelecionada.adicionarJogador(novoJogador);

        if (aceito) {
            salasPorJogador.put(idJogador, salaSelecionada);
            System.out.printf(
                    "[LOBBY] Jogador conectado: %s (ID: %s, sala: %s)%n",
                    nome,
                    idJogador,
                    idSala == null || idSala.isBlank() ? "padrão" : idSala
            );

            responseObserver.onNext(RespostaEntrada.newBuilder()
                    .setIdJogador(idJogador)
                    .setSucesso(true)
                    .setMensagem("Conectado com sucesso à sala.")
                    .build());
        } else {
            responseObserver.onNext(RespostaEntrada.newBuilder()
                    .setSucesso(false)
                    .setMensagem("Não foi possível entrar: a partida já está em andamento.")
                    .build());
        }

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

            registrarListenerDaSala(sala.getRoom());

            responseObserver.onNext(CriarSalaResponse.newBuilder()
                    .setSucesso(true)
                    .setIdSala(sala.getId())
                    .setMensagem("Sala criada com sucesso.")
                    .setTokenCriador(sala.getTokenCriador())
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
            Room roomDaSala = sala.getRoom();

            resposta.addSalas(SalaDisponivel.newBuilder()
                    .setIdSala(sala.getId())
                    .setNomeSala(sala.getNome())
                    .setJogadoresConectados(sala.getJogadoresConectados())
                    .setEstado(sala.getEstado())
                    .addAllCategorias(roomDaSala.getCategorias())
                    .setTotalRodadas(roomDaSala.getTotalRodadas())
                    .addAllLetrasHabilitadas(roomDaSala.getLetrasHabilitadas())
                    .build());
        }

        responseObserver.onNext(resposta.build());
        responseObserver.onCompleted();
    }

    @Override
    public void editarSala(
            EditarSalaRequest request,
            StreamObserver<EditarSalaResponse> responseObserver
    ) {
        try {
            GameConfig config = new GameConfig(
                    request.getCategoriasList(),
                    request.getTotalRodadas(),
                    request.getLetrasHabilitadasList()
            );

            boolean editada = roomManager.editarSala(
                    request.getIdSala(),
                    request.getTokenCriador(),
                    request.getNomeSala(),
                    config
            );

            EditarSalaResponse resposta = EditarSalaResponse.newBuilder()
                    .setSucesso(editada)
                    .setMensagem(editada
                            ? "Sala atualizada."
                            : "Edição recusada: token inválido, sala inexistente ou partida já iniciada.")
                    .build();

            responseObserver.onNext(resposta);
        } catch (IllegalArgumentException e) {
            responseObserver.onNext(EditarSalaResponse.newBuilder()
                    .setSucesso(false)
                    .setMensagem(e.getMessage())
                    .build());
        }

        responseObserver.onCompleted();
    }

    /**
     * Registro do canal contínuo de Server Streaming.
     */
    @Override
    public void ouvirEventos(
            IdentificadorJogador request,
            StreamObserver<EventoJogo> responseObserver
    ) {
        String idJogador = request.getId();
        Room salaDoJogador = salasPorJogador.get(idJogador);

        if (salaDoJogador == null) {
            responseObserver.onError(
                    io.grpc.Status.NOT_FOUND
                            .withDescription("Jogador ou sala não encontrados.")
                            .asRuntimeException()
            );
            return;
        }

        canaisStreaming.put(idJogador, responseObserver);

        ServerCallStreamObserver<EventoJogo> serverObserver =
                (ServerCallStreamObserver<EventoJogo>) responseObserver;

        serverObserver.setOnCancelHandler(() -> {
            canaisStreaming.remove(idJogador, responseObserver);
            salasPorJogador.remove(idJogador, salaDoJogador);
            salaDoJogador.desconectarJogador(idJogador);

            System.out.printf(
                    "[DESCONEXÃO] Canal encerrado para jogador %s na sala.%n",
                    idJogador
            );
        });
    }

    /**
     * Cliente sinaliza que está pronto para jogar.
     */
    @Override
    public void marcarPronto(
            IdentificadorJogador request,
            StreamObserver<Confirmacao> responseObserver
    ) {
        Room sala = buscarSalaDoJogador(request.getId());

        Confirmacao.Builder resposta = Confirmacao.newBuilder();

        if (sala == null) {
            resposta.setSucesso(false)
                    .setMensagem("Jogador ou sala não encontrados.");
        } else {
            sala.marcarJogadorPronto(request.getId());
            resposta.setSucesso(true)
                    .setMensagem("Status de prontidão confirmado.");
        }

        responseObserver.onNext(resposta.build());
        responseObserver.onCompleted();
    }

    /**
     * Disputa atômica do STOP.
     */
    @Override
    public void declararStop(
            IdentificadorJogador request,
            StreamObserver<RespostaStop> responseObserver
    ) {
        Room sala = buscarSalaDoJogador(request.getId());

        boolean venceu = sala != null && sala.processarStop(request.getId());

        RespostaStop resposta = RespostaStop.newBuilder()
                .setFoiOPrimeiro(venceu)
                .setMensagem(venceu
                        ? "Você acionou o STOP com sucesso!"
                        : "STOP não aceito: sala não encontrada, rodada encerrada ou respostas incompletas.")
                .build();

        responseObserver.onNext(resposta);
        responseObserver.onCompleted();
    }

    /**
     * Envio das respostas da rodada pelo cliente.
     */
    @Override
    public void submeterPalavras(
            SubmissaoRespostas request,
            StreamObserver<Confirmacao> responseObserver
    ) {
        Room sala = buscarSalaDoJogador(request.getIdJogador());

        boolean aceita = sala != null
                && sala.submeterPalavras(
                request.getIdJogador(),
                request.getRespostasPorCategoriaMap()
        );

        Confirmacao resposta = Confirmacao.newBuilder()
                .setSucesso(aceita)
                .setMensagem(aceita
                        ? "Respostas registradas com sucesso."
                        : "Submissão rejeitada: sala ou rodada indisponível.")
                .build();

        responseObserver.onNext(resposta);
        responseObserver.onCompleted();
    }

    /**
     * Envio dos votos comunitários de validação.
     */
    @Override
    public void submeterVotos(
            SubmissaoVotos request,
            StreamObserver<Confirmacao> responseObserver
    ) {
        String idVotante = request.getIdJogadorVotante();
        Room sala = buscarSalaDoJogador(idVotante);
        boolean votosAceitos = sala != null;

        if (sala != null) {
            for (VotoItem voto : request.getVotosList()) {
                votosAceitos &= sala.registrarVoto(
                        idVotante,
                        voto.getIdJogadorAlvo(),
                        voto.getCategoria(),
                        voto.getAprovado()
                );
            }
        }

        boolean confirmou = votosAceitos && sala.confirmarVotos(idVotante);

        Confirmacao resposta = Confirmacao.newBuilder()
                .setSucesso(confirmou)
                .setMensagem(confirmou
                        ? "Votos registrados e confirmação recebida."
                        : "Não foi possível registrar os votos nesta sala.")
                .build();

        responseObserver.onNext(resposta);
        responseObserver.onCompleted();
    }

    /**
     * Itera de forma segura sobre os canais HTTP/2 e despacha a mensagem de rede.
     */
    private void transmitir(Room sala, EventoJogo evento) {
        for (Map.Entry<String, StreamObserver<EventoJogo>> entry
                : canaisStreaming.entrySet()) {
            String idJogador = entry.getKey();

            if (salasPorJogador.get(idJogador) != sala) {
                continue;
            }

            try {
                entry.getValue().onNext(evento);
            } catch (Exception e) {
                canaisStreaming.remove(idJogador, entry.getValue());
            }
        }
    }

    private List<JogadorPontuacao> criarPlacarJogadores(
            Room sala,
            Map<String, Integer> pontuacoes
    ) {
        List<JogadorPontuacao> placar = new ArrayList<>();

        pontuacoes.forEach((id, pontos) -> {
            Player jogador = sala.getJogadores().get(id);

            placar.add(JogadorPontuacao.newBuilder()
                    .setIdJogador(id)
                    .setNomeJogador(jogador != null ? jogador.getNome() : id)
                    .setPontuacao(pontos)
                    .build());
        });

        return placar;
    }

    private void registrarListenerDaSala(Room sala) {
        sala.adicionarListener(new Room.RoomListener() {
            @Override
            public void onJogadorEntou(Player jogador) {
                transmitir(sala, EventoJogo.newBuilder()
                        .setTipo(TipoEvento.JOGADOR_ENTROU)
                        .setMensagem(jogador.getNome() + " entrou na sala.")
                        .build());
            }

            @Override
            public void onInicioRodada(Round rodada, int duracaoSegundos) {
                transmitir(sala, EventoJogo.newBuilder()
                        .setTipo(TipoEvento.INICIO_RODADA)
                        .setLetraSorteada(rodada.getLetra())
                        .addAllCategorias(rodada.getCategorias())
                        .setDuracaoSegundos(duracaoSegundos)
                        .setMensagem("Rodada iniciada com a letra: " + rodada.getLetra())
                        .build());
            }

            @Override
            public void onStopAcionado(String nomeJogadorVencedor) {
                transmitir(sala, EventoJogo.newBuilder()
                        .setTipo(TipoEvento.STOP_ACIONADO)
                        .setJogadorQueParou(nomeJogadorVencedor)
                        .setMensagem("O jogador " + nomeJogadorVencedor + " deu STOP!")
                        .build());
            }

            @Override
            public void onTempoRodadaEsgotado() {
                transmitir(sala, EventoJogo.newBuilder()
                        .setTipo(TipoEvento.TEMPO_RODADA_ESGOTADO)
                        .setMensagem("O tempo para preencher as respostas acabou.")
                        .build());
            }

            @Override
            public void onInicioValidacao(Round rodada, int duracaoSegundos) {
                List<RespostaConsolidada> respostas = new ArrayList<>();

                for (Map.Entry<String, Map<String, String>> entry
                        : rodada.getRespostasPorJogador().entrySet()) {
                    String id = entry.getKey();
                    Player jogador = sala.getJogadores().get(id);

                    respostas.add(RespostaConsolidada.newBuilder()
                            .setIdJogador(id)
                            .setNomeJogador(jogador != null ? jogador.getNome() : id)
                            .putAllRespostas(entry.getValue())
                            .build());
                }

                transmitir(sala, EventoJogo.newBuilder()
                        .setTipo(TipoEvento.INICIO_VALIDACAO)
                        .setDuracaoSegundos(duracaoSegundos)
                        .addAllTodasRespostas(respostas)
                        .setMensagem("Início da fase de validação das respostas.")
                        .build());
            }

            @Override
            public void onFimRodada(
                    Round rodada,
                    Map<String, Integer> pontuacaoRodada,
                    Map<String, Integer> placarGeral
            ) {
                transmitir(sala, EventoJogo.newBuilder()
                        .setTipo(TipoEvento.FIM_RODADA)
                        .putAllPontuacaoGeral(placarGeral)
                        .addAllPlacarJogadores(criarPlacarJogadores(sala, placarGeral))
                        .setMensagem("Fim da rodada " + rodada.getNumeroRodadas()
                                + "! Placar atualizado.")
                        .build());
            }

            @Override
            public void onFimJogo(Map<String, Integer> placarFinal) {
                transmitir(sala, EventoJogo.newBuilder()
                        .setTipo(TipoEvento.FIM_JOGO)
                        .putAllPontuacaoGeral(placarFinal)
                        .addAllPlacarJogadores(criarPlacarJogadores(sala, placarFinal))
                        .setMensagem("Fim de jogo! Confira o placar final.")
                        .build());
            }
        });
    }

    private Room buscarSalaDoJogador(String idJogador) {
        return salasPorJogador.get(idJogador);
    }
}
