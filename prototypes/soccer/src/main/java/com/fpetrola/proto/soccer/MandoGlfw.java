package com.fpetrola.proto.soccer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

/**
 * Un joystick de verdad en la compu (el de PS5 por USB, o cualquiera que GLFW reconozca como mando), leído
 * directo de GLFW. Si no hay mando conectado, no hace nada.
 * <ul>
 *   <li>Stick izquierdo (o la cruceta): mover y apuntar, analógico.</li>
 *   <li>Cruz: le pega en el medio (X). Cuadrado: le pega abajo (C).</li>
 *   <li>R1: correr a fondo. L1: cambiar de jugador (Z).</li>
 *   <li>Stick derecho: girar la cámara. L2 / R2: alejarla / acercarla. Click del stick derecho: la
 *       cámara vuelve a la de siempre. Triángulo: otra cámara (como Tab).</li>
 *   <li>Options: la arena de práctica (como P). Create: la dificultad, de a una.</li>
 * </ul>
 */
final class MandoGlfw extends Mando {
    /** Por debajo de esto el stick está suelto. */
    private static final float DEAD = 0.2f;

    private final GLFWGamepadState state = GLFWGamepadState.create();
    private final boolean[] before = new boolean[GLFW.GLFW_GAMEPAD_BUTTON_LAST + 1];
    private int joystick = -1;
    private String name;

    @Override
    boolean read() {
        clear();
        if (joystick < 0 || !GLFW.glfwJoystickIsGamepad(joystick)) find();
        if (joystick < 0 || !GLFW.glfwGetGamepadState(joystick, state)) return false;
        if (stick(GLFW.GLFW_GAMEPAD_AXIS_LEFT_X, GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y, dir)) {
            stick = true;
        } else {
            if (button(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT)) dir.x -= 1;
            if (button(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_RIGHT)) dir.x += 1;
            if (button(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP)) dir.y += 1;
            if (button(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_DOWN)) dir.y -= 1;
        }
        stick(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X, GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y, look);
        // los gatillos van de -1 (sueltos) a 1 (a fondo)
        zoom = (trigger(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER) - trigger(GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER));
        x = button(GLFW.GLFW_GAMEPAD_BUTTON_CROSS);
        c = button(GLFW.GLFW_GAMEPAD_BUTTON_SQUARE);
        sprint = button(GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER);
        z = button(GLFW.GLFW_GAMEPAD_BUTTON_LEFT_BUMPER);
        nextView = pressed(GLFW.GLFW_GAMEPAD_BUTTON_TRIANGLE);
        resetView = pressed(GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_THUMB);
        practice = pressed(GLFW.GLFW_GAMEPAD_BUTTON_START);
        difficulty = pressed(GLFW.GLFW_GAMEPAD_BUTTON_BACK);
        for (int b = 0; b < before.length; b++) before[b] = button(b);
        return true;
    }

    /** Un stick, con la zona muerta sacada y de 0 a 1 parejo afuera; y para arriba. False si está suelto. */
    private boolean stick(int axisX, int axisY, Vector2 out) {
        float ax = state.axes(axisX), ay = -state.axes(axisY);
        float len = (float) Math.sqrt(ax * ax + ay * ay);
        if (len <= DEAD) return false;
        out.set(ax, ay).scl(Math.min(1, (len - DEAD) / (1 - DEAD)) / len);
        return true;
    }

    /** Un gatillo, de 0 (suelto) a 1 (a fondo). */
    private float trigger(int axis) {
        return Math.max(0, (state.axes(axis) + 1) / 2 - 0.05f) / 0.95f;
    }

    private boolean button(int b) {
        return state.buttons(b) == GLFW.GLFW_PRESS;
    }

    private boolean pressed(int b) {
        return button(b) && !before[b];
    }

    private void find() {
        joystick = -1;
        for (int j = GLFW.GLFW_JOYSTICK_1; j <= GLFW.GLFW_JOYSTICK_LAST; j++) {
            if (GLFW.glfwJoystickIsGamepad(j)) {
                joystick = j;
                String n = GLFW.glfwGetGamepadName(j);
                if (n != null && !n.equals(name)) Gdx.app.log("Mando", "conectado: " + n);
                name = n;
                return;
            }
        }
    }
}
