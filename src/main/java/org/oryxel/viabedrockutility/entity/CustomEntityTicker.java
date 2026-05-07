package org.oryxel.viabedrockutility.entity;

import lombok.Getter;
import lombok.Setter;
import net.easecation.bedrockmotion.controller.AnimationController;
import net.easecation.bedrockmotion.controller.AnimationControllerInstance;
import net.easecation.bedrockmotion.model.AnimationEventListener;
import net.easecation.bedrockmotion.mocha.MoLangEngine;
import net.easecation.bedrockmotion.pack.PackManager;
import net.easecation.bedrockmotion.pack.definitions.EntityDefinitions;
import net.easecation.bedrockmotion.render.RenderControllerEvaluator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.resources.ResourceLocation;
import org.cube.converter.data.bedrock.BedrockEntityData;
import org.cube.converter.data.bedrock.controller.BedrockRenderController;
import org.cube.converter.model.impl.bedrock.BedrockGeometryModel;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.enums.bedrock.ActorFlags;
import org.oryxel.viabedrockutility.neoforge.ViaBedrockUtilityNeoForge;
import org.oryxel.viabedrockutility.mappings.BedrockMappings;
import org.oryxel.viabedrockutility.material.data.Material;
import org.oryxel.viabedrockutility.payload.handler.CustomEntityPayloadHandler;
import org.oryxel.viabedrockutility.renderer.CustomEntityRenderer;
import org.oryxel.viabedrockutility.renderer.model.CustomEntityModel;
import org.oryxel.viabedrockutility.util.GeometryUtil;
import team.unnamed.mocha.runtime.Scope;
import team.unnamed.mocha.runtime.value.MutableObjectBinding;
import team.unnamed.mocha.runtime.value.Value;

import team.unnamed.mocha.parser.ast.Expression;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.oryxel.viabedrockutility.payload.handler.CustomEntityPayloadHandler.*;

public class CustomEntityTicker implements AnimationEventListener {
    @Getter
    private final Scope entityScope = BASE_SCOPE.copy();

    @Setter
    private Set<ActorFlags> entityFlags = EnumSet.noneOf(ActorFlags.class);

    private final CustomEntityPayloadHandler payloadHandler = ViaBedrockUtility.getInstance().getPayloadHandler();
    private final PackManager packManager = ViaBedrockUtility.getInstance().getPackManager();
    private final org.oryxel.viabedrockutility.pack.definitions.MaterialDefinitions vbuMaterialDefinitions =
            new org.oryxel.viabedrockutility.pack.definitions.MaterialDefinitions(this.packManager);

    private final EntityDefinitions.EntityDefinition entityDefinition;
    private final Map<String, String> inverseGeometryMap = new HashMap<>();
    private final Map<String, String> inverseTextureMap = new HashMap<>();
    private final Map<String, String> inverseMaterialMap = new HashMap<>();

    // particle_effects alias map from entity definition (short_name to full identifier)
    private final Map<String, String> particleEffects;

    private final Set<String> availableModels = new HashSet<>();
    private final List<RenderControllerEvaluator.EvaluatedModel> models = new ArrayList<>();

    @Getter
    private final CustomEntityRenderer<?> renderer;

    @Setter
    private Integer variant, markVariant, skinId;

    @Setter @Getter
    private Float scale;

    // Updated by renderer each frame with entity world position
    @Setter @Getter
    private org.joml.Vector3f entityPosition = null;

    @Getter
    private Scope lastExecutionScope;

    // animation identifier to condition expression (for per-frame blend weight evaluation)
    @Getter
    private final Map<String, String> animationIdToCondition = new HashMap<>();
    @Getter
    private final Map<String, List<Expression>> parsedAnimationConditions = new HashMap<>();

    // Pre-parsed MoLang expressions for initialize and pre_animation scripts
    private final List<List<Expression>> parsedPreAnimationExpressions = new ArrayList<>();

    @Getter
    private final List<AnimationControllerInstance> controllerInstances = new ArrayList<>();

    private boolean hasPlayInitAnimation;

