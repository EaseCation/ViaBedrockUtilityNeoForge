package org.oryxel.viabedrockutility.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return this::buildConfigScreen;
    }

    private Screen buildConfigScreen(Screen parent) {
        final LodConfig config = LodConfig.getInstance();

        // Capture original tier values to detect user modifications
        final int origT1Dist = (int) config.getTier1().distance;
        final int origT1Int = config.getTier1().frameInterval;
        final int origT2Dist = (int) config.getTier2().distance;
        final int origT2Int = config.getTier2().frameInterval;
        final int origT3Dist = (int) config.getTier3().distance;
        final int origT3Int = config.getTier3().frameInterval;

        // Track user selections via arrays (mutable from lambdas)
        final LodConfig.Preset[] selectedPreset = { config.getPreset() };
        final boolean[] tierModified = { false };

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("config.viabedrockutility.title"));

        ConfigCategory category = builder.getOrCreateCategory(
                Component.translatable("config.viabedrockutility.category.entity_animation_lod"));

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // Preset selector
        category.addEntry(entryBuilder.startEnumSelector(
                        Component.translatable("config.viabedrockutility.option.preset"),
                        LodConfig.Preset.class,
                        config.getPreset())
                .setDefaultValue(LodConfig.Preset.BALANCED)
                .setEnumNameProvider(preset -> switch ((LodConfig.Preset) preset) {
                    case HIGH_QUALITY -> Component.translatable("config.viabedrockutility.preset.high_quality");
                    case BALANCED -> Component.translatable("config.viabedrockutility.preset.balanced");
                    case PERFORMANCE -> Component.translatable("config.viabedrockutility.preset.performance");
                    case CUSTOM -> Component.translatable("config.viabedrockutility.preset.custom");
                })
                .setSaveConsumer(preset -> selectedPreset[0] = preset)
                .build());

        // Tier 1
        category.addEntry(entryBuilder.startIntSlider(
                        Component.translatable("config.viabedrockutility.option.tier1_distance"),
                        origT1Dist, 0, 64)
                .setDefaultValue(24)
                .setTextGetter(val -> val == 0
                        ? Component.translatable("config.viabedrockutility.value.disabled")
                        : Component.translatable("config.viabedrockutility.value.blocks", val))
                .setSaveConsumer(val -> {
                    config.getTier1().distance = val;
                    if (val != origT1Dist) tierModified[0] = true;
                })
                .build());

        category.addEntry(entryBuilder.startIntSlider(
                        Component.translatable("config.viabedrockutility.option.tier1_interval"),
                        origT1Int, 1, 16)
                .setDefaultValue(2)
                .setTextGetter(val -> Component.translatable("config.viabedrockutility.value.every_frames", val))
                .setSaveConsumer(val -> {
                    config.getTier1().frameInterval = val;
                    if (val != origT1Int) tierModified[0] = true;
                })
                .build());

        // Tier 2
        category.addEntry(entryBuilder.startIntSlider(
                        Component.translatable("config.viabedrockutility.option.tier2_distance"),
                        origT2Dist, 0, 96)
                .setDefaultValue(48)
                .setTextGetter(val -> val == 0
                        ? Component.translatable("config.viabedrockutility.value.disabled")
                        : Component.translatable("config.viabedrockutility.value.blocks", val))
                .setSaveConsumer(val -> {
                    config.getTier2().distance = val;
                    if (val != origT2Dist) tierModified[0] = true;
                })
                .build());

        category.addEntry(entryBuilder.startIntSlider(
                        Component.translatable("config.viabedrockutility.option.tier2_interval"),
                        origT2Int, 1, 16)
                .setDefaultValue(4)
                .setTextGetter(val -> Component.translatable("config.viabedrockutility.value.every_frames", val))
                .setSaveConsumer(val -> {
                    config.getTier2().frameInterval = val;
                    if (val != origT2Int) tierModified[0] = true;
                })
                .build());

        // Tier 3
        category.addEntry(entryBuilder.startIntSlider(
                        Component.translatable("config.viabedrockutility.option.tier3_distance"),
                        origT3Dist, 0, 128)
                .setDefaultValue(0)
                .setTextGetter(val -> val == 0
                        ? Component.translatable("config.viabedrockutility.value.disabled")
                        : Component.translatable("config.viabedrockutility.value.blocks", val))
                .setSaveConsumer(val -> {
                    config.getTier3().distance = val;
                    if (val != origT3Dist) tierModified[0] = true;
                })
                .build());

        category.addEntry(entryBuilder.startIntSlider(
                        Component.translatable("config.viabedrockutility.option.tier3_interval"),
                        origT3Int, 1, 16)
                .setDefaultValue(1)
                .setTextGetter(val -> Component.translatable("config.viabedrockutility.value.every_frames", val))
                .setSaveConsumer(val -> {
                    config.getTier3().frameInterval = val;
                    if (val != origT3Int) tierModified[0] = true;
                })
                .build());

        builder.setSavingRunnable(() -> {
            if (tierModified[0]) {
                config.setPreset(LodConfig.Preset.CUSTOM);
            } else {
                config.applyPreset(selectedPreset[0]);
            }
            config.save();
        });

        return builder.build();
    }
}
