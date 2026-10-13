package com.fpetrola.proto.soccer;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.Collision;
import com.badlogic.gdx.physics.bullet.collision.btBoxShape;
import com.badlogic.gdx.physics.bullet.collision.btCapsuleShape;
import com.badlogic.gdx.physics.bullet.collision.btCapsuleShapeX;
import com.badlogic.gdx.physics.bullet.collision.btCollisionDispatcher;
import com.badlogic.gdx.physics.bullet.collision.btCollisionObject;
import com.badlogic.gdx.physics.bullet.collision.btCollisionShape;
import com.badlogic.gdx.physics.bullet.collision.btCylinderShape;
import com.badlogic.gdx.physics.bullet.collision.btCylinderShapeZ;
import com.badlogic.gdx.physics.bullet.collision.btDbvtBroadphase;
import com.badlogic.gdx.physics.bullet.collision.btDefaultCollisionConfiguration;
import com.badlogic.gdx.physics.bullet.collision.btPersistentManifold;
import com.badlogic.gdx.physics.bullet.collision.btSphereShape;
import com.badlogic.gdx.physics.bullet.collision.btStaticPlaneShape;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.physics.bullet.dynamics.btSequentialImpulseConstraintSolver;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.IntArray;

import java.util.List;

/**
 * Lo físico, con Bullet: la pelota (con el aire que la frena y el giro que la curva), los
 * jugadores, los arcos con palos, travesaño y red, el piso y los carteles. Cada jugador es un cuerpo
 * de la rodilla para arriba, que choca con los otros, y dos piernas de la rodilla para abajo que
 * van con la zancada o abiertas si está plantado: entre ellas puede pasar la pelota (el caño). El
 * partido pide (que un jugador vaya a tal velocidad, que la pelota salga así de un pie) y después de
 * cada paso lee dónde quedó cada cosa y quién tocó a quién.
 */
final class Physics implements Disposable {
    static {
        Bullet.init();
    }

    static final float STEP = 1 / 240f;
    /** La cápsula de un jugador: radio, alto total y masa; empieza en la rodilla. */
    static final float BODY_RADIUS = 0.28f, BODY_HEIGHT = 1.86f, BODY_MASS = 75f, KNEE = 0.55f;
    /** Las piernas de la rodilla para abajo: radio, y a qué distancia del eje van cerradas y abiertas. */
    static final float SHIN = 0.07f, CLOSED = 0.12f, OPEN = 0.22f;
    /** El pie del puntazo, una bola chica. */
    static final float FOOT = 0.09f;
    /** Más que esto en un paso (unos 14 m/s) no es una pierna que se mueve: es una que cambió de lugar. */
    private static final float SHIN_JUMP = 0.06f;
    /** Los palos son redondos, de este radio; la red llega hasta esta profundidad. */
    static final float POST = 0.06f, NET_DEPTH = 2f;
    /** Los carteles, alrededor de la cancha, a esta distancia del centro, y de este alto. */
    static final float BOARD_X = Match.HALF_L + 5, BOARD_Z = Match.HALF_W + 4, BOARD_H = 0.9f;

    private static final int G_GROUND = 1, G_BALL = 2, G_BODY = 4, G_LEG = 8, G_STATIC = 16, G_SHIN = 32;
    /** Para saber en los choques qué es cada cosa; los de los jugadores llevan sumado su índice. */
    private static final int ID_GROUND = 1, ID_BALL = 2, ID_NET = 3, ID_FRAME = 4, ID_BOARD = 5;
    private static final int ID_BODY = 100, ID_LEG = 200, ID_DIVE = 300, ID_SHIN = 400, ID_FOOT = 600;
    private static final int NO_RESPONSE = btCollisionObject.CollisionFlags.CF_NO_CONTACT_RESPONSE;

    /** Lo que el partido quiere saber de los choques. */
    interface Contacts {
        /** La pierna de una barrida tocó la pelota ({@code other} null) o a otro jugador. */
        void slideTouched(Player slider, Player other);

