package org.oryxel.viabedrockutility.pack.processor;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.*;
import net.easecation.performance.api.TraceLink;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.util.profiling.Profiler;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** First use never decodes or waits: render a shared placeholder while bounded preparation runs. */
final class LazyBedrockTexture extends AbstractTexture {
    private final Supplier<String> label;
    private final Consumer<Throwable> decodeFailure;
    private byte[] encodedImage;
    private byte[] requiredBytes;
    private boolean closed;
    private volatile boolean failed;
    private DeferredTextureQueue<NativeImage>.Ticket ticket;
    private GpuTexture uploadingTexture;
    private int uploadedRows,uploadSteps;
    private volatile String decodeThread="";
    private Boolean requestedClamp,requestedBlur,requestedMipmaps;

    LazyBedrockTexture(Supplier<String> label,byte[] encodedImage,Consumer<Throwable> decodeFailure){
        this.label=Objects.requireNonNull(label);this.encodedImage=Objects.requireNonNull(encodedImage);this.requiredBytes=encodedImage;this.decodeFailure=Objects.requireNonNull(decodeFailure);
    }
    boolean isLoaded(){return texture!=null;}
    String diagnosticState(){return "loaded="+isLoaded()+"/closed="+closed+"/failed="+failed+"/uploadedRows="+uploadedRows+"/uploadSteps="+uploadSteps+"/decodeThread="+decodeThread;}
    void preload(){request();}
    private void request(){
        if(closed)throw new IllegalStateException("Texture has already been closed");
        if(failed||texture!=null||ticket!=null)return;
        RenderSystem.assertOnRenderThread();
        final byte[] bytes=encodedImage;
        final TextureImageBudget.Dimensions dimensions;
        try{dimensions=TextureImageBudget.inspect(bytes);}catch(RuntimeException invalid){failure(invalid);encodedImage=null;return;}
        ticket=TexturePreparation.QUEUE.request(dimensions.bytes(),()->decode(bytes,dimensions),this::upload,this::failure);
        if(ticket!=null)encodedImage=null; // Decoder owns the compressed bytes until completion/cancellation.
    }
    private NativeImage decode(byte[] bytes,TextureImageBudget.Dimensions expected){
        decodeThread=Thread.currentThread().getName();
        long token=TraceLink.begin("viabedrockutility","texture/decode");boolean failed=true;
        try{
            NativeImage result=NativeImage.read(bytes);
            if(result.getWidth()!=expected.width()||result.getHeight()!=expected.height()){result.close();throw new IllegalArgumentException("Decoded texture dimensions changed");}
            failed=false;return result;
        }catch(IOException e){throw new IllegalArgumentException("Unable to decode Bedrock texture",e);}
        finally{TraceLink.end(token,failed);}
    }
    /** Explicit preload contract retained for callers that require ready GPU textures. Normal drawing remains deferred. */
    void requireLoaded() {
        RenderSystem.assertOnRenderThread();
        if (texture != null) return;
        if (closed || failed || requiredBytes == null) throw new IllegalStateException("Required texture unavailable");
        if (ticket != null) { ticket.close(); ticket = null; }
        discardUploading(); uploadedRows = 0;
        var bytes = requiredBytes;
        try (var image = decode(bytes, TextureImageBudget.inspect(bytes))) {
            upload(image, Long.MAX_VALUE);
            encodedImage = null;
        } catch (RuntimeException failure) { failure(failure); throw failure; }
    }
    private DeferredTextureQueue.Progress upload(NativeImage image,long allowance){
        RenderSystem.assertOnRenderThread();
        if(closed||failed)return new DeferredTextureQueue.Progress(0,true);
        var p=Profiler.get();long token=TraceLink.begin("viabedrockutility","texture/upload");boolean failed=true;
        try{
            if(uploadingTexture==null){
                p.push("ec:viabedrockutility/texture/create_gpu");
                try{uploadingTexture=RenderSystem.getDevice().createTexture(label,5,TextureFormat.RGBA8,image.getWidth(),image.getHeight(),1,1);uploadingTexture.setAddressMode(com.mojang.blaze3d.textures.AddressMode.REPEAT);uploadingTexture.setTextureFilter(FilterMode.NEAREST,false);}
                finally{p.pop();}
            }
            int rows=(int)Math.min(image.getHeight()-uploadedRows,allowance/((long)image.getWidth()*4));
            if(rows<1){failed=false;return new DeferredTextureQueue.Progress(0,false);}
            p.push("ec:viabedrockutility/texture/upload_rows");
            try{RenderSystem.getDevice().createCommandEncoder().writeToTexture(uploadingTexture,image,0,0,0,uploadedRows,image.getWidth(),rows,0,uploadedRows);}
            finally{p.pop();}
            uploadedRows+=rows;uploadSteps++;long bytes=(long)image.getWidth()*rows*4;p.incrementCounter("ec:viabedrockutility/texture/upload_bytes",(int)bytes);
            boolean done=uploadedRows==image.getHeight();
            if(done){
                p.push("ec:viabedrockutility/texture/create_view");
                try{textureView=RenderSystem.getDevice().createTextureView(uploadingTexture);texture=uploadingTexture;uploadingTexture=null;requiredBytes=null;
                    if(requestedClamp!=null)super.setClamp(requestedClamp);
                    if(requestedBlur!=null)super.setFilter(requestedBlur,Boolean.TRUE.equals(requestedMipmaps));
                    else if(requestedMipmaps!=null)super.setUseMipmaps(requestedMipmaps);}
                finally{p.pop();}
            }
            failed=false;return new DeferredTextureQueue.Progress(bytes,done);
        }catch(RuntimeException|Error failure){releaseOwnedTextures();throw failure;}
        finally{TraceLink.end(token,failed);}
    }
    private void failure(Throwable problem){failed=true;requiredBytes=null;decodeFailure.accept(problem);}
    private AbstractTexture fallback(){return Minecraft.getInstance().getTextureManager().getTexture(MissingTextureAtlasSprite.getLocation());}
    @Override public GpuTexture getTexture(){request();return texture!=null?super.getTexture():fallback().getTexture();}
    @Override public GpuTextureView getTextureView(){request();return texture!=null?super.getTextureView():fallback().getTextureView();}
    @Override public void setClamp(boolean clamp){requestedClamp=clamp;request();if(texture!=null)super.setClamp(clamp);}
    @Override public void setFilter(boolean blur,boolean mipmap){requestedBlur=blur;requestedMipmaps=mipmap;request();if(texture!=null)super.setFilter(blur,mipmap);}
    @Override public void setUseMipmaps(boolean mipmap){requestedMipmaps=mipmap;request();if(texture!=null)super.setUseMipmaps(mipmap);}
    private void discardUploading(){if(uploadingTexture!=null){try{uploadingTexture.close();}finally{uploadingTexture=null;}}}
    private void releaseOwnedTextures(){try{discardUploading();}finally{try{super.close();}finally{texture=null;textureView=null;}}}
    @Override public void close(){closed=true;encodedImage=null;requiredBytes=null;if(ticket!=null)ticket.close();releaseOwnedTextures();}
}
