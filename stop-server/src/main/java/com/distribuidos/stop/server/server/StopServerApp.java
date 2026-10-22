package com.distribuidos.stop.server.server;

import com.distribuidos.stop.server.domain.Room;
import com.distribuidos.stop.server.grpc.StopGameServiceImpl;
import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 *  Representa o PROCESSO SERVIDOR que vai abrir a porta TCP na placa de rede e ficar escutando os clientes.
 */
public class StopServerApp {

    // Porta TCP onde o servidor ficará escutando chamadas gRPC
    private static final int PORTA_TCP = 50051;
    private Server server;

    /**
     * Inicializa a infraestrutura de rede gRPC (baseada em Netty).
     */
    public void iniciar() throws IOException{
        // Cria a autoridade central da partida (instância da Room)
        Room room = new Room();

        // Constrói o servidor gRPC vinculando a porta TCP ao serviço remoto implementado
        server = ServerBuilder.forPort(PORTA_TCP)
                .addService(new StopGameServiceImpl(room))
                .build()
                .start();

        System.out.println("==================================================");
        System.out.printf(">>> Servidor gRPC Central ONLINE na porta TCP: %d%n", PORTA_TCP);
        System.out.println(">>> Aguardando conexão dos nós clientes...");
        System.out.println("==================================================");

        // Gancho de finalização do SO (Shutdown Hook):
        // Se alguém apertar Ctrl+C no terminal ou encerrar o processo,
        // o servidor não "trava" as portas; ele desliga de forma limpa.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("\n[SISTEMA] Sinal de desligamento recebido. Encerrando conexões...");
            try {
                StopServerApp.this.parar();
            }catch (InterruptedException e){
                e.printStackTrace(System.err);
            }
            System.err.println("[SISTEMA] Servidor finalizado com sucesso.");
        }));
    }

    /**
     * Fecha as portas de rede e aguarda a conclusão das mensagens pendentes.
     */
    public void parar() throws InterruptedException{
        if (server != null){
            // Dá até 5 segundos para que mensagens no meio do caminho cheguem antes de forçar o corte
            server.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    /**
     * Impede que a JVM finalize a execução do método main.
     * Sem isso, o Java executa o iniciar() e encerra o programa 1 segundo depois.
     */
    public void aguardarTermino() throws InterruptedException{
        if (server != null){
            server.awaitTermination();
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException{
        StopServerApp app = new StopServerApp();
        app.iniciar();
        app.aguardarTermino();
    }
}
