package com.dinosauriojuego.servidor;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.dinosauriojuego.servidor.pantallas.PantallaServidor;

/**
 * Aplicación principal del servidor
 * Ejecuta la simulación del juego y visualiza el estado
 */
public class DinosaurioServerMain extends Game {
    private Skin skin;
    private PantallaServidor pantallaServidor;

    @Override
    public void create() {
        skin = new Skin(Gdx.files.internal("ui/uiskin.json"));
        pantallaServidor = new PantallaServidor(this, skin);
        setScreen(pantallaServidor);
    }

    @Override
    public void dispose() {
        if (skin != null) {
            skin.dispose();
        }
        if (pantallaServidor != null) {
            pantallaServidor.dispose();
        }
    }
}