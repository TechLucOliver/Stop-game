package com.distribuidos.stop.server.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Processa a matriz de respostas e votos após o fechamento da barreira de validação.
 *
 * Por que isolar esta classe (Single Responsibility Principle):
 * Em Sistemas Distribuídos, desacoplar a lógica de negócio da camada de transporte
 * (gRPC/Sockets) é fundamental para:
 * 1. Garantir que as regras de pontuação sejam funções puras e determinísticas
 *    (dadas as mesmas entradas, o resultado é rigorosamente o mesmo em qualquer nó).
 * 2. Permitir testes de unidade automatizados (JUnit) sem precisar subir portas de rede.
 *
 * Tabela de Pontuação Clássica do Stop:
 * - Resposta em branco ou que não inicia com a letra da rodada: 0 pontos.
 * - Resposta vetada pela maioria dos jogadores na validação: 0 pontos.
 * - Resposta válida que coincide com a resposta de outro jogador: 5 pontos.
 * - Resposta válida e exclusiva (outros jogadores deram respostas diferentes): 10 pontos.
 * - Resposta válida em categoria onde NENHUM outro jogador pontuou: 20 pontos.
 */
public class ScoreCalculator {

    /**
     * Calcula a pontuação individual obtida em cada categoria por todos os jogadores na rodada.
     *
     * @param round Objeto contendo as respostas coletadas e os votos da comunidade.
     * @param jogadoresParticipantes Lista de IDs de todos os jogadores ativos na rodada.
     * @return Mapa associativo: ID do Jogador -> Mapa (Categoria -> Pontos Ganhos)
     */
    public static Map<String, Map<String, Integer>> calcularPontosRodada(Round round, Set<String> jogadoresParticipantes){
        Map<String, Map<String, Integer>> pontuacaoFinalPorJogador = new HashMap<>();

        // Inicializa o mapa de retorno para cada jogador ativo
        for (String idJogador : jogadoresParticipantes){
            pontuacaoFinalPorJogador.put(idJogador, new HashMap<>());
        }

        String letraEsperada = round.getLetra().trim().toUpperCase();
        Map<String, Map<String, String>> respostas = round.getRespostasPorJogador();

        // Itera categoria por categoria (ex: "NOME", "CIDADE", "COR")
        for (String categoria : round.getCategorias()){
            String catUpper = categoria.trim().toUpperCase();

            // 1. Coleta apenas as palavras válidas da categoria nesta rodada
            // Mapa: idJogador -> Palavra Válida
            Map<String, String> respostasValidasDaCategoria = new HashMap<>();

            for (String idJogador : jogadoresParticipantes){
                Map<String, String> respostasDoJogador = respostas.get(idJogador);
                String palavra = (respostasDoJogador != null) ? respostasDoJogador.get(catUpper) : null;

                if (palavra != null && !palavra.trim().isEmpty()){
                    palavra = palavra.trim().toUpperCase();

                    // Regra 1: Validação sintática local (deve iniciar com a letra da rodada)
                    boolean iniciaComLetra = palavra.startsWith(letraEsperada);

                    // Regra 2: Validação distribuída por consenso (votação dos pares)
                    boolean aceitaPelaComunidade = round.isRespostaAceita(idJogador, catUpper);

                    if (iniciaComLetra && aceitaPelaComunidade){
                        respostasValidasDaCategoria.put(idJogador, palavra);
                    }else{
                        // Palavra rejeitada ou com letra errada: 0 pontos
                        pontuacaoFinalPorJogador.get(idJogador).put(catUpper, 0);
                    }
                }else {
                    // Não respondeu ou deixou em branco: 0 pontos
                    pontuacaoFinalPorJogador.get(idJogador).put(catUpper, 0);
                }
            }

            // 2. Contabiliza a frequência de cada palavra para identificar repetições
            Map<String, Integer> frequenciaPalavras = new HashMap<>();
            for (String palavraValida : respostasValidasDaCategoria.values()){
                frequenciaPalavras.put(palavraValida, frequenciaPalavras.getOrDefault(palavraValida, 0) + 1);
            }

            int totalValidasNaCategoria = respostasValidasDaCategoria.size();

            // 3. Aplica a distribuição de pontos aos jogadores que acertaram
            for (Map.Entry<String, String> entrada : respostasValidasDaCategoria.entrySet()){
                String idJogador = entrada.getKey();
                String palavra = entrada.getValue();
                int repeticoes = frequenciaPalavras.get(palavra);

                int pontosAtribuidos;

                if (totalValidasNaCategoria == 1){
                    // Apenas este jogador pontuou nesta categoria na rodada inteira
                    pontosAtribuidos = 20;
                } else if (repeticoes > 1) {
                    // Palavra repetida por 2 ou mais jogadores
                    pontosAtribuidos = 5;
                }else {
                    // Palavra exclusiva (outros jogadores pontuaram, mas com palavras distintas)
                    pontosAtribuidos = 10;
                }

                pontuacaoFinalPorJogador.get(idJogador).put(catUpper, pontosAtribuidos);
            }
        }

        return pontuacaoFinalPorJogador;
    }

    /**
     * Totaliza os pontos somados da rodada para cada jogador.
     *
     * @param pontosPorCategoria Mapa gerado pelo método {@link #calcularPontosRodada}.
     * @return Mapa: ID do Jogador -> Soma Total de Pontos Obtidos na Rodada.
     */
    public static Map<String, Integer> somarTotalRodada(Map<String, Map<String, Integer>> pontosPorCategoria){
        Map<String, Integer> totais = new HashMap<>();
        for (Map.Entry<String, Map<String, Integer>> entry : pontosPorCategoria.entrySet()){
            int soma = entry.getValue().values().stream().mapToInt(Integer::intValue).sum();
            totais.put(entry.getKey(),soma);
        }
        return totais;
    }
}
