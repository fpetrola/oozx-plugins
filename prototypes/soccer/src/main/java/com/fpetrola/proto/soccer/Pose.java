package com.fpetrola.proto.soccer;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

/**
 * La pose del muñeco de un jugador, sin gráficos (así se puede probar): dónde va cada parte,
 * relativo al jugador (x adelante, y arriba, z a su derecha). {@link Puppet} la copia a sus nodos.
 * <p>
 * Las piernas tienen muslo y canilla. Se decide dónde va cada pie (corriendo, la zancada; parado,
 * debajo de la cadera, más abiertos si está plantado; con la suela o un amague, el pie de juego; en
 * las acciones, hacia donde apunta la pierna) y la rodilla sale sola, con IK de dos huesos: dados
 * la cadera, el pie y el largo de cada hueso, hay una sola rodilla que dobla hacia adelante.
 */
final class Pose {
    static final float HIP = 0.9f, THIGH = 0.45f, SHIN = 0.45f, LEG = THIGH + SHIN;
    static final float HIP_Z = 0.12f, SHOULDER = 0.6f, SHOULDER_Z = 0.3f;
    /** Del hombro al codo y del codo a la punta de la mano. */
    static final float UPPER = 0.28f, FORE = 0.27f;
    /** Corriendo: cuánto va el pie adelante y atrás, y cuánto sube cuando pasa. */
    private static final float STEP = 0.4f, LIFT = 0.22f;

    /** Dónde está el jugador y hacia dónde mira, en la cancha. */
    final Matrix4 instance = new Matrix4();
    /** Relativo al jugador: el tronco, cada muslo, cada canilla y cada brazo (0 izquierda, 1 derecha). */
    final Matrix4 torso = new Matrix4();
    final Matrix4[] thigh = {new Matrix4(), new Matrix4()}, shin = {new Matrix4(), new Matrix4()}, arm = {new Matrix4(), new Matrix4()},
            forearm = {new Matrix4(), new Matrix4()};
    /** Relativo al jugador: la rodilla y la planta de cada pie, y la punta de cada mano. */
    final Vector3[] knee = {new Vector3(), new Vector3()}, sole = {new Vector3(), new Vector3()}, hand = {new Vector3(), new Vector3()};

    private final Matrix4 hip = new Matrix4(), joint = new Matrix4(), inverse = new Matrix4();
    private final Vector3 target = new Vector3(), k = new Vector3(), f = new Vector3(), u = new Vector3();

