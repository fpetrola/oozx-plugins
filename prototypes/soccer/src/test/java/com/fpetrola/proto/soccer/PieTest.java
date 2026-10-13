package com.fpetrola.proto.soccer;

import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

import static com.fpetrola.proto.soccer.Guion.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * La etapa 1 del plan: lo que sale de combinar la suela (Z), el golpe (X) y las flechas, sin tocar
 * nada más que botones. Cada prueba es una fila de la tabla de {@code PLAN.md}.
 */
class PieTest {
    /** Uno solo con la pelota al pie en el medio de la cancha, mirando hacia +x; se acomoda un momento. */
    private static Player solo(Guion g) {
        Player yo = g.local(6);
        g.despejar(yo);
        g.conPelota(yo, 0, 0, ARRIBA);
        g.durante(0.3f, NADA, false);
        return yo;
    }

    /** Cuánto a la derecha de la línea (que pasa por {@code x0, z0} hacia {@code mira}) está la pelota. */
    private static float derecha(Guion g, float x0, float z0, Vector2 mira) {
        return (g.m.ball.pos.x - x0) * -mira.y + (g.m.ball.pos.z - z0) * mira.x;
    }

    /** Un toque de X: un cuadro apretado y uno suelto. */
    private static void toque(Guion g, Vector2 flechas) {
        g.paso(flechas, true);
        g.paso(flechas, false);
    }

    @Test
    void pisarla() {
        try (Guion g = new Guion(31)) {
            Player yo = solo(g);
            g.durante(1f, ARRIBA, false);
            assertTrue(yo.vel.len() > 5.5f, "corre a " + yo.vel.len());
            g.durante(0.5f, NADA, false, true);
            float d = g.m.ball.dist2D(yo.pos);
            System.out.printf("pisarla: velocidad %.2f, pelota a %.2f%n", yo.vel.len(), d);
            assertTrue(yo.vel.len() < 1f, "va a " + yo.vel.len());
            assertTrue(d < 0.4f, "la pelota quedó a " + d);
            assertSame(yo, g.m.owner);
        }
    }

    @Test
    void croqueta() {
        try (Guion g = new Guion(32)) {
            Player yo = solo(g);
            g.durante(1f, ARRIBA, false);
            float x0 = yo.pos.x, z0 = yo.pos.y;
            Vector2 mira = new Vector2(yo.facing);
            float antes = derecha(g, x0, z0, mira);
            g.durante(0.2f, DERECHA, false, true);
            // al soltar, la pelota sigue un momento con lo que le dio el pie
            float corrida = derecha(g, x0, z0, mira) - antes;
            for (int i = 0; i < 60; i++) {
                g.paso(ARRIBA, false);
                corrida = Math.max(corrida, derecha(g, x0, z0, mira) - antes);
            }
            System.out.printf("croqueta: de costado %.2f%n", corrida);
            assertTrue(corrida > 0.6f && corrida < 1f, "se corrió " + corrida);
            assertSame(yo, g.m.owner, "la tiene que seguir teniendo");
        }
    }

    @Test
    void elastica() {
        try (Guion g = new Guion(33)) {
            Player yo = solo(g);
            float x0 = yo.pos.x, z0 = yo.pos.y;
            Vector2 mira = new Vector2(yo.facing);
            float derecha = -9, izquierda = 9;
            for (int i = 0; i < 7; i++) {
                g.paso(DERECHA, false, true);
                derecha = Math.max(derecha, derecha(g, x0, z0, mira));
            }
            for (int i = 0; i < 7; i++) {
                g.paso(IZQUIERDA, false, true);
                izquierda = Math.min(izquierda, derecha(g, x0, z0, mira));
            }
            for (int i = 0; i < 24; i++) {
                g.paso(NADA, false);
                izquierda = Math.min(izquierda, derecha(g, x0, z0, mira));
            }
            g.durante(0.6f, NADA, false);
            System.out.printf("elástica: a la derecha %.2f, después a la izquierda %.2f%n", derecha, izquierda);
            assertTrue(derecha >= 0.4f, "a la derecha llegó a " + derecha);
            assertTrue(izquierda < 0, "no volvió a la izquierda del eje: " + izquierda);
            assertSame(yo, g.m.owner, "la tiene que seguir teniendo");
        }
    }

