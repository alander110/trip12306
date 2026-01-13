package com.alander.trip12306.ticketservice.remote;

import com.alander.trip12306.convention.result.Result;
import com.alander.trip12306.ticketservice.remote.dto.PayInfoRespDTO;
import com.alander.trip12306.ticketservice.remote.dto.RefundReqDTO;
import com.alander.trip12306.ticketservice.remote.dto.RefundRespDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 支付单远程调用服务
*/
@FeignClient(value = "trip12306-pay-service", url = "${aggregation.remote-url:}")
public interface PayRemoteService {

    /**
     * 支付单详情查询
     */
    @GetMapping("/api/pay-service/pay/query")
    Result<PayInfoRespDTO> getPayInfo(@RequestParam(value = "orderSn") String orderSn);

    /**
     * 公共退款接口
     */
    @PostMapping("/api/pay-service/common/refund")
    Result<RefundRespDTO> commonRefund(@RequestBody RefundReqDTO requestParam);
}
