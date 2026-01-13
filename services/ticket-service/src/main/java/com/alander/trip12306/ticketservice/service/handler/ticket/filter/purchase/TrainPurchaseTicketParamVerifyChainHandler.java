package com.alander.trip12306.ticketservice.service.handler.ticket.filter.purchase;

import cn.hutool.core.collection.CollUtil;
import com.alander.trip12306.cache.DistributedCache;
import com.alander.trip12306.common.toolkit.EnvironmentUtil;
import com.alander.trip12306.convention.exception.ClientException;
import com.alander.trip12306.ticketservice.dao.entity.TrainDO;
import com.alander.trip12306.ticketservice.dao.entity.TrainStationDO;
import com.alander.trip12306.ticketservice.dao.mapper.TrainMapper;
import com.alander.trip12306.ticketservice.dao.mapper.TrainStationMapper;
import com.alander.trip12306.ticketservice.dto.req.PurchaseTicketReqDTO;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.alander.trip12306.ticketservice.common.constant.Index12306Constant.ADVANCE_TICKET_DAY;
import static com.alander.trip12306.ticketservice.common.constant.RedisKeyConstant.TRAIN_INFO;
import static com.alander.trip12306.ticketservice.common.constant.RedisKeyConstant.TRAIN_STATION_STOPOVER_DETAIL;

/**
 * 购票流程过滤器之验证参数是否有效
 * 验证参数有效这个流程会大量交互缓存，为了优化性能需要使用 Lua。为了方便大家理解流程，这里使用多次调用缓存
 */
@Component
@RequiredArgsConstructor
public class TrainPurchaseTicketParamVerifyChainHandler implements TrainPurchaseTicketChainFilter<PurchaseTicketReqDTO> {

    private final TrainMapper trainMapper;
    private final TrainStationMapper trainStationMapper;
    private final DistributedCache distributedCache;

    @Override
    public void handler(PurchaseTicketReqDTO requestParam) {
        // 从分布式缓存获取车次信息，如果缓存不存在则从数据库查询并缓存
        TrainDO trainDO = distributedCache.safeGet(
                TRAIN_INFO + requestParam.getTrainId(),
                TrainDO.class,
                () -> trainMapper.selectById(requestParam.getTrainId()),
                ADVANCE_TICKET_DAY,
                TimeUnit.DAYS
        );
        if (Objects.isNull(trainDO)) {
            throw new ClientException("请检查车次是否存在");
        }
        if (!EnvironmentUtil.isDevEnvironment()) {
            // 查询车次是否已经发售
            if (new Date().before(trainDO.getSaleTime())) {
                throw new ClientException("列车车次暂未发售");
            }
            // 查询车次是否在有效期内
            if (new Date().after(trainDO.getDepartureTime())) {
                throw new ClientException("列车车次已出发禁止购票");
            }
        }
        // 从分布式缓存获取车次站点停靠详情，如果缓存不存在则从数据库查询并缓存
        String trainStationStopoverDetailStr = distributedCache.safeGet(
                TRAIN_STATION_STOPOVER_DETAIL + requestParam.getTrainId(),
                String.class,
                () -> {
                    List<TrainStationDO> actualTrainStationList = trainStationMapper.selectList(Wrappers.lambdaQuery(TrainStationDO.class)
                            .eq(TrainStationDO::getTrainId, requestParam.getTrainId())
                            .select(TrainStationDO::getDeparture));
                    return CollUtil.isNotEmpty(actualTrainStationList) ? JSON.toJSONString(actualTrainStationList) : null;
                },
                ADVANCE_TICKET_DAY,
                TimeUnit.DAYS
        );
        List<TrainStationDO> trainDOList = JSON.parseArray(trainStationStopoverDetailStr, TrainStationDO.class);
        // 验证出发站和到达站是否在车次的运行路径中
        boolean validateStation = validateStation(trainDOList.stream().map(TrainStationDO::getDeparture).toList(),
                requestParam.getDeparture(),
                requestParam.getArrival());
        if (!validateStation) {
            throw new ClientException("列车车站数据错误");
        }
    }


    @Override
    public int getOrder() {
        return 10;
    }

    public boolean validateStation(List<String> stationDOList, String startStation, String endStation) {
        int index1 = stationDOList.indexOf(startStation);
        int index2 = stationDOList.indexOf(endStation);
        if (index1 == -1 || index2 == -1) {
            return false;
        }
        return index2 >= index1;
    }
}
