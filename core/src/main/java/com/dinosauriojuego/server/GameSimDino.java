package com.dinosauriojuego.server;

import com.dinosauriojuego.utiles.Constantes;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

public class GameSimDino {

    public static class Dino {
        public float y;
        public float vy = 0;
        public boolean enPiso = true;
        public boolean agachado = false;
        public boolean vivo = true;

        // cada dino arranca en su propio piso
        public Dino(float yPiso) {
            this.y = yPiso;
        }
    }

    public static class Obstacle {
        public int type;     //0 cactus, 1 ptero
        public int variant;
        public float x, y, w, h;

        //hitboxes (margenes para recortar el rectangulo de colision del sprite)
        public float hbL, hbR, hbB, hbT;

        public float hitX(){ return x + hbL; }
        public float hitY(){ return y + hbB; }
        public float hitW(){ return Math.max(1, w - hbL - hbR); }
        public float hitH(){ return Math.max(1, h - hbB - hbT); }
    }

    private final Random rng = new Random();

    public Dino p1 = new Dino(Constantes.Y_PISO_P1); // p1 arranca en pista superior
    public Dino p2 = new Dino(Constantes.Y_PISO_P2); // p2 arranca en pista inferior

    // cada jugador tiene su propia lista de obstaculos independiente
    public ArrayList<Obstacle> obstaculosP1 = new ArrayList<>();
    public ArrayList<Obstacle> obstaculosP2 = new ArrayList<>();

    public float velocidad = Constantes.VELOCIDAD_INICIAL;

    // timers de spawn independientes por jugador
    private float tiempoSpawnP1 = 0f;
    private float proximoSpawnP1 = 1.2f;
    private float tiempoSpawnP2 = 0f;
    private float proximoSpawnP2 = 1.0f; // arranca un poco desfasado para que no salgan al mismo tiempo

    public float distancia = 0f;
    public int score = 0;

    public boolean terminado = false;
    public String mensajeFin = "";

    // dimensiones base de los sprites de obstaculos
    private static final float CACTUS_W = 150;
    private static final float CACTUS_H = 150;
    private static final float PTERO_W  = 150;
    private static final float PTERO_H  = 150;

    // reinicia todo el estado del juego
    public void reset() {
        obstaculosP1.clear();
        obstaculosP2.clear();
        velocidad = Constantes.VELOCIDAD_INICIAL;
        tiempoSpawnP1 = 0f;
        tiempoSpawnP2 = 0f;
        proximoSpawnP1 = rand(Constantes.TIEMPO_MIN_SPAWN, Constantes.TIEMPO_MAX_SPAWN);
        proximoSpawnP2 = rand(Constantes.TIEMPO_MIN_SPAWN, Constantes.TIEMPO_MAX_SPAWN);
        distancia = 0f;
        score = 0;
        terminado = false;
        mensajeFin = "";
        p1 = new Dino(Constantes.Y_PISO_P1);
        p2 = new Dino(Constantes.Y_PISO_P2);
    }

    // avanza la simulacion un paso de tiempo dt (segundos)
    public void step(float dt, boolean j1Jump, boolean j1Crouch, boolean j2Jump, boolean j2Crouch) {
        if (terminado) return;

        // velocidad y distancia compartidas (los dos van a la misma velocidad)
        velocidad += Constantes.ACELERACION_POR_SEGUNDO * dt;
        distancia += velocidad * dt;
        score = (int)(distancia / 10f);

        // simular cada dino en su pista
        simDino(p1, Constantes.Y_PISO_P1, dt, j1Jump, j1Crouch);
        simDino(p2, Constantes.Y_PISO_P2, dt, j2Jump, j2Crouch);

        // spawn y movimiento de obstaculos de P1
        tiempoSpawnP1 += dt;
        if (tiempoSpawnP1 >= proximoSpawnP1) {
            spawnObstacle(obstaculosP1, Constantes.Y_PISO_P1);
            tiempoSpawnP1 = 0f;
            proximoSpawnP1 = rand(Constantes.TIEMPO_MIN_SPAWN, Constantes.TIEMPO_MAX_SPAWN);
        }

        // spawn y movimiento de obstaculos de P2
        tiempoSpawnP2 += dt;
        if (tiempoSpawnP2 >= proximoSpawnP2) {
            spawnObstacle(obstaculosP2, Constantes.Y_PISO_P2);
            tiempoSpawnP2 = 0f;
            proximoSpawnP2 = rand(Constantes.TIEMPO_MIN_SPAWN, Constantes.TIEMPO_MAX_SPAWN);
        }

        // mover todos los obstaculos hacia la izquierda
        for (Obstacle o : obstaculosP1) o.x -= velocidad * dt;
        for (Obstacle o : obstaculosP2) o.x -= velocidad * dt;

        // eliminar obstaculos que ya salieron de la pantalla
        limpiarObstaculos(obstaculosP1);
        limpiarObstaculos(obstaculosP2);

        // chequear colisiones de cada dino con SU lista de obstaculos
        boolean c1 = p1.vivo && collidesDino(Constantes.X_JUGADOR_1, p1, obstaculosP1);
        boolean c2 = p2.vivo && collidesDino(Constantes.X_JUGADOR_2, p2, obstaculosP2);

        if (c1) p1.vivo = false;
        if (c2) p2.vivo = false;

        // decidir quien gano
        if (!p1.vivo && p2.vivo)       { terminado = true; mensajeFin = "gana jugador 2"; }
        else if (!p2.vivo && p1.vivo)  { terminado = true; mensajeFin = "gana jugador 1"; }
        else if (!p1.vivo && !p2.vivo) { terminado = true; mensajeFin = "empate"; }
    }

