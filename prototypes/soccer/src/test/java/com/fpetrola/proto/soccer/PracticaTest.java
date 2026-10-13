package com.fpetrola.proto.soccer;

import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

import static com.fpetrola.proto.soccer.Guion.*;
import static org.junit.jupiter.api.Assertions.*;

/** La arena de práctica, el cambio de jugador con Z en el control nuevo, y el arquero que juega como uno más. */
class PracticaTest {
    /** Un partido en la arena de práctica, con el control nuevo. */
    private static Guion arena(long semilla) {
        Guion g = new Guion(semilla);
        g.m.scheme = new GolpeScheme();
        g.m.setPractice(true);
        g.paso(NADA, false);
        return g;
    }

    @Test
    void sinRivalesDeCampoYConLaPelotaEnLosPies() {
        try (Guion g = arena(130)) {
            for (Player p : g.m.away.players) {
                if (p.keeper) assertFalse(p.benched);
                else assertTrue(p.benched && Math.abs(p.pos.y) > Match.HALF_W, "#" + p.number + " sigue en la cancha");
            }
            assertNull(g.m.setPiece);
            assertSame(g.m.controlled, g.m.owner, "la pelota tiene que estar en los pies del humano");
        }
    }

    /** Pateada con {@code vel} desde (x, z): si en tres segundos salió de la cancha o cobraron algo. */
    private static boolean sale(float x, float z, Vector2 dir, float speed, float up) {
        try (Guion g = arena(131)) {
            g.m.physics.placeBall(x, Ball.RADIUS, z);
            g.m.kick(g.local(6), dir, speed, up, 0);
            for (int i = 0; i < 180; i++) {
                g.paso(NADA, false);
                boolean afuera = Math.abs(g.m.ball.pos.z) > Match.HALF_W + Physics.ARENA_SIDE + 0.5f
                        || Math.abs(g.m.ball.pos.x) > Match.HALF_L + Physics.ARENA_END + 0.5f;
                if (afuera || g.m.setPiece != null || g.m.messageTime > 0) return true;  // salió, o cobraron algo (gol, otra vez)
            }
            return false;
        }
    }

    @Test
    void laPelotaNoSaleNiPorElCostadoNiPorElFondoNiPorArriba() {
        assertFalse(sale(0, 20, new Vector2(0.2f, 1).nor(), 28, 3), "salió por el costado");
        assertFalse(sale(40, 10, new Vector2(1, 0.2f).nor(), 25, 2), "salió por el fondo, al lado del arco");
        assertFalse(sale(30, 0, new Vector2(1, 0).nor(), 25, 14), "salió por arriba del arco");
    }

    /** Lo que se escuchó. */
    private static final class Oido implements Match.Ears {
        final java.util.List<Match.Noise> oido = new java.util.ArrayList<>();

        public void hear(Match.Noise noise, float strength) {
            oido.add(noise);
        }
    }

    @Test
    void laPelotaSuenaAlPatearlaYAlChocar() {
        try (Guion g = arena(136)) {
            Oido oido = new Oido();
            g.m.ears = oido;
            // contra la pared
            g.m.physics.placeBall(0, 1, 30);
            g.m.kick(g.local(6), new Vector2(0, 1), 15, 1, 0);
            g.durante(1f, NADA, false);
            assertTrue(oido.oido.contains(Match.Noise.KICK), "la patada no sonó");
            assertTrue(oido.oido.contains(Match.Noise.WALL), "la pared no sonó: " + oido.oido);
            // contra el palo
            g.m.physics.placeBall(Match.HALF_L - 6, 1, Match.GOAL_HALF + Physics.POST);
            g.m.kick(g.local(6), new Vector2(1, 0), 15, 0.3f, 0);
            g.durante(1f, NADA, false);
            assertTrue(oido.oido.contains(Match.Noise.POST), "el palo no sonó: " + oido.oido);
            // contra un jugador
            Player el = g.local(4);
            g.m.physics.place(el, 10, -10);
            // al pecho (abajo la pararía con el pie), y de un rival: la de un compañero lo atraviesa
            g.m.physics.placeBall(7, 1.2f, -10);
            g.m.kick(g.visita(0), new Vector2(1, 0), 20, 0, 0);
            g.durante(1f, NADA, false);
            assertTrue(oido.oido.contains(Match.Noise.BODY), "el jugador no sonó: " + oido.oido);
        }
    }

