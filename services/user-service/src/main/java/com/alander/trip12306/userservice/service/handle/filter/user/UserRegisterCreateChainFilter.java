package com.alander.trip12306.userservice.service.handle.filter.user;

import com.alander.trip12306.designpattern.chain.AbstractChainHandler;
import com.alander.trip12306.userservice.common.enums.UserChainMarkEnum;
import com.alander.trip12306.userservice.dto.req.UserRegisterReqDTO;

public interface UserRegisterCreateChainFilter<T extends UserRegisterReqDTO> extends AbstractChainHandler<UserRegisterReqDTO> {

    @Override
    default String mark(){
        return UserChainMarkEnum.USER_REGISTER_FILTER.name();
    }
}
