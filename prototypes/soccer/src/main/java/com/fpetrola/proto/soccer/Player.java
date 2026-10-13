package com.fpetrola.proto.soccer;

import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

final class Player {
    static final float RUN = 7f, RUN_WITH_BALL = 6.4f;
    static final float ACCEL = 40f;
    static final float KICK_ANIM = 0.3f;
    /** Metros por paso doble: las piernas van al ritmo de lo que se avanza. */
    private static final float STRIDE = 2.6f;

    /** Lo que hace cuando no está simplemente corriendo. Mientras dura no maneja el rumbo. */
    enum Act {
        NONE(0, 0),
        /** Barrida: desliza y se levanta. */
        SLIDE(0.95f, 0.55f),
        /** Lo voltearon: cae, queda en el piso y se levanta. */
        FALL(1.6f, 0),
        /** Estirada del arquero. */
        DIVE(1.5f, 0.6f),
        /** Cabezazo saltando. */
        HEAD(0.6f, 0.45f),
        /** Palomita. */
        DIVE_HEAD(1.3f, 0.45f),
        /** Puntazo: estira el pie hasta 1,2 m y lo vuelve a traer. */
        POKE(0.4f, 0.25f);

        /** Cuánto dura todo, y hasta cuándo puede tocar la pelota. */
        final float length, reach;

        Act(float length, float reach) {
            this.length = length;
            this.reach = reach;
        }
    }

    final Team team;
    final int number;
    /** Lugar en la formación, mirando hacia el arco rival: a de -1 (arco propio) a 0, z de -1 a 1. */
    final float baseA, baseZ;
    final boolean keeper;
    /** Su lugar en la lista del partido, que es también el de su cuerpo en la física. */
    int index;

    /** Dónde está y cómo va, copiado de la física (en la cancha: x a lo largo, y = z del mundo a lo ancho). */
    final Vector2 pos = new Vector2();
    final Vector2 vel = new Vector2();
    final Vector2 facing = new Vector2(1, 0);
    /** A qué velocidad quiere ir, y cuánto la puede cambiar por segundo: lo que sus piernas sacan del piso. */
    final Vector2 want = new Vector2();
    float grip = ACCEL;
    /** Un empujón de una vez (tirarse, estirarse): la física se lo da en el próximo paso. */
    final Vector2 launch = new Vector2();
    boolean launched;
    /** Hasta que no baje a cero no puede volver a tocar la pelota. */
    float cooldown;
    /** Para la máquina: cuándo vuelve a pensar. */
    float think;
    /** El arquero ya vio venir el tiro, y cuánto se equivoca al leerlo. */
    boolean alert;
    float misread;

    Act act = Act.NONE;
    float actTime;
    /** Ya tocó la pelota en esta acción: no la vuelve a tocar, y lo que choque después no es falta. */
    boolean actTouched;
    /** Estirada: hacia qué lado (+1 derecha, -1 izquierda de hacia donde mira) y a qué altura viene la pelota. */
    float actSide, actHeight;
    /** Hacia dónde manda la pelota si la toca barriéndose o de cabeza; el humano lo cambia mientras dura. */
    final Vector2 redirect = new Vector2(1, 0);
    /** Dónde pararse en una pelota parada. */
    final Vector2 spot = new Vector2();

    /** El pie con el que juega la pelota. */
    final Foot foot = new Foot();

    /**
     * La suela: apagada; armada (se apretó Z y todavía no se sabe si es un amague o una pisada); o
     * apoyada sobre la pelota, que va adonde va el pie.
     */
    enum Sole { OFF, ARMED, ON }

    Sole sole = Sole.OFF;
    float soleTime;
    /** Después de soltar la suela la pelota queda libre un momento: sigue con lo que le dio el pie. */
    float loose;
    /** Recién soltó la suela: el partido frena la pelota (el pie, al levantarse, no le deja toda su velocidad). */
    boolean soleReleased;
    /** Cuánto hace que el pie quedó detrás del cuerpo: si dura, el cuerpo gira hacia la pelota. */
    float behind;
    /** El control decide hacia dónde mira (con la suela, mientras camina de costado o gira). */
    boolean facingHeld;
    /** Afuera de la cancha (los rivales de campo, en la arena de práctica): no juega. */
    boolean benched;

