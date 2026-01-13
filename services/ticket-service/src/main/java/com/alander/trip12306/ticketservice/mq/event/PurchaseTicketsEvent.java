package com.alander.trip12306.ticketservice.mq.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.alander.trip12306.ticketservice.dto.req.PurchaseTicketReqDTO;
import com.alander.trip12306.user.core.UserInfoDTO;

/**
 * 用户购票事件
*/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseTicketsEvent {

    /**
     * 订单追踪 ID，用户下单 v3 接口专属
     */
    private String orderTrackingId;

    /**
     * 购票方法原始请求入参
     */
    private PurchaseTicketReqDTO originalRequestParam;

    /**
     * 用户上下文信息
     */
    private UserInfoDTO userInfo;
}
