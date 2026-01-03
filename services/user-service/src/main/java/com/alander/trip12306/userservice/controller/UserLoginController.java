package com.alander.trip12306.userservice.controller;

import com.alander.trip12306.convention.result.Result;
import com.alander.trip12306.userservice.dto.req.UserLoginReqDTO;
import com.alander.trip12306.userservice.dto.resp.UserLoginRespDTO;
import com.alander.trip12306.userservice.service.UserLoginService;
import com.alander.trip12306.web.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户登陆控制层
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user-service")
public class UserLoginController {

    private final UserLoginService userLoginService;

    /**
     * 用户登录
     */
    @PostMapping("/v1/login")
    public Result<UserLoginRespDTO> login(@RequestBody UserLoginReqDTO requestParam) {
        return Results.success(userLoginService.login(requestParam));
    }

    /**
     * 通过 Token 检查用户是否登录
     */
    @GetMapping("/check-login")
    public Result<UserLoginRespDTO> checkLogin(@RequestParam("accessToken") String accessToken) {
        UserLoginRespDTO result = userLoginService.checkLogin(accessToken);
        return Results.success(result);
    }

    /**
     * 用户退出登录
     */
    @GetMapping("/logout")
    public Result<Void> logout(@RequestParam(required = false) String accessToken) {
        userLoginService.logout(accessToken);
        return Results.success();
    }
}
