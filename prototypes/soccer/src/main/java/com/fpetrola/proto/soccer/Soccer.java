package com.fpetrola.proto.soccer;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalShadowLight;
import com.badlogic.gdx.graphics.g3d.utils.DepthShaderProvider;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

/**
 * Ventana, cámara y dibujo. Teclado: flechas para moverse; X (potencia) y C (más abajo, más alta)
 * arman el golpe, que sale al soltar los dos; Z cambia al compañero que llega primero; P prende y
 * apaga la arena de práctica (sin rivales de campo, con paredes que no dejan salir la pelota); F1 a
 * F6 ajustan qué tan rápido crecen la potencia, la altura y la comba; I
 * prende y apaga la máquina, 1, 2 y 3 eligen qué tan bien juega, y Tab vuelve a seguir la pelota o
 * cambia de cámara. Con el ratón: arrastrar mueve la cámara, con el botón derecho o el del medio (o
 * con Shift) la gira, y la rueda acerca o aleja.
 * <p>
 * El sol está casi arriba y un poco de costado: las sombras de los jugadores y de la pelota caen
 * cerca de ellos, y lo que se separa la pelota de su sombra dice qué tan alta va.
 */
public final class Soccer extends ApplicationAdapter {
    private static final long ATTR = Usage.Position | Usage.Normal;
    /** El color de lo que tiene que ver con un pase: su flecha y el anillo del que lo recibiría. */
    private static final Color PASS = new Color(0.55f, 0.95f, 1f, 1);

    private Match match;
    private final Pad pad = new Pad();
    private PerspectiveCamera cam;
    private ModelBatch batch, shadowBatch;
    private Environment env;
    private DirectionalShadowLight sun;
    private Pitch pitch;
    private final Array<Model> models = new Array<>();
    private Texture ballTexture;
    private ModelInstance ball, marker, arrow, ring, passRing, arrowHead, footMark, contactMark, wall;
    /**
     * La flecha curva del tiro que se está cargando: un tramo por cada par de puntos del recorrido,
     * su sombra justo debajo sobre el pasto (se ve para dónde curva) y unas varillas que las unen
     * (se ve qué tan alto va).
     */
    private final Array<ModelInstance> segments = new Array<>(), groundSegments = new Array<>(), stems = new Array<>();
    private final Color shotColor = new Color();
    private final Array<Puppet> puppets = new Array<>();
    /** El último que sacó un lateral, para que termine el gesto con los brazos. */
    private Player lastThrower;
    private SpriteBatch hud;
    private BitmapFont font;
    private Ruidos ruidos;

    /** Cámaras armadas: hacia dónde mira (grados sobre el piso), qué tan inclinada y a qué distancia. */
    private enum View {
        SENSIBLE("cámara Sensible", 0, 43, 38), TV("cámara TV", -90, 20, 62);

        final String label;
        final float yaw, pitch, distance;

        View(String label, float yaw, float pitch, float distance) {
            this.label = label;
            this.yaw = yaw;
            this.pitch = pitch;
            this.distance = distance;
        }
    }

