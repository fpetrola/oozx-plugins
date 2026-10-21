package com.fpetrola.proto.soccer;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

/**
 * El control, empezado de nuevo y chico: un golpe a la pelota armado con dos botones y las flechas.
 * <ul>
 *   <li><b>Flechas:</b> mover; la pelota va pegada al pie.</li>
 *   <li><b>X (la cruz del joystick):</b> le pega en el medio de la pelota: sale baja, sin efecto. Lo
 *       que dura apretado es la potencia.</li>
 *   <li><b>C (el cuadrado):</b> le pega abajo: sale alta, con efecto para atrás. También lo que dura
 *       apretado es la potencia.</li>
 *   <li><b>Mientras X o C están apretados:</b> las flechas mueven hacia dónde va la pelota: la flecha
 *       del piso gira hacia la tecla apretada, a cualquiera de los 360 grados (con el stick de un
 *       joystick, va directo para donde apunta el stick). El jugador va girando
 *       detrás, más despacio, pisando alrededor de la pelota. Si se le pega antes de que llegue a
 *       ponerse de frente, el pie no alcanza a sacarla derecho para ahí: sale abierta y con la comba
 *       que haga falta para llegar (y si ni con toda la comba llega, se queda corta de ángulo).</li>
 *   <li>El golpe sale al soltar (si se apretaron los dos, al soltar los dos; si en algún momento se
 *       apretó C, le pega abajo). No hay tiempo máximo.</li>
 *   <li>También se puede armar antes de tener la pelota (suelta, o que viene): el jugador va solo a
 *       buscarla y, apenas la toca, le pega de primera con lo armado (aunque los botones sigan
 *       apretados). Si la agarra un rival, o pasan unos segundos sin llegar después de soltar, se
 *       cancela.</li>
 * </ul>
 * La flecha del tiro muestra el recorrido que haría la pelota si se soltara ahora: la altura y la
 * curva en la forma, la potencia en el color (de amarillo a rojo).
 */
final class GolpeScheme implements ControlScheme {
    /** Velocidad de salida: la más floja y la más fuerte (m/s). */
    private static final float SOFT = 4f, HARD = 32f;
    /** Efecto para atrás pegándole abajo (rad/s). */
    private static final float SPIN = 30f;
    /** Comba: hasta cuánto (rad/s, positivo curva a la derecha); más que en el juego de verdad, para que alcance a cerrar lo que el pie no llega. */
    private static final float MAX_CURL = 150f;
    /** Hasta cuántos grados de para donde mira el pie la saca derecha; más abierto, tiene que darle comba. */
    private static final float CONE = 30f;
    /**
     * Lo que se ajusta en vivo para probar qué se siente mejor: cuánto tarda en llenarse la potencia
     * (s), con qué altura sale (grados) pegándole en el medio y abajo, y qué tan rápido giran la flecha
     * y el jugador detrás (grados por segundo).
     */
    float fill = 0.6f, middle = 10f, under = 40f, turn = 180f, body = 120f;
    /** Si en este golpe se apretó C: le pega abajo. */
    private boolean low;
    /** Sin correr (con el joystick, R1 suelto), a qué parte de la velocidad va. */
    private static final float JOG = 0.7f;
    /** Cuánto espera, armado antes de tener la pelota y ya soltado, a llegar a tocarla (s). */
    private static final float READY = 2f;
    /** Si se empezó a armar sin la pelota (de primera), y cuánto lleva sin ningún botón apretado. */
    private boolean early;
    private float idle;

    /** Cuánto se cargó la potencia (0 a 1), dónde le pega (0 arriba de todo, 0,5 en el medio, 1 abajo de todo; para el punto rojo) y la comba. */
    float power, lowered, curl;
    /** Si se está armando un golpe: desde que se apretó X o C hasta que se sueltan los dos. */
    private boolean arming;
    /** Hacia dónde se quiere mandar la pelota (la flecha del piso), y hacia dónde sale del pie. */
    private final Vector2 aim = new Vector2(1, 0), launch = new Vector2(1, 0);
    private final Vector2 tmp = new Vector2(), tmp2 = new Vector2();
    /** Para probar combas sin tocar la flecha que se muestra. */
    private final ShotPreview trial = new ShotPreview();
    private final Vector3 vel = new Vector3(), spin = new Vector3(), contact = new Vector3();
    private final ShotPreview shot = new ShotPreview();
    private boolean showShot, showContact, dead;
    private Player player;

    @Override
    public String name() {
        return "Golpe: potencia, altura y comba";
    }

    @Override
    public String help() {
        return "Flechas: mover · X: le pega al medio (baja) · C: le pega abajo (alta) · lo que dura apretado es la potencia\n"
                + "Con X o C apretado, las flechas eligen para dónde va (la comba sale sola si no está de frente) · el golpe sale al soltar los dos · Z: el compañero que llega primero\n"
                + "Joystick: stick izq. mover y apuntar · cruz: al medio · cuadrado: abajo · R1: correr · L1: cambiar · stick der.: cámara · L2/R2: zoom · triángulo: otra cámara";
    }

