package org.oryxel.viabedrockutility.particle;

import net.easecation.beparticle.anchor.ParticleSpaceTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.attachable.AttachableHostContext;
import org.oryxel.viabedrockutility.attachable.BedrockTransformConvention;
import org.oryxel.viabedrockutility.renderer.BedrockPlayerModelMetadata;
import org.oryxel.viabedrockutility.renderer.BedrockPlayerPoseDemand;
import org.oryxel.viabedrockutility.renderer.BedrockPlayerWorldPose;
import org.oryxel.viabedrockutility.renderer.CustomPlayerRenderer;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Default VBU host resolver for entities and VBU custom-player model bones/locators. */
final class MinecraftBedrockPoseProvider implements BedrockPoseProvider {
    private final Map<BedrockParticlePlacement.BoundEffect, BedrockPoseSnapshot> samples =
            new ConcurrentHashMap<>();
    private final BedrockPlayerPoseDemand poseDemand;

    MinecraftBedrockPoseProvider() {
        this(ViaBedrockUtility.getInstance().getPlayerPoseDemand());
    }

    MinecraftBedrockPoseProvider(BedrockPlayerPoseDemand poseDemand) {
        this.poseDemand = java.util.Objects.requireNonNull(poseDemand, "poseDemand");
    }

    @Override
    public BedrockPoseSnapshot resolve(BedrockParticlePlacement.BoundEffect placement) {
        final Minecraft minecraft = Minecraft.getInstance();
        final var level = minecraft.level;
        if (level == null) return BedrockPoseSnapshot.unresolved(Long.MIN_VALUE);
        final long tick = level.getGameTime();
        Entity entity = level.getPlayerByUUID(placement.ownerUuid());
        if (entity == null) {
            for (Entity candidate : level.entitiesForRendering()) {
                if (!candidate.isRemoved() && candidate.getUUID().equals(placement.ownerUuid())) {
                    entity = candidate;
                    break;
                }
            }
        }
        if (entity == null) return BedrockPoseSnapshot.unresolved(tick);

        final Entity resolvedEntity = entity;
        return samples.compute(placement, (ignored, previous) -> {
            if (previous != null && previous.valid() && previous.tick() == tick) return previous;
            final BedrockPoseSnapshot raw = resolveUncached(resolvedEntity, placement, tick);
            if (!raw.valid()) return raw;
            final Vector3f velocity;
            if (previous != null && previous.valid() && tick > previous.tick()) {
                velocity = raw.position().sub(previous.position())
                        .mul(20.0F / (float) (tick - previous.tick()));
            } else {
                velocity = entityVelocity(resolvedEntity);
            }
            return new BedrockPoseSnapshot(raw.position(), raw.rotation(), raw.simulationRotation(),
                    raw.continuationRotation(), raw.scale(), velocity, tick, true);
        });
    }

    @Override
    public void clear() {
        samples.clear();
    }

    private BedrockPoseSnapshot resolveUncached(Entity entity,
                                                 BedrockParticlePlacement.BoundEffect placement,
                                                 long tick) {
        if (placement.targetKind() == BedrockParticlePlacement.TargetKind.ENTITY) {
            return entityAnchor(entity, placement, false, tick);
        }

        final EntityRenderer<?, ?> cached = ViaBedrockUtility.getInstance().getPayloadHandler() == null
                ? null : ViaBedrockUtility.getInstance().getPayloadHandler()
                .cachedPlayerRenderer(placement.ownerUuid());
        if (cached instanceof CustomPlayerRenderer customRenderer) {
            final BedrockPlayerModelMetadata metadata = BedrockPlayerModelMetadata.get(customRenderer.getPlayerModel());
            if (metadata == null) return BedrockPoseSnapshot.unresolved(tick);
            if (placement.viewContext() != BedrockParticlePlacement.ViewContext.FIRST_PERSON) {
                poseDemand.request(placement.ownerUuid(), tick);
            }
            final BedrockPoseSnapshot modelPose = placement.targetKind() == BedrockParticlePlacement.TargetKind.BONE
                    ? boneAnchor(entity, customRenderer, metadata, placement, tick)
                    : locatorAnchor(entity, customRenderer, metadata, placement, tick);
            // A VBU host must never silently fall back to a different skeleton contract.
            return modelPose;
        }

        if (placement.targetKind() == BedrockParticlePlacement.TargetKind.BONE
                && "head".equals(placement.targetName().toLowerCase(Locale.ROOT))) {
            return entityAnchor(entity, placement, true, tick);
        }
        return BedrockPoseSnapshot.unresolved(tick);
    }

