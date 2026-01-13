package com.alander.trip12306.payservice.controller;

import lombok.RequiredArgsConstructor;
import com.alander.trip12306.payservice.dto.RefundReqDTO;
import com.alander.trip12306.payservice.dto.RefundRespDTO;
import com.alander.trip12306.payservice.service.RefundService;
import com.alander.trip12306.convention.result.Result;
import com.alander.trip12306.web.Results;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 退款控制层
*/
@RestController
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    /**
     * 公共退款接口
     */
    @PostMapping("/api/pay-service/common/refund")
    public Result<RefundRespDTO> commonRefund(@RequestBody RefundReqDTO requestParam) {
        return Results.success(refundService.commonRefund(requestParam));
    }
}
