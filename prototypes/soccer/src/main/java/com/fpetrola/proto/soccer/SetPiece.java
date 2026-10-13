package com.fpetrola.proto.soccer;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

import java.util.ArrayList;
import java.util.List;

/**
 * Una pelota parada: qué es, quién la saca, dónde se para cada uno y hacia dónde se puede sacar.
 * Como en el Sensible, se saca igual que se pasa o se patea en juego; lo único distinto es que no
 * deja apuntar hacia donde no tiene sentido.
 */
final class SetPiece {
    enum Kind {
        KICKOFF("Saque del medio"), THROW_IN("Lateral"), GOAL_KICK("Saque de arco"),
        CORNER("Córner"), FREE_KICK("Tiro libre"), PENALTY("Penal");

        final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    /** Los que van al área a buscarla (índice en el equipo): los dos delanteros, dos volantes y un central. */
    private static final int[] RUNNERS = {9, 10, 6, 7, 2};
    /** Dónde se paran en un córner, medido desde el arco: distancia a la línea y de qué lado (+ es el del córner). */
    private static final float[][] CORNER_SPOTS = {{5f, 2.5f}, {6.5f, -3f}, {11f, 0.5f}, {8.5f, -0.5f}, {13f, 4f}};

    final Kind kind;
    final Team team;
    /** Dónde está la pelota. */
    final Vector2 spot = new Vector2();
    Player taker;
    /** Hacia dónde apunta el que saca. */
    final Vector2 aim = new Vector2(1, 0);
    /** Los de la barrera de un tiro libre. */
    final List<Player> wall = new ArrayList<>();
    /** Para la máquina: cuánto falta para que saque. */
    float wait;

    private final Vector2 tmp = new Vector2();

    SetPiece(Kind kind, Team team, float x, float z, Player taker) {
        this.kind = kind;
        this.team = team;
        this.taker = taker;
        spot.set(x, z);
    }

    /** Pone la pelota, elige quién saca y le da a cada uno su lugar. */
    void setUp(Match m) {
        Team other = m.other(team);
        m.physics.placeBall(spot.x, Ball.RADIUS, spot.y);
        if (taker == null || taker.keeper != (kind == Kind.GOAL_KICK)) taker = chooseTaker();
        for (Player p : m.players) m.formationSpot(p, spot.x, spot.y);
        switch (kind) {
            case KICKOFF -> kickoff(other);
            case PENALTY -> penalty(m, other);
            case CORNER -> corner(other);
            case FREE_KICK -> freeKick(other);
            case GOAL_KICK -> clearBox(m, other);
            case THROW_IN -> { }
        }
        if (kind != Kind.KICKOFF && kind != Kind.PENALTY) keepAway(other, kind == Kind.THROW_IN ? 3f : 9.15f);
        defaultAim(other);
        if (kind == Kind.KICKOFF || kind == Kind.PENALTY) {
            for (Player p : m.players) {
                p.act = Player.Act.NONE;
                m.physics.place(p, p.spot.x, p.spot.y);
            }
        }
        placeTaker(m);
        wait = 1.3f + MathUtils.random(0.7f);
    }

