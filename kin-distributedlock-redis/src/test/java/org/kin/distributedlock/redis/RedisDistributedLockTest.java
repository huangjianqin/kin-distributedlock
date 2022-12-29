package org.kin.distributedlock.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import org.kin.distributedlock.DistributedLock;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by 健勤 on 2017/5/25.
 */
public class RedisDistributedLockTest {
    /** 测试分布式锁名 */
    private static final String LOCK_NAME = "self-increment";
    /** 自增循环次数 */
    private static final int LOOP = 10;
    /** 统计counter */
    private static volatile Long counter = 0L;

    private static final DistributedLock lock;

    static {
        RedisURI redisUri = RedisURI.builder()
                .withHost("127.0.0.1")
                .withPort(6379)
                .withTimeout(Duration.of(10, ChronoUnit.SECONDS))
                .build();
        RedisClient redisClient = RedisClient.create(redisUri);
        lock = new RedisDistributedLock(redisClient.connect());
    }

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
