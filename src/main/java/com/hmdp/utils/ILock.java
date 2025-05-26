package com.hmdp.utils;

/**
 * @Description: TODO
 * @Author: Tomatos
 * @Date: 2025/5/23 14:41
 */
public interface ILock {
    boolean tryLock();

    void unlock();
}
