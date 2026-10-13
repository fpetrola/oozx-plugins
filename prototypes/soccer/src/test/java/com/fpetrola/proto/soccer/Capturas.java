package com.fpetrola.proto.soccer;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.ScreenUtils;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Capturas del juego de verdad, a mano (no es una prueba):
 * <pre>
 *   mvn test-compile exec:java -Dexec.classpathScope=test -Dexec.mainClass=com.fpetrola.proto.soccer.Capturas [-Dexec.args="nombre ..."]
 * </pre>
 * Cada escena prepara una jugada, aprieta botones cuadro a cuadro con un teclado de mentira (el
 * control lee ese en vez del de verdad) y guarda imágenes en {@code target/capturas}. Sin
 * argumentos corre todas; con nombres, sólo las que empiezan así.
 */
public class Capturas extends ApplicationAdapter {
    /** Una jugada para mostrar. */
    abstract static class Escena {
        final String nombre;
        /** Cámara: hacia dónde mira, inclinación, distancia, y el punto que mira. */
        float yaw = -48, inclinacion = 30, distancia = 30, x, z;
        /** En qué cuadros (desde que arranca) se saca una foto; termina dos cuadros después de la última. */
        int[] fotos = {60};

        Escena(String nombre) {
            this.nombre = nombre;
        }

        Escena camara(float yaw, float inclinacion, float distancia, float x, float z) {
            this.yaw = yaw;
            this.inclinacion = inclinacion;
            this.distancia = distancia;
            this.x = x;
            this.z = z;
            return this;
        }

        Escena fotos(int... cuadros) {
            fotos = cuadros;
            return this;
        }

        /** Pone a los jugadores y la pelota. */
        abstract void preparar(Capturas c);

        /** Las teclas del cuadro {@code t}. */
        abstract void teclas(Capturas c, int t);
    }

    private static final List<Escena> ESCENAS = new ArrayList<>();
    static String carpeta = "target/capturas";
    private static List<String> pedidas = List.of();

    public static void main(String[] args) {
        pedidas = Arrays.asList(args);
        new File(carpeta).mkdirs();
        Lwjgl3ApplicationConfiguration c = new Lwjgl3ApplicationConfiguration();
        c.setWindowedMode(1280, 720);
        c.setBackBufferConfig(8, 8, 8, 8, 16, 0, 4);
        c.setForegroundFPS(60);
        new Lwjgl3Application(new Capturas(), c);
    }

    static void escena(Escena e) {
        ESCENAS.add(e);
    }

    final Soccer juego = new Soccer();
    /** El teclado de mentira. */
    final Pad pad = new Pad();
    Match m;
    /** El esquema de control que usa la escena (por omisión, el del juego); la escena lo puede cambiar. */
    ControlScheme real, deSiempre;
    private final List<Escena> cola = new ArrayList<>();
    private int cuadro, inicio, actual;

    @Override
    public void create() {
        juego.create();
        escribir("fixedDt", 1 / 60f);  // cada cuadro, 1/60 s de partido, tarde lo que tarde en dibujarse
        m = (Match) leer("match");
        deSiempre = real = new SensibleScheme();  // las escenas de antes son del esquema con suela (guardado)
        m.scheme = new ControlScheme() {
            public String name() {
                return real.name();
            }

            public String help() {
                return real.help();
            }

            public void update(Match match, Player p, Pad ignorado, float dt) {
                real.update(match, p, pad, dt);
                pad.consume();
            }

            public Vector2 aim() {
                return real.aim();
            }

            public ShotPreview shot() {
                return real.shot();
            }

            public String hint() {
                return real.hint();
            }

            public Vector3 contact() {
                return real.contact();
            }
        };
        EscenasDeCapturas.cargar();
        for (Escena e : ESCENAS)
            if (pedidas.isEmpty() || pedidas.stream().anyMatch(e.nombre::startsWith)) cola.add(e);
        if (cola.isEmpty()) Gdx.app.exit();
        else empezar();
    }

    private void empezar() {
        Escena e = cola.get(actual);
        if (m.practice) m.setPractice(false);
        m.aiEnabled = false;
        m.setPiece = null;
        m.messageTime = 0;
        pad.set(Vector2.Zero, false, false);
        pad.consume();
        real = deSiempre;
        e.preparar(this);
        escribir("freeCam", true);
        ((Vector3) leer("focus")).set(e.x, 0, e.z);
        escribir("yaw", e.yaw);
        escribir("camPitch", e.inclinacion);
        escribir("distance", e.distancia);
        inicio = cuadro + 1;
    }

    @Override
    public void render() {
        if (actual >= cola.size()) return;
        Escena e = cola.get(actual);
        int t = cuadro - inicio;
        if (t >= 0) e.teclas(this, t);
        juego.render();
        for (int f : e.fotos)
            if (t == f) guardar(e.fotos.length == 1 ? e.nombre : e.nombre + "-" + f);
        if (t == e.fotos[e.fotos.length - 1] + 2) {
            if (++actual == cola.size()) Gdx.app.exit();
            else empezar();
        }
        cuadro++;
    }

    // ---- ayudas para las escenas ------------------------------------------------------------

    Player local(int i) {
        return m.home.players.get(i);
    }

    Player visita(int i) {
        return m.away.players.get(i);
    }

    /** Todos lejos, salvo los que se piden y los arqueros. */
    void despejar(Player... quedan) {
        afuera:
        for (Player p : m.players) {
            for (Player q : quedan) if (q == p) continue afuera;
            if (!p.keeper) m.physics.place(p, -45 + p.index * 2.2f, 37f);
        }
    }

    Player conPelota(Player p, float x, float z, Vector2 mira) {
        m.physics.place(p, x, z);
        p.facing.set(mira).nor();
        m.physics.placeBall(x + p.facing.x * 0.6f, Ball.RADIUS, z + p.facing.y * 0.6f);
        if (p.team.human) m.controlled = p;
        m.control(p);
        return p;
    }

    void teclas(Vector2 flechas, boolean x, boolean z) {
        pad.set(flechas, x, z);
    }

    void teclas(Vector2 flechas, boolean x, boolean z, boolean c) {
        pad.set(flechas, x, z, c);
    }

    private void guardar(String nombre) {
        int w = Gdx.graphics.getBackBufferWidth(), h = Gdx.graphics.getBackBufferHeight();
        byte[] px = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        BufferUtils.copy(px, 0, pm.getPixels(), px.length);
        File f = new File(carpeta, nombre + ".png");
        PixmapIO.writePNG(Gdx.files.absolute(f.getAbsolutePath()), pm);
        pm.dispose();
        System.out.println("captura: " + f);
    }

    private Object leer(String campo) {
        try {
            Field f = Soccer.class.getDeclaredField(campo);
            f.setAccessible(true);
            return f.get(juego);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private void escribir(String campo, Object valor) {
        try {
            Field f = Soccer.class.getDeclaredField(campo);
            f.setAccessible(true);
            f.set(juego, valor);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void dispose() {
        juego.dispose();
    }
}
