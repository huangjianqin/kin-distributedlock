package org.kin.distributedlock.zk;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.kin.distributedlock.AbstractDistributedLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * 基于zk实现分布式超时锁, 并支持进程故障会自动释放分布式锁(自身特性提供)
 * Created by 健勤 on 2017/5/27.
 */
public class ZKDistributedLock extends AbstractDistributedLock {
    private static final Logger log = LoggerFactory.getLogger(ZKDistributedLock.class);
    /** zk客户端 会话超时时间, 5分钟, 减少网络波动(session断开)对临时节点移除的影响 */
    private static final int SESSION_TIMEOUT = (int) TimeUnit.MINUTES.toMillis(5);
    /** 锁节点的父节点路径名 */
    private static final String ZK_DL_PARENT = "kin-distributedlock";
    /** zk客户端 */
    private final CuratorFramework client;

    public ZKDistributedLock() {
        this("localhost:2181");
    }

    public ZKDistributedLock(String address) {
        //RetryNTimes  RetryOneTime  RetryForever  RetryUntilElapsed
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(200, 5);
        client = CuratorFrameworkFactory
                .builder()
                .connectString(address)
                .sessionTimeoutMs(SESSION_TIMEOUT)
                .retryPolicy(retryPolicy)
                //根节点会多出一个以命名空间名称所命名的节点
                .namespace(ZK_DL_PARENT)
                .build();
        client.getConnectionStateListenable().addListener((curatorFramework, connectionState) -> {
            if (ConnectionState.CONNECTED.equals(connectionState)) {
                log.info("zookeeper connect created");
            } else if (ConnectionState.RECONNECTED.equals(connectionState)) {
                log.info("zookeeper reconnected");
            } else if (ConnectionState.LOST.equals(connectionState)) {
                log.warn("disconnect to zookeeper server");
            } else if (ConnectionState.SUSPENDED.equals(connectionState)) {
                log.error("connect to zookeeper server timeout '{}'", SESSION_TIMEOUT);
            }
        });

        client.start();
    }

    @Override
    protected boolean lock0(String name, long expireTime, long sleepTime) {
        String path = "/".concat(ZK_DL_PARENT).concat("/").concat(name);
        long start = System.currentTimeMillis();
        while (true) {
            //自旋一直尝试获得分布式锁
            long now = System.currentTimeMillis();
            if (now - start >= expireTime) {
                //超时
                break;
            }

            try {
                //如果节点已存在(也就是有进程持有锁)将抛出异常
                client.create()
                        .creatingParentsIfNeeded()
                        //会话临时节点
                        .withMode(CreateMode.EPHEMERAL)
                        .withACL(ZooDefs.Ids.OPEN_ACL_UNSAFE)
                        .forPath(path, null);
                log.debug(Thread.currentThread().getName() + String.format(" get distributed lock '%s'", name));
                return true;
            } catch (Exception e) {
                if (e instanceof KeeperException.NodeExistsException) {
                    //有进程持有锁,重新尝试获得锁
                    log.debug(String.format("distributed lock '%s' has allocated, please wait", name));
                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException e1) {
                        //do nothing
                    }
                } else {
                    log.error("", e);
                    break;
                }
            }
        }

        return false;
    }

    @Override
    protected void unlock0(String name) {
        log.debug(Thread.currentThread().getName() + String.format(" release distributed lock '%s'", name));
        String path = "/".concat(ZK_DL_PARENT).concat("/").concat(name);
        //删除zk path
        try {
            client.delete()
                    .guaranteed()
                    .forPath(path);
        } catch (InterruptedException e) {
            log.warn(Thread.currentThread().getName() + " is interrupted when operating");
        } catch (Exception e) {
            log.error("", e);
            if (e instanceof KeeperException.NoNodeException) {
                destroy();
            }
        }
    }

    /**
     * 关闭zk客户端
     */
    @Override
    public void destroy() {
        client.close();
    }
}
