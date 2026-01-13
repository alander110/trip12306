package com.alander.trip12306.ticketservice.service.Impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.alander.trip12306.base.ApplicationContextHolder;
import com.alander.trip12306.cache.DistributedCache;
import com.alander.trip12306.cache.toolkit.CacheUtil;
import com.alander.trip12306.common.toolkit.BeanUtil;
import com.alander.trip12306.convention.exception.ServiceException;
import com.alander.trip12306.convention.result.Result;
import com.alander.trip12306.designpattern.chain.AbstractChainContext;
import com.alander.trip12306.distributedid.toolkit.SnowflakeIdUtil;
import com.alander.trip12306.idempotent.annotation.Idempotent;
import com.alander.trip12306.idempotent.enums.IdempotentSceneEnum;
import com.alander.trip12306.idempotent.enums.IdempotentTypeEnum;
import com.alander.trip12306.log.annotation.ILog;
import com.alander.trip12306.ticketservice.common.enums.*;
import com.alander.trip12306.ticketservice.dao.entity.*;
import com.alander.trip12306.ticketservice.dao.mapper.*;
import com.alander.trip12306.ticketservice.dto.domain.*;
import com.alander.trip12306.ticketservice.dto.req.*;
import com.alander.trip12306.ticketservice.dto.resp.*;
import com.alander.trip12306.ticketservice.dto.resp.TicketOrderDetailRespDTO;
import com.alander.trip12306.ticketservice.mq.event.PurchaseTicketsEvent;
import com.alander.trip12306.ticketservice.mq.produce.PurchaseTicketsSendProduce;
import com.alander.trip12306.ticketservice.remote.PayRemoteService;
import com.alander.trip12306.ticketservice.remote.TicketOrderRemoteService;
import com.alander.trip12306.ticketservice.remote.dto.*;
import com.alander.trip12306.ticketservice.service.SeatService;
import com.alander.trip12306.ticketservice.service.TicketService;
import com.alander.trip12306.ticketservice.service.TrainStationRelationService;
import com.alander.trip12306.ticketservice.service.TrainStationService;
import com.alander.trip12306.ticketservice.service.cache.SeatMarginCacheLoader;
import com.alander.trip12306.ticketservice.service.handler.ticket.dto.TokenResultDTO;
import com.alander.trip12306.ticketservice.service.handler.ticket.dto.TrainPurchaseTicketRespDTO;
import com.alander.trip12306.ticketservice.service.handler.ticket.select.TrainSeatTypeSelector;
import com.alander.trip12306.ticketservice.service.handler.ticket.tokenbucket.TicketAvailabilityTokenBucket;
import com.alander.trip12306.ticketservice.toolkit.DateUtil;
import com.alander.trip12306.ticketservice.toolkit.TimeStringComparator;
import com.alander.trip12306.user.core.UserContext;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.alander.trip12306.ticketservice.common.constant.Index12306Constant.ADVANCE_TICKET_DAY;
import static com.alander.trip12306.ticketservice.common.constant.RedisKeyConstant.*;
import static com.alander.trip12306.ticketservice.toolkit.DateUtil.convertDateToLocalTime;
import static com.baomidou.mybatisplus.extension.toolkit.Db.saveBatch;

