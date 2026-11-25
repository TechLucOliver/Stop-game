package com.distribuidos.stop.server.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class GameConfig {
    private final List<String> categorias;
    private final int totalRodadas;
    private final List<String> letrasDisponiveis;

    public GameConfig(final List<String> categorias, final int totalRodadas, List<String> letrasDisponiveis) {
        this.categorias = normalizarCategorias(categorias);
        this.letrasDisponiveis = normalizarLetras(letrasDisponiveis);

        if (totalRodadas <= 0){
            throw new IllegalArgumentException("A sala precisa ter pelo menos uma rodada.");
        }

        if (totalRodadas > this.letrasDisponiveis.size()){
            throw new IllegalArgumentException(
                    "A quantidade de rodadas não pode exceder o número de letras habilitadas."
            );
        }

        this.totalRodadas = totalRodadas;
    }

    private static List<String> normalizarCategorias(List<String> categorias) {
        if (categorias == null || categorias.isEmpty()) {
            throw new IllegalArgumentException("Cadastre pelo menos uma categoria.");
        }

        List<String> normalizadas = categorias.stream()
                .map(categoria -> {
                    if (categoria == null || categoria.isBlank()) {
                        throw new IllegalArgumentException("Categoria não pode ser vazia.");
                    }
                    return categoria.trim().toUpperCase(Locale.ROOT);
                })
                .toList();

        Set<String> unicas = new HashSet<>(normalizadas);
        if (unicas.size() != normalizadas.size()) {
            throw new IllegalArgumentException("Não é permitido cadastrar categorias duplicadas.");
        }

        return List.copyOf(normalizadas);
    }

    private static List<String> normalizarLetras(List<String> letras) {
        if (letras == null || letras.isEmpty()) {
            throw new IllegalArgumentException("Habilite pelo menos uma letra.");
        }

        List<String> normalizadas = letras.stream()
                .map(letra -> {
                    if (letra == null || letra.trim().length() != 1) {
                        throw new IllegalArgumentException("Cada letra deve conter exatamente um caractere.");
                    }

                    String valor = letra.trim().toUpperCase(Locale.ROOT);
                    if (valor.charAt(0) < 'A' || valor.charAt(0) > 'Z') {
                        throw new IllegalArgumentException("As letras devem estar entre A e Z.");
                    }
                    return valor;
                })
                .toList();

        Set<String> unicas = new HashSet<>(normalizadas);
        if (unicas.size() != normalizadas.size()) {
            throw new IllegalArgumentException("Não é permitido habilitar letras duplicadas.");
        }

        return List.copyOf(normalizadas);
    }

    public List<String> getCategorias() {
        return categorias;
    }

    public int getTotalRodadas() {
        return totalRodadas;
    }

    public List<String> getLetrasDisponiveis() {
        return letrasDisponiveis;
    }
}
