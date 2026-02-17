package com.dinosauriojuego.pantallas;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

import com.dinosauriojuego.core.Main;
import com.dinosauriojuego.net.ServidorDino;
import com.dinosauriojuego.server.SnapshotDino;
import com.dinosauriojuego.utiles.Assets;
import com.dinosauriojuego.utiles.Constantes;
//pantalla que muestra el estado del servidor y la partida en curso segun los snapshots recibidos
public class PantallaServidor extends ScreenAdapter {

    private final Main juego;
    private final Assets assets;

    private OrthographicCamera cam;
    private Viewport viewport;
    private SpriteBatch batch;
    private BitmapFont font;

    private float fondoX = 0f;
    private float velocidadFondo = 140f;

    private boolean esNoche = false;
    private static final int SCORE_CAMBIO = 500;

    private ServidorDino server;

    private static final float X_JUGADOR_1 = Constantes.X_JUGADOR_1;
    private static final float X_JUGADOR_2 = Constantes.X_JUGADOR_2;

    public PantallaServidor(Main juego, Assets assets) {
        this.juego = juego;
        this.assets = assets;
    }

    @Override
    public void show() {
        cam = new OrthographicCamera();
        viewport = new FitViewport(Constantes.ANCHO_VIRTUAL, Constantes.ALTO_VIRTUAL, cam);
        batch = new SpriteBatch();

        font = new BitmapFont();
        font.getData().setScale(1.6f);

        // SERVER UDP (nuevo)
        server = new ServidorDino();
        server.setDaemon(true);
        server.start();
    }

    @Override
    public void render(float delta) {
        SnapshotDino snap = server.getLastSnapshot();

        Gdx.gl.glClearColor(0,0,0,1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        cam.update();
        batch.setProjectionMatrix(cam.combined);
        batch.begin();

        //FONDO
        if (snap != null) {
            esNoche = (snap.score / SCORE_CAMBIO) % 2 == 1;
        }
        Texture fondo = esNoche ? assets.fondoNoche : assets.fondoDia;  //cambia el fondo segun el puntaje

        boolean freezeBg = (snap != null && snap.terminado) || !server.isPartidaIniciada(); //pausa el fondo si la partida termino o no empezo
        if (!freezeBg) fondoX -= velocidadFondo * delta; //mueve el fondo solo si la partida está en curso

        float w = Constantes.ANCHO_VIRTUAL;
        batch.draw(fondo, fondoX, 0, w, Constantes.ALTO_VIRTUAL);
        batch.draw(fondo, fondoX + w, 0, w, Constantes.ALTO_VIRTUAL);
        if (fondoX <= -w) fondoX = 0f;

        //pantalla de carga hasta que los jugaodres le den listo
        if (server.getCantClientes() < 2 || !server.isPartidaIniciada()) {
            font.setColor(0,0,0,1);
            font.getData().setScale(2.2f);
            font.draw(batch, "SERVIDOR DINO", 470, 600);

            font.getData().setScale(1.6f);
            font.draw(batch, "Clientes conectados: " + server.getCantClientes() + "/2", 450, 530);
            font.draw(batch, "Esperando que ambos presionen JUGAR ONLINE...", 300, 490);

            batch.end();
            return;
        }

        // si todavía no llego el primer snapshot
        if (snap == null) {
            font.setColor(1,1,1,1);
            font.getData().setScale(1.6f);
            font.draw(batch, "Cargando snapshot...", 520, 520);
            batch.end();
            return;
        }

        //obstaculos para dibujarlos en pantalla
        for (SnapshotDino.ObstacleState o : snap.obstacles) {
            if (o.type == 0) {
                Texture t;
                if (o.variant == 0) t = assets.cactusChico1;
                else if (o.variant == 1) t = assets.cactusChico2;
                else if (o.variant == 2) t = assets.cactusGrande1;
                else if (o.variant == 3) t = assets.cactusGrande2;
                else t = assets.cactusCombinado;

                batch.draw(t, o.x, Constantes.Y_PISO);
            } else {
                Texture t = (snap.tick % 12 < 6) ? assets.ptero1 : assets.ptero2;
                batch.draw(t, o.x, o.y);
            }
        }

        //dinos
        Texture d1 = elegirDinoTex(snap.p1.vivo, snap.p1.enPiso, snap.p1.agachado, snap.tick);
        batch.draw(d1, X_JUGADOR_1, snap.p1.y);

        Texture d2 = elegirDinoTex(snap.p2.vivo, snap.p2.enPiso, snap.p2.agachado, snap.tick);
        batch.draw(d2, X_JUGADOR_2, snap.p2.y);

        //HUD
        font.setColor(esNoche ? 1f : 0f, esNoche ? 1f : 0f, esNoche ? 1f : 0f, 1f);
        font.getData().setScale(1.6f);
        font.draw(batch, "score: " + snap.score, 20, Constantes.ALTO_VIRTUAL - 55);

        //fin de la partida
        if (snap.terminado) {
            font.setColor(1, 0.3f, 0.3f, 1);
            font.getData().setScale(2.0f);
            font.draw(batch, snap.mensajeFin, 480, 520);
        }

        batch.end();
    }

    private Texture elegirDinoTex(boolean vivo, boolean enPiso, boolean agachado, int tick) {
        if (!vivo) return assets.dinoMuerto;
        if (!enPiso) return assets.dinoQuieto;

        if (agachado) return (tick % 12 < 6) ? assets.dinoAgach1 : assets.dinoAgach2;
        return (tick % 12 < 6) ? assets.dinoMov1 : assets.dinoMov2;
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void dispose() {
        if (server != null) server.cerrar();
        if (batch != null) batch.dispose();
        if (font != null) font.dispose();
    }
}
