package com.fpetrola.proto.soccer;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

/**
 * Dos botones y las flechas, a la manera del Sensible Soccer pero con el pie: el cuerpo, la suela
 * y el golpe. Las jugadas no están programadas una por una: salen de combinar esas tres cosas.
 * <ul>
 *   <li><b>El cuerpo:</b> las flechas lo mueven; con la pelota, la lleva pegada al pie.</li>
 *   <li><b>La suela (mantener Z con la pelota):</b> el pie se apoya en la pelota y la pelota va
 *       adonde va el pie; las flechas mueven el pie, el cuerpo lo sigue si se aleja, y si la pelota
 *       queda atrás, gira hacia ella. Al soltar Z, la pelota sigue un momento con lo que le dio el
 *       pie. Un toque corto de Z, sin flechas nuevas, es un amague: el pie pasa por arriba sin
 *       tocarla. Z apretado cuando llega la pelota la recibe con la suela.</li>
 *   <li><b>El golpe (X):</b> mantenerlo carga fuerza; apenas se toca una flecha la fuerza queda
 *       fija y desde ahí las flechas sólo le dan forma (atrás alto, adelante bajo, costados comba):
 *       así un globito suave no necesita fuerza, y un tiro fuerte se carga primero y se curva
 *       después. La flecha que ya estaba apretada al apretar X (para dónde) no cuenta como forma
 *       mientras se la siga apretando. Al soltar, sale. Cómo sale depende también de cómo le
 *       llega la pelota: rodando hacia el pie le entra por abajo y sube; en el aire, con el pie, el
 *       muslo o la cabeza. Un toque corto es un pase al compañero que haya hacia donde se apunta;
 *       si no hay nadie, un toque: la sube si viene en el aire o rodando hacia el pie (levantarla,
 *       el jueguito), o la empuja unos metros y sale a buscarla (el autopase). Z mientras se carga
 *       cancela el tiro y vuelve a la suela.</li>
 *   <li>Sin la pelota: un toque de X es un puntazo (el pie se estira hasta 1,2 m), mantenerlo es
 *       barrida, y con la pelota en el aire, cabezazo. Un toque de Z cambia de jugador; mantenerlo
 *       contiene: de frente a la pelota, despacio y con las piernas abiertas.</li>
 * </ul>
 * Siempre se muestra lo que va a pasar: con la pelota al pie, el toque que saldría; cargando, por
 * dónde iría el tiro; barriendo o yendo de cabeza, para dónde sale la pelota.
 */
// Desactivado: el partido usa GolpeScheme. Queda guardado (con sus pruebas) para retomarlo en otro momento.
final class SensibleScheme implements ControlScheme {
    /**
     * La variante con botón de elevación: todo igual, salvo que la altura del tiro no la dan las
     * flechas adelante y atrás sino C. Mientras se mantiene C, el punto donde se le pega baja desde
     * arriba de todo de la pelota (sale rasante, con efecto para adelante) hasta abajo de todo (sale
     * bien alta, con efecto para atrás): cuanto más tiempo, más alto. X es la fuerza y las flechas
     * de costado la comba, como siempre. Después de cada golpe el punto vuelve arriba.
     */
    private final boolean liftButton;
    /** Con botón de elevación: cuánto tarda el punto en bajar de arriba de todo a abajo de todo, y la altura (grados) abajo de todo. */
    private static final float SWEEP = 1f, LIFT_TOP = 75f;
    /** Con botón de elevación: cuánto bajó el punto, de 0 (arriba de todo) a 1 (abajo de todo). */
    float lowered;
    private final Vector3 contact = new Vector3();
    private boolean showContact;

    SensibleScheme() {
        this(false);
    }

    SensibleScheme(boolean liftButton) {
        this.liftButton = liftButton;
    }

