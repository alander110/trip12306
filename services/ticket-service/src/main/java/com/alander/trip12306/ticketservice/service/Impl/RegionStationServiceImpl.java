package com.alander.trip12306.ticketservice.service.Impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alander.trip12306.cache.DistributedCache;
import com.alander.trip12306.cache.core.CacheLoader;
import com.alander.trip12306.cache.toolkit.CacheUtil;
import com.alander.trip12306.common.enums.FlagEnum;
import com.alander.trip12306.common.toolkit.BeanUtil;
import com.alander.trip12306.convention.exception.ClientException;
import com.alander.trip12306.ticketservice.common.enums.RegionStationQueryTypeEnum;
import com.alander.trip12306.ticketservice.dao.entity.RegionDO;
import com.alander.trip12306.ticketservice.dao.entity.StationDO;
import com.alander.trip12306.ticketservice.dao.mapper.RegionMapper;
import com.alander.trip12306.ticketservice.dao.mapper.StationMapper;
import com.alander.trip12306.ticketservice.dto.req.RegionStationQueryReqDTO;
import com.alander.trip12306.ticketservice.dto.resp.RegionStationQueryRespDTO;
import com.alander.trip12306.ticketservice.dto.resp.StationQueryRespDTO;
import com.alander.trip12306.ticketservice.service.RegionStationService;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.alander.trip12306.ticketservice.common.constant.Index12306Constant.ADVANCE_TICKET_DAY;
import static com.alander.trip12306.ticketservice.common.constant.RedisKeyConstant.*;

/**
 * 地区以及车站接口实现层
 */
@Service
@RequiredArgsConstructor
public class RegionStationServiceImpl implements RegionStationService {

    private final RegionMapper regionMapper;
    private final StationMapper stationMapper;
    private final DistributedCache distributedCache;
    private final RedissonClient redissonClient;

    @Override
    public List<RegionStationQueryRespDTO> listRegionStation(RegionStationQueryReqDTO requestParam) {
        String key;
        if (StrUtil.isNotBlank(requestParam.getName())) {
            key  = REGION_STATION  + requestParam.getName();
            return safeGetRegionStation(
                    key ,
                    () -> {
                        LambdaQueryWrapper<StationDO> queryWrapper = Wrappers.lambdaQuery(StationDO.class)
                                .likeRight(StationDO::getName, requestParam.getName())
                                .or()
                                .likeRight(StationDO::getSpell, requestParam.getName());
                        List<StationDO> stationDOList = stationMapper.selectList(queryWrapper);
                        return JSON.toJSONString(BeanUtil.convert(stationDOList, RegionStationQueryRespDTO.class));
                    },
                    requestParam.getName()
            );
        }
        key  = REGION_STATION  + requestParam.getQueryType();
        LambdaQueryWrapper<RegionDO> queryWrapper = switch (requestParam.getQueryType()) {
            case 0 -> Wrappers.lambdaQuery(RegionDO.class)
                    .eq(RegionDO::getPopularFlag, FlagEnum.TRUE.code());
            case 1 -> Wrappers.lambdaQuery(RegionDO.class)
                    .in(RegionDO::getInitial, RegionStationQueryTypeEnum.A_E.getSpells());
            case 2 -> Wrappers.lambdaQuery(RegionDO.class)
                    .in(RegionDO::getInitial, RegionStationQueryTypeEnum.F_J.getSpells());
            case 3 -> Wrappers.lambdaQuery(RegionDO.class)
                    .in(RegionDO::getInitial, RegionStationQueryTypeEnum.K_O.getSpells());
            case 4 -> Wrappers.lambdaQuery(RegionDO.class)
                    .in(RegionDO::getInitial, RegionStationQueryTypeEnum.P_T.getSpells());
            case 5 -> Wrappers.lambdaQuery(RegionDO.class)
                    .in(RegionDO::getInitial, RegionStationQueryTypeEnum.U_Z.getSpells());
            default -> throw new ClientException("查询失败，请检查查询参数是否正确");
        };
        return safeGetRegionStation(
                key,
                () -> {
                    List<RegionDO> regionDOList = regionMapper.selectList(queryWrapper);
                    return JSON.toJSONString(BeanUtil.convert(regionDOList, RegionStationQueryRespDTO.class));
                },
                String.valueOf(requestParam.getQueryType())
        );
    }

    @Override
    public List<StationQueryRespDTO> listAllStation() {
        StationQueryRespDTO[] stationQueryRespDTOS = distributedCache.safeGet(
                STATION_ALL,
                StationQueryRespDTO[].class,
                () -> BeanUtil.convert(stationMapper.selectList(Wrappers.emptyWrapper()), StationQueryRespDTO.class)
                        .toArray(new StationQueryRespDTO[0]),
                ADVANCE_TICKET_DAY,
                TimeUnit.DAYS
        );
        return Arrays.asList(stationQueryRespDTOS);
    }

    private  List<RegionStationQueryRespDTO> safeGetRegionStation(final String key, CacheLoader<String> loader, String param) {
        List<RegionStationQueryRespDTO> result;
        if (CollUtil.isNotEmpty(result = JSON.parseArray(distributedCache.get(key, String.class), RegionStationQueryRespDTO.class))) {
            return result;
        }
        String lockKey = String.format(LOCK_QUERY_REGION_STATION_LIST, param);
        RLock lock = redissonClient.getLock(lockKey);
        lock.lock();
        try {
            if (CollUtil.isEmpty(result = JSON.parseArray(distributedCache.get(key, String.class), RegionStationQueryRespDTO.class))) {
                if (CollUtil.isEmpty(result = loadAndSet(key, loader))) {
                    return Collections.emptyList();
                }
            }
        } finally {
            lock.unlock();
        }
        return result;
    }

    private List<RegionStationQueryRespDTO> loadAndSet(final String key, CacheLoader<String> loader) {
        String result = loader.load();
        if (CacheUtil.isNullOrBlank(result)) {
            return Collections.emptyList();
        }
        List<RegionStationQueryRespDTO> respDTOList = JSON.parseArray(result, RegionStationQueryRespDTO.class);
        distributedCache.put(
                key,
                result,
                ADVANCE_TICKET_DAY,
                TimeUnit.DAYS
        );
        return respDTOList;
    }
}
