package com.fpetrola.proto.soccer;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.CylinderShapeBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

import java.util.Random;

/**
 * El estadio: el pasto cortado en franjas y cuadros, las líneas, los arcos con su red y los
 * carteles. Quieto: se arma una vez. Las medidas de los arcos y los carteles son las mismas que
 * usa la física.
 */
final class Pitch implements Disposable {
    private static final long ATTR = Usage.Position | Usage.Normal;
    private static final long GRASS = Usage.Position | Usage.Normal | Usage.ColorPacked | Usage.TextureCoordinates;
    private static final long NET = Usage.Position | Usage.Normal | Usage.TextureCoordinates;
    /** El corte del pasto: franjas a lo largo y bandas a lo ancho, que se cruzan como un tablero suave. */
    private static final int STRIPES = 20, BANDS = 10;
    /** Cada cuántos metros se repite la textura del pasto, y el tamaño de los agujeros de la red. */
    private static final float GRASS_TILE = 2.5f, NET_CELL = 0.14f;

    final ModelInstance grass, lines, frames, nets, boards;
    private final Array<Model> models = new Array<>();
    private final Array<Texture> textures = new Array<>();

    Pitch() {
        Texture grassTexture = keep(grassTexture()), netTexture = keep(netTexture());
        grass = new ModelInstance(keep(buildGrass(grassTexture)));
        lines = new ModelInstance(keep(buildLines()));
        frames = new ModelInstance(keep(buildFrames()));
        nets = new ModelInstance(keep(buildNets(netTexture)));
        boards = new ModelInstance(keep(buildBoards()));
    }

    private Model keep(Model m) {
        models.add(m);
        return m;
    }

    private Texture keep(Texture t) {
        textures.add(t);
        return t;
    }

    // ---- el pasto ----------------------------------------------------------------------------

    private static Model buildGrass(Texture texture) {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        MeshPartBuilder b = mb.part("grass", GL20.GL_TRIANGLES, GRASS,
                new Material(TextureAttribute.createDiffuse(texture), ColorAttribute.createDiffuse(Color.WHITE)));
        Color base = new Color(0.31f, 0.6f, 0.24f, 1), c = new Color();
        float L = Match.HALF_L, W = Match.HALF_W, sw = 2 * L / STRIPES, bw = 2 * W / BANDS;
        for (int i = 0; i < STRIPES; i++)
            for (int j = 0; j < BANDS; j++) {
                float k = 1 + (i % 2 == 0 ? 0.08f : -0.08f) + (j % 2 == 0 ? 0.035f : -0.035f);
                quad(b, -L + i * sw, -W + j * bw, -L + (i + 1) * sw, -W + (j + 1) * bw, c.set(base).mul(k, k, k, 1));
            }
        // Hasta los carteles, pasto parejo y un poco más oscuro; más allá, el piso del estadio.
        float bx = Physics.BOARD_X, bz = Physics.BOARD_Z, far = 90;
        c.set(base).mul(0.86f, 0.86f, 0.86f, 1);
        quad(b, -bx, -bz, bx, -W, c);
        quad(b, -bx, W, bx, bz, c);
        quad(b, -bx, -W, -L, W, c);
        quad(b, L, -W, bx, W, c);
        c.set(0.3f, 0.32f, 0.3f, 1);
        quad(b, -far, -far, far, -bz, c);
        quad(b, -far, bz, far, far, c);
        quad(b, -far, -bz, -bx, bz, c);
        quad(b, bx, -bz, far, bz, c);
        return mb.end();
    }

    private static final VertexInfo V00 = new VertexInfo(), V01 = new VertexInfo(), V11 = new VertexInfo(), V10 = new VertexInfo();

    /** Un rectángulo del piso, de (x0, z0) a (x1, z1), mirando para arriba; la textura va en metros. */
    private static void quad(MeshPartBuilder b, float x0, float z0, float x1, float z1, Color c) {
        b.rect(ground(V00, x0, z0, c), ground(V01, x0, z1, c), ground(V11, x1, z1, c), ground(V10, x1, z0, c));
    }

