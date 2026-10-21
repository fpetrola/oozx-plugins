package com.fpetrola.proto.soccer;

import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerMapping;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.math.Vector2;

/**
 * El mando en Android: los botones de la pantalla ({@link Tactil}) y, si hay uno conectado por
 * Bluetooth o USB, un joystick de verdad, con los mismos botones que en la compu (ver
 * {@link MandoGlfw}). Si hay joystick, los botones de la pantalla no se dibujan, pero siguen andando.
 */
final class MandoAndroid extends Mando {
    /** Por debajo de esto el stick está suelto. */
    private static final float DEAD = 0.2f;

    private final Tactil tactil = new Tactil();
    private Controller pad;
    private boolean nextBefore, resetBefore, practiceBefore, difficultyBefore;

    @Override
    boolean read() {
        clear();
        pad = Controllers.getCurrent();
        if (pad != null && pad.isConnected()) readPad(pad.getMapping());
        else pad = null;
        tactil.read();
        add(tactil);
        return true;
    }

    private void readPad(ControllerMapping m) {
        if (stick(m.axisLeftX, m.axisLeftY, dir)) {
            stick = true;
        } else {
            if (button(m.buttonDpadLeft)) dir.x -= 1;
            if (button(m.buttonDpadRight)) dir.x += 1;
            if (button(m.buttonDpadUp)) dir.y += 1;
            if (button(m.buttonDpadDown)) dir.y -= 1;
        }
        stick(m.axisRightX, m.axisRightY, look);
        zoom = (button(m.buttonR2) ? 1 : 0) - (button(m.buttonL2) ? 1 : 0);
        x = button(m.buttonA);
        c = button(m.buttonX);
        sprint = button(m.buttonR1);
        z = button(m.buttonL1);
        boolean next = button(m.buttonY), reset = button(m.buttonRightStick), prac = button(m.buttonStart), diff = button(m.buttonBack);
        nextView = next && !nextBefore;
        resetView = reset && !resetBefore;
        practice = prac && !practiceBefore;
        difficulty = diff && !difficultyBefore;
        nextBefore = next;
        resetBefore = reset;
        practiceBefore = prac;
        difficultyBefore = diff;
    }

    /** Un stick, con la zona muerta sacada y de 0 a 1 parejo afuera; y para arriba. False si está suelto. */
    private boolean stick(int axisX, int axisY, Vector2 out) {
        if (axisX < 0 || axisY < 0) return false;
        float ax = pad.getAxis(axisX), ay = -pad.getAxis(axisY);
        float len = (float) Math.sqrt(ax * ax + ay * ay);
        if (len <= DEAD) return false;
        out.set(ax, ay).scl(Math.min(1, (len - DEAD) / (1 - DEAD)) / len);
        return true;
    }

    private boolean button(int b) {
        return b >= 0 && pad.getButton(b);
    }

    @Override
    boolean touch() {
        return true;
    }

    @Override
    void draw() {
        if (pad == null) tactil.draw();
    }

    @Override
    void dispose() {
        tactil.dispose();
    }
}
