package com.fpetrola.proto.soccer;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

import static com.fpetrola.proto.soccer.Guion.*;
import static org.junit.jupiter.api.Assertions.*;

/** El control nuevo: X le pega al medio, C abajo (más alta), lo que duran apretados es la potencia; las flechas eligen para dónde va; sale al soltar. */
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

    /** Un golpe con X (al medio) o C (abajo), apretado tanto tiempo. */
    private static Tiro golpe(boolean abajo, float segundos) {
        try (Guion g = new Guion(120)) {
            listo(g);
            mantener(g, segundos, NADA, !abajo, abajo);
            return soltar(g);
        }
    }

    @Test
    void conXSaleBajaYDerecha() {
        Tiro t = golpe(false, 0.5f);
        System.out.printf("X 0,5 s: sube %.2f m, pica a %.1f m, a los 15 m va %.2f de costado%n", t.alto(), t.pica(), t.lado15());
        assertTrue(t.alto() < 2f, "subió a " + t.alto());
        assertEquals(0, t.lado15(), 0.05f);
    }

    @Test
    void conCSaleAltaYCuantoMasDuraMasLejos() {
        Tiro x = golpe(false, 0.4f), corto = golpe(true, 0.2f), largo = golpe(true, 0.4f);
        System.out.printf("C 0,2 s: sube %.2f m y pica a %.1f m; C 0,4 s: %.2f m y %.1f m; X 0,4 s: %.2f m%n",
                corto.alto(), corto.pica(), largo.alto(), largo.pica(), x.alto());
        assertTrue(largo.alto() > x.alto() + 2f, "con C no salió más alta");
        assertTrue(largo.pica() > corto.pica() + 5f, "más tiempo no la mandó más lejos");
    }

    /** Para dónde sale (grados, 0 es para adelante) y para dónde terminó yendo (donde termina la flecha del tiro), con la comba que llevaba. */
    record Rumbo(float sale, float llega, float comba) {
    }

    /** Arma con X, gira la flecha con {@code flechas} durante {@code segundos}, con el cuerpo girando a {@code cuerpo} grados por s, y suelta. */
    private static Rumbo apuntar(Vector2 flechas, float segundos, float cuerpo) {
        try (Guion g = new Guion(122)) {
            GolpeScheme s = listo(g);
            // en el medio y flojo, para que no se vaya de la cancha antes de terminar la flecha
            g.conPelota(g.local(6), 0, 0, ARRIBA);
            g.durante(0.3f, NADA, false);
            s.body = cuerpo;
            s.fill = 1.5f;  // la potencia se sigue cargando mientras gira la flecha
            mantener(g, 0.2f, NADA, true, false);
            mantener(g, segundos, flechas, true, false);
            float comba = s.curl;
            // hasta dónde llega la flecha del tiro: ahí se mide para dónde terminó yendo
            ShotPreview vista = g.m.scheme.shot();
            int u = vista.count - 1;
            float x0 = g.m.ball.pos.x, z0 = g.m.ball.pos.z, sale = Float.NaN, llega = Float.NaN;
            float lejos = Vector2.dst(x0, z0, vista.xyz[u * 3], vista.xyz[u * 3 + 2]);
            for (int i = 0; i < 240 && Float.isNaN(llega); i++) {
                g.paso(NADA, false, false, false);
                if (Float.isNaN(sale) && g.m.ball.vel.len() > 8) sale = MathUtils.atan2(g.m.ball.vel.z, g.m.ball.vel.x) * MathUtils.radDeg;
                if (Vector2.dst(x0, z0, g.m.ball.pos.x, g.m.ball.pos.z) >= lejos)
                    llega = MathUtils.atan2(g.m.ball.pos.z - z0, g.m.ball.pos.x - x0) * MathUtils.radDeg;
            }
            return new Rumbo(sale, llega, comba);
        }
    }

    /** Con un stick, la flecha va enseguida para donde apunta: con el cuerpo que gira rápido, sale para ahí. */
    @Test
    void conElStickLaFlechaVaDirectoParaDondeApunta() {
        Vector2 stick = new Vector2(-0.5f, 0.866f);  // 120 grados
        try (Guion g = new Guion(122)) {
            GolpeScheme s = listo(g);
            s.body = 900f;
            g.pad.analog = true;
            mantener(g, 0.3f, NADA, true, false);
            mantener(g, 0.1f, stick, true, false);
            float sale = Float.NaN;
            for (int i = 0; i < 30 && Float.isNaN(sale); i++) {
                g.paso(stick, false, false, false);
                if (g.m.ball.vel.len() > 8) sale = MathUtils.atan2(g.m.ball.vel.z, g.m.ball.vel.x) * MathUtils.radDeg;
            }
            System.out.printf("con el stick a 120 grados, 0,1 s: sale a %.0f grados%n", sale);
            assertEquals(120, sale, 3f);
        }
    }

    @Test
    void laFlechaVaACualquierLadoYConTiempoSaleDerecha() {
        Rumbo atras = apuntar(ABAJO, 2f, 120f);
        System.out.printf("flecha para atrás 2 s: sale a %.0f grados, termina a %.0f, comba %.1f%n", atras.sale(), atras.llega(), atras.comba());
        assertTrue(Math.abs(atras.llega()) > 170f, "no fue para atrás: " + atras.llega());
        assertEquals(0, atras.comba(), 1e-3f, "con tiempo para girar tiene que salir derecha");
    }

    /** De costado sin tiempo para ponerse de frente: sale abierta, con comba, y termina para donde apuntaba la flecha; para el otro lado, lo mismo al revés. */
    @Test
    void deCostadoSinTiempoLaCombaSaleSolaYEsSimetrica() {
        Rumbo der = apuntar(DERECHA, 0.5f, 90f), izq = apuntar(IZQUIERDA, 0.5f, 90f), rapido = apuntar(DERECHA, 0.5f, 900f);
        System.out.printf("de costado: el cuerpo lento sale a %.0f grados y termina a %.0f (comba %.1f); al otro lado %.0f y %.0f (%.1f);"
                + " el cuerpo rápido sale a %.0f y termina a %.0f (comba %.1f)%n",
                der.sale(), der.llega(), der.comba(), izq.sale(), izq.llega(), izq.comba(), rapido.sale(), rapido.llega(), rapido.comba());
        assertTrue(Math.abs(der.comba()) > 5f, "sin tiempo de ponerse de frente tiene que llevar comba");
        assertEquals(-der.comba(), izq.comba(), 0.5f);
        assertEquals(-der.llega(), izq.llega(), 1f);
        assertTrue(Math.abs(der.llega() - rapido.llega()) < 5f, "con comba no llegó adonde apuntaba: " + der.llega() + " contra " + rapido.llega());
        assertTrue(Math.abs(der.sale() - der.llega()) > 10f, "no salió abierta");
        assertEquals(0, rapido.comba(), 1e-3f);
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

    /**
     * De primera: la pelota suelta 8 m adelante; se arma el tiro antes de llegar ({@code soltarAntes}:
     * soltando los botones enseguida, o manteniéndolos), el jugador va solo y le pega apenas la toca.
     */
    private static Tiro dePrimera(long semilla, boolean soltarAntes) {
        try (Guion g = new Guion(semilla)) {
            GolpeScheme s = new GolpeScheme();
            g.m.scheme = s;
            Player yo = g.local(6);
            g.despejar(yo);
            g.m.physics.place(yo, -30, 0);
            yo.facing.set(1, 0);
            g.m.controlled = yo;
            g.m.physics.placeBall(-22, Ball.RADIUS, 0);
            g.paso(NADA, false);
            mantener(g, 0.3f, NADA, true, true);
            float x0 = -22, alto = 0, pica = Float.NaN;
            boolean laTuvo = false, salio = false;
            for (int i = 0; i < 300; i++) {
                boolean botones = !soltarAntes && !salio;
                g.paso(NADA, botones, false, botones);
                if (g.m.owner == yo) laTuvo = true;
                if (g.m.ball.vel.len() > 8) salio = true;
                alto = Math.max(alto, g.m.ball.pos.y);
                if (salio && Float.isNaN(pica) && alto > 0.5f && g.m.ball.pos.y < Ball.RADIUS + 0.05f) pica = g.m.ball.pos.x - x0;
            }
            assertFalse(laTuvo, "la agarró en vez de pegarle de primera");
            assertTrue(salio, "no le pegó");
            return new Tiro(alto, pica, 0);
        }
    }

    @Test
    void sePuedeArmarAntesDeLlegarYLePegaDePrimera() {
        Tiro manteniendo = dePrimera(129, false), soltado = dePrimera(129, true);
        System.out.printf("de primera: manteniendo los botones sube %.2f m y pica a %.1f m; soltados antes de llegar, %.2f m y %.1f m%n",
                manteniendo.alto(), manteniendo.pica(), soltado.alto(), soltado.pica());
        assertTrue(manteniendo.alto() > 1f && soltado.alto() > 1f, "no salió con la altura armada");
    }

    @Test
    void laFlechaMuestraLoQueSaleYDiceLaVerdad() {
        try (Guion g = new Guion(126)) {
            listo(g).body = 30f;
            mantener(g, 0.3f, NADA, true, true);
            mantener(g, 0.4f, IZQUIERDA, true, false);
            ShotPreview s = g.m.scheme.shot();
            assertNotNull(s, "armando el golpe se tiene que ver la flecha");
            assertTrue(s.power > 0.3f && s.elevation > 10 && Math.abs(s.curl) > 5, "la flecha no muestra potencia, altura y comba: " + s.curl);
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
