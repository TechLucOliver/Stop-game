package com.distribuidos.stop.server.domain;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mantém os dados de estado e pontuação de um cliente conectado.
 *
 * Concorrência:
 * Como a pontuação pode ser lida por threads de streaming e atualizada
 * por threads de cálculo de regras, utilizamos AtomicInteger para garantir
 * que a soma de pontos ocorra com atomicidade e visibilidade na memória RAM.
 */
public class Player {
    private final String id;
    private final String nome;
    private final AtomicInteger pontuacaoTotal;
    private volatile boolean pronto;
    private volatile boolean conectado;

    public Player(String id, String nome) {
        this.id = id;
        this.nome = nome;
        this.pontuacaoTotal = new AtomicInteger(0);
        this.pronto = false;
        this.conectado = true;
    }

    /**
     * Incrementa a pontuação do jogador de forma atômica e segura contra threads.
     */
    public void adicionarPontos(int pontos){
        this.pontuacaoTotal.addAndGet(pontos);
    }

    public String getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public int getPontuacaoTotal() {
        return pontuacaoTotal.get();
    }

    public boolean isPronto() {
        return pronto;
    }

    public boolean isConectado() {
        return conectado;
    }

    public void setPronto(boolean pronto) {
        this.pronto = pronto;
    }

    public void setConectado(boolean conectado) {
        this.conectado = conectado;
    }
}
