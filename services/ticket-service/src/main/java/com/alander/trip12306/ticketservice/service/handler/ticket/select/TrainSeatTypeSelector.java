package com.alander.trip12306.ticketservice.service.handler.ticket.select;

import cn.hutool.core.collection.CollUtil;
import com.alander.trip12306.convention.exception.RemoteException;
import com.alander.trip12306.convention.exception.ServiceException;
import com.alander.trip12306.convention.result.Result;
import com.alander.trip12306.designpattern.strategy.AbstractStrategyChoose;
import com.alander.trip12306.ticketservice.common.enums.PurchaseTicketsErrorCodeEnum;
import com.alander.trip12306.ticketservice.common.enums.VehicleSeatTypeEnum;
import com.alander.trip12306.ticketservice.common.enums.VehicleTypeEnum;
import com.alander.trip12306.ticketservice.dao.entity.TrainStationPriceDO;
import com.alander.trip12306.ticketservice.dao.mapper.TrainStationPriceMapper;
import com.alander.trip12306.ticketservice.dto.domain.PurchaseTicketPassengerDetailDTO;
import com.alander.trip12306.ticketservice.dto.req.PurchaseTicketReqDTO;
import com.alander.trip12306.ticketservice.remote.UserRemoteService;
import com.alander.trip12306.ticketservice.remote.dto.PassengerRespDTO;
import com.alander.trip12306.ticketservice.service.SeatService;
import com.alander.trip12306.ticketservice.service.handler.ticket.dto.SelectSeatDTO;
import com.alander.trip12306.ticketservice.service.handler.ticket.dto.TrainPurchaseTicketRespDTO;
import com.alander.trip12306.user.core.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.toolkit.trace.Trace;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

