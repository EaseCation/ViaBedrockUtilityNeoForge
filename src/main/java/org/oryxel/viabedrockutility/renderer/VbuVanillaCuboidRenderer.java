package org.oryxel.viabedrockutility.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Sodium-independent reference emitter used whenever bulk vertex writing is unavailable. */
public final class VbuVanillaCuboidRenderer {
    private VbuVanillaCuboidRenderer() {
    }

    public static void render(ModelPart.Polygon[] polygons,
                              PoseStack.Pose pose,
                              VertexConsumer consumer,
                              int light,
                              int overlay,
                              int color,
                              float vOffset,
                              boolean flatNormal,
                              Vector3f scratch) {
        final Matrix4f positionMatrix = pose.pose();
        for (ModelPart.Polygon polygon : polygons) {
            if (polygon == null) {
                continue;
            }

            final float normalX;
            final float normalY;
            final float normalZ;
            if (flatNormal) {
                normalX = 0.0F;
                normalY = 1.0F;
                normalZ = 0.0F;
            } else {
                final Vector3f transformedNormal = pose.transformNormal(polygon.normal(), scratch);
                normalX = transformedNormal.x();
                normalY = transformedNormal.y();
                normalZ = transformedNormal.z();
            }

            for (ModelPart.Vertex vertex : polygon.vertices()) {
                final Vector3f transformedPosition = positionMatrix.transformPosition(
                        vertex.pos().x() / 16.0F,
                        vertex.pos().y() / 16.0F,
                        vertex.pos().z() / 16.0F,
                        scratch);
                consumer.addVertex(
                        transformedPosition.x(), transformedPosition.y(), transformedPosition.z(),
                        color, vertex.u(), vertex.v() + vOffset, overlay, light,
                        normalX, normalY, normalZ);
            }
        }
    }
}
