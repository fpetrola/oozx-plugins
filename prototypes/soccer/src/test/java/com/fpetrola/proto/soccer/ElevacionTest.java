package com.fpetrola.proto.soccer;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

import static com.fpetrola.proto.soccer.Guion.*;
import static org.junit.jupiter.api.Assertions.*;

/** La variante del Sensible con botón de elevación: C baja el punto donde le pega (más alto), X es la fuerza, las flechas de costado la comba. */
class ElevacionTest {
    /** Lo que hizo la pelota: lo más alto, dónde picó y cuánto se fue de costado a los 15 m. */
    record Tiro(float alto, float pica, float lado15) {
    }

    /**
     * Con la pelota al pie mirando hacia +x: C mantenido {@code elevar} segundos, X mantenido
     * {@code cargar} segundos sin flechas y después {@code combar} segundos con la flecha a la derecha.
     */
    static Tiro tirar(float elevar, float cargar, float combar) {
        try (Guion g = new Guion(110)) {
            g.m.scheme = new SensibleScheme(true);
            Player yo = g.local(6);
            g.despejar(yo);
            g.conPelota(yo, -30, 0, ARRIBA);
            g.durante(0.3f, NADA, false);
            for (int i = Math.round(elevar / CUADRO); i > 0; i--) g.paso(NADA, false, false, true);
            g.paso(NADA, true);
            for (int i = Math.round(cargar / CUADRO); i > 0; i--) g.paso(NADA, true);
            for (int i = Math.round(combar / CUADRO); i > 0; i--) g.paso(DERECHA, true);
            g.paso(NADA, false);
            float x0 = yo.pos.x, alto = 0, pica = Float.NaN, lado = Float.NaN;
            for (int i = 0; i < 240; i++) {
                g.paso(NADA, false);
                alto = Math.max(alto, g.m.ball.pos.y);
                if (Float.isNaN(pica) && alto > 0.4f && g.m.ball.pos.y < Ball.RADIUS + 0.05f) pica = g.m.ball.pos.x - x0;
                if (Float.isNaN(lado) && g.m.ball.pos.x - x0 > 15) lado = g.m.ball.pos.z;
            }
            return new Tiro(alto, pica, lado);
        }
    }

    @Test
    void sinCSaleRasante() {
        Tiro t = tirar(0, 0.4f, 0);
        System.out.printf("sin C: sube %.2f m%n", t.alto());
        assertTrue(t.alto() < 0.6f, "subió a " + t.alto());
    }

    @Test
    void cuantoMasCMasAlta() {
        Tiro poco = tirar(0.25f, 0.35f, 0), medio = tirar(0.5f, 0.35f, 0), todo = tirar(1f, 0.35f, 0);
        System.out.printf("C 0,25 s: sube %.2f m; 0,5 s: %.2f m; 1 s: %.2f m%n", poco.alto(), medio.alto(), todo.alto());
        assertTrue(poco.alto() < medio.alto() && medio.alto() < todo.alto(), "no sube más con más C");
        assertTrue(todo.alto() > 4f, "con todo C subió a " + todo.alto());
    }

    /** C y un toque de X: un globito suave en el lugar (el sombrero). */
    @Test
    void cYUnToqueEsUnGlobitoSuave() {
        Tiro t = tirar(0.6f, 0, 0);
        System.out.printf("C y un toque: sube %.2f m, pica a %.1f m%n", t.alto(), t.pica());
        assertTrue(t.alto() > 0.6f, "subió a " + t.alto());
        assertTrue(t.pica() < 5f, "picó a " + t.pica());
    }

    @Test
    void laFuerzaEsDeXYNoDeC() {
        Tiro flojo = tirar(0.6f, 0.05f, 0), fuerte = tirar(0.6f, 0.45f, 0);
        System.out.printf("misma elevación: flojo pica a %.1f m, fuerte a %.1f m%n", flojo.pica(), fuerte.pica());
        assertTrue(fuerte.pica() > flojo.pica() * 2, "la fuerza no cambió el tiro: " + flojo.pica() + " / " + fuerte.pica());
    }

    @Test
    void laCombaComoEnElSensible() {
        Tiro t = tirar(0.2f, 0.4f, 0.25f);
        System.out.printf("flecha a la derecha cargando: a los 15 m va %.2f m de costado%n", t.lado15());
        assertTrue(t.lado15() > 1.5f, "no curvó a la derecha: " + t.lado15());
    }

    @Test
    void despuesDelGolpeElPuntoVuelveArriba() {
        try (Guion g = new Guion(111)) {
            SensibleScheme s = new SensibleScheme(true);
            g.m.scheme = s;
            Player yo = g.local(6);
            g.despejar(yo);
            g.conPelota(yo, 0, 0, ARRIBA);
            g.durante(0.2f, NADA, false);
            for (int i = 0; i < 30; i++) g.paso(NADA, false, false, true);
            assertTrue(s.lowered > 0.4f, "C no bajó el punto: " + s.lowered);
            g.paso(NADA, true);
            g.durante(0.3f, NADA, true);
            g.paso(NADA, false);
            g.paso(NADA, false);
            assertEquals(0, s.lowered, 1e-4f);
        }
    }

    @Test
    void tresMinutosConTeclasAlAzarSinRomperse() {
        MathUtils.random.setSeed(12);
        Match m = new Match();
        try {
            m.scheme = new SensibleScheme(true);
            Pad pad = new Pad();
            Vector2 dir = new Vector2();
            boolean x = false, z = false, c = false;
            for (int cuadro = 0; cuadro < 60 * 60 * 3; cuadro++) {
                if (cuadro % 20 == 0) {
                    int d = MathUtils.random(8);
                    dir.set(d == 8 ? 0 : MathUtils.cosDeg(d * 45), d == 8 ? 0 : MathUtils.sinDeg(d * 45));
                }
                if (cuadro % 7 == 0) x = MathUtils.randomBoolean(0.25f);
                if (cuadro % 11 == 0) z = MathUtils.randomBoolean(0.15f);
                if (cuadro % 13 == 0) c = MathUtils.randomBoolean(0.3f);
                pad.set(dir, x, z, c);
                m.update(pad, 1 / 60f);
                assertFalse(Float.isNaN(m.ball.pos.x), "pelota sin posición en el cuadro " + cuadro);
                assertTrue(m.ball.vel.len() < 45, "pelota demasiado rápida: " + m.ball.vel.len());
            }
        } finally {
            m.dispose();
        }
    }
}
