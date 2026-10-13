package com.fpetrola.proto.soccer;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;

import static com.fpetrola.proto.soccer.Guion.*;
import static org.junit.jupiter.api.Assertions.*;

/** La etapa 3 del plan: las piernas con rodillas nunca quedan bajo el pasto ni se rompen. */
class PoseTest {
    /** Revisa la pose de todos: rodillas y pies arriba del pasto, sin NaN, y los huesos de su largo. */
    private static void revisar(Match m, Pose pose, String cuando) {
        for (Player p : m.players) {
            pose.compute(p, m.holder == p, m.throwing(p));
            for (int i = 0; i < 2; i++) {
                Vector3 k = pose.knee[i], f = pose.sole[i];
                String quien = "#" + p.number + " " + p.team.name + " " + p.act + " pierna " + i + " " + cuando;
                assertFalse(Float.isNaN(k.x + k.y + k.z + f.x + f.y + f.z), "NaN en " + quien);
                assertTrue(f.y > -0.02f, "pie bajo el pasto (" + f.y + ") en " + quien);
                assertTrue(k.y > 0, "rodilla bajo el pasto (" + k.y + ") en " + quien);
                assertEquals(Pose.SHIN, k.dst(f), 0.12f, "canilla de otro largo en " + quien);
                Vector3 h = pose.hand[i];
                assertFalse(Float.isNaN(h.x + h.y + h.z), "NaN en la mano de " + quien);
                assertTrue(h.y > -0.1f, "mano bajo el pasto (" + h.y + ") en " + quien);
            }
        }
    }

    @Test
    void tresMinutosDeTeclasAlAzarConLasPiernasEnSuLugar() {
        MathUtils.random.setSeed(8);
        Match m = new Match();
        Pose pose = new Pose();
        try {
            Pad pad = new Pad();
            Vector2 dir = new Vector2();
            boolean x = false, z = false;
            for (int cuadro = 0; cuadro < 60 * 60 * 3; cuadro++) {
                if (cuadro % 20 == 0) {
                    int d = MathUtils.random(8);
                    dir.set(d == 8 ? 0 : MathUtils.cosDeg(d * 45), d == 8 ? 0 : MathUtils.sinDeg(d * 45));
                }
                if (cuadro % 7 == 0) x = MathUtils.randomBoolean(0.25f);
                if (cuadro % 11 == 0) z = MathUtils.randomBoolean(0.3f);
                pad.set(dir, x, z);
                m.update(pad, 1 / 60f);
                revisar(m, pose, "en el cuadro " + cuadro);
            }
        } finally {
            m.dispose();
        }
    }

    @Test
    void laSuelaPoneElPieSobreLaPelota() {
        try (Guion g = new Guion(70)) {
            Player yo = g.local(6);
            g.despejar(yo);
            g.conPelota(yo, 0, 0, ARRIBA);
            g.durante(0.3f, NADA, false);
            g.durante(0.3f, DERECHA, false, true);
            Pose pose = new Pose();
            pose.compute(yo, false, false);
            // el pie derecho, pasado a la cancha, arriba de la pelota
            Vector3 pie = new Vector3(pose.sole[1]).mul(pose.instance);
            float d = Vector2.dst(pie.x, pie.z, g.m.ball.pos.x, g.m.ball.pos.z);
            System.out.printf("suela: el pie a %.2f de la pelota, a %.2f de alto%n", d, pie.y);
            assertTrue(d < 0.15f, "el pie quedó a " + d + " de la pelota");
            assertEquals(2 * Ball.RADIUS, pie.y, 0.05f);
        }
    }
}
