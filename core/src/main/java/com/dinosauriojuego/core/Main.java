package com.dinosauriojuego.core;

import com.badlogic.gdx.Game;
import com.dinosauriojuego.pantallas.PantallaServidor;
import com.dinosauriojuego.utiles.Assets;

public class Main extends Game {
    private Assets assets;
    //carga assets y setea la pantalla del servidor
    @Override
    public void create() {
        assets = new Assets();
        assets.cargar();
        setScreen(new PantallaServidor(this, assets));
    }

    @Override
    public void dispose() {
        super.dispose();
        if (assets != null) assets.dispose();
    }
}