    /** Apretado menos que esto es un toque. */
    private static final float TAP = 0.12f;
    /** Cuánto tarda en llenarse la fuerza, cuánto se puede mantener como mucho, y cuánto espera a la pelota si no llegó. */
    private static final float FILL = 0.4f, MAX_HOLD = 1.6f, WAIT = 0.7f;
    /** Con qué velocidad sale, del tiro más flojo al más fuerte. */
    private static final float SOFT = 5f, HARD = 34f;
    /** Altura de salida en grados: entre cuáles se mueve, y cuánto sube o baja por segundo. */
    private static final float LOWEST = 2f, HIGHEST = 55f, RAISE = 150f, LOWER = 120f;
    /** Comba en rad/s: hasta cuánto, y cuánto suma por segundo. */
    private static final float MAX_CURL = 70f, BEND = 380f;
    /** Cuanto más alta sale de abajo, menos fuerte puede llegar a salir: a la altura máxima, la mitad del tope. En el aire no. */
    private static final float LOFT_COST = 0.5f;
    /**
     * La suela: cuánto espera, sin flechas nuevas, para saber si es un amague o una pisada; desde
     * qué distancia del pie camina el cuerpo, y a qué velocidad; cuánto tarda en girar si la pelota
     * quedó atrás, y a qué velocidad gira.
     */
    private static final float ARM = 0.12f, FOLLOW = 0.5f, SOLE_WALK = 3.5f, TURN_AFTER = 0.08f, TURN = 10f;
    /** Más alta que esto, una pelota que llega no se para sola: se la baja con Z o se le pega con X. */
    private static final float SETTLE = 0.5f;
    /** Sin la pelota, X mantenido esto es barrida (antes de soltarlo, puntazo); y conteniendo, a cuánto camina. */
    private static final float SLIDE_AFTER = 0.2f, CONTAIN_SPEED = 3f;

    private Player player;
    private boolean winding, released, tapped, aimed, dead, throwIn, showing, tackling, heel;
    /** Lo que se muestra con la flecha chata: hacia dónde se apunta parado, o hacia dónde sale en una barrida o un cabezazo. */
    private Vector2 arrow;
    /** Desde que se apretó, cuánto se mantuvo, y cuánto lleva esperando a la pelota. */
    private float since, held, waited;
    /** La altura con que sale es la de la parte del cuerpo que le pega más lo que la subieron o bajaron las flechas. */
    private float elevation, lift, curl;
    private final Vector2 aim = new Vector2(1, 0), kickDir = new Vector2(1, 0), passDir = new Vector2(), tmp = new Vector2(), pressFacing = new Vector2();
    /**
     * La flecha que estaba apretada al apretar X (para dónde; no es forma mientras se la siga
     * apretando), las flechas que sí dan forma, y desde cuándo la fuerza quedó fija (negativo: todavía no).
     */
    private final Vector2 pressDir = new Vector2(), shapeDir = new Vector2();
    private boolean pressHeld;
    private float frozen = -1;
    private String hint;
    private final Vector3 vel = new Vector3(), spin = new Vector3();
    private final ShotPreview shot = new ShotPreview();

    @Override
    public String name() {
        return liftButton ? "Sensible con elevación (C)" : "Sensible: suela y golpe";
    }

    @Override
    public String help() {
        if (liftButton)
            return "Flechas: mover · C mantenido: le pega más abajo, sale más alta · X: tocar = pase o toque; mantener = fuerza, y una flecha de costado la deja fija y da comba\n"
                    + "Z: con la pelota la pisa (las flechas mueven el pie); cerca de la pelota, mantener = contener; lejos de la jugada = otro jugador · sin la pelota, X: puntazo / barrida";
        return "Flechas: mover · X: tocar = pase o toque; mantener = fuerza, y una flecha la deja fija y da forma (atrás alto, adelante bajo, costados comba)\n"
                + "Z: con la pelota la pisa (las flechas mueven el pie); cerca de la pelota, mantener = contener; lejos de la jugada = otro jugador · sin la pelota, X: puntazo / barrida";
    }

    @Override
    public Vector2 aim() {
        return arrow;
    }

    @Override
    public String hint() {
        return hint;
    }

    @Override
    public ShotPreview shot() {
        return showing ? shot : null;
    }

    @Override
    public Vector3 contact() {
        return showContact ? contact : null;
    }

