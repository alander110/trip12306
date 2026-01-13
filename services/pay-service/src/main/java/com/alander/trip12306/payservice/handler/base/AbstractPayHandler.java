package com.alander.trip12306.payservice.handler.base;

import com.alander.trip12306.payservice.dto.base.PayRequest;
import com.alander.trip12306.payservice.dto.base.PayResponse;

/**
 * 抽象支付组件
*/
public abstract class AbstractPayHandler {

    /**
     * 支付抽象接口
     *
     * @param payRequest 支付请求参数
     * @return 支付响应参数
     */
    public abstract PayResponse pay(PayRequest payRequest);
}