    @Override
    public boolean usesZ() {
        return false;
    }

    @Override
    public ShotPreview shot() {
        return showShot ? shot : null;
    }

    @Override
    public Vector2 aim() {
        return dead || arming ? aim : null;
    }

    @Override
    public Vector3 contact() {
        return showContact ? contact : null;
    }

    @Override
    public String hint() {
        if (!arming) return null;
        return String.format("%s · potencia %.0f%% · altura %.0f grados · %s · soltá para pegarle", low ? "Abajo" : "Al medio", power * 100, elevation(),
                Math.abs(curl) < 1 ? "de frente, sin comba" : String.format("de costado: comba %.0f%s", Math.abs(curl), Math.abs(curl) >= MAX_CURL ? ", no llega" : ""));
    }

    @Override
    public void update(Match m, Player p, Pad pad, float dt) {
        if (p != player) {
            player = p;
            arming = false;
            reset();
        }
        SetPiece sp = m.setPiece;
        dead = sp != null && sp.taker == p || m.holder == p;
        boolean pressing = pad.down || pad.cDown;

        boolean theirs = m.owner != null && m.owner.team != p.team;
        if (!arming && pressing && (dead || m.owner == p || m.owner == null && m.setPiece == null)) {
            // empieza a armar el golpe: queda fijo para dónde sale; sin la pelota, es para pegarle de primera
            arming = true;
            early = !dead && m.owner != p;
            low = false;
            idle = 0;
            aim.set(dead ? aim : p.facing);
        }

        if (arming) {
            if (pressing) power = Math.min(1, power + dt / fill);
            if (pad.cDown) low = true;
            lowered = low ? 0.85f : 0.5f;
            if (pressing) {
                // las flechas llevan la flecha del piso hacia ellas, a cualquier lado
                if (pad.analog && pad.dir.len2() > 0.25f) {
                    aim.set(pad.dir).nor();  // con el stick, la flecha va justo para donde apunta
                } else if (!pad.dir.isZero()) {
                    turnTowards(aim, tmp.set(pad.dir).nor(), turn * dt);
                }
                if (!pad.dir.isZero()) {
                    if (sp != null && sp.taker == p) {
                        sp.aim.set(aim);
                        sp.clampAim(sp.aim);
                        aim.set(sp.aim);
                    }
                }
                idle = 0;
            } else {
                idle += dt;
            }
            // el cuerpo va detrás, más despacio; corriendo a buscarla, mira para donde corre
            p.facingHeld = !early;
            if (!early) turnTowards(p.facing, aim, body * dt);
            if (early) {
                // de primera: va solo a buscarla y le pega apenas la toca; si se la lleva un rival o no llega, nada
                m.intercept(p, Player.RUN, tmp);
                p.moveTo(tmp.x, tmp.y, Player.RUN);
                if (theirs || m.setPiece != null || !pressing && idle > READY) cancel();
                else if (m.canKick(p)) strike(m, p);
            } else {
                if (dead) p.stop();
                else p.steer(tmp.set(p.vel).scl(1 - 2f * dt));  // armándolo no corre: sigue con el envión y frena
                if (!pressing) strike(m, p);  // sale al soltar
            }
        } else if (dead) {
            // parado con la pelota: las flechas apuntan
            p.stop();
            if (sp != null && sp.taker == p) {
                if (!pad.dir.isZero()) {
                    sp.aim.set(pad.dir).nor();
                    sp.clampAim(sp.aim);
                }
                aim.set(sp.aim);
            } else if (!pad.dir.isZero()) {
                aim.set(pad.dir).nor();
            }
            p.facing.set(aim);
        } else {
            p.steer(tmp.set(pad.dir).scl(pad.sprint ? Player.RUN : Player.RUN * JOG));
            // la agarra si le llega al pie (baja): desde ahí la lleva pegada
            if (m.owner != p && m.ball.pos.y < 0.5f && m.canTouch(p)) m.control(p);
        }

        showShot = arming;
        if (arming) {
            plan(m, p);
            m.path(m.ball.pos, vel, spin, shot);
            shot.pass = false;
            shot.receiver = null;
            shot.label = null;
            shot.power = power;
            shot.powerFixed = false;
            shot.elevation = elevation();
            shot.curl = curl;
        }
        showContact = (arming || m.owner == p || dead) && m.ball.dist2D(p.pos) < 2.5f;
        if (showContact) placeContact(m, arming ? launch : dead ? aim : p.facing);
    }

    /** Los valores que se ajustan en vivo, para la pantalla. */
    String settings() {
        return String.format("potencia llena en %.1f s (F1 / F2) · abajo sale a %.0f grados (F3 / F4) · la flecha gira %.0f grados por s (F5 / F6)"
                + " · al medio sale a %.0f grados (F7 / F8) · el jugador gira %.0f grados por s (F9 / F10)", fill, under, turn, middle, body);
    }