    private boolean charging() {
        return winding && !released && since >= TAP;
    }

    @Override
    public void update(Match m, Player p, Pad pad, float dt) {
        if (p != player) {
            if (player != null) player.releaseSole();
            player = p;
            winding = tackling = false;
        }
        SetPiece sp = m.setPiece;
        boolean taking = sp != null && sp.taker == p;
        dead = taking || m.holder == p;
        throwIn = taking && sp.kind == SetPiece.Kind.THROW_IN;
        if (liftButton && pad.cDown) lowered = Math.min(1, lowered + dt / SWEEP);

        sole(m, p, pad, dt);
        if (dead) {
            // Parado con la pelota: las flechas apuntan, salvo mientras se carga el tiro.
            p.stop();
            if (!winding) {
                if (taking) {
                    if (!pad.dir.isZero()) {
                        sp.aim.set(pad.dir).nor();
                        sp.clampAim(sp.aim);
                    }
                    aim.set(sp.aim);
                } else if (!pad.dir.isZero() && pad.dir.x * p.team.side > -0.2f) {
                    aim.set(pad.dir).nor();
                } else if (aim.x * p.team.side < 0.2f) {
                    aim.set(p.team.side, 0);  // el arquero recién la agarró: que no apunte a su arco
                }
            }
            p.facing.set(aim);
        } else if (p.sole == Player.Sole.ON) {
            // Con la suela las flechas mueven el pie: el cuerpo lo sigue; esperando la pelota, va a buscarla.
            if (m.owner == p) followFoot(p, dt);
            else if (m.passTarget == p) intercept(m, p);
            else p.steer(tmp.setZero());
            if (!pad.dir.isZero()) aim.set(pad.dir).nor();
            else aim.set(p.facing);
        } else if (m.containing) {
            contain(m, p, pad);
        } else if (charging()) {
            // Cargando el tiro: sigue con el envión, cada vez más despacio, con la pelota al pie.
            p.steer(tmp.set(p.vel).scl(1 - 1.5f * dt));
        } else {
            move(m, p, pad);
            if (!pad.dir.isZero()) aim.set(pad.dir).nor();
            else aim.set(p.facing);
        }
        if (p.busy() && p.act != Player.Act.POKE) p.redirect.set(pad.dir.isZero() ? p.facing : pad.dir).nor();

        if (pad.pressed && !winding && !tackling) press(m, p, pad);
        if (tackling) tackle(m, p, pad, dt);
        showing = false;
        if (winding) wind(m, p, pad, dt);
        if (!dead && !winding && p.sole != Player.Sole.ON && m.owner != p && m.ball.pos.y < SETTLE && m.canTouch(p)) m.control(p);
        if (!showing && !winding && (dead || m.owner == p || m.canKick(p))) showTouch(m, p, pad);
        boolean redirecting = p.reaching() && p.act != Player.Act.DIVE;
        arrow = redirecting ? p.redirect : dead && !charging() ? aim : null;
        hint = hint(m, p);
        showContact = liftButton && (winding || m.owner == p || dead) && m.ball.dist2D(p.pos) < 2.5f;
        if (showContact) placeContact(m, winding ? kickDir : aim);
    }

    /** El punto donde le va a pegar (con botón de elevación): sobre la cara de atrás de la pelota, más abajo cuanto más se mantuvo C, y al costado contrario de la comba. */
    private void placeContact(Match m, Vector2 towards) {
        float fx = -curl / MAX_CURL * 0.7f, fy = (1 - 2 * lowered) * 0.9f, l2 = Math.min(fx * fx + fy * fy, 0.95f);
        float rx = -towards.y, rz = towards.x;
        contact.set(-towards.x, 0, -towards.y).scl((float) Math.sqrt(1 - l2)).add(rx * fx, fy, rz * fx).nor()
                .scl(Ball.RADIUS * 1.02f).add(m.ball.pos);
    }

