package com.alander.trip12306.ticketservice.dao.entity;

import com.alander.trip12306.database.base.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * 订单追踪实体
*/
@Data
@TableName("t_order_tracking")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderTrackingDO extends BaseDO {

    /**
     * id
     */
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 订单号
     */
    private String orderSn;

    /**
     * 状态 0：请求下单成功 1：列车与余票不足 2：购票请求失败
     */
    private Integer status;
}
