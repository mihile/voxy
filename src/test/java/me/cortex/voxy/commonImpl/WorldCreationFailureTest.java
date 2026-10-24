package me.cortex.voxy.commonImpl;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.cortex.voxy.common.config.section.SectionSerializationStorage;
import me.cortex.voxy.common.config.section.SectionStorage;
import me.cortex.voxy.common.config.storage.rocksdb.RocksDBStorageBackend;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.ByteBuffer;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Function;
import java.util.function.LongConsumer;

import static org.junit.Assert.*;

public class WorldCreationFailureTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static WorldIdentifier id(long seed) {
        return new WorldIdentifier(Level.OVERWORLD, seed, null);
    }

    @Test
    public void storageOpenFailureDoesNotBlockReadsRetriesOrShutdown() throws Exception {
        var failure = new IllegalStateException("injected storage open failure");
        var attempts = new AtomicInteger();
        var instance = new TestInstance(key -> {
            if (attempts.getAndIncrement() == 0) throw failure;
            return new EmptyStorage();
        });
        try {
            var key = id(1);
            assertSame(failure, assertThrows(IllegalStateException.class, () -> instance.getOrCreate(key, true)));
            assertNull(completes(() -> instance.getNullable(key)));
            var world = completes(() -> instance.getOrCreate(key, true));
            assertSame(world, instance.getOrCreate(id(1)));
            assertEquals(2, attempts.get());
            world.releaseRef();
            completes(() -> { instance.shutdown(); return null; });
        } finally {
            shutdownIfUnlocked(instance);
        }
    }

    @Test
    public void errorAlsoReleasesWorldLock() throws Exception {
        var failure = new AssertionError("injected constructor error");
        var instance = new TestInstance(key -> { throw failure; });
        try {
            var key = id(2);
            assertSame(failure, assertThrows(AssertionError.class, () -> instance.getOrCreate(key)));
            assertNull(completes(() -> instance.getNullable(key)));
        } finally {
            shutdownIfUnlocked(instance);
        }
    }

    @Test
    public void mapperFailureClosesUnpublishedStorageExactlyOnce() throws Exception {
        var failure = new IllegalStateException("injected mapping read failure");
        var closeFailure = new IllegalStateException("injected cleanup failure");
        var storage = new EmptyStorage() {
            @Override public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() { throw failure; }
            @Override public void close() { super.close(); throw closeFailure; }
        };
        var attempts = new AtomicInteger();
        var instance = new TestInstance(key -> attempts.getAndIncrement() == 0 ? storage : new EmptyStorage());
        try {
            var key = id(3);
            assertSame(failure, assertThrows(IllegalStateException.class, () -> instance.getOrCreate(key)));
            assertEquals(1, storage.closes);
            assertArrayEquals(new Throwable[]{closeFailure}, failure.getSuppressed());
            assertNull(completes(() -> instance.getNullable(key)));
            var world = completes(() -> instance.getOrCreate(key, true));
            world.releaseRef();
            completes(() -> { instance.shutdown(); return null; });
            assertEquals(1, storage.closes);
        } finally {
            shutdownIfUnlocked(instance);
        }
    }

    @Test
    public void actualRocksDbLockConflictCanRecoverWithoutDeletingLockOrData() throws Exception {
        var path = temporary.newFolder("locked-store").getAbsolutePath();
        var holder = new RocksDBStorageBackend(path);
        var instance = new TestInstance(key -> new SectionSerializationStorage(new RocksDBStorageBackend(path)));
        try {
            var key = id(4);
            var failure = assertThrows(RuntimeException.class, () -> instance.getOrCreate(key));
            assertTrue(failure.getCause() instanceof org.rocksdb.RocksDBException);
            assertNull(completes(() -> instance.getNullable(key)));
            holder.close(); holder = null;
            var world = completes(() -> instance.getOrCreate(key, true));
            var marker = ByteBuffer.wrap(new byte[]{11, 22, 33});
            world.storage.putIdMapping(123, marker);
            assertArrayEquals(new byte[]{11, 22, 33}, world.storage.getIdMappingsData().get(123));
            world.releaseRef();
            completes(() -> { instance.shutdown(); return null; });
            var reopened = new RocksDBStorageBackend(path);
            try {
                assertArrayEquals(new byte[]{11, 22, 33}, reopened.getIdMappingsData().get(123));
            } finally { reopened.close(); }
        } finally {
            if (holder != null) holder.close();
            shutdownIfUnlocked(instance);
        }
    }

    @Test
    public void mapperFailureReleasesActualRocksDbHandleForNextOpen() throws Exception {
        var path = temporary.newFolder("mapping-failure").getAbsolutePath();
        var closes = new AtomicInteger();
        var attempts = new AtomicInteger();
        var instance = new TestInstance(key -> {
            var backend = new RocksDBStorageBackend(path);
            if (attempts.getAndIncrement() != 0) return new SectionSerializationStorage(backend);
            return new SectionSerializationStorage(backend) {
                @Override public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() {
                    throw new IllegalStateException("injected mapping failure after DB open");
                }
                @Override public void close() { closes.incrementAndGet(); super.close(); }
            };
        });
        try {
            var key = id(5);
            assertThrows(IllegalStateException.class, () -> instance.getOrCreate(key));
            assertEquals(1, closes.get());
            var world = completes(() -> instance.getOrCreate(key, true));
            world.releaseRef();
            completes(() -> { instance.shutdown(); return null; });
            assertEquals(1, closes.get());
        } finally {
            shutdownIfUnlocked(instance);
        }
    }

    @Test
    public void failedWalFlushStillClosesDatabaseAndPreservesOriginalFailure() throws Exception {
        var path = temporary.newFolder("flush-failure").getAbsolutePath();
        var failure = new IllegalStateException("injected WAL flush failure");
        var backend = new RocksDBStorageBackend(path) {
            @Override public void flush() { throw failure; }
        };
        backend.putIdMapping(456, ByteBuffer.wrap(new byte[]{44, 55}));
        assertSame(failure, assertThrows(IllegalStateException.class, backend::close));
        var reopened = new RocksDBStorageBackend(path);
        try {
            assertArrayEquals(new byte[]{44, 55}, reopened.getIdMappingsData().get(456));
        } finally { reopened.close(); }
    }

    @Test
    public void concurrentRequestsAfterFailurePublishOnlyOneSuccessfulEngine() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var attempts = new AtomicInteger();
        var storage = new EmptyStorage();
        var instance = new TestInstance(key -> {
            if (attempts.getAndIncrement() == 0) {
                entered.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { throw new RuntimeException(e); }
                throw new IllegalStateException("injected first concurrent open failure");
            }
            return storage;
        });
        var executor = Executors.newFixedThreadPool(3, task -> {
            var thread = new Thread(task, "concurrent-world-creation-test");
            thread.setDaemon(true);
            return thread;
        });
        try {
            var first = executor.submit(() -> instance.getOrCreate(id(6)));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var second = executor.submit(() -> instance.getOrCreate(id(6)));
            var third = executor.submit(() -> instance.getOrCreate(id(6)));
            release.countDown();
            assertTrue(assertThrows(ExecutionException.class,
                    () -> first.get(5, TimeUnit.SECONDS)).getCause() instanceof IllegalStateException);
            assertSame(second.get(5, TimeUnit.SECONDS), third.get(5, TimeUnit.SECONDS));
            assertEquals(2, attempts.get());
            completes(() -> { instance.shutdown(); return null; });
            assertEquals(1, storage.closes);
        } finally {
            release.countDown();
            executor.shutdownNow();
            shutdownIfUnlocked(instance);
        }
    }

    private static <T> T completes(Callable<T> action) throws Exception {
        var executor = Executors.newSingleThreadExecutor(task -> {
            var thread = new Thread(task, "world-creation-failure-test");
            thread.setDaemon(true);
            return thread;
        });
        try { return executor.submit(action).get(5, TimeUnit.SECONDS); }
        finally { executor.shutdownNow(); }
    }

    // A regression must fail promptly even though StampedLock.lock is not interruptible.
    private static void shutdownIfUnlocked(TestInstance instance) throws Exception {
        if (!instance.isRunning()) return;
        var field = VoxyInstance.class.getDeclaredField("activeWorldLock");
        field.setAccessible(true);
        var lock = (StampedLock) field.get(instance);
        var stamp = lock.tryWriteLock();
        if (stamp != 0) {
            lock.unlockWrite(stamp);
            completes(() -> { instance.shutdown(); return null; });
        }
    }

    private static final class TestInstance extends VoxyInstance {
        private final Function<WorldIdentifier, SectionStorage> factory;
        TestInstance(Function<WorldIdentifier, SectionStorage> factory) { this.factory = factory; }
        @Override protected SectionStorage createStorage(WorldIdentifier key) { return factory.apply(key); }
    }

    private static class EmptyStorage extends SectionStorage {
        int closes;
        @Override public int loadSection(WorldSection section) { return 1; }
        @Override public void saveSection(WorldSection section) { }
        @Override public void putIdMapping(int id, ByteBuffer data) { }
        @Override public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() { return new Int2ObjectOpenHashMap<>(); }
        @Override public void flush() { }
        @Override public void close() { closes++; }
        @Override public void iteratePositions(int level, LongConsumer callback) { }
    }
}
