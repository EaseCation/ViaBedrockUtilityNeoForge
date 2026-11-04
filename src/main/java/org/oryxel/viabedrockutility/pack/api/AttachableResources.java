package org.oryxel.viabedrockutility.pack.api;

import net.minecraft.client.Minecraft;
import net.easecation.bedrockmotion.pack.PackManager;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.pack.processor.TextureProcessor;
import java.util.*;

/** Optional resource preparation, with no actor, playback clock, events or mutable bone state.
 * Confirms declared native dependencies and GPU textures. Does not prove a later draw is visible.
 */
public final class AttachableResources {
    private AttachableResources() {}
    public record Receipt(long connection,long generation,String item,int attachables,int geometries,int animations,int textures) {}
    public static Receipt prepare(long connection,long generation,String item) {
        if(!Minecraft.getInstance().isSameThread())throw new IllegalStateException("Attachable preparation requires client thread");
        var packs=current(connection,generation);
        var candidates=packs.getAttachableDefinitions().candidatesFor(Objects.requireNonNull(item));
        if(candidates.isEmpty())throw new IllegalArgumentException("No native attachable for item: "+item);
        Set<String> geometries=new HashSet<>(),animations=new HashSet<>(),textures=new LinkedHashSet<>();
        for(var candidate:candidates) {
            var data=candidate.data();
            for(String geometry:data.getGeometries().values()) {
                if(!packs.getModelDefinitions().getEntityModels().containsKey(geometry))throw new IllegalArgumentException("Missing attachable geometry: "+geometry);
                geometries.add(geometry);
            }
            if(data.getGeometries().isEmpty())throw new IllegalArgumentException("Attachable has no declared geometry: "+candidate.identifier());
            for(var controller:data.getControllers()) {
                if(!packs.getRenderControllerDefinitions().getRenderControllers().containsKey(controller.identifier()))
                    throw new IllegalArgumentException("Missing attachable render controller: "+controller.identifier());
            }
            // A controller alias is resolved within its attachable, not across different candidates.
            Set<String> visited=new HashSet<>();
            for(String animation:data.getAnimations().values())animation(packs,data.getAnimations(),animation,visited);
            animations.addAll(visited);textures.addAll(data.getTextures().values());
        }
        // Validate the complete dependency set before causing GPU allocation.
        for(String texture:textures)if(!TextureProcessor.isRegisteredTexture(texture))
            throw new IllegalArgumentException("Missing attachable texture: "+texture);
        for(String texture:textures)TextureProcessor.prepareRequiredTexture(texture);
        current(connection,generation);
        return new Receipt(connection,generation,item,candidates.size(),geometries.size(),animations.size(),textures.size());
    }
    private static void animation(PackManager packs,Map<String,String> aliases,String id,Set<String> visited) {
        var pending=new ArrayDeque<String>();pending.add(id);
        while(!pending.isEmpty()) {
            String selected=pending.removeFirst();
            if(!visited.add(selected))continue;
            if(packs.getAnimationDefinitions().getAnimations().containsKey(selected))continue;
            var controller=packs.getAnimationControllerDefinitions().getControllers().get(selected);
            if(controller==null)throw new IllegalArgumentException("Missing attachable animation: "+selected);
            if(!controller.getStates().containsKey(controller.getInitialState()))throw new IllegalArgumentException("Missing animation controller initial state: "+selected);
            for(var state:controller.getStates().values())for(var entry:state.getAnimations()) {
                String target=aliases.get(entry.shortName());
                if(target==null)throw new IllegalArgumentException("Missing attachable animation alias: "+entry.shortName());
                if(!visited.contains(target))pending.addLast(target);
            }
        }
    }

    private static PackManager current(long connection,long generation) {
        var host=ViaBedrockUtility.getInstance();var current=host.getPackGeneration();
        if(host.getConnectionEpoch()!=connection||current.generation()!=generation||current.manager()==null)
            throw new IllegalStateException("Attachable resource generation retired");
        return current.manager();
    }
}
