package com.distribuidos.stop.server.domain;

import com.distribuidos.stop.server.concurrency.StopLockManeger;

import java.util.*;
import java.util.concurrent.*;
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
        void onInicioRodada(Round rodada, int duracaoSegundos);
        void onStopAcionado(String nomeJogadorVencedor);
        void onTempoRodadaEsgotado();
        void onInicioValidacao(Round rodada, int duracaoSegundos);
        void onFimRodada(Round rodada, Map<String, Integer> pontuacaoRodada, Map<String, Integer> placarGeral);
        void onFimJogo(Map<String, Integer> placarFinal);
    }

    private static final int MINIMO_JOGADORES = 2;

    private static final int TEMPO_RODADA_SEGUNDOS = 40;
    private static final int TEMPO_VOTACAO_SEGUNDOS = 60;

    private volatile int tempoRodadaAtualSegundos = TEMPO_RODADA_SEGUNDOS;
    private volatile int tempoVotacaoAtualSegundos = TEMPO_VOTACAO_SEGUNDOS;
    private ScheduledFuture<?> tarefaTimeoutRodada;

    private volatile int totalRodadas;
    private volatile List<String> categorias;
    private final List<String> letrasDisponiveis;

    private final Map<String, Player> jogadores = new ConcurrentHashMap<>();
    private final List<RoomListener> listeners = new CopyOnWriteArrayList<>();
    private final StopLockManeger lockManeger = new StopLockManeger();

    // jogadores já submeteram seus votos na rodada atual
    private final Set<String> jogadoresQueVotaram = ConcurrentHashMap.newKeySet();

    private final AtomicReference<GameState> estadoAtual = new AtomicReference<>(GameState.LOBBY);
    private volatile Round rodadaAtual;
    private volatile List<String> letrasHabilitadas;
    private int contadorRodadas = 0;

    /**
     * Pool de threads dedicado para agendamento temporal de eventos no servidor.
     * Garante execução pontual de barreiras sem bloquear threads de rede do Netty.
     */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> tarefaTimeoutValidacao;

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

    public Room() {
        this(new GameConfig(
                List.of("NOME", "CEP", "ANIMAL", "OBJETO", "COR"),
                3,
                List.of(
                        "A", "B", "C", "D", "E", "F", "G", "H", "I",
                        "J", "K", "L", "M", "N", "O", "P", "Q", "R",
                        "S", "T", "U", "V", "W", "X", "Y", "Z"
                )
        ));
    }

    public Room(GameConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("A configuração da sala é obrigatória.");
        }

        this.totalRodadas = config.getTotalRodadas();
        this.categorias = config.getCategorias();
        this.letrasHabilitadas = config.getLetrasDisponiveis();
        this.letrasDisponiveis = Collections.synchronizedList(
                new ArrayList<>(this.letrasHabilitadas)
        );
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
        if (contadorRodadas >= totalRodadas || letrasDisponiveis.isEmpty()){
            finalizarPartida();
            return;
        }

        contadorRodadas++;
        Collections.shuffle(letrasDisponiveis);
        String letraSorteada = letrasDisponiveis.remove(0);

        this.rodadaAtual = new Round(contadorRodadas, letraSorteada, categorias);
        this.jogadoresQueVotaram.clear();
        this.lockManeger.reiniciar();
        this.estadoAtual.set(GameState.RODADA_ATIVA);

        System.out.printf("[RODADA %d] Iniciada com a letra '%s'.%n", contadorRodadas, letraSorteada);
        listeners.forEach(lis -> lis.onInicioRodada(this.rodadaAtual, tempoRodadaAtualSegundos));
        tarefaTimeoutRodada = scheduler.schedule(
                this::encerrarRodadaPorTempo,
                tempoRodadaAtualSegundos,
                TimeUnit.SECONDS
        );
    }

    /**
     * Ponto Crítico de Concorrência: Processa a disputa atômica do STOP.
     */
    public synchronized boolean processarStop(String idJogador){
        if (estadoAtual.get() != GameState.RODADA_ATIVA
                || !jogadores.containsKey(idJogador)
                || !respostasCompletas(idJogador)){
            return false;
        }

        boolean venceuCorrida = lockManeger.tentarAcionarStop(idJogador);
        if (venceuCorrida){
            Player p = jogadores.get(idJogador);
            tarefaTimeoutRodada.cancel(false);
            String nome = (p != null) ? p.getNome() : "Desconhecido";
            System.out.printf("[STOP] Acionado por %s. Janela de carência de 5s iniciada...%n", nome);
            listeners.forEach(lis -> lis.onStopAcionado(nome));

            // Dispara janela de tolerância: 5 segundos para que todos submetam suas palavras
            scheduler.schedule(this::avancarParaValidacao, 5, TimeUnit.SECONDS);
        }

        return venceuCorrida;
    }

    /**
     * Coleta as palavras preenchidas pelo jogador durante a rodada ativa.
     */
    public synchronized  boolean submeterPalavras(String idJogador, Map<String, String> respostas){
        if (estadoAtual.get() != GameState.RODADA_ATIVA
                || !jogadores.containsKey(idJogador)){
            return false;
        }

        rodadaAtual.registrarRespostas(idJogador, respostas);
        return true;
    }

    /**
     * Transição de Estado: Avança da digitação para a validação das palavras.
     */
    public synchronized void avancarParaValidacao(){
        if (estadoAtual.compareAndSet(GameState.RODADA_ATIVA, GameState.VALIDACAO)) {
            System.out.printf(
                    "[ESTADO] Transição para VALIDACAO. Tempo para votar: %ds.%n",
                    tempoVotacaoAtualSegundos
            );

            listeners.forEach(listener ->
                    listener.onInicioValidacao(rodadaAtual, tempoVotacaoAtualSegundos)
            );

            tarefaTimeoutValidacao = scheduler.schedule(
                    this::finalizarValidacaoECalcularPontos,
                    tempoVotacaoAtualSegundos,
                    TimeUnit.SECONDS
            );
        }
    }

    /**
     * Registra o voto de um cliente avaliando a resposta de outro.
     */
    public synchronized boolean registrarVoto(
            String idJogadorVotante,
            String idJogadorAlvo,
            String categoria,
            boolean aprovado
    ) {
        if (estadoAtual.get() != GameState.VALIDACAO
                || rodadaAtual == null
                || !jogadores.containsKey(idJogadorVotante)) {
            return false;
        }

        rodadaAtual.registrarVoto(idJogadorAlvo, categoria, aprovado);
        return true;
    }

    public synchronized boolean confirmarVotos(String idJogador) {
        if (estadoAtual.get() != GameState.VALIDACAO
                || !jogadores.containsKey(idJogador)) {
            return false;
        }

        jogadoresQueVotaram.add(idJogador);

        int totalJogadoresNaSala = jogadores.size();

        if (jogadoresQueVotaram.size() >= totalJogadoresNaSala) {
            System.out.println("[BARREIRA] Todos os jogadores conectados confirmaram os votos.");

            if (tarefaTimeoutValidacao != null) {
                tarefaTimeoutValidacao.cancel(false);
            }

            finalizarValidacaoECalcularPontos();
        }

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

        System.out.println("[APURAÇÃO] Calculando pontuações da rodada...");
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

        scheduler.schedule(() -> {
            if (contadorRodadas < totalRodadas){
                iniciarNovaRodada();
            }else {
                finalizarPartida();
            }
        }, 7, TimeUnit.SECONDS);
    }

    private void finalizarPartida(){
        this.estadoAtual.set(GameState.FIM_JOGO);
        System.out.println("[ESTADO] Partida encerrada! Placar consolidado transmitido.");
        Map<String, Integer> placarFinal = new HashMap<>();
        jogadores.forEach((id, p) -> placarFinal.put(id, p.getPontuacaoTotal()));
        listeners.forEach(lis -> lis.onFimJogo(placarFinal));
    }

    /**
     * Encerra o agendador de threads no shutdown do servidor.
     */
    public void desligarAgendador(){
        this.scheduler.shutdownNow();
    }

    private void encerrarRodadaPorTempo() {
        synchronized (this) {
            if (estadoAtual.get() != GameState.RODADA_ATIVA || lockManeger.getStopAcionado()) {
                return;
            }

            // Marca a rodada como encerrada para que um STOP posterior não vença.
            lockManeger.tentarAcionarStop("__TEMPO_ESGOTADO__");
        }

        listeners.forEach(RoomListener::onTempoRodadaEsgotado);

        // Mantém uma janela curta para submissões que já estavam a caminho.
        scheduler.schedule(this::avancarParaValidacao, 5, TimeUnit.SECONDS);
    }

    private boolean respostasCompletas(String idJogador) {
        if (rodadaAtual == null) {
            return false;
        }

        Map<String, String> respostas =
                rodadaAtual.getRespostasPorJogador().get(idJogador);

        if (respostas == null) {
            return false;
        }

        return rodadaAtual.getCategorias().stream().allMatch(categoria -> {
            String chave = categoria.trim().toUpperCase();
            String resposta = respostas.get(chave);

            return resposta != null && !resposta.isBlank();
        });
    }

    private boolean respostasContemTodasAsCategorias(Map<String, String> respostas) {
        if (rodadaAtual == null || respostas == null) {
            return false;
        }

        return rodadaAtual.getCategorias().stream().allMatch(categoria -> {
            String chave = categoria.trim().toUpperCase();
            String resposta = respostas.get(chave);

            return resposta != null && !resposta.isBlank();
        });
    }

    public synchronized boolean atualizarConfiguracao(GameConfig config) {
        if (config == null
                || estadoAtual.get() != GameState.LOBBY
                || contadorRodadas > 0) {
            return false;
        }

        this.totalRodadas = config.getTotalRodadas();
        this.categorias = config.getCategorias();
        this.letrasHabilitadas = config.getLetrasDisponiveis();

        synchronized (letrasDisponiveis) {
            letrasDisponiveis.clear();
            letrasDisponiveis.addAll(this.letrasHabilitadas);
        }

        return true;
    }

    public int getTotalRodadas() {
        return totalRodadas;
    }

    public List<String> getCategorias() {
        return categorias;
    }

    public List<String> getLetrasHabilitadas() {
        return letrasHabilitadas;
    }
}
