package me.cortex.voxy.common.world.other;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.*;
import static org.junit.Assert.*;
public class MapperOpacityTest {
 @BeforeClass public static void init(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
 @Test public void storedOpacityIsLazyAndExactlyMatchesNewEntries()throws Exception {
  var flag=Mapper.StateEntry.class.getDeclaredField("pendingOpacity");flag.setAccessible(true);
  for(var block:new net.minecraft.world.level.block.Block[]{Blocks.STONE,Blocks.AIR,Blocks.GLASS,Blocks.OAK_LEAVES}) {
   var entry=new Mapper.StateEntry(3,block.defaultBlockState());assertFalse(flag.getBoolean(entry));
   var loaded=Mapper.StateEntry.deserialize(3,entry.serialize(),new boolean[1]);
   assertEquals(block!=Blocks.OAK_LEAVES,flag.getBoolean(loaded));
   var executor=java.util.concurrent.Executors.newFixedThreadPool(4);
   try {var tasks=new java.util.ArrayList<java.util.concurrent.Future<Integer>>();for(int i=0;i<40;i++)tasks.add(executor.submit(loaded::resolvedOpacity));for(var result:tasks)assertEquals(entry.opacity,(int)result.get());}
   finally {executor.shutdownNow();}
   assertFalse(flag.getBoolean(loaded));assertEquals(entry.opacity,loaded.resolvedOpacity());
  }
 }
}
