package com.fpetrola.proto.soccer;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

/**
 * Los arqueros los maneja siempre la máquina, como en el Sensible: se paran entre la pelota y el
 * arco, leen los tiros, se estiran y atajan o dan rebote, y salen a buscar la que queda suelta en
 * el área. Con la pelota en las manos la saca la máquina, salvo el del equipo humano, que la deja
 * sacar al humano.
 */
final class Keeper {
    /** De la cadera a la punta de los dedos, estirado. */
    static final float REACH = 1.6f;

    private final Match m;
    private final Ball ghost = new Ball();
    private final Vector2 tmp = new Vector2();
    private final Vector3 body = new Vector3(), hands = new Vector3();
    /** Por dónde, a qué altura y cuándo cruza la pelota la línea del arquero. */
    private float hitZ, hitY, hitT;

    Keeper(Match m) {
        this.m = m;
    }

    void update(Player k, float dt) {
        if (m.owner == k) {
            // La máquina: la tiene un momento y la saca.
            k.steer(Vector2.Zero);
            if (k.think <= 0) distribute(k);
            return;
        }
        if (m.holder == k) {
            hold(k, dt);
            return;
        }
        if (k.busy()) return;
        if (m.penaltyKick && m.ball.lastTouch != null && m.ball.lastTouch.team != k.team) {
            m.penaltyKick = false;
            guess(k);
            return;
        }
        if (shotComing(k)) {
            if (!k.alert) {
                // Lo ve venir, tarda un poco en reaccionar y no lo lee perfecto: cuanto más fuerte, peor.
                // El de la máquina, según la dificultad.
                Difficulty d = k.team.human ? Difficulty.NORMAL : m.difficulty;
                k.alert = true;
                k.think = 0.05f + MathUtils.random(1.6f * d.keeperReaction);
                k.misread = MathUtils.random(-1f, 1f) * m.ball.vel.len() * 0.015f * d.keeperMisread;
            }
            k.facing.set(k.team.side, 0);
            if (k.think <= 0) react(k);
            else k.steer(Vector2.Zero);
            return;
        }
        k.alert = false;
        if (rushOut(k)) return;
        position(k);
    }

    /** Si la pelota va al arco o cerca: por dónde y cuándo cruza la línea del arquero. */
    private boolean shotComing(Player k) {
        Ball b = m.ball;
        int side = k.team.side;
        if (m.holder != null || m.owner != null || b.vel.x * -side < 5f) return false;
        float lineX = k.team.ownGoalX();
        ghost.set(b);
        boolean crossed = false;
        float step = 1 / 60f;
        for (float t = step; t < 1.8f; t += step) {
            ghost.step(step);
            if (!crossed && (ghost.pos.x - k.pos.x) * side <= 0) {
                crossed = true;
                hitZ = ghost.pos.z;
                hitY = ghost.pos.y;
                hitT = t;
            }
            if ((ghost.pos.x - lineX) * side <= 0) {
                if (!crossed) {
                    hitZ = ghost.pos.z;
                    hitY = ghost.pos.y;
                    hitT = t;
                }
                return Math.abs(ghost.pos.z) < Match.GOAL_HALF + 0.6f && ghost.pos.y < Match.GOAL_H + 0.5f;
            }
        }
        return false;
    }

    /**
     * Si le llega con un paso al costado, da el paso. Si no, se va corriendo de costado hacia donde
     * viene y se tira recién cuando le va a llegar, para estar todavía en el aire cuando pase.
     */
    private void react(Player k) {
        float z = hitZ + k.misread, lateral = z - k.pos.y;
        if (Math.abs(lateral) < 0.8f && hitY < 2.2f) k.moveTo(k.pos.x, z, 5f);
        else if (hitT > 0.45f) k.moveTo(k.pos.x, MathUtils.clamp(z, -Match.GOAL_HALF, Match.GOAL_HALF), 4.5f);
        else dive(k, lateral, hitY, hitT);
    }

    private void dive(Player k, float lateral, float height, float time) {
        float side = Math.signum(lateral);
        float need = Math.max(0, Math.abs(lateral) - REACH);
        float speed = MathUtils.clamp(need / Math.max(time - 0.1f, 0.15f), 1.5f, 3.8f);
        k.facing.set(k.team.side, 0);
        k.start(Player.Act.DIVE);
        k.launch(0, side * speed);
        k.actSide = side * k.team.side;
        m.physics.diveLetsThrough(k, false);
        k.actHeight = MathUtils.clamp(height, 0.2f, 2.3f);
    }

    /** Penal: se tira apenas patean, adivinando el lado; a veces se queda en el medio. */
    private void guess(Player k) {
        if (!shotComing(k)) return;
        float real = hitZ - k.pos.y;
        if (Math.abs(real) < 0.6f || MathUtils.randomBoolean(0.15f)) return;
        boolean right = MathUtils.randomBoolean(0.55f);
        float side = right ? Math.signum(real) : -Math.signum(real);
        dive(k, side * Math.max(Math.abs(real), 2.4f), right ? hitY : MathUtils.random(0.3f, 1.8f), Math.max(hitT, 0.4f));
    }

