package com.alander.trip12306.ticketservice.dto.resp;

import lombok.Data;

/**
 * 订单追踪返回详情实体
*/
@Data
public class OrderTrackingRespDTO {

    /**
     * 订单号
     */
    private String orderSn;

    /**
     * 状态 0：请求下单成功 1：列车与余票不足 2：购票请求失败
     */
    private Integer status;
}
