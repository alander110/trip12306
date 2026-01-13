package com.alander.trip12306.payservice.convert;

import com.alander.trip12306.payservice.common.enums.PayChannelEnum;
import com.alander.trip12306.payservice.dto.PayCallbackCommand;
import com.alander.trip12306.payservice.dto.base.AliPayCallbackRequest;
import com.alander.trip12306.payservice.dto.base.PayCallbackRequest;
import com.alander.trip12306.common.toolkit.BeanUtil;

import java.util.Objects;

/**
 * 支付回调请求入参转换器
*/
public final class PayCallbackRequestConvert {

    /**
     * {@link PayCallbackCommand} to {@link PayCallbackRequest}
     *
     * @param payCallbackCommand 支付回调请求参数
     * @return {@link PayCallbackRequest}
     */
    public static PayCallbackRequest command2PayCallbackRequest(PayCallbackCommand payCallbackCommand) {
        PayCallbackRequest payCallbackRequest = null;
        if (Objects.equals(payCallbackCommand.getChannel(), PayChannelEnum.ALI_PAY.getCode())) {
            payCallbackRequest = BeanUtil.convert(payCallbackCommand, AliPayCallbackRequest.class);
            ((AliPayCallbackRequest) payCallbackRequest).setOrderRequestId(payCallbackCommand.getOrderRequestId());
        }
        return payCallbackRequest;
    }
}
