package org.oryxel.viabedrockutility.pack.processor;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class DeferredTextureQueueTest {
    static final class Image implements AutoCloseable {int closes;public void close(){assertEquals(0,closes++);}}
    @Test void requestDoesNotDecodeAndByteBudgetIsHeldUntilAllUploadSlicesFinish(){
        var work=new ArrayDeque<Runnable>();var queue=new DeferredTextureQueue<Image>(work::add,2,64);var image=new Image();var remaining=new AtomicInteger(16);var calls=new AtomicInteger();
        var ticket=queue.request(16,()->{calls.incrementAndGet();return image;},(v,b)->{assertSame(image,v);long n=Math.min(b,remaining.get());remaining.addAndGet(-(int)n);return new DeferredTextureQueue.Progress(n,remaining.get()==0);},t->fail(t));
        assertNotNull(ticket);assertEquals(0,calls.get());assertEquals(16,queue.reservedBytes());
        queue.pump(8,2,100,()->0);assertEquals(16,remaining.get());work.remove().run();
        queue.pump(8,2,100,()->0);assertEquals(8,remaining.get());assertEquals(0,image.closes);assertEquals(16,queue.reservedBytes());
        queue.pump(8,2,100,()->0);assertTrue(ticket.complete());assertEquals(1,image.closes);assertEquals(0,queue.reservedBytes());assertEquals(0,queue.pendingJobs());ticket.close();assertEquals(1,image.closes);
    }
    @Test void capacityAndExecutorRejectionNeverDecodeOnCallerOrLeakReservations(){
        var work=new ArrayDeque<Runnable>();var queue=new DeferredTextureQueue<Image>(work::add,1,16);
        var ticket=queue.request(16,Image::new,(v,b)->new DeferredTextureQueue.Progress(0,true),t->fail(t));
        assertNull(queue.request(1,()->{fail("not submitted");return null;},(v,b)->null,t->{}));assertNull(queue.request(17,Image::new,(v,b)->null,t->{}));
        ticket.close();work.remove().run();assertEquals(0,queue.reservedBytes());
        var rejected=new DeferredTextureQueue<Image>(r->{throw new RejectedExecutionException();},2,32);
        assertNull(rejected.request(16,()->{fail("caller decode");return null;},(v,b)->null,t->{}));assertEquals(0,rejected.reservedBytes());assertEquals(0,rejected.pendingJobs());
    }
    @Test void closingWhileDecodeRunsDiscardsLateResultsAndHoldsMemoryPermit()throws Exception{
        var started=new CountDownLatch(1);var finish=new CountDownLatch(1);var pool=Executors.newSingleThreadExecutor();var image=new Image();var uploads=new AtomicInteger();
        try{
            var queue=new DeferredTextureQueue<Image>(pool,2,16);var ticket=queue.request(16,()->{started.countDown();try{assertTrue(finish.await(5,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}return image;},(v,b)->{uploads.incrementAndGet();return new DeferredTextureQueue.Progress(0,true);},t->fail(t));
            assertTrue(started.await(5,TimeUnit.SECONDS));ticket.close();assertEquals(16,queue.reservedBytes());assertNull(queue.request(1,Image::new,(v,b)->null,t->{}));finish.countDown();pool.submit(()->{}).get(5,TimeUnit.SECONDS);
            queue.pump(16,2,100,()->0);assertEquals(0,uploads.get());assertEquals(1,image.closes);assertEquals(0,queue.reservedBytes());
        }finally{finish.countDown();pool.shutdownNow();}
    }
    @Test void obsoleteReadyGenerationIsReleasedWithoutUploadingOverTheReplacement(){
        var work=new ArrayDeque<Runnable>();var queue=new DeferredTextureQueue<Image>(work::add,2,32);var old=new Image();var fresh=new Image();var uploaded=new ArrayList<Image>();
        var t=queue.request(16,()->old,(v,b)->{uploaded.add(v);return new DeferredTextureQueue.Progress(16,true);},e->fail(e));work.remove().run();t.close();
        queue.request(16,()->fresh,(v,b)->{uploaded.add(v);return new DeferredTextureQueue.Progress(16,true);},e->fail(e));work.remove().run();queue.pump(16,1,100,()->0);
        assertEquals(List.of(fresh),uploaded);assertEquals(1,old.closes);assertEquals(1,fresh.closes);assertEquals(0,queue.reservedBytes());
    }
    @Test void decodeAndUploadFailuresReleaseTheirResourcesAndReportOriginalCause(){
        var work=new ArrayDeque<Runnable>();var queue=new DeferredTextureQueue<Image>(work::add,2,32);var errors=new ArrayList<Throwable>();var failure=new IllegalStateException("decode");
        queue.request(16,()->{throw failure;},(v,b)->null,errors::add);work.remove().run();assertEquals(List.of(failure),errors);assertEquals(0,queue.reservedBytes());
        var image=new Image();var uploadFailure=new IllegalStateException("upload");queue.request(16,()->image,(v,b)->{throw uploadFailure;},errors::add);work.remove().run();queue.pump(16,1,100,()->0);assertSame(uploadFailure,errors.get(1));assertEquals(1,image.closes);assertEquals(0,queue.reservedBytes());
    }
    @Test void oneFrameRespectsOperationsAndTimeBudgetAtThirtySixtyAndUnlimitedCadence(){
        for(int fps:new int[]{30,60,0}){
            var work=new ArrayDeque<Runnable>();var queue=new DeferredTextureQueue<Image>(work::add,8,128);var uploads=new AtomicInteger();var clock=new AtomicLong();
            for(int i=0;i<5;i++)queue.request(16,Image::new,(v,b)->{uploads.incrementAndGet();clock.addAndGet(2_000_000);return new DeferredTextureQueue.Progress(16,true);},t->fail(t));
            while(!work.isEmpty())work.remove().run();queue.pump(64,2,2_000_000,clock::get);assertEquals(1,uploads.get());assertEquals(4,queue.pendingJobs());
            clock.addAndGet(fps==0?1_000_000:1_000_000_000/fps);queue.pump(16,2,100_000_000,clock::get);assertEquals(2,uploads.get());
            queue.pump(128,8,100_000_000,clock::get);assertEquals(0,queue.reservedBytes());
        }
    }
}
