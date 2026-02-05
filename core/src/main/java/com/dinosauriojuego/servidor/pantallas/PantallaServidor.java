package com.dinosauriojuego.servidor.pantallas;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.dinosauriojuego.servidor.DinosaurioServerMain;
import com.dinosauriojuego.servidor.logica.GameSimulacion;
import com.dinosauriojuego.servidor.network.HiloServidor;

/**
 * Pantalla del servidor que visualiza el estado del juego
 */
public class PantallaServidor implements Screen {
    private static final float GAME_WIDTH = 1200;
    private static final float GAME_HEIGHT = 720;

    private DinosaurioServerMain game;
    private Skin skin;

    private OrthographicCamera camera;
    private Viewport viewport;
    private SpriteBatch batch;
    private ShapeRenderer shapeRenderer;

    // Texturas
    private Texture dinoCyan1, dinoCyan2;
    private Texture dinoOrange1, dinoOrange2;
    private Texture dinoAgachado1, dinoAgachado2;
    private Texture cactusTexture;
    private Texture pajaro1Texture, pajaro2Texture;
    private Texture fondoTexture;

    // Sonidos
    private Sound sonidoMuerte;

    // UI
    private Stage stage;
    private Label infoLabel;
    private Label estadoLabel;
    private Label puntuacionLabel;
    private Label mensajeLabel;

    // Servidor
    private HiloServidor servidor;

    // Animación de fondo
    private float fondoOffset = 0f;

    // Estado anterior para detectar cambios
    private boolean anteriormenteTerminado = false;

    public PantallaServidor(DinosaurioServerMain game, Skin skin) {
        this.game = game;
        this.skin = skin;

        this.camera = new OrthographicCamera();
        this.camera.setToOrtho(false, GAME_WIDTH, GAME_HEIGHT);
        this.viewport = new FitViewport(GAME_WIDTH, GAME_HEIGHT, camera);

        this.batch = new SpriteBatch();
        this.shapeRenderer = new ShapeRenderer();
        this.stage = new Stage(viewport);

        cargarRecursos();
        setupUI();

        // Iniciar servidor
        servidor = new HiloServidor();
        servidor.setDaemon(true);
        servidor.start();
    }

