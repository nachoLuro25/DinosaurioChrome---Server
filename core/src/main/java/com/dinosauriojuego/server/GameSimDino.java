package com.dinosauriojuego.server;

import com.dinosauriojuego.utiles.Constantes;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

public class GameSimDino {

    public static class Dino {
        public float y = Constantes.Y_PISO;
        public float vy = 0;
        public boolean enPiso = true;
        public boolean agachado = false;
        public boolean vivo = true;
    }

    public static class Obstacle {
        public int type;     //0 cactus, 1 ptero
        public int variant;
        public float x, y, w, h;

        //hitboxes
        public float hbL, hbR, hbB, hbT;

        public float hitX(){ return x + hbL; }
        public float hitY(){ return y + hbB; }
        public float hitW(){ return Math.max(1, w - hbL - hbR); }
        public float hitH(){ return Math.max(1, h - hbB - hbT); }
    }

    private final Random rng = new Random();

    public Dino p1 = new Dino();
    public Dino p2 = new Dino();
    public ArrayList<Obstacle> obstacles = new ArrayList<>();

    public float velocidad = Constantes.VELOCIDAD_INICIAL;
    public float tiempoSpawn = 0f;
    public float proximoSpawn = 1.2f;

    public float distancia = 0f;
    public int score = 0;

    public boolean terminado = false;
    public String mensajeFin = "";
    // dimensiones obstaculos
    private static final float CACTUS_W = 60;
    private static final float CACTUS_H = 80;
    private static final float PTERO_W  = 92;
    private static final float PTERO_H  = 60;

    // reinicia el juego
    public void reset() {
        obstacles.clear();
        velocidad = Constantes.VELOCIDAD_INICIAL;
        tiempoSpawn = 0f;
        proximoSpawn = rand(Constantes.TIEMPO_MIN_SPAWN, Constantes.TIEMPO_MAX_SPAWN);
        distancia = 0f;
        score = 0;
        terminado = false;
        mensajeFin = "";
        p1 = new Dino();
        p2 = new Dino();
    }
    // avanza la simulacion un paso de tiempo dt (segundos)
    public void step(float dt, boolean j1Jump, boolean j1Crouch, boolean j2Jump, boolean j2Crouch) {
        if (terminado) return;

        // actualiza velocidad y distancia
        velocidad += Constantes.ACELERACION_POR_SEGUNDO * dt;
        distancia += velocidad * dt;
        score = (int)(distancia / 10f);

        simDino(p1, dt, j1Jump, j1Crouch);
        simDino(p2, dt, j2Jump, j2Crouch);

        // manejo de spawn de obstaculos
        tiempoSpawn += dt;
        if (tiempoSpawn >= proximoSpawn) {
            spawnObstacle();
            tiempoSpawn = 0f;
            proximoSpawn = rand(Constantes.TIEMPO_MIN_SPAWN, Constantes.TIEMPO_MAX_SPAWN);
        }

        for (Obstacle o : obstacles) o.x -= velocidad * dt;

        // eliminar obstaculos que salieron de pantalla
        Iterator<Obstacle> it = obstacles.iterator();
        while (it.hasNext()) {
            Obstacle o = it.next();
            if (o.x + o.w < 0) it.remove();
        }

        // chequear colisiones
        boolean c1 = p1.vivo && collidesDino(1, p1);
        boolean c2 = p2.vivo && collidesDino(2, p2);

        if (c1) p1.vivo = false;
        if (c2) p2.vivo = false;

        if (!p1.vivo && p2.vivo) { terminado = true; mensajeFin = "gana jugador 2"; }
        else if (!p2.vivo && p1.vivo) { terminado = true; mensajeFin = "gana jugador 1"; }
        else if (!p1.vivo && !p2.vivo) { terminado = true; mensajeFin = "empate"; }
    }

    // simula un dino individual
    private void simDino(Dino d, float dt, boolean jumpJustPressed, boolean crouchHeld) {
        if (!d.vivo) return;
        // salto
        if (jumpJustPressed && d.enPiso) {
            d.vy = Constantes.VELOCIDAD_SALTO;
            d.enPiso = false;
        }
        // agacharse
        d.agachado = crouchHeld;
        // gravedad y movimiento vertical
        d.vy += Constantes.GRAVEDAD * dt;
        d.y += d.vy * dt;
        // piso
        if (d.y <= Constantes.Y_PISO) {
            d.y = Constantes.Y_PISO;
            d.vy = 0;
            d.enPiso = true;
        }
    }


    //COLISIONES: chequear si un dino colisiona con algun obstaculo
    private boolean collidesDino(int playerId, Dino d) {
        float dx = (playerId == 1) ? Constantes.X_JUGADOR_1 : Constantes.X_JUGADOR_2;


        float spriteW = 44f;
        float spriteH = d.agachado ? 40f : 60f;


        float hitW = spriteW - 14f;
        float hitH = spriteH - (d.agachado ? 10f : 14f);

        float hitX = dx + 7f;
        float hitY = d.y + 6f;

        if (d.agachado) hitY = d.y + 4f;

        // chequear colision con cada obstaculo
        for (Obstacle o : obstacles) {
            if (rectOverlap(hitX, hitY, hitW, hitH, o.hitX(), o.hitY(), o.hitW(), o.hitH())) return true;
        }
        return false;
    }

    // chequear si dos rectangulos se superponen
    private boolean rectOverlap(float ax, float ay, float aw, float ah,
                                float bx, float by, float bw, float bh) {
        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
    }


    private void spawnObstacle() {
        int nivel = 0;
        if (score > 300) nivel = 3; // nivel de dificultad basado en el puntaje
        else if (score > 150) nivel = 2;
        else if (score > 60) nivel = 1;

        float xSpawn = Constantes.ANCHO_VIRTUAL + 60;

        if (nivel < 2) spawnCactus(nivel, xSpawn);
        else {
            int r = rng.nextInt(5);
            if (r == 0) spawnPtero(xSpawn);
            else spawnCactus(nivel, xSpawn);
        }
    }


    private void spawnCactus(int nivel, float xSpawn) {
        Obstacle o = new Obstacle();
        o.type = 0;

        int variant;
        if (nivel == 0) variant = rng.nextInt(2);
        else if (nivel == 1) variant = rng.nextInt(4);
        else variant = rng.nextInt(5);

        o.variant = variant;
        o.x = xSpawn;
        o.y = Constantes.Y_PISO;
        o.w = CACTUS_W;
        o.h = CACTUS_H;


        o.hbL = 10;
        o.hbR = 10;
        o.hbB = 2;
        o.hbT = 6;

        obstacles.add(o);
    }


    private void spawnPtero(float xSpawn) {
        Obstacle o = new Obstacle();
        o.type = 1;

        int r = rng.nextInt(3);
        o.variant = r;

        float y = Constantes.Y_PISO + 55;
        if (r == 1) y = Constantes.Y_PISO + 85;
        if (r == 2) y = Constantes.Y_PISO + 110;

        o.x = xSpawn;
        o.y = y;
        o.w = PTERO_W;
        o.h = PTERO_H;

        o.hbL = 16;
        o.hbR = 16;
        o.hbB = 10;
        o.hbT = 12;

        obstacles.add(o);
    }

    private float rand(float a, float b) {
        return a + rng.nextFloat() * (b - a);
    }
}
