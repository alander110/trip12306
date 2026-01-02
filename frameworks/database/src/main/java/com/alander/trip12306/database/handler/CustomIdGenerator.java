package com.alander.trip12306.database.handler;

import com.alander.trip12306.distributedid.toolkit.SnowflakeIdUtil;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;

/**
 * 自定义雪花算法生成器
*/
public class CustomIdGenerator implements IdentifierGenerator {

    @Override
    public Number nextId(Object entity) {
        return SnowflakeIdUtil.nextId();
    }
}