    private static VertexInfo ground(VertexInfo v, float x, float z, Color c) {
        return v.setPos(x, 0, z).setNor(0, 1, 0).setCol(c).setUV(x / GRASS_TILE, z / GRASS_TILE);
    }

    /** El pasto de cerca: un gris con manchas y hojitas que oscurece o aclara el verde de cada franja. */
    private static Texture grassTexture() {
        int n = 256;
        Pixmap pm = new Pixmap(n, n, Pixmap.Format.RGB888);
        Random r = new Random(7);
        for (int y = 0; y < n; y++)
            for (int x = 0; x < n; x++) {
                float g = 0.85f + 0.15f * r.nextFloat();
                pm.drawPixel(x, y, Color.rgba8888(g, g, g, 1));
            }
        for (int i = 0; i < 2600; i++) {
            int x = r.nextInt(n), y = r.nextInt(n), len = 2 + r.nextInt(5);
            float g = r.nextBoolean() ? 1f : 0.7f;
            int col = Color.rgba8888(g * 0.97f, g, g * 0.95f, 1);
            for (int k = 0; k < len; k++) pm.drawPixel((x + k / 3) % n, (y + k) % n, col);
        }
        return repeating(pm);
    }

    /** La malla de la red: transparente, con hilos blancos. */
    private static Texture netTexture() {
        int n = 32;
        Pixmap pm = new Pixmap(n, n, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0, 0, 0, 0);
        pm.fill();
        pm.setColor(1, 1, 1, 0.95f);
        pm.fillRectangle(0, 0, n, 2);
        pm.fillRectangle(0, 0, 2, n);
        return repeating(pm);
    }

    private static Texture repeating(Pixmap pm) {
        Texture t = new Texture(pm, true);
        t.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        t.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        t.setAnisotropicFilter(8f);
        pm.dispose();
        return t;
    }

    // ---- las líneas --------------------------------------------------------------------------

    private static Model buildLines() {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        MeshPartBuilder b = mb.part("lines", GL20.GL_TRIANGLES, ATTR, new Material(ColorAttribute.createDiffuse(new Color(0.96f, 0.96f, 0.94f, 1))));
        float L = Match.HALF_L, W = Match.HALF_W;
        line(b, -L, -W, L, -W);
        line(b, -L, W, L, W);
        line(b, -L, -W, -L, W);
        line(b, L, -W, L, W);
        line(b, 0, -W, 0, W);
        circle(b, 0, 0, 9.15f, 0, 360);
        dot(b, 0, 0);
        for (int s = -1; s <= 1; s += 2) {
            float gx = s * L;
            rect(b, gx, gx - s * Match.BOX_DEPTH, -Match.BOX_HALF, Match.BOX_HALF);
            rect(b, gx, gx - s * 5.5f, -9.16f, 9.16f);
            float spot = gx - s * Match.PENALTY_SPOT;
            dot(b, spot, 0);
            // La medialuna: la parte del círculo de 9,15 m alrededor del punto penal que queda fuera del área.
            float half = MathUtils.acos((Match.BOX_DEPTH - Match.PENALTY_SPOT) / 9.15f) * MathUtils.radDeg;
            float center = s > 0 ? 180 : 0;
            circle(b, spot, 0, 9.15f, center - half, center + half);
            // Los cuartos de círculo de los córners.
            for (int t = -1; t <= 1; t += 2) {
                float from = s > 0 ? (t > 0 ? 180 : 90) : (t > 0 ? 270 : 0);
                circle(b, gx, t * W, 1f, from, from + 90);
            }
        }
        return mb.end();
    }

    private static final Matrix4 LINE = new Matrix4();

