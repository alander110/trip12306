package com.alander.trip12306.ticketservice.service.handler.ticket.filter.refund;


import com.alander.trip12306.designpattern.chain.AbstractChainHandler;
import com.alander.trip12306.ticketservice.common.enums.TicketChainMarkEnum;
import com.alander.trip12306.ticketservice.dto.req.RefundTicketReqDTO;

/**
 * 列车车票退款过滤器
*/
public interface TrainRefundTicketChainFilter<T extends RefundTicketReqDTO> extends AbstractChainHandler<RefundTicketReqDTO> {

    @Override
    default String mark() {
        return TicketChainMarkEnum.TRAIN_REFUND_TICKET_FILTER.name();
    }
}
