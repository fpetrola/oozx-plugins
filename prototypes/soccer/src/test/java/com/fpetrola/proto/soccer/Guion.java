package com.fpetrola.proto.soccer;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

/**
 * Un partido sin ventana y un guion de teclas: pone jugadores donde hace falta, aprieta botones a
 * 60 cuadros por segundo, como el juego de verdad, y deja mirar qué pasó. Arranca sin la máquina
 * (los de campo quietos; los arqueros atajan igual) y con la pelota en juego.
 */
final class Guion implements AutoCloseable {
    static final float CUADRO = 1 / 60f;
    static final Vector2 NADA = new Vector2(), ARRIBA = new Vector2(1, 0), ABAJO = new Vector2(-1, 0),
            DERECHA = new Vector2(0, 1), IZQUIERDA = new Vector2(0, -1);

    final Match m;
    final Pad pad = new Pad();

    Guion(long semilla) {
        MathUtils.random.setSeed(semilla);
        m = new Match();
        m.scheme = new SensibleScheme();  // estas pruebas son del esquema con suela, que quedó guardado sin usar
        m.aiEnabled = false;
        m.setPiece = null;
        m.messageTime = 0;
    }

    Player local(int i) {
        return m.home.players.get(i);
    }

    Player visita(int i) {
        return m.away.players.get(i);
    }

    /** Un cuadro con esas teclas: flechas, X y Z. */
    void paso(Vector2 flechas, boolean x, boolean z) {
        pad.set(flechas, x, z);
        m.update(pad, CUADRO);
    }

    /** Un cuadro con C también (el esquema a toques lo usa para subir el punto). */
    void paso(Vector2 flechas, boolean x, boolean z, boolean c) {
        pad.set(flechas, x, z, c);
        m.update(pad, CUADRO);
    }

    void paso(Vector2 flechas, boolean x) {
        paso(flechas, x, false);
    }

    /** Las mismas teclas durante {@code segundos}. */
    void durante(float segundos, Vector2 flechas, boolean x, boolean z) {
        for (int i = Math.round(segundos / CUADRO); i > 0; i--) paso(flechas, x, z);
    }

    void durante(float segundos, Vector2 flechas, boolean x) {
        durante(segundos, flechas, x, false);
    }

    /** Todos los de campo contra el cartel de un costado, salvo los que se piden. */
    void despejar(Player... quedan) {
        afuera:
        for (Player p : m.players) {
            for (Player q : quedan) if (q == p) continue afuera;
            if (!p.keeper) m.physics.place(p, -45 + p.index * 2.2f, 37f);
        }
    }

    /** Uno con la pelota al pie, mirando hacia {@code mira}, y es el que maneja el humano si es local. */
    Player conPelota(Player p, float x, float z, Vector2 mira) {
        m.physics.place(p, x, z);
        p.facing.set(mira).nor();
        m.physics.placeBall(x + p.facing.x * 0.6f, Ball.RADIUS, z + p.facing.y * 0.6f);
        if (p.team.human) m.controlled = p;
        m.control(p);
        return p;
    }

    /** La dirección {@code rel} (x adelante, y derecha) vista desde hacia donde mira {@code p}. */
    static Vector2 desde(Player p, Vector2 rel) {
        Vector2 f = p.facing;
        Vector2 d = new Vector2(f).scl(rel.x).add(-f.y * rel.y, f.x * rel.y);
        return d.isZero() ? d : d.nor();
    }

    @Override
    public void close() {
        m.dispose();
    }
}
