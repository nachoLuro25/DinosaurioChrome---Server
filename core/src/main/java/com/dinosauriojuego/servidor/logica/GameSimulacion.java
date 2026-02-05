package com.dinosauriojuego.servidor.logica;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Simulación autoritativa del juego en el servidor
 * Mantiene el estado real del juego y lo sincroniza con los clientes
 */
public class GameSimulacion {

    // Constantes de física
    private static final float GRAVEDAD = -800f;
    private static final float FUERZA_SALTO = 400f;
    private static final float Y_PISO = 60f;

    // Constantes de velocidad
    private static final float VELOCIDAD_INICIAL = 260f;
    private static final float VELOCIDAD_INCREMENTO = 10.0f;

    // Constantes de spawn
    private static final float SPAWN_INICIAL = 1.8f;
    private static final float SPAWN_MINIMO = 1.0f;
    private static final float ANCHO_PANTALLA = 1200f;

    // Estado de un dinosaurio
    public static class EstadoDino {
        public float y = Y_PISO;
        public float velocidadY = 0;
        public boolean enSuelo = true;
        public boolean agachado = false;
        public boolean vivo = true;
        public int spriteActual = 0;

        // Dimensiones
        private static final float ANCHO = 50f;
        private static final float ALTO_NORMAL = 60f;
        private static final float ALTO_AGACHADO = 30f;

        public float getAlto() {
            return agachado ? ALTO_AGACHADO : ALTO_NORMAL;
        }

        public void reset() {
            y = Y_PISO;
            velocidadY = 0;
            enSuelo = true;
            agachado = false;
            vivo = true;
            spriteActual = 0;
        }
    }

    // Estado de un obstáculo
    public static class EstadoObstaculo {
        public int tipo; // 0 = cactus, 1 = pájaro
        public int variante; // Diferentes variantes visuales
        public float x;
        public float y;
        public float ancho;
        public float alto;

        public Rectangle getBounds() {
            // Hitbox reducida para mejor jugabilidad
            float reduccionX = ancho * 0.15f;
            float reduccionY = alto * 0.10f;

            Rectangle bounds = new Rectangle();
            bounds.x = x + reduccionX;
            bounds.y = y + reduccionY;
            bounds.width = ancho - (reduccionX * 2);
            bounds.height = alto - (reduccionY * 2);
            return bounds;
        }
    }

    // Estado del juego
    public final EstadoDino jugador1 = new EstadoDino();
    public final EstadoDino jugador2 = new EstadoDino();
    public final List<EstadoObstaculo> obstaculos = new ArrayList<>();

    public float velocidad = VELOCIDAD_INICIAL;
    public int puntuacion = 0;
    public boolean terminado = false;
    public String mensajeFin = "";

    private float tiempoSpawnActual = 0f;
    private float tiempoSpawnObstaculo = SPAWN_INICIAL;
    private float tiempoAnimacion = 0f;

