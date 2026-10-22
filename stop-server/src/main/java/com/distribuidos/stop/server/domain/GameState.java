package com.distribuidos.stop.server.domain;

/**
 * Garante a integridade temporal do jogo centralizado. Cada chamada RPC
 * recebida pelo servidor valida primeiro se o estado atual permite aquela ação.
 *
 * Transições Válidas:
 * LOBBY -> RODADA_ATIVA -> VALIDACAO -> FIM_RODADA -> (nova rodada ou FIM_JOGO)
 */
public enum GameState {

    /**
     * Clientes estão entrando na sala e marcando prontidão.
     * Métodos permitidos: Conectar, MarcarPronto.
     */
    LOBBY,

    /**
     * A rodada está em andamento com a letra sorteada.
     * Clientes estão preenchendo as respostas localmente.
     * Métodos permitidos: DeclararStop, SubmeterPalavras.
     */
    RODADA_ATIVA,

    /**
     * O STOP foi acionado ou o tempo esgotou. Submissões de palavras estão travadas.
     * O servidor distribuiu as respostas e aguarda os votos dos jogadores.
     * Métodos permitidos: SubmeterVotos.
     */
    VALIDACAO,

    /**
     * Votos foram apurados e o placar da rodada foi calculado.
     * Exibição de pontuações parciais aos jogadores.
     */
    FIM_RODADA,

    /**
     * Número máximo de rodadas atingido ou condição de vitória atendida.
     * Sessão encerrada.
     */
    FIM_JOGO
}
