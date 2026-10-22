package com.distribuidos.stop.server.concurrency;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Gerenciador de concorrência com exclusão mútua atômica via hardware (CAS).
 */
public class StopLockManeger {

    private final AtomicBoolean stopAcionado = new AtomicBoolean(false);
    private final AtomicReference<String> jogadorVencedorStop = new AtomicReference<>(null);

    public boolean tentarAcionarStop(String idJogador){
        boolean conseguiuAcionar = this.stopAcionado.compareAndSet(false, true);
        if (conseguiuAcionar){
            this.jogadorVencedorStop.set(idJogador);
        }
        return conseguiuAcionar;
    }

    public boolean getStopAcionado() {
        return this.stopAcionado.get();
    }

    public String getJogadorVencedorStop() {
        return this.jogadorVencedorStop.get();
    }

    public void reiniciar(){
        this.jogadorVencedorStop.set(null);
        this.stopAcionado.set(false);
    }
}
