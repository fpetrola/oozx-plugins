package com.fpetrola.proto.soccer;

import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

import static com.fpetrola.proto.soccer.Guion.*;
import static org.junit.jupiter.api.Assertions.*;

/** El control nuevo: X potencia, C más abajo (más alta), flechas de costado mientras se arma = comba; sale al soltar los dos. */
class GolpeTest {
    /** Lo que hizo la pelota: lo más alto, dónde picó, y cuánto se fue de costado a los 15 m. */
    record Tiro(float alto, float pica, float lado15) {
    }

    /** Un cuadro de un guion: flechas, X, C. */
    record Paso(Vector2 flechas, boolean x, boolean c) {
    }

    private static GolpeScheme listo(Guion g) {
        GolpeScheme s = new GolpeScheme();
        g.m.scheme = s;
        Player yo = g.local(6);
        g.despejar(yo);
        g.conPelota(yo, -30, 0, ARRIBA);
        g.durante(0.3f, NADA, false);
        return s;
    }

    /** {@code segundos} con esas teclas. */
    private static void mantener(Guion g, float segundos, Vector2 flechas, boolean x, boolean c) {
        for (int i = Math.round(segundos / CUADRO); i > 0; i--) g.paso(flechas, x, false, c);
    }

    /** Suelta todo y mira la pelota. */
    private static Tiro soltar(Guion g) {
        float x0 = g.m.ball.pos.x, alto = 0, pica = Float.NaN, lado = Float.NaN;
        for (int i = 0; i < 240; i++) {
            g.paso(NADA, false, false, false);
            alto = Math.max(alto, g.m.ball.pos.y);
            if (Float.isNaN(pica) && alto > 0.4f && g.m.ball.pos.y < Ball.RADIUS + 0.05f) pica = g.m.ball.pos.x - x0;
            if (Float.isNaN(lado) && g.m.ball.pos.x - x0 > 15) lado = g.m.ball.pos.z;
        }
        return new Tiro(alto, pica, lado);
    }

    @Test
    void conXSolaSaleRasanteYFuerte() {
        try (Guion g = new Guion(120)) {
            listo(g);
            mantener(g, 0.5f, NADA, true, false);
            Tiro t = soltar(g);
            System.out.printf("X sola: sube %.2f m, a los 15 m va %.2f de costado%n", t.alto(), t.lado15());
            assertTrue(t.alto() < 0.6f, "subió a " + t.alto());
            assertEquals(0, t.lado15(), 0.05f);
        }
    }

    @Test
    void cuantoMasCMasAlta() {
        float[] altos = new float[3];
        float[] c = {0.2f, 0.5f, 1f};
        for (int i = 0; i < 3; i++) {
            try (Guion g = new Guion(121)) {
                listo(g);
                mantener(g, c[i], NADA, false, true);
                mantener(g, 0.3f, NADA, true, false);
                altos[i] = soltar(g).alto();
            }
        }
        System.out.printf("C 0,2 s: sube %.2f m; 0,5 s: %.2f m; 1 s: %.2f m%n", altos[0], altos[1], altos[2]);
        assertTrue(altos[0] < altos[1] && altos[1] < altos[2], "no sube más con más C");
        assertTrue(altos[2] > 3f, "con todo C subió a " + altos[2]);
    }

    @Test
    void laCombaVaParaElLadoDeLaFlechaYEsSimetrica() {
        float der, izq;
        try (Guion g = new Guion(122)) {
            listo(g);
            mantener(g, 0.4f, NADA, true, false);
            mantener(g, 0.3f, DERECHA, true, false);
            der = soltar(g).lado15();
        }
        try (Guion g = new Guion(122)) {
            listo(g);
            mantener(g, 0.4f, NADA, true, false);
            mantener(g, 0.3f, IZQUIERDA, true, false);
            izq = soltar(g).lado15();
        }
        System.out.printf("comba: con la flecha a la derecha va %.2f m, a la izquierda %.2f m%n", der, izq);
        assertTrue(der > 2f, "a la derecha " + der);
        assertEquals(-der, izq, 0.05f);
    }

