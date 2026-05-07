package org.oryxel.viabedrockutility.renderer;

import lombok.Getter;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.resources.ResourceLocation;
import org.oryxel.viabedrockutility.mixin.interfaces.ICuboid;
import org.oryxel.viabedrockutility.mixin.interfaces.IModelPart;

import java.util.concurrent.ThreadLocalRandom;

@Getter
public class AnimatedSkinOverlay {
    // Expression types matching Bedrock's Persona_AnimationExpression enum
    public static final int EXPRESSION_LINEAR = 0;
    public static final int EXPRESSION_BLINKING = 1;

    // Blinking timing constants (in ticks, 20 ticks = 1 second)
    private static final int BLINK_DURATION = 3;         // Eyes closed for ~150ms
    private static final int BLINK_INTERVAL_MIN = 60;    // Minimum 3 seconds between blinks
    private static final int BLINK_INTERVAL_MAX = 160;   // Maximum 8 seconds between blinks

    // Linear animation timing
    private static final int LINEAR_TICKS_PER_FRAME = 2;

    private final PlayerModel model;
    private final ResourceLocation textureId;
    private final int type;
    private final int totalFrames;
    private final int expression;
    private final float frameVSize;

    private int currentFrame = 0;
    private int tickCounter = 0;
    private int nextBlinkIn;  // ticks until next blink (for Blinking expression)

    public AnimatedSkinOverlay(PlayerModel model, ResourceLocation textureId, int type, int totalFrames, int expression, int textureHeight, int frameHeight) {
        this.model = model;
        this.textureId = textureId;
        this.type = type;
        this.totalFrames = totalFrames;
        this.expression = expression;
        this.frameVSize = (float) frameHeight / textureHeight;
        this.nextBlinkIn = randomBlinkInterval();
        updateVOffsetOnCuboids();
    }

    public void tick() {
        if (expression == EXPRESSION_BLINKING) {
            tickBlink();
        } else {
            tickLinear();
        }
    }

    private void tickLinear() {
        tickCounter++;
        if (tickCounter >= LINEAR_TICKS_PER_FRAME) {
            tickCounter = 0;
            currentFrame = (currentFrame + 1) % totalFrames;
            updateVOffsetOnCuboids();
        }
    }

    private void tickBlink() {
        if (currentFrame == 0) {
            // Eyes open: count down to next blink
            nextBlinkIn--;
            if (nextBlinkIn <= 0) {
                currentFrame = 1 % totalFrames;
                tickCounter = 0;
                updateVOffsetOnCuboids();
            }
        } else {
            // Eyes closed (or mid-blink for multi-frame): hold briefly then return
            tickCounter++;
            if (tickCounter >= BLINK_DURATION) {
                // For multi-frame blinks, advance to next frame or return to frame 0
                int nextFrame = currentFrame + 1;
                if (nextFrame >= totalFrames) {
                    // Blink complete, return to open eyes
                    currentFrame = 0;
                    tickCounter = 0;
                    nextBlinkIn = randomBlinkInterval();
                } else {
                    // Advance to next blink frame
                    currentFrame = nextFrame;
                    tickCounter = 0;
                }
                updateVOffsetOnCuboids();
            }
        }
    }

    private static int randomBlinkInterval() {
        return ThreadLocalRandom.current().nextInt(BLINK_INTERVAL_MIN, BLINK_INTERVAL_MAX + 1);
    }

    public float getCurrentVOffset() {
        return currentFrame * frameVSize;
    }

    public void updateVOffsetOnCuboids() {
        float vOffset = getCurrentVOffset();
        applyVOffsetRecursive(model.root(), vOffset);
    }

    private static void applyVOffsetRecursive(ModelPart part, float vOffset) {
        for (ModelPart.Cube cuboid : ((IModelPart) (Object) part).viaBedrockUtility$getCuboids()) {
            ((ICuboid) (Object) cuboid).viaBedrockUtility$setVOffset(vOffset);
        }
        for (ModelPart child : ((IModelPart) (Object) part).viaBedrockUtility$getChildren().values()) {
            applyVOffsetRecursive(child, vOffset);
        }
    }

    public void copyBoneTransformsFrom(PlayerModel source) {
        copyPartTransform(source.head, model.head);
        copyPartTransform(source.body, model.body);
        copyPartTransform(source.leftArm, model.leftArm);
        copyPartTransform(source.rightArm, model.rightArm);
        copyPartTransform(source.leftLeg, model.leftLeg);
        copyPartTransform(source.rightLeg, model.rightLeg);
    }

    private static void copyPartTransform(ModelPart source, ModelPart target) {
        if (source == null || target == null) return;
        target.xRot = source.xRot;
        target.yRot = source.yRot;
        target.zRot = source.zRot;
        target.visible = source.visible;
    }
}
