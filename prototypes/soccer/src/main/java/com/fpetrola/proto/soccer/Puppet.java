package com.fpetrola.proto.soccer;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.CapsuleShapeBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.SphereShapeBuilder;
import com.badlogic.gdx.math.Matrix4;

/**
 * El muñeco de un jugador, mirando hacia +x: tronco con cabeza, dos brazos con brazo y antebrazo, y
 * dos piernas con muslo y canilla. Cada parte cuelga de su articulación (el muslo de la cadera, la
 * canilla de la rodilla, el brazo del hombro, el antebrazo del codo) y va adonde diga su {@link Pose}.
 */
final class Puppet {
    private static final long ATTR = Usage.Position | Usage.Normal;
    private static final Color SKIN = new Color(0.95f, 0.78f, 0.62f, 1), BOOT = new Color(0.08f, 0.08f, 0.08f, 1);

    /** Los arqueros, de verde oscuro como en el Sensible. */
    static final Color KEEPER = new Color(0.1f, 0.4f, 0.15f, 1);

    static Model build(Color shirtColor, Color shortsColor) {
        Material shirt = material(shirtColor), shorts = material(shortsColor), skin = material(SKIN), boot = material(BOOT);
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        mb.node().id = "torso";
        BoxShapeBuilder.build(mb.part("pelvis", GL20.GL_TRIANGLES, ATTR, shorts), 0, 0.02f, 0, 0.26f, 0.22f, 0.4f);
        MeshPartBuilder chest = mb.part("chest", GL20.GL_TRIANGLES, ATTR, shirt);
        chest.setVertexTransform(new Matrix4().setToTranslation(0, 0.38f, 0));
        CapsuleShapeBuilder.build(chest, 0.22f, 0.72f, 10);
        MeshPartBuilder head = mb.part("head", GL20.GL_TRIANGLES, ATTR, skin);
        head.setVertexTransform(new Matrix4().setToTranslation(0, 0.92f, 0));
        SphereShapeBuilder.build(head, 0.3f, 0.34f, 0.3f, 10, 8);
        head.setVertexTransform(null);
        BoxShapeBuilder.build(head, 0.15f, 0.92f, 0, 0.08f, 0.06f, 0.06f);
        float t = Pose.THIGH, s = Pose.SHIN;
        for (String id : new String[]{"thighL", "thighR"}) {
            // de la cadera a la rodilla: el pantalón y la pierna
            mb.node().id = id;
            BoxShapeBuilder.build(mb.part(id + ".shorts", GL20.GL_TRIANGLES, ATTR, shorts), 0, -0.15f, 0, 0.19f, 0.3f, 0.18f);
            BoxShapeBuilder.build(mb.part(id + ".skin", GL20.GL_TRIANGLES, ATTR, skin), 0, -(0.3f + t) / 2, 0, 0.15f, t - 0.28f, 0.14f);
        }
        for (String id : new String[]{"shinL", "shinR"}) {
            // de la rodilla al pie: la canilla y el botín
            mb.node().id = id;
            BoxShapeBuilder.build(mb.part(id + ".skin", GL20.GL_TRIANGLES, ATTR, skin), 0, -(s - 0.06f) / 2, 0, 0.13f, s - 0.04f, 0.13f);
            BoxShapeBuilder.build(mb.part(id + ".boot", GL20.GL_TRIANGLES, ATTR, boot), 0.05f, -s + 0.04f, 0, 0.25f, 0.08f, 0.13f);
        }
        float up = Pose.UPPER, fore = Pose.FORE;
        for (String id : new String[]{"armL", "armR"}) {
            // del hombro al codo: la manga y el brazo
            mb.node().id = id;
            BoxShapeBuilder.build(mb.part(id + ".sleeve", GL20.GL_TRIANGLES, ATTR, shirt), 0, -0.11f, 0, 0.12f, 0.22f, 0.12f);
            BoxShapeBuilder.build(mb.part(id + ".skin", GL20.GL_TRIANGLES, ATTR, skin), 0, -(0.21f + up) / 2, 0, 0.095f, up - 0.2f, 0.095f);
        }
        for (String id : new String[]{"forearmL", "forearmR"}) {
            // del codo a la mano
            mb.node().id = id;
            BoxShapeBuilder.build(mb.part(id + ".skin", GL20.GL_TRIANGLES, ATTR, skin), 0, -fore / 2, 0, 0.085f, fore, 0.085f);
        }
        return mb.end();
    }

    private static Material material(Color color) {
        return new Material(ColorAttribute.createDiffuse(color));
    }

    final ModelInstance instance;
    private final Node torso;
    private final Node[] thigh, shin, arm, forearm;
    private final Pose pose = new Pose();

    Puppet(Model model) {
        instance = new ModelInstance(model);
        torso = node("torso");
        thigh = new Node[]{node("thighL"), node("thighR")};
        shin = new Node[]{node("shinL"), node("shinR")};
        arm = new Node[]{node("armL"), node("armR")};
        forearm = new Node[]{node("forearmL"), node("forearmR")};
    }

    private Node node(String id) {
        Node node = instance.getNode(id);
        node.isAnimated = true;  // la pose se escribe directo en localTransform
        return node;
    }

    /**
     * @param holdingBall el arquero con la pelota en las manos
     * @param throwIn     el que va a sacar un lateral, con la pelota sobre la cabeza
     */
    void pose(Player p, boolean holdingBall, boolean throwIn) {
        pose.compute(p, holdingBall, throwIn);
        instance.transform.set(pose.instance);
        torso.localTransform.set(pose.torso);
        for (int i = 0; i < 2; i++) {
            thigh[i].localTransform.set(pose.thigh[i]);
            shin[i].localTransform.set(pose.shin[i]);
            arm[i].localTransform.set(pose.arm[i]);
            forearm[i].localTransform.set(pose.forearm[i]);
        }
        instance.calculateTransforms();
    }
}