    @Test
    void giro() {
        try (Guion g = new Guion(34)) {
            Player yo = solo(g);
            Vector2 antes = new Vector2(yo.facing);
            g.durante(0.5f, ABAJO, false, true);
            float dot = yo.facing.dot(antes), pie = Vector2.dst(yo.foot.pos.x, yo.foot.pos.z, g.m.ball.pos.x, g.m.ball.pos.z);
            System.out.printf("giro: mira %.2f respecto de antes, pelota a %.2f del pie%n", dot, pie);
            assertTrue(dot < -0.5f, "mira " + dot);
            assertTrue(pie < 0.3f, "la pelota quedó a " + pie + " del pie");
            assertSame(yo, g.m.owner);
        }
    }

    /** Pisarla para atrás un momento, soltar y un toque: la pelota sube. Devuelve hasta dónde subió. */
    private static float levantar(Guion g, Player yo) {
        g.durante(0.2f, ABAJO, false, true);
        g.paso(NADA, false);
        toque(g, NADA);
        float alto = 0;
        for (int i = 0; i < 30; i++) {
            g.paso(NADA, false);
            alto = Math.max(alto, g.m.ball.pos.y);
        }
        return alto;
    }

    @Test
    void levantarla() {
        try (Guion g = new Guion(35)) {
            Player yo = solo(g);
            float alto = levantar(g, yo);
            float cae = Float.NaN;
            for (int i = 0; i < 120 && Float.isNaN(cae); i++) {
                g.paso(NADA, false);
                if (g.m.ball.pos.y < Ball.RADIUS + 0.03f) cae = g.m.ball.dist2D(yo.pos);
            }
            System.out.printf("levantarla: subió a %.2f, cayó a %.2f del jugador%n", alto, cae);
            assertTrue(alto > 0.8f && alto < 1.5f, "subió a " + alto);
            assertTrue(cae < 1.5f, "cayó a " + cae);
        }
    }

    /**
     * El jueguito: después de levantarla, un toque cada vez que baja de 0,7 m. Devuelve cuántos
     * toques seguidos hizo sin que toque el piso, hasta {@code toques}.
     */
    private static int jueguito(Guion g, Player yo, int toques) {
        int hechos = 0;
        float antes = g.m.ball.pos.y;
        boolean apretado = false;
        for (int i = 0; i < 60 * 8 && hechos < toques; i++) {
            float y = g.m.ball.pos.y;
            if (y < Ball.RADIUS + 0.03f) break;
            boolean x = !apretado && y < antes && y < 0.7f;
            if (x) {
                apretado = true;
                hechos++;
            } else if (y > antes) {
                apretado = false;
            }
            antes = y;
            g.paso(NADA, x);
        }
        return hechos;
    }

    @Test
    void jueguito() {
        try (Guion g = new Guion(36)) {
            Player yo = solo(g);
            Vector2 desde = new Vector2(yo.pos);
            levantar(g, yo);
            int toques = jueguito(g, yo, 5);
            // el quinto toque la vuelve a subir: que no caiga antes
            for (int i = 0; i < 30; i++) {
                g.paso(NADA, false);
                if (g.m.ball.pos.y < Ball.RADIUS + 0.03f) toques = Math.min(toques, 4);
            }
            float movido = yo.pos.dst(desde);
            System.out.printf("jueguito: %d toques, se movió %.2f%n", toques, movido);
            assertEquals(5, toques, "toques seguidos sin que pique");
            assertTrue(movido < 1.5f, "se movió " + movido);
        }
    }