    @Test
    void lasVelocidadesSeAjustanEnVivo() {
        try (Guion g = arena(137)) {
            GolpeScheme s = (GolpeScheme) g.m.scheme;
            s.fill = 0.3f;
            for (int i = 0; i < 20; i++) g.paso(NADA, true, false, false);  // 0,33 s
            assertEquals(1, s.power, 1e-4f, "con la potencia en 0,3 s no se llenó");
            s.sweep = 2f;
            for (int i = 0; i < 60; i++) g.paso(NADA, true, false, true);  // 1 s de C
            assertEquals(0.5f, s.lowered, 0.02f, "con la altura en 2 s, en 1 s tiene que ir por la mitad");
        }
    }

    /** El vidrio rebota: una pelota que llega rodando a 10 m/s vuelve rápido, no se queda pegada. */
    @Test
    void elVidrioRebota() {
        try (Guion g = arena(138)) {
            g.m.physics.placeBall(0, Ball.RADIUS, Match.HALF_W - 2);
            g.m.kick(g.visita(0), new Vector2(0, 1), 10, 0, 0);
            float vuelta = 0;
            for (int i = 0; i < 120; i++) {
                g.paso(NADA, false);
                vuelta = Math.max(vuelta, -g.m.ball.vel.z);
            }
            System.out.printf("contra el vidrio a 10 m/s: vuelve a %.1f m/s%n", vuelta);
            assertTrue(vuelta > 5f, "volvió a " + vuelta);
        }
    }

    @Test
    void elGolCuentaYLaPelotaVuelveALosPies() {
        try (Guion g = arena(132)) {
            g.m.physics.place(g.visita(0), 52, 15);  // el arquero, lejos
            g.m.physics.placeBall(40, Ball.RADIUS, 0);
            g.m.kick(g.local(9), new Vector2(1, 0), 25, 0.5f, 0);
            g.durante(3f, NADA, false);
            assertEquals(1, g.m.home.goals);
            assertNull(g.m.setPiece);
            assertSame(g.m.controlled, g.m.owner, "después del gol la pelota tiene que volver a los pies del humano");
        }
    }

    @Test
    void elArqueroDeLaMaquinaLaAtajaYSeLaDevuelve() {
        try (Guion g = arena(133)) {
            Player k = g.visita(0);
            g.m.physics.placeBall(k.pos.x - 10, Ball.RADIUS, k.pos.y);
            g.m.kick(g.local(9), new Vector2(1, 0), 9, 0, 0);
            boolean atajo = false, devolvio = false;
            for (int i = 0; i < 300 && !devolvio; i++) {
                g.paso(NADA, false);
                if (g.m.owner == k) atajo = true;
                if (atajo && g.m.owner != k && g.m.passTarget != null && g.m.passTarget.team == g.m.home) devolvio = true;
            }
            assertTrue(atajo, "no la agarró");
            assertTrue(devolvio, "no se la pasó a un jugador del humano");
        }
    }

    @Test
    void conElControlNuevoZCambiaAlQueLlegaPrimeroAunqueEsteCerca() {
        try (Guion g = arena(134)) {
            Player yo = g.local(6), otro = g.local(5);
            g.despejar(yo, otro);
            g.m.physics.place(yo, 0, 0);
            g.m.physics.place(otro, 4, 1);
            g.m.physics.placeBall(3.5f, Ball.RADIUS, 0.5f);  // suelta, más cerca del otro, a 3,5 m del que maneja
            g.m.controlled = yo;
            g.paso(NADA, false);
            g.paso(NADA, false, true);
            g.paso(NADA, false);
            assertSame(otro, g.m.controlled);
        }
    }

    @Test
    void elArqueroDelHumanoLaJuegaComoCualquiera() {
        try (Guion g = arena(135)) {
            Player k = g.local(0);
            g.m.physics.place(g.local(10), 0, 25);
            g.m.physics.placeBall(k.pos.x + 8, Ball.RADIUS, k.pos.y);
            g.m.kick(g.visita(0), new Vector2(-1, 0), 9, 0, 0);
            int i = 0;
            while (g.m.owner != k && i++ < 200) g.paso(NADA, false);
            assertSame(k, g.m.owner, "no la agarró");
            g.paso(NADA, false);
            assertSame(k, g.m.controlled, "con la pelota, el arquero lo maneja el humano");
            // la lleva como uno más y le pega con el control nuevo
            float x0 = k.pos.x;
            g.durante(0.5f, new Vector2(1, 0), false);
            assertTrue(k.pos.x - x0 > 1f, "no la llevó");
            g.durante(0.4f, NADA, true);
            g.paso(NADA, false);
            g.paso(NADA, false);
            assertNotSame(k, g.m.owner, "no le pegó");
            assertTrue(g.m.ball.vel.len() > 10, "le pegó flojo: " + g.m.ball.vel.len());
        }
    }
}
