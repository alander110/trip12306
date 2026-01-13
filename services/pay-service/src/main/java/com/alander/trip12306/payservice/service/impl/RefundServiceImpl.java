package com.alander.trip12306.payservice.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.alander.trip12306.payservice.common.enums.TradeStatusEnum;
import com.alander.trip12306.payservice.convert.RefundRequestConvert;
import com.alander.trip12306.payservice.dao.entity.PayDO;
import com.alander.trip12306.payservice.dao.entity.RefundDO;
import com.alander.trip12306.payservice.dao.mapper.PayMapper;
import com.alander.trip12306.payservice.dao.mapper.RefundMapper;
import com.alander.trip12306.payservice.dto.RefundCommand;
import com.alander.trip12306.payservice.dto.RefundCreateDTO;
import com.alander.trip12306.payservice.dto.RefundReqDTO;
import com.alander.trip12306.payservice.dto.RefundRespDTO;
import com.alander.trip12306.payservice.dto.base.RefundRequest;
import com.alander.trip12306.payservice.dto.base.RefundResponse;
import com.alander.trip12306.payservice.handler.AliRefundNativeHandler;
import com.alander.trip12306.payservice.mq.event.RefundResultCallbackOrderEvent;
import com.alander.trip12306.payservice.mq.produce.RefundResultCallbackOrderSendProduce;
import com.alander.trip12306.payservice.remote.TicketOrderRemoteService;
import com.alander.trip12306.payservice.remote.dto.TicketOrderDetailRespDTO;
import com.alander.trip12306.payservice.service.RefundService;
import com.alander.trip12306.common.toolkit.BeanUtil;
import com.alander.trip12306.convention.exception.ServiceException;
import com.alander.trip12306.convention.result.Result;
import com.alander.trip12306.designpattern.strategy.AbstractStrategyChoose;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Objects;


/**
 * 退款接口层实现
*/
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private final PayMapper payMapper;
    private final RefundMapper refundMapper;
    private final TicketOrderRemoteService ticketOrderRemoteService;
    private final AbstractStrategyChoose abstractStrategyChoose;
    private final RefundResultCallbackOrderSendProduce refundResultCallbackOrderSendProduce;

    @Override
    @Transactional
    public RefundRespDTO commonRefund(RefundReqDTO requestParam) {
        LambdaQueryWrapper<PayDO> queryWrapper = Wrappers.lambdaQuery(PayDO.class)
                .eq(PayDO::getOrderSn, requestParam.getOrderSn());
        PayDO payDO = payMapper.selectOne(queryWrapper);
        if (Objects.isNull(payDO)) {
            log.error("支付单不存在，orderSn：{}", requestParam.getOrderSn());
            throw new ServiceException("支付单不存在");
        }
        payDO.setPayAmount(payDO.getTotalAmount() - requestParam.getRefundAmount());
        //创建退款单
        RefundCreateDTO refundCreateDTO = BeanUtil.convert(requestParam, RefundCreateDTO.class);
        refundCreateDTO.setPaySn(payDO.getPaySn());
        createRefund(refundCreateDTO);
        /**
         * {@link AliRefundNativeHandler}
         */
        // 策略模式：通过策略模式封装退款渠道和退款场景，用户退款时动态选择对应的退款组件
        RefundCommand refundCommand = BeanUtil.convert(payDO, RefundCommand.class);
        refundCommand.setPayAmount(new BigDecimal(requestParam.getRefundAmount()));
        RefundRequest refundRequest = RefundRequestConvert.command2RefundRequest(refundCommand);
        RefundResponse result = abstractStrategyChoose.chooseAndExecuteResp(refundRequest.buildMark(), refundRequest);
        payDO.setStatus(result.getStatus());
        LambdaUpdateWrapper<PayDO> updateWrapper = Wrappers.lambdaUpdate(PayDO.class)
                .eq(PayDO::getOrderSn, requestParam.getOrderSn());
        int updateResult = payMapper.update(payDO, updateWrapper);
        if (updateResult <= 0) {
            log.error("修改支付单退款结果失败，支付单信息：{}", JSON.toJSONString(payDO));
            throw new ServiceException("修改支付单退款结果失败");
        }
        LambdaUpdateWrapper<RefundDO> refundUpdateWrapper = Wrappers.lambdaUpdate(RefundDO.class)
                .eq(RefundDO::getOrderSn, requestParam.getOrderSn());
        RefundDO refundDO = new RefundDO();
        refundDO.setTradeNo(result.getTradeNo());
        refundDO.setStatus(result.getStatus());
        int refundUpdateResult = refundMapper.update(refundDO, refundUpdateWrapper);
        if (refundUpdateResult <= 0) {
            log.error("修改退款单退款结果失败，退款单信息：{}", JSON.toJSONString(refundDO));
            throw new ServiceException("修改退款单退款结果失败");
        }
        // 退款成功，回调订单服务告知退款结果，修改订单流转状态
        if (Objects.equals(result.getStatus(), TradeStatusEnum.TRADE_CLOSED.tradeCode())) {
            RefundResultCallbackOrderEvent refundResultCallbackOrderEvent = RefundResultCallbackOrderEvent.builder()
                    .orderSn(requestParam.getOrderSn())
                    .refundTypeEnum(requestParam.getRefundTypeEnum())
                    .partialRefundTicketDetailList(requestParam.getRefundDetailReqDTOList())
                    .build();
            refundResultCallbackOrderSendProduce.sendMessage(refundResultCallbackOrderEvent);
        }
        // 当前无业务数据返回，为了避免后续发展，暂时返回空实体
        return new RefundRespDTO();
    }

    private void createRefund(RefundCreateDTO requestParam) {
        Result<TicketOrderDetailRespDTO> queryTicketResult = ticketOrderRemoteService.queryTicketOrderByOrderSn(requestParam.getOrderSn());
        if (!queryTicketResult.isSuccess() && Objects.isNull(queryTicketResult.getData())) {
            throw new ServiceException("车票订单不存在");
        }
        TicketOrderDetailRespDTO orderDetailRespDTO = queryTicketResult.getData();
        requestParam.getRefundDetailReqDTOList().forEach(each -> {
            RefundDO refundDO = new RefundDO();
            refundDO.setPaySn(requestParam.getPaySn());
            refundDO.setOrderSn(requestParam.getOrderSn());
            refundDO.setTrainId(orderDetailRespDTO.getTrainId());
            refundDO.setTrainNumber(orderDetailRespDTO.getTrainNumber());
            refundDO.setDeparture(orderDetailRespDTO.getDeparture());
            refundDO.setArrival(orderDetailRespDTO.getArrival());
            refundDO.setDepartureTime(orderDetailRespDTO.getDepartureTime());
            refundDO.setArrivalTime(orderDetailRespDTO.getArrivalTime());
            refundDO.setRidingDate(orderDetailRespDTO.getRidingDate());
            refundDO.setSeatType(each.getSeatType());
            refundDO.setIdType(each.getIdType());
            refundDO.setIdCard(each.getIdCard());
            refundDO.setRealName(each.getRealName());
            refundDO.setRefundTime(new Date());
            refundDO.setAmount(each.getAmount());
            refundDO.setUserId(each.getUserId());
            refundDO.setUsername(each.getUsername());
            refundMapper.insert(refundDO);
        });
    }
}