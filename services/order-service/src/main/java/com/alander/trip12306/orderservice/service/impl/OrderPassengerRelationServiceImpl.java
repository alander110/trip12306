package com.alander.trip12306.orderservice.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.alander.trip12306.orderservice.dao.entity.OrderItemPassengerDO;
import com.alander.trip12306.orderservice.dao.mapper.OrderItemPassengerMapper;
import com.alander.trip12306.orderservice.service.OrderPassengerRelationService;
import org.springframework.stereotype.Service;

/**
 * 乘车人订单关系接口层实现
*/
@Service
public class OrderPassengerRelationServiceImpl extends ServiceImpl<OrderItemPassengerMapper, OrderItemPassengerDO> implements OrderPassengerRelationService {
}
