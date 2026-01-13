package com.alander.trip12306.ticketservice.controller;

import com.alander.trip12306.convention.result.Result;
import com.alander.trip12306.ticketservice.dto.req.CancelTicketOrderReqDTO;
import com.alander.trip12306.ticketservice.dto.req.PurchaseTicketReqDTO;
import com.alander.trip12306.ticketservice.dto.req.RefundTicketReqDTO;
import com.alander.trip12306.ticketservice.dto.req.TicketPageQueryReqDTO;
import com.alander.trip12306.ticketservice.dto.resp.OrderTrackingRespDTO;
import com.alander.trip12306.ticketservice.dto.resp.RefundTicketRespDTO;
import com.alander.trip12306.ticketservice.dto.resp.TicketPageQueryRespDTO;
import com.alander.trip12306.ticketservice.dto.resp.TicketPurchaseRespDTO;
import com.alander.trip12306.ticketservice.remote.dto.PayInfoRespDTO;
import com.alander.trip12306.ticketservice.service.TicketService;
import com.alander.trip12306.web.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 车票控制层
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ticket-service/ticket")
public class TicketController {

    private final TicketService ticketService;

    /**
     * 根据条件查询车票
     */
    @GetMapping("/query")
    public Result<TicketPageQueryRespDTO> pageListTicketQuery(TicketPageQueryReqDTO requestParam) {
        return Results.success(ticketService.pageListTicketQueryV1(requestParam));
    }

    /**
     * 购买车票
     */
    @PostMapping("/purchase")
    public Result<TicketPurchaseRespDTO> purchaseTickets(@RequestBody PurchaseTicketReqDTO requestParam) {
        return Results.success(ticketService.purchaseTicketsV1(requestParam));
    }

    /**
     * 购买车票v2
     */
    @PostMapping("/purchase/v2")
    public Result<TicketPurchaseRespDTO> purchaseTicketsV2(@RequestBody PurchaseTicketReqDTO requestParam) {
        return Results.success(ticketService.purchaseTicketsV2(requestParam));
    }

    /**
     * 购买车票v3
     */
    @PostMapping("/purchase/v3")
    public Result<String> purchaseTicketsV3(@RequestBody PurchaseTicketReqDTO requestParam) {
        return Results.success(ticketService.purchaseTicketsV3(requestParam));
    }

    /**
     * 购买车票v3查询订单结果
     */
    @GetMapping("/purchase/v3/query")
    public Result<OrderTrackingRespDTO> purchaseTicketsV3Query(@RequestParam(value = "orderTrackingId") String orderTrackingId) {
        return Results.success(ticketService.purchaseTicketsV3Query(orderTrackingId));
    }

    /**
     * 取消车票订单
     */
    @PostMapping("/cancel")
    public Result<Void> cancelTicketOrder(@RequestBody CancelTicketOrderReqDTO requestParam) {
        ticketService.cancelTicketOrder(requestParam);
        return Results.success();
    }

    /**
     * 支付单详情查询
     */
    @GetMapping("/pay/query")
    public Result<PayInfoRespDTO> getPayInfo(@RequestParam(value = "orderSn") String orderSn) {
        return Results.success(ticketService.getPayInfo(orderSn));
    }

    /**
     * 公共退款接口
     */
    @PostMapping("/refund")
    public Result<RefundTicketRespDTO> commonTicketRefund(@RequestBody RefundTicketReqDTO requestParam) {
        return Results.success(ticketService.commonTicketRefund(requestParam));
    }

}