    /** Qué hacen los botones ahora, para la pantalla. */
    private String hint(Match m, Player p) {
        if (liftButton) {
            String lift = String.format("elevación %.0f%%", lowered * 100);
            if (charging() && frozen >= 0) return "Fuerza fija (" + lift + "): flechas de costado = comba · C la sube más · soltá X para patear";
            if (charging()) return "Cargando fuerza (" + lift + "): soltá X para patear · una flecha de costado la deja fija y da comba · C la sube";
            if (m.owner == p && p.sole != Player.Sole.ON) return "C mantenido: sale más alta (" + lift + ") · X: pase o tiro";
        }
        if (charging() && frozen >= 0) return "Fuerza fija: las flechas le dan forma (atrás alto, adelante bajo, costados comba) · soltá X para patear";
        if (charging()) return "Cargando fuerza: soltá X para patear · tocar una flecha deja la fuerza fija y le da forma · Z cancela y la pisa";
        if (p.sole == Player.Sole.ON && m.owner == p) return "Suela: las flechas mueven el pie y la pelota va con él · X le pega · soltar Z la suelta";
        if (p.sole == Player.Sole.ON) return "Esperándola con la suela: con una flecha la acomoda para ese lado";
        if (m.containing) return "Conteniendo: de frente a la pelota, despacio y con las piernas abiertas";
        if (tackling) return "Soltar X: puntazo · mantener: barrida";
        return null;
    }

    // ---- la suela ------------------------------------------------------------------------------

    /**
     * Z con la pelota. Si al apretarlo hay flechas nuevas (distintas de hacia donde corre), la suela
     * se apoya enseguida y el pie va para ahí. Si no, queda armada un momento: si Z se suelta antes,
     * fue un amague; si se mantiene, la pisa.
     */
    private void sole(Match m, Player p, Pad pad, float dt) {
        switch (p.sole) {
            case OFF -> {
                if (pad.zPressed && !dead && !winding && m.soleContext(p)) {
                    p.soleTime = 0;
                    if (m.owner != p || newDirection(pad, p)) putSole(m, p);
                    else p.sole = Player.Sole.ARMED;
                }
            }
            case ARMED -> {
                p.soleTime += dt;
                if (!pad.zDown) {
                    p.sole = Player.Sole.OFF;
                    p.foot.startFeint();
                } else if (m.owner != p) {
                    p.sole = Player.Sole.OFF;  // se la sacaron
                } else if (newDirection(pad, p) || p.soleTime >= ARM) {
                    putSole(m, p);
                }
            }
            case ON -> {
                if (!pad.zDown || m.setPiece != null || p.busy()) {
                    p.releaseSole();
                } else if (m.owner != p) {
                    // Recibiendo con la suela: la toma cuando llega, a la altura que venga; si ya no viene, no hay nada que pisar.
                    if (m.canTouch(p)) m.control(p);
                    else if (m.owner != null || m.passTarget != p && !m.ballArriving(p, 0.5f)) p.releaseSole();
                }
            }
        }
        if (p.sole == Player.Sole.ON) p.foot.drive(p, pad.dir, dt);
        else if (p.foot.feint > 0) p.foot.feint(p, m.ball.pos, dt);
    }

    /** La suela se apoya: sobre la pelota si la tiene; si no, la toma si está al alcance o la espera. */
    private void putSole(Match m, Player p) {
        p.sole = Player.Sole.ON;
        p.behind = 0;
        if (m.owner == p) p.foot.onBall(m.ball);
        else if (m.canTouch(p)) m.control(p);
    }

    /** Flechas que no son las de seguir corriendo: hacia otro lado, o cualquiera si está casi quieto. */
    private static boolean newDirection(Pad pad, Player p) {
        if (pad.dir.isZero()) return false;
        float speed = p.vel.len();
        if (speed < 1.5f) return true;
        float cos = (pad.dir.x * p.vel.x + pad.dir.y * p.vel.y) / (pad.dir.len() * speed);
        return cos < MathUtils.cosDeg(35f);
    }

