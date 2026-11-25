package com.distribuidos.stop.server.domain;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RoomManager {

    private final ConcurrentHashMap<String, Sala> salas = new ConcurrentHashMap<>();

    public Sala criarSala(String nome, GameConfig configuracao) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("O nome da sala é obrigatório.");
        }
        if (configuracao == null) {
            throw new IllegalArgumentException("A configuração da sala é obrigatória.");
        }

        String id = UUID.randomUUID().toString();
        Sala sala = new Sala(id, nome.trim(), new Room(configuracao));
        salas.put(id, sala);
        return sala;
    }

    public Sala buscarSala(String id) {
        return id == null ? null : salas.get(id);
    }

    public List<Sala> listarSalas() {
        return salas.values().stream()
                .sorted(Comparator.comparing(Sala::getNome))
                .toList();
    }

    public static final class Sala {
        private final String id;
        private final String nome;
        private final Room room;

        private Sala(String id, String nome, Room room) {
            this.id = id;
            this.nome = nome;
            this.room = room;
        }

        public String getId() {
            return id;
        }

        public String getNome() {
            return nome;
        }

        public Room getRoom() {
            return room;
        }

        public int getJogadoresConectados() {
            return (int) room.getJogadores().values().stream()
                    .filter(Player::isConectado)
                    .count();
        }

        public String getEstado() {
            return room.getEstadoAtual().name();
        }
    }
}
