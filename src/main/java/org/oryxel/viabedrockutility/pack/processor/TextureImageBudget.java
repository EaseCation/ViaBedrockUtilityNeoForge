package org.oryxel.viabedrockutility.pack.processor;

/** Read PNG dimensions before a native decoder allocates pixel storage. */
final class TextureImageBudget {
    static final int MAX_SIDE = 4096;
    static final int MAX_ENCODED_BYTES = 32 * 1024 * 1024;
    static final long MAX_PIXELS_BYTES = 64L * 1024 * 1024;
    record Dimensions(int width, int height) { long bytes(){return (long)width*height*4;} }
    static Dimensions inspect(byte[] bytes) {
        if(bytes==null||bytes.length<33||bytes.length>MAX_ENCODED_BYTES)throw new IllegalArgumentException("PNG byte budget");
        byte[] signature={(byte)137,80,78,71,13,10,26,10};
        for(int i=0;i<8;i++)if(bytes[i]!=signature[i])throw new IllegalArgumentException("Expected PNG");
        if(integer(bytes,8)!=13||integer(bytes,12)!=0x49484452)throw new IllegalArgumentException("Missing PNG IHDR");
        int width=integer(bytes,16),height=integer(bytes,20);
        if(width<1||height<1||width>MAX_SIDE||height>MAX_SIDE||(long)width*height*4>MAX_PIXELS_BYTES)throw new IllegalArgumentException("PNG dimensions exceed budget");
        return new Dimensions(width,height);
    }
    private static int integer(byte[] b,int p){return (b[p]&255)<<24|(b[p+1]&255)<<16|(b[p+2]&255)<<8|b[p+3]&255;}
}
