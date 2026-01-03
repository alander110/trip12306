package com.alander.trip12306.userservice.service.handle.filter.user;

import com.alander.trip12306.convention.exception.ClientException;
import com.alander.trip12306.userservice.common.enums.UserRegisterErrorCodeEnum;
import com.alander.trip12306.userservice.dto.req.UserRegisterReqDTO;
import com.alander.trip12306.userservice.service.UserLoginService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 用户注册用户名唯一检验
 */
@Component
@RequiredArgsConstructor
public final class UserRegisterHasUsernameChainHandler implements UserRegisterCreateChainFilter<UserRegisterReqDTO> {

    private final UserLoginService userLoginService;

    @Override
    public void handler(UserRegisterReqDTO requestParam) {
        if(!userLoginService.hasUsername(requestParam.getUsername())){
            throw new ClientException(UserRegisterErrorCodeEnum.HAS_USERNAME_NOTNULL);
        }
    }

    @Override
    public int getOrder() {
        return 1;
    }
}