    /** Con la suela, el cuerpo camina hacia el pie si se aleja, si no se frena; si la pelota quedó atrás, gira hacia ella. */
    private void followFoot(Player p, float dt) {
        Foot f = p.foot;
        tmp.set(f.pos.x - p.pos.x, f.pos.z - p.pos.y);
        float d = tmp.len();
        if (d > FOLLOW) tmp.setLength(Math.min(SOLE_WALK, (d - FOLLOW) * 6f));
        else tmp.setZero();
        p.steer(tmp, 15f);
        p.facingHeld = true;
        // Si el pie queda atrás un rato, gira hasta mirarlo.
        if (f.forward(p) < -0.15f) p.behind += dt;
        else if (p.behind <= TURN_AFTER) p.behind = 0;
        if (p.behind > TURN_AFTER && d > 0.1f) {
            tmp.set(f.pos.x - p.pos.x, f.pos.z - p.pos.y).nor();
            float a = MathUtils.atan2(p.facing.crs(tmp), p.facing.dot(tmp));
            p.facing.rotateRad(MathUtils.clamp(a, -TURN * dt, TURN * dt));
            if (Math.abs(a) < 0.15f) p.behind = 0;
        }
    }

    // ---- el cuerpo -----------------------------------------------------------------------------

    private void move(Match m, Player p, Pad pad) {
        boolean newInput = !pad.dir.isZero() && !pad.dir.epsilonEquals(passDir, 0.01f);
        if (m.passTarget == p && !newInput) {
            intercept(m, p);
        } else if (!pad.dir.isZero()) {
            p.steer(tmp.set(pad.dir).scl(m.owner == p ? Player.RUN_WITH_BALL : Player.RUN));
        } else {
            p.steer(tmp.setZero());
        }
    }

    /** Va adonde la encuentra; si le cae ahí nomás (un jueguito), se queda. */
    private void intercept(Match m, Player p) {
        m.intercept(p, Player.RUN, tmp);
        if (tmp.dst(p.pos) < 0.8f) p.steer(tmp.setZero());
        else p.moveTo(tmp.x, tmp.y, Player.RUN);
    }

    // ---- el golpe ------------------------------------------------------------------------------

    private void press(Match m, Player p, Pad pad) {
        // con la pelota al pie, por llegar, o bajando hacia él (el jueguito: apretar un poco antes la espera, no cabecea)
        if (dead || m.canKick(p) || m.ballArriving(p, 0.25f) || m.ballFalling(p, 0.7f) || p.sole == Player.Sole.ON && m.owner == p) {
            if (p.sole != Player.Sole.OFF) p.releaseSole();
            winding = true;
            released = tapped = false;
            since = held = waited = 0;
            kickDir.set(aim);
            aimed = !pad.dir.isZero();
            pressDir.set(pad.dir);
            pressHeld = aimed;
            frozen = -1;
            // para atrás de hacia donde mira: un taco (no se da vuelta, le pega con el talón)
            heel = !dead && aimed && kickDir.dot(p.facing) < -0.5f;
            pressFacing.set(p.facing);
            lift = curl = 0;
        } else if (m.canHead(p)) {
            m.startHeader(p);
        } else if (!p.busy()) {
            tackling = true;
            since = 0;
        }
    }

    /** Sin la pelota: si suelta X enseguida, puntazo; si lo mantiene, barrida. */
    private void tackle(Match m, Player p, Pad pad, float dt) {
        since += dt;
        if (p.busy()) {
            tackling = false;
        } else if (!pad.down) {
            tackling = false;
            m.poke(p);
        } else if (since >= SLIDE_AFTER) {
            tackling = false;
            p.startSlide(pad.dir.isZero() ? p.facing : pad.dir);
        }
    }

    /** Z mantenido sin la pelota: de frente a la pelota, a lo sumo a paso de contener, con las piernas abiertas. */
    private void contain(Match m, Player p, Pad pad) {
        p.steer(tmp.set(pad.dir).scl(CONTAIN_SPEED));
        tmp.set(m.ball.pos.x - p.pos.x, m.ball.pos.z - p.pos.y);
        if (!tmp.isZero(0.05f)) p.facing.set(tmp).nor();
        p.facingHeld = true;
        p.containing = true;
    }

