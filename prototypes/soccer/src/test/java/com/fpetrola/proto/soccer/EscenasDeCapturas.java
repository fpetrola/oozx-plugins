package com.fpetrola.proto.soccer;

import com.badlogic.gdx.math.Vector2;

import static com.fpetrola.proto.soccer.Capturas.escena;

/** Las jugadas que saca {@link Capturas}. */
final class EscenasDeCapturas {
    private EscenasDeCapturas() {
    }

    static final Vector2 NADA = new Vector2(), ARRIBA = new Vector2(1, 0), ABAJO = new Vector2(-1, 0),
            DERECHA = new Vector2(0, 1), IZQUIERDA = new Vector2(0, -1);

    static void cargar() {
        // Un tiro cargado: primero la fuerza (sin flechas), después la forma: alto y con comba a la izquierda.
        escena(new Capturas.Escena("tiro-alto-comba-izquierda") {
            void preparar(Capturas c) {
                c.despejar(c.local(9));
                c.conPelota(c.local(9), 18, -4, ARRIBA);
            }

            void teclas(Capturas c, int t) {
                c.teclas(t < 2 ? ARRIBA : t < 16 ? NADA : new Vector2(-1, -1).nor(), t < 30, false);
            }
        }.camara(-48, 30, 40, 33, -2).fotos(29));

        // Con la pelota al pie: el pase que saldría con un toque.
        escena(new Capturas.Escena("pase-corto") {
            void preparar(Capturas c) {
                c.despejar(c.local(9), c.local(5));
                c.conPelota(c.local(9), 10, -5, new Vector2(14, 3));
                c.m.physics.place(c.local(5), 24, -2);
            }

            void teclas(Capturas c, int t) {
                c.teclas(new Vector2(14, 3).nor(), false, false);
            }
        }.camara(-48, 30, 30, 17, -3).fotos(20));

        // ---- etapa 1: la suela y el golpe ----

        // Pisarla para atrás, soltar y un toque: la pelota sube; después, un toque cada vez que baja.
        escena(new Capturas.Escena("levantar-y-jueguito") {
            float antes;
            boolean apretado;

            void preparar(Capturas c) {
                c.despejar(c.local(6));
                c.conPelota(c.local(6), 0, 0, ARRIBA);
                antes = 0;
                apretado = false;
            }

            void teclas(Capturas c, int t) {
                if (t < 12) {
                    c.teclas(ABAJO, false, true);
                } else if (t < 14) {
                    c.teclas(NADA, t == 13, false);
                } else {
                    float y = c.m.ball.pos.y;
                    boolean x = !apretado && y < antes && y < 0.7f && y > 0.15f;
                    if (x) apretado = true;
                    else if (y > antes) apretado = false;
                    antes = y;
                    c.teclas(NADA, x, false);
                }
            }
        }.camara(90, 12, 7, 0.4f, 0).fotos(6, 16, 30, 48, 75, 110, 140));

        // La elástica: el pie lleva la pelota a la derecha y enseguida a la izquierda.
        escena(new Capturas.Escena("elastica") {
            void preparar(Capturas c) {
                c.despejar(c.local(6));
                c.conPelota(c.local(6), 0, 0, ARRIBA);
            }

            void teclas(Capturas c, int t) {
                if (t < 20) c.teclas(NADA, false, false);
                else if (t < 27) c.teclas(DERECHA, false, true);
                else if (t < 34) c.teclas(IZQUIERDA, false, true);
                else c.teclas(NADA, false, false);
            }
        }.camara(180, 40, 6, 0.6f, 0).fotos(20, 24, 27, 30, 33, 38));

        // El giro: la pisa y la arrastra para atrás; si queda atrás, el cuerpo gira hacia ella.
        escena(new Capturas.Escena("giro") {
            void preparar(Capturas c) {
                c.despejar(c.local(6));
                c.conPelota(c.local(6), 0, 0, ARRIBA);
            }

            void teclas(Capturas c, int t) {
                c.teclas(t >= 10 && t < 40 ? ABAJO : NADA, false, t >= 10 && t < 40);
            }
        }.camara(-60, 35, 7, 0, 0).fotos(10, 18, 26, 34, 42));

        // ---- etapa 2: las piernas, el caño, el puntazo, contener ----

        // Un toque al espacio contra un rival plantado de frente, cerca: pasa entre las piernas abiertas.
        escena(new Capturas.Escena("cano") {
            void preparar(Capturas c) {
                c.despejar(c.local(6), c.visita(5));
                c.conPelota(c.local(6), 0, 0, ARRIBA);
                c.m.physics.place(c.visita(5), 2.4f, 0);
                c.visita(5).facing.set(-1, 0);
            }

            void teclas(Capturas c, int t) {
                c.teclas(NADA, t == 30, false);
            }
        }.camara(90, 8, 5, 2.4f, 0).fotos(29, 40, 46, 52, 64));

        // Puntazo: el pie se estira hasta la pelota del que viene de frente y se la saca.
        escena(new Capturas.Escena("puntazo") {
            void preparar(Capturas c) {
                c.despejar(c.local(4), c.visita(9));
                c.conPelota(c.visita(9), 10, 0, ABAJO);
                c.m.physics.place(c.local(4), 8.2f, 0);
                c.local(4).facing.set(1, 0);
                c.m.controlled = c.local(4);
            }

            void teclas(Capturas c, int t) {
                c.teclas(NADA, t == 20, false);
            }
        }.camara(90, 15, 6, 9.1f, 0).fotos(20, 24, 27, 31, 45));

        // Conteniendo: Z mantenido, de frente a la pelota, despacio y con las piernas abiertas.
        escena(new Capturas.Escena("contener") {
            void preparar(Capturas c) {
                c.despejar(c.local(4), c.visita(9));
                c.conPelota(c.visita(9), 10, 0, ABAJO);
                c.m.physics.place(c.local(4), 5, 1);
                c.m.controlled = c.local(4);
            }

            void teclas(Capturas c, int t) {
                c.teclas(t < 40 ? IZQUIERDA : NADA, false, true);
            }
        }.camara(200, 20, 7, 6, 0.5f).fotos(50));

        // ---- etapa 3: las rodillas ----

        // Corriendo con la pelota: las rodillas se doblan con la zancada.
        escena(new Capturas.Escena("carrera") {
            void preparar(Capturas c) {
                c.despejar(c.local(9));
                c.conPelota(c.local(9), -10, 0, ARRIBA);
            }

            void teclas(Capturas c, int t) {
                c.teclas(ARRIBA, false, false);
            }
        }.camara(90, 10, 9, -2, 0).fotos(60, 63, 66, 69));

        // La suela: el pie arriba de la pelota, al costado, atrás.
        escena(new Capturas.Escena("pisada") {
            void preparar(Capturas c) {
                c.despejar(c.local(6));
                c.conPelota(c.local(6), 0, 0, ARRIBA);
            }

            void teclas(Capturas c, int t) {
                Vector2 f = t < 30 ? NADA : t < 45 ? DERECHA : IZQUIERDA;
                c.teclas(f, false, t >= 15);
            }
        }.camara(150, 25, 5, 0.3f, 0).fotos(25, 42, 56));

        // ---- el control nuevo: potencia, altura y comba ----

        // X y C a la vez, y la flecha a la izquierda: la flecha del tiro sube, se pone roja y curva.
        escena(new Capturas.Escena("golpe") {
            void preparar(Capturas c) {
                c.real = new GolpeScheme();
                c.despejar(c.local(6));
                c.conPelota(c.local(6), -20, 0, ARRIBA);
            }

            void teclas(Capturas c, int t) {
                if (t < 10) c.teclas(NADA, false, false, false);
                else if (t < 40) c.teclas(NADA, true, false, t < 35);
                else if (t < 60) c.teclas(IZQUIERDA, true, false, false);
                else c.teclas(NADA, false, false, false);
            }
        }.camara(-60, 35, 22, -12, 0).fotos(20, 40, 59, 80));

        // ---- la arena de práctica ----

        // Sin rivales de campo, con paredes casi transparentes alrededor; un tiro alto que pega en la pared.
        escena(new Capturas.Escena("practica") {
            void preparar(Capturas c) {
                c.real = new GolpeScheme();
                c.m.setPractice(true);
            }

            void teclas(Capturas c, int t) {
                if (t < 10) c.teclas(NADA, false, false, false);
                else if (t < 40) c.teclas(NADA, true, false, true);
                else if (t < 55) c.teclas(DERECHA, true, false, false);
                else c.teclas(NADA, false, false, false);
            }
        }.camara(-35, 28, 70, 10, 0).fotos(50, 120));

        // ---- etapa 4: la pared ----

        // Pase al compañero, el que la dio sigue corriendo, y la devuelve de primera.
        escena(new Capturas.Escena("pared") {
            int devuelta;

            void preparar(Capturas c) {
                devuelta = -1;
                c.m.aiEnabled = true;
                c.despejar(c.local(9), c.local(10));
                c.conPelota(c.local(9), -5, -3, ARRIBA);
                c.m.physics.place(c.local(10), 6, 5);
            }

            void teclas(Capturas c, int t) {
                // la da, el que la dio sigue corriendo, y cuando el otro la tiene se la devuelve hacia él
                Vector2 hacia = new Vector2(11, 8).nor();
                Player vuelta = c.local(9);
                if (t < 20) c.teclas(ARRIBA, false, false);
                else if (t < 22) c.teclas(hacia, t == 20, false);
                else if (devuelta < 0 && (c.m.owner != c.local(10) || t < 40)) c.teclas(NADA, false, false);
                else {
                    if (devuelta < 0) devuelta = t;
                    c.teclas(t == devuelta ? new Vector2(vuelta.pos).sub(c.local(10).pos).nor() : NADA, t == devuelta, false);
                }
            }
        }.camara(-90, 50, 30, 3, 0).fotos(21, 60, 110, 150, 190));
    }
}