    /**
     * @param holdingBall el arquero con la pelota en las manos
     * @param throwIn     el que va a sacar un lateral, con la pelota sobre la cabeza
     */
    void compute(Player p, boolean holdingBall, boolean throwIn) {
        // Corriendo: los pies van y vienen con la zancada y el que pasa adelante sube; los brazos al
        // revés que las piernas, y el cuerpo un poco adelante.
        float run = p.busy() ? 0 : Math.min(p.vel.len() / Player.RUN, 1f);
        float swing = 40f * run * MathUtils.sin(p.stride);
        float hipY = HIP - 0.02f - 0.08f * run;
        float pitch = 0, roll = 0, lean = -10f * run, armLA = -0.8f * swing, armRA = 0.8f * swing;
        /** Cuánto dobla los codos (grados, el antebrazo hacia adelante): corriendo, mucho; parado, un poco. */
        float elbow = 15f + 70f * run;
        /** En las acciones y al patear, hacia dónde apunta cada pierna (grados, positivo adelante); NaN es que va con la zancada. */
        float legLA = Float.NaN, legRA = Float.NaN;

        if (p.heel > 0) {
            // El taco: la pierna derecha va para atrás con la rodilla doblada y le pega con el talón.
            float s = MathUtils.sin(MathUtils.PI * (1 - p.heel / Player.KICK_ANIM));
            legRA = MathUtils.lerp(-swing, -50f, s);
            legLA = swing * (1 - s);
            lean = MathUtils.lerp(lean, 8f, s);
        } else if (p.kickAnim > 0) {
            float s = MathUtils.sin(MathUtils.PI * (1 - p.kickAnim / Player.KICK_ANIM));
            if (throwIn) {
                armLA = armRA = MathUtils.lerp(170f, 40f, s);
                elbow = 25f;
            } else {
                legRA = MathUtils.lerp(-swing, 75f, s);
                legLA = MathUtils.lerp(swing, -10f, s);
                armLA = MathUtils.lerp(armLA, 35f, s);
                armRA = MathUtils.lerp(armRA, -30f, s);
                elbow = MathUtils.lerp(elbow, 45f, s);
                lean = MathUtils.lerp(lean, 12f, s);
            }
        } else if (throwIn) {
            armLA = armRA = 170f;
            elbow = 25f;
            lean = 8f;
        }
        if (holdingBall && p.act == Player.Act.NONE) {
            armLA = armRA = 75f;
            elbow = 60f;
        }

        switch (p.act) {
            case SLIDE -> {
                // Tirado hacia atrás, una pierna estirada sobre el pasto y la otra apenas más abajo.
                float s = p.envelope(0.1f, 0.55f);
                hipY = MathUtils.lerp(hipY, 0.16f, s);
                pitch = 62f * s;
                lean = MathUtils.lerp(lean, 0, s);
                legLA = 28f * s;
                legRA = 16f * s;
                armLA = MathUtils.lerp(armLA, -20f, s);
                armRA = MathUtils.lerp(armRA, -20f, s);
                elbow = MathUtils.lerp(elbow, 30f, s);
            }
            case FALL -> {
                // De cara al piso, con los brazos adelante.
                float s = p.envelope(0.25f, 1.2f);
                hipY = MathUtils.lerp(hipY, 0.18f, s);
                pitch = -84f * s;
                lean = MathUtils.lerp(lean, 0, s);
                legLA = -6f * s;
                legRA = 4f * s;
                armLA = MathUtils.lerp(armLA, 160f, s);
                armRA = MathUtils.lerp(armRA, 150f, s);
                elbow = MathUtils.lerp(elbow, 15f, s);
            }
            case DIVE -> {
                // Volando de costado con los brazos estirados hacia la pelota.
                float s = p.envelope(0.1f, 1.1f);
                hipY = p.diveHipY();
                roll = p.diveRoll();
                lean = 0;
                legLA = 0;
                legRA = -8f * s;
                armLA = MathUtils.lerp(armLA, 170f, s);
                armRA = MathUtils.lerp(armRA, 170f, s);
                elbow = MathUtils.lerp(elbow, 5f, s);
            }
            case HEAD -> {
                float s = p.envelope(0.1f, 0.45f);
                hipY += p.jump();
                lean = MathUtils.lerp(lean, -8f, s);
                legLA = -20f * s;
                legRA = -10f * s;
                armLA = MathUtils.lerp(armLA, 120f, s);
                armRA = MathUtils.lerp(armRA, 120f, s);
                elbow = MathUtils.lerp(elbow, 35f, s);
            }
            case DIVE_HEAD -> {
                // Palomita: el cuerpo horizontal hacia adelante, y al piso.
                float s = p.envelope(0.1f, 0.9f), landed = MathUtils.clamp((p.actTime - 0.35f) / 0.25f, 0, 1);
                hipY = MathUtils.lerp(hipY, MathUtils.lerp(0.55f, 0.2f, landed), s);
                pitch = -82f * s;
                lean = 0;
                legLA = 0;
                legRA = 5f * s;
                armLA = MathUtils.lerp(armLA, 150f, s);
                armRA = MathUtils.lerp(armRA, 150f, s);
                elbow = MathUtils.lerp(elbow, 10f, s);
            }
            case POKE -> {
                // Puntazo: la pierna derecha va hacia el pie que se estira (abajo), el cuerpo se echa un poco atrás.
                float s = MathUtils.sin(MathUtils.PI * MathUtils.clamp(p.actTime / Player.Act.POKE.reach, 0, 1));
                lean = MathUtils.lerp(lean, 10f, s);
                hipY -= 0.12f * s;
                armLA = MathUtils.lerp(armLA, 30f, s);
                armRA = MathUtils.lerp(armRA, -25f, s);
                elbow = MathUtils.lerp(elbow, 40f, s);
            }
            default -> { }
        }

        float angle = MathUtils.atan2(-p.facing.y, p.facing.x) * MathUtils.radDeg;
        instance.setToTranslation(p.pos.x, 0, p.pos.y).rotate(Vector3.Y, angle);
        hip.setToTranslation(0, hipY, 0).rotate(Vector3.X, roll).rotate(Vector3.Z, pitch);
        torso.set(hip).rotate(Vector3.Z, lean);
        arm[0].set(torso).translate(0, SHOULDER, -SHOULDER_Z).rotate(Vector3.Z, armLA);
        arm[1].set(torso).translate(0, SHOULDER, SHOULDER_Z).rotate(Vector3.Z, armRA);
        for (int side = 0; side < 2; side++) {
            // el antebrazo cuelga del codo, doblado hacia adelante
            forearm[side].set(arm[side]).translate(0, -UPPER, 0).rotate(Vector3.Z, elbow);
            hand[side].set(0, -FORE, 0).mul(forearm[side]);
        }

        boolean upright = pitch == 0 && roll == 0;
        for (int side = 0; side < 2; side++) {
            float sign = side == 0 ? -1 : 1, legA = side == 0 ? legLA : legRA;
            joint.set(hip).translate(0, 0, sign * HIP_Z);
            if (!Float.isNaN(legA)) {
                // la pierna apunta hacia ahí, casi estirada (en el taco, con la rodilla doblada)
                target.set(MathUtils.sinDeg(legA), -MathUtils.cosDeg(legA), 0).scl(LEG * (side == 1 && p.heel > 0 ? 0.8f : 0.97f));
            } else if (side == 1 && p.act == Player.Act.POKE) {
                // el puntazo: la pierna va hacia el pie que se estira a ras del piso
                p.pokeFoot(u);
                float fx = u.x - p.pos.x, fz = u.z - p.pos.y;
                target.set(fx * p.facing.x + fz * p.facing.y, u.y - hipY, fx * -p.facing.y + fz * p.facing.x - HIP_Z);
            } else if (side == 1 && p.foot.active) {
                // el pie de juego: sobre la pelota, al costado, atrás
                target.set(p.foot.forward(p), p.foot.pos.y - hipY, p.foot.side(p) - HIP_Z);
            } else {
                float phase = p.stride + side * MathUtils.PI;
                float apart = sign * 0.1f * p.stance;
                target.set(STEP * run * MathUtils.sin(phase), LIFT * run * Math.max(0, MathUtils.cos(phase)) - hipY, apart);
                if (side == 0 && p.foot.active) target.x -= 0.1f;  // el de apoyo, un poco atrás
            }
            aboveGrass(upright, hipY);
            ik(target, k, f);
            if (u.set(k).mul(joint).y < 0.03f) {
                // la rodilla quedaría bajo el pasto (tirado boca abajo): que doble para el otro lado
                float along = k.dot(f) / Math.max(f.len2(), 1e-6f);
                k.set(u.set(f).scl(2 * along).sub(k));
            }
            // muslo de la cadera a la rodilla, canilla de la rodilla al pie
            thigh[side].set(joint);
            orient(thigh[side], k);
            shin[side].set(joint).translate(k);
            orient(shin[side], u.set(f).sub(k));
            knee[side].set(k).mul(joint);
            sole[side].set(f).mul(joint);
        }
    }

