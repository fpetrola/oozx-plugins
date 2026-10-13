package com.fpetrola.proto.soccer;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

/**
 * La pelota tal como la ve el partido: dónde está, cómo va y cómo gira, copiado de la física
 * después de cada paso. También tiene el modelo del aire (arrastre y efecto Magnus) y del pasto,
 * que usan la física de verdad y las copias que se adelantan para predecir dónde va a estar.
 */
final class Ball {
    static final float RADIUS = 0.11f, MASS = 0.43f, GRAVITY = 9.8f;
    /** Aire: arrastre F = -DRAG |v| v y efecto F = MAGNUS (ω × v), en newtons. */
    static final float DRAG = 0.0057f, MAGNUS = 0.0034f;
    /** Pasto: rodando frena ROLL_C + ROLL_K·v m/s². */
    static final float ROLL_C = 2f, ROLL_K = 0.4f;
    /** El giro se va apagando así por segundo. */
    static final float SPIN_DAMPING = 0.05f;
    /**
     * De un pique sale con esta fracción de la velocidad vertical (el pasto se come el resto), y
     * con esta de la horizontal: el roce del pique la frena.
     */
    static final float BOUNCE = 0.45f, BOUNCE_ROLL = 0.85f;
    /** Rodando, el giro sobre el eje vertical la desvía esta fracción de lo que la desviaría en el aire. */
    static final float SWERVE = 0.25f;

    final Vector3 pos = new Vector3(0, RADIUS, 0);
    final Vector3 vel = new Vector3();
    /** Velocidad angular (rad/s): el eje vertical la curva, el horizontal la levanta o la baja. */
    final Vector3 spin = new Vector3();
    /** Cómo está girada, para dibujarla rodando. */
    final Quaternion rot = new Quaternion();
    Player lastTouch;
    /** Hacia dónde salió la última patada. */
    final Vector2 kickDir = new Vector2(1, 0);

    private final Vector3 force = new Vector3();

    /** Copia el estado de otra, para adelantarse a ver dónde va a estar. */
    Ball set(Ball other) {
        pos.set(other.pos);
        vel.set(other.vel);
        spin.set(other.spin);
        return this;
    }

    boolean onGround() {
        return pos.y <= RADIUS + 0.02f && Math.abs(vel.y) < 0.6f;
    }

    /** La fuerza del aire sobre una pelota que va a {@code v} girando a {@code w}: la frena y la curva. */
    static Vector3 airForce(Vector3 v, Vector3 w, Vector3 out) {
        float drag = -DRAG * v.len();
        return out.set(drag * v.x + MAGNUS * (w.y * v.z - w.z * v.y),
                drag * v.y + MAGNUS * (w.z * v.x - w.x * v.z),
                drag * v.z + MAGNUS * (w.x * v.y - w.y * v.x));
    }

    /**
     * Un paso de una copia que predice; a la de verdad la mueve Bullet. El aire es el mismo; los
     * piques y el pasto, aproximados.
     */
    void step(float h) {
        if (onGround()) {
            float s = speed2D();
            if (s > 0) {
                float slower = Math.max(0, s - (ROLL_C + ROLL_K * s) * h) / s;
                vel.x *= slower;
                vel.z *= slower;
            }
            vel.y = 0;
            swerve(vel, spin, h);
        } else {
            airForce(vel, spin, force);
            vel.mulAdd(force, h / MASS);
            vel.y -= GRAVITY * h;
        }
        spin.scl(1 - SPIN_DAMPING * h);
        pos.mulAdd(vel, h);
        if (pos.y < RADIUS) {
            pos.y = RADIUS;
            if (vel.y < -1.5f) {
                vel.y = -vel.y * BOUNCE;
                vel.x *= BOUNCE_ROLL;
                vel.z *= BOUNCE_ROLL;
                spin.scl(0.6f);
            } else {
                vel.y = 0;
            }
        }
    }

    /** El desvío de una pelota que rueda girando sobre su eje vertical: de costado, sin cambiarle la rapidez. */
    static void swerve(Vector3 v, Vector3 w, float h) {
        float a = SWERVE * MAGNUS / MASS * h;
        float dx = w.y * v.z * a, dz = -w.y * v.x * a;
        v.x += dx;
        v.z += dz;
    }

    float speed2D() {
        return (float) Math.sqrt(vel.x * vel.x + vel.z * vel.z);
    }

    float dist2D(Vector2 p) {
        return Vector2.len(pos.x - p.x, pos.z - p.y);
    }

    /** Lo que recorre rodando mientras baja de v0 a v1. */
    static float rollDistance(float v0, float v1) {
        float log = (float) Math.log((ROLL_C + ROLL_K * v0) / (ROLL_C + ROLL_K * v1));
        return ((v0 - v1) - ROLL_C / ROLL_K * log) / ROLL_K;
    }

    /** Cuánto tarda rodando en bajar de {@code v0} a {@code v1}. */
    static float rollTime(float v0, float v1) {
        if (v0 <= v1) return 0;
        return (float) Math.log((ROLL_C + ROLL_K * v0) / (ROLL_C + ROLL_K * v1)) / ROLL_K;
    }

    /** Con qué velocidad hay que sacarla rodando para que a d metros todavía vaya a {@code arrive}. */
    static float speedToReach(float d, float arrive) {
        float lo = arrive, hi = 60f;
        for (int i = 0; i < 30; i++) {
            float mid = (lo + hi) / 2;
            if (rollDistance(mid, arrive) < d) lo = mid;
            else hi = mid;
        }
        return hi;
    }
}
