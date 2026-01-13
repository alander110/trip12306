package com.alander.trip12306.ticketservice.service.handler.ticket.filter.purchase;

import com.alander.trip12306.designpattern.chain.AbstractChainHandler;
import com.alander.trip12306.ticketservice.common.enums.TicketChainMarkEnum;
import com.alander.trip12306.ticketservice.dto.req.PurchaseTicketReqDTO;

/**
 * 列车购买车票过滤器
 */
public interface TrainPurchaseTicketChainFilter<T extends PurchaseTicketReqDTO> extends AbstractChainHandler<PurchaseTicketReqDTO> {

    @Override
    default String mark(){
        return TicketChainMarkEnum.TRAIN_PURCHASE_TICKET_FILTER.name();
    }
}
