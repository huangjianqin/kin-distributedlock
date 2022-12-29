package org.kin.distributedlock.redis;

import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.kin.distributedlock.AbstractDistributedLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于redis实现分布式超时锁, 并支持进程故障会自动释放锁(基于超时实现)
 * 会负责释放redis连接
 * Created by 健勤 on 2017/5/23.
 */
public class RedisDistributedLock extends AbstractDistributedLock {
    private static final Logger log = LoggerFactory.getLogger(RedisDistributedLock.class);
    /** redis set成功返回值 */
    private static final String REDIS_SET_REPLY = "OK";

    /** redis连接 */
    private final StatefulRedisConnection<String, String> connection;
    /** redis同步命令远程执行抽象 */
    private final RedisCommands<String, String> redisCommands;

    public RedisDistributedLock(StatefulRedisConnection<String, String> connection) {
        this.connection = connection;
        this.redisCommands = connection.sync();
    }

    @Override
    protected boolean lock0(String name, long expireTime, long sleepTime) {
        long start = System.currentTimeMillis();
        while (true) {
            //自旋一直尝试获得分布式锁
            long now = System.currentTimeMillis();
            if (now - start >= expireTime) {
                //超时
                break;
            }
            Thread currentThread = Thread.currentThread();

            //setnxpx
            if (REDIS_SET_REPLY.equalsIgnoreCase(redisCommands.set(name, now + "," + expireTime, new SetArgs().px(expireTime).nx()))) {
                log.debug(currentThread.getName() + String.format(" get distributed lock '%s'", name));
                onLock(name);
                return true;
            }

            try {
                //睡眠一会再重试
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                //do nothing
            }
        }

        return false;
    }

    @Override
    protected void unlock0(String name) {
        log.debug(Thread.currentThread().getName() + String.format(" release distributed lock '%s'", name));

        redisCommands.del(name);
    }

    /**
     * 销毁分布式锁
     * 关闭redis客户端
     */
    @Override
    public void destroy() {
        connection.close();
    }
}
