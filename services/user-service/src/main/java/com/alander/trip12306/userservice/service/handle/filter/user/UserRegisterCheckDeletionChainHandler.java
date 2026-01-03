package com.alander.trip12306.userservice.service.handle.filter.user;

import com.alander.trip12306.convention.exception.ClientException;
import com.alander.trip12306.userservice.dto.req.UserRegisterReqDTO;
import com.alander.trip12306.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserRegisterCheckDeletionChainHandler implements UserRegisterCreateChainFilter<UserRegisterReqDTO> {

    private final UserService userService;


    @Override
    public void handler(UserRegisterReqDTO requestParam) {
        Integer userDeletionNum = userService.queryUserDeletionNum(requestParam.getIdType(), requestParam.getIdCard());
        if (userDeletionNum >= 5) {
            throw new ClientException("证件号多次注销账号已被加入黑名单");
        }
    }

    @Override
    public int getOrder() {
        return 2;
    }
}