    private static BedrockPoseSnapshot boneAnchor(Entity entity, CustomPlayerRenderer renderer,
                                                    BedrockPlayerModelMetadata metadata,
                                                    BedrockParticlePlacement.BoundEffect placement,
                                                    long tick) {
        final BedrockPlayerModelMetadata.Bone bone = metadata.firstBone(placement.targetName());
        if (bone == null) return BedrockPoseSnapshot.unresolved(tick);
        if (placement.viewContext() != BedrockParticlePlacement.ViewContext.FIRST_PERSON) {
            final BedrockPlayerWorldPose pose = renderer.thirdPersonPose(placement.ownerUuid(), tick);
            final BedrockPlayerWorldPose.Anchor anchor = pose == null ? null : pose.bone(placement.targetName());
            return anchor == null ? BedrockPoseSnapshot.unresolved(tick)
                    : worldMatrixSnapshot(entity, anchor.matrix(), placement, false,
                    anchor.bindingOffsetFrame(), tick);
        }
        final AttachableHostContext host = new AttachableHostContext(metadata);
        final Matrix4f anchor = host.firstPersonAttachmentMatrix(bone);
        return modelMatrixSnapshot(entity, anchor, placement, false, tick);
    }

    private static BedrockPoseSnapshot locatorAnchor(Entity entity, CustomPlayerRenderer renderer,
                                                       BedrockPlayerModelMetadata metadata,
                                                       BedrockParticlePlacement.BoundEffect placement,
                                                       long tick) {
        final BedrockPlayerModelMetadata.LocatorMatch match = metadata.findLocator(placement.targetName());
        if (match == null) return BedrockPoseSnapshot.unresolved(tick);
        if (placement.viewContext() != BedrockParticlePlacement.ViewContext.FIRST_PERSON) {
            final BedrockPlayerWorldPose pose = renderer.thirdPersonPose(placement.ownerUuid(), tick);
            final BedrockPlayerWorldPose.Anchor anchor = pose == null ? null : pose.locator(placement.targetName());
            return anchor == null ? BedrockPoseSnapshot.unresolved(tick)
                    : worldMatrixSnapshot(entity, anchor.matrix(), placement,
                    anchor.ignoreInheritedScale(), anchor.bindingOffsetFrame(), tick);
        }
        final AttachableHostContext host = new AttachableHostContext(metadata);
        Matrix4f anchor = host.firstPersonAttachmentMatrix(match.bone(), match.locator().point());
        if (match.locator().ignoreInheritedScale()) {
            final Vector3f translation = anchor.getTranslation(new Vector3f());
            final Quaternionf rotation = anchor.getUnnormalizedRotation(new Quaternionf()).normalize();
            anchor = new Matrix4f().translationRotateScale(translation, rotation, new Vector3f(1.0F));
        }
        return modelMatrixSnapshot(entity, anchor, placement, match.locator().ignoreInheritedScale(), tick);
    }

    private static BedrockPoseSnapshot modelMatrixSnapshot(Entity entity, Matrix4f modelAnchor,
                                                             BedrockParticlePlacement.BoundEffect placement,
                                                             boolean ignoreScale, long tick) {
        final Matrix4f world = worldBase(entity, placement)
                .mul(modelAnchor);
        return worldMatrixSnapshot(entity, world, placement, ignoreScale,
                BedrockTransformConvention.BindingOffsetFrame.OWNER_ATTACHMENT, tick);
    }

    private static BedrockPoseSnapshot worldMatrixSnapshot(Entity entity, Matrix4f worldAnchor,
                                                             BedrockParticlePlacement.BoundEffect placement,
                                                             boolean ignoreScale,
                                                             BedrockTransformConvention.BindingOffsetFrame offsetFrame,
                                                             long tick) {
        final Matrix4f world = new Matrix4f(worldAnchor)
                .translate(BedrockParticleFrames.localOffset(placement, offsetFrame))
                .rotate(placement.localRotation());
        final Vector3f scale = ignoreScale ? new Vector3f(1.0F) : world.getScale(new Vector3f());
        final Quaternionf targetRotation = world.getUnnormalizedRotation(new Quaternionf()).normalize();
        final Quaternionf ownerRootRotation = BedrockParticleFrames.ownerRootRotation(
                bodyYaw(entity), placement.localRotation());
        final Quaternionf ownerViewRotation = ownerViewRotation(entity, placement.localRotation());
        final Quaternionf simulationRotation = BedrockParticleFrames.simulationRotation(
                placement.orientationPolicy(), targetRotation, ownerRootRotation,
                ownerViewRotation, placement.localRotation());
        return new BedrockPoseSnapshot(
                world.getTranslation(new Vector3f()),
                targetRotation,
                simulationRotation,
                simulationRotation,
                scale,
                entityVelocity(entity), tick, true);
    }