    private Player chooseTaker() {
        if (kind == Kind.GOAL_KICK) return team.players.get(0);
        if (kind == Kind.KICKOFF) return team.players.get(10);
        Player best = null;
        float bestD = Float.MAX_VALUE;
        for (Player p : team.players) {
            if (p.keeper) continue;
            float d = p.pos.dst(spot);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    /** Cada uno en su mitad, y los que no sacan fuera del círculo central. */
    private void kickoff(Team other) {
        for (Player p : team.players) p.kickoffSpot();
        for (Player p : other.players) {
            p.kickoffSpot();
            float d = p.spot.len();
            if (d < 9.6f) {
                if (d < 0.1f) p.spot.set(-other.side, 0);
                p.spot.setLength(9.6f);
            }
        }
    }

    /** El arquero en la línea; todos los demás fuera del área, detrás de la pelota. */
    private void penalty(Match m, Team other) {
        float gx = other.ownGoalX(), edge = gx + other.side * (Match.BOX_DEPTH + 3f);
        int i = 0;
        for (Player p : m.players) {
            if (p == taker) continue;
            if (p.keeper) {
                if (p.team == other) p.spot.set(gx + other.side * 0.3f, 0);
                continue;
            }
            p.spot.set(edge + other.side * (i % 2) * 1.5f, (i / 2 - 4.5f) * 3.2f + (i % 2) * 1.4f);
            i++;
        }
    }

    /** Cinco van al área; a cada uno lo marca uno, un paso más cerca del arco, y otro cuida el primer palo. */
    private void corner(Team other) {
        float gx = other.ownGoalX(), sx = Math.signum(gx), sz = Math.signum(spot.y);
        int d = 1;
        for (int n = 0; n < RUNNERS.length; n++) {
            Player a = team.players.get(RUNNERS[n]);
            if (a == taker) continue;
            a.spot.set(gx - sx * CORNER_SPOTS[n][0], sz * CORNER_SPOTS[n][1]);
            other.players.get(d++).spot.set(a.spot.x + sx * 1f, a.spot.y - sz * 0.4f);
        }
        other.players.get(d).spot.set(gx - sx * 0.8f, sz * (Match.GOAL_HALF - 0.4f));
        other.players.get(0).spot.set(gx - sx * 0.6f, sz * 0.8f);
    }

    /** Cerca del arco y de frente, barrera a 9,15 m; y unos cuantos de los que patean se arriman al área. */
    private void freeKick(Team other) {
        float gx = other.ownGoalX();
        tmp.set(gx - spot.x, -spot.y);
        float dist = tmp.len();
        if (dist > 32f || Math.abs(tmp.x) / dist < 0.42f) return;
        int size = dist < 22f ? 4 : 3;
        tmp.nor();
        float cx = spot.x + tmp.x * 9.15f, cz = spot.y + tmp.y * 9.15f;
        for (int n = 0; n < size; n++) {
            Player p = nearestFree(other, cx, cz);
            float off = (n - (size - 1) / 2f) * 0.62f;
            p.spot.set(cx - tmp.y * off, cz + tmp.x * off);
            wall.add(p);
        }
        int n = 0;
        for (int idx : RUNNERS) {
            Player p = team.players.get(idx);
            if (p == taker || n++ >= 3) continue;
            p.spot.set(gx - Math.signum(gx) * MathUtils.random(7f, 13f), MathUtils.random(-9f, 9f));
        }
    }

    private Player nearestFree(Team t, float x, float z) {
        Player best = null;
        float bestD = Float.MAX_VALUE;
        for (Player p : t.players) {
            if (p.keeper || wall.contains(p)) continue;
            float d = Vector2.dst(p.spot.x, p.spot.y, x, z);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    /** En el saque de arco los rivales salen del área. */
    private void clearBox(Match m, Team other) {
        float edge = team.ownGoalX() + team.side * (Match.BOX_DEPTH + 1.5f);
        for (Player p : other.players)
            if (!p.keeper && m.inBox(team, p.spot.x, p.spot.y)) p.spot.x = edge;
    }

    /** Los rivales, a {@code r} de la pelota como mínimo. */
    private void keepAway(Team other, float r) {
        for (Player p : other.players) {
            if (p.keeper || wall.contains(p)) continue;
            tmp.set(p.spot).sub(spot);
            if (tmp.len() >= r) continue;
            if (tmp.isZero(0.1f)) tmp.set(-other.side, 0);
            p.spot.set(spot).add(tmp.setLength(r + 0.3f));
        }
    }

    private void defaultAim(Team other) {
        float gx = other.ownGoalX();
        switch (kind) {
            case THROW_IN -> aim.set(team.side * 0.5f, -Math.signum(spot.y));
            case GOAL_KICK -> aim.set(team.side, -Math.signum(spot.y) * 0.3f);
            case CORNER -> aim.set(gx - Math.signum(gx) * Match.PENALTY_SPOT - spot.x, -spot.y);
            case PENALTY -> aim.set(gx - spot.x, -spot.y);
            case FREE_KICK -> {
                if (Vector2.dst(spot.x, spot.y, gx, 0) < 32f) aim.set(gx - spot.x, -spot.y);
                else aim.set(team.side, 0);
            }
            case KICKOFF -> aim.set(team.players.get(9).spot).sub(spot);
        }
        aim.nor();
        clampAim(aim);
    }

    /** El que saca, detrás de la pelota mirando hacia donde apunta; en el lateral, del otro lado de la línea. */
    private void placeTaker(Match m) {
        Player p = taker;
        p.act = Player.Act.NONE;
        if (kind == Kind.THROW_IN) p.spot.set(spot.x, Math.signum(spot.y) * (Match.HALF_W + 0.3f));
        else p.spot.set(spot).mulAdd(aim, -0.55f);
        m.physics.place(p, p.spot.x, p.spot.y);
        p.facing.set(aim);
    }

    /** No deja apuntar afuera ni para atrás donde no tiene sentido. */
    void clampAim(Vector2 a) {
        switch (kind) {
            case THROW_IN -> limit(a, tmp.set(0, -Math.signum(spot.y)), 80f);
            case CORNER -> limit(a, tmp.set(-Math.signum(spot.x), -Math.signum(spot.y)).nor(), 45f);
            case GOAL_KICK -> limit(a, tmp.set(team.side, 0), 80f);
            case PENALTY -> limit(a, tmp.set(Math.signum(spot.x) * Match.HALF_L - spot.x, -spot.y).nor(), 30f);
            default -> { }
        }
    }

    private static void limit(Vector2 a, Vector2 center, float max) {
        float angle = MathUtils.atan2(center.crs(a), center.dot(a)) * MathUtils.radDeg;
        if (Math.abs(angle) > max) a.set(center).rotateDeg(Math.signum(angle) * max);
    }

    /** La máquina saca: penal a un palo, tiro libre cerca con comba, centro al área, saque largo, o un pase corto. */
    void aiTake(Match m) {
        Player p = taker;
        float gx = m.other(team).ownGoalX();
        switch (kind) {
            case PENALTY -> {
                float z = MathUtils.randomBoolean(0.15f) ? 0 : MathUtils.randomSign() * (Match.GOAL_HALF - MathUtils.random(0.4f, 1.1f));
                aim.set(gx - spot.x, z - spot.y).nor();
                m.kick(p, aim, MathUtils.random(21f, 27f), MathUtils.random(0.3f, 3f), 0);
            }
            case FREE_KICK -> {
                if (Vector2.dst(spot.x, spot.y, gx, 0) < 30f && MathUtils.randomBoolean(0.7f)) {
                    // Por arriba de la barrera, a un palo, con comba: apunta afuera y que el giro la meta.
                    float curl = MathUtils.randomSign() * 45f, z = MathUtils.randomSign() * (Match.GOAL_HALF - 0.8f);
                    m.aimCurled(spot, gx, z, 24f, 7f, curl, aim);
                    m.kick(p, aim, 24f, 7f, curl);
                } else {
                    pass(m, p);
                }
            }
            case CORNER -> {
                // Un centro por arriba a alguno de los que fueron al área, con un poco de comba.
                Player target = team.players.get(RUNNERS[MathUtils.random(RUNNERS.length - 1)]);
                if (target == p) target = team.players.get(9);
                aim.set(target.pos).sub(spot);
                float d = aim.len();
                aim.nor();
                m.kick(p, aim, 19f, m.lob(d, 19f, Ball.RADIUS), MathUtils.random(-20f, 20f));
            }
            case GOAL_KICK -> {
                Player mate = m.bestForward(p);
                if (mate == null) {
                    m.kick(p, aim, 24f, 11f, 0);
                } else {
                    aim.set(mate.pos).sub(spot);
                    float d = aim.len(), vh = Math.min(24f, d / 1.3f);
                    aim.nor();
                    m.kick(p, aim, vh, m.lob(d, vh, Ball.RADIUS), 0);
                }
            }
            default -> pass(m, p);
        }
    }

    private void pass(Match m, Player p) {
        Player mate = m.nearestMate(p, aim, kind == Kind.THROW_IN ? 80f : 180f);
        if (mate != null) m.passTo(p, mate);
        else m.kick(p, aim, 12f, 2f, 0);
    }
}
