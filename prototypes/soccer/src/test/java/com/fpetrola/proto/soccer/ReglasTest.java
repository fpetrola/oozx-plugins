package com.fpetrola.proto.soccer;

import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

import static com.fpetrola.proto.soccer.Guion.*;
import static org.junit.jupiter.api.Assertions.*;

/** Las reglas: pelotas que salen, goles, y las barridas que deciden los contactos de la física. */
class ReglasTest {
    @Test
    void unaPelotaAlCostadoDaUnSoloLateral() {
        try (Guion g = new Guion(1)) {
            g.m.aiEnabled = true;
            Player yo = g.local(6);
            g.conPelota(yo, 0, 30, DERECHA);
            g.m.kick(yo, new Vector2(0, 1), 10, 0, 0);
            int laterales = 0;
            SetPiece visto = null;
            for (int i = 0; i < 600; i++) {
                g.paso(NADA, false);
                if (g.m.setPiece != null && g.m.setPiece != visto) {
                    visto = g.m.setPiece;
                    if (visto.kind == SetPiece.Kind.THROW_IN) laterales++;
                }
            }
            assertEquals(1, laterales);
        }
    }

    @Test
    void afueraPorElFondoDeUnAtacanteEsSaqueDeArcoDelArquero() {
        try (Guion g = new Guion(2)) {
            Player yo = g.local(6);
            g.conPelota(yo, 45, 25, ARRIBA);
            g.m.kick(yo, new Vector2(1, 0.2f).nor(), 20, 0, 0);
            g.durante(2f, NADA, false);
            SetPiece sp = g.m.setPiece;
            assertNotNull(sp);
            assertEquals(SetPiece.Kind.GOAL_KICK, sp.kind);
            assertSame(g.m.away, sp.team);
            assertTrue(sp.taker.keeper);
        }
    }

    @Test
    void afueraPorElFondoDeUnDefensorEsCornerYLoSacaElHumano() {
        try (Guion g = new Guion(3)) {
            Player ellos = g.visita(3);
            g.conPelota(ellos, 48, -25, ARRIBA);
            g.m.kick(ellos, new Vector2(1, -0.2f).nor(), 15, 0, 0);
            g.durante(2f, NADA, false);
            SetPiece sp = g.m.setPiece;
            assertNotNull(sp);
            assertEquals(SetPiece.Kind.CORNER, sp.kind);
            assertSame(g.m.home, sp.team);
            assertSame(sp.taker, g.m.controlled);
        }
    }

    @Test
    void golYSacaDelMedioElQueLoRecibio() {
        try (Guion g = new Guion(4)) {
            Player yo = g.local(9);
            g.conPelota(yo, 45, 0, ARRIBA);
            g.m.physics.place(g.visita(0), 52, 20);
            g.m.kick(yo, new Vector2(1, 0), 25, 0.5f, 0);
            g.durante(3f, NADA, false);
            assertEquals(1, g.m.home.goals);
            assertNotNull(g.m.setPiece);
            assertEquals(SetPiece.Kind.KICKOFF, g.m.setPiece.kind);
            assertSame(g.m.away, g.m.setPiece.team);
        }
    }

    /** Barrida del local #5 desde (sx, sz) hacia {@code dir} contra el visitante #10, que lleva la pelota hacia -x desde (rx, rz). */
    static String barrida(float rx, float rz, float sx, float sz, Vector2 dir) {
        try (Guion g = new Guion(5)) {
            Player ellos = g.visita(9), yo = g.local(4);
            g.despejar(ellos, yo);
            g.conPelota(ellos, rx, rz, ABAJO);
            g.m.physics.place(yo, sx, sz);
            yo.facing.set(dir).nor();
            g.m.controlled = yo;
            String r = "nada";
            for (int i = 0; i < 60; i++) {
                ellos.steer(new Vector2(-5, 0));
                g.paso(dir, i >= 1 && i <= 14);  // mantener X es barrida
                if (g.m.messageTime > 1.9f && (g.m.message.equals("Falta") || g.m.message.equals("¡Penal!"))) return g.m.message;
                if (yo.actTouched) r = "limpia";
            }
            return r;
        }
    }

    @Test
    void laBarridaQueTocaPrimeroLaPelotaEsLimpia() {
        assertEquals("limpia", barrida(10, 0, 6.5f, 0.1f, ARRIBA));
    }

    @Test
    void laBarridaQueTocaPrimeroAlRivalEsFalta() {
        assertEquals("Falta", barrida(10, 0, 12.2f, 0.1f, ABAJO));
        // de costado, en la línea de sus piernas (el cuerpo empieza en la rodilla: abajo sólo están las piernas)
        assertEquals("Falta", barrida(10, 0, 10.05f, -2.2f, DERECHA));
    }

    @Test
    void laFaltaEnElAreaPropiaEsPenal() {
        assertEquals("¡Penal!", barrida(-40, 0, -37.8f, 0.1f, ABAJO));
    }
}
