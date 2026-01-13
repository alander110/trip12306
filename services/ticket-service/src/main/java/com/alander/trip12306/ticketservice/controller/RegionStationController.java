package com.alander.trip12306.ticketservice.controller;

import com.alander.trip12306.convention.result.Result;
import com.alander.trip12306.ticketservice.dto.req.RegionStationQueryReqDTO;
import com.alander.trip12306.ticketservice.dto.resp.RegionStationQueryRespDTO;
import com.alander.trip12306.ticketservice.dto.resp.StationQueryRespDTO;
import com.alander.trip12306.ticketservice.service.RegionStationService;
import com.alander.trip12306.web.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 地区以及车站查询控制层
 */
@RestController
@RequiredArgsConstructor
public class RegionStationController {

    private final RegionStationService regionStationService;

    /**
     * 查询车站&城市站点集合信息
     */
    @GetMapping("/api/ticket-service/region-station/query")
    public Result<List<RegionStationQueryRespDTO>> listRegionStation(RegionStationQueryReqDTO requestParam) {
        return Results.success(regionStationService.listRegionStation(requestParam));
    }

    /**
     * 查询车站站点集合信息
     */
    @GetMapping("/api/ticket-service/station/all")
    public Result<List<StationQueryRespDTO>> listAllStation() {
        return Results.success(regionStationService.listAllStation());
    }

}
