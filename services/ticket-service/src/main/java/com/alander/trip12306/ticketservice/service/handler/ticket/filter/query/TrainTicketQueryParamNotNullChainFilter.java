package com.alander.trip12306.ticketservice.service.handler.ticket.filter.query;

import com.alander.trip12306.convention.exception.ClientException;
import com.alander.trip12306.ticketservice.dto.req.TicketPageQueryReqDTO;
import jodd.util.StringUtil;
import org.springframework.stereotype.Component;

/**
 * 查询列车车票流程过滤器之验证数据是否为空或空的字符串
 */
@Component
public class TrainTicketQueryParamNotNullChainFilter implements TrainTicketQueryChainFilter<TicketPageQueryReqDTO> {
    @Override
    public void handler(TicketPageQueryReqDTO requestParam) {
        if(StringUtil.isBlank(requestParam.getFromStation())){
            throw new ClientException("出发地不能为空");
        }
        if(StringUtil.isBlank(requestParam.getToStation())){
            throw new ClientException("目的地不能为空");
        }
        if(requestParam.getDepartureDate() == null){
            throw new ClientException("出发日期不能为空");
        }
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
