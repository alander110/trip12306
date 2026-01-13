package com.alander.trip12306.ticketservice.service.Impl;

import com.alander.trip12306.cache.DistributedCache;
import com.alander.trip12306.ticketservice.dao.entity.TrainStationRelationDO;
import com.alander.trip12306.ticketservice.dao.mapper.TrainStationRelationMapper;
import com.alander.trip12306.ticketservice.service.TrainStationRelationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.alander.trip12306.ticketservice.common.constant.RedisKeyConstant.TRAIN_STATION_RELATION_KEY;

@Service
@RequiredArgsConstructor
public class TrainStationRelationServiceImpl implements TrainStationRelationService {

    private final TrainStationRelationMapper trainStationRelationMapper;
    private final DistributedCache distributedCache;

    @Override
    public TrainStationRelationDO findRelation(String trainId, String departure, String arrival) {
        return distributedCache.safeGet(String.format(TRAIN_STATION_RELATION_KEY, trainId, departure, arrival),
                TrainStationRelationDO.class,
                () -> {
                    LambdaQueryWrapper<TrainStationRelationDO> queryWrapper = Wrappers.lambdaQuery(TrainStationRelationDO.class)
                            .eq(TrainStationRelationDO::getTrainId, trainId)
                            .eq(TrainStationRelationDO::getDeparture, departure)
                            .eq(TrainStationRelationDO::getArrival, arrival);
                    TrainStationRelationDO trainStationRelationDO = trainStationRelationMapper.selectOne(queryWrapper);
                    return trainStationRelationDO;
                },
                1800000L);
    }
}
