package com.fpetrola.proto.soccer;

import com.badlogic.gdx.math.Vector3;

/**
 * Lo que va a hacer la pelota, para mostrarlo: el pase que saldría con un toque, o el tiro que se
 * está cargando, con qué fuerza, altura y comba saldría si se suelta ahora; y por dónde iría la
 * pelota (la misma cuenta del aire que usa la física).
 */
final class ShotPreview {
    static final int MAX = 64;

    /** Un pase (lo que saldría con un toque) o un tiro que se está cargando. */
    boolean pass;
    /** A quién va el pase; null si va al espacio. */
    Player receiver;
    /** Qué es, si no es un pase a alguien: un toque que la levanta, un jueguito, un autopase. */
    String label;

    /** De 0 a 1, y si ya quedó fija (tocó una flecha de forma). */
    float power;
    boolean powerFixed;
    /** Grados sobre el piso. */
    float elevation;
    /** Giro sobre el eje vertical, rad/s: positivo curva hacia la derecha. */
    float curl;
    /** Los puntos del recorrido, de a tres coordenadas. */
    final float[] xyz = new float[MAX * 3];
    int count;

    void add(Vector3 p) {
        if (count == MAX) return;
        xyz[count * 3] = p.x;
        xyz[count * 3 + 1] = p.y;
        xyz[count * 3 + 2] = p.z;
        count++;
    }
}
