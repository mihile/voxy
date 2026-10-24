package me.cortex.voxy.verify;

import me.cortex.voxy.commonImpl.WorldCreationFailureTest;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.junit.runner.JUnitCore;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/** Runs the same behavioral tests against the installable, remapped classes in Fabric. */
public final class StorageFailureVerification implements ModInitializer {
    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            var thread = new Thread(() -> {
                var logger = LoggerFactory.getLogger("VoxyStorageVerification");
                try {
                    var result = JUnitCore.runClasses(WorldCreationFailureTest.class);
                    for (var failure : result.getFailures()) logger.error("VOXY_STORAGE_TEST FAILURE {}", failure.getTrace());
                    String summary = (result.wasSuccessful() ? "PASS" : "FAIL") + " tests=" + result.getRunCount()
                            + " failures=" + result.getFailureCount() + " millis=" + result.getRunTime();
                    Files.writeString(Path.of("voxy-storage-test-result.txt"), summary + "\n");
                    if (result.wasSuccessful()) logger.info("VOXY_STORAGE_TEST {}", summary);
                    else logger.error("VOXY_STORAGE_TEST {}", summary);
                } catch (Throwable failure) {
                    logger.error("VOXY_STORAGE_TEST FAILURE", failure);
                }
            }, "Voxy storage failure verification");
            thread.setDaemon(true);
            thread.start();
        });
    }
}
