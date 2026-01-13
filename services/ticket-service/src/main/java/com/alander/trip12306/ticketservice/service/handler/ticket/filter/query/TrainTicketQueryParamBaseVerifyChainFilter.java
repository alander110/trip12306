package com.alander.trip12306.ticketservice.service.handler.ticket.filter.query;

import com.alander.trip12306.convention.exception.ClientException;
import com.alander.trip12306.ticketservice.dto.req.TicketPageQueryReqDTO;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/**
 * 查询列车车票流程过滤器之基础数据验证
 */
@Component
public class TrainTicketQueryParamBaseVerifyChainFilter implements TrainTicketQueryChainFilter<TicketPageQueryReqDTO> {
    @Override
    public void handler(TicketPageQueryReqDTO requestParam) {
        if(requestParam.getDepartureDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate().isBefore(LocalDate.now())){
            throw new ClientException("出发日期不能早于当前日期");
        }
        if(Objects.equals(requestParam.getFromStation(),requestParam.getToStation())){
            throw new ClientException("出发地不能与目的地相同");
        }
    }

    @Override
    public int getOrder() {
        return 10;
    }
}