    /** Apretó con la pelota al pie (o por llegar): mientras lo mantiene carga y le da forma; al soltar, toca o patea. */
    private void wind(Match m, Player p, Pad pad, float dt) {
        if (pad.zPressed && charging() && !dead) {
            // Z cancela el tiro: vuelve a la suela.
            winding = false;
            p.soleTime = 0;
            putSole(m, p);
            return;
        }
        since += dt;
        if (!released) {
            if (!pad.down || since >= MAX_HOLD) {
                released = true;
                // lo que se mantuvo hasta el último paso con el botón apretado: lo que mostraba la flecha
                held = frozen >= 0 ? frozen : pad.down ? since : since - dt;
                tapped = held < TAP;
            } else if (since >= TAP) {
                shapeInput(pad);
                if (frozen < 0 && shaping()) frozen = since;  // tocó una flecha: la fuerza queda donde estaba
                shape(m, p, shapeDir, dt);
                show(m, p);
            }
        }
        if (!released) return;
        if (!dead && (!m.canKick(p) || tapped && m.waitForFoot(p))) {
            if ((waited += dt) > WAIT) winding = false;  // la pelota no llegó
            return;
        }
        winding = false;
        strike(m, p, pad);
        lowered = 0;  // el próximo golpe vuelve a partir de arriba de la pelota
    }

    /** Le pega: un toque (pase o toque) o el golpe cargado. */
    private void strike(Match m, Player p, Pad pad) {
        // con botón de elevación, un toque con el punto ya bajado es un globito suave (no un pase)
        boolean chip = liftButton && lowered > 0.1f && !dead;
        if (tapped && !chip) {
            passDir.set(pad.dir);
            if (dead) {
                m.pass(p, kickDir);
            } else {
                if (heel) {
                    p.facing.set(pressFacing);
                    p.heel = Player.KICK_ANIM;
                }
                m.touchBall(p, kickDir, aimed);
                if (heel) p.kickAnim = 0;
            }
        } else {
            launch(m, p, held);
            m.kick(p, kickDir, (float) Math.sqrt(vel.x * vel.x + vel.z * vel.z), vel.y, curl, backspin());
            m.markReceiver(p);
        }
    }

    /**
     * Las flechas, mirando hacia donde va el tiro: atrás lo sube, adelante lo baja, los costados le
     * dan comba. Lo que sube o baja se suma a la altura con que le pega la parte del cuerpo que la
     * agarre, que puede cambiar mientras se carga (una pelota que cae del pecho al pie).
     */
    private void shape(Match m, Player p, Vector2 dir, float dt) {
        if (dir.len2() < 0.25f) return;
        float len = dir.len(), base = base(m, p);
        float along = dir.dot(kickDir) / len, side = (dir.x * -kickDir.y + dir.y * kickDir.x) / len;
        if (liftButton) along = 0;  // la altura la da C
        if (along < -0.3f) lift = Math.min(HIGHEST + 25f - base, lift - RAISE * along * dt);
        else if (along > 0.3f) lift = Math.max(LOWEST - base, lift - LOWER * along * dt);
        if (!throwIn && Math.abs(side) > 0.3f) curl = MathUtils.clamp(curl + Math.signum(side) * BEND * dt, -MAX_CURL, MAX_CURL);
    }

    /**
     * Las flechas que dan forma: todas, salvo la que ya estaba apretada al apretar X (para dónde) mientras
     * se la siga apretando; si se aprieta otra junto con ella, cuenta sólo la nueva. Soltada esa, cualquiera.
     */
    private void shapeInput(Pad pad) {
        shapeDir.set(pad.dir);
        if (!pressHeld) return;
        float along = shapeDir.dot(pressDir) / Math.max(pressDir.len2(), 1e-6f);
        if (along > 0.5f) shapeDir.mulAdd(pressDir, -along);
        else pressHeld = false;
    }

    /** Si las flechas están dando forma (y entonces la fuerza queda fija): con botón de elevación, sólo las de costado. */
    private boolean shaping() {
        if (shapeDir.len2() <= 0.25f) return false;
        if (!liftButton) return true;
        float side = (shapeDir.x * -kickDir.y + shapeDir.y * kickDir.x) / shapeDir.len();
        return Math.abs(side) > 0.3f;
    }

