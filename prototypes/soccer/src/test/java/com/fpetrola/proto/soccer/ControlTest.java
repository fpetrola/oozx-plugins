package com.fpetrola.proto.soccer;

import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

import static com.fpetrola.proto.soccer.Guion.*;
import static org.junit.jupiter.api.Assertions.*;

/** A quién maneja el humano: lo cambia él con Z, salvo cuando no tiene sentido esperar. */
class ControlTest {
    @Test
    void yendoAMarcarNoCambiaSolo() {
        try (Guion g = new Guion(21)) {
            Player yo = g.local(6), otro = g.local(5), ellos = g.visita(9);
            g.despejar(yo, otro, ellos);
            g.m.physics.place(yo, -10, 0);
            g.m.physics.place(otro, 6, 4);
            g.conPelota(ellos, 10, 0, ABAJO);
            g.m.controlled = yo;
            for (int i = 0; i < 90; i++) {
                ellos.steer(new Vector2(-4, 0));
                g.paso(ARRIBA, false);
                assertSame(yo, g.m.controlled);
            }
            assertSame(otro, g.m.switchTo, "el anillo marca al que llega primero");
        }
    }

    @Test
    void zCambiaAlMarcadoYOtraZVuelve() {
        try (Guion g = new Guion(22)) {
            Player yo = g.local(6), otro = g.local(5), ellos = g.visita(9);
            g.despejar(yo, otro, ellos);
            g.m.physics.place(yo, -10, 0);
            g.m.physics.place(otro, 6, 4);
            g.conPelota(ellos, 10, 0, ABAJO);
            g.m.controlled = yo;
            g.paso(NADA, false);
            g.paso(NADA, false, true);
            g.paso(NADA, false);
            assertSame(otro, g.m.controlled);
            g.paso(NADA, false, true);
            g.paso(NADA, false);
            assertSame(yo, g.m.controlled);
        }
    }

    /** Cerca de la pelota, Z es para pisarla o contener: nunca cambia de jugador (no se pierde el que estaba con la pelota). */
    @Test
    void cercaDeLaPelotaZNoCambiaDeJugador() {
        try (Guion g = new Guion(26)) {
            Player yo = g.local(6), otro = g.local(5);
            g.despejar(yo, otro);
            g.m.physics.place(yo, 0, 0);
            g.m.physics.place(otro, 3, 1);
            g.m.physics.placeBall(3, Ball.RADIUS, 0);  // suelta, a 3 m: no la alcanza todavía
            g.m.controlled = yo;
            g.paso(NADA, false);
            g.paso(NADA, false, true);
            g.paso(NADA, false);
            assertSame(yo, g.m.controlled, "un toque de Z cerca de la pelota cambió de jugador");
            g.durante(0.5f, NADA, false, true);
            assertSame(yo, g.m.controlled, "mantener Z cerca de la pelota cambió de jugador");
        }
    }

    /** Un toque de X sin flechas es un toque: no se la pasa a un compañero que está lejos para donde mira. */
    @Test
    void unToqueSinFlechaNoLaPasaAUnCompañeroLejos() {
        try (Guion g = new Guion(27)) {
            Player yo = g.local(6), el = g.local(9);
            g.despejar(yo, el);
            g.conPelota(yo, 0, 0, ARRIBA);
            g.m.physics.place(el, 30, 2);
            g.durante(0.3f, NADA, false);
            g.paso(NADA, true);
            g.paso(NADA, false);
            assertSame(yo, g.m.controlled);
            assertSame(yo, g.m.passTarget, "tiene que ser un toque para ir a buscarla");
        }
    }

    @Test
    void muyLejosDeLaJugadaCambiaSolo() {
        try (Guion g = new Guion(23)) {
            Player lejos = g.local(2), cerca = g.local(9);
            g.despejar(lejos, cerca);
            g.m.physics.place(lejos, -45, 20);
            g.m.physics.place(cerca, 15, 2);
            g.m.physics.placeBall(20, Ball.RADIUS, 0);
            g.m.controlled = lejos;
            g.durante(0.1f, NADA, false);
            assertSame(cerca, g.m.controlled);
        }
    }

    @Test
    void laPelotaSueltaLaTomaElQueLlegaPrimero() {
        try (Guion g = new Guion(24)) {
            Player cerca = g.local(5), delCamino = g.local(9);
            g.despejar(cerca, delCamino);
            g.m.physics.place(cerca, -14, 2);
            g.m.physics.place(delCamino, 15, 9);
            g.m.controlled = cerca;
            g.m.physics.placeBall(-10, Ball.RADIUS, 0);
            g.m.kick(g.visita(3), new Vector2(1, 0.35f).nor(), 20f, 0, 0);
            g.durante(0.2f, NADA, false);
            assertSame(delCamino, g.m.controlled);
        }
    }

    @Test
    void elPaseLoManejaElQueLoRecibeYVaABuscarlo() {
        try (Guion g = new Guion(25)) {
            Player yo = g.local(6), otro = g.local(9);
            g.despejar(yo, otro);
            g.conPelota(yo, -10, 0, ARRIBA);
            g.m.physics.place(otro, 8, 3);
            g.durante(0.2f, ARRIBA, false);
            g.paso(ARRIBA, true);
            g.paso(ARRIBA, false);
            assertSame(otro, g.m.controlled);
            g.durante(3f, NADA, false);
            assertSame(otro, g.m.owner);
        }
    }
}
