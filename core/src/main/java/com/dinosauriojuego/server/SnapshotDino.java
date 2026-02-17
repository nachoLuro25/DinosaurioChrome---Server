package com.dinosauriojuego.server;

import java.util.ArrayList;

// snapshot del estado del juego para enviarlo a los clientes
public class SnapshotDino {
    public int tick;
    public int score;
    public float velocidad;

    public boolean started;
    public boolean terminado;
    public String mensajeFin = "";


    public int resetReadyCount = 0;

    public DinoState p1 = new DinoState();
    public DinoState p2 = new DinoState();

    public ArrayList<ObstacleState> obstacles = new ArrayList<>();

    public static class DinoState {
        public float y, vy;
        public boolean enPiso, agachado, vivo;
    }

    public static class ObstacleState {
        public int type;     // 0=cactus, 1=ptero
        public int variant;  // cactus 0..4 / ptero 0..2
        public float x, y;
    }
}
