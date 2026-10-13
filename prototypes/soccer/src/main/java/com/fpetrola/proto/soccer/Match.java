package com.fpetrola.proto.soccer;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;

import java.util.ArrayList;
import java.util.List;

/**
 * El partido: reglas y pelotas paradas, y la máquina que maneja a los jugadores de campo (los
 * arqueros tienen la suya, {@link Keeper}). Lo físico (la pelota, los choques, los arcos) lo hace
 * {@link Physics}; acá se decide qué quiere hacer cada uno y qué significa lo que tocó. Las
 * acciones con la pelota (llevarla, pasarla, patearla) están acá para que las use tanto la máquina
 * como cualquier {@link ControlScheme}. Como en el Sensible, no hay offside.
 */
final class Match implements Physics.Contacts, Disposable {
    static final float HALF_L = 52.5f, HALF_W = 34f;
    static final float GOAL_HALF = 3.66f, GOAL_H = 2.44f;
    static final float BOX_DEPTH = 16.5f, BOX_HALF = 20.16f, PENALTY_SPOT = 11f;
    /** Hasta dónde llega para agarrarla, y hasta dónde se estira para patearla. */
    static final float REACH = 0.9f, KICK_REACH = 1.4f;
    /** Más lejos que esto de la pelota, el jugador del humano ya no está en la jugada: el control cambia solo. */
    static final float FAR = 30f;
    /** La suela sobre la pelota: un resorte (por segundo) con una aceleración máxima; si la pelota se va más lejos que esto del pie, se suelta. */
    static final float SOLE_GAIN = 25f, SOLE_ACCEL = 220f, SOLE_LOST = 1.2f;
    /** Con qué velocidad le llega un pase por abajo al compañero: firme, para que no lo corte cualquiera. */
    static final float PASS_ARRIVE = 9f;
    /** Al soltar la suela, la pelota se queda con esta parte de lo que iba más rápido que el cuerpo. */
    static final float SOLE_KEEP = 0.5f;

    /** Con qué parte del cuerpo se le llega a la pelota: el pie a ras del piso o en el aire, el muslo, la cabeza. */
    enum Part { GROUND, FOOT, THIGH, HEAD }
    /** El partido piensa a este paso; la física da dos por cada uno. */
    static final float TICK = 1 / 120f;
    /** Z sin la pelota al pie, cerca de la jugada: mantenido esto, contener. */
    static final float CONTAIN_AFTER = 0.2f;
    /** Más cerca que esto de la pelota, Z nunca cambia de jugador: es para pisarla o contener. */
    static final float Z_NEAR = 5f;
    /** Más cerca que esto del que lleva la pelota, el defensor de la máquina deja de ir a cortarla y mira el pie. */
    static final float READ_FROM = 5f;

    final Team home = new Team("Local", new Color(0.85f, 0.1f, 0.1f, 1), Color.WHITE, 1, true);
    final Team away = new Team("Visita", new Color(0.15f, 0.35f, 0.9f, 1), new Color(0.1f, 0.1f, 0.2f, 1), -1, false);
    final List<Player> players = new ArrayList<>();
    final Ball ball = new Ball();
    final Trajectory trajectory = new Trajectory();
    final Keeper keepers = new Keeper(this);
    final Physics physics;
    /** El control del humano. {@link SensibleScheme} (con suela, y su variante con botón de elevación) quedó guardado, sin usar. */
    ControlScheme scheme = new GolpeScheme();
    /** Apagada, los de campo que no maneja el humano se quedan quietos; los arqueros siguen atajando. */
    boolean aiEnabled = true;
    Difficulty difficulty = Difficulty.NORMAL;

    Player controlled;
    /** A quién pasaría el control si el humano aprieta su botón; null si no se puede cambiar. */
    Player switchTo;
    /** Los ruidos de la pelota: al patearla y al chocar contra un palo, una pared o un jugador. */
    enum Noise { KICK, POST, WALL, BODY }

    /** Quien escucha los ruidos (la ventana los hace sonar); null sin ventana. */
    interface Ears {
        /** Un ruido, con qué fuerza: la velocidad de la patada o lo que cambió la de la pelota al chocar (m/s). */
        void hear(Noise noise, float strength);
    }

    Ears ears;
    /** La arena de práctica: sin rivales de campo (sólo el arquero de enfrente), con paredes que no dejan salir la pelota. */
    boolean practice;
    /** El humano mantiene Z sin la pelota cerca: el que maneja contiene. */
    boolean containing;
    /** Z apretado sin la pelota cerca, y desde hace cuánto: al soltarlo enseguida, cambia de jugador. */
    private boolean zFree;
    private float zTime;
    /** El que lleva la pelota pegada al pie; deja de serlo al patearla o al perderla. */
    Player owner;
    /** A quién va el último pase, para que vaya a buscarlo. */
    Player passTarget;
    /** El arquero que la tiene en las manos. */
    Player holder;
    /** La pelota parada que se está por sacar; null mientras se juega. */
    SetPiece setPiece;
    /** Recién se pateó un penal: el arquero tiene que adivinar. */
    boolean penaltyKick;
    float clock, holdTime;
    String message = "";
    float messageTime;

    /** Después del silbato la pelota está muerta un rato, hasta acomodar la pelota parada que sigue. */
    private float dead;
    private SetPiece next;
    /**
     * El equipo que acaba de patear o pasar: mientras la pelota va fuerte, pasa de largo por sus
     * jugadores (salvo por el que recibe el pase), como en el Sensible.
     */
    private Team shotBy;
    private float shotTime, accumulator;
    private final Ball ghost = new Ball();
    private final Vector2 tmp = new Vector2(), tmp2 = new Vector2(), zero = new Vector2();
    private final Vector3 v3 = new Vector3(), w3 = new Vector3();

