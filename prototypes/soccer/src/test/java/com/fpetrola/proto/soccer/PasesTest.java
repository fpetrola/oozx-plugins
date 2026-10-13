package com.fpetrola.proto.soccer;

import org.junit.jupiter.api.Test;

import static com.fpetrola.proto.soccer.Guion.*;
import static org.junit.jupiter.api.Assertions.*;

/** Los pases por abajo: le llegan al compañero salvo que un rival esté de verdad en el camino. */
class PasesTest {
    /**
     * El humano le pasa por abajo, con un toque, a un compañero a 15 m; un defensor de la máquina
     * está parado a {@code lado} metros del medio del camino. Devuelve si el pase llegó.
     */
    static boolean llega(float lado, long semilla) {
        try (Guion g = new Guion(semilla)) {
            g.m.aiEnabled = true;
            g.m.difficulty = Difficulty.NORMAL;
            Player yo = g.local(6), el = g.local(9), ellos = g.visita(6);
            g.despejar(yo, el, ellos);
            g.conPelota(yo, -10, 0, ARRIBA);
            g.m.physics.place(el, 5, 0);
            g.m.physics.place(ellos, -2.5f, lado);
            g.paso(NADA, false);
            g.paso(ARRIBA, true);
            g.paso(ARRIBA, false);
            for (int i = 0; i < 180; i++) {
                g.paso(NADA, false);
                if (g.m.owner == el) return true;
                if (g.m.owner == ellos || g.m.ball.lastTouch == ellos) return false;
            }
            return false;
        }
    }

    static int llegan(float lado) {
        int n = 0;
        for (int s = 0; s < 10; s++) if (llega(lado, 300 + s)) n++;
        return n;
    }

    @Test
    void unDefensorEnElCaminoLoCortaYUnoAlCostadoNo() {
        int encima = llegan(0.3f), pegado = llegan(0.8f), cerca = llegan(2f), lejos = llegan(3.5f);
        System.out.printf("pases que llegan de 10, con el defensor a 0,3 m del camino: %d; a 0,8 m: %d; a 2 m: %d; a 3,5 m: %d%n",
                encima, pegado, cerca, lejos);
        assertTrue(encima <= 2, "con el defensor en el camino llegaron " + encima);
        assertTrue(pegado <= 5, "con el defensor pegado al camino llegaron " + pegado);
        assertTrue(lejos >= 9, "con el defensor a 3,5 m del camino llegaron " + lejos);
    }
}
