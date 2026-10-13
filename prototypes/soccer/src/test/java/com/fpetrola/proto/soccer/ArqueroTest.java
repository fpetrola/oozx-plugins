package com.fpetrola.proto.soccer;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

import static com.fpetrola.proto.soccer.Guion.*;
import static org.junit.jupiter.api.Assertions.*;

/** El arquero: ataja solo, y cuando la tiene la juega como cualquiera. */
class ArqueroTest {
    /** Goles de {@code tiros} tiros fuertes al arco desde 16 a 24 m contra el arquero visitante, con esa dificultad. */
    static int goles(Difficulty d, int tiros, long semilla) {
        int goles = 0;
        MathUtils.random.setSeed(semilla);
        for (int i = 0; i < tiros; i++) {
            try (Guion g = new Guion(semilla + i)) {
                g.m.difficulty = d;
                Player s = g.local(9);
                g.despejar(s);
                float dist = MathUtils.random(16f, 24f), z0 = MathUtils.random(-8f, 8f);
                g.m.physics.place(s, Match.HALF_L - dist - 0.6f, z0);
                g.m.physics.placeBall(Match.HALF_L - dist, Ball.RADIUS, z0);
                g.durante(1f, NADA, false);  // que el arquero se acomode
                Vector2 dir = new Vector2(Match.HALF_L - g.m.ball.pos.x, MathUtils.random(-3.2f, 3.2f) - g.m.ball.pos.z).nor();
                g.m.kick(s, dir, MathUtils.random(20f, 28f), MathUtils.random(0.5f, 3f), 0);
                for (int k = 0; k < 150 && g.m.home.goals == 0 && Math.abs(g.m.ball.pos.x) < Match.HALF_L + 0.3f; k++) g.paso(NADA, false);
                goles += g.m.home.goals;
            }
        }
        return goles;
    }

    @Test
    void ataja() {
        int goles = goles(Difficulty.NORMAL, 60, 11);
        assertTrue(goles > 6 && goles < 36, goles + " goles de 60");
    }

    @Test
    void elDeLaMaquinaLaSacaEnseguida() {
        try (Guion g = new Guion(12)) {
            g.m.aiEnabled = true;
            Player k = g.visita(0);
            g.m.physics.placeBall(k.pos.x - 1, Ball.RADIUS, k.pos.y);
            g.m.catchBall(k);
            g.durante(3f, NADA, false);
            assertNotSame(k, g.m.owner);
            assertNotSame(k, g.m.ball.lastTouch);
        }
    }

    @Test
    void elDelHumanoLaLlevaComoCualquiera() {
        try (Guion g = new Guion(13)) {
            Player k = g.local(0);
            g.m.physics.placeBall(k.pos.x + 1, Ball.RADIUS, k.pos.y);
            g.m.catchBall(k);
            float x0 = k.pos.x;
            g.durante(1f, ARRIBA, false);
            assertSame(k, g.m.controlled);
            assertSame(k, g.m.owner);
            assertTrue(k.pos.x - x0 > 3f, "avanzó " + (k.pos.x - x0));
        }
    }

    @Test
    void enSuAreaNoSeLaSacan() {
        try (Guion g = new Guion(14)) {
            Player k = g.local(0), ellos = g.visita(9);
            g.despejar(ellos);
            g.m.physics.placeBall(k.pos.x + 1, Ball.RADIUS, k.pos.y);
            g.m.catchBall(k);
            g.m.physics.place(ellos, k.pos.x + 2.5f, k.pos.y);
            for (int i = 0; i < 90; i++) {
                ellos.steer(new Vector2(g.m.ball.pos.x - ellos.pos.x, g.m.ball.pos.z - ellos.pos.y).nor().scl(5));
                g.paso(NADA, false);
            }
            assertSame(k, g.m.owner);
        }
    }
}