/**
 * 购票时列车座位选择器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public final class TrainSeatTypeSelector {

    private final SeatService seatService;
    private final UserRemoteService userRemoteService;
    private final TrainStationPriceMapper trainStationPriceMapper;
    private final AbstractStrategyChoose abstractStrategyChoose;
    private final ThreadPoolExecutor selectSeatThreadPoolExecutor;

    /**
     * 选择并锁定座位的核心方法
     * 根据乘客需求选择合适的座位，并将选中的座位进行锁定，防止其他用户重复购买
     *
     * @param trainType    列车类型（如高铁、动车等）
     * @param requestParam 购票请求参数，包含乘客信息、出发到达站等
     * @return 选中的座位信息列表
     */
    @Trace(operationName = "select-and-lock-seat")
    public List<TrainPurchaseTicketRespDTO> selectAndLockSeat(Integer trainType, PurchaseTicketReqDTO requestParam) {
        // 获取请求中的乘客详情列表
        List<PurchaseTicketPassengerDetailDTO> passengerDetails = requestParam.getPassengers();

        // 按座位类型对乘客进行分组，便于后续分别处理不同座位类型的乘客需求
        Map<Integer, List<PurchaseTicketPassengerDetailDTO>> seatTypeMap = passengerDetails.stream()
                .collect(Collectors.groupingBy(PurchaseTicketPassengerDetailDTO::getSeatType));

        // 创建线程安全的列表用于存储最终选座结果
        List<TrainPurchaseTicketRespDTO> actualResult = Collections.synchronizedList(new ArrayList<>(seatTypeMap.size()));

        // 如果有多种座位类型需求，使用线程池并行处理以提高性能
        if (seatTypeMap.size() > 1) {
            List<Future<List<TrainPurchaseTicketRespDTO>>> futureResults = new ArrayList<>(seatTypeMap.size());

            // 遍历每种座位类型，提交任务到线程池并行处理
            seatTypeMap.forEach((seatType, passengerSeatDetails) -> {
                // 线程池参数如何设置？详情查看：https://nageoffer.com/12306/question
                Future<List<TrainPurchaseTicketRespDTO>> completableFuture = selectSeatThreadPoolExecutor
                        .submit(() -> distributeSeats(trainType, seatType, requestParam, passengerSeatDetails));
                futureResults.add(completableFuture);
            });

            // 并行流极端情况下有坑，详情参考：https://nageoffer.com/12306/question
            // 遍历Future结果，获取各个线程的处理结果并合并到结果列表
            futureResults.parallelStream().forEach(completableFuture -> {
                try {
                    actualResult.addAll(completableFuture.get());
                } catch (Exception e) {
                    // 发生异常时抛出票源不足的错误
                    throw new ServiceException(PurchaseTicketsErrorCodeEnum.INSUFFICIENT_TRAIN_TICKETS);
                }
            });
        } else {
            // 如果只有一种座位类型，直接串行处理
            seatTypeMap.forEach((seatType, passengerSeatDetails) -> {
                List<TrainPurchaseTicketRespDTO> aggregationResult = distributeSeats(trainType, seatType, requestParam, passengerSeatDetails);
                actualResult.addAll(aggregationResult);
            });
        }

        // 验证选座结果数量是否与乘客数量匹配，不匹配则抛出票源不足异常
        if (CollUtil.isEmpty(actualResult) || !Objects.equals(actualResult.size(), passengerDetails.size())) {
            throw new ServiceException(PurchaseTicketsErrorCodeEnum.INSUFFICIENT_TRAIN_TICKETS);
        }

        // 提取选中座位的乘客ID列表，用于后续获取乘客详细信息
        List<String> passengerIds = actualResult.stream()
                .map(TrainPurchaseTicketRespDTO::getPassengerId)
                .collect(Collectors.toList());

        // 声明远程调用结果变量
        Result<List<PassengerRespDTO>> passengerRemoteResult;
        List<PassengerRespDTO> passengerRemoteResultList;

        try {
            // 远程调用用户服务，根据乘客ID获取乘客详细信息
            passengerRemoteResult = userRemoteService.listPassengerQueryByIds(UserContext.getUsername(), passengerIds);

            // 检查远程调用结果，如果失败或数据为空则抛出远程异常
            if (!passengerRemoteResult.isSuccess() || CollUtil.isEmpty(passengerRemoteResultList = passengerRemoteResult.getData())) {
                throw new RemoteException("用户服务远程调用查询乘车人相关信息错误");
            }
        } catch (Throwable ex) {
            // 记录远程调用异常日志
            if (ex instanceof RemoteException) {
                log.error("用户服务远程调用查询乘车人相关信息错误，当前用户：{}，请求参数：{}", UserContext.getUsername(), passengerIds);
            } else {
                log.error("用户服务远程调用查询乘车人相关信息错误，当前用户：{}，请求参数：{}", UserContext.getUsername(), passengerIds, ex);
            }
            throw ex;
        }

        // 遍历选中的座位结果，填充乘客详细信息和票价信息
        actualResult.forEach(each -> {
            String passengerId = each.getPassengerId();

            // 从远程获取的乘客信息中查找对应乘客并填充详细信息
            passengerRemoteResultList.stream()
                    .filter(item -> Objects.equals(item.getId(), passengerId))
                    .findFirst()
                    .ifPresent(passenger -> {
                        each.setIdCard(passenger.getIdCard());        // 设置身份证号
                        each.setPhone(passenger.getPhone());          // 设置电话
                        each.setUserType(passenger.getDiscountType()); // 设置用户类型（如学生票等优惠类型）
                        each.setIdType(passenger.getIdType());        // 设置证件类型
                        each.setRealName(passenger.getRealName());    // 设置真实姓名
                    });

            // 构建查询票价的条件，根据列车ID、出发站、到达站和座位类型查询价格
            LambdaQueryWrapper<TrainStationPriceDO> lambdaQueryWrapper = Wrappers.lambdaQuery(TrainStationPriceDO.class)
                    .eq(TrainStationPriceDO::getTrainId, requestParam.getTrainId())
                    .eq(TrainStationPriceDO::getDeparture, requestParam.getDeparture())
                    .eq(TrainStationPriceDO::getArrival, requestParam.getArrival())
                    .eq(TrainStationPriceDO::getSeatType, each.getSeatType())
                    .select(TrainStationPriceDO::getPrice); // 只查询价格字段，优化性能

            // 查询票价信息并设置到座位结果中
            TrainStationPriceDO trainStationPriceDO = trainStationPriceMapper.selectOne(lambdaQueryWrapper);
            each.setAmount(trainStationPriceDO.getPrice()); // 设置金额
        });

        // 购买列车中间站点余票如何更新？详细查看：https://nageoffer.com/12306/question
        // 锁定选中的座位，防止其他用户购买相同的座位
        seatService.lockSeat(requestParam.getTrainId(), requestParam.getDeparture(), requestParam.getArrival(), actualResult);

        // 返回选中的座位信息列表
        return actualResult;
    }


        /**
     * 分配座位
     * 根据列车类型和座位类型选择合适的座位分配策略，并执行座位分配
     *
     * @param trainType 列车类型编码
     * @param seatType 座位类型编码
     * @param requestParam 购票请求参数
     * @param passengerSeatDetails 乘客座位详情列表
     * @return 分配结果的座位信息列表
     */
    private List<TrainPurchaseTicketRespDTO> distributeSeats(Integer trainType, Integer seatType, PurchaseTicketReqDTO requestParam, List<PurchaseTicketPassengerDetailDTO> passengerSeatDetails) {
        // 构建策略选择的key，由列车类型和座位类型组合而成
        String buildStrategyKey = VehicleTypeEnum.findNameByCode(trainType) + VehicleSeatTypeEnum.findNameByCode(seatType);
        // 构建座位选择的DTO对象
        SelectSeatDTO selectSeatDTO = SelectSeatDTO.builder()
                .seatType(seatType)
                .passengerSeatDetails(passengerSeatDetails)
                .requestParam(requestParam)
                .build();
        try {
            return abstractStrategyChoose.chooseAndExecuteResp(buildStrategyKey, selectSeatDTO);
        } catch (ServiceException ex) {
            throw new ServiceException("当前车次列车类型暂未适配，请购买G35或G39车次");
        }
    }

}
