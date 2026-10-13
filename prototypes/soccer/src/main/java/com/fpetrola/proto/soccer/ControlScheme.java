package com.fpetrola.proto.soccer;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

/**
 * Cómo el jugador que maneja el humano trata la pel
 * ota. Es la pieza pensada para cambiarse: el
 * resto del partido (reglas, física, la máquina) no sabe qué esquema se está usando.
 */
interface ControlScheme {
    String name();

    /** Las teclas, para mostrarlas en pantalla (puede tener varias líneas). */
    String help();

    void update(Match match, Player player, Pad pad, float dt);

    /** Hacia dónde está apuntando, cuando vale la pena mostrarlo (una pelota parada); si no, null. */
    default Vector2 aim() {
        return null;
    }

    /** El tiro que se está cargando, para mostrar por dónde iría; si no se está cargando, null. */
    default ShotPreview shot() {
        return null;
    }

    /** Qué hacen los botones en lo que se está haciendo ahora (la suela, cargando un tiro...); null si nada especial. */
    default String hint() {
        return null;
    }

    /** Si el esquema usa Z (la suela, contener); si no, Z es sólo cambiar de jugador. */
    default boolean usesZ() {
        return true;
    }

    /** Dónde le va a pegar a la pelota, para marcarlo sobre ella; null si no se muestra. */
    default Vector3 contact() {
        return null;
    }
}