    /** Lo que antes fallaba: lo mismo apretado en otro orden sale igual, y la potencia llega al máximo aunque haya flechas. */
    @Test
    void elOrdenDeLasTeclasNoCambiaElGolpe() {
        float[] a = golpe(new Paso[]{new Paso(NADA, true, false), new Paso(DERECHA, true, true), new Paso(NADA, false, true)});
        float[] b = golpe(new Paso[]{new Paso(NADA, false, true), new Paso(DERECHA, true, true), new Paso(NADA, true, false)});
        System.out.printf("X primero: potencia %.2f altura %.2f comba %.1f; C primero: %.2f %.2f %.1f%n", a[0], a[1], a[2], b[0], b[1], b[2]);
        assertArrayEquals(a, b, 1e-4f);
        try (Guion g = new Guion(123)) {
            GolpeScheme s = listo(g);
            mantener(g, 0.3f, NADA, true, false);
            mantener(g, 0.5f, DERECHA, true, false);
            assertEquals(1, s.power, 1e-4f, "con la flecha apretada la potencia se trabó");
        }
    }

    /** Cada tramo dura 0,25 s; devuelve potencia, cuánto bajó y comba justo antes de soltar. */
    private static float[] golpe(Paso[] pasos) {
        try (Guion g = new Guion(124)) {
            GolpeScheme s = listo(g);
            for (Paso p : pasos) mantener(g, 0.25f, p.flechas(), p.x(), p.c());
            return new float[]{s.power, s.lowered, s.curl};
        }
    }

    @Test
    void saleAlSoltarLosDosYNoHayTiempoMaximo() {
        try (Guion g = new Guion(125)) {
            listo(g);
            Player yo = g.local(6);
            mantener(g, 3f, NADA, true, true);
            assertSame(yo, g.m.owner, "salió antes de soltar");
            mantener(g, 0.2f, NADA, false, true);  // suelta X, C sigue
            assertSame(yo, g.m.owner, "salió con C todavía apretado");
            g.paso(NADA, false, false, false);
            g.paso(NADA, false, false, false);
            assertNotSame(yo, g.m.owner, "no salió al soltar los dos");
            assertTrue(g.m.ball.vel.len() > 10, "salió floja: " + g.m.ball.vel.len());
        }
    }

    @Test
    void laFlechaMuestraLoQueSaleYDiceLaVerdad() {
        try (Guion g = new Guion(126)) {
            listo(g);
            mantener(g, 0.3f, NADA, true, true);
            mantener(g, 0.2f, IZQUIERDA, true, false);
            ShotPreview s = g.m.scheme.shot();
            assertNotNull(s, "armando el golpe se tiene que ver la flecha");
            assertTrue(s.power > 0.3f && s.elevation > 10 && s.curl < -10, "la flecha no muestra potencia, altura y comba");
            float[] puntos = s.xyz.clone();
            int n = s.count;
            g.paso(NADA, false, false, false);
            float error = 0;
            for (int i = 0, k = 1; i < 240 && k < n; i++) {
                g.pad.set(NADA, false, false, false);
                g.m.update(g.pad, 1 / 120f);
                if (i % 4 == 3) {
                    float dx = g.m.ball.pos.x - puntos[k * 3], dy = g.m.ball.pos.y - puntos[k * 3 + 1], dz = g.m.ball.pos.z - puntos[k * 3 + 2];
                    error = Math.max(error, (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
                    k++;
                }
            }
            System.out.printf("la pelota se apartó a lo sumo %.2f m de la flecha%n", error);
            // la flecha es la del último cuadro apretado; el golpe sale un cuadro después, con la pelota un poco corrida
            assertTrue(error < 1.5f, "se apartó " + error);
        }
    }
}
