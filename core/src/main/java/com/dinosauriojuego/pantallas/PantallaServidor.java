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

    private final Main   juego;
    private final Assets assets;

    private OrthographicCamera cam;
    private Viewport      viewport;
    private SpriteBatch   batch;
    private BitmapFont    fontGrande;
    private BitmapFont    fontMedia;
    private BitmapFont    fontChica;
    private ShapeRenderer shape;
    private GlyphLayout   layout;

    // cada pista tiene su propio offset de scroll del fondo
    private float fondoXP1 = 0f;
    private float fondoXP2 = 0f;
    private static final float VEL_FONDO  = 140f;

    private boolean esNoche = false;
    private static final int SCORE_CAMBIO = 500;

    private ServidorDino server;
    private float tiempo       = 0f;
    private float dinoAnimTick = 0f;

    // nubes del lobby
    private static final int NUM_NUBES = 6;
    private float[] nubeX   = new float[NUM_NUBES];
    private float[] nubeY   = new float[NUM_NUBES];
    private float[] nubeSpd = new float[NUM_NUBES];
    private float[] nubeW   = new float[NUM_NUBES];

    private float[] cactusDecoX = { 180f, 520f, 820f, 1100f };

    // paleta Chrome
    private static final Color COL_BG      = new Color(0.95f, 0.95f, 0.95f, 1f);
    private static final Color COL_BLANCO  = new Color(1f,    1f,    1f,    1f);
    private static final Color COL_NUBE    = new Color(0.82f, 0.82f, 0.82f, 1f);
    private static final Color COL_PISO    = new Color(0.70f, 0.70f, 0.70f, 1f);
    private static final Color COL_GRIS    = new Color(0.55f, 0.55f, 0.55f, 1f);
    private static final Color COL_GRIS_OSC= new Color(0.38f, 0.38f, 0.38f, 1f);
    private static final Color COL_NEGRO   = new Color(0.20f, 0.20f, 0.20f, 1f);

    public PantallaServidor(Main juego, Assets assets) {
        this.juego  = juego;
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

        float[] alts  = { 530f, 570f, 610f, 500f, 545f, 585f };
        float[] anchs = { 155f, 115f, 195f, 135f, 175f, 108f };
        float[] spds  = {  28f,  22f,  18f,  32f,  25f,  20f };
        java.util.Random rng = new java.util.Random(7);
        for (int i = 0; i < NUM_NUBES; i++) {
            nubeX[i] = rng.nextFloat() * Constantes.ANCHO_VIRTUAL;
            nubeY[i] = alts[i]; nubeSpd[i] = spds[i]; nubeW[i] = anchs[i];
        }

        server = new ServidorDino();
        server.setDaemon(true);
        server.start();
    }

    @Override
    public void render(float delta) {
        tiempo       += delta;
        dinoAnimTick += delta * 60f;

        SnapshotDino snap         = server.getLastSnapshot();
        boolean      partidaEnCurso = server.isPartidaIniciada() && server.getCantClientes() >= 2;

        viewport.apply();
        cam.update();

        if (!partidaEnCurso) { dibujarLobby(delta); return; }

        // ---- PARTIDA EN CURSO -----------------------------------------------
        Gdx.gl.glClearColor(COL_BG.r, COL_BG.g, COL_BG.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        if (snap != null) esNoche = (snap.score / SCORE_CAMBIO) % 2 == 1;
        Texture fondo = esNoche ? assets.fondoNoche : assets.fondoDia;

        boolean freeze = (snap != null && snap.terminado);
        if (!freeze) {
            fondoXP1 -= VEL_FONDO * delta;
            fondoXP2 -= VEL_FONDO * delta;
        }
        float W = Constantes.ANCHO_VIRTUAL;
        float H = Constantes.ALTO_VIRTUAL;
        if (fondoXP1 <= -W) fondoXP1 = 0f;
        if (fondoXP2 <= -W) fondoXP2 = 0f;

        // convertir coordenadas virtuales a pixels reales para glScissor
        float scaleX = (float) Gdx.graphics.getWidth()  / W;
        float scaleY = (float) Gdx.graphics.getHeight() / H;

        int pista1Y = Math.round(Constantes.Y_DIVISOR * scaleY);
        int pista1H = Math.round((H - Constantes.Y_DIVISOR) * scaleY);
        int pista2H = Math.round(Constantes.Y_DIVISOR * scaleY);

        // ---- FONDO PISTA P1 (zona superior) con scissor ----
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glScissor(0, pista1Y, Gdx.graphics.getWidth(), pista1H);

        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        float offsetP1 = Constantes.Y_PISO_P1 - 210f; // 210 es donde el fondo tiene su piso naturalmente
        batch.draw(fondo, fondoXP1,     offsetP1, W, H);
        batch.draw(fondo, fondoXP1 + W, offsetP1, W, H);
        batch.end();

        // ---- FONDO PISTA P2 (zona inferior) con scissor ----
        Gdx.gl.glScissor(0, 0, Gdx.graphics.getWidth(), pista2H);

        batch.begin();
        float offsetP2 = Constantes.Y_PISO_P2 - 210f;
        batch.draw(fondo, fondoXP2,     offsetP2, W, H);
        batch.draw(fondo, fondoXP2 + W, offsetP2, W, H);
        batch.end();

        // apagar scissor para dibujar el resto sin restriccion
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);

        // ---- DIBUJAR PISTAS ----
        if (snap != null) {
            batch.begin();
            dibujarPista(snap, true);  // pista superior P1
            dibujarPista(snap, false); // pista inferior P2

            // HUD score
            fontMedia.setColor(COL_GRIS_OSC);
            fontMedia.getData().setScale(1.5f);
            String scoreStr = String.format("%05d", snap.score);
            layout.setText(fontMedia, scoreStr);
            fontMedia.draw(batch, scoreStr, W - layout.width - 24, H - 22);
            batch.end();

            // pisos y divisor
            dibujarPisosYDivisor();

            if (snap.terminado) { dibujarFinPartida(snap); return; }
        } else {
            dibujarPisosYDivisor();
        }
    }

    // dibuja los elementos de una pista: obstaculos, dino y etiqueta
    // esP1=true => pista superior (P1), esP1=false => pista inferior (P2)
    private void dibujarPista(SnapshotDino snap, boolean esP1) {
        final float DINO_W     = 74.8f;
        final float DINO_Y_OFF = 40f;

        float xJugador = esP1 ? Constantes.X_JUGADOR_1 : Constantes.X_JUGADOR_2;
        float yPiso    = esP1 ? Constantes.Y_PISO_P1   : Constantes.Y_PISO_P2;
        SnapshotDino.DinoState dino = esP1 ? snap.p1 : snap.p2;
        java.util.ArrayList<SnapshotDino.ObstacleState> obs =
                esP1 ? snap.obstaculosP1 : snap.obstaculosP2;
        String etiqueta = esP1 ? "P1" : "P2";

        for (SnapshotDino.ObstacleState o : obs) {
            if (o.type == 0) {
                Texture t;
                if      (o.variant == 0) t = assets.cactusChico1;
                else if (o.variant == 1) t = assets.cactusChico2;
                else if (o.variant == 2) t = assets.cactusGrande1;
                else if (o.variant == 3) t = assets.cactusGrande2;
                else                     t = assets.cactusCombinado;
                batch.draw(t, o.x, yPiso);
            } else {
                Texture t = (snap.tick % 12 < 6) ? assets.ptero1 : assets.ptero2;
                batch.draw(t, o.x, o.y);
            }
        }

        float dinoH = dino.agachado ? 51f : 102f;
        Texture dinoTex = elegirDinoTex(dino.vivo, dino.enPiso, dino.agachado, snap.tick);
        batch.draw(dinoTex, xJugador, dino.y + DINO_Y_OFF, DINO_W, dinoH);

        fontChica.setColor(COL_GRIS_OSC);
        fontChica.getData().setScale(1.1f);
        fontChica.draw(batch, etiqueta, xJugador + 8, dino.y + DINO_Y_OFF + dinoH + 6);
    }

    // dibuja los pisos de cada pista y la linea divisoria
    private void dibujarPisosYDivisor() {
        float W = Constantes.ANCHO_VIRTUAL;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shape.setProjectionMatrix(cam.combined);
        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(COL_PISO);
        shape.rect(0, Constantes.Y_PISO_P1 - 4, W, 4f); // piso pista P1
        shape.rect(0, Constantes.Y_PISO_P2 - 4, W, 4f); // piso pista P2
        shape.end();
    }

    // -------------------------------------------------------------------------
    private void dibujarLobby(float delta) {
        float W   = Constantes.ANCHO_VIRTUAL;
        float H   = Constantes.ALTO_VIRTUAL;
        int   cant = server.getCantClientes();

        Gdx.gl.glClearColor(COL_BG.r, COL_BG.g, COL_BG.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shape.setProjectionMatrix(cam.combined);

        // nubes moviendose
        shape.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < NUM_NUBES; i++) {
            nubeX[i] -= nubeSpd[i] * delta;
            if (nubeX[i] + nubeW[i] < 0) nubeX[i] = W + 20;
            dibujarNube(nubeX[i], nubeY[i], nubeW[i]);
        }
        shape.end();

        // piso del lobby
        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(COL_PISO);
        shape.rect(0, Constantes.Y_PISO_P1 - 4, W, 4f);
        shape.end();

        // cactus decorativos
        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        batch.setColor(0.78f, 0.78f, 0.78f, 1f);
        for (float cx : cactusDecoX) batch.draw(assets.cactusChico1, cx, Constantes.Y_PISO_P1);
        batch.setColor(COL_BLANCO);

        // dino animado
        Texture dinoTex = ((int) dinoAnimTick % 12 < 6) ? assets.dinoMov1 : assets.dinoMov2;
        batch.draw(dinoTex, 280f, Constantes.Y_PISO_P1);

        // HI score decorativo
        fontMedia.setColor(COL_GRIS);
        fontMedia.getData().setScale(1.45f);
        String hiStr = "HI  00000";
        layout.setText(fontMedia, hiStr);
        fontMedia.draw(batch, hiStr, W - layout.width - 24, H - 24);

        // titulo
        fontGrande.setColor(COL_NEGRO);
        fontGrande.getData().setScale(3.5f);
        centrarTexto(fontGrande, "DINO CHROME", W, H - 88);
        batch.end();

        // linea bajo el titulo
        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(COL_PISO);
        shape.rect(W / 2f - 210, H - 148, 420, 2.5f);
        shape.end();

        batch.begin();
        fontChica.setColor(COL_GRIS);
        fontChica.getData().setScale(1.25f);
        centrarTexto(fontChica, "MULTIJUGADOR  —  SERVIDOR", W, H - 170);

        float bloqueY = H / 2f + 70f;
        fontMedia.getData().setScale(1.55f);
        if (cant == 0) { fontMedia.setColor(COL_GRIS_OSC); centrarTexto(fontMedia, "Esperando jugadores...", W, bloqueY); }
        else if (cant == 1) { fontMedia.setColor(COL_NEGRO); centrarTexto(fontMedia, "Jugador 1 conectado", W, bloqueY); }
        else { fontMedia.setColor(COL_NEGRO); centrarTexto(fontMedia, "Ambos jugadores conectados", W, bloqueY); }

        fontChica.setColor(COL_GRIS_OSC);
        fontChica.getData().setScale(1.2f);
        centrarTexto(fontChica, (cant < 2) ? cant + " / 2  jugadores conectados" : "Listos para empezar", W, bloqueY - 36);

        batch.end();

        float circY = H / 2f - 30f, circ1X = W / 2f - 140f, circ2X = W / 2f + 140f, radio = 20f;
        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(cant >= 1 ? COL_NEGRO : COL_NUBE); shape.circle(circ1X, circY, radio);
        shape.setColor(cant >= 2 ? COL_NEGRO : COL_NUBE); shape.circle(circ2X, circY, radio);
        shape.end();
        shape.begin(ShapeRenderer.ShapeType.Line);
        Gdx.gl.glLineWidth(2f);
        shape.setColor(COL_PISO); shape.circle(circ1X, circY, radio + 4); shape.circle(circ2X, circY, radio + 4);
        shape.end();
        Gdx.gl.glLineWidth(1f);

        batch.begin();
        fontChica.getData().setScale(1.15f);
        fontChica.setColor(cant >= 1 ? COL_BLANCO : COL_GRIS); centrarTexto(fontChica, "P1", circ1X * 2f, circY + 9);
        fontChica.setColor(cant >= 2 ? COL_BLANCO : COL_GRIS); centrarTexto(fontChica, "P2", circ2X * 2f, circY + 9);
        fontChica.setColor(COL_GRIS_OSC); fontChica.getData().setScale(1.1f);
        centrarTexto(fontChica, "Jugador 1", circ1X * 2f, circY - radio - 14);
        centrarTexto(fontChica, "Jugador 2", circ2X * 2f, circY - radio - 14);

        if (cant == 2) {
            float alpha = 0.5f + 0.5f * (float) Math.abs(Math.sin(tiempo * 3.2f));
            fontMedia.setColor(COL_NEGRO.r, COL_NEGRO.g, COL_NEGRO.b, alpha);
            fontMedia.getData().setScale(1.4f);
            centrarTexto(fontMedia, "Ambos presionen  JUGAR ONLINE", W, H / 2f - 108f);
        } else {
            int dots = (int)(tiempo * 2f) % 4;
            fontChica.setColor(COL_GRIS); fontChica.getData().setScale(1.15f);
            centrarTexto(fontChica, "Buscando en la red local" + ".".repeat(dots), W, H / 2f - 108f);
        }

        fontChica.setColor(COL_NUBE); fontChica.getData().setScale(1.0f);
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

        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(0.95f, 0.95f, 0.95f, 0.80f);
        shape.rect(0, 0, W, H);
        shape.end();

        float bw = 540f, bh = 196f;
        float bx = (W - bw) / 2f, by = (H - bh) / 2f;

        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(COL_BLANCO); shape.rect(bx, by, bw, bh);
        shape.end();

        shape.begin(ShapeRenderer.ShapeType.Line);
        Gdx.gl.glLineWidth(2f); shape.setColor(COL_NEGRO); shape.rect(bx, by, bw, bh);
        shape.end(); Gdx.gl.glLineWidth(1f);

        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(COL_NEGRO); shape.rect(bx, by + bh - 7f, bw, 7f);
        shape.end();

        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        fontGrande.setColor(COL_NEGRO); fontGrande.getData().setScale(2.7f);
        centrarTexto(fontGrande, "GAME OVER", W, by + bh - 16);
        fontMedia.setColor(COL_GRIS_OSC); fontMedia.getData().setScale(1.6f);
        centrarTexto(fontMedia, snap.mensajeFin.toUpperCase(), W, by + bh / 2f + 14);
        fontChica.setColor(COL_GRIS); fontChica.getData().setScale(1.2f);
        centrarTexto(fontChica, "SCORE  " + String.format("%05d", snap.score), W, by + 46);
        batch.end();
    }

    private void dibujarNube(float x, float y, float w) {
        float h = w * 0.40f;
        shape.setColor(COL_NUBE);
        shape.rect(x + w * 0.15f, y,             w * 0.70f, h * 0.52f);
        shape.rect(x + w * 0.30f, y + h * 0.42f, w * 0.40f, h * 0.36f);
        shape.rect(x,             y + h * 0.14f, w * 0.20f, h * 0.34f);
        shape.rect(x + w * 0.80f, y + h * 0.14f, w * 0.20f, h * 0.34f);
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

    @Override public void resize(int w, int h) { viewport.update(w, h, true); }

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