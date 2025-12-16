package com.dinosauriojuego;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.dinosauriojuego.pantallas.DinosaurioGameScreen;
import com.dinosauriojuego.pantallas.MenuScreen;

/**
 * Clase principal de la aplicación
 */
public class DinosaurioChromePrincipal extends Game {
    private Skin skin;
    private MenuScreen menuScreen;
    private DinosaurioGameScreen gameScreen;

    @Override
    public void create() {
        skin = new Skin(Gdx.files.internal("ui/uiskin.json"));
        menuScreen = new MenuScreen(this, skin);
        setScreen(menuScreen);
    }

    public void iniciarJuego() {
        if (gameScreen != null) {
            gameScreen.dispose();
        }
        gameScreen = new DinosaurioGameScreen(skin);
        setScreen(gameScreen);
    }

    public void volverAlMenu() {
        setScreen(menuScreen);
    }

    @Override
    public void dispose() {
        if (skin != null) {
            skin.dispose();
        }
        if (menuScreen != null) {
            menuScreen.dispose();
        }
        if (gameScreen != null) {
            gameScreen.dispose();
        }
    }
}