    // simula un dino individual en su pista (yPiso es el piso de esa pista)
    private void simDino(Dino d, float yPiso, float dt, boolean jumpJustPressed, boolean crouchHeld) {
        if (!d.vivo) return;
        // salto: solo si esta en el piso
        if (jumpJustPressed && d.enPiso) {
            d.vy = Constantes.VELOCIDAD_SALTO;
            d.enPiso = false;
        }
        // agacharse
        d.agachado = crouchHeld;
        // gravedad y movimiento vertical
        d.vy += Constantes.GRAVEDAD * dt;
        d.y  += d.vy * dt;
        // colision con el piso de su pista
        if (d.y <= yPiso) {
            d.y = yPiso;
            d.vy = 0;
            d.enPiso = true;
        }
    }

    // elimina obstaculos que ya pasaron por la izquierda de la pantalla
    private void limpiarObstaculos(ArrayList<Obstacle> lista) {
        Iterator<Obstacle> it = lista.iterator();
        while (it.hasNext()) {
            if (it.next().x + CACTUS_W < 0) it.remove();
        }
    }

    // chequea si un dino colisiona con algun obstaculo de su lista
    private boolean collidesDino(float dx, Dino d, ArrayList<Obstacle> lista) {
        float spriteW = 74.8f;
        float spriteH = d.agachado ? 51f : 102f;

        float hitW = spriteW - 62f;
        float hitH = spriteH - (d.agachado ? 25f : 40f);

        float hitX = dx + 48f;
        float hitY = d.agachado ? d.y + 44f : d.y + 40f;

        for (Obstacle o : lista) {
            if (rectOverlap(hitX, hitY, hitW, hitH, o.hitX(), o.hitY(), o.hitW(), o.hitH())) return true;
        }
        return false;
    }

    // chequea superposicion de dos rectangulos
    private boolean rectOverlap(float ax, float ay, float aw, float ah,
                                float bx, float by, float bw, float bh) {
        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
    }

    // spawnea un obstaculo en la lista y piso indicados (cada pista tiene su propio piso)
    private void spawnObstacle(ArrayList<Obstacle> lista, float yPiso) {
        int nivel = 0;
        if (score > 300)      nivel = 3;
        else if (score > 150) nivel = 2;
        else if (score > 60)  nivel = 1;

        float xSpawn = Constantes.ANCHO_VIRTUAL + 60;

        if (nivel < 2) {
            spawnCactus(lista, nivel, xSpawn, yPiso);
        } else {
            int r = rng.nextInt(5);
            if (r == 0) spawnPtero(lista, xSpawn, yPiso);
            else        spawnCactus(lista, nivel, xSpawn, yPiso);
        }
    }

    // crea un cactus en la pista indicada (yPiso define en que altura aparece)
    private void spawnCactus(ArrayList<Obstacle> lista, int nivel, float xSpawn, float yPiso) {
        Obstacle o = new Obstacle();
        o.type = 0;

        int variant;
        if      (nivel == 0) variant = rng.nextInt(2);
        else if (nivel == 1) variant = rng.nextInt(4);
        else                 variant = rng.nextInt(5);

        o.variant = variant;
        o.x = xSpawn;
        o.y = yPiso; // el cactus se para en el piso de su pista
        o.w = CACTUS_W;
        o.h = CACTUS_H;

        // margenes de la hitbox (recortan el sprite para que la colision sea mas justa)
        o.hbL = 78;
        o.hbR = 48;
        o.hbB = 60;
        o.hbT = 20;

        lista.add(o);
    }

    // crea un pterodactilo en la pista indicada
    private void spawnPtero(ArrayList<Obstacle> lista, float xSpawn, float yPiso) {
        Obstacle o = new Obstacle();
        o.type = 1;

        int r = rng.nextInt(3);
        o.variant = r;

        // altura del ptero relativa al piso de su pista
        float offsetBase = 30f; // cuánto lo subís

        float y = yPiso + 55 + offsetBase;
        if (r == 1) y = yPiso + 100 + offsetBase;
        if (r == 2) y = yPiso + 110 + offsetBase;

        o.x = xSpawn;
        o.y = y;
        o.w = PTERO_W;
        o.h = PTERO_H;

        o.hbL = 45;
        o.hbR = 45;
        o.hbB = 0;
        o.hbT = 80;

        lista.add(o);
    }

    private float rand(float a, float b) {
        return a + rng.nextFloat() * (b - a);
    }
}