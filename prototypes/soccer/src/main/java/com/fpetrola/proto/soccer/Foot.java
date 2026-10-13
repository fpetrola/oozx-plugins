package com.fpetrola.proto.soccer;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

/**
 * El pie de juego de un jugador: dónde está y cómo se mueve, dentro de lo que alcanza la pierna.
 * Cuando el control no lo usa, va con el cuerpo y el muñeco corre con su zancada. Cuando lo usa
 * (la suela, un amague), el control le dice hacia dónde moverse y el pie va, con su velocidad y su
 * aceleración, sin salirse del alcance. La pierna del muñeco lo sigue.
 */
final class Foot {
    /** Hasta dónde llega desde el centro del cuerpo: adelante, atrás y a los costados. */
    static final float FRONT = 0.85f, BACK = 0.45f, SIDE = 0.7f;
    /** Lo más rápido que se mueve el pie respecto del cuerpo, y cuánto cambia su velocidad por segundo. */
    static final float SPEED = 6f, ACCEL = 220f;
    /** Donde descansa apoyado sobre la pelota: un poco delante del cuerpo. */
    static final float REST = 0.3f;
    /** El amague dura esto: el pie pasa por arriba de la pelota de un costado al otro. */
    static final float FEINT = 0.25f;

    /** Dónde está (en la cancha: x, altura, z) y cómo se mueve. */
    final Vector3 pos = new Vector3(), vel = new Vector3();
    /** Si el control lo está usando; si no, va con el cuerpo. */
    boolean active;
    /** Lo que le queda al amague, y para qué lado sale (+1 derecha, -1 izquierda de hacia donde mira). */
    float feint, feintSide = 1;

    private final Vector2 want = new Vector2();

    /** Va con el cuerpo, descansando delante de él. */
    void rest(Player p) {
        active = false;
        pos.set(p.pos.x + p.facing.x * REST, Ball.RADIUS, p.pos.y + p.facing.y * REST);
        vel.set(p.vel.x, 0, p.vel.y);
    }

    /**
     * Lo mueve el control: hacia {@code dir} (de largo a lo sumo 1) a la velocidad del pie, además
     * de lo que se mueve el cuerpo; sin flechas vuelve despacio a descansar delante del cuerpo.
     */
    void drive(Player p, Vector2 dir, float dt) {
        active = true;
        if (dir.isZero()) {
            want.set(p.pos.x + p.facing.x * REST - pos.x, p.pos.y + p.facing.y * REST - pos.z).scl(6f).limit(2.5f);
        } else {
            want.set(dir).scl(SPEED);
        }
        want.add(p.vel);
        float dx = want.x - vel.x, dz = want.y - vel.z, d = (float) Math.sqrt(dx * dx + dz * dz), max = ACCEL * dt;
        if (d > max) {
            dx *= max / d;
            dz *= max / d;
        }
        vel.add(dx, 0, dz);
        pos.add(vel.x * dt, 0, vel.z * dt);
        pos.y = 2 * Ball.RADIUS;
        reach(p);
    }

    /** Se apoya sobre la pelota, donde está y con lo que se mueve. */
    void onBall(Ball b) {
        active = true;
        pos.set(b.pos.x, 2 * Ball.RADIUS, b.pos.z);
        vel.set(b.vel.x, 0, b.vel.z);
    }

    /** El amague: el pie hace un arco por arriba de la pelota, sin tocarla. */
    void startFeint() {
        feint = FEINT;
        feintSide = -feintSide;
    }

    /** Un cuadro del amague: el pie va de un costado de la pelota al otro, por arriba. */
    void feint(Player p, Vector3 ball, float dt) {
        active = true;
        feint -= dt;
        float k = 1 - Math.max(feint, 0) / FEINT, lateral = feintSide * MathUtils.cos(MathUtils.PI * k) * 0.3f;
        float rx = -p.facing.y, rz = p.facing.x;
        float x = ball.x + rx * lateral, z = ball.z + rz * lateral, y = Ball.RADIUS + MathUtils.sin(MathUtils.PI * k) * 0.25f;
        vel.set((x - pos.x) / dt, (y - pos.y) / dt, (z - pos.z) / dt);
        pos.set(x, y, z);
    }

    /** Cuánto adelante del centro del cuerpo está (negativo, atrás). */
    float forward(Player p) {
        return (pos.x - p.pos.x) * p.facing.x + (pos.z - p.pos.y) * p.facing.y;
    }

    /** Cuánto a la derecha del centro del cuerpo está (negativo, a la izquierda). */
    float side(Player p) {
        return (pos.x - p.pos.x) * -p.facing.y + (pos.z - p.pos.y) * p.facing.x;
    }

    /** Que no se salga de lo que alcanza la pierna; lo que se pasa, lo pierde de velocidad. */
    private void reach(Player p) {
        float f = forward(p), s = side(p);
        float cf = MathUtils.clamp(f, -BACK, FRONT), cs = MathUtils.clamp(s, -SIDE, SIDE);
        float r = (float) Math.sqrt(cf * cf + cs * cs);
        if (r > FRONT) {
            cf *= FRONT / r;
            cs *= FRONT / r;
        }
        if (cf == f && cs == s) return;
        float fx = p.facing.x, fz = p.facing.y;
        pos.x = p.pos.x + fx * cf - fz * cs;
        pos.z = p.pos.y + fz * cf + fx * cs;
        // lo que empujaba hacia afuera del alcance se pierde: queda la velocidad del cuerpo y lo de adentro
        float rvx = vel.x - p.vel.x, rvz = vel.z - p.vel.y;
        float vf = rvx * fx + rvz * fz, vs = rvx * -fz + rvz * fx;
        if (cf != f) vf = 0;
        if (cs != s) vs = 0;
        vel.x = p.vel.x + fx * vf - fz * vs;
        vel.z = p.vel.y + fz * vf + fx * vs;
    }
}
