package com.alander.trip12306.ticketservice.common.enums;

import com.alander.trip12306.convention.errorcode.IErrorCode;
import lombok.AllArgsConstructor;

/**
 * 用户购票错误码枚举
*/
@AllArgsConstructor
public enum PurchaseTicketsErrorCodeEnum implements IErrorCode {

    INSUFFICIENT_TRAIN_TICKETS("B010001", "站点余票不足，请尝试更换座位类型或选择其它站点");

    /**
     * 错误码
     */
    private final String code;

    /**
     * 错误提示消息
     */
    private final String message;

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