    private static void line(MeshPartBuilder b, float x1, float z1, float x2, float z2) {
        float dx = x2 - x1, dz = z2 - z1, len = (float) Math.sqrt(dx * dx + dz * dz);
        LINE.setToTranslation((x1 + x2) / 2, 0.012f, (z1 + z2) / 2).rotate(Vector3.Y, MathUtils.atan2(-dz, dx) * MathUtils.radDeg);
        b.setVertexTransform(LINE);
        BoxShapeBuilder.build(b, 0, 0, 0, len + 0.12f, 0.01f, 0.12f);
        b.setVertexTransform(null);
    }

    /** Un punto pintado: el del medio y los del penal. */
    private static void dot(MeshPartBuilder b, float x, float z) {
        b.setVertexTransform(LINE.setToTranslation(x, 0.012f, z));
        CylinderShapeBuilder.build(b, 0.3f, 0.01f, 0.3f, 16);
        b.setVertexTransform(null);
    }

    private static void rect(MeshPartBuilder b, float x1, float x2, float z1, float z2) {
        line(b, x1, z1, x2, z1);
        line(b, x2, z1, x2, z2);
        line(b, x2, z2, x1, z2);
    }

    private static void circle(MeshPartBuilder b, float cx, float cz, float r, float from, float to) {
        int n = Math.max(4, (int) ((to - from) / 6));
        for (int i = 0; i < n; i++) {
            float a1 = (from + (to - from) * i / n) * MathUtils.degRad, a2 = (from + (to - from) * (i + 1) / n) * MathUtils.degRad;
            line(b, cx + r * MathUtils.cos(a1), cz + r * MathUtils.sin(a1), cx + r * MathUtils.cos(a2), cz + r * MathUtils.sin(a2));
        }
    }

    // ---- los arcos ---------------------------------------------------------------------------

    /** Palos y travesaño redondos, y unos caños finos atrás que sostienen la red. */
    private static Model buildFrames() {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        MeshPartBuilder b = mb.part("frames", GL20.GL_TRIANGLES, ATTR, new Material(ColorAttribute.createDiffuse(Color.WHITE)));
        Matrix4 m = new Matrix4();
        float r = Physics.POST, half = Match.GOAL_HALF, h = Match.GOAL_H, width = half + 2 * r;
        for (int s = -1; s <= 1; s += 2) {
            float x = s * Match.HALF_L, back = x + s * Physics.NET_DEPTH;
            for (int side = -1; side <= 1; side += 2) {
                b.setVertexTransform(m.setToTranslation(x, h / 2, side * (half + r)));
                CylinderShapeBuilder.build(b, 2 * r, h, 2 * r, 14);
                b.setVertexTransform(m.setToTranslation(back, h / 2, side * width));
                CylinderShapeBuilder.build(b, 0.05f, h, 0.05f, 8);
            }
            b.setVertexTransform(m.setToTranslation(x, h + r, 0).rotate(Vector3.X, 90));
            CylinderShapeBuilder.build(b, 2 * r, 2 * width, 2 * r, 14);
            b.setVertexTransform(m.setToTranslation(back, h + r, 0).rotate(Vector3.X, 90));
            CylinderShapeBuilder.build(b, 0.05f, 2 * width, 0.05f, 8);
        }
        b.setVertexTransform(null);
        return mb.end();
    }

