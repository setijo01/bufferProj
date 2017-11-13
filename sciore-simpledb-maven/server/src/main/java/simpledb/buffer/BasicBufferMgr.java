package simpledb.buffer;

import java.util.*;
import simpledb.file.*;

/**
 * Manages the pinning and unpinning of buffers to blocks.
 * @author Edward Sciore
 *
 */
class BasicBufferMgr {
   private Buffer[] bufferpool;
   private int numAvailable;
   private int strategy;
   private ArrayList<Integer> FIFOlist;
   private ArrayList<Integer> LRUlist;
   private int Clocklast;

   /**
    * Creates a buffer manager having the specified number 
    * of buffer slots.
    * This constructor depends on both the {@link FileMgr} and
    * {@link simpledb.log.LogMgr LogMgr} objects 
    * that it gets from the class
    * {@link simpledb.server.SimpleDB}.
    * Those objects are created during system initialization.
    * Thus this constructor cannot be called until 
    * {@link simpledb.server.SimpleDB#initFileAndLogMgr(String)} or
    * is called first.
    * @param numbuffs the number of buffer slots to allocate
    */
   BasicBufferMgr(int numbuffs) {
      bufferpool = new Buffer[numbuffs];
      FIFOlist = new ArrayList<Integer>();
      LRUlist = new ArrayList<Integer>();
      Clocklast = 0;
      numAvailable = numbuffs;
      for (int i=0; i<numbuffs; i++)
         bufferpool[i] = new Buffer();
   }
   
   /**
    * Flushes the dirty buffers modified by the specified transaction.
    * @param txnum the transaction's id number
    */
   synchronized void flushAll(int txnum) {
      for (Buffer buff : bufferpool)
         if (buff.isModifiedBy(txnum))
         buff.flush();
   }
   
   /**
    * Pins a buffer to the specified block. 
    * If there is already a buffer assigned to that block
    * then that buffer is used;  
    * otherwise, an unpinned buffer from the pool is chosen.
    * Returns a null value if there are no available buffers.
    * @param blk a reference to a disk block
    * @return the pinned buffer
    */
   synchronized Buffer pin(Block blk) {
      Buffer buff = findExistingBuffer(blk);
      if (buff == null) {
         buff = chooseUnpinnedBuffer();
         if (buff == null)
            return null;
         buff.assignToBlock(blk);
      }
      if (!buff.isPinned())
         numAvailable--;
      buff.pin();
      int lastPinned = Arrays.asList(bufferpool).indexOf(buff);
      if (FIFOlist.contains(lastPinned))
          FIFOlist.remove(new Integer(lastPinned));
      FIFOlist.add(lastPinned);
      return buff;
   }
   
   /**
    * Allocates a new block in the specified file, and
    * pins a buffer to it. 
    * Returns null (without allocating the block) if 
    * there are no available buffers.
    * @param filename the name of the file
    * @param fmtr a pageformatter object, used to format the new block
    * @return the pinned buffer
    */
   synchronized Buffer pinNew(String filename, PageFormatter fmtr) {
      Buffer buff = chooseUnpinnedBuffer();
      if (buff == null)
         return null;
      buff.assignToNew(filename, fmtr);
      numAvailable--;
      buff.pin();
      return buff;
   }
   
   /**
    * Unpins the specified buffer.
    * @param buff the buffer to be unpinned
    */
   synchronized void unpin(Buffer buff) {
      buff.unpin();
      int lastUnpinned = Arrays.asList(bufferpool).indexOf(buff);
      if (LRUlist.contains(lastUnpinned))
          LRUlist.remove(new Integer(lastUnpinned));
      LRUlist.add(lastUnpinned);
      if (!buff.isPinned())
         numAvailable++;
   }
   
   /**
    * Returns the number of available (i.e. unpinned) buffers.
    * @return the number of available buffers
    */
   int available() {
      return numAvailable;
   }
   
   private Buffer findExistingBuffer(Block blk) {
      for (Buffer buff : bufferpool) {
         Block b = buff.block();
         if (b != null && b.equals(blk))
            return buff;
      }
      return null;
   }
   
   private Buffer chooseUnpinnedBuffer() {
     switch (this.strategy) {
      case 0:
       return useNaiveStrategy();
      case 1:
        return useFIFOStrategy();
      case 2:
        return useLRUStrategy();
      case 3:
        return useClockStrategy();
      default:
        return null;
     }
   }
   /**
    * @return Allocated buffers
    */
   public Buffer[] getBuffers() {
     return this.bufferpool;
   }
   /**
    * Set buffer selection strategy
    * @param s (0 - Naive, 1 - FIFO, 2 - LRU, 3 - Clock)
    */
   public void setStrategy(int s) {
     this.strategy = s;
   }
   /**
    * Naive buffer selection strategy
    * @return 
    */
   private Buffer useNaiveStrategy() {
      for (Buffer buff : bufferpool)
         if (!buff.isPinned())
         return buff;
      return null;
   }
   /**
    * FIFO buffer selection strategy
    * @return 
    */
   private Buffer useFIFOStrategy() {
      int buffIndx = FIFOlist.get(0);
      if(!bufferpool[buffIndx].isPinned()){
          Buffer buff = bufferpool[buffIndx];
          FIFOlist.remove(new Integer(buffIndx));
          return buff;
      }
      return null;
   }
   /**
    * LRU buffer selection strategy
    * @return 
    */
   private Buffer useLRUStrategy() {
      System.out.println(LRUlist);
      int buffIndx = LRUlist.get(0);
      System.out.println(buffIndx);
      if(!bufferpool[buffIndx].isPinned()){
          Buffer buff = bufferpool[buffIndx];
          LRUlist.remove(new Integer(buffIndx));
          return buff;
      }
      return null;
   }
   /**
    * Clock buffer selection strategy
    * @return 
    */
   private Buffer useClockStrategy() {
      int startIndx = FIFOlist.get(FIFOlist.size()-1);
      int searchIndx = startIndx+1;
      while(searchIndx % (bufferpool.length+1)!= startIndx){
          if(!bufferpool[searchIndx % (bufferpool.length+1)].isPinned()){
              Buffer buff = bufferpool[searchIndx % (bufferpool.length+1)];
              return buff;
          }
          searchIndx++;
      }
      return null;
   }
}
