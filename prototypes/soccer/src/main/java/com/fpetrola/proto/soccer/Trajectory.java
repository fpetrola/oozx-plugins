package com.fpetrola.proto.soccer;

import com.badlogic.gdx.math.Vector2;

/**
 * Por dónde va a ir la pelota en los próximos segundos, muestreado: con eso se sabe quién llega
 * primero, a quién darle el control y dónde va a encontrarla cada uno. Si alguien la lleva, la
 * pelota va con él; si está parada o en las manos del arquero, no se mueve.
 */
final class Trajectory {
    static final float DT = 1 / 20f;
    static final int N = 60;
    /** Lo que tarda en arrancar: un jugador no sale al instante a toda velocidad. */
    private static final float START = 0.1f;
    /** Más alta que esto no se juega con los pies ni se para con el pecho. */
    private static final float PLAYABLE = 1.7f;

    final float[] x = new float[N], y = new float[N], z = new float[N];
    private final Ball ghost = new Ball();
    private final Vector2 lead = new Vector2();

    void predict(Match m) {
        if (m.owner != null) {
            // Va con el que la lleva, y hacia donde la lleve su pie.
            Player o = m.owner;
            m.lead(o, lead);
            for (int k = 0; k < N; k++) {
                float t = k * DT;
                x[k] = o.pos.x + o.vel.x * t + o.facing.x * 0.6f + lead.x;
                y[k] = Ball.RADIUS;
                z[k] = o.pos.y + o.vel.y * t + o.facing.y * 0.6f + lead.y;
            }
            return;
        }
        boolean still = m.holder != null || m.setPiece != null;
        ghost.set(m.ball);
        for (int k = 0; k < N; k++) {
            x[k] = ghost.pos.x;
            y[k] = ghost.pos.y;
            z[k] = ghost.pos.z;
            if (!still) for (int s = 0; s < 4; s++) ghost.step(DT / 4);
        }
    }

    /** Cuándo llega {@code p}, corriendo a {@code speed}, a la pelota a una altura que se pueda jugar; si no llega, infinito. */
    float reach(Player p, float speed) {
        int k = first(p, speed);
        return k < 0 ? Float.MAX_VALUE : k * DT;
    }

    /** Como {@link #reach}, pero sin contar los primeros {@code from} segundos (el que la acaba de patear la tiene al lado). */
    float reachAfter(Player p, float speed, float from) {
        for (int k = 0; k < N; k++) {
            if (k * DT < from || y[k] > PLAYABLE) continue;
            float d = Vector2.dst(x[k], z[k], p.pos.x, p.pos.y) - Match.REACH;
            if (d <= speed * Math.max(0, k * DT - START)) return k * DT;
        }
        return Float.MAX_VALUE;
    }

    /** Dónde la encuentra; si no llega, dónde termina. */
    Vector2 meet(Player p, float speed, Vector2 out) {
        int k = first(p, speed);
        if (k < 0) k = N - 1;
        return out.set(x[k], z[k]);
    }

    /** Si antes de {@code within} segundos la pelota baja de {@code height} a menos de {@code dist} de él: le cae encima. */
    boolean fallsTo(Player p, float within, float height, float dist) {
        for (int k = 1; k < N && k * DT <= within; k++)
            if (y[k] < height && y[k] < y[k - 1] && Vector2.dst(x[k], z[k], p.pos.x, p.pos.y) < dist) return true;
        return false;
    }

    /** Si antes de {@code within} segundos le va a pasar a menos de {@code dist} de los pies. */
    boolean passes(Player p, float within, float dist) {
        for (int k = 0; k < N && k * DT <= within; k++)
            if (y[k] < 1.2f && Vector2.dst(x[k], z[k], p.pos.x, p.pos.y) < dist) return true;
        return false;
    }

    private int first(Player p, float speed) {
        for (int k = 0; k < N; k++) {
            if (y[k] > PLAYABLE) continue;
            float d = Vector2.dst(x[k], z[k], p.pos.x, p.pos.y) - Match.REACH;
            if (d <= speed * Math.max(0, k * DT - START)) return k;
        }
        return -1;
    }
}
