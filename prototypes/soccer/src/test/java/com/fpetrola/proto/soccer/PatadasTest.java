package com.fpetrola.proto.soccer;

import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

import static com.fpetrola.proto.soccer.Guion.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Los tiros cargados: se aprieta X mirando al arco, se mantiene para cargar fuerza, se toca una
 * flecha (desde ahí la fuerza queda fija y la flecha le da forma) y se suelta. Se mide cómo sale
 * la pelota de verdad (Bullet) y si la flecha que se mostraba dijo la verdad.
 */
class PatadasTest {
    /** Lo que se vio en la flecha justo antes de soltar, y lo que hizo la pelota después: lo más alto, el desvío a los 25 m y dónde picó. */
    record Tiro(float fuerza, float altura, float comba, float alto, float desvio25, float pica, float errorFlecha) {
    }

    /**
     * Desde 30 m antes de la mitad, mirando al arco de la derecha: X con la flecha hacia el arco, se
     * suelta la flecha y se mantiene X {@code cargar} segundos, y después {@code formar} segundos
     * con la flecha {@code forma} (relativa al tiro: x adelante, y derecha).
     */
    static Tiro tirar(float cargar, Vector2 forma, float formar) {
        try (Guion g = new Guion(7)) {
            Player yo = g.local(6);
            g.despejar(yo);
            g.conPelota(yo, -30, 0, ARRIBA);
            g.durante(0.3f, ARRIBA, false);
            float x0 = g.m.ball.pos.x, z0 = g.m.ball.pos.z;
            ShotPreview visto = null;
            float[] puntos = null;
            int n = 0;
            g.paso(ARRIBA, true);
            Vector2 flecha = desde(yo, forma);
            int cargando = Math.round(cargar / CUADRO), total = cargando + Math.round(formar / CUADRO);
            for (int i = 1; i < total; i++) {
                g.paso(i < cargando ? NADA : flecha, true);
                ShotPreview s = g.m.scheme.shot();
                if (s != null && !s.pass) {
                    visto = s;
                    puntos = s.xyz.clone();
                    n = s.count;
                }
            }
            assertNotNull(visto, "mientras se carga se tiene que ver la flecha del tiro");
            float fuerza = visto.power, altura = visto.elevation, comba = visto.curl;
            g.paso(NADA, false);
            float alto = 0, desvio = Float.NaN, pica = Float.NaN, error = 0;
            int k = 1;
            for (int i = 0; i < 300; i++) {
                g.pad.set(NADA, false);
                g.m.update(g.pad, 1 / 120f);
                alto = Math.max(alto, g.m.ball.pos.y);
                if (Float.isNaN(desvio) && g.m.ball.pos.x - x0 > 25) desvio = g.m.ball.pos.z - z0;
                if (Float.isNaN(pica) && alto > 0.5f && g.m.ball.pos.y < Ball.RADIUS + 0.05f) pica = g.m.ball.pos.x - x0;
                if (i % 4 == 3 && k < n) {
                    float dx = g.m.ball.pos.x - puntos[k * 3], dy = g.m.ball.pos.y - puntos[k * 3 + 1], dz = g.m.ball.pos.z - puntos[k * 3 + 2];
                    error = Math.max(error, (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
                    k++;
                }
            }
            return new Tiro(fuerza, altura, comba, alto, desvio, pica, error);
        }
    }

    @Test
    void sinFlechasSaleAMediaAlturaYRecto() {
        Tiro t = tirar(0.4f, NADA, 0);
        assertTrue(t.alto() < 1.5f, "alto " + t.alto());
        assertEquals(0, t.desvio25(), 0.05f);
    }

    @Test
    void adelanteEsRasante() {
        Tiro t = tirar(0.46f, ARRIBA, 0.2f);
        assertTrue(t.alto() < 0.45f, "alto " + t.alto());
    }

    @Test
    void atrasEsGlobo() {
        Tiro media = tirar(0.3f, ABAJO, 0.2f), globo = tirar(0.45f, ABAJO, 0.35f);
        assertTrue(media.alto() > 1.5f && media.alto() < globo.alto(), "media " + media.alto() + ", globo " + globo.alto());
        assertTrue(globo.alto() > 4f && globo.alto() < 14f, "globo " + globo.alto());
    }

    @Test
    void lasCombasSonSimetricasYSeNotan() {
        Tiro der = tirar(0.46f, DERECHA, 0.2f), izq = tirar(0.46f, IZQUIERDA, 0.2f);
        assertTrue(der.desvio25() > 3f, "derecha " + der.desvio25());
        assertEquals(-der.desvio25(), izq.desvio25(), 0.05f);
    }

    @Test
    void laFlechaDiceLaVerdad() {
        for (Vector2 forma : new Vector2[]{NADA, ARRIBA, ABAJO, DERECHA, new Vector2(-1, 1).nor()}) {
            Tiro t = tirar(0.46f, forma, 0.2f);
            assertTrue(t.errorFlecha() < 1f, "con " + forma + " la pelota se apartó " + t.errorFlecha() + " m de la flecha");
        }
    }

    /** Lo que antes no salía: alto sin fuerza. Apenas se aprieta X, la flecha atrás: la fuerza queda en lo mínimo y sube cuanto se quiera. */
    @Test
    void unGlobitoSuaveNoNecesitaFuerza() {
        Tiro t = tirar(0.14f, ABAJO, 0.35f);
        System.out.printf("globito: fuerza %.2f, altura %.0f grados, sube %.2f m, pica a %.1f m%n", t.fuerza(), t.altura(), t.alto(), t.pica());
        assertTrue(t.fuerza() < 0.05f, "fuerza " + t.fuerza());
        assertTrue(t.altura() > 55f, "altura " + t.altura());
        assertTrue(t.pica() < 6f, "picó a " + t.pica());
    }

    /** Y al revés: primero se carga (sin flechas) y después se curva, sin que la comba le sume ni le saque fuerza. */
    @Test
    void laFuerzaQuedaFijaAlDarleForma() {
        Tiro sola = tirar(0.4f, NADA, 0), curvada = tirar(0.4f, DERECHA, 0.4f);
        System.out.printf("fuerza sin forma %.2f, con comba después %.2f (comba %.0f)%n", sola.fuerza(), curvada.fuerza(), curvada.comba());
        assertEquals(sola.fuerza(), curvada.fuerza(), 0.03f, "la comba le cambió la fuerza");
        assertTrue(curvada.comba() > 60f, "comba " + curvada.comba());
    }
}
