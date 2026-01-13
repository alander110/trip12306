package com.alander.trip12306.ticketservice.mq.consumer;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import com.alander.trip12306.ticketservice.common.constant.TicketRocketMQConstant;
import com.alander.trip12306.ticketservice.common.enums.PurchaseTicketsErrorCodeEnum;
import com.alander.trip12306.ticketservice.dao.entity.OrderTrackingDO;
import com.alander.trip12306.ticketservice.dao.mapper.OrderTrackingMapper;
import com.alander.trip12306.ticketservice.dto.req.PurchaseTicketReqDTO;
import com.alander.trip12306.ticketservice.dto.resp.TicketPurchaseRespDTO;
import com.alander.trip12306.ticketservice.mq.domain.MessageWrapper;
import com.alander.trip12306.ticketservice.mq.event.PurchaseTicketsEvent;
import com.alander.trip12306.ticketservice.service.TicketService;
import com.alander.trip12306.convention.exception.ServiceException;
import com.alander.trip12306.idempotent.annotation.Idempotent;
import com.alander.trip12306.idempotent.enums.IdempotentSceneEnum;
import com.alander.trip12306.idempotent.enums.IdempotentTypeEnum;
import com.alander.trip12306.user.core.UserContext;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 用户异步购票消费者
*/
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = TicketRocketMQConstant.PURCHASE_TICKET_ASYNC_TOPIC_KEY,
        consumerGroup = TicketRocketMQConstant.PURCHASE_TICKET_ASYNC_CG_KEY
)
public class PurchaseTicketsConsumer implements RocketMQListener<MessageWrapper<PurchaseTicketsEvent>> {

    private final TicketService ticketService;
    private final OrderTrackingMapper orderTrackingMapper;

    @Idempotent(
            uniqueKeyPrefix = "trip12306-ticket:purchase_tickets_v3:",
            key = "#messageWrapper.getKeys()",
            type = IdempotentTypeEnum.SPEL,
            scene = IdempotentSceneEnum.MQ,
            keyTimeout = 7200L
    )
    @Override
    public void onMessage(MessageWrapper<PurchaseTicketsEvent> messageWrapper) {
        log.info("[用户异步购票] 开始消费：{}", JSON.toJSONString(messageWrapper));

        // 获取用户购票参数
        PurchaseTicketsEvent purchaseTicketsEvent = messageWrapper.getMessage();
        PurchaseTicketReqDTO originalRequestParam = purchaseTicketsEvent.getOriginalRequestParam();
        String orderTrackingId = purchaseTicketsEvent.getOrderTrackingId();

        // 发起用户创建订单
        TicketPurchaseRespDTO ticketPurchaseRespDTO = null;
        boolean insufficientTrainTicketsFlag = false;
        String username = purchaseTicketsEvent.getUserInfo().getUsername();
        try {
            UserContext.setUser(purchaseTicketsEvent.getUserInfo());
            ticketPurchaseRespDTO = ticketService.executePurchaseTickets(originalRequestParam);
        } catch (ServiceException se) {
            // 错误可能有两种，其中一个是列车无余票，另外可能是发起购票失败，比如订单服务宕机、Redis 宕机等极端情况
            insufficientTrainTicketsFlag = Objects.equals(se.getErrorCode(), PurchaseTicketsErrorCodeEnum.INSUFFICIENT_TRAIN_TICKETS.code());
        } finally {
            UserContext.removeUser();
        }

        // 根据用户是否创建订单成功构建订单追踪实体
        OrderTrackingDO orderTrackingDO = OrderTrackingDO.builder()
                .id(Long.parseLong(orderTrackingId))
                .username(username)
                .orderSn(ticketPurchaseRespDTO != null ? ticketPurchaseRespDTO.getOrderSn() : null)
                // 状态 0：请求下单成功 1：列车与余票不足 2：购票请求失败
                .status(ticketPurchaseRespDTO != null ? 0 : insufficientTrainTicketsFlag ? 1 : 2)
                .build();

        // 新增订单追踪记录，方便为购票 v3 接口异步下单后的结果提供查询能力
        orderTrackingMapper.insert(orderTrackingDO);
    }
}
