package com.alander.trip12306.ticketservice.service.Impl;


import com.alander.trip12306.cache.DistributedCache;
import com.alander.trip12306.common.toolkit.BeanUtil;
import com.alander.trip12306.ticketservice.dao.entity.TrainStationDO;
import com.alander.trip12306.ticketservice.dao.mapper.TrainStationMapper;
import com.alander.trip12306.ticketservice.dto.domain.RouteDTO;
import com.alander.trip12306.ticketservice.dto.resp.TrainStationQueryRespDTO;
import com.alander.trip12306.ticketservice.service.TrainStationService;
import com.alander.trip12306.ticketservice.toolkit.StationCalculateUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import static com.alander.trip12306.ticketservice.common.constant.RedisKeyConstant.LIST_TAKEOUT_TRAIN_STATION_ROUTE_KEY;
import static com.alander.trip12306.ticketservice.common.constant.RedisKeyConstant.LIST_TRAIN_STATION_ROUTE_KEY;

/**
 * 列车站点关系接口实现层
 */
@Service
@RequiredArgsConstructor
public class TrainStationServiceImpl implements TrainStationService {

    private final TrainStationMapper trainStationMapper;
    private final DistributedCache distributedCache;

    @Override
    public List<TrainStationQueryRespDTO> listTrainStationQuery(String trainId) {
        LambdaQueryWrapper<TrainStationDO> queryWrapper = Wrappers.lambdaQuery(TrainStationDO.class)
                .eq(TrainStationDO::getTrainId, trainId);
        List<TrainStationDO> trainStationDOList = trainStationMapper.selectList(queryWrapper);
        return BeanUtil.convert(trainStationDOList, TrainStationQueryRespDTO.class);
    }

    @Override
    public List<RouteDTO> listTrainStationRoute(String trainId, String departure, String arrival) {
        String cacheKey = String.format(LIST_TRAIN_STATION_ROUTE_KEY, trainId, departure, arrival);
        String resultJSONStr = distributedCache.safeGet(cacheKey,
                String.class,
                () -> {
                    LambdaQueryWrapper<TrainStationDO> queryWrapper = Wrappers.lambdaQuery(TrainStationDO.class)
                            .eq(TrainStationDO::getTrainId, trainId)
                            .select(TrainStationDO::getDeparture);
                    List<TrainStationDO> trainStationDOList = trainStationMapper.selectList(queryWrapper);
                    List<String> trainStationAllList = trainStationDOList.stream().map(TrainStationDO::getDeparture).collect(Collectors.toList());
                    return JSON.toJSONString(StationCalculateUtil.throughStation(trainStationAllList, departure, arrival));
                },
                1800000L);
        return JSON.parseArray(resultJSONStr, RouteDTO.class);
    }

    @Override
    public List<RouteDTO> listTakeoutTrainStationRoute(String trainId, String departure, String arrival) {
        String cacheKey = String.format(LIST_TAKEOUT_TRAIN_STATION_ROUTE_KEY, trainId, departure, arrival);
        String resultJSONStr = distributedCache.safeGet(cacheKey,
                String.class,
                () -> {
                    LambdaQueryWrapper<TrainStationDO> queryWrapper = Wrappers.lambdaQuery(TrainStationDO.class)
                            .eq(TrainStationDO::getTrainId, trainId)
                            .select(TrainStationDO::getDeparture);
                    List<TrainStationDO> trainStationDOList = trainStationMapper.selectList(queryWrapper);
                    List<String> trainStationAllList = trainStationDOList.stream().map(TrainStationDO::getDeparture).collect(Collectors.toList());
                    return JSON.toJSONString(StationCalculateUtil.takeoutStation(trainStationAllList, departure, arrival));
                },
                1800000L);
        return JSON.parseArray(resultJSONStr, RouteDTO.class);
    }
}