    /** La red: fondo, costados y techo, con la malla medida en metros para que los agujeros sean parejos; se ve de los dos lados. */
    private static Model buildNets(Texture texture) {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        Material material = new Material(TextureAttribute.createDiffuse(texture), ColorAttribute.createDiffuse(Color.WHITE),
                new BlendingAttribute(true, 1f), FloatAttribute.createAlphaTest(0.05f), IntAttribute.createCullFace(GL20.GL_NONE));
        MeshPartBuilder b = mb.part("nets", GL20.GL_TRIANGLES, NET, material);
        float half = Match.GOAL_HALF + 2 * Physics.POST, h = Match.GOAL_H + 2 * Physics.POST;
        for (int s = -1; s <= 1; s += 2) {
            float x0 = s * Match.HALF_L, x1 = x0 + s * Physics.NET_DEPTH;
            netQuad(b, x1, 0, -half, x1, h, -half, x1, h, half, x1, 0, half, 2, 1, -s, 0, 0);
            for (int side = -1; side <= 1; side += 2) {
                float z = side * half;
                netQuad(b, x0, 0, z, x1, 0, z, x1, h, z, x0, h, z, 0, 1, 0, 0, -side);
            }
            netQuad(b, x0, h, -half, x1, h, -half, x1, h, half, x0, h, half, 0, 2, 0, 1, 0);
        }
        return mb.end();
    }

    private static final VertexInfo N0 = new VertexInfo(), N1 = new VertexInfo(), N2 = new VertexInfo(), N3 = new VertexInfo();
    private static final float[] P = new float[3];

    /** Un paño de red entre cuatro esquinas; la malla va en metros a lo largo de los ejes {@code u} y {@code v} (0 = x, 1 = y, 2 = z). */
    private static void netQuad(MeshPartBuilder b, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
                                float dx, float dy, float dz, int u, int v, float nx, float ny, float nz) {
        b.rect(net(N0, ax, ay, az, u, v, nx, ny, nz), net(N1, bx, by, bz, u, v, nx, ny, nz),
                net(N2, cx, cy, cz, u, v, nx, ny, nz), net(N3, dx, dy, dz, u, v, nx, ny, nz));
    }

    private static VertexInfo net(VertexInfo vi, float x, float y, float z, int u, int v, float nx, float ny, float nz) {
        P[0] = x;
        P[1] = y;
        P[2] = z;
        return vi.setPos(x, y, z).setNor(nx, ny, nz).setUV(P[u] / NET_CELL, P[v] / NET_CELL);
    }

    // ---- los carteles ------------------------------------------------------------------------

    /** Carteles de colores alrededor de la cancha, en tramos de diez metros, con una franja blanca arriba. */
    private static Model buildBoards() {
        Color[] colors = {Color.valueOf("c62828"), Color.valueOf("1565c0"), Color.valueOf("f9a825"), Color.valueOf("2e7d32"), Color.valueOf("6a1b9a"), Color.valueOf("ef6c00")};
        float bx = Physics.BOARD_X, bz = Physics.BOARD_Z, h = Physics.BOARD_H, seg = 10, t = 0.12f;
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        for (int c = 0; c <= colors.length; c++) {
            boolean trim = c == colors.length;
            MeshPartBuilder b = mb.part("board" + c, GL20.GL_TRIANGLES, ATTR, new Material(ColorAttribute.createDiffuse(trim ? Color.WHITE : colors[c])));
            int k = 0;
            for (int s = -1; s <= 1; s += 2) {
                for (float x = -bx; x < bx - 0.01f; x += seg, k++) {
                    float len = Math.min(seg, bx - x);
                    if (trim) BoxShapeBuilder.build(b, x + len / 2, h + 0.03f, s * bz, len, 0.06f, t + 0.02f);
                    else if (k % colors.length == c) BoxShapeBuilder.build(b, x + len / 2, h / 2, s * bz, len, h, t);
                }
                for (float z = -bz; z < bz - 0.01f; z += seg, k++) {
                    float len = Math.min(seg, bz - z);
                    if (trim) BoxShapeBuilder.build(b, s * bx, h + 0.03f, z + len / 2, t + 0.02f, 0.06f, len);
                    else if (k % colors.length == c) BoxShapeBuilder.build(b, s * bx, h / 2, z + len / 2, t, h, len);
                }
            }
        }
        return mb.end();
    }

    @Override
    public void dispose() {
        for (Model m : models) m.dispose();
        for (Texture t : textures) t.dispose();
    }
}
