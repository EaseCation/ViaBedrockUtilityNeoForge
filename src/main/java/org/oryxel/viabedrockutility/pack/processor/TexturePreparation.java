package org.oryxel.viabedrockutility.pack.processor;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.concurrent.*;
import net.minecraft.util.profiling.Profiler;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** One bounded preparation pipeline shared by server-pack texture generations. */
public final class TexturePreparation {
    private static final ThreadPoolExecutor WORKERS=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(32),r->{Thread t=new Thread(r,"VBU-TextureDecode");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    static final DeferredTextureQueue<NativeImage> QUEUE=new DeferredTextureQueue<>(WORKERS,32,TextureImageBudget.MAX_PIXELS_BYTES);
    private TexturePreparation(){}
    public static void beforeFrame(RenderFrameEvent.Pre event){
        RenderSystem.assertOnRenderThread();var p=Profiler.get();p.push("ec:viabedrockutility/texture/pump");
        try{QUEUE.pump(4L*1024*1024,2,2_000_000L,System::nanoTime);p.incrementCounter("ec:viabedrockutility/texture/pending",QUEUE.pendingJobs());}
        finally{p.pop();}
    }
}
