package com.alander.trip12306.userservice.controller;

import com.alander.trip12306.convention.result.Result;
import com.alander.trip12306.userservice.dto.req.UserDeletionReqDTO;
import com.alander.trip12306.userservice.dto.req.UserRegisterReqDTO;
import com.alander.trip12306.userservice.dto.req.UserUpdateReqDTO;
import com.alander.trip12306.userservice.dto.resp.UserQueryActualRespDTO;
import com.alander.trip12306.userservice.dto.resp.UserQueryRespDTO;
import com.alander.trip12306.userservice.dto.resp.UserRegisterRespDTO;
import com.alander.trip12306.userservice.service.UserLoginService;
import com.alander.trip12306.userservice.service.UserService;
import com.alander.trip12306.web.Results;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户信息控制层
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user-service")
public class UserInfoController {

    private final UserService userService;
    private final UserLoginService userLoginService;

    /**
     * 根据用户名查询用户信息
     */
    @GetMapping("/query")
    public Result<UserQueryRespDTO> queryUserByUsername(@RequestParam("username") @NotEmpty String username) {
        return Results.success(userService.queryUserByUsername(username));
    }

    /**
     * 根据用户名查询用户无脱敏信息
     */
    @GetMapping("/actual/query")
    public Result<UserQueryActualRespDTO> queryActualUserByUsername(@RequestParam("username") @NotEmpty String username) {
        return Results.success(userService.queryActualUserByUsername(username));
    }

    /**
     * 检查用户名是否已存在
     */
    @GetMapping("/has-username")
    public Result<Boolean> hasUsername(@RequestParam("username") @NotEmpty String username) {
        return Results.success(userLoginService.hasUsername(username));
    }

    /**
     * 注册用户
     */
    @PostMapping("register")
    public Result<UserRegisterRespDTO> register(@RequestBody @Valid UserRegisterReqDTO requestParam) {
        return Results.success(userLoginService.register(requestParam));
    }

    /**
     * 修改用户
     */
    @PutMapping("update")
    public Result<Void> update(@RequestBody @Valid UserUpdateReqDTO requestParam) {
        userService.update(requestParam);
        return Results.success();
    }

    /**
     * 注销用户
     */
    @PostMapping("/deletion")
    public Result<Void> deletion(@RequestBody @Valid UserDeletionReqDTO requestParam){
        userLoginService.deletion(requestParam);
        return Results.success();
    }

}
