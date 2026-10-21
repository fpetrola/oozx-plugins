package com.fpetrola.proto.soccer;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

/** El juego en una ventana de la compu, con el teclado, el ratón y el joystick que lea GLFW. */
public final class Escritorio {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Soccer");
        config.setWindowedMode(1280, 720);
        config.useVsync(true);
        config.setForegroundFPS(60);
        config.setBackBufferConfig(8, 8, 8, 8, 16, 0, 4);
        // con -Dtactil, los botones de la pantalla del teléfono, tocados con el ratón
        new Lwjgl3Application(new Soccer(Boolean.getBoolean("tactil") ? new Tactil() : new MandoGlfw()), config);
    }
}
