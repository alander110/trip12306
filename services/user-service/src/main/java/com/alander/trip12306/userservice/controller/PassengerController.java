package com.alander.trip12306.userservice.controller;

import com.alander.trip12306.convention.result.Result;
import com.alander.trip12306.idempotent.annotation.Idempotent;
import com.alander.trip12306.idempotent.enums.IdempotentSceneEnum;
import com.alander.trip12306.idempotent.enums.IdempotentTypeEnum;
import com.alander.trip12306.user.core.UserContext;
import com.alander.trip12306.userservice.dto.req.PassengerRemoveReqDTO;
import com.alander.trip12306.userservice.dto.req.PassengerReqDTO;
import com.alander.trip12306.userservice.dto.resp.PassengerRespDTO;
import com.alander.trip12306.userservice.service.PassengerService;
import com.alander.trip12306.web.Results;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 乘车人控制层
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user-service")
public class PassengerController {

    private final PassengerService passengerService;

    /**
     * 根据用户名查询乘车人列表
     */
    @GetMapping("/passenger/query")
    public Result<List<PassengerRespDTO>> listPassengerQueryByUsername() {
        return Results.success(passengerService.listPassengerQueryByUsername(UserContext.getUsername()));
    }

    /**
     * 根据乘车人 ID 集合查询乘车人列表
     */
    @GetMapping("/inner/passenger/actual/query/ids")
    public Result<List<PassengerRespDTO>> listPassengerQueryByIds(@RequestParam("username") String username, @RequestParam("ids") List<Long> ids) {
        return Results.success(passengerService.listPassengerQueryByIds(username, ids));
    }

    /**
     * 新增乘车人
     */
    @Idempotent(
            uniqueKeyPrefix = "trip12306-user:lock_passenger-alter:",
            key = "T(com.alander.trip12306.user.core.UserContext).getUsername()",
            type = IdempotentTypeEnum.SPEL,
            scene = IdempotentSceneEnum.RESTAPI,
            message = "正在新增乘车人，请稍后再试..."
    )
    @PostMapping("/passenger/save")
    public Result<Void> savePassenger(@RequestBody PassengerReqDTO requestParam) {
        passengerService.savePassenger(requestParam);
        return Results.success();
    }

    /**
     * 修改乘车人
     */
    @Idempotent(
            uniqueKeyPrefix = "trip12306-user:lock_passenger-alter:",
            key = "T(com.alander.trip12306.user.core.UserContext).getUsername()",
            type = IdempotentTypeEnum.SPEL,
            scene = IdempotentSceneEnum.RESTAPI,
            message = "正在修改乘车人，请稍后再试..."
    )
    @PostMapping("/passenger/update")
    public Result<Void> updatePassenger(@RequestBody PassengerReqDTO requestParam) {
        passengerService.updatePassenger(requestParam);
        return Results.success();
    }

    /**
     * 移除乘车人
     */
    @PostMapping("/passenger/remove")
    public Result<Void> removePassenger(@RequestBody PassengerRemoveReqDTO requestParam) {
        passengerService.removePassenger(requestParam);
        return Results.success();
    }
}
