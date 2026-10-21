package com.fpetrola.proto.soccer;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Partidos enteros: un humano que aprieta cualquier cosa contra la máquina, y las dificultades. */
class PartidoTest {
    @Test
    void diezMinutosConTeclasAlAzarSinRomperse() {
        MathUtils.random.setSeed(3);
        Match m = new Match();
        try {
            Pad pad = new Pad();
            Set<SetPiece.Kind> vistas = EnumSet.noneOf(SetPiece.Kind.class);
            Vector2 dir = new Vector2();
            boolean x = false, z = false;
            for (int cuadro = 0; cuadro < 60 * 60 * 10; cuadro++) {
                if (cuadro % 20 == 0) {
                    int d = MathUtils.random(8);
                    dir.set(d == 8 ? 0 : MathUtils.cosDeg(d * 45), d == 8 ? 0 : MathUtils.sinDeg(d * 45));
                    if (MathUtils.randomBoolean(0.5f)) dir.set(m.ball.pos.x - m.controlled.pos.x, m.ball.pos.z - m.controlled.pos.y).nor();
                }
                if (cuadro % 7 == 0) x = MathUtils.randomBoolean(0.25f);
                if (cuadro % 97 == 0) x = true;
                if (cuadro % 11 == 0) z = MathUtils.randomBoolean(0.15f);
                if (cuadro % 600 == 0) m.aiEnabled = MathUtils.randomBoolean(0.8f);
                if (cuadro % 900 == 0) m.difficulty = Difficulty.values()[MathUtils.random(2)];
                pad.set(dir, x, z);
                m.update(pad, 1 / 60f);
                if (m.setPiece != null) vistas.add(m.setPiece.kind);
                for (Player p : m.players) {
                    assertFalse(Float.isNaN(p.pos.x) || Float.isNaN(p.pos.y), "jugador sin posición");
                    assertTrue(Math.abs(p.pos.x) < 70 && Math.abs(p.pos.y) < 50, "jugador " + p.number + " afuera del estadio: " + p.pos);
                }
                assertFalse(Float.isNaN(m.ball.pos.x), "pelota sin posición en el cuadro " + cuadro);
                // un tiro fuerte puede pasar por arriba de los carteles y caer en la tribuna: lo que no tiene que haber es una pelota disparada
                assertTrue(Math.abs(m.ball.pos.x) < 100 && Math.abs(m.ball.pos.z) < 80 && m.ball.pos.y > -0.01f, "pelota afuera del estadio: " + m.ball.pos);
                assertTrue(m.ball.vel.len() < 45, "pelota demasiado rápida: " + m.ball.vel.len());
            }
            assertTrue(vistas.size() >= 4, "pelotas paradas vistas: " + vistas);
        } finally {
            m.dispose();
        }
    }

    @Test
    void elArqueroDeLaMaquinaAtajaSegunLaDificultad() {
        int facil = ArqueroTest.goles(Difficulty.EASY, 80, 9), normal = ArqueroTest.goles(Difficulty.NORMAL, 80, 9),
                dificil = ArqueroTest.goles(Difficulty.HARD, 80, 9);
        assertTrue(facil > normal && normal > dificil, "goles: fácil " + facil + ", normal " + normal + ", difícil " + dificil);
    }

    @Test
    void enFacilLaMaquinaHaceMenosGolesQueEnDificil() {
        int facil = golesDeLaMaquina(Difficulty.EASY), dificil = golesDeLaMaquina(Difficulty.HARD);
        System.out.printf("goles de la máquina en seis minutos contra un humano quieto: fácil %d, difícil %d%n", facil, dificil);
        assertTrue(facil < dificil, "fácil " + facil + ", difícil " + dificil);
        assertTrue(facil <= 10, "en fácil hizo " + facil + " en seis minutos contra un humano quieto");
    }

    /** Goles de la máquina en seis minutos contra un humano que saca del medio y se queda quieto. */
    static int golesDeLaMaquina(Difficulty d) {
        MathUtils.random.setSeed(5);
        Match m = new Match();
        try {
            m.scheme = new SensibleScheme();  // el humano quieto saca del medio con un pase, como siempre
            m.difficulty = d;
            Pad pad = new Pad();
            for (int f = 0; f < 60 * 60 * 6; f++) {
                boolean x = m.setPiece != null && m.setPiece.team == m.home && f % 60 < 3;
                pad.set(Vector2.Zero, x);
                m.update(pad, 1 / 60f);
            }
            return m.away.goals;
        } finally {
            m.dispose();
        }
    }
}