    /** Las piernas: 0 cerradas (corriendo), 1 abiertas (plantado, conteniendo, o recién engañado). */
    float stance;
    /** Conteniendo (Z sin la pelota): de frente a la pelota, despacio y con las piernas abiertas. */
    boolean containing;
    /**
     * La máquina, defendiendo cerca del que lleva la pelota: hacia dónde cree que la lleva el pie, si
     * se jugó a un lado (y cuánto le falta para volver a mirar, y de qué lado), si ya vio que va para
     * un costado, y cuánto le queda con las piernas abiertas por haberse jugado.
     */
    final Vector2 read = new Vector2();
    float commit, committedSide, opened;
    boolean tempted;
    /** Después de dar un pase, cuánto le queda corriendo hacia adelante (la pared) y hacia dónde. */
    float runOn;
    final Vector2 runDir = new Vector2();
    /** Lo que le queda al gesto del taco: le pega con el talón y no se da vuelta. */
    float heel;
    /**
     * La máquina: después de cada patada tarda esto en darse cuenta adónde va la pelota; mientras,
     * sigue yendo adonde iba (y cuándo fue la última vez que la persiguió, y adónde).
     */
    float react, lastChase = -1;
    final Vector2 chaseTo = new Vector2();

    /** Para el muñeco: en qué punto de la zancada va y cuánto le queda al gesto de patear. */
    float stride, kickAnim;

    private final Vector2 tmp = new Vector2();

    Player(Team team, int number, float baseA, float baseZ, boolean keeper) {
        this.team = team;
        this.number = number;
        this.baseA = baseA;
        this.baseZ = baseZ;
        this.keeper = keeper;
    }

    boolean busy() {
        return act != Act.NONE;
    }

    /** En una acción y todavía a tiempo de tocar la pelota con ella. */
    boolean reaching() {
        return act != Act.NONE && actTime < act.reach && !actTouched;
    }

    void start(Act a) {
        act = a;
        actTime = 0;
        actTouched = false;
    }

    void startSlide(Vector2 dir) {
        start(Act.SLIDE);
        facing.set(dir).nor();
        redirect.set(facing);
        launch(facing.x * 9.5f, facing.y * 9.5f);
    }

    /** Puntazo hacia {@code dir}: el pie sale para ahí y el cuerpo acompaña un poco. */
    void startPoke(Vector2 dir) {
        start(Act.POKE);
        redirect.set(dir).nor();
        facing.set(redirect);
        launch(vel.x * 0.5f + redirect.x * 2f, vel.y * 0.5f + redirect.y * 2f);
    }

    /** Dónde está el pie del puntazo: sale del cuerpo, se estira hasta 1,2 m a mitad del gesto y vuelve. */
    Vector3 pokeFoot(Vector3 out) {
        float k = MathUtils.clamp(actTime / Act.POKE.reach, 0, 1), ext = 0.3f + 0.9f * MathUtils.sin(MathUtils.PI * k);
        return out.set(pos.x + redirect.x * ext, Ball.RADIUS, pos.y + redirect.y * ext);
    }

    void launch(float vx, float vz) {
        launch.set(vx, vz);
        launched = true;
        vel.set(vx, vz);
    }

    /** Se planta: deja de querer moverse y frena en seco. */
    void stop() {
        want.setZero();
        launch(0, 0);
    }

    /** Cuánto está en el aire en el cabezazo. */
    float jump() {
        return act == Act.HEAD ? 0.45f * MathUtils.sin(MathUtils.PI * Math.min(actTime / act.length, 1)) : 0;
    }

    /** Cuánto tiene puesta la pose de la acción: entra en {@code in} segundos, se mantiene hasta {@code until} y sale al final. */
    float envelope(float in, float until) {
        if (act == Act.NONE) return 0;
        if (actTime < in) return Interpolation.smooth.apply(actTime / in);
        if (actTime < until) return 1;
        return Interpolation.smooth.apply(Math.max(0, (act.length - actTime) / (act.length - until)));
    }

