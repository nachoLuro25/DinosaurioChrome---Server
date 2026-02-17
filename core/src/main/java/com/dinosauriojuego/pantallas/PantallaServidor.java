package com.dinosauriojuego.pantallas;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

import com.dinosauriojuego.core.Main;
import com.dinosauriojuego.net.ServidorDino;
import com.dinosauriojuego.server.SnapshotDino;
import com.dinosauriojuego.utiles.Assets;
import com.dinosauriojuego.utiles.Constantes;

public class PantallaServidor extends ScreenAdapter {

    private final Main juego;
    private final Assets assets;

    private OrthographicCamera cam;
    private Viewport viewport;
    private SpriteBatch batch;
    private BitmapFont fontGrande;
    private BitmapFont fontMedia;
    private BitmapFont fontChica;
    private ShapeRenderer shape;
    private GlyphLayout layout;

    private float fondoX = 0f;
    private float velocidadFondo = 140f;
    private boolean esNoche = false;
    private static final int SCORE_CAMBIO = 500;

    private ServidorDino server;
    private float tiempo = 0f;

    // nubes del lobby
    private static final int NUM_NUBES = 6;
    private float[] nubeX   = new float[NUM_NUBES];
    private float[] nubeY   = new float[NUM_NUBES];
    private float[] nubeSpd = new float[NUM_NUBES];
    private float[] nubeW   = new float[NUM_NUBES];

    // cactus decorativos fijos en el fondo del lobby
    private float[] cactusDecoX = { 180f, 520f, 820f, 1100f };

    // animacion del dino del lobby
    private float dinoAnimTick = 0f;

    private static final float X_JUGADOR_1 = Constantes.X_JUGADOR_1;
    private static final float X_JUGADOR_2 = Constantes.X_JUGADOR_2;

    // paleta fiel al juego de Chrome
    private static final Color COL_BG      = new Color(0.95f, 0.95f, 0.95f, 1f);
    private static final Color COL_BLANCO  = new Color(1f,    1f,    1f,    1f);
    private static final Color COL_NUBE    = new Color(0.82f, 0.82f, 0.82f, 1f);
    private static final Color COL_PISO    = new Color(0.70f, 0.70f, 0.70f, 1f);
    private static final Color COL_GRIS    = new Color(0.55f, 0.55f, 0.55f, 1f);
    private static final Color COL_GRIS_OSC= new Color(0.38f, 0.38f, 0.38f, 1f);
    private static final Color COL_NEGRO   = new Color(0.20f, 0.20f, 0.20f, 1f);

    public PantallaServidor(Main juego, Assets assets) {
        this.juego = juego;
        this.assets = assets;
    }

    @Override
    public void show() {
        cam      = new OrthographicCamera();
        viewport = new FitViewport(Constantes.ANCHO_VIRTUAL, Constantes.ALTO_VIRTUAL, cam);
        batch    = new SpriteBatch();
        shape    = new ShapeRenderer();
        layout   = new GlyphLayout();

        fontGrande = new BitmapFont();
        fontMedia  = new BitmapFont();
        fontChica  = new BitmapFont();

        // posiciones iniciales de nubes
        float[] alts  = { 430f, 470f, 510f, 400f, 445f, 485f };
        float[] anchs = { 155f, 115f, 195f, 135f, 175f, 108f };
        float[] spds  = {  28f,  22f,  18f,  32f,  25f,  20f };
        java.util.Random rng = new java.util.Random(7);
        for (int i = 0; i < NUM_NUBES; i++) {
            nubeX[i]   = rng.nextFloat() * Constantes.ANCHO_VIRTUAL;
            nubeY[i]   = alts[i];
            nubeSpd[i] = spds[i];
            nubeW[i]   = anchs[i];
        }

        server = new ServidorDino();
        server.setDaemon(true);
        server.start();
    }