    /** Se deja de armar sin pegarle. */
    private void cancel() {
        arming = false;
        reset();
    }

    /** Le pega con lo armado, si la tiene al pie, la está tocando de primera, o es una pelota parada suya. */
    private void strike(Match m, Player p) {
        arming = false;
        if (dead || m.owner == p || early && m.canKick(p)) {
            plan(m, p);
            float hs = (float) Math.sqrt(vel.x * vel.x + vel.z * vel.z);
            tmp.set(vel.x, vel.z).scl(1 / Math.max(hs, 0.001f));
            m.kick(p, tmp, hs, vel.y, curl, backspin());
            m.markReceiver(p);
        }
        reset();
    }

    /** El próximo golpe empieza de cero: sin potencia, pegándole arriba de todo, sin comba. */
    private void reset() {
        power = curl = 0;
        lowered = 0.5f;
        low = false;
    }

    private float elevation() {
        return low ? under : middle;
    }

    /** Efecto sobre el eje horizontal: pegándole abajo, para atrás (la levanta); en el medio, nada. */
    private float backspin() {
        return low ? SPIN : 0;
    }

    /**
     * Cómo saldría la pelota si se soltara ahora: deja la velocidad en {@link #vel} y el giro en
     * {@link #spin}. Si la flecha está a más de {@link #CONE} de para donde mira, sale por el borde de
     * lo que alcanza el pie, con la comba que la lleva a pasar por la flecha donde termina el recorrido.
     */
    private void plan(Match m, Player p) {
        float a = angle(p.facing, aim);
        if (Math.abs(a) <= CONE) {
            launch.set(aim);
            curl = 0;
        } else {
            launch.set(p.facing).nor().rotateDeg(Math.signum(a) * CONE);
            float lo = -MAX_CURL, hi = MAX_CURL, mlo = miss(m, lo), mhi = miss(m, hi);
            if (Math.signum(mlo) == Math.signum(mhi)) {
                curl = Math.abs(mlo) < Math.abs(mhi) ? lo : hi;  // ni con toda la comba llega: lo más que se pueda
            } else {
                for (int i = 0; i < 12; i++) {
                    float mid = (lo + hi) / 2, mm = miss(m, mid);
                    if (Math.signum(mm) == Math.signum(mlo)) {
                        lo = mid;
                        mlo = mm;
                    } else {
                        hi = mid;
                    }
                }
                curl = (lo + hi) / 2;
            }
        }
        setUp(curl);
    }

    /** La velocidad y el giro saliendo por {@link #launch} con esa comba. */
    private void setUp(float c) {
        float speed = SOFT + (HARD - SOFT) * power * (float) Math.sqrt(power);
        float e = elevation() * MathUtils.degRad, hs = speed * MathUtils.cos(e), back = backspin();
        vel.set(launch.x * hs, speed * MathUtils.sin(e), launch.y * hs);
        spin.set(-launch.y * back, -c, launch.x * back);
    }

    /** Con esa comba, cuántos grados de la flecha termina el recorrido (con signo). */
    private float miss(Match m, float c) {
        setUp(c);
        m.path(m.ball.pos, vel, spin, trial);
        int last = trial.count - 1;
        tmp2.set(trial.xyz[last * 3] - m.ball.pos.x, trial.xyz[last * 3 + 2] - m.ball.pos.z);
        return angle(aim, tmp2);
    }

    /** Cuántos grados hay que girar {@code from} para llegar a {@code to} (positivo, contra el reloj). */
    private static float angle(Vector2 from, Vector2 to) {
        return MathUtils.atan2(from.crs(to), from.dot(to)) * MathUtils.radDeg;
    }

    /** Gira {@code v} hacia {@code to}, a lo sumo {@code max} grados; justo al revés, siempre para el mismo lado. */
    private static void turnTowards(Vector2 v, Vector2 to, float max) {
        float a = angle(v, to);
        if (Math.abs(a) > 179.5f) a = 179.5f;
        v.rotateDeg(MathUtils.clamp(a, -max, max)).nor();
    }

    /** El punto rojo sobre la pelota: en la cara de atrás, más abajo cuanto más se bajó, y al costado contrario de la comba. */
    private void placeContact(Match m, Vector2 towards) {
        float fx = -curl / MAX_CURL * 0.7f, fy = (1 - 2 * lowered) * 0.9f, l2 = Math.min(fx * fx + fy * fy, 0.95f);
        float rx = -towards.y, rz = towards.x;
        contact.set(-towards.x, 0, -towards.y).scl((float) Math.sqrt(1 - l2)).add(rx * fx, fy, rz * fx).nor()
                .scl(Ball.RADIUS * 1.02f).add(m.ball.pos);
    }
}
