package com.alander.trip12306.userservice.common.constant;

/**
 * Redis Key 定义常量类
 */
public final class RedisKeyConstant {

    /**
     * 用户注册锁，Key Prefix + 用户名
     */
    public static final String LOCK_USER_REGISTER_KEY = "trip12306-user-service:lock:user-register:";

    /**
     * 用户注销锁，Key Prefix + 用户名
     */
    public static final String LOCK_USER_DELETION_LEY = "trip12306-user-service:lock:user-deletion:";

    /**
     * 用户注册可复用用户名分片，Key Prefix + Idx
     */
    public static final String USER_REGISTER_REUSE_SHARDING_KEY = "trip12306-user-service:user-reuse:";

    /**
     * 用户乘车人列表，Key Prefix + 用户名
     */
    public static final String USER_PASSENGER_LIST_KEY = "trip12306-user-service:user-passenger-list:";

    /**
     * 用户注销次数，Key prefix + 证件类型_证件号
     */
    public static final String USER_DELETION_COUNT_KEY = "trip12306-user-service:user-deletion-count:%d-%s";
}