    public CustomEntityTicker(final EntityDefinitions.EntityDefinition entityDefinition) {
        final Minecraft client = Minecraft.getInstance();
        final EntityRendererProvider.Context context = new EntityRendererProvider.Context(client.getEntityRenderDispatcher(),
                client.getItemModelResolver(), client.getMapRenderer(), client.getBlockRenderer(),
                client.getResourceManager(), client.getEntityModels(), new EquipmentAssetManager(), client.font);
        this.renderer = new CustomEntityRenderer<>(this, new CopyOnWriteArrayList<>(), context);

        this.entityDefinition = entityDefinition;

    // particle_effects alias map from entity definition (short_name to full identifier)
        final Map<String, String> pe = entityDefinition.entityData().getParticleEffects();
        this.particleEffects = pe != null ? pe : Map.of();

        final MutableObjectBinding variableBinding = new MutableObjectBinding();
        // Bedrock engine provides gliding_speed_value based on entity movement attribute.
        // Default to typical player walking speed in blocks/second (~4.317),
        // matching the unit used by query.modified_move_speed (blocks/second).
        variableBinding.set("gliding_speed_value", Value.of(4.317));
        this.entityScope.set("variable", variableBinding);
        this.entityScope.set("v", variableBinding);

        // Pre-parse and execute initialize scripts
        try {
            for (String initExpression : this.entityDefinition.entityData().getScripts().initialize()) {
                final List<Expression> parsed = MoLangEngine.parse(initExpression);
                MoLangEngine.eval(this.entityScope, parsed);
            }
        } catch (Throwable e) {
            ViaBedrockUtilityNeoForge.LOGGER.warn("Failed to initialize custom entity variables", e);
        }

        // Pre-parse pre_animation scripts (evaluated every frame in renderer)
        for (String expr : this.entityDefinition.entityData().getScripts().pre_animation()) {
            try {
                this.parsedPreAnimationExpressions.add(MoLangEngine.parse(expr));
            } catch (IOException e) {
                ViaBedrockUtilityNeoForge.LOGGER.warn("Failed to parse pre_animation expression: {}", expr, e);
            }
        }

        final MutableObjectBinding geometryBinding = new MutableObjectBinding();
        for (Map.Entry<String, String> entry : entityDefinition.entityData().getGeometries().entrySet()) {
            geometryBinding.set(entry.getKey(), Value.of(entry.getValue()));
            this.inverseGeometryMap.putIfAbsent(entry.getValue(), entry.getKey());
        }
        geometryBinding.block();
        this.entityScope.set("geometry", geometryBinding);

        final MutableObjectBinding textureBinding = new MutableObjectBinding();
        for (Map.Entry<String, String> entry : entityDefinition.entityData().getTextures().entrySet()) {
            textureBinding.set(entry.getKey(), Value.of(entry.getValue()));
            this.inverseTextureMap.putIfAbsent(entry.getValue(), entry.getKey());
        }
        textureBinding.block();
        this.entityScope.set("texture", textureBinding);

        final MutableObjectBinding materialBinding = new MutableObjectBinding();
        for (Map.Entry<String, String> entry : entityDefinition.entityData().getMaterials().entrySet()) {
            materialBinding.set(entry.getKey(), Value.of(entry.getValue()));
            this.inverseMaterialMap.putIfAbsent(entry.getValue(), entry.getKey());
        }
        materialBinding.block();
        this.entityScope.set("material", materialBinding);

        this.entityScope.readOnly(true);
    }

    // --- AnimationEventListener implementation ---

    @Override
    public void onTimelineEvent(List<String> expressions) {
        try {
            for (String expression : expressions) {
                MoLangEngine.eval(this.entityScope, expression);
            }
        } catch (Throwable e) {
            ViaBedrockUtilityNeoForge.LOGGER.warn("Failed to initialize custom entity pre-animation variables", e);
        }

        this.update();
    }

    @Override
    public Scope getEntityScope() {
        return this.entityScope;
    }

    @Override
    public void onParticleEvent(String effectShortName, String locator) {
        final String identifier = this.particleEffects.get(effectShortName);
        if (identifier == null) {
            ViaBedrockUtilityNeoForge.LOGGER.debug("[Particle] Unknown particle effect alias: {}", effectShortName);
            return;
        }
        // Skip if position not yet initialized (renderer hasn't run)
        if (entityPosition == null) return;
        net.easecation.beparticle.ParticleManager.INSTANCE.spawnEmitter(
                identifier,
                new org.joml.Vector3f(entityPosition),
                null
        );
    }

    // --- End AnimationEventListener ---

    /**
     * Executes pre_animation scripts once per frame with the given scope containing query bindings.
     * Variable writes persist via the shared MutableObjectBinding on entityScope.
     */
    public void runPreAnimationTask(final Scope frameScope) {
        try {
            for (List<Expression> parsed : this.parsedPreAnimationExpressions) {
                MoLangEngine.eval(frameScope, parsed);
            }
        } catch (Throwable e) {
            ViaBedrockUtilityNeoForge.LOGGER.warn("Failed to evaluate pre-animation scripts", e);
        }
    }