        /** El pie de un puntazo tocó la pelota ({@code other} null) o la pierna de otro jugador. */
        void pokeTouched(Player poker, Player other);

        /** La pelota pegó en el cuerpo de un jugador, o en el arquero estirado. */
        void ballHitBody(Player p);

        /** La pelota está tocando la red. */
        void ballInNet();

        /** La pelota chocó contra algo ({@code what}: {@link #BUMP_POST} y los otros) y cambió de velocidad {@code strength} m/s. */
        void ballBumped(int what, float strength);
    }

    private final btDefaultCollisionConfiguration config = new btDefaultCollisionConfiguration();
    private final btCollisionDispatcher dispatcher = new btCollisionDispatcher(config);
    private final btDbvtBroadphase broadphase = new btDbvtBroadphase();
    private final btSequentialImpulseConstraintSolver solver = new btSequentialImpulseConstraintSolver();
    private final btDiscreteDynamicsWorld world = new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, config);
    private final Array<Disposable> trash = new Array<>();
    /** Las paredes de la arena de práctica: muy altas, justo afuera de las líneas, con hueco sólo en la boca de los arcos. */
    static final float WALL_H = 15f, ARENA_SIDE = 3f, ARENA_END = 3.5f;
    /** Contra qué picó la pelota, para el ruido: un palo o el travesaño, una pared o un cartel, un jugador. */
    static final int BUMP_POST = 1, BUMP_WALL = 2, BUMP_BODY = 3;
    /** Desde cuánto cambio de velocidad (m/s) en un paso un choque hace ruido. */
    private static final float BUMP_MIN = 1.5f;
    private final Vector3 before = new Vector3();
    private final Array<btRigidBody> walls = new Array<>();
    /** Cada pared como caja: centro (x, y, z) y medias medidas (x, y, z), para dibujarlas. */
    final Array<float[]> wallBoxes = new Array<>();
    private boolean wallsOn;

    private final List<Player> players;
    private final Ball ball;
    private final btRigidBody ballBody;
    /** Por jugador: el cuerpo, la pierna de la barrida, el pie del puntazo y (los arqueros) el cuerpo estirado; y dos piernas por jugador. */
    private final btRigidBody[] bodies, legs, feet, dives, shins;
    private final boolean[] legOn, footOn, diveOn, shinOn, lying, ignored, teleported;
    /** La pelota clavada en un lugar: una pelota parada, o en las manos del arquero. */
    private boolean pinned;
    private final Matrix4 pin = new Matrix4(), t = new Matrix4(), was = new Matrix4(), ballAt = new Matrix4();
    private final Vector3 v = new Vector3(), w = new Vector3(), f = new Vector3(), a = new Vector3(), b = new Vector3();
    private final Quaternion q = new Quaternion();
    /** Los toques de las piernas de las barridas y de los pies de los puntazos en este paso. */
    private final IntArray legBall = new IntArray(), legBody = new IntArray(), footBall = new IntArray(), footBody = new IntArray();

    Physics(List<Player> players, Ball ball) {
        this.players = players;
        this.ball = ball;
        int n = players.size();
        bodies = new btRigidBody[n];
        legs = new btRigidBody[n];
        feet = new btRigidBody[n];
        dives = new btRigidBody[n];
        shins = new btRigidBody[2 * n];
        legOn = new boolean[n];
        footOn = new boolean[n];
        diveOn = new boolean[n];
        shinOn = new boolean[2 * n];
        lying = new boolean[n];
        ignored = new boolean[n];
        teleported = new boolean[n];
        world.setGravity(v.set(0, -Ball.GRAVITY, 0));

        add(fixed(keep(new btStaticPlaneShape(v.set(0, 1, 0), 0)), t.idt(), ID_GROUND, 0.8f, 0.8f), G_GROUND, G_BALL);

        // La pelota: rebota, roza, y no atraviesa nada aunque vaya muy rápido.
        btSphereShape sphere = keep(new btSphereShape(Ball.RADIUS));
        sphere.calculateLocalInertia(Ball.MASS, v.setZero());
        ballBody = keep(new btRigidBody(Ball.MASS, null, sphere, v));
        ballBody.setRestitution(Ball.BOUNCE / 0.8f);  // por la del piso (0,8): pica con Ball.BOUNCE
        ballBody.setFriction(0.6f);
        ballBody.setDamping(0, Ball.SPIN_DAMPING);
        ballBody.setCcdMotionThreshold(Ball.RADIUS / 2);
        ballBody.setCcdSweptSphereRadius(Ball.RADIUS * 0.9f);
        ballBody.setActivationState(Collision.DISABLE_DEACTIVATION);
        ballBody.setUserValue(ID_BALL);
        ballBody.setCenterOfMassTransform(t.setToTranslation(0, Ball.RADIUS, 0));
        world.addRigidBody(ballBody, G_BALL, G_GROUND | G_BODY | G_LEG | G_STATIC | G_SHIN);

        // Los jugadores: cápsulas de la rodilla para arriba, que no se caen ni suben, empujadas hacia la
        // velocidad que quieren; abajo, las dos piernas, que se mueven a mano.
        btCapsuleShape capsule = keep(new btCapsuleShape(BODY_RADIUS, BODY_HEIGHT - KNEE - 2 * BODY_RADIUS));
        btCapsuleShapeX leg = keep(new btCapsuleShapeX(0.13f, 0.85f));
        btCapsuleShapeX stretched = keep(new btCapsuleShapeX(0.22f, 1.3f));
        btCapsuleShape shin = keep(new btCapsuleShape(SHIN, KNEE - 2 * SHIN));
        btSphereShape foot = keep(new btSphereShape(FOOT));
        Vector3 inertia = new Vector3();
        capsule.calculateLocalInertia(BODY_MASS, inertia);
        for (int i = 0; i < n; i++) {
            Player p = players.get(i);
            btRigidBody body = keep(new btRigidBody(BODY_MASS, null, capsule, inertia));
            body.setAngularFactor(0);
            body.setLinearFactor(v.set(1, 0, 1));
            body.setFriction(0);
            body.setRestitution(0.3f);
            body.setActivationState(Collision.DISABLE_DEACTIVATION);
            body.setUserValue(ID_BODY + i);
            world.addRigidBody(body, G_BODY, G_BALL | G_BODY | G_LEG | G_STATIC);
            bodies[i] = body;
            legs[i] = kinematic(leg, ID_LEG + i, body);
            feet[i] = kinematic(foot, ID_FOOT + i, body);
            for (int s = 0; s < 2; s++) {
                btRigidBody k = kinematic(shin, ID_SHIN + 2 * i + s, body);
                k.setIgnoreCollisionCheck(legs[i], true);
                k.setIgnoreCollisionCheck(feet[i], true);
                shins[2 * i + s] = k;
            }
            if (p.keeper) dives[i] = kinematic(stretched, ID_DIVE + i, body);
            place(p, p.pos.x, p.pos.y);
        }
        arena();
        goal(1);
        goal(-1);
        boards();
    }

    private <T extends Disposable> T keep(T thing) {
        trash.add(thing);
        return thing;
    }

    private btRigidBody fixed(btCollisionShape shape, Matrix4 transform, int id, float friction, float restitution) {
        btRigidBody body = keep(new btRigidBody(0, null, shape, Vector3.Zero));
        body.setWorldTransform(transform);
        body.setUserValue(id);
        body.setFriction(friction);
        body.setRestitution(restitution);
        return body;
    }

    private void add(btRigidBody body, int group, int mask) {
        world.addRigidBody(body, group, mask);
    }

    /** Un cuerpo que se mueve a mano (no lo empuja nadie, él empuja) y que no choca con su dueño. */
    private btRigidBody kinematic(btCollisionShape shape, int id, btRigidBody owner) {
        btRigidBody k = keep(new btRigidBody(0, null, shape, Vector3.Zero));
        k.setCollisionFlags(k.getCollisionFlags() | btCollisionObject.CollisionFlags.CF_KINEMATIC_OBJECT);
        k.setActivationState(Collision.DISABLE_DEACTIVATION);
        k.setUserValue(id);
        k.setFriction(0.6f);
        k.setRestitution(0.4f);
        k.setIgnoreCollisionCheck(owner, true);
        return k;
    }

    /** Un arco: palos y travesaño redondos que hacen rebotar, y la red, que se la come. */
    private void goal(int s) {
        float x = s * Match.HALF_L, half = Match.GOAL_HALF, h = Match.GOAL_H, thin = 0.03f;
        btCylinderShape post = keep(new btCylinderShape(v.set(POST, h / 2, POST)));
        for (int side = -1; side <= 1; side += 2)
            add(fixed(post, t.setToTranslation(x, h / 2, side * (half + POST)), ID_FRAME, 0.3f, 1f), G_STATIC, G_BALL | G_BODY);
        btCylinderShapeZ bar = keep(new btCylinderShapeZ(v.set(POST, POST, half + 2 * POST)));
        add(fixed(bar, t.setToTranslation(x, h + POST, 0), ID_FRAME, 0.3f, 1f), G_STATIC, G_BALL | G_BODY);
        float back = x + s * NET_DEPTH, middle = x + s * NET_DEPTH / 2, width = half + 2 * POST;
        add(fixed(keep(new btBoxShape(v.set(thin, h / 2, width))), t.setToTranslation(back, h / 2, 0), ID_NET, 1f, 0.05f), G_STATIC, G_BALL | G_BODY);
        btBoxShape wall = keep(new btBoxShape(v.set(NET_DEPTH / 2, h / 2, thin)));
        for (int side = -1; side <= 1; side += 2)
            add(fixed(wall, t.setToTranslation(middle, h / 2, side * width), ID_NET, 1f, 0.05f), G_STATIC, G_BALL | G_BODY);
        add(fixed(keep(new btBoxShape(v.set(NET_DEPTH / 2, thin, width))), t.setToTranslation(middle, h + 2 * POST, 0), ID_NET, 1f, 0.05f),
                G_STATIC, G_BALL | G_BODY);
    }

    /** Los carteles alrededor de la cancha: la pelota que sale rebota en ellos y los jugadores no se van. */
    private void boards() {
        float thin = 0.1f;
        btBoxShape along = keep(new btBoxShape(v.set(BOARD_X, BOARD_H / 2, thin)));
        btBoxShape across = keep(new btBoxShape(v.set(thin, BOARD_H / 2, BOARD_Z)));
        for (int s = -1; s <= 1; s += 2) {
            add(fixed(along, t.setToTranslation(0, BOARD_H / 2, s * BOARD_Z), ID_BOARD, 0.5f, 0.5f), G_STATIC, G_BALL | G_BODY);
            add(fixed(across, t.setToTranslation(s * BOARD_X, BOARD_H / 2, 0), ID_BOARD, 0.5f, 0.5f), G_STATIC, G_BALL | G_BODY);
        }
    }

    /** Arma (sin ponerlas) las paredes de la arena de práctica. */
    private void arena() {
        // unos metros afuera de las líneas y detrás de los arcos: se ve si entró al arco o pasó al lado
        float h = WALL_H, thin = 0.1f, side = Match.HALF_W + ARENA_SIDE, end = Match.HALF_L + ARENA_END;
        for (int s = -1; s <= 1; s += 2) {
            wall(0, h / 2, s * side, end, h / 2, thin);  // los costados
            wall(s * end, h / 2, 0, thin, h / 2, side);  // los fondos
        }
    }

    private void wall(float x, float y, float z, float hx, float hy, float hz) {
        // vidrio: rebota bastante (con la de la pelota, sale con tres cuartos de la velocidad) y casi no frena de costado
        walls.add(fixed(keep(new btBoxShape(v.set(hx, hy, hz))), t.setToTranslation(x, y, z), ID_BOARD, 0.1f, 0.75f / (Ball.BOUNCE / 0.8f)));
        wallBoxes.add(new float[]{x, y, z, hx, hy, hz});
    }

    /** Pone o saca las paredes de la arena de práctica. */
    void walls(boolean on) {
        if (on == wallsOn) return;
        wallsOn = on;
        for (btRigidBody w : walls) {
            if (on) world.addRigidBody(w, G_STATIC, G_BALL | G_BODY);
            else world.removeRigidBody(w);
        }
    }

    // ---- lo que pide el partido --------------------------------------------------------------

    /** Pone a un jugador en un lugar, quieto. */
    void place(Player p, float x, float z) {
        btRigidBody body = bodies[p.index];
        body.setCenterOfMassTransform(t.setToTranslation(x, (KNEE + BODY_HEIGHT) / 2, z));
        teleported[p.index] = true;
        body.setLinearVelocity(Vector3.Zero);
        p.pos.set(x, z);
        p.vel.setZero();
        p.want.setZero();
        p.launched = false;
    }

    /** Clava la pelota en un lugar (una pelota parada, las manos del arquero) hasta que la suelten o le peguen. */
    void pinBall(float x, float y, float z) {
        pin.setToTranslation(x, y, z);
        if (!pinned) {
            pinned = true;
            ballBody.setCollisionFlags(ballBody.getCollisionFlags() | NO_RESPONSE);
        }
        holdBall();
    }

    void releaseBall() {
        if (!pinned) return;
        pinned = false;
        ballBody.setCollisionFlags(ballBody.getCollisionFlags() & ~NO_RESPONSE);
    }

    private void holdBall() {
        ballBody.setCenterOfMassTransform(pin);
        ballBody.setLinearVelocity(Vector3.Zero);
        ballBody.setAngularVelocity(Vector3.Zero);
        readBall();
    }

    /** Le pega: sale con esa velocidad y ese giro. Si sale para arriba, primero la despega del piso. */
    void kickBall(Vector3 vel, Vector3 spin) {
        releaseBall();
        if (vel.y > 0 && ball.pos.y < Ball.RADIUS + 0.03f)
            ballBody.setCenterOfMassTransform(t.setToTranslation(ball.pos.x, Ball.RADIUS + 0.03f, ball.pos.z));
        ballBody.setLinearVelocity(vel);
        ballBody.setAngularVelocity(spin);
        readBall();
    }

    /** Sólo la velocidad: al llevarla pegada al pie, al pararla. */
    void moveBall(Vector3 vel) {
        ballBody.setLinearVelocity(vel);
        readBall();
    }

    /** Sólo el giro: el efecto que se le sigue dando con las flechas después de patear. */
    void spinBall(Vector3 spin) {
        ballBody.setAngularVelocity(spin);
        readBall();
    }

    void placeBall(float x, float y, float z) {
        releaseBall();
        ballBody.setCenterOfMassTransform(t.setToTranslation(x, y, z));
        ballBody.setLinearVelocity(Vector3.Zero);
        ballBody.setAngularVelocity(Vector3.Zero);
        readBall();
    }

    /** Que la pelota pase de largo por este jugador (cuerpo y piernas): el que la lleva al pie, o los compañeros del que la pateó. */
    void ballIgnores(Player p, boolean ignore) {
        int i = p.index;
        if (ignored[i] == ignore) return;
        ignored[i] = ignore;
        ignore(bodies[i], ignore);
        ignore(shins[2 * i], ignore);
        ignore(shins[2 * i + 1], ignore);
    }

    /**
     * Que la pelota no choque (o vuelva a chocar) con {@code k}. Al dejar de chocar se vacía el
     * contacto que tenían: Bullet ya no lo actualiza pero lo sigue resolviendo, y si la pelota estaba
     * metida entre las piernas del que acaba de agarrarla, la despedía con la velocidad de la pierna.
     */
    private void ignore(btRigidBody k, boolean ignore) {
        ballBody.setIgnoreCollisionCheck(k, ignore);
        if (!ignore) return;
        int id = k.getUserValue();
        for (int i = dispatcher.getNumManifolds() - 1; i >= 0; i--) {
            btPersistentManifold m = dispatcher.getManifoldByIndexInternal(i);
            int a = m.getBody0().getUserValue(), b = m.getBody1().getUserValue();
            if (a == ID_BALL && b == id || b == ID_BALL && a == id) m.clearManifold();
        }
    }

    /** Dónde está la pierna {@code side} (0 la izquierda, 1 la derecha) de {@code p}, a ras del piso. */
    Vector3 shin(Player p, int side, Vector3 out) {
        float sign = side == 0 ? -1 : 1;
        float run = p.act == Player.Act.NONE ? Math.min(p.vel.len() / Player.RUN, 1f) : 0;
        float fwd = -sign * 0.25f * run * MathUtils.sin(p.stride), lateral = sign * (CLOSED + (OPEN - CLOSED) * p.stance);
        return out.set(p.pos.x + p.facing.x * fwd - p.facing.y * lateral, 0, p.pos.y + p.facing.y * fwd + p.facing.x * lateral);
    }

    /** El arquero estirado no llegó a tiempo: la pelota le pasa por la punta de los dedos. */
    void diveLetsThrough(Player k, boolean through) {
        if (dives[k.index] != null) ballBody.setIgnoreCollisionCheck(dives[k.index], through);
    }

    // ---- un paso -----------------------------------------------------------------------------

    void step(Contacts listener) {
        drive();
        kinematics();
        if (pinned) holdBall();
        else airAndGrass();
        before.set(ballBody.getLinearVelocity());
        float fall = before.y;
        world.stepSimulation(STEP, 0);
        if (pinned) holdBall();
        read();
        if (!pinned && fall < -1.5f && ball.vel.y > 0 && ball.pos.y < Ball.RADIUS + 0.05f) bounced();
        contacts(listener, pinned ? 0 : ball.vel.dst(before));
    }

    /** Picó: el roce con el pasto le saca velocidad de costado y giro (como en la copia que predice). */
    private void bounced() {
        moveBall(v.set(ball.vel.x * Ball.BOUNCE_ROLL, ball.vel.y, ball.vel.z * Ball.BOUNCE_ROLL));
        spinBall(w.set(ball.spin).scl(0.6f));
    }

    /**
     * Cada jugador va hacia la velocidad que quiere, cambiándola como mucho según su agarre: si lo
     * chocan, lo desplazan, y le cuesta volver. Tirado en el piso o estirado en el aire no choca de
     * parado: de eso se encargan la pierna de la barrida y el cuerpo estirado del arquero.
     */
    private void drive() {
        for (int i = 0; i < bodies.length; i++) {
            Player p = players.get(i);
            btRigidBody body = bodies[i];
            v.set(body.getLinearVelocity());
            if (p.launched) {
                v.x = p.launch.x;
                v.z = p.launch.y;
                p.launched = false;
            } else {
                float dx = p.want.x - v.x, dz = p.want.y - v.z, d = (float) Math.sqrt(dx * dx + dz * dz), max = p.grip * STEP;
                if (d > max) {
                    dx *= max / d;
                    dz *= max / d;
                }
                v.x += dx;
                v.z += dz;
            }
            body.setLinearVelocity(v);
            boolean down = p.act == Player.Act.FALL || p.act == Player.Act.DIVE || p.act == Player.Act.DIVE_HEAD;
            if (down != lying[i]) {
                lying[i] = down;
                int flags = body.getCollisionFlags();
                body.setCollisionFlags(down ? flags | NO_RESPONSE : flags & ~NO_RESPONSE);
            }
        }
    }

    /** Las piernas, la pierna de cada barrida, el pie de cada puntazo y el cuerpo estirado de los arqueros siguen a su dueño. */
    private void kinematics() {
        for (int i = 0; i < bodies.length; i++) {
            Player p = players.get(i);
            // Parado, corriendo, saltando o estirando el pie, las dos piernas; tirado, ninguna.
            boolean standing = p.act == Player.Act.NONE || p.act == Player.Act.HEAD || p.act == Player.Act.POKE;
            for (int s = 0; s < 2; s++) {
                int k = 2 * i + s;
                boolean jump = teleported[i];
                if (standing) {
                    shin(p, s, a);
                    t.setToTranslation(a.x, KNEE / 2 + p.jump(), a.z);
                    // Si la pierna salta de lugar (lo pusieron en otro lado, o se dio vuelta de golpe y las
                    // piernas cambiaron de lado), que no empuje con esa velocidad: Bullet la sacaría del salto.
                    if (shinOn[k]) {
                        shins[k].getWorldTransform(was);
                        jump |= was.getTranslation(b).dst2(a.x, b.y, a.z) > SHIN_JUMP * SHIN_JUMP;
                    }
                }
                shinOn[k] = follow(shins[k], shinOn[k], standing, G_BALL | G_LEG);
                if (standing && jump) shins[k].setInterpolationWorldTransform(t);
            }
            teleported[i] = false;
            boolean poke = p.act == Player.Act.POKE && p.actTime < p.act.reach;
            if (poke) {
                p.pokeFoot(a);
                t.setToTranslation(a);
            }
            footOn[i] = follow(feet[i], footOn[i], poke, G_BALL | G_SHIN);
            boolean slide = p.act == Player.Act.SLIDE && p.actTime < p.act.reach;
            if (slide) {
                float angle = MathUtils.atan2(-p.facing.y, p.facing.x) * MathUtils.radDeg;
                t.setToTranslation(p.pos.x + p.facing.x * 0.6f, 0.15f, p.pos.y + p.facing.y * 0.6f).rotate(Vector3.Y, angle);
            }
            legOn[i] = follow(legs[i], legOn[i], slide, G_BALL | G_BODY | G_SHIN);
            if (dives[i] == null) continue;
            boolean dive = p.act == Player.Act.DIVE && p.actTime < p.act.reach;
            if (dive) {
                a.set(p.pos.x, p.diveHipY(), p.pos.y);
                p.diveHands(b);
                f.set(b).sub(a).nor();
                q.setFromCross(Vector3.X, f);
                t.set(a.add(b).scl(0.5f), q);
            }
            diveOn[i] = follow(dives[i], diveOn[i], dive, G_BALL);
        }
    }

    /** Prende, mueve o apaga un cuerpo cinemático. Al moverlo, Bullet saca de ahí con qué velocidad empuja. */
    private boolean follow(btRigidBody k, boolean on, boolean want, int mask) {
        if (want) {
            k.setWorldTransform(t);
            if (!on) {
                k.setInterpolationWorldTransform(t);
                world.addRigidBody(k, k.getUserValue() >= ID_SHIN && k.getUserValue() < ID_FOOT ? G_SHIN : G_LEG, mask);
            }
        } else if (on) {
            world.removeRigidBody(k);
        }
        return want;
    }

    /** En el aire, el arrastre y el efecto; rodando, el pasto la frena, y gira como rueda para que el roce no se lo devuelva. */
    private void airAndGrass() {
        v.set(ballBody.getLinearVelocity());
        w.set(ballBody.getAngularVelocity());
        float y = ballBody.getCenterOfMassPosition().y;
        if (y <= Ball.RADIUS + 0.015f && Math.abs(v.y) < 0.6f) {
            float s = (float) Math.sqrt(v.x * v.x + v.z * v.z);
            if (s > 0) {
                float slower = Math.max(0, s - (Ball.ROLL_C + Ball.ROLL_K * s) * STEP) / s;
                v.x *= slower;
                v.z *= slower;
            }
            Ball.swerve(v, w, STEP);
            ballBody.setLinearVelocity(v);
            ballBody.setAngularVelocity(w.set(v.z / Ball.RADIUS, w.y * (1 - Ball.SPIN_DAMPING * STEP), -v.x / Ball.RADIUS));
        } else {
            ballBody.applyCentralForce(Ball.airForce(v, w, f));
        }
    }

    private void read() {
        for (int i = 0; i < bodies.length; i++) {
            Player p = players.get(i);
            v.set(bodies[i].getCenterOfMassPosition());
            p.pos.set(v.x, v.z);
            v.set(bodies[i].getLinearVelocity());
            p.vel.set(v.x, v.z);
        }
        readBall();
    }

    private void readBall() {
        ball.pos.set(ballBody.getCenterOfMassPosition());
        ball.vel.set(ballBody.getLinearVelocity());
        ball.spin.set(ballBody.getAngularVelocity());
        ballBody.getWorldTransform(ballAt);
        ballAt.getRotation(ball.rot, true);
    }

    /** Quién tocó a quién en este paso. De las barridas, primero lo que tocaron de la pelota: si en el mismo paso tocan las dos cosas, se la llevó limpia. */
    private void contacts(Contacts listener, float jolt) {
        int bump = 0;
        legBall.clear();
        legBody.clear();
        footBall.clear();
        footBody.clear();
        boolean net = false;
        for (int i = dispatcher.getNumManifolds() - 1; i >= 0; i--) {
            btPersistentManifold m = dispatcher.getManifoldByIndexInternal(i);
            if (!touching(m)) continue;
            int a = m.getBody0().getUserValue(), b = m.getBody1().getUserValue();
            if (a > b) {
                int swap = a;
                a = b;
                b = swap;
            }
            if (a == ID_BALL) {
                if (b == ID_FRAME) bump = BUMP_POST;
                else if (b == ID_BOARD) bump = BUMP_WALL;
                else if (b >= ID_BODY && b < ID_FOOT && (b < ID_LEG || b >= ID_DIVE)) bump = BUMP_BODY;
                if (b == ID_NET) net = true;
                else if (b >= ID_FOOT) footBall.add(b - ID_FOOT);
                else if (b >= ID_SHIN) listener.ballHitBody(players.get((b - ID_SHIN) / 2));
                else if (b >= ID_DIVE) listener.ballHitBody(players.get(b - ID_DIVE));
                else if (b >= ID_LEG) legBall.add(b - ID_LEG);
                else if (b >= ID_BODY) listener.ballHitBody(players.get(b - ID_BODY));
            } else if (a >= ID_BODY && a < ID_LEG && b >= ID_LEG && b < ID_DIVE) {
                legBody.add(b - ID_LEG);
                legBody.add(a - ID_BODY);
            } else if (a >= ID_LEG && a < ID_DIVE && b >= ID_SHIN && b < ID_FOOT) {
                legBody.add(a - ID_LEG);
                legBody.add((b - ID_SHIN) / 2);
            } else if (a >= ID_SHIN && a < ID_FOOT && b >= ID_FOOT) {
                footBody.add(b - ID_FOOT);
                footBody.add((a - ID_SHIN) / 2);
            }
        }
        for (int i = 0; i < legBall.size; i++) listener.slideTouched(players.get(legBall.get(i)), null);
        for (int i = 0; i < legBody.size; i += 2) listener.slideTouched(players.get(legBody.get(i)), players.get(legBody.get(i + 1)));
        for (int i = 0; i < footBall.size; i++) listener.pokeTouched(players.get(footBall.get(i)), null);
        for (int i = 0; i < footBody.size; i += 2) listener.pokeTouched(players.get(footBody.get(i)), players.get(footBody.get(i + 1)));
        if (net) listener.ballInNet();
        if (bump != 0 && jolt > BUMP_MIN) listener.ballBumped(bump, jolt);
    }

    private static boolean touching(btPersistentManifold m) {
        for (int j = m.getNumContacts() - 1; j >= 0; j--)
            if (m.getContactPoint(j).getDistance() < 0.01f) return true;
        return false;
    }

    @Override
    public void dispose() {
        world.dispose();
        for (Disposable d : trash) d.dispose();
        solver.dispose();
        broadphase.dispose();
        dispatcher.dispose();
        config.dispose();
    }
}
