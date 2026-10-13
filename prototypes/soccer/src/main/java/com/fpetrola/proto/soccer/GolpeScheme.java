package com.fpetrola.proto.soccer;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

/**
 * El control, empezado de nuevo y chico: un golpe a la pelota armado con dos botones y las flechas.
 * <ul>
 *   <li><b>Flechas:</b> mover; la pelota va pegada al pie.</li>
 *   <li><b>X, mantenido:</b> la potencia, que sube mientras se lo mantiene.</li>
 *   <li><b>C, mantenido:</b> baja el punto donde se le pega, de arriba de todo de la pelota (sale
 *       rasante, con efecto para adelante) a abajo de todo (sale bien alta, con efecto para atrás).</li>
 *   <li><b>Mientras X o C están apretados:</b> el jugador no gira, y la flecha a la izquierda o a la
 *       derecha de hacia donde apunta le va sumando comba para ese lado (para el otro, se la saca).</li>
 *   <li>El golpe sale cuando se sueltan los dos. No hay tiempo máximo.</li>
 * </ul>
 * La flecha del tiro muestra el recorrido que haría la pelota si se soltara ahora: la altura y la
 * curva en la forma, la potencia en el color (de amarillo a rojo).
 */
final class GolpeScheme implements ControlScheme {
    /** Velocidad de salida: la más floja y la más fuerte (m/s). */
    private static final float SOFT = 4f, HARD = 32f;
    /** La altura de salida (grados) pegándole arriba de todo y abajo de todo. */
    private static final float LOW = 6f, HIGH = 75f;
    /** Efecto sobre el eje horizontal en las puntas (rad/s): para adelante pegándole arriba, para atrás pegándole abajo. */
    private static final float SPIN = 30f;
    /** Comba: hasta cuánto (rad/s, positivo curva a la derecha). */
    private static final float MAX_CURL = 70f;
    /**
     * Lo que se ajusta en vivo para probar qué se siente mejor: cuánto tarda en llenarse la potencia
     * (s), cuánto tarda el punto en bajar de arriba de todo a abajo de todo (s), y cuánta comba suma
     * por segundo la flecha de costado (rad/s²).
     */
    float fill = 0.6f, sweep = 1f, bend = 350f;

    /** Cuánto se cargó la potencia (0 a 1), cuánto bajó el punto (0 arriba de todo, 1 abajo de todo) y la comba. */
    float power, lowered, curl;
    /** Si se está armando un golpe: desde que se apretó X o C hasta que se sueltan los dos. */
    private boolean arming;
    /** Hacia dónde sale: queda fijo al empezar a armarlo. */
    private final Vector2 aim = new Vector2(1, 0);
    private final Vector2 tmp = new Vector2();
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
        return "Flechas: mover · X mantenido: potencia · C mantenido: le pega más abajo (sale más alta)\n"
                + "Con X o C apretado, flecha a la izquierda o derecha: comba para ese lado · el golpe sale al soltar los dos · Z: el compañero que llega primero";
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
        return dead ? aim : null;
    }

    @Override
    public Vector3 contact() {
        return showContact ? contact : null;
    }

    @Override
    public String hint() {
        if (!arming) return null;
        return String.format("Potencia %.0f%% · altura %.0f grados · comba %.0f%s · soltá X y C para pegarle", power * 100, elevation(),
                Math.abs(curl), curl > 1 ? " a la derecha" : curl < -1 ? " a la izquierda" : "");
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

        if (!arming && pressing && (dead || m.owner == p)) {
            // empieza a armar el golpe: queda fijo para dónde sale
            arming = true;
            aim.set(dead ? aim : p.facing);
        }

        if (arming) {
            if (pad.down) power = Math.min(1, power + dt / fill);
            if (pad.cDown) lowered = Math.min(1, lowered + dt / sweep);
            float side = pad.dir.x * -aim.y + pad.dir.y * aim.x;  // la flecha, de costado respecto de para dónde sale
            curl = MathUtils.clamp(curl + side * bend * dt, -MAX_CURL, MAX_CURL);
            p.facingHeld = true;
            if (dead) p.stop();
            else p.steer(tmp.set(p.vel).scl(1 - 2f * dt));  // armándolo no corre: sigue con el envión y frena
            if (!pressing) strike(m, p);
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
            p.steer(tmp.set(pad.dir).scl(Player.RUN));
            // la agarra si le llega al pie (baja): desde ahí la lleva pegada
            if (m.owner != p && m.ball.pos.y < 0.5f && m.canTouch(p)) m.control(p);
        }

        showShot = arming;
        if (arming) {
            plan();
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
        if (showContact) placeContact(m, arming || dead ? aim : p.facing);
    }

    /** Los valores que se ajustan en vivo, para la pantalla. */
    String settings() {
        return String.format("potencia llena en %.1f s (F1 / F2) · altura de arriba a abajo en %.1f s (F3 / F4) · comba %.0f por s (F5 / F6)",
                fill, sweep, bend);
    }

    /** Suelta los dos: le pega con lo armado, si la tiene al pie (o es una pelota parada suya). */
    private void strike(Match m, Player p) {
        arming = false;
        if (dead || m.owner == p) {
            plan();
            float hs = (float) Math.sqrt(vel.x * vel.x + vel.z * vel.z);
            tmp.set(vel.x, vel.z).scl(1 / Math.max(hs, 0.001f));
            m.kick(p, tmp, hs, vel.y, curl, backspin());
            m.markReceiver(p);
        }
        reset();
    }

    /** El próximo golpe empieza de cero: sin potencia, pegándole arriba de todo, sin comba. */
    private void reset() {
        power = lowered = curl = 0;
    }

    private float elevation() {
        return MathUtils.lerp(LOW, HIGH, lowered);
    }

    /** Efecto sobre el eje horizontal: positivo la levanta (le pega abajo), negativo la baja (le pega arriba). */
    private float backspin() {
        return (2 * lowered - 1) * SPIN;
    }

    /** Cómo saldría la pelota si se soltara ahora: deja la velocidad en {@link #vel} y el giro en {@link #spin}. */
    private void plan() {
        float speed = SOFT + (HARD - SOFT) * power * (float) Math.sqrt(power);
        float e = elevation() * MathUtils.degRad, hs = speed * MathUtils.cos(e), back = backspin();
        vel.set(aim.x * hs, speed * MathUtils.sin(e), aim.y * hs);
        spin.set(-aim.y * back, -curl, aim.x * back);
    }

    /** El punto rojo sobre la pelota: en la cara de atrás, más abajo cuanto más se bajó, y al costado contrario de la comba. */
    private void placeContact(Match m, Vector2 towards) {
        float fx = -curl / MAX_CURL * 0.7f, fy = (1 - 2 * lowered) * 0.9f, l2 = Math.min(fx * fx + fy * fy, 0.95f);
        float rx = -towards.y, rz = towards.x;
        contact.set(-towards.x, 0, -towards.y).scl((float) Math.sqrt(1 - l2)).add(rx * fx, fy, rz * fx).nor()
                .scl(Ball.RADIUS * 1.02f).add(m.ball.pos);
    }
}
