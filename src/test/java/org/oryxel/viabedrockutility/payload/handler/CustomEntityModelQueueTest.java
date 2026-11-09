package org.oryxel.viabedrockutility.payload.handler;

import net.easecation.bedrockmotion.pack.PackManager;
import net.easecation.bedrockmotion.pack.definitions.EntityDefinitions;
import org.junit.jupiter.api.Test;
import org.oryxel.viabedrockutility.entity.CustomEntityTicker;
import org.oryxel.viabedrockutility.payload.impl.entity.AnimatePayload;
import org.oryxel.viabedrockutility.payload.impl.entity.ModelRequestPayload;
import org.oryxel.viabedrockutility.renderer.CustomEntityRenderer;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomEntityModelQueueTest {
    private static final UUID ID = new UUID(0, 1);
    private static final EntityDefinitions.EntityDefinition DEF = new EntityDefinitions.EntityDefinition("test:entity", null);
    private ModelRequestPayload model(UUID id, int skin) {
        return new ModelRequestPayload("test:entity", Set.of(), null, null, skin, 1F, id);
    }
    private static final class Handler extends CustomEntityPayloadHandler {
        final List<String> calls = new ArrayList<>();
        final CustomEntityTicker ticker = mock(CustomEntityTicker.class);
        Handler(PackManager packs) {
            this.packManager = packs;
            when(ticker.getEntityDefinition()).thenReturn(DEF);
            when(ticker.getRenderer()).thenReturn(mock(CustomEntityRenderer.class));
        }
        @Override CustomEntityTicker createTicker(PackManager manager, EntityDefinitions.EntityDefinition definition) {
            calls.add("create");return ticker;
        }
        @Override void playCustomAnimation(UUID id, String animation) {calls.add(animation);}
        void pumpUntilIdle() {
            for(int i=0;i<10 && ((Number)modelRequestSnapshot().get("pendingOperations")).intValue()>0;i++) drainModelRequests();
        }
    }
    private PackManager packs() {
        PackManager packs = mock(PackManager.class, RETURNS_DEEP_STUBS);
        when(packs.getEntityDefinitions().getEntities()).thenReturn(Map.of("test:entity", DEF));
        return packs;
    }
    @Test void realHandlerDefersBuildAndCoalescesToLatestState() {
        Handler h=new Handler(packs());
        for(int i=0;i<1000;i++)h.handle(model(ID,i));
        assertTrue(h.calls.isEmpty());assertTrue(h.getCachedCustomEntities().isEmpty());
        h.pumpUntilIdle();
        assertEquals(List.of("create"),h.calls);verify(h.ticker).updateRenderQueries(null,null,999);verify(h.ticker).update();
        h.handle(model(ID,999));h.drainModelRequests();verify(h.ticker,times(1)).update();
    }
    @Test void animationsWaitForModelAndDoNotOvertakeStateChanges() {
        Handler h=new Handler(packs());h.handle(model(ID,1));h.handle(new AnimatePayload(ID,"animation.walk"));h.handle(model(ID,2));
        assertTrue(h.calls.isEmpty());h.pumpUntilIdle();
        assertEquals(List.of("create","animation.walk"),h.calls);
        var order=inOrder(h.ticker);order.verify(h.ticker).updateRenderQueries(null,null,1);order.verify(h.ticker).update();
        order.verify(h.ticker).updateRenderQueries(null,null,2);order.verify(h.ticker).update();
    }
    @Test void removeBeforeFirstFrameCancelsModelAnimationAndHurtState() {
        Handler h=new Handler(packs());h.handle(model(ID,1));h.handle(new AnimatePayload(ID,"animation.walk"));
        h.markCustomEntityHurt(ID,100);h.markCustomEntityDead(ID);
        assertTrue(h.getCustomEntityHurtTracker().isDead(ID));assertTrue(h.getCustomEntityHurtTracker().isHurt(ID,100));
        h.removeCustomEntity(ID);h.drainModelRequests();assertTrue(h.calls.isEmpty());
        assertFalse(h.getCustomEntityHurtTracker().isDead(ID));assertFalse(h.getCustomEntityHurtTracker().isHurt(ID,100));
    }
    @Test void resetAndReusedUuidOnlyApplyNewSession() {
        Handler h=new Handler(packs());h.handle(model(ID,1));h.resetConnectionState();
        h.onPackManagerChanged(packs());h.handle(model(ID,2));h.pumpUntilIdle();
        verify(h.ticker,never()).updateRenderQueries(null,null,1);verify(h.ticker).updateRenderQueries(null,null,2);
    }
    @Test void packUnavailableStillUsesBoundedQueueAndPreservesInitialAnimationOrder() {
        Handler h=new Handler(null);
        h.handle((org.oryxel.viabedrockutility.payload.BasePayload)model(ID,1));
        h.handle((org.oryxel.viabedrockutility.payload.BasePayload)new AnimatePayload(ID,"animation.walk"));
        h.handle((org.oryxel.viabedrockutility.payload.BasePayload)model(ID,2));
        h.drainModelRequests();assertTrue(h.calls.isEmpty());
        assertEquals(3,h.modelRequestSnapshot().get("pendingOperations"));
        h.onPackManagerChanged(packs());h.pumpUntilIdle();
        assertEquals(List.of("create","animation.walk"),h.calls);verify(h.ticker,times(2)).update();
    }
    @Test void oversizedIdentifierIsRejectedBeforeResourcePackQueueing() {
        Handler h=new Handler(null);
        h.handle((org.oryxel.viabedrockutility.payload.BasePayload)new ModelRequestPayload("x".repeat(1025),Set.of(),null,null,null,1F,ID));
        assertEquals(0,h.modelRequestSnapshot().get("entities"));assertEquals(1L,h.modelRequestSnapshot().get("invalidRequests"));
    }
    @Test void worldReplacementClearsPendingWorkWithoutRetiringPackManager() {
        Handler h=new Handler(packs());h.handle(model(ID,1));h.clearCustomEntityModels();
        h.handle(model(ID,2));h.pumpUntilIdle();
        verify(h.ticker,never()).updateRenderQueries(null,null,1);verify(h.ticker).updateRenderQueries(null,null,2);
    }
    @Test void resourceReloadQueuesRebuildInsteadOfRebuildingAllSynchronously() {
        Handler h=new Handler(packs());h.handle(model(ID,1));h.pumpUntilIdle();h.calls.clear();
        h.onPackManagerChanged(packs());assertTrue(h.calls.isEmpty());assertTrue(h.getCachedCustomEntities().isEmpty());
        h.pumpUntilIdle();assertEquals(List.of("create"),h.calls);verify(h.ticker,times(2)).update();
    }
    @Test void invalidDefinitionIsAttemptedOnceWithoutRepeatedBuildOrLog() {
        Handler h=new Handler(packs());var bad=new ModelRequestPayload("test:missing",Set.of(),null,null,null,1F,ID);
        for(int i=0;i<1000;i++)h.handle(bad);h.pumpUntilIdle();
        assertTrue(h.calls.isEmpty());assertEquals(1L,h.modelRequestSnapshot().get("unknownModels"));
        h.handle(bad);h.drainModelRequests();assertEquals(1L,h.modelRequestSnapshot().get("unknownModels"));
    }
    @Test void failingInitialModelIsNotPublishedAndIsNotRetriedEveryFrame() {
        Handler h=new Handler(packs());doThrow(new IllegalStateException("failure")).when(h.ticker).update();
        h.handle(model(ID,1));h.pumpUntilIdle();assertTrue(h.getCachedCustomEntities().isEmpty());
        h.handle(model(ID,1));h.drainModelRequests();verify(h.ticker,times(1)).update();
        assertEquals(1L,h.modelRequestSnapshot().get("failed"));
    }
}
