package org.kin.distributedlock;

/**
 * @author huangjianqin
 * @date 2022/12/28
 */
public abstract class AbstractDistributedLock implements DistributedLock {
    /**
     * 尝试获取分布式锁
     * @param name  分布式锁名
     * @param expireTime    超时时间, 毫秒
     * @param sleepTime     请求分布式锁失败后等待时间, 即两次请求的间隔时间, 毫秒
     * @return  是否获取分布式锁成功
     */
    protected abstract boolean lock0(String name, long expireTime, long sleepTime);

    /**
     * 释放分布式锁
     * @param name  分布式锁名
     */
    protected abstract void unlock0(String name);

    @Override
    public final boolean lock(String name, long expireTime, long sleepTime) {
        if (isAlreadyLocked(name)) {
            //已获得分布式锁, 可重入
            return true;
        }
        return lock0(name, expireTime, sleepTime);
    }

    @Override
    public final void unlock(String name) {
        unlock0(name);
        onUnlock(name);
    }

    @Override
    public final void unlockSafely(String name) {
        if (!isAlreadyLocked(name)) {
            throw new IllegalStateException(String.format("can not unlock, due to '%s' doesn't have lock '%s'", Thread.currentThread().getName(), name));
        }
        unlock(name);
    }

    /**
     * 判断是否已经拥有分布式锁
     * @param name  分布式锁名
     * @return  是否已经拥有分布式锁
     */
    protected boolean isAlreadyLocked(String name){
        return OWNED_LOCK.get().contains(name);
    }

    /**
     * 获得分布式锁成功后, 该线程缓存分布式锁名
     * @param name  分布式锁名
     */
    protected void onLock(String name){
        OWNED_LOCK.get().add(name);
    }

    /**
     * 释放分布式锁成功后, 该线程移除分布式锁名缓存
     * @param name  分布式锁名
     */
    protected void onUnlock(String name){
        OWNED_LOCK.get().remove(name);
    }
}
