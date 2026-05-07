package org.oryxel.viabedrockutility.payload.impl.camera;

import lombok.Getter;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

@Getter
public final class CameraPresetsPayload extends CameraPayload {

    private final List<PresetEntry> presets;

    private CameraPresetsPayload(List<PresetEntry> presets) {
        super(CameraPayloadType.CAMERA_PRESETS);
        this.presets = presets;
    }

    public static CameraPresetsPayload decode(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<PresetEntry> presets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String name = CameraPayload.readString(buf);
            String parent = CameraPayload.readString(buf);
            Float posX = readOptionalFloat(buf);
            Float posY = readOptionalFloat(buf);
            Float posZ = readOptionalFloat(buf);
            Float rotX = readOptionalFloat(buf);
            Float rotY = readOptionalFloat(buf);
            Byte audioListener = readOptionalByte(buf);
            Boolean playerEffects = readOptionalBoolean(buf);
            presets.add(new PresetEntry(name, parent, posX, posY, posZ, rotX, rotY, audioListener, playerEffects));
        }
        return new CameraPresetsPayload(presets);
    }

    private static Float readOptionalFloat(FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readFloat() : null;
    }

    private static Byte readOptionalByte(FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readByte() : null;
    }

    private static Boolean readOptionalBoolean(FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readBoolean() : null;
    }

    @Getter
    public static class PresetEntry {
        private final String name;
        private final String parent;
        private final Float posX, posY, posZ;
        private final Float rotX, rotY;
        private final Byte audioListener;
        private final Boolean playerEffects;

        public PresetEntry(String name, String parent, Float posX, Float posY, Float posZ, Float rotX, Float rotY, Byte audioListener, Boolean playerEffects) {
            this.name = name;
            this.parent = parent;
            this.posX = posX;
            this.posY = posY;
            this.posZ = posZ;
            this.rotX = rotX;
            this.rotY = rotY;
            this.audioListener = audioListener;
            this.playerEffects = playerEffects;
        }
    }
}
