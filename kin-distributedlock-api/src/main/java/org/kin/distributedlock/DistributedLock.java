package org.kin.distributedlock;

import java.util.LinkedList;

/**
 * Created by 健勤 on 2017/5/25.
 */
public interface DistributedLock {
    /** 当前线程已获取的分布式锁名 */
    ThreadLocal<LinkedList<String>> OWNED_LOCK = ThreadLocal.withInitial(LinkedList::new);

    /**
     * 尝试获取分布式锁
     * @param name  分布式锁名
     * @param expireTime    超时时间, 毫秒
     * @param sleepTime     请求分布式锁失败后等待时间, 即两次请求的间隔时间, 毫秒
     * @return  是否获取分布式锁成功
     */
    boolean lock(String name, long expireTime, long sleepTime);

    /**
     * 尝试获取分布式锁
     * @param name  分布式锁名
     * @param expireTime    超时时间, 毫秒
     * @return  是否获取分布式锁成功
     */
    default boolean lock(String name, long expireTime){
        return lock(name, expireTime, 50);
    }

    /**
     * 释放分布式锁
     * @param name  分布式锁名
     */
    void unlock(String name);

    /**
     * 安全释放分布式锁, 会判断当前线程是获得分布式锁线程
     * @param name  分布式锁名
     */
    void unlockSafely(String name);

    /**
     * 销毁, 释放资源
     */
    void destroy();
}