    @Test
    void sombrero() {
        try (Guion g = new Guion(37)) {
            Player yo = solo(g);
            levantar(g, yo);
            assertEquals(2, jueguito(g, yo, 2));
            // Cuando baja de 1,2 m, X con flecha adelante 0,2 s.
            float antes = g.m.ball.pos.y;
            for (int i = 0; i < 120; i++) {
                float y = g.m.ball.pos.y;
                if (y < antes && y < 1.2f) break;
                antes = y;
                g.paso(NADA, false);
            }
            g.durante(0.2f, ARRIBA, true);
            g.paso(NADA, false);
            float x0 = g.m.ball.pos.x, alto = 0, pica = Float.NaN;
            for (int i = 0; i < 240 && Float.isNaN(pica); i++) {
                g.paso(NADA, false);
                alto = Math.max(alto, g.m.ball.pos.y);
                if (alto > 1f && g.m.ball.pos.y < Ball.RADIUS + 0.05f) pica = g.m.ball.pos.x - x0;
            }
            System.out.printf("sombrero: pico %.2f, pica a %.2f%n", alto, pica);
            assertTrue(alto >= 2f, "pico " + alto);
            assertTrue(pica > 4f && pica < 8f, "picó a " + pica);
        }
    }

    @Test
    void controlOrientado() {
        try (Guion g = new Guion(38)) {
            Player pasador = g.local(5), yo = g.local(6);
            g.despejar(pasador, yo);
            g.conPelota(pasador, -12, 0, ARRIBA);
            g.m.physics.place(yo, 0, 0);
            yo.facing.set(-1, 0);
            g.durante(0.2f, NADA, false);
            toque(g, ARRIBA);
            assertSame(yo, g.m.controlled, "el pase lo maneja el que lo recibe");
            Vector2 derecha = desde(yo, DERECHA), mira = new Vector2(yo.facing);
            float x0 = yo.pos.x, z0 = yo.pos.y;
            // Z y la flecha cuando falta poco para que llegue, y un rato después de que llegó.
            int i = 0;
            while (g.m.ball.dist2D(yo.pos) > 3f && i++ < 240) g.paso(NADA, false);
            int llego = -1;
            for (i = 0; i < 60; i++) {
                g.paso(derecha, false, true);
                if (llego < 0 && g.m.owner == yo) llego = i;
                if (llego >= 0 && i - llego > 18) break;
            }
            g.durante(0.4f, NADA, false);
            float corrida = derecha(g, x0, z0, mira);
            System.out.printf("control orientado: llegó en el cuadro %d, la pelota quedó %.2f al costado%n", llego, corrida);
            assertTrue(llego >= 0, "no la recibió");
            // mira hacia -x: su derecha es -z, y "derecha" de la línea hacia donde mira es lo mismo
            assertTrue(corrida > 0.8f && corrida < 1.5f, "quedó a " + corrida);
            assertSame(yo, g.m.owner);
        }
    }

    @Test
    void taco() {
        try (Guion g = new Guion(39)) {
            Player yo = solo(g);
            Vector2 antes = new Vector2(yo.facing);
            toque(g, ABAJO);
            g.paso(NADA, false);
            Vector2 v = new Vector2(g.m.ball.vel.x, g.m.ball.vel.z);
            float dot = v.cpy().nor().dot(antes);
            System.out.printf("taco: sale a %.2f m/s, %.2f respecto de hacia donde miraba%n", v.len(), dot);
            assertTrue(dot < -0.7f, "sale hacia " + dot);
            assertTrue(v.len() > 5f && v.len() < 10f, "sale a " + v.len());
        }
    }

    @Test
    void autopase() {
        try (Guion g = new Guion(40)) {
            Player yo = solo(g);
            float x0 = g.m.ball.pos.x;
            toque(g, NADA);
            g.paso(NADA, false);
            Trajectory t = g.m.trajectory;
            float rueda = t.x[Trajectory.N - 1] - x0;
            float tarda = -1;
            for (int i = 0; i < 120 && tarda < 0; i++) {
                g.paso(NADA, false);
                if (g.m.owner == yo) tarda = (i + 2) * CUADRO;
            }
            System.out.printf("autopase: rodaría %.2f m, la vuelve a tener en %.2f s%n", rueda, tarda);
            assertTrue(rueda > 6f && rueda < 8f, "rodaría " + rueda);
            assertTrue(tarda > 0 && tarda < 2f, "la volvió a tener en " + tarda);
        }
    }
}
