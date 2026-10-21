package com.fpetrola.proto.soccer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;

/**
 * Los botones en la pantalla del teléfono, con los mismos nombres que en el teclado.
 * <ul>
 *   <li>Mitad izquierda: el stick. Donde se apoya el dedo queda el centro, y la dirección es para
 *       dónde se lo corre; llevándolo más allá del borde del círculo, corre a fondo.</li>
 *   <li>Abajo a la derecha: X (el golpe, el grande), Z (la suela, o cambiar de jugador) y C (pegarle
 *       más abajo).</li>
 *   <li>Arriba: otra cámara, la arena de práctica y la dificultad del rival.</li>
 * </ul>
 * Cada dedo es independiente: se puede mover con uno y mantener X con otro.
 */
final class Tactil extends Mando {
    private static final int FINGERS = 10;
    /** Lo que se puede correr el dedo desde el centro, como parte del alto de la pantalla. */
    private static final float REACH = 0.12f;

    /** Un botón redondo: dónde está (como parte del alto de la pantalla, medido desde abajo a la derecha o desde arriba). */
    private static final class Boton {
        final String label;
        /** Si se mide desde arriba (los de las cámaras y eso) o desde abajo a la derecha. */
        final boolean top;
        final float fx, fy, fr;
        float cx, cy, r;
        boolean down, before;

        Boton(String label, boolean top, float fx, float fy, float fr) {
            this.label = label;
            this.top = top;
            this.fx = fx;
            this.fy = fy;
            this.fr = fr;
        }

        void place(int w, int h) {
            r = fr * h;
            cx = top ? fx * h : w - fx * h;
            cy = top ? h - fy * h : fy * h;
        }

        boolean contains(float x, float y) {
            float dx = x - cx, dy = y - cy, grab = r * 1.25f;  // un poco más grande que lo que se ve
            return dx * dx + dy * dy <= grab * grab;
        }
    }

    private final Boton bx = new Boton("X", false, 0.2f, 0.2f, 0.11f);
    private final Boton bz = new Boton("Z", false, 0.47f, 0.13f, 0.08f);
    private final Boton bc = new Boton("C", false, 0.15f, 0.47f, 0.08f);
    private final Boton camara = new Boton("Cámara", true, 0.12f, 0.22f, 0.06f);
    private final Boton practica = new Boton("Práctica", true, 0.3f, 0.22f, 0.06f);
    private final Boton rival = new Boton("Rival", true, 0.48f, 0.22f, 0.06f);
    private final Boton[] botones = {bx, bz, bc, camara, practica, rival};

    /** El dedo que lleva el stick (-1 si ninguno), dónde se apoyó y dónde está, en píxeles con y para arriba. */
    private int finger = -1;
    /** Qué dedos estaban apoyados en el cuadro anterior, para saber cuáles se apoyaron recién. */
    private final boolean[] touched = new boolean[FINGERS];
    private final Vector2 center = new Vector2(), thumb = new Vector2();
    private int w, h;

    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont font;
    private final GlyphLayout layout = new GlyphLayout();
    private final Color fill = new Color(), line = new Color();

    @Override
    boolean read() {
        clear();
        w = Gdx.graphics.getWidth();
        h = Gdx.graphics.getHeight();
        for (Boton b : botones) {
            b.place(w, h);
            b.before = b.down;
            b.down = false;
        }
        if (finger >= 0 && !Gdx.input.isTouched(finger)) finger = -1;
        for (int i = 0; i < FINGERS; i++) {
            boolean fresh = !touched[i];
            touched[i] = Gdx.input.isTouched(i);
            if (!touched[i]) continue;
            float x = Gdx.input.getX(i), y = h - 1 - Gdx.input.getY(i);
            if (i == finger) {
                thumb.set(x, y);
                continue;
            }
            Boton hit = null;
            for (Boton b : botones) if (b.contains(x, y)) hit = b;
            if (hit != null) {
                hit.down = true;
            } else if (finger < 0 && fresh && x < w * 0.5f) {
                // un dedo nuevo en la mitad izquierda, fuera de los botones: ahí queda el stick
                finger = i;
                center.set(x, y);
                thumb.set(x, y);
            }
        }
        if (finger >= 0) {
            float reach = REACH * h;
            dir.set(thumb).sub(center).scl(1 / reach);
            float len = dir.len();
            if (len < 0.15f) dir.setZero();
            else if (len > 1) dir.scl(1 / len);
            stick = true;
            sprint = len > 1.05f;
            if (len > 1.6f) center.set(thumb).mulAdd(dir, -1.6f * reach);  // si se va muy lejos, el centro lo sigue
        }
        x = bx.down;
        z = bz.down;
        c = bc.down;
        nextView = camara.down && !camara.before;
        practice = practica.down && !practica.before;
        difficulty = rival.down && !rival.before;
        return true;
    }

    @Override
    boolean touch() {
        return true;
    }

    @Override
    void draw() {
        if (w == 0) return;
        if (shapes == null) {
            shapes = new ShapeRenderer();
            batch = new SpriteBatch();
            font = new BitmapFont();
        }
        shapes.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
        batch.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Boton b : botones) {
            fill.set(1, 1, 1, b.down ? 0.45f : 0.18f);
            shapes.setColor(fill);
            shapes.circle(b.cx, b.cy, b.r, 40);
        }
        if (finger >= 0) {
            shapes.setColor(fill.set(1, 1, 1, 0.12f));
            shapes.circle(center.x, center.y, REACH * h, 40);
            shapes.setColor(fill.set(1, 1, 1, sprint ? 0.6f : 0.4f));
            shapes.circle(center.x + dir.x * REACH * h, center.y + dir.y * REACH * h, REACH * h * 0.4f, 30);
        }
        shapes.end();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(line.set(1, 1, 1, 0.6f));
        for (Boton b : botones) shapes.circle(b.cx, b.cy, b.r, 40);
        if (finger >= 0) shapes.circle(center.x, center.y, REACH * h, 40);
        shapes.end();
        batch.begin();
        for (Boton b : botones) {
            font.getData().setScale(Math.max(1, b.r / (b.label.length() > 1 ? 30 : 14)));
            layout.setText(font, b.label);
            font.draw(batch, b.label, b.cx - layout.width / 2, b.cy + layout.height / 2);
        }
        batch.end();
    }

    @Override
    void dispose() {
        if (shapes == null) return;
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }
}