    /**
     * Populates entity-level MoLang query bindings (variant, mark_variant, skin_id, entity flags).
     * Shared between update() (render controller evaluation) and renderer's buildFrameScope().
     */
    public void populateEntityQueries(final MutableObjectBinding queryBinding) {
        if (this.variant != null) {
            queryBinding.set("variant", Value.of(this.variant));
        }
        if (this.markVariant != null) {
            queryBinding.set("mark_variant", Value.of(this.markVariant));
        }
        if (this.skinId != null) {
            queryBinding.set("skin_id", Value.of(this.skinId));
        }

        final Set<ActorFlags> entityFlags = this.entityFlags();
        for (Map.Entry<ActorFlags, String> entry : BedrockMappings.getBedrockEntityFlagMoLangQueries().entrySet()) {
            if (entityFlags.contains(entry.getKey())) {
                queryBinding.set(entry.getValue(), Value.of(true));
            }
        }
        if (entityFlags.contains(ActorFlags.ONFIRE)) {
            queryBinding.set("is_onfire", Value.of(true));
        }
    }

    public void update() {
        final Scope executionScope = this.entityScope.copy();
        this.lastExecutionScope = executionScope;
        final MutableObjectBinding queryBinding = new MutableObjectBinding();
        this.populateEntityQueries(queryBinding);

        queryBinding.block();
        executionScope.set("query", queryBinding);
        executionScope.set("q", queryBinding);

        if (!evaluateRenderControllerChange(executionScope)) {
            ViaBedrockUtilityNeoForge.LOGGER.debug("[Entity] update(): no render controller change, models={}", this.renderer.getModels().size());
            this.renderer.getAnimators().values().forEach(animator -> animator.setBaseScope(executionScope.copy()));
            return;
        }

        ViaBedrockUtilityNeoForge.LOGGER.debug("[Entity] update(): render controller changed, evaluatedModels={}", this.models.size());
        final Set<String> old = new HashSet<>(this.availableModels);
        this.availableModels.clear();
        for (RenderControllerEvaluator.EvaluatedModel model : this.models) {
            final ResourceLocation texture = ResourceLocation.parse(model.textureValue().toLowerCase(Locale.ROOT));

            BedrockGeometryModel geometry = this.packManager.getModelDefinitions().getEntityModels().get(model.geometryValue());
            if (geometry == null) {
                ViaBedrockUtilityNeoForge.LOGGER.warn("[Entity] update(): geometry '{}' not found in packManager", model.geometryValue());
                continue;
            }

            if (old.contains(model.key())) {
                this.availableModels.add(model.key());
                continue;
            }

            final CustomEntityModel<CustomEntityRenderer.CustomEntityRenderState> cModel = (CustomEntityModel<CustomEntityRenderer.CustomEntityRenderState>) GeometryUtil.buildModel(geometry, false, false, model.geometryValue());

            var visibleBounds = this.packManager.getModelDefinitions().getVisibleBoundsMap()
                    .getOrDefault(model.geometryValue(), net.easecation.bedrockmotion.pack.definitions.VisibleBounds.DEFAULT);
            // Pre-parse part visibility expressions to avoid per-frame re-parsing
            Map<String, Object> parsedPV = null;
            if (model.controller() != null && !model.controller().partVisibility().isEmpty()) {
                parsedPV = new HashMap<>();
                for (Map.Entry<String, String> pvEntry : model.controller().partVisibility().entrySet()) {
                    if ("true".equals(pvEntry.getValue())) {
                        parsedPV.put(pvEntry.getKey(), Boolean.TRUE);
                    } else if ("false".equals(pvEntry.getValue())) {
                        parsedPV.put(pvEntry.getKey(), Boolean.FALSE);
                    } else {
                        try {
                            parsedPV.put(pvEntry.getKey(), MoLangEngine.parse(pvEntry.getValue()));
                        } catch (IOException e) {
                            ViaBedrockUtilityNeoForge.LOGGER.warn("[Entity] Failed to parse part visibility expression: {}", pvEntry.getValue(), e);
                            parsedPV.put(pvEntry.getKey(), Boolean.TRUE);
                        }
                    }
                }
            }
            this.renderer.getModels().add(new CustomEntityRenderer.Model(model.key(), model.geometryValue(), cModel, texture, evalMaterial(executionScope, model.controller()), model.controller(), visibleBounds, parsedPV));
            this.availableModels.add(model.key());
        }
        this.renderer.getModels().removeIf(model -> !this.availableModels.contains(model.key()));
        ViaBedrockUtilityNeoForge.LOGGER.debug("[Entity] update(): final renderer models={}", this.renderer.getModels().size());

        if (!this.hasPlayInitAnimation) {
            this.entityDefinition.entityData().getScripts().animates().forEach(animate -> {
                // Register ALL animations unconditionally; condition is evaluated per-frame as blend weight
                try {
                    final String animId = this.entityDefinition.entityData().getAnimations().get(animate.name());

                    // Check if this is an animation controller reference
                    if (animId != null && animId.startsWith("controller.animation.")) {
                        final AnimationController controller = this.packManager.getAnimationControllerDefinitions()
                                .getControllers().get(animId);
                        if (controller != null) {
                            final AnimationControllerInstance instance = new AnimationControllerInstance(
                                    controller,
                                    this.entityDefinition.entityData().getAnimations(),
                                    this.packManager.getAnimationDefinitions(),
                                    this);
                            this.controllerInstances.add(instance);
                            ViaBedrockUtilityNeoForge.LOGGER.debug("[Animation] Registered controller '{}' ({})", animate.name(), animId);
                        } else {
                            ViaBedrockUtilityNeoForge.LOGGER.debug("[Animation] Controller '{}' ({}) not found in definitions", animate.name(), animId);
                        }
                        return; // forEach return = continue
                    }

                    final var animData = this.packManager.getAnimationDefinitions().getAnimations().get(animId);
                    if (animData != null) {
                        this.renderer.play(animData);
                        final String animIdentifier = animData.animation().getIdentifier();
                        this.animationIdToCondition.put(animIdentifier, animate.expression());
                        // Pre-parse condition expression for per-frame evaluation
                        if (animate.expression() != null && !animate.expression().isBlank()) {
                            try {
                                this.parsedAnimationConditions.put(animIdentifier, MoLangEngine.parse(animate.expression()));
                            } catch (IOException ignored) {}
                        }
                        ViaBedrockUtilityNeoForge.LOGGER.debug("[Animation] Registered '{}' ({}), condition='{}'", animate.name(), animId, animate.expression());
                    }
                } catch (Throwable e) {
                    ViaBedrockUtilityNeoForge.LOGGER.warn("Failed to register animation: {}", animate.name(), e);
                }
            });
            this.hasPlayInitAnimation = true;
        }

        this.renderer.getAnimators().values().forEach(animator -> animator.setBaseScope(executionScope.copy()));
    }

