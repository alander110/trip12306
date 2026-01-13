package com.alander.trip12306.payservice.handler.base;

import com.alander.trip12306.payservice.dto.base.PayCallbackRequest;

/**
 * 抽象支付回调组件
*/
public abstract class AbstractPayCallbackHandler {

    /**
     * 支付回调抽象接口
     *
     * @param payCallbackRequest 支付回调请求参数
     */
    public abstract void callback(PayCallbackRequest payCallbackRequest);
}
