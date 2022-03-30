package org.kin.distributelock;

import java.util.concurrent.locks.Lock;

/**
 * Created by 健勤 on 2017/5/25.
 */
public interface DistributeLock extends Lock {
    /**
     * 初始化
     */
    void init();

    /**
     * 销毁, 释放资源
     */
    void destroy();
}
