package org.oryxel.viabedrockutility.pack.processor;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import org.oryxel.viabedrockutility.neoforge.ViaBedrockUtilityNeoForge;

/** Explicit singleplayer-only fixture. Never automatically prepares resources or connects to servers. */
public final class TexturePreparationDebug {
    private static final ResourceLocation ID=ResourceLocation.fromNamespaceAndPath("viabedrockutility","texture_test_fixture");
    private static LazyBedrockTexture fixture;
    private static int generation,fixtureSize;
    private static boolean firstUseWasDeferred;
    private static volatile String error="";
    private TexturePreparationDebug(){}
    public static void register(RegisterClientCommandsEvent event){
        event.getDispatcher().register(Commands.literal("vbutexturedebug")
            .executes(ctx->{String text=snapshot();ctx.getSource().sendSuccess(()->Component.literal(text).append(Component.literal(" [复制全部]").withStyle(s->s.withClickEvent(new ClickEvent.CopyToClipboard(text)))),false);ViaBedrockUtilityNeoForge.LOGGER.info("[TexturePreparationDebug] {}",text);return 1;})
            .then(Commands.literal("test").requires(s->Minecraft.getInstance().hasSingleplayerServer())
                .then(Commands.argument("size",IntegerArgumentType.integer(16,2048)).executes(ctx->{prepare(IntegerArgumentType.getInteger(ctx,"size"));ctx.getSource().sendSuccess(()->Component.literal("单人纹理测试已提交；使用 /vbutexturedebug 查看结果，clear 释放。"),false);return 1;})))
            .then(Commands.literal("clear").executes(ctx->{clear();return 1;})));
    }
    private static void prepare(int size){
        clear();int expected=generation;error="";fixtureSize=size;
        CompletableFuture.supplyAsync(()->{
            try{var image=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<size;y++)for(int x=0;x<size;x++)image.setRGB(x,y,0xff000000|((x*255/size)<<16)|(y*255/size));var bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);return bytes.toByteArray();}
            catch(Exception failure){throw new IllegalStateException(failure);}
        }).whenComplete((bytes,failure)->Minecraft.getInstance().execute(()->{
            if(expected!=generation||!Minecraft.getInstance().hasSingleplayerServer())return;
            if(failure!=null){error=failure.getClass().getSimpleName();return;}
            fixture=new LazyBedrockTexture(()->"VBU local texture fixture",bytes,t->error=t.getClass().getSimpleName());
            Minecraft.getInstance().getTextureManager().register(ID,fixture);
            fixture.getTextureView();firstUseWasDeferred=!fixture.isLoaded();
        }));
    }
    public static void render(net.neoforged.neoforge.client.event.RenderGuiEvent.Post event){
        if(fixture==null||!Minecraft.getInstance().hasSingleplayerServer()||Minecraft.getInstance().options.hideGui)return;
        var g=event.getGuiGraphics();int y=Math.max(28,Minecraft.getInstance().getWindow().getGuiScaledHeight()-100);
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,ID,8,y,0f,0f,64,64,fixtureSize,fixtureSize,fixtureSize,fixtureSize);
        g.drawString(Minecraft.getInstance().font,fixture.isLoaded()?"VBU texture ready":"VBU texture preparing",8,y-12,0xffffffff);
    }
    public static void clear(){generation++;if(fixture!=null){Minecraft.getInstance().getTextureManager().release(ID);fixture=null;}firstUseWasDeferred=false;}
    public static String snapshot(){return "firstUseDeferred="+firstUseWasDeferred+", fixture="+(fixture==null?"none":fixture.diagnosticState())+", pending="+TexturePreparation.QUEUE.pendingJobs()+", reservedBytes="+TexturePreparation.QUEUE.reservedBytes()+", error="+error;}
}
