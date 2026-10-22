package com.distribuidos.stop.client.network;

import com.distribuidos.stop.*;
import com.distribuidos.stop.StopGameServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Isola toda a complexidade de rede (sockets, HTTP/2, stubs) da interface do usuário (UI).
 * A interface gráfica apenas chama os métodos desta classe, sem precisar
 * saber como o gRPC serializa ou envia os pacotes pela rede.
 */
public class GameGrpcClient {

    private final ManagedChannel channel;

    // Stub bloqueante: para requisições síncronas unárias (Request-Response)
    private final StopGameServiceGrpc.StopGameServiceBlockingStub blockingStub;

    // Stub assíncrono: obrigatório para Server Streaming (recebimento reativo de eventos)
    private final StopGameServiceGrpc.StopGameServiceStub asyncStub;

    private String idJogador;
    private String nomeJogador;

    /**
     * Inicializa o canal gRPC e instancia os stubs compilados.
     *
     * @param host Endereço IP do servidor (ex: "localhost" ou "192.168.1.X")
     * @param porta Porta TCP em que o servidor está escutando (ex: 50051)
     */
    public GameGrpcClient(String host, int porta){
        // Constrói o canal de comunicação HTTP/2
        this.channel = ManagedChannelBuilder.forAddress(host, porta)
                .usePlaintext()
                .build();

        // Cria os stubs gerados automaticamente pelo compilador Protobuf
        this.blockingStub = StopGameServiceGrpc.newBlockingStub(channel);
        this.asyncStub = StopGameServiceGrpc.newStub(channel);
    }

    /**
     * Executa o handshake inicial com o servidor remoto.
     * Chamada síncrona: a thread aguarda a resposta do servidor.
     */
    public boolean conectar(String nome){
        this.nomeJogador = nome;

        RequisicaoEntrada request = RequisicaoEntrada.newBuilder()
                .setNome(nome)
                .build();

        // Chamada remota via stub bloqueante
        RespostaEntrada resposta = blockingStub.conectar(request);

        if (resposta.getSucesso()){
            this.idJogador = resposta.getIdJogador();
            System.out.printf("[REDE] Conectado com sucesso! ID atribuído: %s%n", this.idJogador);
            return true;
        }else{
            System.err.printf("[REDE] Erro ao conectar: %s%n", resposta.getMensagem());
            return false;
        }
    }

    /**
     * Abre o canal de Server Streaming para escutar eventos em tempo real.
     *
     * @param manipuladorEvento Função callback que receberá os eventos vindos do servidor.
     */
    public void iniciarEscutaEventos(Consumer<EventoJogo> manipuladorEvento){
        if (this.idJogador == null){
            throw new IllegalStateException("O jogador precisa estar conectado antes de ouvir os eventos.");
        }

        IdentificadorJogador request = IdentificadorJogador.newBuilder()
                .setId(this.idJogador)
                .build();

        // Chamada remota assíncrona com StreamObserver
        asyncStub.ouvirEventos(request, new StreamObserver<EventoJogo>() {
            @Override
            public void onNext(EventoJogo evento) {
                // Executado sempre que o servidor envia uma mensagem push
                manipuladorEvento.accept(evento);
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("[STREAM] Erro ou queda na conexão com o servidor: " + t.getMessage());
            }

            @Override
            public void onCompleted() {
                System.out.println("[STREAM] Canal de eventos encerrado pelo servidor.");
            }
        });
    }

    /**
     * Notifica o servidor de que o jogador está pronto para iniciar.
     */
    public boolean marcarPronto(){
        Confirmacao resposta = blockingStub.marcarPronto(
                IdentificadorJogador.newBuilder().setId(this.idJogador).build()
        );
        return resposta.getSucesso();
    }

    /**
     * Disputa atômica do STOP.
     *
     * @return true se este nó foi o primeiro a registrar o STOP; false caso contrário.
     */
    public boolean declararStop(){
        RespostaStop resposta = blockingStub.declararStop(
                IdentificadorJogador.newBuilder().setId(this.idJogador).build()
        );
        return resposta.getFoiOPrimeiro();
    }

    /**
     * Submete as palavras preenchidas pelo jogador ao fim da rodada.
     */
    public boolean submeterPalavras(Map<String, String> respostasPorCategoria){
        SubmissaoRespostas request = SubmissaoRespostas.newBuilder()
                .setIdJogador(this.idJogador)
                .putAllRespostasPorCategoria(respostasPorCategoria)
                .build();

        Confirmacao resposta = blockingStub.submeterPalavras(request);
        return resposta.getSucesso();
    }

    /**
     * Encerra a conexão e libera os recursos de rede do canal Netty.
     */
    public void desconectar(){
        try {
            channel.shutdown().awaitTermination(3, TimeUnit.SECONDS);
        }catch (InterruptedException e){
            channel.shutdownNow();
        }
    }

    public String getIdJogador() {
        return idJogador;
    }

    public String getNomeJogador() {
        return nomeJogador;
    }
}
