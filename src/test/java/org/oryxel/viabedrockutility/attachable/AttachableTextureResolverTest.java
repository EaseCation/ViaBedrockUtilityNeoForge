package org.oryxel.viabedrockutility.attachable;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import javax.imageio.ImageIO;
import net.easecation.bedrockmotion.pack.PackManager;
import net.easecation.bedrockmotion.pack.content.Content;
import org.junit.jupiter.api.Test;
import org.oryxel.viabedrockutility.pack.processor.TextureProcessorTest;
import org.oryxel.viabedrockutility.util.GeometryUtil;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AttachableTextureResolverTest {
    static class CountingContent extends Content {
        int decodes;
        @Override public LazyImage getImage(String path) {
            if(super.getImage(path)==null)return null;
            return new LazyImage(get(path),"png") {
                @Override public BufferedImage getImage(){decodes++;return super.getImage();}
            };
        }
    }
    private static PackManager owner(Content... contents){var p=mock(PackManager.class);when(p.getPacks()).thenReturn(List.of(contents));return p;}
    private static byte[] png(int firstAlpha)throws Exception{
        var image=new BufferedImage(2,2,BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0,0,(firstAlpha<<24)|0x123456);image.setRGB(1,0,0x00123456);image.setRGB(0,1,0x7f123456);image.setRGB(1,1,0xff123456);
        var out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);return out.toByteArray();
    }
    private static GeometryUtil.TextureAlpha alpha(byte[] bytes)throws Exception{return GeometryUtil.TextureAlpha.from(ImageIO.read(new java.io.ByteArrayInputStream(bytes)));}
    private static String randomBase(){return "textures/"+UUID.randomUUID();}
    private static GeometryUtil.TextureAlpha resolve(PackManager p,String base){return AttachableTextureResolver.resolve(p,Map.of("default",base+".png"),"default",null);}

    @Test void repeatedQueriesReuseRegisteredAlphaWithoutImageDecoding()throws Exception{
        var c=new CountingContent();String base=randomBase();byte[] bytes=png(255);c.put(base+".png",bytes);var p=owner(c);var expected=alpha(bytes);
        try(var ignored=TextureProcessorTest.cachedAlpha(p,c,base+".png",expected)){
            for(int i=0;i<20;i++)assertSame(expected,resolve(p,base));
            assertEquals(0,c.decodes);assertArrayEquals(new byte[]{(byte)255,0,127,(byte)255},expected.alpha());
        }
    }
    @Test void higherPriorityValidOrBrokenSourcesKeepTheOldSearchOrder()throws Exception{
        var low=new CountingContent();var high=new CountingContent();String base=randomBase();byte[] bytes=png(30);low.put(base+".png",bytes);high.put(base+".png",png(200));var p=owner(low,high);var cached=alpha(bytes);
        try(var ignored=TextureProcessorTest.cachedAlpha(p,low,base+".png",cached)){
            assertEquals(200,resolve(p,base).alpha()[0]&255);assertTrue(high.decodes>0);assertEquals(0,low.decodes);
            // Recognized PNG signature but broken decode: preserve fallback to the lower valid layer.
            high.put(base+".png",new byte[]{(byte)137,80,78,71,13,10,26,10,0});
            assertSame(cached,resolve(p,base));assertEquals(0,low.decodes);
        }
    }
    @Test void differentManagerAndReplacedSourceDoNotReuseStaleAlpha()throws Exception{
        var c=new CountingContent();String base=randomBase();byte[] bytes=png(10);c.put(base+".png",bytes);var old=owner(c);var next=owner(c);var cached=alpha(bytes);
        try(var ignored=TextureProcessorTest.cachedAlpha(next,c,base+".png",cached)){
            assertNotSame(cached,resolve(old,base));assertTrue(c.decodes>0);
            c.put(base+".png",png(240));assertEquals(240,resolve(next,base).alpha()[0]&255);
        }
    }
    @Test void namespaceAliasesDoNotSelectADifferentlyRegisteredCacheEntry()throws Exception{
        var c=new CountingContent();String base=randomBase();byte[] normal=png(55);c.put(base+".png",normal);c.put("custom:"+base+".png",png(220));var p=owner(c);
        try(var ignored=TextureProcessorTest.cachedAlpha(p,c,"custom:"+base+".png",alpha(png(220)))){
            // Existing resolver strips namespace before looking in Content; caching must not change that behavior.
            var value=AttachableTextureResolver.resolve(p,Map.of("skin","custom:"+base+".png"),"skin",null);
            assertArrayEquals(alpha(normal).alpha(),value.alpha());assertTrue(c.decodes>0);
        }
    }
    @Test void missingAndMalformedTexturesStillReturnNullAndReleasedSourceCanUseMatchingFallback()throws Exception{
        var c=new CountingContent();String base=randomBase();var p=owner(c);assertNull(resolve(p,base));
        c.put(base+".png",new byte[]{1,2,3});assertNull(resolve(p,base));
        var basePack=new CountingContent();byte[] bytes=png(90);basePack.put(base+".png",bytes);var cached=alpha(bytes);
        try(var ignored=TextureProcessorTest.cachedAlpha(p,basePack,base+".png",cached)){
            assertSame(cached,resolve(p,base));
            assertNull(resolve(owner(c),base));
        }
    }
}
