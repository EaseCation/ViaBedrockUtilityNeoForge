package org.oryxel.viabedrockutility.pack.processor;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;
class TextureImageBudgetTest {
    private byte[] png(int w,int h){byte[] b=new byte[33];var buf=ByteBuffer.wrap(b);buf.putLong(0x89504e470d0a1a0aL).putInt(13).putInt(0x49484452).putInt(w).putInt(h);return b;}
    @Test void dimensionsAreValidatedBeforeNativeAllocation(){
        assertEquals(64L*1024*1024,TextureImageBudget.inspect(png(4096,4096)).bytes());
        for(int bad:new int[]{0,-1,4097,Integer.MAX_VALUE})assertThrows(IllegalArgumentException.class,()->TextureImageBudget.inspect(png(bad,32)));
        assertThrows(IllegalArgumentException.class,()->TextureImageBudget.inspect(new byte[32]));
        byte[] invalid=png(1,1);invalid[12]=0;assertThrows(IllegalArgumentException.class,()->TextureImageBudget.inspect(invalid));
    }
}
