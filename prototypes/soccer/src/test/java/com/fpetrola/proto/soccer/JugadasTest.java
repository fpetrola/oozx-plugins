package com.fpetrola.proto.soccer;

import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

import static com.fpetrola.proto.soccer.Guion.*;
import static org.junit.jupiter.api.Assertions.*;

/** La etapa 4 del plan: lo que hace falta alrededor del toque para que las jugadas sirvan en un partido. */
class JugadasTest {
    @Test
    void elPaseLeLlegaAlCompañeroQueVaCorriendoSinQueFrene() {
        try (Guion g = new Guion(80)) {
            Player yo = g.local(6), el = g.local(9);
            g.despejar(yo, el);
            g.conPelota(yo, -10, -8, ARRIBA);
            g.m.physics.place(el, -4, 4);
            g.durante(0.3f, NADA, false);
            // él va corriendo hacia adelante (la máquina está apagada: se lo empuja a mano) y yo se la paso con un toque
            Vector2 hacia = new Vector2(el.pos).sub(yo.pos).nor();
            el.launch(Player.RUN, 0);
            g.paso(hacia, true);
            el.launch(Player.RUN, 0);
            g.paso(hacia, false);
            assertSame(el, g.m.controlled, "el pase lo maneja el que lo recibe");
            // sin tocar nada, va adonde la encuentra: si el pase lo adelantó bien, no tiene que frenar
            float frenó = 99;
            int i = 0;
            for (; i < 180 && g.m.owner != el; i++) {
                g.paso(NADA, false);
                frenó = Math.min(frenó, el.vel.len());
            }
            System.out.printf("pase adelantado: la tomó en %.2f s, lo más lento que fue %.2f m/s%n", i * CUADRO, frenó);
            assertSame(el, g.m.owner, "no le llegó");
            assertTrue(frenó > 4f, "tuvo que frenar a " + frenó);
        }
    }

    @Test
    void elQueDaLaParedSigueCorriendo() {
        try (Guion g = new Guion(81)) {
            g.m.aiEnabled = true;
            Player yo = g.local(9), el = g.local(10);
            g.despejar(yo, el);
            g.conPelota(yo, -5, -3, ARRIBA);
            g.m.physics.place(el, 6, 5);
            g.durante(0.3f, ARRIBA, false);
            float x0 = yo.pos.x;
            Vector2 hacia = new Vector2(el.pos).sub(yo.pos).nor();
            g.paso(hacia, true);
            g.paso(hacia, false);
            assertSame(el, g.m.controlled);
            g.durante(1f, NADA, false);
            System.out.printf("pared: el que la dio siguió %.2f m%n", yo.pos.x - x0);
            assertTrue(yo.pos.x - x0 > 4f, "el que la dio avanzó " + (yo.pos.x - x0));
        }
    }

    @Test
    void unTiroCargadoAlEspacioEsDelCompañeroQueLlegaPrimero() {
        try (Guion g = new Guion(82)) {
            Player yo = g.local(6), el = g.local(9), ellos = g.visita(4);
            g.despejar(yo, el, ellos);
            g.conPelota(yo, -10, 0, ARRIBA);
            g.m.physics.place(el, 10, 8);
            g.m.physics.place(ellos, 10, -15);
            g.durante(0.3f, NADA, false);
            // apunta a la derecha de adelante y carga un poco
            Vector2 hacia = new Vector2(1, 0.4f).nor();
            g.paso(hacia, true);
            g.durante(0.35f, NADA, true);
            g.paso(NADA, false);
            g.paso(NADA, false);
            System.out.printf("tiro al espacio: lo recibe %s%n", g.m.passTarget == null ? "nadie" : "#" + g.m.passTarget.number);
            assertSame(el, g.m.passTarget, "el tiro tiene que ser para el compañero");
            assertSame(el, g.m.controlled);
        }
    }

    /** Un globito en el lugar le vuelve a caer al que lo hizo: no se le pasa el control a un compañero lejos. */
    @Test
    void unSombreroEnElLugarNoLePasaElControlAOtro() {
        try (Guion g = new Guion(85)) {
            Player yo = g.local(6), el = g.local(9);
            g.despejar(yo, el);
            g.conPelota(yo, 0, 0, ARRIBA);
            g.m.physics.place(el, 15, 6);
            g.durante(0.3f, NADA, false);
            // X y enseguida atrás: la fuerza queda en lo mínimo y sube
            g.paso(NADA, true);
            g.durante(0.1f, NADA, true);
            g.durante(0.35f, ABAJO, true);
            g.paso(NADA, false);
            for (int i = 0; i < 150; i++) {
                g.paso(NADA, false);
                assertSame(yo, g.m.controlled, "le pasó el control a otro en el cuadro " + i);
            }
        }
    }

    @Test
    void elTacoNoSeDaVuelta() {
        try (Guion g = new Guion(83)) {
            Player yo = g.local(6);
            g.despejar(yo);
            g.conPelota(yo, 0, 0, ARRIBA);
            g.durante(0.3f, NADA, false);
            Vector2 antes = new Vector2(yo.facing);
            g.paso(ABAJO, true);
            g.paso(ABAJO, false);
            assertTrue(yo.heel > 0, "tiene que estar haciendo el gesto del taco");
            assertTrue(yo.facing.dot(antes) > 0.9f, "se dio vuelta: " + yo.facing.dot(antes));
            Vector2 v = new Vector2(g.m.ball.vel.x, g.m.ball.vel.z).nor();
            assertTrue(v.dot(antes) < -0.7f, "la pelota sale hacia " + v.dot(antes));
        }
    }

    @Test
    void conLaPelotaLosDelanterosSeVanAdelante() {
        try (Guion g = new Guion(84)) {
            g.m.aiEnabled = true;
            Player yo = g.local(6), nueve = g.local(9);
            g.despejar(yo, nueve);
            g.conPelota(yo, -5, 0, ARRIBA);
            g.m.physics.place(nueve, 0, -5);
            g.durante(3f, NADA, false);
            Vector2 lugar = new Vector2();
            g.m.formationTarget(nueve, g.m.ball.pos.x, g.m.ball.pos.z, lugar);
            System.out.printf("desmarque: el 9 está en x %.1f, su lugar en la formación x %.1f%n", nueve.pos.x, lugar.x);
            assertTrue(nueve.pos.x > lugar.x + 5f, "el 9 no se fue adelante: " + nueve.pos.x + " contra " + lugar.x);
        }
    }
}
