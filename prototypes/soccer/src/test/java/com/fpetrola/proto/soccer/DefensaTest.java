package com.fpetrola.proto.soccer;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

import static com.fpetrola.proto.soccer.Guion.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * La etapa 2 del plan: las piernas (el caño), el puntazo, contener, y la máquina que lee el pie y
 * cae en los amagues según la dificultad.
 */
class DefensaTest {
    /**
     * Pelotas rasantes a 10 m/s al medio de un rival, con un poco de error al costado: cuántas le
     * pasan entre las piernas. Parado de frente a la pelota tiene las piernas abiertas; corriendo
     * (lo maneja la máquina: va a buscar la pelota), cerradas.
     */
    static int caños(boolean corriendo, int intentos) {
        int pasan = 0;
        try (Guion g = new Guion(50)) {
            g.m.aiEnabled = corriendo;
            Player ellos = g.visita(5), yo = g.local(6);
            g.despejar(ellos, yo);
            for (int i = 0; i < intentos; i++) {
                g.m.physics.place(ellos, corriendo ? 9 : 5, 0);
                ellos.facing.set(-1, 0);
                g.m.physics.place(yo, -3, 8);
                g.m.physics.placeBall(0, Ball.RADIUS, MathUtils.random(-0.05f, 0.05f));
                g.m.ball.lastTouch = null;
                for (int k = 0; k < 30; k++) g.paso(NADA, false);
                g.m.kick(yo, new Vector2(1, 0), 10, 0, 0);
                for (int k = 0; k < 90; k++) {
                    g.paso(NADA, false);
                    if (g.m.ball.lastTouch == ellos) break;
                    if (g.m.ball.pos.x > ellos.pos.x + 0.6f) {
                        pasan++;
                        break;
                    }
                }
            }
        }
        return pasan;
    }

    @Test
    void elCañoPasaEntreLasPiernasAbiertasYNoEntreLasCerradas() {
        int abiertas = caños(false, 100), cerradas = caños(true, 100);
        System.out.printf("caño: parado pasan %d de 100, corriendo %d de 100%n", abiertas, cerradas);
        assertTrue(abiertas >= 60, "parado pasaron " + abiertas);
        assertTrue(cerradas <= 10, "corriendo pasaron " + cerradas);
    }

    /**
     * El humano espera parado con la pelota y, cuando el defensor de la máquina llega a 2 m, hace
     * la elástica (derecha y enseguida izquierda) y sale en diagonal para la izquierda. Devuelve
     * cuántas veces el defensor se jugó primero al lado del primer movimiento, y cuántas veces el
     * humano siguió con la pelota un segundo después.
     */
    static int[] amagues(Difficulty d, int intentos) {
        int engañados = 0, conservadas = 0;
        for (int i = 0; i < intentos; i++) {
            try (Guion g = new Guion(200 + i)) {
                g.m.aiEnabled = true;
                g.m.difficulty = d;
                Player yo = g.local(6), ellos = g.visita(6);
                g.despejar(yo, ellos);
                g.conPelota(yo, 0, 0, ARRIBA);
                g.m.physics.place(ellos, 6 + MathUtils.random(1f), MathUtils.random(-0.5f, 0.5f));
                int t = 0;
                while (g.m.ball.dist2D(ellos.pos) > 2f && t++ < 180) g.paso(NADA, false);
                float lado = 0;
                Vector2 salida = new Vector2(1, -1).nor();
                for (int k = 0; k < 7; k++) {
                    g.paso(DERECHA, false, true);
                    if (lado == 0 && ellos.commit > 0) lado = ellos.committedSide;
                }
                for (int k = 0; k < 7; k++) {
                    g.paso(IZQUIERDA, false, true);
                    if (lado == 0 && ellos.commit > 0) lado = ellos.committedSide;
                }
                for (int k = 0; k < 60; k++) {
                    g.paso(salida, false);
                    if (lado == 0 && ellos.commit > 0) lado = ellos.committedSide;
                }
                if (lado > 0) engañados++;
                if (g.m.owner == yo) conservadas++;
            }
        }
        return new int[]{engañados, conservadas};
    }