    private View view = View.SENSIBLE;
    /** Si es mayor que cero, cada cuadro avanza exactamente esto (para las capturas); si no, lo que pasó de verdad. */
    private float fixedDt;
    private float yaw = view.yaw, camPitch = view.pitch, distance = view.distance;
    /** Movida a mano: deja de seguir la pelota hasta que se apriete C. */
    private boolean freeCam;
    private final Vector3 focus = new Vector3(), target = new Vector3(), a = new Vector3(), b = new Vector3();
    private final Quaternion q = new Quaternion();
    private final Vector2 tmp = new Vector2(), fwd = new Vector2();

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Soccer");
        config.setWindowedMode(1280, 720);
        config.useVsync(true);
        config.setForegroundFPS(60);
        config.setBackBufferConfig(8, 8, 8, 8, 16, 0, 4);
        new Lwjgl3Application(new Soccer(), config);
    }

    @Override
    public void create() {
        match = new Match();
        ruidos = new Ruidos();
        match.ears = ruidos;
        batch = new ModelBatch();
        shadowBatch = new ModelBatch(new DepthShaderProvider());
        sun = new DirectionalShadowLight(4096, 4096, 110f, 110f, 1f, 300f);
        sun.set(0.85f, 0.85f, 0.8f, -0.3f, -1f, -0.4f);
        env = new Environment();
        env.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.46f, 0.47f, 0.5f, 1f));
        env.add(sun);
        env.shadowMap = sun;
        cam = new PerspectiveCamera(40, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.near = 0.5f;
        cam.far = 400f;

        pitch = new Pitch();
        ballTexture = ballTexture();
        ball = new ModelInstance(keep(new ModelBuilder().createSphere(Ball.RADIUS * 2, Ball.RADIUS * 2, Ball.RADIUS * 2, 24, 18,
                new Material(TextureAttribute.createDiffuse(ballTexture)), ATTR | Usage.TextureCoordinates)));
        marker = new ModelInstance(keep(new ModelBuilder().createCone(0.5f, 0.5f, 0.5f, 8,
                new Material(ColorAttribute.createDiffuse(Color.YELLOW)), ATTR)));
        Model homeModel = keep(Puppet.build(match.home.shirt, match.home.shorts));
        Model awayModel = keep(Puppet.build(match.away.shirt, match.away.shorts));
        Model homeKeeper = keep(Puppet.build(Puppet.KEEPER, match.home.shorts));
        Model awayKeeper = keep(Puppet.build(Puppet.KEEPER, match.away.shorts));
        for (Player p : match.players) {
            boolean home = p.team == match.home;
            puppets.add(new Puppet(p.keeper ? (home ? homeKeeper : awayKeeper) : (home ? homeModel : awayModel)));
        }
        arrow = new ModelInstance(keep(buildArrow()));
        ring = new ModelInstance(keep(new ModelBuilder().createCylinder(1.3f, 0.02f, 1.3f, 24,
                new Material(ColorAttribute.createDiffuse(new Color(0.4f, 0.85f, 1f, 1)), new BlendingAttribute(0.55f)), ATTR)));
        passRing = new ModelInstance(keep(new ModelBuilder().createCylinder(1.3f, 0.02f, 1.3f, 24,
                new Material(ColorAttribute.createDiffuse(PASS), new BlendingAttribute(0.6f)), ATTR)));
        Model segment = keep(new ModelBuilder().createBox(1f, 0.13f, 0.13f, new Material(ColorAttribute.createDiffuse(Color.YELLOW)), ATTR));
        Model groundSegment = keep(new ModelBuilder().createBox(1f, 0.01f, 0.1f,
                new Material(ColorAttribute.createDiffuse(Color.BLACK), new BlendingAttribute(0.45f)), ATTR));
        Model stem = keep(new ModelBuilder().createBox(0.04f, 1f, 0.04f,
                new Material(ColorAttribute.createDiffuse(Color.WHITE), new BlendingAttribute(0.6f)), ATTR));
        for (int i = 0; i < ShotPreview.MAX; i++) {
            segments.add(new ModelInstance(segment));
            groundSegments.add(new ModelInstance(groundSegment));
            stems.add(new ModelInstance(stem));
        }
        contactMark = new ModelInstance(keep(new ModelBuilder().createSphere(0.07f, 0.07f, 0.07f, 10, 8,
                new Material(ColorAttribute.createDiffuse(new Color(0.95f, 0.1f, 0.1f, 1))), ATTR)));
        wall = new ModelInstance(keep(new ModelBuilder().createBox(1f, 1f, 1f,
                new Material(ColorAttribute.createDiffuse(new Color(0.75f, 0.9f, 1f, 1)), new BlendingAttribute(0.12f)), ATTR)));
        footMark = new ModelInstance(keep(new ModelBuilder().createCylinder(0.2f, 0.03f, 0.2f, 12,
                new Material(ColorAttribute.createDiffuse(new Color(1f, 0.55f, 0.1f, 1))), ATTR)));
        arrowHead = new ModelInstance(keep(new ModelBuilder().createCone(0.6f, 0.9f, 0.6f, 12,
                new Material(ColorAttribute.createDiffuse(Color.YELLOW)), ATTR)));
        Gdx.input.setInputProcessor(new Mouse());

        hud = new SpriteBatch();
        font = new BitmapFont();
        font.getData().setScale(1.6f);
    }

    private Model keep(Model m) {
        models.add(m);
        return m;
    }

    @Override
    public void render() {
        float dt = fixedDt > 0 ? fixedDt : Math.min(Gdx.graphics.getDeltaTime(), 1 / 20f);
        if (Gdx.input.isKeyJustPressed(Keys.ESCAPE)) Gdx.app.exit();
        if (Gdx.input.isKeyJustPressed(Keys.I)) match.aiEnabled = !match.aiEnabled;
        if (Gdx.input.isKeyJustPressed(Keys.P)) match.setPractice(!match.practice);
        if (match.scheme instanceof GolpeScheme g) tune(g);
        if (Gdx.input.isKeyJustPressed(Keys.NUM_1)) match.difficulty = Difficulty.EASY;
        if (Gdx.input.isKeyJustPressed(Keys.NUM_2)) match.difficulty = Difficulty.NORMAL;
        if (Gdx.input.isKeyJustPressed(Keys.NUM_3)) match.difficulty = Difficulty.HARD;
        if (Gdx.input.isKeyJustPressed(Keys.TAB)) {
            // Si estaba movida a mano vuelve a seguir la pelota; si ya la seguía, cambia de cámara.
            if (!freeCam) view = View.values()[(view.ordinal() + 1) % View.values().length];
            freeCam = false;
            yaw = view.yaw;
            camPitch = view.pitch;
            distance = view.distance;
        }

        readPad();
        match.update(pad, dt);
        updateCamera(dt);
        pose();

        // Primero las sombras: lo que las proyecta, visto desde el sol, alrededor de lo que mira la cámara.
        sun.begin(focus, sun.direction);
        shadowBatch.begin(sun.getCamera());
        for (Puppet p : puppets) shadowBatch.render(p.instance);
        shadowBatch.render(ball);
        shadowBatch.render(pitch.frames);
        shadowBatch.render(pitch.boards);
        shadowBatch.end();
        sun.end();

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        Gdx.gl.glClearColor(0.42f, 0.6f, 0.82f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        batch.begin(cam);
        batch.render(pitch.grass, env);
        batch.render(pitch.lines, env);
        batch.render(pitch.frames, env);
        batch.render(pitch.boards, env);
        for (Puppet p : puppets) batch.render(p.instance, env);
        Player c = match.controlled;
        Vector2 aim = match.scheme.aim();
        if (aim != null) {
            // La flecha sale de la pelota; de los pies del jugador si saca un lateral, barre o va de cabeza.
            boolean feet = match.throwing(c) || c.busy();
            float x = feet ? c.pos.x : match.ball.pos.x, z = feet ? c.pos.y : match.ball.pos.z;
            arrow.transform.setToTranslation(x, 0.03f, z).rotate(Vector3.Y, MathUtils.atan2(-aim.y, aim.x) * MathUtils.radDeg);
            batch.render(arrow, env);
        }
        marker.transform.setToTranslation(c.pos.x, 2.4f, c.pos.y).rotate(Vector3.X, 180);
        batch.render(marker, env);
        if (match.switchTo != null) {
            ring.transform.setToTranslation(match.switchTo.pos.x, 0.03f, match.switchTo.pos.y);
            batch.render(ring, env);
        }
        batch.render(ball, env);
        if (c.foot.active) {
            // El pie de juego, cuando lo mueve el control: arriba de la pelota en la suela, de costado en un amague.
            footMark.transform.setToTranslation(c.foot.pos.x, c.foot.pos.y + 0.03f, c.foot.pos.z);
            batch.render(footMark, env);
        }
        Vector3 hit = match.scheme.contact();
        if (hit != null) {
            // donde le va a pegar el pie (la variante con botón de elevación)
            contactMark.transform.setToTranslation(hit);
            batch.render(contactMark, env);
        }
        drawShot(match.scheme.shot());
        batch.render(pitch.nets, env);
        if (match.practice) {
            // las paredes de la arena de práctica, casi transparentes
            for (float[] w : match.physics.wallBoxes) {
                wall.transform.setToTranslation(w[0], w[1], w[2]).scale(w[3] * 2, w[4] * 2, w[5] * 2);
                batch.render(wall, env);
            }
        }
        batch.end();

        drawHud();
    }

    /**
     * La flecha del tiro que se está cargando: sigue el recorrido que haría la pelota si se suelta
     * ahora (sube con la altura, se curva con la comba, se alarga con la fuerza) y termina en una
     * punta. Va de amarillo a rojo según la fuerza. Abajo, sobre el pasto, va su sombra, y cada
     * tanto una varilla las une: desde cualquier cámara se ve para dónde curva y qué tan alto va.
     */
    private void drawShot(ShotPreview s) {
        if (s == null || s.count < 2) return;
        if (s.pass) shotColor.set(PASS);
        else shotColor.set(Color.YELLOW).lerp(Color.RED, s.power);
        if (s.pass && s.receiver != null) {
            passRing.transform.setToTranslation(s.receiver.pos.x, 0.03f, s.receiver.pos.y);
            batch.render(passRing, env);
        }
        for (int i = 0; i + 1 < s.count; i++) {
            a.set(s.xyz[i * 3], s.xyz[i * 3 + 1], s.xyz[i * 3 + 2]);
            b.set(s.xyz[i * 3 + 3], s.xyz[i * 3 + 4], s.xyz[i * 3 + 5]);
            float len = a.dst(b);
            if (len < 0.001f) continue;
            if (i % 3 == 0 && a.y > 0.35f) {
                ModelInstance stem = stems.get(i);
                stem.transform.setToTranslation(a.x, a.y / 2, a.z).scale(1, a.y, 1);
                batch.render(stem, env);
            }
            float flat = Vector2.dst(a.x, a.z, b.x, b.z);
            if (flat > 0.001f) {
                ModelInstance shadow = groundSegments.get(i);
                shadow.transform.setToTranslation((a.x + b.x) / 2, 0.02f, (a.z + b.z) / 2)
                        .rotate(Vector3.Y, MathUtils.atan2(-(b.z - a.z), b.x - a.x) * MathUtils.radDeg).scale(flat, 1, 1);
                batch.render(shadow, env);
            }
            ModelInstance seg = segments.get(i);
            q.setFromCross(Vector3.X, b.sub(a).nor());
            seg.transform.set(a.mulAdd(b, len / 2), q).scale(len, 1, 1);
            ((ColorAttribute) seg.materials.get(0).get(ColorAttribute.Diffuse)).color.set(shotColor);
            batch.render(seg, env);
        }
        int n = s.count - 1;
        a.set(s.xyz[n * 3 - 3], s.xyz[n * 3 - 2], s.xyz[n * 3 - 1]);
        b.set(s.xyz[n * 3], s.xyz[n * 3 + 1], s.xyz[n * 3 + 2]);
        if (a.dst2(b) > 1e-6f) q.setFromCross(Vector3.Y, a.sub(b).scl(-1).nor());
        arrowHead.transform.set(b, q);
        ((ColorAttribute) arrowHead.materials.get(0).get(ColorAttribute.Diffuse)).color.set(shotColor);
        batch.render(arrowHead, env);
    }

    /** Pone a cada muñeco como está su jugador, y la pelota donde está. */
    private void pose() {
        for (int i = 0; i < match.players.size(); i++) {
            Player p = match.players.get(i);
            puppets.get(i).pose(p, match.holder == p, match.throwing(p) || (p.kickAnim > 0 && p == lastThrower));
        }
        if (match.setPiece != null && match.setPiece.kind == SetPiece.Kind.THROW_IN) lastThrower = match.setPiece.taker;
        ball.transform.set(match.ball.pos, match.ball.rot);  // girada como en la física: se la ve rodar
    }

    /**
     * La pelota de gajos blancos con doce manchas negras (en las puntas de un icosaedro, como los
     * pentágonos de la de verdad): así se ve cómo gira y que rueda, en vez de patinar.
     */
    private static Texture ballTexture() {
        float g = (1 + (float) Math.sqrt(5)) / 2;
        float[][] tips = {{0, 1, g}, {0, 1, -g}, {0, -1, g}, {0, -1, -g}, {1, g, 0}, {1, -g, 0}, {-1, g, 0}, {-1, -g, 0},
                {g, 0, 1}, {g, 0, -1}, {-g, 0, 1}, {-g, 0, -1}};
        for (float[] t : tips) {
            float l = (float) Math.sqrt(t[0] * t[0] + t[1] * t[1] + t[2] * t[2]);
            t[0] /= l;
            t[1] /= l;
            t[2] /= l;
        }
        int w = 256, h = 128;
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        float spot = MathUtils.cos(0.36f), seam = MathUtils.cos(0.42f);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float lon = (x + 0.5f) / w * MathUtils.PI2, lat = (y + 0.5f) / h * MathUtils.PI;
                float dx = MathUtils.sin(lat) * MathUtils.cos(lon), dy = MathUtils.cos(lat), dz = MathUtils.sin(lat) * MathUtils.sin(lon);
                float near = -1;
                for (float[] t : tips) near = Math.max(near, dx * t[0] + dy * t[1] + dz * t[2]);
                pm.drawPixel(x, y, near > spot ? 0x1c1c1cff : near > seam ? 0xc8c8c8ff : 0xf4f4f4ff);
            }
        }
        Texture tex = new Texture(pm, true);
        tex.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        pm.dispose();
        return tex;
    }

    /**
     * Ajustes en vivo del golpe, para probar qué se siente mejor: F1 y F2 hacen más lenta o más rápida
     * la potencia, F3 y F4 la altura, F5 y F6 la comba, y F7 y F8 acortan o alargan la espera entre
     * soltar C y apretar X.
     */
    private void tune(GolpeScheme g) {
        if (Gdx.input.isKeyJustPressed(Keys.F1)) g.fill = Math.min(3f, g.fill + 0.1f);
        if (Gdx.input.isKeyJustPressed(Keys.F2)) g.fill = Math.max(0.1f, g.fill - 0.1f);
        if (Gdx.input.isKeyJustPressed(Keys.F3)) g.sweep = Math.min(3f, g.sweep + 0.1f);
        if (Gdx.input.isKeyJustPressed(Keys.F4)) g.sweep = Math.max(0.1f, g.sweep - 0.1f);
        if (Gdx.input.isKeyJustPressed(Keys.F5)) g.bend = Math.max(25f, g.bend - 25f);
        if (Gdx.input.isKeyJustPressed(Keys.F6)) g.bend = Math.min(1000f, g.bend + 25f);
        if (Gdx.input.isKeyJustPressed(Keys.F7)) g.grace = Math.max(0.1f, g.grace - 0.1f);
        if (Gdx.input.isKeyJustPressed(Keys.F8)) g.grace = Math.min(2f, g.grace + 0.1f);
    }

    private void readPad() {
        float sx = 0, sy = 0;
        if (Gdx.input.isKeyPressed(Keys.LEFT)) sx -= 1;
        if (Gdx.input.isKeyPressed(Keys.RIGHT)) sx += 1;
        if (Gdx.input.isKeyPressed(Keys.UP)) sy += 1;
        if (Gdx.input.isKeyPressed(Keys.DOWN)) sy -= 1;
        boolean button = Gdx.input.isKeyPressed(Keys.X), switching = Gdx.input.isKeyPressed(Keys.Z);
        // De pantalla a cancha: "arriba" es hacia donde mira la cámara, aplastado sobre el piso.
        fwd.set(cam.direction.x, cam.direction.z).nor();
        tmp.set(fwd).scl(sy).add(-fwd.y * sx, fwd.x * sx);
        pad.set(tmp, button, switching, Gdx.input.isKeyPressed(Keys.C));
    }

    private void updateCamera(float dt) {
        if (!freeCam) {
            float tx = MathUtils.clamp(match.ball.pos.x, -Match.HALF_L + 8, Match.HALF_L - 8);
            float tz = MathUtils.clamp(match.ball.pos.z, -Match.HALF_W + 6, Match.HALF_W - 6);
            focus.lerp(target.set(tx, 0, tz), Math.min(1, 4f * dt));
        }
        // Gira alrededor del punto que mira: yaw es hacia dónde mira sobre el piso y camPitch cuánto mira hacia abajo.
        float flat = MathUtils.cosDeg(camPitch) * distance;
        cam.position.set(focus.x - MathUtils.cosDeg(yaw) * flat, MathUtils.sinDeg(camPitch) * distance,
                focus.z - MathUtils.sinDeg(yaw) * flat);
        cam.up.set(Vector3.Y);
        cam.lookAt(focus);
        cam.up.set(Vector3.Y);
        cam.viewportWidth = Gdx.graphics.getWidth();
        cam.viewportHeight = Gdx.graphics.getHeight();
        cam.update();
    }

    private void drawHud() {
        int w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        hud.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
        hud.begin();
        int t = (int) match.clock;
        font.draw(hud, String.format("%s %d - %d %s     %02d:%02d", match.home.name, match.home.goals,
                match.away.goals, match.away.name, t / 60, t % 60), 20, h - 20);
        font.draw(hud, match.aiEnabled ? "Rival: " + match.difficulty.label : "IA apagada", w - 260, h - 20);
        if (match.scheme instanceof GolpeScheme g) {
            font.getData().setScale(1.1f);
            font.draw(hud, "Ajustes: " + g.settings(), 20, h - 50);
            font.getData().setScale(1.6f);
        }
        if (match.messageTime > 0) font.draw(hud, match.message, w / 2f - 80, h / 2f + 120);
        ShotPreview shot = match.scheme.shot();
        if (shot != null && shot.pass) {
            String what = shot.receiver != null ? "Pase a #" + shot.receiver.number : shot.label != null ? shot.label : "Pase al espacio";
            font.draw(hud, what, w / 2f - 90, h - 70);
        } else if (shot != null) {
            String side = shot.curl > 1 ? " a la derecha" : shot.curl < -1 ? " a la izquierda" : "";
            font.draw(hud, String.format("Fuerza %d%%%s   Altura %d grados   Comba %d%s", Math.round(shot.power * 100), shot.powerFixed ? " (fija)" : "",
                    Math.round(shot.elevation), Math.round(Math.abs(shot.curl)), side), w / 2f - 260, h - 70);
        }
        font.getData().setScale(1.1f);
        String hint = match.scheme.hint();
        if (hint != null) font.draw(hud, hint, 20, 134);
        font.draw(hud, match.scheme.name() + "  ·  " + (freeCam ? "cámara a mano" : view.label)
                + "  ·  P: práctica " + (match.practice ? "sí" : "no") + "  ·  I: IA " + (match.aiEnabled ? "sí" : "no")
                + "  ·  1 2 3: rival fácil, normal, difícil", 20, 108);
        font.draw(hud, match.scheme.help(), 20, 82);
        font.draw(hud, "Ratón: arrastrar = mover la cámara, botón derecho o del medio (o Shift) = girarla, rueda = zoom · Tab = seguir la pelota / otra cámara", 20, 30);
        font.getData().setScale(1.6f);
        hud.end();
    }

    /**
     * Arrastrar mueve la cámara sobre la cancha; con el botón derecho o el del medio, o con Shift,
     * la gira alrededor de lo que mira; la rueda acerca y aleja.
     */
    private final class Mouse extends InputAdapter {
        private int lastX, lastY, button;

        @Override
        public boolean touchDown(int x, int y, int pointer, int button) {
            lastX = x;
            lastY = y;
            this.button = button;
            return true;
        }

        @Override
        public boolean touchDragged(int x, int y, int pointer) {
            int dx = x - lastX, dy = y - lastY;
            lastX = x;
            lastY = y;
            boolean shift = Gdx.input.isKeyPressed(Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Keys.SHIFT_RIGHT);
            if (button == Buttons.RIGHT || button == Buttons.MIDDLE || shift) {
                yaw += dx * 0.3f;
                camPitch = MathUtils.clamp(camPitch + dy * 0.3f, 8f, 88f);
            } else {
                // Agarrar la cancha y arrastrarla: el punto que mira va al revés que el ratón.
                freeCam = true;
                float s = distance * 0.0016f, lx = MathUtils.cosDeg(yaw), lz = MathUtils.sinDeg(yaw);
                focus.x = MathUtils.clamp(focus.x + (dx * lz + dy * lx) * s, -Match.HALF_L - 10, Match.HALF_L + 10);
                focus.z = MathUtils.clamp(focus.z + (-dx * lx + dy * lz) * s, -Match.HALF_W - 10, Match.HALF_W + 10);
            }
            return true;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            distance = MathUtils.clamp(distance * (1 + amountY * 0.12f), 6f, 160f);
            return true;
        }
    }

    /** Una flecha chata sobre el pasto, apuntando hacia +x, para ver hacia dónde se saca. */
    private static Model buildArrow() {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        MeshPartBuilder b = mb.part("arrow", GL20.GL_TRIANGLES, ATTR, new Material(ColorAttribute.createDiffuse(Color.YELLOW)));
        BoxShapeBuilder.build(b, 1.6f, 0, 0, 2.4f, 0.02f, 0.14f);
        Matrix4 m = new Matrix4();
        for (int s = -1; s <= 1; s += 2) {
            b.setVertexTransform(m.setToTranslation(2.75f, 0, 0).rotate(Vector3.Y, s * 35f).translate(-0.35f, 0, 0));
            BoxShapeBuilder.build(b, 0, 0, 0, 0.75f, 0.02f, 0.14f);
        }
        b.setVertexTransform(null);
        return mb.end();
    }

    @Override
    public void dispose() {
        for (Model m : models) m.dispose();
        ballTexture.dispose();
        pitch.dispose();
        sun.dispose();
        batch.dispose();
        shadowBatch.dispose();
        hud.dispose();
        font.dispose();
        match.dispose();
        ruidos.dispose();
    }
}
