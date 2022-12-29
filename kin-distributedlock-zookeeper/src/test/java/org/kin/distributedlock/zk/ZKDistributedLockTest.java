package org.kin.distributedlock.zk;

import org.kin.distributedlock.DistributedLock;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by 健勤 on 2017/5/25.
 */
public class ZKDistributedLockTest {
    /** 测试分布式锁名 */
    private static final String LOCK_NAME = "self-increment";
    /** 自增循环次数 */
    private static final int LOOP = 10;
    /** 统计counter */
    private static Long counter = 0L;

    public static final DistributedLock lock = new ZKDistributedLock();

    public static void main(String[] args) throws InterruptedException {
        int parallel = 50;
        ExecutorService executor = Executors.newFixedThreadPool(parallel);
        CountDownLatch latch = new CountDownLatch(parallel);
        for (int i = 0; i < parallel; i++) {
            executor.submit(new SelfIncrementThread(latch));
        }
        System.out.println("starting...");
        while (latch.getCount() > 0) {
            System.out.println("当前Counter = " + counter);
            Thread.sleep(20000);
        }
        latch.await();
        System.out.println("self increment end!");
        System.out.println(counter);
        System.out.println(counter == parallel * LOOP);
        executor.shutdown();
        lock.unlock(LOCK_NAME);
        lock.destroy();
    }

    private static class SelfIncrementThread implements Runnable {
        private final CountDownLatch latch;

        public SelfIncrementThread(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void run() {
            Random random = new Random();
            for (int i = 0; i < LOOP; i++) {
                while(!lock.lock(LOCK_NAME, 200)){

                }
                counter++;
                try {
                    Thread.sleep(random.nextInt(500));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                lock.unlock(LOCK_NAME);
            }
            latch.countDown();
        }
    }
}