    // -------------------------------------------------------------------------
    @Override
    public void render(float delta) {
        tiempo       += delta;
        dinoAnimTick += delta * 60f;

        SnapshotDino snap         = server.getLastSnapshot();
        boolean      partidaEnCurso = server.isPartidaIniciada() && server.getCantClientes() >= 2;

        viewport.apply();
        cam.update();

        if (!partidaEnCurso) {
            dibujarLobby(delta);
            return;
        }

        // ---- PARTIDA EN CURSO ------------------------------------------------
        Gdx.gl.glClearColor(COL_BG.r, COL_BG.g, COL_BG.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        if (snap != null) esNoche = (snap.score / SCORE_CAMBIO) % 2 == 1;
        Texture fondo = esNoche ? assets.fondoNoche : assets.fondoDia;

        boolean freezeBg = (snap != null && snap.terminado);
        if (!freezeBg) fondoX -= velocidadFondo * delta;
        float w = Constantes.ANCHO_VIRTUAL;

        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        batch.draw(fondo, fondoX, 0, w, Constantes.ALTO_VIRTUAL);
        batch.draw(fondo, fondoX + w, 0, w, Constantes.ALTO_VIRTUAL);
        if (fondoX <= -w) fondoX = 0f;

        if (snap != null) {
            // obstáculos
            for (SnapshotDino.ObstacleState o : snap.obstacles) {
                if (o.type == 0) {
                    Texture t;
                    if      (o.variant == 0) t = assets.cactusChico1;
                    else if (o.variant == 1) t = assets.cactusChico2;
                    else if (o.variant == 2) t = assets.cactusGrande1;
                    else if (o.variant == 3) t = assets.cactusGrande2;
                    else                     t = assets.cactusCombinado;
                    batch.draw(t, o.x, Constantes.Y_PISO);
                } else {
                    Texture t = (snap.tick % 12 < 6) ? assets.ptero1 : assets.ptero2;
                    batch.draw(t, o.x, o.y);
                }
            }

            // dinos
            Texture d1 = elegirDinoTex(snap.p1.vivo, snap.p1.enPiso, snap.p1.agachado, snap.tick);
            batch.draw(d1, X_JUGADOR_1, snap.p1.y);
            Texture d2 = elegirDinoTex(snap.p2.vivo, snap.p2.enPiso, snap.p2.agachado, snap.tick);
            batch.draw(d2, X_JUGADOR_2, snap.p2.y);

            // HUD minimalista estilo Chrome: score arriba a la derecha
            fontMedia.setColor(COL_GRIS_OSC);
            fontMedia.getData().setScale(1.5f);
            String scoreStr = String.format("%05d", snap.score);
            layout.setText(fontMedia, scoreStr);
            fontMedia.draw(batch, scoreStr,
                    Constantes.ANCHO_VIRTUAL - layout.width - 24,
                    Constantes.ALTO_VIRTUAL - 22);

            // etiquetas de jugadores
            fontChica.setColor(COL_GRIS_OSC);
            fontChica.getData().setScale(1.1f);
            fontChica.draw(batch, "P1", X_JUGADOR_1 + 8, snap.p1.y + 74);
            fontChica.draw(batch, "P2", X_JUGADOR_2 + 8, snap.p2.y + 74);

            if (snap.terminado) {
                batch.end();
                dibujarFinPartida(snap);
                return;
            }
        }
        batch.end();
    }

    // -------------------------------------------------------------------------
    private void dibujarLobby(float delta) {
        float W = Constantes.ANCHO_VIRTUAL;
        float H = Constantes.ALTO_VIRTUAL;
        int   cant = server.getCantClientes();

        // fondo color Chrome
        Gdx.gl.glClearColor(COL_BG.r, COL_BG.g, COL_BG.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shape.setProjectionMatrix(cam.combined);

        // --- nubes moviéndose ---
        shape.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < NUM_NUBES; i++) {
            nubeX[i] -= nubeSpd[i] * delta;
            if (nubeX[i] + nubeW[i] < 0) nubeX[i] = W + 20;
            dibujarNube(nubeX[i], nubeY[i], nubeW[i]);
        }
        shape.end();

        // --- línea del piso ---
        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(COL_PISO);
        shape.rect(0, Constantes.Y_PISO - 4, W, 4f);
        shape.end();

        // --- cactus decorativos grises en el piso ---
        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        batch.setColor(0.78f, 0.78f, 0.78f, 1f);
        for (float cx : cactusDecoX) {
            batch.draw(assets.cactusChico1, cx, Constantes.Y_PISO);
        }
        batch.setColor(COL_BLANCO);

        // --- dino animado caminando ---
        Texture dinoTex = ((int) dinoAnimTick % 12 < 6) ? assets.dinoMov1 : assets.dinoMov2;
        batch.draw(dinoTex, 280f, Constantes.Y_PISO);

        // ---- TEXTOS ----

        // HI score decorativo (esquina superior derecha, estilo Chrome)
        fontMedia.setColor(COL_GRIS);
        fontMedia.getData().setScale(1.45f);
        String hiStr = "HI  00000";
        layout.setText(fontMedia, hiStr);
        fontMedia.draw(batch, hiStr, W - layout.width - 24, H - 24);

        // título grande
        fontGrande.setColor(COL_NEGRO);
        fontGrande.getData().setScale(3.5f);
        centrarTexto(fontGrande, "DINO CHROME", W, H - 88);

        // línea fina debajo del título
        batch.end();
        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(COL_PISO);
        shape.rect(W / 2f - 210, H - 148, 420, 2.5f);
        shape.end();
        batch.begin();

        // subtítulo
        fontChica.setColor(COL_GRIS);
        fontChica.getData().setScale(1.25f);
        centrarTexto(fontChica, "MULTIJUGADOR  —  SERVIDOR", W, H - 170);

        // --- bloque de estado de conexion ---
        float bloqueY = H / 2f + 70f;

        // texto principal de estado
        fontMedia.getData().setScale(1.55f);
        if (cant == 0) {
            fontMedia.setColor(COL_GRIS_OSC);
            centrarTexto(fontMedia, "Esperando jugadores...", W, bloqueY);
        } else if (cant == 1) {
            fontMedia.setColor(COL_NEGRO);
            centrarTexto(fontMedia, "Jugador 1 conectado", W, bloqueY);
        } else {
            fontMedia.setColor(COL_NEGRO);
            centrarTexto(fontMedia, "Ambos jugadores conectados", W, bloqueY);
        }

        // subtexto de estado
        fontChica.setColor(COL_GRIS_OSC);
        fontChica.getData().setScale(1.2f);
        String sub = (cant < 2)
                ? cant + " / 2  jugadores conectados"
                : "Listos para empezar";
        centrarTexto(fontChica, sub, W, bloqueY - 36);

        // --- indicadores P1 / P2 ---
        batch.end();

        float circY  = H / 2f - 30f;
        float circ1X = W / 2f - 140f;
        float circ2X = W / 2f + 140f;
        float radio  = 20f;

        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(cant >= 1 ? COL_NEGRO : COL_NUBE);
        shape.circle(circ1X, circY, radio);
        shape.setColor(cant >= 2 ? COL_NEGRO : COL_NUBE);
        shape.circle(circ2X, circY, radio);
        shape.end();

        shape.begin(ShapeRenderer.ShapeType.Line);
        Gdx.gl.glLineWidth(2f);
        shape.setColor(COL_PISO);
        shape.circle(circ1X, circY, radio + 4);
        shape.circle(circ2X, circY, radio + 4);
        shape.end();
        Gdx.gl.glLineWidth(1f);

        batch.begin();
        // letras dentro del círculo
        fontChica.getData().setScale(1.15f);
        fontChica.setColor(cant >= 1 ? COL_BLANCO : COL_GRIS);
        centrarTexto(fontChica, "P1", circ1X * 2f, circY + 9);
        fontChica.setColor(cant >= 2 ? COL_BLANCO : COL_GRIS);
        centrarTexto(fontChica, "P2", circ2X * 2f, circY + 9);

        // etiquetas debajo
        fontChica.setColor(COL_GRIS_OSC);
        fontChica.getData().setScale(1.1f);
        centrarTexto(fontChica, "Jugador 1", circ1X * 2f, circY - radio - 14);
        centrarTexto(fontChica, "Jugador 2", circ2X * 2f, circY - radio - 14);

        // --- mensaje inferior ---
        if (cant == 2) {
            // parpadeo como el "Press Space" del original
            float alpha = 0.5f + 0.5f * (float)Math.abs(Math.sin(tiempo * 3.2f));
            fontMedia.setColor(COL_NEGRO.r, COL_NEGRO.g, COL_NEGRO.b, alpha);
            fontMedia.getData().setScale(1.4f);
            centrarTexto(fontMedia, "Ambos presionen  JUGAR ONLINE", W, H / 2f - 108f);
        } else {
            // puntos animados estilo "cargando"
            int dots = (int)(tiempo * 2f) % 4;
            String espera = "Buscando en la red local" + ".".repeat(dots);
            fontChica.setColor(COL_GRIS);
            fontChica.getData().setScale(1.15f);
            centrarTexto(fontChica, espera, W, H / 2f - 108f);
        }

        // puerto inferior izquierdo
        fontChica.setColor(COL_NUBE);
        fontChica.getData().setScale(1.0f);
        fontChica.draw(batch, "UDP :" + ServidorDino.PUERTO, 20, 26);

        batch.end();
    }

    // -------------------------------------------------------------------------
    private void dibujarFinPartida(SnapshotDino snap) {
        float W = Constantes.ANCHO_VIRTUAL;
        float H = Constantes.ALTO_VIRTUAL;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shape.setProjectionMatrix(cam.combined);

        // overlay blanco semitransparente
        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(0.95f, 0.95f, 0.95f, 0.80f);
        shape.rect(0, 0, W, H);
        shape.end();

        // caja central
        float bw = 540f, bh = 196f;
        float bx = (W - bw) / 2f, by = (H - bh) / 2f;

        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(COL_BLANCO);
        shape.rect(bx, by, bw, bh);
        shape.end();

        // borde negro fino
        shape.begin(ShapeRenderer.ShapeType.Line);
        Gdx.gl.glLineWidth(2f);
        shape.setColor(COL_NEGRO);
        shape.rect(bx, by, bw, bh);
        shape.end();
        Gdx.gl.glLineWidth(1f);

        // franja negra superior (estilo panel Chrome)
        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(COL_NEGRO);
        shape.rect(bx, by + bh - 7f, bw, 7f);
        shape.end();

        batch.setProjectionMatrix(cam.combined);
        batch.begin();

        fontGrande.setColor(COL_NEGRO);
        fontGrande.getData().setScale(2.7f);
        centrarTexto(fontGrande, "GAME OVER", W, by + bh - 16);

        fontMedia.setColor(COL_GRIS_OSC);
        fontMedia.getData().setScale(1.6f);
        centrarTexto(fontMedia, snap.mensajeFin.toUpperCase(), W, by + bh / 2f + 14);

        fontChica.setColor(COL_GRIS);
        fontChica.getData().setScale(1.2f);
        centrarTexto(fontChica, "SCORE  " + String.format("%05d", snap.score), W, by + 46);

        batch.end();
    }

    // -------------------------------------------------------------------------
    /** Dibuja una nube pixel-art con rectángulos apilados, igual al juego de Chrome. */
    private void dibujarNube(float x, float y, float w) {
        float h = w * 0.40f;
        shape.setColor(COL_NUBE);
        shape.rect(x + w * 0.15f, y,            w * 0.70f, h * 0.52f); // cuerpo
        shape.rect(x + w * 0.30f, y + h * 0.42f, w * 0.40f, h * 0.36f); // cima
        shape.rect(x,             y + h * 0.14f, w * 0.20f, h * 0.34f); // lado izq
        shape.rect(x + w * 0.80f, y + h * 0.14f, w * 0.20f, h * 0.34f); // lado der
    }

    private void centrarTexto(BitmapFont font, String texto, float areaW, float y) {
        layout.setText(font, texto);
        font.draw(batch, texto, (areaW - layout.width) / 2f, y);
    }

    private Texture elegirDinoTex(boolean vivo, boolean enPiso, boolean agachado, int tick) {
        if (!vivo)    return assets.dinoMuerto;
        if (!enPiso)  return assets.dinoQuieto;
        if (agachado) return (tick % 12 < 6) ? assets.dinoAgach1 : assets.dinoAgach2;
        return (tick % 12 < 6) ? assets.dinoMov1 : assets.dinoMov2;
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void dispose() {
        if (server     != null) server.cerrar();
        if (batch      != null) batch.dispose();
        if (shape      != null) shape.dispose();
        if (fontGrande != null) fontGrande.dispose();
        if (fontMedia  != null) fontMedia.dispose();
        if (fontChica  != null) fontChica.dispose();
    }
}