    /** Que el pie no se meta bajo el pasto: si el objetivo queda abajo, lo sube al ras. */
    private void aboveGrass(boolean upright, float hipY) {
        if (upright) {
            if (target.y < -hipY) target.y = -hipY;
            return;
        }
        // en las acciones la cadera está inclinada: se pasa al jugador, se sube al ras y se vuelve
        target.mul(joint);
        if (target.y >= 0) {
            target.mul(inverse.set(joint).inv());
            return;
        }
        target.y = 0;
        target.mul(inverse.set(joint).inv());
    }

    /** Gira {@code m} para que lo que apunta hacia abajo (-y) apunte hacia {@code dir}. */
    private static void orient(Matrix4 m, Vector3 dir) {
        float len = dir.len();
        if (len < 1e-5f) return;
        float ux = dir.x / len, uy = dir.y / len, uz = MathUtils.clamp(dir.z / len, -1, 1);
        m.rotate(Vector3.Z, MathUtils.atan2(ux, -uy) * MathUtils.radDeg).rotate(Vector3.X, -MathUtils.asin(uz) * MathUtils.radDeg);
    }

    /**
     * IK de dos huesos desde la cadera: la rodilla y el pie para llegar a {@code target}. Si no
     * llega, la pierna estirada hacia ahí; la rodilla dobla hacia adelante (o hacia arriba si el pie
     * va adelante).
     */
    static void ik(Vector3 target, Vector3 knee, Vector3 foot) {
        float d = target.len();
        float ux, uy, uz;
        if (d < 1e-4f) {
            ux = 0;
            uy = -1;
            uz = 0;
        } else {
            ux = target.x / d;
            uy = target.y / d;
            uz = target.z / d;
        }
        float dc = MathUtils.clamp(d, Math.abs(THIGH - SHIN) + 0.08f, LEG - 0.001f);
        float cos = MathUtils.clamp((THIGH * THIGH + dc * dc - SHIN * SHIN) / (2 * THIGH * dc), -1, 1), sin = (float) Math.sqrt(1 - cos * cos);
        // hacia dónde dobla: lo que tiene de adelante, quitado lo que va hacia el pie
        float nx = 1 - ux * ux, ny = -ux * uy, nz = -ux * uz, nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nl < 1e-3f) {
            nx = -uy * ux;
            ny = 1 - uy * uy;
            nz = -uy * uz;
            nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        }
        nx /= nl;
        ny /= nl;
        nz /= nl;
        knee.set(ux * THIGH * cos + nx * THIGH * sin, uy * THIGH * cos + ny * THIGH * sin, uz * THIGH * cos + nz * THIGH * sin);
        foot.set(ux * dc, uy * dc, uz * dc);
    }
}
