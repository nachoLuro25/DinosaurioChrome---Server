package com.dinosauriojuego.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.dinosauriojuego.servidor.DinosaurioServerMain;

/**
 * Lanzador de la aplicación servidor para escritorio (LWJGL3)
 */
public class Lwjgl3LauncherServidor {
    public static void main(String[] args) {
        createApplication();
    }

    private static Lwjgl3Application createApplication() {
        return new Lwjgl3Application(new DinosaurioServerMain(), getDefaultConfiguration());
    }

    private static Lwjgl3ApplicationConfiguration getDefaultConfiguration() {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("DinosaurioChrome - SERVIDOR");

        // Configuración de rendimiento
        configuration.useVsync(true);
        configuration.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1);

        // Ventana del servidor
        configuration.setWindowedMode(1200, 720);

        // Ícono de la ventana
        configuration.setWindowIcon("dino.png", "dino.png", "dino.png", "dino.png");

        // OpenGL
        configuration.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.ANGLE_GLES20, 0, 0);

        return configuration;
    }
}