    Match() {
        players.addAll(home.players);
        players.addAll(away.players);
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            p.index = i;
            p.kickoffSpot();
            p.pos.set(p.spot);
        }
        physics = new Physics(players, ball);
        controlled = home.players.get(10);
        startSetPiece(new SetPiece(SetPiece.Kind.KICKOFF, home, 0, 0, null));
        say(setPiece.kind.label);
    }

    Team other(Team t) {
        return t == home ? away : home;
    }

    /** Lo que pasó en un cuadro: el partido piensa a paso fijo, tantas veces como entren. */
    void update(Pad pad, float frameDt) {
        accumulator = Math.min(accumulator + frameDt, 0.1f);
        while (accumulator >= TICK) {
            accumulator -= TICK;
            tick(pad);
            pad.consume();
        }
    }

    private void tick(Pad pad) {
        float dt = TICK;
        if (messageTime > 0) messageTime -= dt;
        if (dead > 0) {
            dead -= dt;
            for (Player p : players) {
                p.steer(zero);
                p.update(dt);
            }
            physics.step(this);
            physics.step(this);
            if (dead <= 0) {
                if (next != null) startSetPiece(next);
                else restartPractice();
            }
            return;
        }
        if (setPiece == null) clock += dt;
        shotTime -= dt;
        if (owner != null && (ball.dist2D(owner.pos) > 3f || ball.pos.y > 1.5f || owner.busy())) setOwner(null);
        if (shotBy != null && (ball.speed2D() < 8f || shotTime <= 0 && ball.speed2D() < 15f)) shotBy = null;
        trajectory.predict(this);
        for (Player p : players) {
            p.facingHeld = false;
            p.foot.active = false;
            p.containing = false;
        }
        chooseControlled(pad);

        Player homeChaser = chaser(home), awayChaser = chaser(away), awayPresser = presser(away, awayChaser);
        for (Player p : players) {
            if (p.benched) p.steer(zero);
            else if (setPiece != null && p != setPiece.taker) setPieceMove(p);
            else if (p == controlled) scheme.update(this, p, pad, dt);
            else if (setPiece != null) takerWaits(p, dt);
            else if (p.keeper) keepers.update(p, dt);
            else if (!aiEnabled) p.steer(zero);
            else if (p == homeChaser || p == awayChaser || p == awayPresser) chase(p, dt);
            else {
                position(p);
                // el de la máquina que no salió a buscarla igual la para si le pasa al alcance
                if (!p.team.human && canTouch(p)) control(p);
            }
        }
        if (holder != null && holder == controlled && (holdTime += dt) > 5f) keepers.distribute(holder);
        for (Player p : players) {
            p.update(dt);
            stance(p, dt);
        }
        holdOrCarry(dt);
        keepers.contact(home.players.get(0));
        keepers.contact(away.players.get(0));
        for (Player p : players)
            if (p.act == Player.Act.HEAD || p.act == Player.Act.DIVE_HEAD) headContact(p);
        // El que la lleva no choca con su pelota, ni el que recién la tocó (un taco pasa por al lado de su
        // cuerpo), ni el que la tiró para arriba para ir a buscarla (levantarla, el jueguito).
        for (Player p : players)
            physics.ballIgnores(p, p == owner || shotBy == p.team && p != passTarget
                    || ball.lastTouch == p && (p.cooldown > 0 || p == passTarget && ball.pos.y > Ball.RADIUS + 0.1f));
        physics.step(this);
        physics.step(this);
        for (Player p : players)
            if (!p.foot.active && p.foot.feint <= 0) p.foot.rest(p);
        if (setPiece == null && dead <= 0) checkBounds();
    }

    /** La pelota clavada (una pelota parada, en las manos del arquero) o pegada al pie del que la lleva. */
    private void holdOrCarry(float dt) {
        if (setPiece != null) {
            if (setPiece.kind == SetPiece.Kind.THROW_IN) physics.pinBall(setPiece.taker.pos.x, 2.15f, Math.signum(setPiece.spot.y) * (HALF_W - 0.2f));  // adentro de la línea, si no al sacar ya está afuera
            else physics.pinBall(setPiece.spot.x, Ball.RADIUS, setPiece.spot.y);
        } else if (holder != null) {
            hands(holder, v3);
            physics.pinBall(v3.x, v3.y, v3.z);
        } else {
            physics.releaseBall();
            for (Player p : players) {
                if (!p.soleReleased) continue;
                p.soleReleased = false;
                if (p == owner) {
                    // el pie se levanta: la pelota sigue para donde iba, pero no con toda la velocidad del pie
                    v3.set(p.vel.x + (ball.vel.x - p.vel.x) * SOLE_KEEP, ball.vel.y, p.vel.y + (ball.vel.z - p.vel.y) * SOLE_KEEP);
                    physics.moveBall(v3);
                }
            }
            if (owner == null) return;
            if (owner.sole == Player.Sole.ON) sole(owner, dt);
            else if (owner.loose <= 0) keepClose(owner, dt);
        }
    }

    /**
     * La suela sobre la pelota: la pelota va hacia el pie con un resorte. Es un resorte, y no la
     * pelota clavada al pie, para que un rival que la toca pueda sacarla; si se va lejos del pie, se
     * soltó.
     */
    private void sole(Player p, float dt) {
        Foot f = p.foot;
        if (Vector2.dst(f.pos.x, f.pos.z, ball.pos.x, ball.pos.z) > SOLE_LOST) {
            p.releaseSole();
            setOwner(null);
            return;
        }
        float dx = f.vel.x + (f.pos.x - ball.pos.x) * SOLE_GAIN - ball.vel.x;
        float dz = f.vel.z + (f.pos.z - ball.pos.z) * SOLE_GAIN - ball.vel.z;
        float d = (float) Math.sqrt(dx * dx + dz * dz), max = SOLE_ACCEL * dt;
        if (d > max) {
            dx *= max / d;
            dz *= max / d;
        }
        physics.moveBall(v3.set(ball.vel.x + dx, ball.vel.y, ball.vel.z + dz));
    }

    /** Dónde tiene la pelota el arquero: en las manos estiradas si se tiró, si no contra el pecho. */
    private void hands(Player k, Vector3 out) {
        if (k.act == Player.Act.DIVE) k.diveHands(out);
        else out.set(k.pos.x + k.facing.x * 0.45f, 1.1f, k.pos.y + k.facing.y * 0.45f);
    }

    private void setOwner(Player p) {
        if (owner != null && owner != p && owner.sole == Player.Sole.ON) owner.releaseSole();
        owner = p;
    }

    // ---- quién maneja el humano -------------------------------------------------------------

    /**
     * El que maneja el humano. Lo cambia el humano con su botón, al compañero que llega primero a
     * la pelota. Solo cambia si quedó muy lejos de la jugada o se cayó, en las pelotas paradas, con
     * la pelota en las manos del arquero, y al pasarla (va al que la recibe).
     */
    private void chooseControlled(Pad pad) {
        switchTo = null;
        containing = false;
        if (setPiece != null) {
            zFree = false;
            if (setPiece.team == home) controlled = setPiece.taker;
            return;
        }
        if (holder != null && holder.team == home) {
            zFree = false;
            controlled = holder;
            return;
        }
        Player keeper = home.players.get(0);
        if (owner == keeper) {
            zFree = false;
            controlled = keeper;
            return;
        }
        Player other = first(home, controlled);
        if (controlled.keeper || controlled.act == Player.Act.FALL) {
            zFree = false;
            if (other != null) controlled = other;
            return;
        }
        if (!scheme.usesZ() && pad.zPressed) {
            // el esquema no usa Z: siempre es cambiar al compañero que llega primero a la pelota
            pad.zPressed = false;
            if (owner != controlled && other != null) {
                controlled = other;
                switchTo = first(home, controlled);
                return;
            }
        }
        // Z sin la pelota al pie: cerca de la jugada nunca cambia de jugador (mantenido, contiene: así no se
        // pierde el que estaba haciendo algo con la pelota); lejos, cambia al que llega primero.
        if (pad.zPressed && controlled.sole == Player.Sole.OFF && !soleContext(controlled)) {
            pad.zPressed = false;
            if (ball.dist2D(controlled.pos) < Z_NEAR) {
                zFree = true;
                zTime = 0;
            } else if (other != null && owner != controlled) {
                controlled = other;
                switchTo = first(home, controlled);
                return;
            }
        }
        if (zFree) {
            if (pad.zDown) {
                zTime += TICK;
                containing = zTime >= CONTAIN_AFTER && owner != controlled;
            } else {
                zFree = false;
            }
        }
        if (owner == controlled || other == null || controlled.sole != Player.Sole.OFF) return;
        switchTo = other;
        // Pelota suelta (un pase, un rebote): el que llega primero a tomarla.
        if (owner == null && passTarget == null && !controlled.busy() && !containing
                && trajectory.reach(other, Player.RUN) < trajectory.reach(controlled, Player.RUN) - 0.3f) {
            controlled = other;
            switchTo = first(home, controlled);
            return;
        }
        float mine = ball.dist2D(controlled.pos);
        if (!controlled.busy() && mine > FAR && ball.dist2D(other.pos) < mine - 15f) controlled = other;
    }

    /** El de campo que llega primero a la pelota (sin contar a {@code except}); si ninguno llega, el más cerca de donde termina. */
    private Player first(Team team, Player except) {
        Player best = null;
        float bestT = Float.MAX_VALUE, bestD = Float.MAX_VALUE;
        for (Player p : team.players) {
            if (p == except || p.keeper || p.benched || p.act == Player.Act.FALL) continue;
            float t = trajectory.reach(p, Player.RUN);
            float d = Vector2.dst(trajectory.x[Trajectory.N - 1], trajectory.z[Trajectory.N - 1], p.pos.x, p.pos.y);
            if (t < bestT || t == bestT && d < bestD) {
                bestT = t;
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    // ---- la máquina ---------------------------------------------------------------------------

    /** El que va a buscar la pelota: el que la lleva, el que espera el pase o el que llega primero. En el equipo humano, nadie. */
    private Player chaser(Team team) {
        if (team.human || setPiece != null) return null;
        if (owner != null && owner.team == team) return owner;
        if (passTarget != null && passTarget.team == team && !passTarget.keeper) return passTarget;
        return first(team, null);
    }

    /** En las dificultades que aprietan con dos, el segundo que sale a buscar al que lleva la pelota. */
    private Player presser(Team team, Player chaser) {
        if (team.human || setPiece != null || difficulty.pressers < 2 || owner == null || owner.team == team) return null;
        return first(team, chaser);
    }

    /** Qué tan rápido corre: los de la máquina, según la dificultad. */
    private float run(Player p) {
        return p.team.human ? Player.RUN : Player.RUN * difficulty.speed;
    }

    /** Dónde la puede alcanzar corriendo a {@code speed}. */
    Vector2 intercept(Player p, float speed, Vector2 out) {
        return trajectory.meet(p, speed, out);
    }

    private void chase(Player p, float dt) {
        if (owner == p) {
            carry(p);
            return;
        }
        if (p.busy()) return;
        boolean fresh = clock - p.lastChase < 0.1f;
        p.lastChase = clock;
        if (p.react > 0) {
            // todavía no se dio cuenta de adónde va la pelota: sigue adonde iba, o en su lugar si recién empieza
            if (fresh) p.moveTo(p.chaseTo.x, p.chaseTo.y, run(p));
            else position(p);
            if (canTouch(p)) control(p);
            return;
        }
        if (owner != null && owner.team != p.team && ball.dist2D(p.pos) < READ_FROM) {
            // Cerca del que la lleva: va adonde cree que la va a llevar, según lo que lee del pie.
            read(p, dt);
            tmp.set(ball.pos.x + owner.vel.x * 0.25f + p.read.x, ball.pos.z + owner.vel.y * 0.25f + p.read.y);
        } else {
            p.read.setZero();
            p.commit = 0;
            p.tempted = false;
            intercept(p, run(p), tmp);
        }
        p.moveTo(tmp.x, tmp.y, run(p));
        p.chaseTo.set(tmp);
        if (canTouch(p)) {
            control(p);
        } else if (canHead(p) && ball.dist2D(p.pos) < 1.2f && ball.pos.y > 1.3f) {
            // De cabeza: al arco si está cerca, si no para adelante.
            p.start(Player.Act.HEAD);
            float gx = p.team.attackGoalX();
            if (Math.abs(gx - p.pos.x) < 20f) p.redirect.set(gx - p.pos.x, -p.pos.y).nor();
            else p.redirect.set(p.team.side, MathUtils.random(-0.4f, 0.4f)).nor();
        } else if (owner != null && owner.team != p.team && ball.dist2D(p.pos) < 2.6f
                && MathUtils.random() < difficulty.slides * dt) {
            // A veces se tira a barrer; si le pega al rival antes que a la pelota, es falta.
            tmp.set(ball.pos.x - p.pos.x, ball.pos.z - p.pos.y);
            p.startSlide(tmp);
            p.redirect.set(p.team.side, 0);
        }
    }

    /**
     * El defensor de la máquina mira el pie del que lleva la pelota: lo que ve le llega con retraso
     * (según la dificultad) y, si ve que la pelota va para un costado, a veces se juega a ese lado y
     * no vuelve a mirar por un rato, con las piernas abiertas un instante. Así lo engaña cualquier
     * amague, sin programar cada uno: el pie va para un lado, él se juega, la pelota va para el otro.
     */
    private void read(Player d, float dt) {
        Difficulty diff = difficulty;
        d.commit -= dt;
        if (d.commit > 0) return;
        lead(owner, tmp2);
        d.read.lerp(tmp2, Math.min(1, dt / diff.reaction));
        float side = d.read.x * -owner.facing.y + d.read.y * owner.facing.x;
        boolean wide = Math.abs(side) > 0.3f;
        if (wide && !d.tempted) {
            d.tempted = true;
            if (MathUtils.random() < diff.bite) {
                // se juega: va un metro para ese lado
                d.commit = diff.commit;
                d.committedSide = Math.signum(side);
                d.read.set(-owner.facing.y, owner.facing.x).scl(d.committedSide);
                d.opened = 0.15f;
            }
        } else if (!wide) {
            d.tempted = false;
        }
    }

    /** Hacia dónde lleva la pelota el pie de {@code o}: 0,6 s de lo que se mueve respecto del cuerpo, a lo sumo un metro. */
    Vector2 lead(Player o, Vector2 out) {
        if (!o.foot.active) return out.setZero();
        return out.set(o.foot.vel.x - o.vel.x, o.foot.vel.z - o.vel.y).scl(0.6f).limit(1f);
    }

    /**
     * Las piernas abiertas o cerradas: abiertas si contiene, si recién se jugó a un lado, o si está
     * plantado de frente a la pelota; cerradas corriendo. Entre las piernas abiertas pasa la pelota.
     */
    private void stance(Player p, float dt) {
        p.opened -= dt;
        boolean open = p.containing || p.opened > 0 || planted(p);
        p.stance = MathUtils.clamp(p.stance + (open ? 6f : -6f) * dt, 0, 1);
    }

    private boolean planted(Player p) {
        if (p == owner || p.busy() || p.vel.len2() > 1.5f) return false;
        float dx = ball.pos.x - p.pos.x, dz = ball.pos.z - p.pos.y, d = (float) Math.sqrt(dx * dx + dz * dz);
        return d < 8f && (d < 0.6f || dx * p.facing.x + dz * p.facing.y > 0.7f * d);
    }

    /** La máquina con la pelota: va hacia el arco esquivando y cada tanto decide si tira o la pasa. */
    private void carry(Player p) {
        Team t = p.team;
        float gx = t.attackGoalX();
        Player opp = nearestOpponent(p);
        float oppD = opp == null ? 99 : opp.pos.dst(p.pos);
        tmp.set(gx - p.pos.x, -p.pos.y * 0.3f).nor();
        if (oppD < 6) tmp.add(tmp2.set(p.pos).sub(opp.pos).nor().scl(0.8f)).nor();
        p.steer(tmp.scl(Player.RUN_WITH_BALL * difficulty.speed));

        if (p.think > 0) return;
        p.think = difficulty.think;
        if (Vector2.dst(p.pos.x, p.pos.y, gx, 0) < difficulty.shootFrom) {
            // Apunta a algún lugar del arco, y no siempre le emboca.
            float error = GOAL_HALF + difficulty.shotError;
            tmp.set(gx - p.pos.x, MathUtils.random(-error, error) - p.pos.y).nor();
            kick(p, tmp, difficulty.shotSpeed + MathUtils.random(5f), 1f + MathUtils.random(4f), MathUtils.random(-15f, 15f));
        } else if (oppD < 4f && MathUtils.randomBoolean(0.5f)) {
            Player mate = bestForward(p);
            if (mate != null) passTo(p, mate);
        }
    }

    Player bestForward(Player p) {
        Player best = null;
        float bestScore = -Float.MAX_VALUE;
        for (Player m : p.team.players) {
            if (m == p || m.keeper) continue;
            float d = m.pos.dst(p.pos);
            if (d < 6 || d > 38) continue;
            Player mark = nearestOpponent(m);
            float free = mark == null ? 8 : Math.min(mark.pos.dst(m.pos), 8);
            float score = p.team.toA(m.pos.x - p.pos.x) - 0.3f * d + free * 1.5f;
            if (score > bestScore) {
                bestScore = score;
                best = m;
            }
        }
        return best;
    }

    /** El compañero más cerca que esté, como mucho, a {@code cone} grados de {@code aim}. */
    Player nearestMate(Player p, Vector2 aim, float cone) {
        Player best = null;
        float bestD = Float.MAX_VALUE;
        for (Player m : p.team.players) {
            if (m == p || m.keeper) continue;
            tmp2.set(m.pos).sub(p.pos);
            float d = tmp2.len();
            if (d < 3 || d > 30 || Math.abs(MathUtils.atan2(aim.crs(tmp2), aim.dot(tmp2))) * MathUtils.radDeg > cone) continue;
            if (d < bestD) {
                bestD = d;
                best = m;
            }
        }
        return best;
    }

    /** Para que el arquero la saque con la mano: el compañero más cerca sin un rival encima. */
    Player freeMate(Player k, float maxDist) {
        Player best = null;
        float bestD = maxDist;
        for (Player m : k.team.players) {
            if (m == k) continue;
            float d = m.pos.dst(k.pos);
            Player mark = nearestOpponent(m);
            if (d < bestD && (mark == null || mark.pos.dst(m.pos) > 5f)) {
                bestD = d;
                best = m;
            }
        }
        return best;
    }

    /**
     * Su lugar en la formación. El que acaba de dar un pase sigue corriendo un poco (para la pared);
     * con la pelota en su equipo, los de adelante se desmarcan: van más adelante y se abren del rival
     * que tienen cerca.
     */
    private void position(Player p) {
        if (p.runOn > 0 && !p.keeper) {
            p.steer(tmp.set(p.runDir).scl(run(p)));
            return;
        }
        formationTarget(p, ball.pos.x, ball.pos.z, tmp);
        Team t = p.team;
        if (owner != null && owner.team == t && owner != p && !p.keeper && p.baseA > -0.45f) {
            // los del humano se desmarcan siempre igual; los de la máquina, según la dificultad
            float push = t.human ? 9f : difficulty.runs;
            tmp.x += t.side * (p.baseA > -0.2f ? push : push * 0.45f);
            Player mark = nearestOpponent(p);
            if (mark != null && mark.pos.dst(p.pos) < 6f) tmp.y += (p.pos.y >= mark.pos.y ? 1 : -1) * 4f;
            tmp.x = MathUtils.clamp(tmp.x, -HALF_L + 3, HALF_L - 3);
            tmp.y = MathUtils.clamp(tmp.y, -HALF_W + 2, HALF_W - 2);
        }
        p.moveTo(tmp.x, tmp.y, run(p) * 0.8f);
    }

    /** Dónde le toca estar en la formación con la pelota en (bx, bz). */
    void formationTarget(Player p, float bx, float bz, Vector2 out) {
        Team t = p.team;
        float a = p.baseA * HALF_L * 0.7f + t.toA(bx) * 0.6f + 8f;
        a = MathUtils.clamp(a, -HALF_L + 4, HALF_L - 6);
        out.set(t.toX(a), p.baseZ * HALF_W * 0.85f + bz * 0.35f);
    }

    /** Su lugar para una pelota parada en (bx, bz), antes de los arreglos de cada una. */
    void formationSpot(Player p, float bx, float bz) {
        if (p.keeper) p.spot.set(p.team.ownGoalX() + p.team.side * 1.2f, MathUtils.clamp(bz * 0.1f, -2f, 2f));
        else formationTarget(p, bx, bz, p.spot);
    }

    private Player nearestOpponent(Player p) {
        Player best = null;
        float bestD = Float.MAX_VALUE;
        for (Player o : other(p.team).players) {
            if (o.benched) continue;
            float d = o.pos.dst2(p.pos);
            if (d < bestD) {
                bestD = d;
                best = o;
            }
        }
        return best;
    }

    /** El jugador de campo de {@code team} más cerca de {@code p}. */
    Player nearestTo(Team team, Player p) {
        Player best = null;
        float bestD = Float.MAX_VALUE;
        for (Player o : team.players) {
            if (o.keeper || o.benched) continue;
            float d = o.pos.dst2(p.pos);
            if (d < bestD) {
                bestD = d;
                best = o;
            }
        }
        return best;
    }

    // ---- la arena de práctica -------------------------------------------------------------------

    /**
     * Prende o apaga la arena de práctica: los rivales de campo salen de la cancha (queda su
     * arquero), se ponen las paredes y la pelota arranca en los pies del humano. Al apagarla, vuelve
     * el partido con un saque del medio.
     */
    void setPractice(boolean on) {
        practice = on;
        physics.walls(on);
        for (int i = 0; i < away.players.size(); i++) {
            Player p = away.players.get(i);
            if (p.keeper) continue;
            p.benched = on;
            p.kickoffSpot();
            if (on) physics.place(p, -40 + i * 3f, HALF_W + 3f);  // afuera, detrás de la pared
            else physics.place(p, p.spot.x, p.spot.y);
        }
        if (on) {
            restartPractice();
        } else {
            dead = 0;
            next = null;
            startSetPiece(new SetPiece(SetPiece.Kind.KICKOFF, home, 0, 0, null));
        }
    }

    /** La práctica vuelve a empezar: los compañeros en su lugar y la pelota en los pies del 11, en el medio. */
    private void restartPractice() {
        setPiece = null;
        next = null;
        dead = 0;
        holder = passTarget = null;
        shotBy = null;
        penaltyKick = false;
        for (Player p : home.players) {
            p.kickoffSpot();
            physics.place(p, p.spot.x, p.spot.y);
        }
        Player k = away.players.get(0);
        k.kickoffSpot();
        physics.place(k, k.spot.x, k.spot.y);
        Player yo = home.players.get(10);
        physics.place(yo, -1f, 0);
        yo.facing.set(home.side, 0);
        physics.placeBall(-1f + home.side * 0.6f, Ball.RADIUS, 0);
        controlled = yo;
        control(yo);
    }

    boolean inBox(Team t, float x, float z) {
        return Math.abs(x - t.ownGoalX()) < BOX_DEPTH && Math.abs(z) < BOX_HALF;
    }

    // ---- acciones con la pelota, para la máquina y para los esquemas de control ---------------

    /**
     * Si la puede agarrar. Al que la lleva sólo se la saca un rival que llegue a la pelota más o
     * menos tan cerca como él. Una pelota rápida es más difícil de parar (el que maneja el humano
     * llega un poco más), y la que acaba de patear un compañero no la frena nadie salvo a quien va.
     */
    boolean canTouch(Player p) {
        if (p.benched || setPiece != null || holder != null || p.busy() || p.cooldown > 0 || ball.pos.y > 1.6f) return false;
        if (shotBy == p.team && p != passTarget) return false;
        float d = ball.dist2D(p.pos), margin = p.team.human ? 0.3f : difficulty.steal;
        if (owner != null && owner != p && (owner.team == p.team || d > ball.dist2D(owner.pos) + margin)) return false;
        // El arquero en su área la tiene en la mano: no se la pueden sacar.
        if (owner != null && owner.keeper && owner.team != p.team && inBox(owner.team, owner.pos.x, owner.pos.y)) return false;
        float reach = p == controlled ? 1.05f : REACH;
        return d < reach * MathUtils.clamp(1.5f - ball.speed2D() / 30f, 0.5f, 1f);
    }

    /** Para patear se estira más que para agarrarla, y al que la lleva no lo frena haberla tocado recién; en el aire, con el pie, el muslo o la cabeza. */
    boolean canKick(Player p) {
        return reach(p) != null;
    }

    /** Con la pelota en el aire cerca, puede ir de cabeza. */
    boolean canHead(Player p) {
        return setPiece == null && holder == null && !p.busy() && ball.pos.y > 0.9f && ball.dist2D(p.pos) < 4.5f;
    }

    /**
     * Si Z es la suela para {@code p}: tiene la pelota, la tiene suelta al alcance, o le está por
     * llegar. Si no, Z es cambiar de jugador.
     */
    boolean soleContext(Player p) {
        if (setPiece != null || holder != null || p.busy()) return false;
        if (owner == p) return true;
        if (owner != null) return false;
        return passTarget == p || canTouch(p) || ballArriving(p, 0.4f);
    }

    /** Con qué le pegaría {@code p} ahora; null si no le llega. */
    Part reach(Player p) {
        if (setPiece != null || holder != null || p.busy()) return null;
        if (owner != null && owner != p) return null;
        if (owner != p && p.cooldown > 0) return null;
        float d = ball.dist2D(p.pos), y = ball.pos.y;
        if (y <= 0.25f) return d < KICK_REACH ? Part.GROUND : null;
        if (y <= 0.7f) return d < 1f ? Part.FOOT : null;
        if (y <= 1.25f) return d < 0.75f ? Part.THIGH : null;
        if (y <= 2.1f) return d < 0.6f ? Part.HEAD : null;
        return null;
    }

    /**
     * Qué tan rápido viene rodando la pelota hacia los pies de {@code p}: de adelante hacia atrás,
     * según hacia dónde mira y respecto de cómo se mueve él (negativo si se va para adelante).
     */
    float approach(Player p) {
        return -((ball.vel.x - p.vel.x) * p.facing.x + (ball.vel.z - p.vel.y) * p.facing.y);
    }

    /**
     * Cuánto le entra el pie por abajo a una pelota que viene rodando hacia él, de 0 a 1: la que
     * arrastró para atrás con la suela, o un pase que llega despacio. Una que viene rápida no: esa
     * se juega de primera. Tampoco la que lleva pegada al pie corriendo.
     */
    float scoop(Player p) {
        if (ball.pos.y > 0.25f || owner == p && p.loose <= 0 && p.sole == Player.Sole.OFF) return 0;
        // recién pisada (la suela todavía encima o recién soltada) y debajo del pie o detrás: el pie le entra por abajo seguro
        if (owner == p && (ball.pos.x - p.pos.x) * p.facing.x + (ball.pos.z - p.pos.y) * p.facing.y < 0.45f) return 1;
        float a = approach(p);
        return MathUtils.clamp((a - 0.4f) / 2.5f, 0, 1) * MathUtils.clamp((7f - a) / 2f, 0, 1);
    }

    /** Con qué altura (grados) arranca un tiro de {@code p}: según cómo le llega la pelota al pie. */
    float startElevation(Player p) {
        Part part = reach(p);
        if (part == null || part == Part.GROUND) return 8f + 62f * scoop(p);
        return part == Part.FOOT ? 60f : part == Part.THIGH ? 60f : 25f;
    }

    /**
     * Lo que haría un toque de X, sin hacerlo: a quién va (null si no es un pase) y con qué
     * velocidad sale la pelota. En el aire, la vuelve a subir para que caiga delante (el jueguito;
     * con flechas, un poco para ese lado). Al pie, si hay un compañero hacia donde se apunta, es un
     * pase; si no, depende de cómo le llega la pelota: rodando hacia el pie, le entra por abajo y la
     * levanta; si no, la empuja unos metros (el autopase).
     */
    Player planTouch(Player p, Vector2 aim, boolean pointing, Vector3 vel) {
        Part part = reach(p);
        if (part != null && part != Part.GROUND) {
            lift(p, part == Part.FOOT ? 4.6f : part == Part.THIGH ? 3.4f : 3f, aim, pointing, vel);
            return null;
        }
        // pase sólo si apunta con una flecha: sin flechas un toque es un toque (no se va a un compañero lejos)
        Player mate = pointing ? receiver(p, aim) : null;
        if (mate != null) {
            planTo(p, mate, vel);
            return mate;
        }
        float s = scoop(p);
        if (s > 0.3f) {
            lift(p, 3.4f + 1.4f * s, aim, pointing, vel);
        } else {
            float speed = Ball.speedToReach(7f, 0.5f);
            vel.set(aim.x * speed, 0, aim.y * speed);
        }
        return null;
    }

    /** Para que suba a {@code up} y vuelva a caer delante del cuerpo, o un poco hacia donde apunta. */
    private void lift(Player p, float up, Vector2 aim, boolean pointing, Vector3 vel) {
        float t = 2 * up / Ball.GRAVITY;
        float vx = p.vel.x + (p.pos.x + p.facing.x * 0.45f - ball.pos.x) / t, vz = p.vel.y + (p.pos.y + p.facing.y * 0.45f - ball.pos.z) / t;
        if (pointing) {
            vx += aim.x * 1.2f;
            vz += aim.y * 1.2f;
        }
        vel.set(vx, up, vz);
    }

    /** Qué sería un toque sin compañero, para la ayuda en pantalla. */
    String touchLabel(Player p) {
        Part part = reach(p);
        if (part == Part.HEAD) return "Toque de cabeza";
        if (part == Part.THIGH) return "Toque de muslo";
        if (part == Part.FOOT) return "Jueguito";
        return scoop(p) > 0.3f ? "La levanta" : "Autopase";
    }

    /** Un toque de X: lo que dice {@link #planTouch}. Si no es un pase, sale a buscarla. */
    void touchBall(Player p, Vector2 aim, boolean pointing) {
        boolean air = ball.pos.y > 0.25f;
        Player mate = planTouch(p, aim, pointing, v3);
        if (mate != null) {
            passTo(p, mate);
            return;
        }
        float hs = (float) Math.sqrt(v3.x * v3.x + v3.z * v3.z);
        if (hs > 0.01f) tmp2.set(v3.x / hs, v3.z / hs);
        else tmp2.set(p.facing);
        kick(p, tmp2, hs, v3.y, 0, v3.y > 0 && !air ? 6f : 0);
        if (v3.y > 0) p.kickAnim = Player.KICK_ANIM * 0.6f;
        passTarget = p;
    }

    /** Si la pelota, suelta, le va a caer a los pies (a menos de 0,75 m de alto) dentro de {@code within} segundos: el jueguito. */
    boolean ballFalling(Player p, float within) {
        return owner == null && holder == null && setPiece == null && trajectory.fallsTo(p, within, 0.75f, 1f);
    }

    /**
     * Un toque esperando una pelota que baja: si le llega primero al muslo o a la cabeza pero va a
     * seguir bajando hasta el pie, mejor esperarla con el pie.
     */
    boolean waitForFoot(Player p) {
        Part part = reach(p);
        return (part == Part.THIGH || part == Part.HEAD) && ball.vel.y < 0 && trajectory.fallsTo(p, 0.4f, 0.7f, 1f);
    }

    /** Si la pelota le va a llegar a los pies dentro de {@code within} segundos. */
    boolean ballArriving(Player p, float within) {
        return owner == null && holder == null && trajectory.passes(p, within, KICK_REACH);
    }

    /** Salta a cabecear o, si viene baja, se tira de palomita, hacia donde la va a encontrar. */
    void startHeader(Player p) {
        Trajectory tr = trajectory;
        int bestK = 0;
        float best = Float.MAX_VALUE;
        for (int k = 0; k <= 14; k++) {
            float d = Vector2.dst(tr.x[k], tr.z[k], p.pos.x, p.pos.y) - Player.RUN * k * Trajectory.DT * 0.5f;
            if (d < best) {
                best = d;
                bestK = k;
            }
        }
        tmp.set(tr.x[bestK] - p.pos.x, tr.z[bestK] - p.pos.y);
        if (!tmp.isZero(0.05f)) p.facing.set(tmp).nor();
        if (tr.y[bestK] > 1.3f) {
            p.start(Player.Act.HEAD);
            tmp.scl(1 / Math.max(bestK * Trajectory.DT, 0.25f)).limit(6f);
            p.launch(tmp.x, tmp.y);
        } else {
            p.start(Player.Act.DIVE_HEAD);
            p.launch(p.facing.x * 8f, p.facing.y * 8f);
        }
        p.redirect.set(p.facing);
    }

    /** Un puntazo: estira el pie hacia la pelota si está cerca y baja; si no, hacia donde mira. */
    void poke(Player p) {
        tmp.set(ball.pos.x - p.pos.x, ball.pos.z - p.pos.y);
        p.startPoke(tmp.len() < 2f && ball.pos.y < 0.5f && !tmp.isZero(0.05f) ? tmp : p.facing);
    }

    /** La agarra: amortigua la pelota y desde ahí la lleva pegada. */
    void control(Player p) {
        touch(p);
        v3.set(p.vel.x + (ball.vel.x - p.vel.x) * 0.3f, ball.vel.y * 0.3f, p.vel.y + (ball.vel.z - p.vel.y) * 0.3f);
        physics.moveBall(v3);
        physics.spinBall(w3.setZero());
        setOwner(p);
        p.think = 0.3f;
        p.cooldown = 0.2f;
    }

    /**
     * Pelota al pie: va hacia un punto delante del que la lleva, que se aleja y se acerca con la
     * zancada como si la fuera tocando, y gira con él.
     */
    private void keepClose(Player p, float dt) {
        if (p.busy() || ball.pos.y > 0.4f) return;
        float ahead = 0.5f + 0.25f * Math.abs(MathUtils.sin(p.stride));
        float tx = p.pos.x + p.facing.x * ahead, tz = p.pos.y + p.facing.y * ahead;
        float dx = p.vel.x + (tx - ball.pos.x) * 8f - ball.vel.x;
        float dz = p.vel.y + (tz - ball.pos.z) * 8f - ball.vel.z;
        float d = (float) Math.sqrt(dx * dx + dz * dz), max = 80f * dt;
        if (d > max) {
            dx *= max / d;
            dz *= max / d;
        }
        physics.moveBall(v3.set(ball.vel.x + dx, ball.vel.y, ball.vel.z + dz));
    }

    /**
     * Le pega: la pelota sale hacia {@code dir} a {@code speed}, subiendo a {@code up}, con
     * {@code curl} de giro sobre el eje vertical (rad/s; positivo la curva hacia la derecha) y
     * {@code backspin} sobre el horizontal (positivo la levanta, negativo la baja).
     */
    void kick(Player p, Vector2 dir, float speed, float up, float curl, float backspin) {
        if (holder == p) holder = null;
        touch(p);
        v3.set(dir.x * speed, up, dir.y * speed);
        w3.set(-dir.y * backspin, -curl, dir.x * backspin);
        physics.kickBall(v3, w3);
        if (ears != null) ears.hear(Noise.KICK, speed);
        ball.kickDir.set(dir).nor();
        setOwner(null);
        // la máquina tarda en darse cuenta de adónde va: según la dificultad la del rival, poco la del humano
        for (Player o : players)
            if (o != p) o.react = (o.team.human ? 0.08f : difficulty.reaction) * MathUtils.random(0.7f, 1.3f);
        shotBy = p.team;
        shotTime = 0.4f;
        p.cooldown = 0.35f;
        p.kickAnim = Player.KICK_ANIM;
    }

    void kick(Player p, Vector2 dir, float speed, float up, float curl) {
        kick(p, dir, speed, up, curl, 0);
    }

    /** Lo que se le sigue haciendo a la pelota recién pateada: subirla o bajarla, darle comba o efecto, y la fuerza que va a tener. */
    void nudge(float up, float curl, float backspin, float speed) {
        Vector2 d = ball.kickDir;
        float hs = ball.speed2D();
        v3.set(ball.vel);
        if (hs > 0.01f) {
            v3.x *= speed / hs;
            v3.z *= speed / hs;
        }
        v3.y += up;
        physics.moveBall(v3);
        physics.spinBall(w3.set(ball.spin).add(-d.y * backspin, -curl, d.x * backspin));
    }

    private void touch(Player p) {
        ball.lastTouch = p;
        passTarget = null;
        shotBy = null;
        if (setPiece != null && setPiece.taker == p) {
            penaltyKick = setPiece.kind == SetPiece.Kind.PENALTY;
            setPiece = null;
        }
    }

    /** Pase al compañero mejor ubicado en esa dirección; si no hay ninguno, la pelota va igual. */
    Player pass(Player p, Vector2 aim) {
        Player best = receiver(p, aim);
        if (best != null) passTo(p, best);
        else kick(p, straightPass(p, aim, v3));
        return best;
    }

    /** Lo que haría un pase hacia {@code aim}, sin hacerlo: a quién va (o null, al espacio) y con qué velocidad sale. */
    Player planPass(Player p, Vector2 aim, Vector3 vel) {
        Player best = receiver(p, aim);
        if (best != null) planTo(p, best, vel);
        else straightPass(p, aim, vel);
        return best;
    }

    /** El compañero que recibiría un pase hacia {@code aim}: el más alineado y cerca, a menos de 45 grados. */
    private Player receiver(Player p, Vector2 aim) {
        Player best = null;
        float bestScore = Float.MAX_VALUE;
        for (Player m : p.team.players) {
            if (m == p) continue;
            tmp.set(m.pos).sub(p.pos);
            float d = tmp.len();
            if (d < 3 || d > 45) continue;
            float angle = Math.abs(MathUtils.atan2(aim.crs(tmp), aim.dot(tmp))) * MathUtils.radDeg;
            if (angle > 45) continue;
            float score = angle + d * 0.4f;
            if (score < bestScore) {
                bestScore = score;
                best = m;
            }
        }
        return best;
    }

    /** Sin nadie a quien dársela: por abajo hacia ahí, o con la mano un poco por arriba. */
    private Vector3 straightPass(Player p, Vector2 aim, Vector3 vel) {
        boolean hands = throwing(p) || holder == p;
        float speed = hands ? 11f : 15f;
        return vel.set(aim.x * speed, hands ? 3f : 0, aim.y * speed);
    }

    /**
     * Al pie si está cerca, por arriba si está lejos; con la mano (el arquero o un lateral), por el
     * aire para que le caiga al pie. Lo que sube lo calcula con el mismo aire que la frena.
     */
    void passTo(Player p, Player mate) {
        planTo(p, mate, v3);
        if (!p.team.human) {
            // La máquina no la pone siempre justa: según la dificultad, se le va un poco.
            tmp.set(v3.x, v3.z).rotateDeg(MathUtils.random(-difficulty.passError, difficulty.passError));
            v3.x = tmp.x;
            v3.z = tmp.y;
        }
        kick(p, v3);
        passTarget = mate;
        if (mate.team.human && !mate.keeper) controlled = mate;
        if (!p.keeper && setPiece == null && holder == null) {
            // El que la da sigue corriendo hacia adelante un momento: la pared.
            p.runDir.set(p.team.side, 0);
            if (p.vel.len2() > 4f) p.runDir.add(tmp.set(p.vel).nor()).nor();
            p.runOn = 1.2f;
        }
    }

    /**
     * Después de un tiro cargado que no va al arco: si un compañero llega adonde va la pelota antes
     * que cualquier rival, es para él: va a buscarla y, si es del humano, pasa a manejarlo.
     */
    void markReceiver(Player p) {
        trajectory.predict(this);
        float endX = trajectory.x[Trajectory.N - 1];
        if (Math.abs(endX) > HALF_L) return;
        // si le vuelve a caer al que la pateó (un sombrero, levantarla en el lugar), es para él
        float mine = trajectory.reachAfter(p, run(p), 0.4f);
        Player best = null;
        float bestT = Float.MAX_VALUE, theirs = Float.MAX_VALUE;
        for (Player m : p.team.players) {
            if (m == p || m.keeper) continue;
            float t = trajectory.reach(m, run(m));
            if (t < bestT) {
                bestT = t;
                best = m;
            }
        }
        for (Player o : other(p.team).players) theirs = Math.min(theirs, trajectory.reach(o, run(o)));
        if (best == null || bestT == Float.MAX_VALUE || bestT > theirs - 0.2f || mine <= bestT + 0.3f) {
            if (mine < theirs) passTarget = p;  // va a buscarla él, y no se cambia de jugador
            return;
        }
        passTarget = best;
        if (best.team.human) controlled = best;
    }

    /**
     * Con qué velocidad tiene que salir la pelota para llegarle a {@code mate}: adonde va a estar
     * cuando le llegue, según cómo viene corriendo (se calcula tres veces: el viaje depende de adónde).
     */
    private Vector3 planTo(Player p, Player mate, Vector3 vel) {
        boolean hands = holder == p || throwing(p);
        float lead = 0, d, vh = 0, up = 0;
        for (int i = 0; i < 3; i++) {
            tmp.set(mate.pos).mulAdd(mate.vel, lead).sub(p.pos);
            d = tmp.len();
            if (hands) {
                vh = MathUtils.clamp(d * 0.6f + 6f, 8f, 17f);
                up = lob(d, vh, ball.pos.y);
                lead = 2 * Math.max(up, 0) / Ball.GRAVITY;
            } else if (d > 30) {
                vh = 21f;
                up = lob(d, vh, Ball.RADIUS + 0.03f);
                lead = 2 * Math.max(up, 0) / Ball.GRAVITY;
            } else {
                vh = Math.min(Ball.speedToReach(d, PASS_ARRIVE), 26f);
                up = 0;
                lead = Ball.rollTime(vh, PASS_ARRIVE);
            }
            lead = MathUtils.clamp(lead, 0, 3f);
        }
        tmp.nor();
        return vel.set(tmp.x * vh, up, tmp.y * vh);
    }

    /** Le pega con esa velocidad, sin giro. */
    private void kick(Player p, Vector3 vel) {
        float hs = (float) Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        tmp2.set(vel.x, vel.z).scl(1 / Math.max(hs, 0.001f));
        kick(p, tmp2, hs, vel.y, 0);
    }

    /** Con qué velocidad para arriba tiene que salir, a {@code vh} hacia adelante y desde {@code h} de alto, para caer a {@code d} metros. */
    float lob(float d, float vh, float h) {
        float lo = -2f, hi = 25f;
        for (int i = 0; i < 14; i++) {
            float up = (lo + hi) / 2;
            if (flight(vh, up, h) < d) lo = up;
            else hi = up;
        }
        return (lo + hi) / 2;
    }

    /** Hasta dónde llega por el aire antes de picar. */
    private float flight(float vh, float up, float h) {
        ghost.pos.set(0, Math.max(h, Ball.RADIUS + 0.03f), 0);
        ghost.vel.set(vh, up, 0);
        ghost.spin.setZero();
        for (int i = 0; i < 720 && !ghost.onGround(); i++) ghost.step(1 / 120f);
        return ghost.pos.x;
    }

    /**
     * Por dónde iría una pelota que sale de {@code from} con esa velocidad y ese giro, hasta que
     * pica y sigue un poco (o hasta cuarenta metros): para dibujarla mientras se carga el tiro.
     */
    void path(Vector3 from, Vector3 vel, Vector3 spin, ShotPreview out) {
        ghost.pos.set(from);
        if (vel.y > 0) ghost.pos.y = Math.max(from.y, Ball.RADIUS + 0.03f);
        ghost.vel.set(vel);
        ghost.spin.set(spin);
        out.count = 0;
        float traveled = 0;
        boolean landed = vel.y <= 0;
        for (int i = 0; i < 360 && traveled < 40f && !(landed && traveled > 15f); i++) {
            if (i % 4 == 0) out.add(ghost.pos);
            float px = ghost.pos.x, pz = ghost.pos.z;
            boolean flying = !ghost.onGround();
            ghost.step(1 / 120f);
            traveled += Vector2.dst(px, pz, ghost.pos.x, ghost.pos.z);
            if (flying && ghost.onGround()) landed = true;
        }
        out.add(ghost.pos);
    }

    /** Hacia dónde apuntar para que, con esa comba, la pelota pase por (tx, tz). */
    Vector2 aimCurled(Vector2 from, float tx, float tz, float speed, float up, float curl, Vector2 out) {
        out.set(tx - from.x, tz - from.y);
        float dist = out.len();
        out.nor();
        for (int i = 0; i < 4; i++) {
            ghost.pos.set(from.x, Ball.RADIUS + 0.03f, from.y);
            ghost.vel.set(out.x * speed, up, out.y * speed);
            ghost.spin.set(0, -curl, 0);
            for (int s = 0; s < 480 && Vector2.dst(ghost.pos.x, ghost.pos.z, from.x, from.y) < dist; s++) ghost.step(1 / 120f);
            tmp2.set(ghost.pos.x - from.x, ghost.pos.z - from.y);
            tmp.set(tx - from.x, tz - from.y);
            out.rotateDeg(-MathUtils.atan2(tmp.crs(tmp2), tmp.dot(tmp2)) * MathUtils.radDeg);
        }
        return out;
    }

    /** El que está por sacar un lateral. */
    boolean throwing(Player p) {
        return setPiece != null && setPiece.kind == SetPiece.Kind.THROW_IN && setPiece.taker == p;
    }

    /** El arquero la agarra: desde ahí la lleva como cualquier jugador (en su área nadie se la saca). */
    void catchBall(Player k) {
        setOwner(k);
        k.think = 0.6f;
        passTarget = null;
        shotBy = null;
        holdTime = 0;
        ball.lastTouch = k;
        k.cooldown = 0.2f;
    }

    /** Rebote del arquero parado: casi siempre para adelante y a un costado, a veces al córner. */
    void parry(Player k) {
        float speed = ball.vel.len();
        v3.set(k.team.side * speed * MathUtils.random(-0.15f, 0.35f), 2f + MathUtils.random(3f),
                ball.vel.z * 0.3f + MathUtils.randomSign() * MathUtils.random(2f, 6f));
        physics.kickBall(v3, w3.setZero());
        ball.lastTouch = k;
        setOwner(null);
        passTarget = null;
        shotBy = null;
        k.cooldown = 0.6f;
    }

    // ---- choques (los cuenta la física) -------------------------------------------------------

    /**
     * La pierna de una barrida tocó algo. Si tocó la pelota primero, se la lleva hacia donde diga
     * {@link Player#redirect}; si después voltea al rival, no es falta. Si tocó al rival antes que a
     * la pelota, es falta: tiro libre, o penal si fue en el área del que barrió.
     */
    @Override
    public void slideTouched(Player slider, Player other) {
        if (setPiece != null || dead > 0 || slider.act != Player.Act.SLIDE) return;
        if (other == null) {
            if (slider.actTouched || holder != null) return;
            slider.actTouched = true;
            if (owner != null && owner.team != slider.team) owner.cooldown = 0.8f;
            kick(slider, slider.redirect, slider.vel.len() + 4f, 0.5f, 0);
            slider.kickAnim = 0;
        } else if (other.team != slider.team && !other.busy()) {
            if (slider.actTouched) other.start(Player.Act.FALL);
            else foul(slider, other);
        }
    }

    /**
     * El pie de un puntazo tocó algo. Si tocó la pelota primero, se la lleva hacia donde estiró el
     * pie y el que la llevaba la pierde (salvo el arquero en su área, que la tiene en la mano). Si
     * tocó la pierna de un rival antes que la pelota, es falta, como en la barrida.
     */
    @Override
    public void pokeTouched(Player poker, Player other) {
        if (setPiece != null || dead > 0 || poker.act != Player.Act.POKE) return;
        if (other == null) {
            if (poker.actTouched || holder != null) return;
            if (owner != null && owner.keeper && owner.team != poker.team && inBox(owner.team, owner.pos.x, owner.pos.y)) return;
            poker.actTouched = true;
            if (owner != null && owner != poker) owner.cooldown = 0.6f;
            kick(poker, poker.redirect, 7f, 0.3f, 0);
            poker.kickAnim = 0;
        } else if (other.team != poker.team && !other.busy() && !poker.actTouched) {
            foul(poker, other);
        }
    }

    /** La pelota pegó en alguien: ahora es el último que la tocó, y el que la llevaba la perdió. */
    @Override
    public void ballHitBody(Player p) {
        if (holder != null || setPiece != null || p == owner || ball.lastTouch == p) return;
        ball.lastTouch = p;
        setOwner(null);
        passTarget = null;
        shotBy = null;
    }

    /** La red cede y la frena: casi no rebota. */
    @Override
    public void ballBumped(int what, float strength) {
        if (ears == null) return;
        ears.hear(what == Physics.BUMP_POST ? Noise.POST : what == Physics.BUMP_WALL ? Noise.WALL : Noise.BODY, strength);
    }

    @Override
    public void ballInNet() {
        if (ball.vel.len2() > 1f) physics.moveBall(v3.set(ball.vel).scl(0.6f));
    }

    private void headContact(Player p) {
        if (p.actTime > p.act.reach || p.actTouched || holder != null || setPiece != null) return;
        float hx = p.pos.x, hz = p.pos.y, hy = 1.75f + p.jump();
        if (p.act == Player.Act.DIVE_HEAD) {
            hx += p.facing.x * 0.9f;
            hz += p.facing.y * 0.9f;
            hy = 0.45f;
        }
        if (Vector3.dst(ball.pos.x, ball.pos.y, ball.pos.z, hx, hy, hz) > 0.65f) return;
        p.actTouched = true;
        float speed = p.act == Player.Act.HEAD ? Math.min(11f + ball.vel.len() * 0.3f, 20f) : 17f;
        kick(p, p.redirect, speed, p.act == Player.Act.HEAD ? 1.2f : 0.6f, 0);
        p.kickAnim = 0;
    }

    // ---- reglas ------------------------------------------------------------------------------

    private void foul(Player fouler, Player victim) {
        victim.start(Player.Act.FALL);
        float x = MathUtils.clamp(victim.pos.x, -HALF_L + 1, HALF_L - 1), z = MathUtils.clamp(victim.pos.y, -HALF_W + 1, HALF_W - 1);
        if (inBox(fouler.team, x, z)) {
            float px = fouler.team.ownGoalX() + fouler.team.side * PENALTY_SPOT;
            whistle(new SetPiece(SetPiece.Kind.PENALTY, victim.team, px, 0, victim), "¡Penal!", 1.6f);
        } else {
            whistle(new SetPiece(SetPiece.Kind.FREE_KICK, victim.team, x, z, victim), "Falta", 1.4f);
        }
    }

    private void checkBounds() {
        float x = ball.pos.x, z = ball.pos.z;
        if (practice) {
            // las líneas no cuentan (las paredes están un poco afuera): sólo el gol; si igual se escapa, vuelve a empezar
            // gol es estar adentro de la red (las paredes están más atrás: se puede pasar al lado del arco)
            float behind = Math.abs(x) - HALF_L;
            boolean goal = behind > Ball.RADIUS && behind < Physics.NET_DEPTH && Math.abs(z) < GOAL_HALF && ball.pos.y < GOAL_H;
            if (goal) {
                (home.ownGoalX() * x > 0 ? away : home).goals++;
                whistle(null, "¡GOL!", 1.5f);
            } else if (behind > Physics.ARENA_END + 1 || Math.abs(z) > HALF_W + Physics.ARENA_SIDE + 1) {
                whistle(null, "Otra vez", 0.5f);
            }
            return;
        }
        if (Math.abs(x) > HALF_L + Ball.RADIUS) {
            Team defending = home.ownGoalX() * x > 0 ? home : away, attacking = other(defending);
            float sx = Math.signum(x), sz = z >= 0 ? 1 : -1;
            if (Math.abs(z) < GOAL_HALF && ball.pos.y < GOAL_H) {
                attacking.goals++;
                whistle(new SetPiece(SetPiece.Kind.KICKOFF, defending, 0, 0, null), "¡GOL de " + attacking.name + "!", 2.5f);
            } else if (ball.lastTouch != null && ball.lastTouch.team == defending) {
                whistle(new SetPiece(SetPiece.Kind.CORNER, attacking, sx * (HALF_L - 0.4f), sz * (HALF_W - 0.4f), null), "Córner", 1.2f);
            } else {
                whistle(new SetPiece(SetPiece.Kind.GOAL_KICK, defending, sx * (HALF_L - 5.5f), sz * 8.9f, null), "Saque de arco", 1.2f);
            }
        } else if (Math.abs(z) > HALF_W + Ball.RADIUS) {
            Team taker = ball.lastTouch != null && ball.lastTouch.team == home ? away : home;
            whistle(new SetPiece(SetPiece.Kind.THROW_IN, taker, MathUtils.clamp(x, -HALF_L + 1, HALF_L - 1), Math.signum(z) * HALF_W, null), "Lateral", 1f);
        }
    }

    void whistle(SetPiece sp, String text, float delay) {
        next = sp;
        dead = delay;
        holder = passTarget = null;
        setOwner(null);
        shotBy = null;
        physics.releaseBall();
        say(text);
    }

    private void startSetPiece(SetPiece sp) {
        setPiece = sp;
        holder = passTarget = null;
        setOwner(null);
        penaltyKick = false;
        sp.setUp(this);
        if (sp.team == home) controlled = sp.taker;
    }

    /** Los que no sacan van a su lugar y miran la pelota. */
    private void setPieceMove(Player p) {
        p.moveTo(p.spot.x, p.spot.y, Player.RUN);
        if (p.vel.len2() < 1f) p.facing.set(ball.pos.x - p.pos.x, ball.pos.z - p.pos.y).nor();
    }

    /** El que saca por la máquina espera un poco a que se acomoden y saca. */
    private void takerWaits(Player p, float dt) {
        p.steer(zero);
        setPiece.wait -= dt;
        if (setPiece.wait <= 0) setPiece.aiTake(this);
    }

    private void say(String text) {
        message = text;
        messageTime = 2f;
    }

    @Override
    public void dispose() {
        physics.dispose();
    }
}
