package com.qsx.appoint.support;

import java.util.List;
import java.util.concurrent.*;

/** 并发测试保留原任务和屏障；异常退出时取消任务，避免ExecutorService.close无界等待。 */
public final class TestExecutor extends AbstractExecutorService implements AutoCloseable {
    private final ExecutorService delegate;
    public TestExecutor(int threads) { delegate = Executors.newFixedThreadPool(threads); }
    @Override public void shutdown() { delegate.shutdown(); }
    @Override public List<Runnable> shutdownNow() { return delegate.shutdownNow(); }
    @Override public boolean isShutdown() { return delegate.isShutdown(); }
    @Override public boolean isTerminated() { return delegate.isTerminated(); }
    @Override public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }
    @Override public void execute(Runnable task) { delegate.execute(task); }
    @Override public void close() {
        delegate.shutdownNow();
        try {
            if (!delegate.awaitTermination(15, TimeUnit.SECONDS)) throw new AssertionError("并发测试线程池15秒内未结束");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError("等待并发测试线程结束时被中断", error);
        }
    }
}
