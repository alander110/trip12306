package com.alander.trip12306.designpattern.builder;

import java.io.Serializable;

/**
 * Builder 模式抽象接口
 * @param <T>
 */
public interface Builder<T> extends Serializable {

    /**
     * 构建对象
     * @return 构建后的对象
     */
    T build();
}
