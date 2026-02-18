package com.dinosauriojuego.utiles;

public final class Constantes {
    private Constantes(){}

    public static final int ANCHO_VIRTUAL = 1280;
    public static final int ALTO_VIRTUAL  = 720;

    // Piso de cada pista (pantalla dividida en dos mitades)
    // P1 juega en la mitad SUPERIOR, P2 en la mitad INFERIOR
    public static final float Y_PISO_P1 = 430f; // piso de la pista superior (jugador 1)
    public static final float Y_PISO_P2 = 100f; // piso de la pista inferior (jugador 2)

    // Linea divisoria entre las dos pistas (centro de pantalla)
    public static final float Y_DIVISOR = 310f;

    public static final float GRAVEDAD = -1700f;
    public static final float VELOCIDAD_SALTO = 700f;

    public static final float VELOCIDAD_INICIAL = 320f;
    public static final float ACELERACION_POR_SEGUNDO = 10f;

    public static final float TIEMPO_MIN_SPAWN = 0.8f;
    public static final float TIEMPO_MAX_SPAWN = 1.6f;

    // X fija de cada dino (ambos arrancan en el mismo X pero en distinta pista)
    public static final float X_JUGADOR_1 = 180f;
    public static final float X_JUGADOR_2 = 180f;
}