    /**
     * Actualiza la simulación del juego
     */
    public void actualizar(float deltaTime, boolean j1Saltar, boolean j1Agachar,
                           boolean j2Saltar, boolean j2Agachar) {
        if (terminado) {
            return;
        }

        // Actualizar dinosaurios
        actualizarDino(jugador1, deltaTime, j1Saltar, j1Agachar);
        actualizarDino(jugador2, deltaTime, j2Saltar, j2Agachar);

        // Incrementar velocidad
        velocidad += VELOCIDAD_INCREMENTO * deltaTime;

        // Actualizar puntuación (1 punto cada 10 unidades de distancia)
        puntuacion = (int)((velocidad - VELOCIDAD_INICIAL) * 0.4f);

        // Spawn de obstáculos
        tiempoSpawnActual += deltaTime;
        if (tiempoSpawnActual >= tiempoSpawnObstaculo) {
            spawnObstaculo();
            tiempoSpawnActual = 0;

            // Ajustar tiempo de spawn según velocidad
            float factorVelocidad = (velocidad - VELOCIDAD_INICIAL) / 500f;
            tiempoSpawnObstaculo = SPAWN_INICIAL + (factorVelocidad * 0.3f);
            if (tiempoSpawnObstaculo < SPAWN_MINIMO) {
                tiempoSpawnObstaculo = SPAWN_MINIMO;
            }
        }

        // Actualizar obstáculos
        Iterator<EstadoObstaculo> it = obstaculos.iterator();
        while (it.hasNext()) {
            EstadoObstaculo obs = it.next();
            obs.x -= velocidad * deltaTime;

            // Eliminar obstáculos fuera de pantalla
            if (obs.x + obs.ancho < 0) {
                it.remove();
            }
        }

        // Detectar colisiones
        if (jugador1.vivo && colisiona(jugador1, 50)) {
            jugador1.vivo = false;
        }
        if (jugador2.vivo && colisiona(jugador2, 50)) {
            jugador2.vivo = false;
        }

        // Determinar fin del juego
        if (!jugador1.vivo && !jugador2.vivo) {
            terminado = true;
            mensajeFin = "EMPATE!";
        } else if (!jugador1.vivo) {
            terminado = true;
            mensajeFin = "JUGADOR 2 GANA!";
        } else if (!jugador2.vivo) {
            terminado = true;
            mensajeFin = "JUGADOR 1 GANA!";
        }

        // Actualizar animación
        tiempoAnimacion += deltaTime;
        if (tiempoAnimacion >= 0.1f) {
            if (jugador1.enSuelo) {
                jugador1.spriteActual = (jugador1.spriteActual + 1) % 2;
            }
            if (jugador2.enSuelo) {
                jugador2.spriteActual = (jugador2.spriteActual + 1) % 2;
            }
            tiempoAnimacion = 0;
        }
    }

    /**
     * Actualiza un dinosaurio individual
     */
    private void actualizarDino(EstadoDino dino, float deltaTime, boolean saltar, boolean agachar) {
        if (!dino.vivo) {
            return;
        }

        // Salto
        if (saltar && dino.enSuelo && !dino.agachado) {
            dino.velocidadY = FUERZA_SALTO;
            dino.enSuelo = false;
        }

        // Agacharse (solo en el suelo)
        if (!dino.enSuelo) {
            dino.agachado = false;
        } else {
            dino.agachado = agachar;
        }

        // Aplicar gravedad
        dino.velocidadY += GRAVEDAD * deltaTime;
        dino.y += dino.velocidadY * deltaTime;

        // Limitar al suelo
        if (dino.y <= Y_PISO) {
            dino.y = Y_PISO;
            dino.velocidadY = 0;
            dino.enSuelo = true;
        }
    }

    /**
     * Detecta colisión entre un dinosaurio y los obstáculos
     */
    private boolean colisiona(EstadoDino dino, float x) {
        Rectangle dinoBounds = new Rectangle();
        dinoBounds.x = x;
        dinoBounds.y = dino.y;
        dinoBounds.width = EstadoDino.ANCHO;
        dinoBounds.height = dino.getAlto();

        for (EstadoObstaculo obs : obstaculos) {
            if (dinoBounds.overlaps(obs.getBounds())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Genera un nuevo obstáculo aleatorio
     */
    private void spawnObstaculo() {
        EstadoObstaculo obs = new EstadoObstaculo();
        obs.x = ANCHO_PANTALLA;

        float random = MathUtils.random(1f);
        if (random < 0.7f) {
            // Cactus
            obs.tipo = 0;
            obs.variante = MathUtils.random(0, 4);
            obs.y = Y_PISO;
            obs.ancho = 30;
            obs.alto = MathUtils.random(30f, 50f);
        } else {
            // Pájaro
            obs.tipo = 1;
            obs.variante = MathUtils.random(0, 2);
            obs.ancho = 50;
            obs.alto = 25;

            // Alturas variables para pájaros
            float[] alturas = {80f, 110f, 140f};
            obs.y = alturas[MathUtils.random(0, alturas.length - 1)];
        }

        obstaculos.add(obs);
    }

    /**
     * Reinicia el juego a su estado inicial
     */
    public void reset() {
        jugador1.reset();
        jugador2.reset();
        obstaculos.clear();
        velocidad = VELOCIDAD_INICIAL;
        puntuacion = 0;
        terminado = false;
        mensajeFin = "";
        tiempoSpawnActual = 0f;
        tiempoSpawnObstaculo = SPAWN_INICIAL;
        tiempoAnimacion = 0f;
    }
}