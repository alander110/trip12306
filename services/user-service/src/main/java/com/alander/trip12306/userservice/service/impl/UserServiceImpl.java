package com.alander.trip12306.userservice.service.impl;

import com.alander.trip12306.cache.DistributedCache;
import com.alander.trip12306.common.toolkit.BeanUtil;
import com.alander.trip12306.convention.exception.ClientException;
import com.alander.trip12306.userservice.dao.entity.UserDO;
import com.alander.trip12306.userservice.dao.entity.UserMailDO;
import com.alander.trip12306.userservice.dao.mapper.UserMailMapper;
import com.alander.trip12306.userservice.dao.mapper.UserMapper;
import com.alander.trip12306.userservice.dto.req.UserUpdateReqDTO;
import com.alander.trip12306.userservice.dto.resp.UserQueryActualRespDTO;
import com.alander.trip12306.userservice.dto.resp.UserQueryRespDTO;
import com.alander.trip12306.userservice.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Optional;

import static com.alander.trip12306.userservice.common.constant.RedisKeyConstant.USER_DELETION_COUNT_KEY;


/**
 * 用户信息接口实现层
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final DistributedCache distributedCache;
    private final UserMailMapper userMailMapper;

    @Override
    public UserQueryRespDTO queryUserByUsername(String username) {
        LambdaQueryWrapper<UserDO> queryWrapper = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, username);
        UserDO userDO = userMapper.selectOne(queryWrapper);
        if (userDO == null) {
            throw new ClientException("用户不存在,请检查用户名是否正确");
        }
        return BeanUtil.convert(userDO, UserQueryRespDTO.class);
    }

    @Override
    public UserQueryActualRespDTO queryActualUserByUsername(String username) {
        return BeanUtil.convert(queryUserByUsername(username), UserQueryActualRespDTO.class);
    }


    @Override
    public Integer queryUserDeletionNum(Integer idType, String idCard) {
        Integer userDeletionCount = distributedCache.get(String.format(USER_DELETION_COUNT_KEY, idType, idCard),Integer.class);
        return Optional.ofNullable(userDeletionCount).orElse(0);
    }

    @Override
    public void update(UserUpdateReqDTO requestParam) {
        UserQueryRespDTO userQueryRespDTO = queryUserByUsername(requestParam.getUsername());
        UserDO userDO = BeanUtil.convert(requestParam, UserDO.class);
        LambdaUpdateWrapper<UserDO> userUpdateWrapper = Wrappers.lambdaUpdate(UserDO.class)
                .eq(UserDO::getUsername, userDO.getUsername());
        userMapper.update(userDO,userUpdateWrapper);
        if(StringUtils.isNotBlank(requestParam.getMail()) && ! requestParam.getMail().equals(userQueryRespDTO.getMail())){
            LambdaUpdateWrapper<UserMailDO> updateWrapper = Wrappers.lambdaUpdate(UserMailDO.class)
                    .eq(UserMailDO::getMail, userQueryRespDTO.getMail());
            userMailMapper.delete(updateWrapper);
            UserMailDO userMailDO = UserMailDO.builder()
                    .mail(requestParam.getMail())
                    .username(requestParam.getUsername())
                    .build();
            userMailMapper.insert(userMailDO);
        }
    }
}
