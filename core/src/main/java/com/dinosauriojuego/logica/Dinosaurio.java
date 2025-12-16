package com.dinosauriojuego.logica;

import com.badlogic.gdx.math.Rectangle;

/**
 * Clase que representa al dinosaurio jugador
 * Dimensiones reales del PNG: 81x93
 */
public class Dinosaurio {
    // Posición y dimensiones
    private float x;
    private float y;
    private float ancho;
    private float alto;

    // Física
    private float velocidadY;
    private static final float GRAVEDAD = -800f;
    private static final float FUERZA_SALTO = 400f;

    // Estados
    private boolean saltando;
    private boolean agachado;

    // Alturas
    private float alturaSuelo;
    private float alturaOriginal;
    private float alturaAgachado;

    // Colisión - Hitbox ajustada
    private Rectangle bounds;
    private static final float HITBOX_REDUCCION_X = 0.15f;
    private static final float HITBOX_REDUCCION_Y = 0.10f;

    // Animación de correr
    private float tiempoAnimacion;
    private static final float TIEMPO_CAMBIO_SPRITE = 0.1f; // Cambiar sprite cada 0.1 segundos
    private int spriteActual; // 0 = pata izquierda, 1 = pata derecha

    public Dinosaurio(float ancho, float alto) {
        this.ancho = ancho;
        this.alturaOriginal = alto;
        this.alturaAgachado = alto * 0.5f;
        this.alto = alturaOriginal;
        this.x = 50;
        this.alturaSuelo = 60;
        this.y = alturaSuelo;
        this.velocidadY = 0;
        this.saltando = false;
        this.agachado = false;
        this.bounds = new Rectangle();
        this.tiempoAnimacion = 0;
        this.spriteActual = 0;
        actualizarHitbox();
    }

    private void actualizarHitbox() {
        // Hitbox exacta sin reducciones
        bounds.x = x;
        bounds.y = y;
        bounds.width = ancho;
        bounds.height = alto;
    }

    public void update(float deltaTime, float alturaPantalla) {
        this.alturaSuelo = 60;

        // Aplicar gravedad
        velocidadY += GRAVEDAD * deltaTime;
        y += velocidadY * deltaTime;

        // Limitar a altura del suelo
        if (y <= alturaSuelo) {
            y = alturaSuelo;
            velocidadY = 0;
            saltando = false;
        }

        // Actualizar animación de correr (cuando está en el suelo, tanto corriendo como agachado)
        if (!saltando) {
            tiempoAnimacion += deltaTime;
            if (tiempoAnimacion >= TIEMPO_CAMBIO_SPRITE) {
                spriteActual = (spriteActual + 1) % 2; // Alterna entre 0 y 1
                tiempoAnimacion = 0;
            }
        }

        actualizarHitbox();
    }

    public void saltar() {
        if (!saltando && !agachado) {
            velocidadY = FUERZA_SALTO;
            saltando = true;
        }
    }

    public void agacharse(boolean agachar) {
        if (!saltando) {
            agachado = agachar;
            if (agachado) {
                alto = alturaAgachado;
            } else {
                alto = alturaOriginal;
            }
            actualizarHitbox();
        }
    }

    public boolean colisiona(Obstaculo obstaculo) {
        return bounds.overlaps(obstaculo.getBounds());
    }

    public void reset() {
        y = alturaSuelo;
        velocidadY = 0;
        saltando = false;
        agachado = false;
        alto = alturaOriginal;
        tiempoAnimacion = 0;
        spriteActual = 0;
        actualizarHitbox();
    }

    // Getters
    public float getX() { return x; }
    public float getY() { return y; }
    public float getAncho() { return ancho; }
    public float getAlto() { return alto; }
    public boolean estaSaltando() { return saltando; }
    public boolean estaAgachado() { return agachado; }
    public Rectangle getBounds() { return bounds; }
    public int getSpriteActual() { return spriteActual; }
}
