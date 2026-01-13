package com.alander.trip12306.ticketservice.service.handler.ticket.filter.query;

import com.alander.trip12306.designpattern.chain.AbstractChainHandler;
import com.alander.trip12306.ticketservice.common.enums.TicketChainMarkEnum;
import com.alander.trip12306.ticketservice.dto.req.TicketPageQueryReqDTO;

/**
 * 车票查询过滤器
 */
public interface TrainTicketQueryChainFilter<T extends TicketPageQueryReqDTO>  extends AbstractChainHandler<TicketPageQueryReqDTO> {

    @Override
    default String mark(){
        return TicketChainMarkEnum.TRAIN_QUERY_FILTER.name();
    }
}
