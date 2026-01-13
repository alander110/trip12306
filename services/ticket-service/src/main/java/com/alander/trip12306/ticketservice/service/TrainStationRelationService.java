package com.alander.trip12306.ticketservice.service;

import com.alander.trip12306.ticketservice.dao.entity.TrainStationRelationDO;

public interface TrainStationRelationService {

    /**
     * 查询列车站点关系记录
     *
     * @param trainId   列车 ID
     * @param departure 出发站点
     * @param arrival   到达站点
     * @return
     */
    TrainStationRelationDO findRelation(String trainId, String departure, String arrival);

}