    private boolean evaluateRenderControllerChange(final Scope executionScope) {
        final List<RenderControllerEvaluator.EvaluatedModel> newModels = RenderControllerEvaluator.evaluate(
                this.entityDefinition.entityData(),
                executionScope,
                this.packManager.getRenderControllerDefinitions(),
                this.inverseGeometryMap,
                this.inverseTextureMap
        );

        if (!newModels.isEmpty() && !this.models.equals(newModels)) {
            this.availableModels.clear();
            this.models.clear();
            this.models.addAll(newModels);
            return true;
        } else {
            return false;
        }
    }

    private Material evalMaterial(final Scope executionScope, BedrockRenderController controller) {
        if (controller == null || controller.materialsMap().isEmpty()) {
            return DEFAULT_MATERIAL;
        }

        // I know it is more complex than just this but this is the case most of the time, and also I don't have the energy.
        if (!controller.materialsMap().containsKey("*")) {
            return DEFAULT_MATERIAL;
        }

        final Scope renderControllerMaterialScope = executionScope.copy();
        try {
            renderControllerMaterialScope.set("array", this.getArrayBinding(executionScope, controller.materials()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            final String materialMapName = MoLangEngine.eval(renderControllerMaterialScope, controller.materialsMap().get("*")).getAsString();
            final String materialName = this.entityDefinition.entityData().getMaterials().get(this.inverseMaterialMap.get(materialMapName));

            if (materialName == null) {
                return DEFAULT_MATERIAL;
            }

            return this.vbuMaterialDefinitions.getMaterial(materialName);
        } catch (IOException ignored) {}

        return DEFAULT_MATERIAL;
    }

    private MutableObjectBinding getArrayBinding(final Scope executionScope, final List<BedrockRenderController.Array> arrays) throws IOException {
        final MutableObjectBinding arrayBinding = new MutableObjectBinding();
        for (BedrockRenderController.Array array : arrays) {
            if (array.name().toLowerCase(Locale.ROOT).startsWith("array.")) {
                final String[] resolvedExpressions = new String[array.values().size()];
                for (int i = 0; i < array.values().size(); i++) {
                    resolvedExpressions[i] = MoLangEngine.eval(executionScope, array.values().get(i)).getAsString();
                }
                arrayBinding.set(array.name().substring(6), Value.of(resolvedExpressions));
            }
        }
        arrayBinding.block();
        return arrayBinding;
    }

    public Set<ActorFlags> entityFlags() {
        return this.entityFlags;
    }
}