    private static Matrix4f worldBase(Entity entity, BedrockParticlePlacement.BoundEffect placement) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (placement.viewContext() == BedrockParticlePlacement.ViewContext.FIRST_PERSON
                && minecraft.player == entity && minecraft.options.getCameraType().isFirstPerson()) {
            final var camera = minecraft.gameRenderer.getMainCamera();
            final var position = camera.getPosition();
            return new Matrix4f()
                    .translation((float) position.x, (float) position.y, (float) position.z)
                    .rotate(camera.rotation());
        }
        return new Matrix4f()
                .translation((float) entity.getX(), (float) entity.getY(), (float) entity.getZ())
                .rotateY((float) Math.toRadians(180.0F - bodyYaw(entity)));
    }

    private static BedrockPoseSnapshot entityAnchor(Entity entity,
                                                      BedrockParticlePlacement.BoundEffect placement,
                                                      boolean head, long tick) {
        final Minecraft minecraft = Minecraft.getInstance();
        final boolean cameraAnchor = head
                && placement.viewContext() == BedrockParticlePlacement.ViewContext.FIRST_PERSON
                && minecraft.player == entity && minecraft.options.getCameraType().isFirstPerson();
        final var camera = minecraft.gameRenderer.getMainCamera();
        final var base = cameraAnchor ? camera.getPosition() : head ? entity.getEyePosition() : entity.position();
        final Quaternionf ownerFrameRotation = BedrockParticleFrames.ownerRootRotation(
                bodyYaw(entity), new Quaternionf());
        final Quaternionf targetFrameRotation = !head ? new Quaternionf(ownerFrameRotation) : cameraAnchor
                ? new Quaternionf(camera.rotation())
                : ParticleSpaceTransform.javaEntityViewRotation(entity.getYRot(), entity.getXRot());
        final Quaternionf localRotation = placement.localRotation();
        final Quaternionf ownerRootRotation = new Quaternionf(ownerFrameRotation).mul(localRotation);
        final Quaternionf ownerViewRotation = ownerViewRotation(entity, localRotation);
        // The VBU-head fallback preserves BONE binding semantics. ENTITY offsets (including the
        // SpawnParticleEffectPacket V2 path) remain in their caller-provided entity-root frame.
        final BedrockParticleFrames.PlacedFrame targetFrame = BedrockParticleFrames.placeLocalFrame(
                new Vector3f((float) base.x, (float) base.y, (float) base.z), targetFrameRotation,
                BedrockParticleFrames.localOffset(placement,
                        BedrockTransformConvention.BindingOffsetFrame.OWNER_ATTACHMENT), localRotation);
        final Quaternionf targetRotation = targetFrame.rotation();
        final Quaternionf simulationRotation = BedrockParticleFrames.simulationRotation(
                placement.orientationPolicy(), targetRotation, ownerRootRotation,
                ownerViewRotation, localRotation);
        return new BedrockPoseSnapshot(
                targetFrame.position(),
                targetRotation, simulationRotation, simulationRotation, new Vector3f(1.0F),
                entityVelocity(entity), tick, true);
    }

    private static float bodyYaw(Entity entity) {
        return entity instanceof LivingEntity living ? living.yBodyRot : entity.getYRot();
    }

    private static Quaternionf ownerViewRotation(Entity entity, Quaternionf localRotation) {
        return ParticleSpaceTransform.javaEntityViewRotation(entity.getYRot(), entity.getXRot())
                .mul(localRotation)
                .normalize();
    }

    private static Vector3f entityVelocity(Entity entity) {
        final var velocity = entity.getDeltaMovement();
        return new Vector3f((float) velocity.x, (float) velocity.y, (float) velocity.z).mul(20.0F);
    }
}
