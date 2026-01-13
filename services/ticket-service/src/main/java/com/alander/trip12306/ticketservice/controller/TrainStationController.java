package com.alander.trip12306.ticketservice.controller;

import com.alander.trip12306.convention.result.Result;
import com.alander.trip12306.ticketservice.dto.resp.TrainStationQueryRespDTO;
import com.alander.trip12306.ticketservice.service.TrainStationService;
import com.alander.trip12306.web.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 列车站点控制层
 */
@RestController
@RequiredArgsConstructor
public class TrainStationController {

    private final TrainStationService trainStationService;

    /**
     * 根据列车 ID 查询站点信息
     */
    @GetMapping("/api/ticket-service/train-station/query")
    public Result<List<TrainStationQueryRespDTO>> listTrainStationQuery(String trainId) {
        return Results.success(trainStationService.listTrainStationQuery(trainId));
    }
}