    /** Estirada: se tumba de costado enseguida y se endereza al levantarse. */
    float diveRoll() {
        return actSide * 80f * diveUp();
    }

    /** Estirada: la cadera va a la altura de la pelota mientras vuela y al piso cuando cae. */
    float diveHipY() {
        float air = Math.max(0.35f, actHeight - 0.35f);
        float landed = MathUtils.clamp((actTime - 0.45f) / 0.3f, 0, 1);
        return MathUtils.lerp(0.9f, MathUtils.lerp(air, 0.22f, landed), diveUp());
    }

    private float diveUp() {
        float down = MathUtils.clamp(actTime / 0.15f, 0, 1), getUp = MathUtils.clamp((actTime - 1.1f) / 0.4f, 0, 1);
        return Interpolation.smooth.apply(down) * (1 - Interpolation.smooth.apply(getUp));
    }

    /** Estirada: dónde están las manos, estiradas hacia el lado al que se tira. */
    void diveHands(Vector3 out) {
        float roll = diveRoll(), h = diveHipY();
        float sideX = -facing.y * Math.signum(actSide), sideZ = facing.x * Math.signum(actSide);
        float out2D = MathUtils.sinDeg(Math.abs(roll)) * Keeper.REACH;
        out.set(pos.x + sideX * out2D, h + MathUtils.cosDeg(roll) * Keeper.REACH, pos.y + sideZ * out2D);
    }

    /** Hacia qué velocidad ir; la física lo lleva de a poco, según cuánto agarre tenga. */
    void steer(Vector2 desired) {
        steer(desired, ACCEL);
    }

    void steer(Vector2 desired, float grip) {
        if (busy()) return;
        want.set(desired);
        this.grip = grip;
    }

    void moveTo(float x, float z, float speed) {
        tmp.set(x - pos.x, z - pos.y);
        float d = tmp.len();
        if (d < 0.3f) tmp.setZero();
        else tmp.setLength(Math.min(speed, d * 3f));
        steer(tmp);
    }

    /**
     * Lo que pasa con el tiempo: las acciones, la zancada, hacia dónde mira. En una acción no
     * maneja: frena solo, más o menos fuerte según cuál sea.
     */
    void update(float dt) {
        if (busy()) {
            actTime += dt;
            float brake = switch (act) {
                case SLIDE -> actTime < act.reach ? 3.5f : 12f;
                case DIVE, DIVE_HEAD -> actTime < act.reach ? 1f : 14f;
                case HEAD -> 2f;
                default -> 6f;
            };
            want.setZero();
            grip = brake * vel.len();
            if (actTime >= act.length) {
                act = Act.NONE;
                grip = ACCEL;
            }
        } else if (!keeper && !facingHeld && heel <= 0 && vel.len2() > 0.25f) {
            facing.set(vel).nor();
        }
        if (!busy()) stride += vel.len() * dt * MathUtils.PI2 / STRIDE;
        cooldown -= dt;
        think -= dt;
        kickAnim -= dt;
        loose -= dt;
        runOn -= dt;
        heel -= dt;
        react -= dt;
    }

    /** Suelta la suela: la pelota sigue un momento con lo que le dio el pie. */
    void releaseSole() {
        if (sole == Sole.ON) {
            loose = 0.2f;
            soleReleased = true;
        }
        sole = Sole.OFF;
        behind = 0;
    }

    /** Su lugar para el saque del medio (lo pone ahí la pelota parada). */
    void kickoffSpot() {
        spot.set(team.toX(baseA * Match.HALF_L * 0.9f), baseZ * Match.HALF_W * 0.8f);
        if (keeper) spot.x = team.toX(-Match.HALF_L + 1);
        facing.set(team.side, 0);
        act = Act.NONE;
        kickAnim = 0;
        sole = Sole.OFF;
    }
}
