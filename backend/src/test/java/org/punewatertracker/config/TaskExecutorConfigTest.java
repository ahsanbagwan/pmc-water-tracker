package org.punewatertracker.config;

import org.junit.jupiter.api.Test;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

public class TaskExecutorConfigTest {

    @Test
    void sourceLinkCheckExecutor_runsFourTasksConcurrently() throws Exception {
        Executor executor = new TaskExecutorConfig().sourceLinkCheckExecutor();
        int concurrentTasks = 4; // matches corePoolSize in TaskExecutorConfig

        CyclicBarrier barrier = new CyclicBarrier(concurrentTasks);
        CountDownLatch allTasksFinished = new CountDownLatch(concurrentTasks);

        for (int i = 0; i < concurrentTasks; i++) {
            executor.execute(() -> {
                try {
                    // Only returns once all `concurrentTasks` threads have reached this line
                    // simultaneously -- impossible unless they're genuinely running at once.
                    barrier.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException | BrokenBarrierException | TimeoutException e) {
                    throw new RuntimeException(e);
                } finally {
                    allTasksFinished.countDown();
                }
            });
        }

        boolean completedInTime = allTasksFinished.await(5, TimeUnit.SECONDS);

        assertThat(completedInTime)
                .as("All %d tasks should complete once they meet at the barrier together -- "
                        + "if this times out, the executor isn't actually running them concurrently "
                        + "(e.g. corePoolSize got reduced below %d)", concurrentTasks, concurrentTasks)
                .isTrue();
    }
}