/**
 * 车票接口实现层
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService, CommandLineRunner {

    private final TrainMapper trainMapper;
    private final TrainStationRelationMapper trainStationRelationMapper;
    private final TrainStationPriceMapper trainStationPriceMapper;
    private final DistributedCache distributedCache;
    private final TicketOrderRemoteService ticketOrderRemoteService;
    private final PayRemoteService payRemoteService;
    private final StationMapper stationMapper;
    private final SeatService seatService;
    private final TrainStationService trainStationService;
    private final TrainStationRelationService trainStationRelationService;
    private final OrderTrackingMapper orderTrackingMapper;
    private final TrainSeatTypeSelector trainSeatTypeSelector;
    private final SeatMarginCacheLoader seatMarginCacheLoader;
    private final AbstractChainContext<TicketPageQueryReqDTO> ticketPageQueryAbstractChainContext;
    private final AbstractChainContext<PurchaseTicketReqDTO> purchaseTicketAbstractChainContext;
    private final AbstractChainContext<RefundTicketReqDTO> refundReqDTOAbstractChainContext;
    private final RedissonClient redissonClient;
    private final ConfigurableEnvironment environment;
    private final TicketAvailabilityTokenBucket ticketAvailabilityTokenBucket;
    private final PurchaseTicketsSendProduce purchaseTicketsSendProduce;
    private final TransactionTemplate transactionTemplate;
    private TicketService ticketService;


    @Value("${ticket.availability.cache-update.type:}")
    private String ticketAvailabilityCacheUpdateType;
    @Value("${framework.cache.redis.prefix:}")
    private String cacheRedisPrefix;

    @Override
    public TicketPageQueryRespDTO pageListTicketQueryV1(TicketPageQueryReqDTO requestParam) {

        // 第一步：通过责任链模式对查询请求进行预处理过滤（如权限校验、参数验证等）
        ticketPageQueryAbstractChainContext.handler(TicketChainMarkEnum.TRAIN_QUERY_FILTER.name(), requestParam);

        // 获取Redis模板实例用于后续缓存操作
        StringRedisTemplate stringRedisTemplate = (StringRedisTemplate) distributedCache.getInstance();


        // 第二步：从Redis中批量获取出发站和到达站的区域信息
        List<Object> stationDetails = stringRedisTemplate.opsForHash()
                .multiGet(REGION_TRAIN_STATION_MAPPING, Lists.newArrayList(requestParam.getFromStation(), requestParam.getToStation()));

        /*
         * 第三步：检查Redis中是否存在缺失的站点区域信息
         * 统计null值的数量，如果大于0说明有站点信息未缓存
         */
        long count = stationDetails.stream().filter(Objects::isNull).count();
        if (count > 0) {
            /*
             * 第四步：处理缓存缺失情况 - 使用分布式锁保证数据一致性
             * 获取分布式锁，防止多个服务实例同时更新缓存导致数据不一致
             */
            RLock lock = redissonClient.getLock(LOCK_REGION_TRAIN_STATION_MAPPING);
            lock.lock();
            try {
                // 双重检查：在获取锁后再次检查缓存，避免其他线程已经更新了缓存
                stationDetails = stringRedisTemplate.opsForHash()
                        .multiGet(REGION_TRAIN_STATION_MAPPING, Lists.newArrayList(requestParam.getFromStation(), requestParam.getToStation()));
                count = stationDetails.stream().filter(Objects::isNull).count();

                if (count > 0) {
                    /*
                      第五步：缓存确实缺失，从数据库加载完整的站点区域映射数据
                      查询所有站点信息，构建站点编码到区域名称的完整映射
                     */
                    List<StationDO> stationDOList = stationMapper.selectList(Wrappers.emptyWrapper());
                    Map<String, String> regionTrainStationMap = new HashMap<>();
                    stationDOList.forEach(each -> regionTrainStationMap.put(each.getCode(), each.getRegionName()));

                    // 将完整的映射关系批量写入Redis缓存
                    stringRedisTemplate.opsForHash().putAll(REGION_TRAIN_STATION_MAPPING, regionTrainStationMap);

                    // 重新获取当前请求所需的站点区域信息
                    stationDetails = new ArrayList<>();
                    stationDetails.add(regionTrainStationMap.get(requestParam.getFromStation()));
                    stationDetails.add(regionTrainStationMap.get(requestParam.getToStation()));
                }
            } finally {
                // 确保锁一定被释放，避免死锁
                lock.unlock();
            }
        }

        // 初始化车次结果列表
        List<TicketListDTO> seatResults = new ArrayList<>();

        /*
          第六步：构建区域间列车信息的Redis缓存Key
          格式：region_train_station:{出发区域}:{到达区域}
          用于存储两个区域之间的所有车次信息
         */
        String buildRegionTrainStationHashKey = String.format(REGION_TRAIN_STATION, stationDetails.get(0), stationDetails.get(1));

        /*
          第七步：尝试从Redis获取区域间的列车信息缓存
          如果缓存存在则直接使用，减少数据库查询
         */
        Map<Object, Object> regionTrainStationAllMap = stringRedisTemplate.opsForHash().entries(buildRegionTrainStationHashKey);
        if (MapUtil.isEmpty(regionTrainStationAllMap)) {
            /*
              第八步：区域间列车缓存不存在，使用分布式锁防止缓存击穿
              多个请求同时发现缓存失效时，只有一个请求会执行数据库查询
             */
            RLock lock = redissonClient.getLock(LOCK_REGION_TRAIN_STATION);
            lock.lock();
            try {
                // 双重检查锁定模式：再次检查缓存，防止其他线程已经重建了缓存
                regionTrainStationAllMap = stringRedisTemplate.opsForHash().entries(buildRegionTrainStationHashKey);
                if (MapUtil.isEmpty(regionTrainStationAllMap)) {
                    /*
                      第九步：真正执行数据库查询，获取两个区域间的列车关系信息
                      查询指定出发区域和到达区域的所有车次路线信息
                     */
                    LambdaQueryWrapper<TrainStationRelationDO> queryWrapper = Wrappers.lambdaQuery(TrainStationRelationDO.class)
                            .eq(TrainStationRelationDO::getStartRegion, stationDetails.get(0))
                            .eq(TrainStationRelationDO::getEndRegion, stationDetails.get(1));
                    List<TrainStationRelationDO> trainStationRelationList = trainStationRelationMapper.selectList(queryWrapper);

                    // 遍历每个列车路线关系，构建完整的车次信息DTO
                    for (TrainStationRelationDO each : trainStationRelationList) {
                        /*
                          第十步：获取列车基础信息，使用分布式缓存优化
                          先从缓存获取列车信息，缓存不存在时从数据库查询并写入缓存
                          ADVANCE_TICKET_DAY：缓存有效期为预售天数
                         */
                        TrainDO trainDO = distributedCache.safeGet(
                                TRAIN_INFO + each.getTrainId(),
                                TrainDO.class,
                                () -> trainMapper.selectById(each.getTrainId()),  // 缓存加载器：数据库查询
                                ADVANCE_TICKET_DAY,
                                TimeUnit.DAYS);

                        // 构建车次列表DTO对象
                        TicketListDTO result = new TicketListDTO();
                        result.setTrainId(String.valueOf(trainDO.getId()));
                        result.setTrainNumber(trainDO.getTrainNumber());
                        // 转换日期时间为指定格式的字符串
                        result.setDepartureTime(convertDateToLocalTime(each.getDepartureTime(), "HH:mm"));
                        result.setArrivalTime(convertDateToLocalTime(each.getArrivalTime(), "HH:mm"));
                        // 计算行程耗时（小时差）
                        result.setDuration(DateUtil.calculateHourDifference(each.getDepartureTime(), each.getArrivalTime()));
                        result.setDeparture(each.getDeparture());
                        result.setArrival(each.getArrival());
                        result.setDepartureFlag(each.getDepartureFlag());
                        result.setArrivalFlag(each.getArrivalFlag());
                        result.setTrainType(trainDO.getTrainType());
                        result.setTrainBrand(trainDO.getTrainBrand());

                        // 处理列车标签（如果有）
                        if (StrUtil.isNotBlank(trainDO.getTrainTag())) {
                            result.setTrainTags(StrUtil.split(trainDO.getTrainTag(), ","));
                        }

                        // 计算是否跨天到达
                        long betweenDay = cn.hutool.core.date.DateUtil.betweenDay(each.getDepartureTime(), each.getArrivalTime(), false);
                        result.setDaysArrived((int) betweenDay);

                        // 设置售票状态：当前时间在开售时间之后为可售(0)，否则为未开售(1)
                        result.setSaleStatus(new Date().after(trainDO.getSaleTime()) ? 0 : 1);
                        result.setSaleTime(convertDateToLocalTime(trainDO.getSaleTime(), "MM-dd HH:mm"));

                        seatResults.add(result);

                        /*
                          第十一步：将构建的车次信息序列化后存入区域列车缓存
                          缓存Key格式：trainId_departure_arrival，便于后续精确查找
                         */
                        regionTrainStationAllMap.put(CacheUtil.buildKey(String.valueOf(each.getTrainId()), each.getDeparture(), each.getArrival()), JSON.toJSONString(result));
                    }

                    // 将所有车次信息批量写入Redis缓存
                    stringRedisTemplate.opsForHash().putAll(buildRegionTrainStationHashKey, regionTrainStationAllMap);
                }
            } finally {
                lock.unlock(); // 释放分布式锁
            }
        }

        /*
          第十二步：如果seatResults为空（说明是从缓存中获取的数据）
          需要将缓存中的JSON字符串反序列化为TicketListDTO对象列表
         */
        seatResults = CollUtil.isEmpty(seatResults)
                ? regionTrainStationAllMap.values().stream().map(each -> JSON.parseObject(each.toString(), TicketListDTO.class)).toList()
                : seatResults;

        /*
          第十三步：按发车时间对车次列表进行排序
          使用自定义的时间字符串比较器实现时间顺序排序
         */
        seatResults = seatResults.stream().sorted(new TimeStringComparator()).toList();

        /*
          第十四步：为每个车次添加座位类型和余票数量信息
          这是最复杂的部分，涉及多层缓存和数据聚合
         */
        for (TicketListDTO each : seatResults) {
            /*
             * 获取车次区间的票价信息，使用缓存优化
             * 缓存Key格式：train_station_price:{trainId}:{departure}:{arrival}
             */
            String trainStationPriceStr = distributedCache.safeGet(
                    String.format(TRAIN_STATION_PRICE, each.getTrainId(), each.getDeparture(), each.getArrival()),
                    String.class,
                    () -> {
                        // 缓存加载器：从数据库查询指定车次区间的票价信息
                        LambdaQueryWrapper<TrainStationPriceDO> trainStationPriceQueryWrapper = Wrappers.lambdaQuery(TrainStationPriceDO.class)
                                .eq(TrainStationPriceDO::getDeparture, each.getDeparture())
                                .eq(TrainStationPriceDO::getArrival, each.getArrival())
                                .eq(TrainStationPriceDO::getTrainId, each.getTrainId());
                        return JSON.toJSONString(trainStationPriceMapper.selectList(trainStationPriceQueryWrapper));
                    },
                    ADVANCE_TICKET_DAY,
                    TimeUnit.DAYS
            );

            // 解析票价信息JSON为对象列表
            List<TrainStationPriceDO> trainStationPriceDOList = JSON.parseArray(trainStationPriceStr, TrainStationPriceDO.class);
            List<SeatClassDTO> seatClassList = new ArrayList<>();

            // 遍历每种座位类型的票价信息，获取对应的余票数量
            trainStationPriceDOList.forEach(item -> {
                String seatType = String.valueOf(item.getSeatType());
                // 构建余票缓存的Key后缀
                String keySuffix = StrUtil.join("_", each.getTrainId(), item.getDeparture(), item.getArrival());

                /*
                 * 第十五步：获取座位余票数量，采用两级缓存策略
                 * 1. 首先从Redis缓存获取
                 * 2. Redis缓存未命中时，从本地缓存加载器获取
                 */
                Object quantityObj = stringRedisTemplate.opsForHash().get(TRAIN_STATION_REMAINING_TICKET + keySuffix, seatType);
                int quantity = Optional.ofNullable(quantityObj)
                        .map(Object::toString)
                        .map(Integer::parseInt)
                        .orElseGet(() -> {
                            // Redis缓存未命中，使用本地缓存加载器获取余票信息
                            Map<String, String> seatMarginMap = seatMarginCacheLoader.load(String.valueOf(each.getTrainId()), seatType, item.getDeparture(), item.getArrival());
                            return Optional.ofNullable(seatMarginMap.get(String.valueOf(item.getSeatType()))).map(Integer::parseInt).orElse(0);
                        });

                // 构建座位类型DTO：包含座位类型、余票数量、价格和是否可候补
                seatClassList.add(new SeatClassDTO(
                        item.getSeatType(),
                        quantity,
                        new BigDecimal(item.getPrice()).divide(new BigDecimal("100"), 1, RoundingMode.HALF_UP), // 价格单位转换（分转元）
                        false  // 是否可候补
                ));
            });

            // 设置车次的座位类型列表
            each.setSeatClassList(seatClassList);
        }

        /*
         * 第十六步：构建最终的分页查询响应对象
         * 包含车次列表以及各种用于前端筛选的下拉选项列表
         */
        return TicketPageQueryRespDTO.builder()
                .trainList(seatResults)  // 车次列表
                .departureStationList(buildDepartureStationList(seatResults))  // 出发站筛选列表
                .arrivalStationList(buildArrivalStationList(seatResults))      // 到达站筛选列表
                .trainBrandList(buildTrainBrandList(seatResults))              // 列车品牌筛选列表
                .seatClassTypeList(buildSeatClassList(seatResults))            // 座位类型筛选列表
                .build();
    }

    @Override
    public TicketPageQueryRespDTO pageListTicketQueryV2(TicketPageQueryReqDTO requestParam) {
        // 责任链模式 验证城市名称是否存在、不存在加载缓存以及出发日期不能小于当前日期等等
        ticketPageQueryAbstractChainContext.handler(TicketChainMarkEnum.TRAIN_QUERY_FILTER.name(), requestParam);
        StringRedisTemplate stringRedisTemplate = (StringRedisTemplate) distributedCache.getInstance();
        // 列车查询逻辑较为复杂，详细解析文章查看 https://nageoffer.com/12306/question
        // v2 版本更符合企业级高并发真实场景解决方案，完美解决了 v1 版本性能深渊问题。通过 Jmeter 压测聚合报告得知，性能提升在 300% - 500%+
        // 其实还能有 v3 版本，性能估计在原基础上还能进一步提升一倍。不过 v3 版本太过于复杂，不易读且不易扩展，就不写具体的代码了。面试中 v2 版本已经够和面试官吹的了
        List<Object> stationDetails = stringRedisTemplate.opsForHash()
                .multiGet(REGION_TRAIN_STATION_MAPPING, Lists.newArrayList(requestParam.getFromStation(), requestParam.getToStation()));
        String buildRegionTrainStationHashKey = String.format(REGION_TRAIN_STATION, stationDetails.get(0), stationDetails.get(1));
        Map<Object, Object> regionTrainStationAllMap = stringRedisTemplate.opsForHash().entries(buildRegionTrainStationHashKey);
        List<TicketListDTO> seatResults = regionTrainStationAllMap.values().stream()
                .map(each -> JSON.parseObject(each.toString(), TicketListDTO.class))
                .sorted(new TimeStringComparator())
                .toList();
        List<String> trainStationPriceKeys = seatResults.stream()
                .map(each -> String.format(cacheRedisPrefix + TRAIN_STATION_PRICE, each.getTrainId(), each.getDeparture(), each.getArrival()))
                .toList();
        List<Object> trainStationPriceObjs = stringRedisTemplate.executePipelined((RedisCallback<String>) connection -> {
            trainStationPriceKeys.forEach(each -> connection.stringCommands().get(each.getBytes()));
            return null;
        });
        List<TrainStationPriceDO> trainStationPriceDOList = new ArrayList<>();
        List<String> trainStationRemainingKeyList = new ArrayList<>();
        for (Object each : trainStationPriceObjs) {
            List<TrainStationPriceDO> trainStationPriceList = JSON.parseArray(each.toString(), TrainStationPriceDO.class);
            trainStationPriceDOList.addAll(trainStationPriceList);
            for (TrainStationPriceDO item : trainStationPriceList) {
                String trainStationRemainingKey = cacheRedisPrefix + TRAIN_STATION_REMAINING_TICKET + StrUtil.join("_", item.getTrainId(), item.getDeparture(), item.getArrival());
                trainStationRemainingKeyList.add(trainStationRemainingKey);
            }
        }
        List<Object> trainStationRemainingObjs = stringRedisTemplate.executePipelined((RedisCallback<String>) connection -> {
            for (int i = 0; i < trainStationRemainingKeyList.size(); i++) {
                connection.hashCommands().hGet(trainStationRemainingKeyList.get(i).getBytes(), trainStationPriceDOList.get(i).getSeatType().toString().getBytes());
            }
            return null;
        });
        for (TicketListDTO each : seatResults) {
            List<Integer> seatTypesByCode = VehicleTypeEnum.findSeatTypesByCode(each.getTrainType());
            List<Object> remainingTicket = new ArrayList<>(trainStationRemainingObjs.subList(0, seatTypesByCode.size()));
            List<TrainStationPriceDO> trainStationPriceDOSub = new ArrayList<>(trainStationPriceDOList.subList(0, seatTypesByCode.size()));
            trainStationRemainingObjs.subList(0, seatTypesByCode.size()).clear();
            trainStationPriceDOList.subList(0, seatTypesByCode.size()).clear();
            List<SeatClassDTO> seatClassList = new ArrayList<>();
            for (int i = 0; i < trainStationPriceDOSub.size(); i++) {
                TrainStationPriceDO trainStationPriceDO = trainStationPriceDOSub.get(i);
                SeatClassDTO seatClassDTO = SeatClassDTO.builder()
                        .type(trainStationPriceDO.getSeatType())
                        .quantity(Integer.parseInt(remainingTicket.get(i).toString()))
                        .price(new BigDecimal(trainStationPriceDO.getPrice()).divide(new BigDecimal("100"), 1, RoundingMode.HALF_UP))
                        .candidate(false)
                        .build();
                seatClassList.add(seatClassDTO);
            }
            each.setSeatClassList(seatClassList);
        }
        return TicketPageQueryRespDTO.builder()
                .trainList(seatResults)
                .departureStationList(buildDepartureStationList(seatResults))
                .arrivalStationList(buildArrivalStationList(seatResults))
                .trainBrandList(buildTrainBrandList(seatResults))
                .seatClassTypeList(buildSeatClassList(seatResults))
                .build();
    }

    @ILog
    @Idempotent(
            uniqueKeyPrefix = "trip2306-ticket:lock_purchase-tickets:",
            key = "T(com.alander.trip12306.base.ApplicationContextHolder).getBean('environment').getProperty('unique-name', '')"
                    + "+'_'+"
                    + "T(com.alander.trip12306.user.core.UserContext).getUsername()",
            message = "正在执行下单流程，请稍后...",
            scene = IdempotentSceneEnum.RESTAPI,
            type = IdempotentTypeEnum.SPEL
    )
    @Override
    public TicketPurchaseRespDTO purchaseTicketsV1(PurchaseTicketReqDTO requestParam) {
        // 责任链模式，验证 1：参数必填 2：参数正确性 3：乘客是否已买当前车次等...
        purchaseTicketAbstractChainContext.handler(TicketChainMarkEnum.TRAIN_PURCHASE_TICKET_FILTER.name(), requestParam);
        // v1 版本购票存在 4 个较为严重的问题，v2 版本相比较 v1 版本更具有业务特点以及性能，整体提升较大
        // 写了详细的 v2 版本购票升级指南，详情查看：https://nageoffer.com/12306/question
        String lockKey = environment.resolvePlaceholders(String.format(LOCK_PURCHASE_TICKETS, requestParam.getTrainId()));
        RLock lock = redissonClient.getLock(lockKey);
        lock.lock();
        try {
            return executePurchaseTickets(requestParam);
        } finally {
            lock.unlock();
        }
    }

    private final Cache<String, ReentrantLock> localLockMap = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.DAYS)
            .build();

    private final Cache<String, Boolean> tokenTicketsRefreshMap = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    @ILog
    @Idempotent(
            uniqueKeyPrefix = "trip12306-ticket:lock_purchase-tickets:",
            key = "T(com.alander.trip12306.bases.ApplicationContextHolder).getBean('environment').getProperty('unique-name', '')"
                    + "+'_'+"
                    + "T(org.opengoofy.trip12306.frameworks.starter.user.core.UserContext).getUsername()",
            message = "正在执行下单流程，请稍后...",
            scene = IdempotentSceneEnum.RESTAPI,
            type = IdempotentTypeEnum.SPEL
    )
    @Override
    public TicketPurchaseRespDTO purchaseTicketsV2(PurchaseTicketReqDTO requestParam) {
        // 责任链模式，验证 1：参数必填 2：参数正确性 3：乘客是否已买当前车次等...
        purchaseTicketAbstractChainContext.handler(TicketChainMarkEnum.TRAIN_PURCHASE_TICKET_FILTER.name(), requestParam);
        // 为什么需要令牌限流？余票缓存限流不可以么？详情查看：https://nageoffer.com/12306/question
        TokenResultDTO tokenResult = ticketAvailabilityTokenBucket.takeTokenFromBucket(requestParam);
        if (tokenResult.getTokenIsNull()) {
            Boolean isRefreshing = tokenTicketsRefreshMap.asMap().putIfAbsent(requestParam.getTrainId(), true);
            if (isRefreshing == null) {
                tokenIsNullRefreshToken(requestParam, tokenResult);
            }
            throw new ServiceException("列车站点已无余票");
        }
        // v1 版本购票存在 4 个较为严重的问题，v2 版本相比较 v1 版本更具有业务特点以及性能，整体提升较大
        // 写了详细的 v2 版本购票升级指南，详情查看：https://nageoffer.com/12306/question
        List<ReentrantLock> localLockList = new ArrayList<>();
        List<RLock> distributedLockList = new ArrayList<>();
        Map<Integer, List<PurchaseTicketPassengerDetailDTO>> seatTypeMap = requestParam.getPassengers().stream()
                .collect(Collectors.groupingBy(PurchaseTicketPassengerDetailDTO::getSeatType));
        seatTypeMap.forEach((searType, count) -> {
            String lockKey = environment.resolvePlaceholders(String.format(LOCK_PURCHASE_TICKETS_V2, requestParam.getTrainId(), searType));
            ReentrantLock localLock = localLockMap.asMap().computeIfAbsent(
                    lockKey,
                    k -> new ReentrantLock(true)
            );
            localLockList.add(localLock);
            RLock distributedLock = redissonClient.getFairLock(lockKey);
            distributedLockList.add(distributedLock);
        });
        try {
            localLockList.forEach(ReentrantLock::lock);
            distributedLockList.forEach(RLock::lock);
            return ticketService.executePurchaseTickets(requestParam);
        } finally {
            localLockList.forEach(localLock -> {
                try {
                    localLock.unlock();
                } catch (Throwable ignored) {
                }
            });
            distributedLockList.forEach(distributedLock -> {
                try {
                    distributedLock.unlock();
                } catch (Throwable ignored) {
                }
            });
        }
    }

    @ILog
    @Idempotent(
            uniqueKeyPrefix = "trip12306-ticket:lock_purchase-tickets:",
            key = "T(com.alander.trip12306.bases.ApplicationContextHolder).getBean('environment').getProperty('unique-name', '')"
                    + "+'_'+"
                    + "T(org.opengoofy.trip12306.frameworks.starter.user.core.UserContext).getUsername()",
            message = "正在执行下单流程，请稍后...",
            scene = IdempotentSceneEnum.RESTAPI,
            type = IdempotentTypeEnum.SPEL
    )
    @Override
    public String purchaseTicketsV3(PurchaseTicketReqDTO requestParam) {
        // 前置逻辑同v2接口，此处重复波浪线忽略
        // ----------------------------------- before --------------------------------------------------------------------
        purchaseTicketAbstractChainContext.handler(TicketChainMarkEnum.TRAIN_PURCHASE_TICKET_FILTER.name(), requestParam);
        TokenResultDTO tokenResult = ticketAvailabilityTokenBucket.takeTokenFromBucket(requestParam);
        if (tokenResult.getTokenIsNull()) {
            Boolean isRefreshing = tokenTicketsRefreshMap.asMap().putIfAbsent(requestParam.getTrainId(), true);
            if (isRefreshing == null) {
                tokenIsNullRefreshToken(requestParam, tokenResult);
            }
            throw new ServiceException("列车站点已无余票");
        }
        // ----------------------------------- after ---------------------------------------------------------------------
        // 通过消息队列方式异步完成下单接口吞吐量提升，通过业务模式规避复杂度
        PurchaseTicketsEvent purchaseTicketsEvent = PurchaseTicketsEvent.builder()
                .orderTrackingId(SnowflakeIdUtil.nextIdStr())
                .originalRequestParam(requestParam)
                .userInfo(UserContext.getUser())
                .build();
        SendResult sendResult = purchaseTicketsSendProduce.sendMessage(purchaseTicketsEvent);
        if (!Objects.equals(sendResult.getSendStatus(), SendStatus.SEND_OK)) {
            throw new ServiceException("发送用户异步购票消息失败");
        }
        // 返回全局唯一标识，当做用户购票异步返回和订单之间的关联关系
        return purchaseTicketsEvent.getOrderTrackingId();
    }

    @Override
    public OrderTrackingRespDTO purchaseTicketsV3Query(String orderTrackingId) {
        LambdaQueryWrapper<OrderTrackingDO> queryWrapper = Wrappers.lambdaQuery(OrderTrackingDO.class)
                .eq(OrderTrackingDO::getId, orderTrackingId)
                // 添加 username 字段，以防止非登录用户访问其他用户的数据，避免数据横向越权
                .eq(OrderTrackingDO::getUsername, UserContext.getUsername());
        OrderTrackingDO orderTrackingDO = orderTrackingMapper.selectOne(queryWrapper);
        return BeanUtil.convert(orderTrackingDO, OrderTrackingRespDTO.class);
    }

    /**
     * 执行购买车票的核心业务逻辑
     * 此方法负责处理车票购买的完整流程，包括座位锁定、本地订单创建、远程订单服务调用等
     *
     * @param requestParam 购票请求参数，包含列车ID、出发站、到达站、乘客信息等
     * @return TicketPurchaseRespDTO 购票响应结果，包含订单号和车票详情
     */
    @Override
    public TicketPurchaseRespDTO executePurchaseTickets(PurchaseTicketReqDTO requestParam) {
        String trainId = requestParam.getTrainId();
        // 从分布式缓存中获取列车信息，如果缓存不存在则从数据库查询并存入缓存
        // 缓存有效期为预售天数，避免频繁查询数据库
        TrainDO trainDO = distributedCache.safeGet(
                TRAIN_INFO + trainId,
                TrainDO.class,
                () -> trainMapper.selectById(trainId),  // 缓存未命中时的加载逻辑
                ADVANCE_TICKET_DAY,
                TimeUnit.DAYS);

        // 查询列车站点关系信息，获取出发时间和到达时间
        TrainStationRelationDO trainStationRelationDO = trainStationRelationService
                .findRelation(trainId, requestParam.getDeparture(), requestParam.getArrival());

        // 初始化订单项和车票详情列表
        List<TicketOrderItemCreateRemoteReqDTO> orderItemCreateRemoteReqDTOList = new ArrayList<>();
        List<TicketOrderDetailRespDTO> ticketOrderDetailResults = new ArrayList<>();

        // 使用AtomicReference包装订单号，解决Lambda表达式中无法修改外部变量的问题
        // Lambda表达式中只能访问final或隐式final的局部变量
        AtomicReference<String> orderSn = new AtomicReference<>();

        // 使用编程式事务模板，精确控制事务边界，避免长事务导致的性能问题
        transactionTemplate.executeWithoutResult(status -> {
            try {
                // 调用车厢座位类型选择器，锁定符合条件的座位
                // 此处会进行座位选择算法，返回选中的座位信息
                List<TrainPurchaseTicketRespDTO> trainPurchaseTicketResults = trainSeatTypeSelector.selectAndLockSeat(trainDO.getTrainType(), requestParam);

                // 将选中的座位信息转换为本地数据库实体对象
                List<TicketDO> ticketDOList = trainPurchaseTicketResults.stream()
                        .map(each -> TicketDO.builder()
                                .username(UserContext.getUsername())  // 设置当前用户名
                                .trainId(Long.parseLong(requestParam.getTrainId()))  // 设置列车ID
                                .carriageNumber(each.getCarriageNumber())  // 设置车厢号
                                .seatNumber(each.getSeatNumber())  // 设置座位号
                                .passengerId(each.getPassengerId())  // 设置乘客ID
                                .ticketStatus(TicketStatusEnum.UNPAID.getCode())  // 设置票状态为未支付
                                .build())
                        .toList();

                // 批量保存车票信息到本地数据库
                saveBatch(ticketDOList);

                // 遍历选中的座位，构建远程订单服务调用所需的参数
                trainPurchaseTicketResults.forEach(each -> {
                    // 构建订单项创建请求参数
                    TicketOrderItemCreateRemoteReqDTO orderItemCreateRemoteReqDTO = TicketOrderItemCreateRemoteReqDTO.builder()
                            .amount(each.getAmount())  // 金额
                            .carriageNumber(each.getCarriageNumber())  // 车厢号
                            .seatNumber(each.getSeatNumber())  // 座位号
                            .idCard(each.getIdCard())  // 身份证号
                            .idType(each.getIdType())  // 证件类型
                            .phone(each.getPhone())  // 电话号码
                            .seatType(each.getSeatType())  // 座位类型
                            .ticketType(each.getUserType())  // 票类型
                            .realName(each.getRealName())  // 真实姓名
                            .build();

                    // 构建本地车票详情响应参数
                    TicketOrderDetailRespDTO ticketOrderDetailRespDTO = TicketOrderDetailRespDTO.builder()
                            .amount(each.getAmount())  // 金额
                            .carriageNumber(each.getCarriageNumber())  // 车厢号
                            .seatNumber(each.getSeatNumber())  // 座位号
                            .idCard(each.getIdCard())  // 身份证号
                            .idType(each.getIdType())  // 证件类型
                            .seatType(each.getSeatType())  // 座位类型
                            .ticketType(each.getUserType())  // 票类型
                            .realName(each.getRealName())  // 真实姓名
                            .build();

                    // 添加到相应的列表中
                    orderItemCreateRemoteReqDTOList.add(orderItemCreateRemoteReqDTO);
                    ticketOrderDetailResults.add(ticketOrderDetailRespDTO);
                });

                // 构建远程订单创建请求参数
                TicketOrderCreateRemoteReqDTO orderCreateRemoteReqDTO = TicketOrderCreateRemoteReqDTO.builder()
                        .departure(requestParam.getDeparture())  // 出发站
                        .arrival(requestParam.getArrival())  // 到达站
                        .orderTime(new Date())  // 订单时间
                        .source(SourceEnum.INTERNET.getCode())  // 订单来源
                        .trainNumber(trainDO.getTrainNumber())  // 车次号
                        .departureTime(trainStationRelationDO.getDepartureTime())  // 出发时间
                        .arrivalTime(trainStationRelationDO.getArrivalTime())  // 到达时间
                        .ridingDate(trainStationRelationDO.getDepartureTime())  // 乘车日期
                        .userId(UserContext.getUserId())  // 用户ID
                        .username(UserContext.getUsername())  // 用户名
                        .trainId(Long.parseLong(requestParam.getTrainId()))  // 列车ID
                        .ticketOrderItems(orderItemCreateRemoteReqDTOList)  // 订单项列表
                        .build();

                // 调用远程订单服务创建订单
                try {
                    Result<String> ticketOrderResult = ticketOrderRemoteService.createTicketOrder(orderCreateRemoteReqDTO);

                    // 检查远程调用结果，如果失败则抛出异常
                    if (!ticketOrderResult.isSuccess() || StrUtil.isBlank(ticketOrderResult.getData())) {
                        log.error("订单服务调用失败，返回结果：{}", ticketOrderResult.getMessage());
                        throw new ServiceException("订单服务调用失败");
                    }

                    // 设置订单号到AtomicReference中，供方法返回使用
                    orderSn.set(ticketOrderResult.getData());
                } catch (Throwable ex) {
                    // 记录远程调用异常日志
                    log.error("远程调用订单服务创建错误，请求参数：{}", JSON.toJSONString(requestParam), ex);
                    throw ex;
                }
            } catch (Exception ex) {
                // 发生异常时标记事务回滚
                status.setRollbackOnly();
                throw ex;
            }
        });

        // 返回购票响应结果，包含订单号和车票详情列表
        return new TicketPurchaseRespDTO(orderSn.get(), ticketOrderDetailResults);
    }


    @Override
    public PayInfoRespDTO getPayInfo(String orderSn) {
        return payRemoteService.getPayInfo(orderSn).getData();
    }

    @ILog
    @Override
    public void cancelTicketOrder(CancelTicketOrderReqDTO requestParam) {
        Result<Void> cancelOrderResult = ticketOrderRemoteService.cancelTicketOrder(requestParam);
        if (cancelOrderResult.isSuccess() && !StrUtil.equals(ticketAvailabilityCacheUpdateType, "binlog")) {
            Result<com.alander.trip12306.ticketservice.remote.dto.TicketOrderDetailRespDTO> ticketOrderDetailResult = ticketOrderRemoteService.queryTicketOrderByOrderSn(requestParam.getOrderSn());
            com.alander.trip12306.ticketservice.remote.dto.TicketOrderDetailRespDTO ticketOrderDetail = ticketOrderDetailResult.getData();
            String trainId = String.valueOf(ticketOrderDetail.getTrainId());
            String departure = ticketOrderDetail.getDeparture();
            String arrival = ticketOrderDetail.getArrival();
            List<TicketOrderPassengerDetailRespDTO> trainPurchaseTicketResults = ticketOrderDetail.getPassengerDetails();
            try {
                seatService.unlock(trainId, departure, arrival, BeanUtil.convert(trainPurchaseTicketResults, TrainPurchaseTicketRespDTO.class));
            } catch (Throwable ex) {
                log.error("[取消订单] 订单号：{} 回滚列车DB座位状态失败", requestParam.getOrderSn(), ex);
                throw ex;
            }
            ticketAvailabilityTokenBucket.rollbackInBucket(ticketOrderDetail);
            try {
                StringRedisTemplate stringRedisTemplate = (StringRedisTemplate) distributedCache.getInstance();
                Map<Integer, List<TicketOrderPassengerDetailRespDTO>> seatTypeMap = trainPurchaseTicketResults.stream()
                        .collect(Collectors.groupingBy(TicketOrderPassengerDetailRespDTO::getSeatType));
                List<RouteDTO> routeDTOList = trainStationService.listTakeoutTrainStationRoute(trainId, departure, arrival);
                routeDTOList.forEach(each -> {
                    String keySuffix = StrUtil.join("_", trainId, each.getStartStation(), each.getEndStation());
                    seatTypeMap.forEach((seatType, ticketOrderPassengerDetailRespDTOList) -> {
                        stringRedisTemplate.opsForHash()
                                .increment(TRAIN_STATION_REMAINING_TICKET + keySuffix, String.valueOf(seatType), ticketOrderPassengerDetailRespDTOList.size());
                    });
                });
            } catch (Throwable ex) {
                log.error("[取消关闭订单] 订单号：{} 回滚列车Cache余票失败", requestParam.getOrderSn(), ex);
                throw ex;
            }
        }
    }

    @Override
    public RefundTicketRespDTO commonTicketRefund(RefundTicketReqDTO requestParam) {
        // 责任链模式，验证 1：参数必填
        refundReqDTOAbstractChainContext.handler(TicketChainMarkEnum.TRAIN_REFUND_TICKET_FILTER.name(), requestParam);
        Result<com.alander.trip12306.ticketservice.remote.dto.TicketOrderDetailRespDTO> orderDetailRespDTOResult = ticketOrderRemoteService.queryTicketOrderByOrderSn(requestParam.getOrderSn());
        if (!orderDetailRespDTOResult.isSuccess() && Objects.isNull(orderDetailRespDTOResult.getData())) {
            throw new ServiceException("车票订单不存在");
        }
        com.alander.trip12306.ticketservice.remote.dto.TicketOrderDetailRespDTO ticketOrderDetailRespDTO = orderDetailRespDTOResult.getData();
        List<TicketOrderPassengerDetailRespDTO> passengerDetails = ticketOrderDetailRespDTO.getPassengerDetails();
        if (CollectionUtil.isEmpty(passengerDetails)) {
            throw new ServiceException("车票子订单不存在");
        }
        RefundReqDTO refundReqDTO = new RefundReqDTO();
        if (RefundTypeEnum.PARTIAL_REFUND.getType().equals(requestParam.getType())) {
            TicketOrderItemQueryReqDTO ticketOrderItemQueryReqDTO = new TicketOrderItemQueryReqDTO();
            ticketOrderItemQueryReqDTO.setOrderSn(requestParam.getOrderSn());
            ticketOrderItemQueryReqDTO.setOrderItemRecordIds(requestParam.getSubOrderRecordIdReqList());
            Result<List<TicketOrderPassengerDetailRespDTO>> queryTicketItemOrderById = ticketOrderRemoteService.queryTicketItemOrderById(ticketOrderItemQueryReqDTO);
            List<TicketOrderPassengerDetailRespDTO> partialRefundPassengerDetails = passengerDetails.stream()
                    .filter(item -> queryTicketItemOrderById.getData().contains(item))
                    .collect(Collectors.toList());
            refundReqDTO.setRefundTypeEnum(RefundTypeEnum.PARTIAL_REFUND);
            refundReqDTO.setRefundDetailReqDTOList(partialRefundPassengerDetails);
        } else if (RefundTypeEnum.FULL_REFUND.getType().equals(requestParam.getType())) {
            refundReqDTO.setRefundTypeEnum(RefundTypeEnum.FULL_REFUND);
            refundReqDTO.setRefundDetailReqDTOList(passengerDetails);
        }
        if (CollectionUtil.isNotEmpty(passengerDetails)) {
            Integer partialRefundAmount = passengerDetails.stream()
                    .mapToInt(TicketOrderPassengerDetailRespDTO::getAmount)
                    .sum();
            refundReqDTO.setRefundAmount(partialRefundAmount);
        }
        refundReqDTO.setOrderSn(requestParam.getOrderSn());
        Result<RefundRespDTO> refundRespDTOResult = payRemoteService.commonRefund(refundReqDTO);
        if (!refundRespDTOResult.isSuccess() && Objects.isNull(refundRespDTOResult.getData())) {
            throw new ServiceException("车票订单退款失败");
        }
        return null; // 暂时返回空实体
    }

    private final ScheduledExecutorService tokenIsNullRefreshExecutor = Executors.newScheduledThreadPool(1);

    private void tokenIsNullRefreshToken(PurchaseTicketReqDTO requestParam, TokenResultDTO tokenResult) {
        RLock lock = redissonClient.getLock(String.format(LOCK_TOKEN_BUCKET_ISNULL, requestParam.getTrainId()));
        if (!lock.tryLock()) {
            return;
        }
        tokenIsNullRefreshExecutor.schedule(() -> {
            try {
                List<Integer> seatTypes = new ArrayList<>();
                Map<Integer, Integer> tokenCountMap = new HashMap<>();
                tokenResult.getTokenIsNullSeatTypeCounts().stream()
                        .map(each -> each.split("_"))
                        .forEach(split -> {
                            int seatType = Integer.parseInt(split[0]);
                            seatTypes.add(seatType);
                            tokenCountMap.put(seatType, Integer.parseInt(split[1]));
                        });
                List<SeatTypeCountDTO> seatTypeCountDTOList = seatService.listSeatTypeCount(Long.parseLong(requestParam.getTrainId()), requestParam.getDeparture(), requestParam.getArrival(), seatTypes);
                for (SeatTypeCountDTO each : seatTypeCountDTOList) {
                    Integer tokenCount = tokenCountMap.get(each.getSeatType());
                    if (tokenCount <= each.getSeatCount()) {
                        ticketAvailabilityTokenBucket.delTokenInBucket(requestParam);
                        break;
                    }
                }
            } finally {
                lock.unlock();
            }
        }, 10, TimeUnit.SECONDS);
    }

    @Override
    public void run(String... args) throws Exception {
        ticketService = ApplicationContextHolder.getBean(TicketService.class);
    }


    /**
     * 构建出发站列表
     */
    private List<String> buildDepartureStationList(List<TicketListDTO> seatResults) {
        return seatResults.stream().map(TicketListDTO::getDeparture).distinct().collect(Collectors.toList());
    }

    /**
     * 构建到达站列表
     */
    private List<String> buildArrivalStationList(List<TicketListDTO> seatResults) {
        return seatResults.stream().map(TicketListDTO::getArrival).distinct().collect(Collectors.toList());
    }

    /**
     * 构建座位类型列表
     */
    private List<Integer> buildSeatClassList(List<TicketListDTO> seatResults) {
        Set<Integer> resultSeatClassList = new HashSet<>();
        for (TicketListDTO each : seatResults) {
            for (SeatClassDTO item : each.getSeatClassList()) {
                resultSeatClassList.add(item.getType());
            }
        }
        return resultSeatClassList.stream().toList();
    }

    /**
     * 构建车次品牌列表
     */
    private List<Integer> buildTrainBrandList(List<TicketListDTO> seatResults) {
        Set<Integer> trainBrandSet = new HashSet<>();
        for (TicketListDTO each : seatResults) {
            if (StrUtil.isNotBlank(each.getTrainBrand())) {
                trainBrandSet.addAll(StrUtil.split(each.getTrainBrand(), ",").stream().map(Integer::parseInt).toList());
            }
        }
        return trainBrandSet.stream().toList();
    }
}
