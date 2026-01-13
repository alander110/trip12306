package com.alander.trip12306.ticketservice.canal;

import cn.hutool.core.collection.CollUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.alander.trip12306.ticketservice.common.enums.CanalExecuteStrategyMarkEnum;
import com.alander.trip12306.ticketservice.mq.event.CanalBinlogEvent;
import com.alander.trip12306.ticketservice.remote.TicketOrderRemoteService;
import com.alander.trip12306.ticketservice.remote.dto.TicketOrderDetailRespDTO;
import com.alander.trip12306.ticketservice.remote.dto.TicketOrderPassengerDetailRespDTO;
import com.alander.trip12306.ticketservice.service.SeatService;
import com.alander.trip12306.ticketservice.service.handler.ticket.dto.TrainPurchaseTicketRespDTO;
import com.alander.trip12306.ticketservice.service.handler.ticket.tokenbucket.TicketAvailabilityTokenBucket;
import com.alander.trip12306.common.toolkit.BeanUtil;
import com.alander.trip12306.convention.result.Result;
import com.alander.trip12306.designpattern.strategy.AbstractExecuteStrategy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 订单关闭或取消后置处理组件
*/
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCloseCacheAndTokenUpdateHandler implements AbstractExecuteStrategy<CanalBinlogEvent, Void> {

    private final TicketOrderRemoteService ticketOrderRemoteService;
    private final SeatService seatService;
    private final TicketAvailabilityTokenBucket ticketAvailabilityTokenBucket;

    @Override
    public void execute(CanalBinlogEvent message) {
        List<Map<String, Object>> messageDataList = message.getData().stream()
                .filter(each -> each.get("status") != null)
                .filter(each -> Objects.equals(each.get("status"), "30"))
                .toList();
        if (CollUtil.isEmpty(messageDataList)) {
            return;
        }
        for (Map<String, Object> each : messageDataList) {
            Result<TicketOrderDetailRespDTO> orderDetailResult = ticketOrderRemoteService.queryTicketOrderByOrderSn(each.get("order_sn").toString());
            TicketOrderDetailRespDTO orderDetailResultData = orderDetailResult.getData();
            if (orderDetailResult.isSuccess() && orderDetailResultData != null) {
                String trainId = String.valueOf(orderDetailResultData.getTrainId());
                List<TicketOrderPassengerDetailRespDTO> passengerDetails = orderDetailResultData.getPassengerDetails();
                seatService.unlock(trainId, orderDetailResultData.getDeparture(), orderDetailResultData.getArrival(), BeanUtil.convert(passengerDetails, TrainPurchaseTicketRespDTO.class));
                ticketAvailabilityTokenBucket.rollbackInBucket(orderDetailResultData);
            }
        }
    }

    @Override
    public String mark() {
        return CanalExecuteStrategyMarkEnum.T_ORDER.getActualTable();
    }

    @Override
    public String patternMatchMark() {
        return CanalExecuteStrategyMarkEnum.T_ORDER.getPatternMatchTable();
    }
}
