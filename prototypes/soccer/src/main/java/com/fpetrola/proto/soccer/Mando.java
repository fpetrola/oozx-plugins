package com.fpetrola.proto.soccer;

import com.badlogic.gdx.math.Vector2;

/**
 * Lo que se lee de un mando en cada cuadro, venga de donde venga: el joystick de la compu
 * ({@link MandoGlfw}), los botones en la pantalla del teléfono ({@link Tactil}) o un mando por
 * Bluetooth en Android. El juego no sabe cuál es.
 */
abstract class Mando {
    /** Ninguno: para las capturas y para cuando no hay nada que leer. */
    static final Mando NINGUNO = new Mando() {
        @Override
        boolean read() {
            return false;
        }
    };

    /** Lo leído en este cuadro: la dirección en la pantalla (x a la derecha, y para arriba), si viene del stick, y los botones. */
    final Vector2 dir = new Vector2();
    boolean stick, x, z, c, sprint;
    /** La cámara: cuánto girarla (stick derecho, -1 a 1) y cuánto acercarla (R2 menos L2, -1 a 1). */
    final Vector2 look = new Vector2();
    float zoom;
    /** Botones que hacen una cosa por vez: se apretaron recién en este cuadro. */
    boolean nextView, resetView, practice, difficulty;

    /** Lee el mando; false si no hay ninguno. */
    abstract boolean read();

    /** Si se juega tocando la pantalla: entonces arrastrar no mueve la cámara y no hace falta la ayuda del teclado. */
    boolean touch() {
        return false;
    }

    /** Lo que el mando dibuja encima del juego (los botones de la pantalla), si dibuja algo. */
    void draw() {
    }

    void dispose() {
    }

    void clear() {
        dir.setZero();
        look.setZero();
        zoom = 0;
        stick = x = z = c = sprint = nextView = resetView = practice = difficulty = false;
    }

    /** Suma lo leído de otro: los botones de los dos, y la dirección del otro si la mueve. */
    void add(Mando o) {
        if (!o.dir.isZero()) {
            dir.set(o.dir);
            stick = o.stick;
            sprint = o.sprint;
        }
        if (!o.look.isZero()) look.set(o.look);
        if (o.zoom != 0) zoom = o.zoom;
        x |= o.x;
        z |= o.z;
        c |= o.c;
        nextView |= o.nextView;
        resetView |= o.resetView;
        practice |= o.practice;
        difficulty |= o.difficulty;
    }
}
