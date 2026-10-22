package com.distribuidos.stop.server.domain;

import com.distribuidos.stop.server.concurrency.StopLockManeger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Papel na Arquitetura:
 * Atua como a autoridade absoluta do estado do jogo (Single Source of Truth).
 * Gerencia o ciclo de vida da partida, transições de estado, quórum de jogadores
 * e a criação das rodadas.
 *
 * Desacoplamento via Observer Pattern (Listener):
 * Para manter a camada de domínio 100% pura (sem conhecer classes do gRPC ou Netty),
 * a Room se comunica com a camada de rede através da interface funcional {@link RoomListener}.
 * Sempre que o estado da partida muda, os listeners registrados são notificados
 * para disparar os pacotes de rede aos clientes.
 */
public class Room {

    /**
     * Interface de retorno para notificar a camada de rede (gRPC) sobre eventos do jogo.
     */
    public interface RoomListener{
        void onJogadorEntou(Player jogador);
        void onInicioRodada(Round rodada);
        void onStopAcionado(String nomeJogadorVencedor);
        void onInicioValidacao(Round rodada);
        void onFimRodada(Round rodada, Map<String, Integer> pontuacaoRodada, Map<String, Integer> placarGeral);
        void onFimJogo(Map<String, Integer> placarFinal);
    }

    private static final int MINIMO_JOGADORES = 2;
    private static final int TOTAL_RODADAS = 3;
    private static final List<String> CATEGORIAS_PADRAO = List.of("NOME", "CEP", "ANIMAL", "OBJETO", "COR");