    @Test
    void laElasticaEngañaMasAlFacilQueAlDificil() {
        int[] facil = amagues(Difficulty.EASY, 40), dificil = amagues(Difficulty.HARD, 40);
        System.out.printf("amague: fácil se jugó al primer lado %d/40 y la conservó %d/40; difícil %d/40 y %d/40%n",
                facil[0], facil[1], dificil[0], dificil[1]);
        assertTrue(facil[0] >= 24, "fácil se jugó " + facil[0]);
        assertTrue(dificil[0] <= 10, "difícil se jugó " + dificil[0]);
        assertTrue(facil[1] >= 20, "contra el fácil la conservó " + facil[1]);
        assertTrue(dificil[1] <= 12, "contra el difícil la conservó " + dificil[1]);
    }

    /**
     * El humano, parado en ({@code yoX}, {@code yoZ}) mirando hacia +x, hace un puntazo contra el
     * visitante #10, parado en (10, 0) mirando hacia {@code mira} con la pelota delante. (Más cerca
     * que 1 m de la pelota no hace falta: la toma sola, como siempre.)
     */
    static String puntazo(float yoX, float yoZ, Vector2 mira) {
        try (Guion g = new Guion(60)) {
            Player ellos = g.visita(9), yo = g.local(4);
            g.despejar(ellos, yo);
            g.conPelota(ellos, 10, 0, mira);
            g.m.physics.place(yo, yoX, yoZ);
            yo.facing.set(1, 0);
            g.m.controlled = yo;
            g.paso(NADA, false);
            assertSame(ellos, g.m.owner, "la tiene que seguir teniendo él antes del puntazo");
            for (int i = 0; i < 40; i++) {
                g.paso(NADA, i == 0);
                if (g.m.messageTime > 1.9f && g.m.message.equals("Falta")) return "falta";
            }
            if (yo.act == Player.Act.NONE && yo.actTouched && g.m.owner != ellos) return "se la sacó";
            return g.m.owner == ellos ? "nada" : "suelta";
        }
    }

    @Test
    void elPuntazoQueLlegaPrimeroALaPelotaSeLaSaca() {
        // de frente, a 1,8 m del que la lleva: el pie llega a la pelota, que está entre los dos
        assertEquals("se la sacó", puntazo(8.2f, 0, ABAJO));
    }

    @Test
    void elPuntazoQueLlegaPrimeroALaPiernaEsFalta() {
        // de atrás, a 1 m: la pierna del que la lleva está antes que la pelota
        assertEquals("falta", puntazo(9f, 0.3f, ARRIBA));
    }

    @Test
    void conteniendoQuedaDeFrenteDespacioYConLasPiernasAbiertas() {
        try (Guion g = new Guion(61)) {
            Player yo = g.local(4), ellos = g.visita(9);
            g.despejar(yo, ellos);
            g.conPelota(ellos, 10, 0, ABAJO);
            g.m.physics.place(yo, 6, 0);  // cerca de la jugada: lejos, Z cambia de jugador
            g.m.controlled = yo;
            g.durante(0.6f, DERECHA, false, true);
            float dot = new Vector2(g.m.ball.pos.x - yo.pos.x, g.m.ball.pos.z - yo.pos.y).nor().dot(yo.facing);
            System.out.printf("contener: va a %.2f, mira a la pelota %.2f, piernas %.2f%n", yo.vel.len(), dot, yo.stance);
            assertSame(yo, g.m.controlled, "mantener Z no cambia de jugador");
            assertTrue(yo.vel.len() <= 3.05f, "va a " + yo.vel.len());
            assertTrue(yo.vel.len() > 2f, "con la flecha camina: " + yo.vel.len());
            assertTrue(dot > 0.95f, "mira " + dot);
            assertTrue(yo.stance > 0.9f, "piernas " + yo.stance);
        }
    }

    @Test
    void unToqueDeZSinLaPelotaSigueCambiandoDeJugador() {
        try (Guion g = new Guion(62)) {
            Player yo = g.local(6), otro = g.local(5), ellos = g.visita(9);
            g.despejar(yo, otro, ellos);
            g.m.physics.place(yo, -10, 0);
            g.m.physics.place(otro, 6, 4);
            g.conPelota(ellos, 10, 0, ABAJO);
            g.m.controlled = yo;
            g.paso(NADA, false);
            g.durante(0.1f, NADA, false, true);
            g.paso(NADA, false);
            assertSame(otro, g.m.controlled);
        }
    }
}
