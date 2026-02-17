package com.dinosauriojuego.utiles;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.graphics.Texture;

public class Assets {
    public Texture cactusChico1, cactusChico2, cactusGrande1, cactusGrande2, cactusCombinado;
    public Texture dinoQuieto, dinoMov1, dinoMov2, dinoAgach1, dinoAgach2, dinoMuerto;
    public Texture ptero1, ptero2;
    public Texture fondoDia, fondoNoche;

    private Preferences prefs;
    private float volumen = 0.5f;

    public void cargar() {
        cactusChico1 = new Texture("Cactus/cactuschico1.png");
        cactusChico2 = new Texture("Cactus/cactuschico2.png");
        cactusGrande1 = new Texture("Cactus/cactusgrande1.png");
        cactusGrande2 = new Texture("Cactus/cactusgrande2.png");
        cactusCombinado = new Texture("Cactus/cactuscombinado.png");

        dinoQuieto = new Texture("Dinosaurio2/dino2quieto.png");
        dinoMov1   = new Texture("Dinosaurio2/dino2movimiento1.png");
        dinoMov2   = new Texture("Dinosaurio2/dino2movimiento2.png");
        dinoAgach1 = new Texture("Dinosaurio2/dino2agachado1.png");
        dinoAgach2 = new Texture("Dinosaurio2/dino2agachado2.png");
        dinoMuerto = new Texture("Dinosaurio2/dino2muerto.png");

        ptero1 = new Texture("Ptedoractilos/pterodactilo1.png");
        ptero2 = new Texture("Ptedoractilos/pterodactilo2.png");

        fondoDia   = new Texture("Cactus/fondodia.png");
        fondoNoche = new Texture("Cactus/fondonoche.png");

        prefs = Gdx.app.getPreferences("dinochrome_server");
        volumen = prefs.getFloat("volumen", 0.5f);
    }

    public float getVolumen() { return volumen; }
    public void setVolumen(float v) {
        volumen = Math.max(0f, Math.min(1f, v));
        if (prefs != null) prefs.putFloat("volumen", volumen).flush();
    }

    public void dispose() {
        if (cactusChico1 != null) cactusChico1.dispose();
        if (cactusChico2 != null) cactusChico2.dispose();
        if (cactusGrande1 != null) cactusGrande1.dispose();
        if (cactusGrande2 != null) cactusGrande2.dispose();
        if (cactusCombinado != null) cactusCombinado.dispose();

        if (dinoQuieto != null) dinoQuieto.dispose();
        if (dinoMov1 != null) dinoMov1.dispose();
        if (dinoMov2 != null) dinoMov2.dispose();
        if (dinoAgach1 != null) dinoAgach1.dispose();
        if (dinoAgach2 != null) dinoAgach2.dispose();
        if (dinoMuerto != null) dinoMuerto.dispose();

        if (ptero1 != null) ptero1.dispose();
        if (ptero2 != null) ptero2.dispose();

        if (fondoDia != null) fondoDia.dispose();
        if (fondoNoche != null) fondoNoche.dispose();
    }
}