    // Letras disponíveis para sorteio sem repetição
    private final List<String> letrasDisponiveis = Collections.synchronizedList(new ArrayList<>(
            List.of("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "L", "M", "N", "O", "P", "R", "S", "T", "U", "V")
    ));

    private final Map<String, Player> jogadores = new ConcurrentHashMap<>();
    private final List<RoomListener> listeners = new CopyOnWriteArrayList<>();
    private final StopLockManeger lockManeger = new StopLockManeger();

    private final AtomicReference<GameState> estadoAtual = new AtomicReference<>(GameState.LOBBY);
    private volatile Round rodadaAtual;
    private int contadorRodadas = 0;

    public void adicionarListener(RoomListener listener){
        this.listeners.add(listener);
    }

    public GameState getEstadoAtual(){
        return this.estadoAtual.get();
    }

    public Round getRodadaAtual(){
        return this.rodadaAtual;
    }

    public Map<String, Player> getJogadores(){
        return Collections.unmodifiableMap(jogadores);
    }

    /**
     * Registra um novo cliente no lobby.
     */
    public synchronized boolean adicionarJogador(Player jogador){
        if (estadoAtual.get() != GameState.LOBBY){
            return false;
        }
        jogadores.put(jogador.getId(), jogador);
        listeners.forEach(lis -> lis.onJogadorEntou(jogador));
        return true;
    }

    /**
     * Remove o cliente em caso de desconexão.
     */
    public synchronized void desconectarJogador(String idJogador){
        Player p = jogadores.get(idJogador);
        if (p != null){
            p.setConectado(false);
            if (estadoAtual.get() == GameState.LOBBY){
                jogadores.remove(idJogador);
            }
        }
    }

    /**
     * Jogador sinaliza que está pronto. Se todos estiverem, inicia a partida.
     */
    public synchronized void marcarJogadorPronto(String idJogador){
        Player p = jogadores.get(idJogador);
        if (p == null || estadoAtual.get() != GameState.LOBBY){
            return;
        }

        p.setPronto(true);

        long jogadoresProntos = jogadores.values().stream().filter(Player::isPronto).count();
        if (jogadores.size() >= MINIMO_JOGADORES && jogadoresProntos == jogadores.size()){
            iniciarNovaRodada();
        }
    }

    /**
     * Transição de Estado: Inicia uma nova rodada sorteando a letra e limpando as travas.
     */
    public synchronized void iniciarNovaRodada(){
        if (contadorRodadas >= TOTAL_RODADAS || letrasDisponiveis.isEmpty()){
            finalizarPartida();
            return;
        }

        contadorRodadas++;
        Collections.shuffle(letrasDisponiveis);
        String letraSorteada = letrasDisponiveis.remove(0);

        this.rodadaAtual = new Round(contadorRodadas, letraSorteada, CATEGORIAS_PADRAO);
        this.lockManeger.reiniciar();
        this.estadoAtual.set(GameState.RODADA_ATIVA);

        listeners.forEach(lis -> lis.onInicioRodada(this.rodadaAtual));
    }

    /**
     * Ponto Crítico de Concorrência: Processa a disputa atômica do STOP.
     */
    public boolean processarStop(String idJogador){
        if (estadoAtual.get() != GameState.RODADA_ATIVA){
            return false;
        }

        boolean venceuCorrida = lockManeger.tentarAcionarStop(idJogador);
        if (venceuCorrida){
            Player p = jogadores.get(idJogador);
            String nome = (p != null) ? p.getNome() : "Desconhecido";
            listeners.forEach(lis -> lis.onStopAcionado(nome));
        }

        return venceuCorrida;
    }

    /**
     * Coleta as palavras preenchidas pelo jogador durante a rodada ativa.
     */
    public boolean submeterPalavras(String idJogador, Map<String, String> respostas){
        if (rodadaAtual == null){
            return false;
        }

        rodadaAtual.registrarRespostas(idJogador, respostas);
        return true;
    }

    /**
     * Transição de Estado: Avança da digitação para a validação das palavras.
     */
    public synchronized void avancarParaValidacao(){
        if (estadoAtual.compareAndSet(GameState.RODADA_ATIVA, GameState.VALIDACAO)){
            listeners.forEach(lis -> lis.onInicioValidacao(rodadaAtual));
        }
    }

    /**
     * Registra o voto de um cliente avaliando a resposta de outro.
     */
    public boolean registrarVoto(String idJogadorAlvo, String categoria, boolean aprovado){
        if (estadoAtual.get() != GameState.VALIDACAO || rodadaAtual == null){
            return false;
        }
        rodadaAtual.registrarVoto(idJogadorAlvo, categoria, aprovado);
        return true;
    }

    /**
     * Transição de Estado: Encerra a validação, computa a pontuação determinística
     * e atualiza o placar geral da partida.
     */
    public synchronized void finalizarValidacaoECalcularPontos(){
        if (!estadoAtual.compareAndSet(GameState.VALIDACAO, GameState.FIM_RODADA)){
            return;
        }

        Set<String> jogadoresAtivos = jogadores.keySet();

        // Executa o cálculo determinístico através do ScoreCalculator
        Map<String, Map<String, Integer>> pontosDetalhe = ScoreCalculator.calcularPontosRodada(rodadaAtual, jogadoresAtivos);
        Map<String, Integer> totalRodada = ScoreCalculator.somarTotalRodada(pontosDetalhe);

        // Aplica os pontos na pontuação acumulada atômica de cada jogador
        Map<String, Integer> placarGeral = new HashMap<>();
        for (Map.Entry<String, Integer> entry : totalRodada.entrySet()){
            Player p = jogadores.get(entry.getKey());
            if (p != null){
                p.adicionarPontos(entry.getValue());
                placarGeral.put(entry.getKey(), p.getPontuacaoTotal());
            }
        }

        listeners.forEach(lis -> lis.onFimRodada(rodadaAtual, totalRodada, placarGeral));
    }

    private void finalizarPartida(){
        this.estadoAtual.set(GameState.FIM_JOGO);
        Map<String, Integer> placarFinal = new HashMap<>();
        jogadores.forEach((id, p) -> placarFinal.put(id, p.getPontuacaoTotal()));
        listeners.forEach(lis -> lis.onFimJogo(placarFinal));
    }


}
