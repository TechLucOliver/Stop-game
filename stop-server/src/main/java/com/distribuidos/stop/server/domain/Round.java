package com.distribuidos.stop.server.domain;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Armazena os parâmetros dinâmicos da rodada (letra, categorias ativas) e
 * coleta as respostas e votos enviados pelos diferentes nós clientes na rede.
 *
 * Tratamento de Concorrência:
 * As chamadas RPC 'SubmeterPalavras' e 'SubmeterVotos' chegam de forma
 * assíncrona vindas de portas TCP distintas. Para suportar escrita paralela
 * sem deadlocks nem corrupção de ponteiros na JVM, empregamos ConcurrentHashMap.
 */
public class Round {
    private final int numeroRodadas;
    private final String letra;
    private final List<String> categorias;

    /**
     * Armazena as palavras submetidas:
     * Chave externa: idJogador | Valor: Mapa de (Categoria -> Palavra Digitada)
     */
    private final Map<String, Map<String, String>> respostasPorJogador = new ConcurrentHashMap<>();

    /**
     * Armazena as decisões de validação comunitária:
     * Chave: idJogadorAlvo_Categoria | Valor: Lista de votos booleanos (true = aceito, false = vetado)
     */
    private final Map<String, List<Boolean>> votosPorResposta = new ConcurrentHashMap<>();

    public Round(int numeroRodadas, String letra, List<String> categorias) {
        this.numeroRodadas = numeroRodadas;
        this.letra = letra;
        this.categorias = categorias;
    }

    public int getNumeroRodadas() {
        return numeroRodadas;
    }

    public String getLetra() {
        return letra;
    }

    public List<String> getCategorias() {
        return categorias;
    }

    /**
     * Registra com segurança concorrente as palavras enviadas por um cliente.
     */
    public void registrarRespostas(String idJogador, Map<String, String> respostas){
        // Normaliza as palavras em caixa alta e sem espaços sobrando
        Map<String, String> normalizadas = new HashMap<>();
        respostas.forEach((cat, val) -> normalizadas.put(cat.trim().toUpperCase(), val.trim().toUpperCase()));
        respostasPorJogador.put(idJogador, normalizadas);
    }

    public Map<String, Map<String, String>> getRespostasPorJogador(){
        return respostasPorJogador;
    }

    /**
     * Registra um voto emitido por um jogador sobre a palavra de outro.
     */
    public void registrarVoto(String idJogadorAlvo, String categoria, boolean aprovado){
        String chave = idJogadorAlvo + "_" + categoria.trim().toUpperCase();

        // computeIfAbsent garante inserção atômica da lista caso a chave ainda não exista
        votosPorResposta.computeIfAbsent(chave, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(aprovado);
    }

    /**
     * Avalia se uma determinada palavra foi aceita pelo consenso dos jogadores.
     * Regra: Uma resposta é válida se tiver mais aprovações do que rejeições.
     */
    public boolean isRespostaAceita(String idJogador, String categoria) {
        String chave = idJogador + "_" + categoria.trim().toUpperCase();
        List<Boolean> votos = votosPorResposta.get(chave);

        // Se ninguém votou contra (ou não houve votos), considera aceita por padrão
        if (votos == null || votos.isEmpty()) {
            return true;
        }

        long votosPositivos = votos.stream().filter(v -> v).count();
        long votosNegativos = votos.size() - votosPositivos;

        return votosPositivos >= votosNegativos;
    }
}
