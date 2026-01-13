package com.alander.trip12306.ticketservice.dao.entity;

import com.alander.trip12306.database.base.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * 车票实体
*/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_ticket")
public class TicketDO extends BaseDO {

    /**
     * id
     */
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 列车id
     */
    private Long trainId;

    /**
     * 车厢号
     */
    private String carriageNumber;

    /**
     * 座位号
     */
    private String seatNumber;

    /**
     * 乘车人 ID
     */
    private String passengerId;

    /**
     * 车票状态
     */
    private Integer ticketStatus;
}
