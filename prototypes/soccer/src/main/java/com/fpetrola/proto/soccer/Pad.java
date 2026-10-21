package com.fpetrola.proto.soccer;

import com.badlogic.gdx.math.Vector2;

/**
 * Lo único que el jugador humano le dice al juego: una dirección y dos botones. X es el pie que le
 * pega a la pelota; Z, con la pelota, es la suela, y sin la pelota cambia de jugador. Todo esquema
 * de control tiene que arreglarse con esto; la idea es justamente ver cuánto juego sale de tan poco.
 * <p>
 * La dirección ya viene en coordenadas de la cancha (x a lo largo, y = z del mundo a lo ancho),
 * traducida desde la pantalla según hacia dónde mire la cámara.
 */
final class Pad {
    final Vector2 dir = new Vector2();
    /** X: si está apretado, y si se apretó o se soltó desde la última vez que el partido miró. */
    boolean down, pressed, released;
    /** Z, igual. */
    boolean zDown, zPressed, zReleased;
    /** C: sólo si está apretado (la variante con botón de elevación: mantenido, le pega más abajo). */
    boolean cDown;
    /** Si la dirección viene de un stick analógico (dice exactamente para dónde, no es una de ocho teclas). */
    boolean analog;
    /** Si va corriendo a fondo: con el teclado siempre; con el joystick, mientras se mantiene R1. */
    boolean sprint = true;

    void set(Vector2 worldDir, boolean button) {
        set(worldDir, button, false);
    }

    /** Lo que se lee del teclado en cada cuadro. Los cambios de los botones quedan hasta que el partido los vea. */
    void set(Vector2 worldDir, boolean x, boolean z) {
        set(worldDir, x, z, false);
    }

    void set(Vector2 worldDir, boolean x, boolean z, boolean c) {
        cDown = c;
        dir.set(worldDir);
        if (dir.len2() > 1) dir.nor();
        if (x && !down) pressed = true;
        if (!x && down) released = true;
        down = x;
        if (z && !zDown) zPressed = true;
        if (!z && zDown) zReleased = true;
        zDown = z;
    }

    /** El partido ya vio los cambios de los botones: puede pensar varias veces por cuadro y no tiene que verlos dos veces. */
    void consume() {
        pressed = released = zPressed = zReleased = false;
    }
}