    /** Pelota suelta en el área que el arquero alcanza antes que los rivales: sale a buscarla. */
    private boolean rushOut(Player k) {
        Ball b = m.ball;
        if (m.owner != null || b.pos.y > 2f || !m.inBox(k.team, b.pos.x, b.pos.z)) return false;
        float mine = b.dist2D(k.pos);
        for (Player o : m.other(k.team).players) if (b.dist2D(o.pos) < mine - 0.5f) return false;
        m.intercept(k, 7f, tmp);
        k.moveTo(tmp.x, tmp.y, 7f);
        k.facing.set(b.pos.x - k.pos.x, b.pos.z - k.pos.y).nor();
        return true;
    }

    /**
     * Entre la pelota y el centro del arco, un poco adelante de la línea. Si un rival entra al área
     * con la pelota, le sale a achicar.
     */
    private void position(Player k) {
        Ball b = m.ball;
        Team t = k.team;
        float lineX = t.ownGoalX();
        tmp.set(b.pos.x - lineX, b.pos.z);
        float dist = tmp.len();
        boolean press = m.owner != null && m.owner.team != t && m.inBox(t, b.pos.x, b.pos.z);
        float out = press ? MathUtils.clamp(dist - 1.2f, 0.8f, 7f) : MathUtils.clamp(dist * 0.07f, 0.8f, 3.5f);
        tmp.nor().scl(out);
        float x = lineX + tmp.x, z = press ? tmp.y : MathUtils.clamp(tmp.y, -Match.GOAL_HALF, Match.GOAL_HALF);
        if ((x - lineX) * t.side < 0.5f) x = lineX + t.side * 0.5f;
        k.moveTo(x, z, press ? 6f : 5f);
        if (dist > 0.01f) k.facing.set(b.pos.x - k.pos.x, b.pos.z - k.pos.y).nor();
    }

    private void hold(Player k, float dt) {
        k.stop();
        k.facing.set(k.team.side, 0);
        m.holdTime += dt;
        if (m.holdTime > 1.4f) distribute(k);
    }

    /** La saca: con la mano a un compañero libre cerca, o de volea bien larga. */
    void distribute(Player k) {
        // como cualquier jugador: se la pasa a un compañero libre; en la práctica, sin compañeros, se la devuelve al humano
        Player mate = m.freeMate(k, 40f);
        if (mate == null && m.practice) mate = m.nearestTo(m.other(k.team), k);
        if (mate != null) {
            m.passTo(k, mate);
        } else {
            tmp.set(k.team.side, MathUtils.random(-0.35f, 0.35f)).nor();
            m.kick(k, tmp, 21f, 10f, 0);
        }
        k.cooldown = 1f;
    }

    /**
     * Si la pelota le llega a las manos. Parado alcanza un metro alrededor y hasta el travesaño;
     * estirado, todo el cuerpo hasta la punta de los dedos, y decide un poco antes de que la pelota
     * llegue. Cuanto más fuerte viene y más en la punta de los dedos, menos chances tiene. Si la
     * agarra cerca del cuerpo, se la queda; si no, la toca: parado la desvía él, estirado la desvía
     * el choque con su cuerpo. Si no llega, estirado le pasa por los dedos.
     */
    void contact(Player k) {
        Ball b = m.ball;
        if (m.holder != null || m.setPiece != null || k.cooldown > 0) return;
        if (m.owner != null && m.owner.team == k.team) return;
        if (b.lastTouch == k && b.speed2D() > 3f) return;
        boolean diving = k.act == Player.Act.DIVE;
        float ext;
        if (diving) {
            if (k.actTime > k.act.reach || k.actTouched) return;
            body.set(k.pos.x, k.diveHipY(), k.pos.y);
            k.diveHands(hands);
            float t = closest(b.pos, body, hands);
            float d = Vector3.dst(b.pos.x, b.pos.y, b.pos.z,
                    body.x + (hands.x - body.x) * t, body.y + (hands.y - body.y) * t, body.z + (hands.z - body.z) * t);
            if (d > 0.6f) return;
            k.actTouched = true;
            ext = t;
        } else if (!k.busy()) {
            float d = b.dist2D(k.pos);
            if (d > 1f || b.pos.y > Match.GOAL_H) return;
            ext = d;
        } else {
            return;
        }
        float speed = b.vel.len();
        if (!diving && speed < 12f && m.owner == null) {
            // una pelota floja la agarra siempre, como la pararía cualquier jugador
            m.catchBall(k);
            return;
        }
        float chance = 0.98f - Math.max(0, speed - 12f) * 0.015f - ext * 0.25f;
        if (!k.team.human) chance = Math.min(chance * m.difficulty.keeperSaves, 0.99f);
        if (m.owner != null) chance = 0.5f;  // a los pies de un rival
        if (MathUtils.random() > chance) {
            if (diving) m.physics.diveLetsThrough(k, true);
            k.cooldown = 0.4f;
            return;
        }
        if (ext < 0.35f && speed < 26f) m.catchBall(k);
        else if (!diving) m.parry(k);
    }

    /** Dónde cae, de 0 (en a) a 1 (en b), el punto del segmento más cerca de p. */
    private static float closest(Vector3 p, Vector3 a, Vector3 b) {
        float ex = b.x - a.x, ey = b.y - a.y, ez = b.z - a.z;
        float len2 = ex * ex + ey * ey + ez * ez;
        if (len2 == 0) return 0;
        return MathUtils.clamp(((p.x - a.x) * ex + (p.y - a.y) * ey + (p.z - a.z) * ez) / len2, 0, 1);
    }
}