    /** Cuánto se cargó: hasta que quedó fija, o hasta ahora. */
    private float strength() {
        return frozen >= 0 ? frozen : since;
    }

    /** La altura con que sale sin flechas: la del lateral, la del arquero con la mano, o la de la parte del cuerpo que le pega. */
    private float base(Match m, Player p) {
        return throwIn ? 25f : m.holder == p ? 35f : m.startElevation(p);
    }

    /**
     * De 0 a 1 según cuánto se mantuvo. Sube despacio al principio y rápido al final: los toques
     * suaves (un globito, un sombrero) se pueden dosificar sin que tarde más el tiro fuerte.
     */
    private float power(float time) {
        float k = MathUtils.clamp((time - TAP) / FILL, 0, 1);
        return k * (float) Math.sqrt(k);
    }

    /** Un poco de efecto según la altura: alto flota, rasante cae. */
    private float backspin() {
        return (Math.min(elevation, HIGHEST) - 10f) * 0.4f;
    }

    /** Con qué velocidad saldría la pelota si se suelta ahora. Desde el piso, cuanto más alta, menos fuerte; en el aire no. */
    private void launch(Match m, Player p, float time) {
        float power = power(time);
        elevation = liftButton && !throwIn ? MathUtils.lerp(LOWEST, LIFT_TOP, lowered) : MathUtils.clamp(base(m, p) + lift, LOWEST, HIGHEST + 25f);
        Match.Part part = m.reach(p);
        boolean fromGround = part == null || part == Match.Part.GROUND;
        float loft = fromGround ? 1 - LOFT_COST * Math.min(elevation, HIGHEST) / HIGHEST : 1;
        // lo alto le baja el tope, no el mínimo: un globito suave sale con la fuerza más floja igual
        float top = Math.max(SOFT, HARD * loft);
        float speed = throwIn ? 8f + 9f * power : SOFT + (top - SOFT) * power + (m.holder != null ? 2f : 0);
        float e = elevation * MathUtils.degRad, hs = speed * MathUtils.cos(e);
        vel.set(kickDir.x * hs, speed * MathUtils.sin(e), kickDir.y * hs);
    }

    /** Lo que se mostraría si se suelta ahora: los valores y por dónde iría la pelota. */
    private void show(Match m, Player p) {
        launch(m, p, strength());
        float back = backspin();
        spin.set(-kickDir.y * back, -curl, kickDir.x * back);
        m.path(m.ball.pos, vel, spin, shot);
        shot.pass = false;
        shot.receiver = null;
        shot.label = null;
        shot.power = power(strength());
        shot.powerFixed = frozen >= 0;
        shot.elevation = elevation;
        shot.curl = curl;
        showing = true;
    }

    /** Con la pelota al pie, sin apretar: el toque que saldría, y a quién. */
    private void showTouch(Match m, Player p, Pad pad) {
        if (liftButton && lowered > 0.1f && !dead) {
            // con el punto bajado, un toque es un globito suave: se muestra ése
            kickDir.set(aim);
            curl = 0;
            launch(m, p, 0);
            float back = backspin();
            m.path(m.ball.pos, vel, spin.set(-kickDir.y * back, 0, kickDir.x * back), shot);
            shot.pass = true;
            shot.receiver = null;
            shot.label = "Globito";
            shot.power = 0;
            shot.elevation = elevation;
            shot.curl = 0;
            showing = true;
            return;
        }
        shot.receiver = dead ? m.planPass(p, aim, vel) : m.planTouch(p, aim, !pad.dir.isZero(), vel);
        m.path(m.ball.pos, vel, spin.setZero(), shot);
        shot.pass = true;
        shot.label = dead || shot.receiver != null ? null : m.touchLabel(p);
        shot.power = 0;
        float hs = (float) Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        shot.elevation = MathUtils.atan2(vel.y, hs) * MathUtils.radDeg;
        shot.curl = 0;
        showing = true;
    }
}