    private void cargarRecursos() {
        try {
            dinoCyan1 = new Texture(Gdx.files.internal("dino1.png"));
            dinoCyan2 = new Texture(Gdx.files.internal("dino2.png"));
            dinoOrange1 = new Texture(Gdx.files.internal("dino1.png"));
            dinoOrange2 = new Texture(Gdx.files.internal("dino2.png"));
            dinoAgachado1 = new Texture(Gdx.files.internal("dinoAgachado1.png"));
            dinoAgachado2 = new Texture(Gdx.files.internal("dinoAgachado2.png"));

            cactusTexture = new Texture(Gdx.files.internal("cactus.png"));
            pajaro1Texture = new Texture(Gdx.files.internal("pajaro1.png"));
            pajaro2Texture = new Texture(Gdx.files.internal("pajaro2.png"));

            fondoTexture = new Texture(Gdx.files.internal("fondo.png"));
            fondoTexture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);

            sonidoMuerte = Gdx.audio.newSound(Gdx.files.internal("sonidoMuerte.ogg"));
        } catch (Exception e) {
            System.out.println("⚠️ No se pudieron cargar algunas texturas/sonidos");
        }
    }

    private void setupUI() {
        infoLabel = new Label("", skin, "default");
        infoLabel.setFontScale(2.0f);
        infoLabel.setPosition(20, GAME_HEIGHT - 40);
        infoLabel.setColor(Color.WHITE);
        stage.addActor(infoLabel);

        estadoLabel = new Label("", skin, "default");
        estadoLabel.setFontScale(1.8f);
        estadoLabel.setPosition(20, GAME_HEIGHT - 80);
        estadoLabel.setColor(Color.LIGHT_GRAY);
        stage.addActor(estadoLabel);

        puntuacionLabel = new Label("", skin, "default");
        puntuacionLabel.setFontScale(2.5f);
        puntuacionLabel.setPosition(20, GAME_HEIGHT - 130);
        puntuacionLabel.setColor(Color.YELLOW);
        stage.addActor(puntuacionLabel);

        mensajeLabel = new Label("", skin, "default");
        mensajeLabel.setFontScale(4.0f);
        mensajeLabel.setColor(Color.RED);
        mensajeLabel.setVisible(false);
        stage.addActor(mensajeLabel);
    }

    @Override
    public void show() {
    }

    @Override
    public void render(float delta) {
        GameSimulacion sim = servidor.getSimulacion();

        // Actualizar labels
        infoLabel.setText("SERVIDOR - Clientes: " + servidor.getCantidadClientes() + "/2");

        if (!servidor.isJuegoIniciado()) {
            estadoLabel.setText("Esperando jugadores...");
        } else {
            estadoLabel.setText("Tick: " + servidor.getTick());
        }

        puntuacionLabel.setText("Puntuación: " + sim.puntuacion);

        // Detectar fin del juego y reproducir sonido
        if (sim.terminado && !anteriormenteTerminado) {
            if (sonidoMuerte != null) {
                sonidoMuerte.play(1.0f);
            }
            anteriormenteTerminado = true;
        }
        if (!sim.terminado) {
            anteriormenteTerminado = false;
        }

        // Mensaje de fin
        if (sim.terminado) {
            mensajeLabel.setText(sim.mensajeFin);
            mensajeLabel.setVisible(true);
            mensajeLabel.pack();
            mensajeLabel.setPosition((GAME_WIDTH - mensajeLabel.getWidth()) / 2, GAME_HEIGHT / 2);
        } else {
            mensajeLabel.setVisible(false);
        }

        // Actualizar fondo
        if (servidor.isJuegoIniciado() && !sim.terminado) {
            fondoOffset += sim.velocidad * delta;
        }

        // Renderizar
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.1f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        camera.update();
        batch.setProjectionMatrix(camera.combined);

        batch.begin();

        // Fondo
        if (fondoTexture != null) {
            float fondoAncho = fondoTexture.getWidth();
            float fondoAlto = 60;
            int repeticiones = (int) Math.ceil(GAME_WIDTH / fondoAncho) + 2;
            float offsetNormalizado = fondoOffset % fondoAncho;

            for (int i = -1; i < repeticiones; i++) {
                float x = i * fondoAncho - offsetNormalizado;
                batch.draw(fondoTexture, x, 22, fondoAncho, fondoAlto);
            }
        } else {
            // Suelo de respaldo
            batch.end();
            shapeRenderer.setProjectionMatrix(camera.combined);
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(0.5f, 0.5f, 0.5f, 1);
            shapeRenderer.rect(0, 0, GAME_WIDTH, 60);
            shapeRenderer.end();
            batch.begin();
        }

        // Obstáculos
        for (GameSimulacion.EstadoObstaculo obs : sim.obstaculos) {
            if (obs.tipo == 0 && cactusTexture != null) {
                batch.draw(cactusTexture, obs.x, obs.y, obs.ancho, obs.alto);
            } else if (obs.tipo == 1) {
                Texture pajaroTex = (servidor.getTick() % 12 < 6) ? pajaro1Texture : pajaro2Texture;
                if (pajaroTex != null) {
                    batch.draw(pajaroTex, obs.x, obs.y, obs.ancho, obs.alto);
                }
            }

            // Fallback si no hay texturas
            if ((obs.tipo == 0 && cactusTexture == null) ||
                    (obs.tipo == 1 && pajaro1Texture == null)) {
                batch.end();
                shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
                shapeRenderer.setColor(obs.tipo == 0 ? Color.RED : Color.BLUE);
                shapeRenderer.rect(obs.x, obs.y, obs.ancho, obs.alto);
                shapeRenderer.end();
                batch.begin();
            }
        }

        // Dinosaurio 1 (Cyan)
        dibujarDinosaurio(sim.jugador1, 50, Color.CYAN, dinoCyan1, dinoCyan2);

        // Dinosaurio 2 (Orange) - un poco más a la izquierda para ver ambos
        dibujarDinosaurio(sim.jugador2, 150, Color.ORANGE, dinoOrange1, dinoOrange2);

        batch.end();

        // UI
        stage.act(delta);
        stage.draw();
    }

    private void dibujarDinosaurio(GameSimulacion.EstadoDino dino, float x, Color color,
                                   Texture tex1, Texture tex2) {
        if (!dino.vivo) {
            // Dinosaurio muerto - dibujar en gris
            batch.setColor(0.5f, 0.5f, 0.5f, 1);
        } else {
            batch.setColor(color);
        }

        Texture textura;
        if (dino.agachado) {
            textura = (dino.spriteActual == 0) ? dinoAgachado1 : dinoAgachado2;
        } else {
            textura = (dino.spriteActual == 0) ? tex1 : tex2;
        }

        if (textura != null) {
            batch.draw(textura, x, dino.y, 50, dino.getAlto());
        } else {
            // Fallback
            batch.end();
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(color);
            shapeRenderer.rect(x, dino.y, 50, dino.getAlto());
            shapeRenderer.end();
            batch.begin();
        }

        batch.setColor(Color.WHITE);
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
    }

    @Override
    public void dispose() {
        if (servidor != null) {
            servidor.cerrar();
        }

        if (batch != null) batch.dispose();
        if (shapeRenderer != null) shapeRenderer.dispose();
        if (stage != null) stage.dispose();

        if (dinoCyan1 != null) dinoCyan1.dispose();
        if (dinoCyan2 != null) dinoCyan2.dispose();
        if (dinoOrange1 != null) dinoOrange1.dispose();
        if (dinoOrange2 != null) dinoOrange2.dispose();
        if (dinoAgachado1 != null) dinoAgachado1.dispose();
        if (dinoAgachado2 != null) dinoAgachado2.dispose();
        if (cactusTexture != null) cactusTexture.dispose();
        if (pajaro1Texture != null) pajaro1Texture.dispose();
        if (pajaro2Texture != null) pajaro2Texture.dispose();
        if (fondoTexture != null) fondoTexture.dispose();
        if (sonidoMuerte != null) sonidoMuerte.dispose();
    }
}