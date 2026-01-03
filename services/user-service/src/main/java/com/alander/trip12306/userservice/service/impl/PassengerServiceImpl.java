package com.alander.trip12306.userservice.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdcardUtil;
import cn.hutool.core.util.PhoneUtil;
import com.alander.trip12306.cache.DistributedCache;
import com.alander.trip12306.common.toolkit.BeanUtil;
import com.alander.trip12306.convention.exception.ClientException;
import com.alander.trip12306.convention.exception.ServiceException;
import com.alander.trip12306.idempotent.annotation.Idempotent;
import com.alander.trip12306.idempotent.enums.IdempotentSceneEnum;
import com.alander.trip12306.idempotent.enums.IdempotentTypeEnum;
import com.alander.trip12306.user.core.UserContext;
import com.alander.trip12306.userservice.common.enums.VerifyStatusEnum;
import com.alander.trip12306.userservice.dao.entity.PassengerDO;
import com.alander.trip12306.userservice.dao.mapper.PassengerMapper;
import com.alander.trip12306.userservice.dto.req.PassengerRemoveReqDTO;
import com.alander.trip12306.userservice.dto.req.PassengerReqDTO;
import com.alander.trip12306.userservice.dto.resp.PassengerRespDTO;
import com.alander.trip12306.userservice.service.PassengerService;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.toolkit.SqlHelper;
import jodd.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.alander.trip12306.userservice.common.constant.RedisKeyConstant.USER_PASSENGER_LIST_KEY;

/**
 * 乘车人接口实现层
 */
@Slf4j
@Service
public class PassengerServiceImpl implements PassengerService {
    private final DistributedCache distributedCache;
    private final PassengerMapper passengerMapper;

    public PassengerServiceImpl(DistributedCache distributedCache, PassengerMapper passengerMapper) {
        this.distributedCache = distributedCache;
        this.passengerMapper = passengerMapper;
    }

    @Override
    public List<PassengerRespDTO> listPassengerQueryByUsername(String username) {
        String actualUserPassengerListStr = getActualUserPassengerListStr(username);
        return Optional.ofNullable(actualUserPassengerListStr)
                .map(each -> JSON.parseArray(each, PassengerDO.class))
                .map(each -> BeanUtil.convert(each, PassengerRespDTO.class))
                .orElse(null);
    }

    public String getActualUserPassengerListStr(String username) {
        return distributedCache.safeGet(
                USER_PASSENGER_LIST_KEY + username,
                String.class,
                () -> {
                    List<PassengerDO> passengerDOList = passengerMapper.selectList(Wrappers.lambdaQuery(PassengerDO.class).eq(PassengerDO::getUsername, username));
                    return CollUtil.isNotEmpty(passengerDOList) ? JSON.toJSONString(passengerDOList) : null;
                },
                1,
                TimeUnit.DAYS

        );
    }

    @Override
    public List<PassengerRespDTO> listPassengerQueryByIds(String username, List<Long> ids) {
        String actualUserPassengerListStr = getActualUserPassengerListStr(username);
        if(StringUtil.isEmpty(actualUserPassengerListStr)){
            return null;
        }
        return JSON.parseArray(actualUserPassengerListStr,PassengerDO.class)
                .stream().filter(passengerDO -> ids.contains(passengerDO.getId()))
                .map(each -> BeanUtil.convert(each,PassengerRespDTO.class))
                .toList();
    }

    @Override
    public void savePassenger(PassengerReqDTO requestParam) {
        verifyPassenger(requestParam);
        String username = UserContext.getUsername();
        try {
            PassengerDO passengerDO = BeanUtil.convert(requestParam, PassengerDO.class);
            passengerDO.setUsername(username);
            passengerDO.setCreateDate(new Date());
            passengerDO.setVerifyStatus(VerifyStatusEnum.REVIEWED.getCode());
            int inserted = passengerMapper.insert(passengerDO);
            if (!SqlHelper.retBool(inserted)) {
                throw new ServiceException(String.format("[%s] 新增乘车人失败", username));
            }
        } catch (Exception ex) {
            if (ex instanceof ServiceException) {
                log.error("{}，请求参数：{}", ex.getMessage(), com.alibaba.fastjson2.JSON.toJSONString(requestParam));
            } else {
                log.error("[{}] 新增乘车人失败，请求参数：{}", username, com.alibaba.fastjson2.JSON.toJSONString(requestParam), ex);
            }
            throw ex;
        }
        delUserPassengerCache(username);
    }

    @Override
    public void updatePassenger(PassengerReqDTO requestParam) {
        verifyPassenger(requestParam);
        String username = UserContext.getUsername();
        try {
            PassengerDO passengerDO = BeanUtil.convert(requestParam, PassengerDO.class);
            passengerDO.setUsername(username);
            LambdaUpdateWrapper<PassengerDO> updateWrapper = Wrappers.lambdaUpdate(PassengerDO.class)
                    .eq(PassengerDO::getUsername, username)
                    .eq(PassengerDO::getId, requestParam.getId());
            int updated = passengerMapper.update(passengerDO, updateWrapper);
            if (!SqlHelper.retBool(updated)) {
                throw new ServiceException(String.format("[%s] 修改乘车人失败", username));
            }
        } catch (Exception ex) {
            if (ex instanceof ServiceException) {
                log.error("{}，请求参数：{}", ex.getMessage(), com.alibaba.fastjson2.JSON.toJSONString(requestParam));
            } else {
                log.error("[{}] 修改乘车人失败，请求参数：{}", username, com.alibaba.fastjson2.JSON.toJSONString(requestParam), ex);
            }
            throw ex;
        }
        delUserPassengerCache(username);
    }

    @Idempotent(
            uniqueKeyPrefix = "index12306-user:lock_passenger-alter:",
            key = "T(com.alander.trip12306.user.core.UserContext).getUsername()",
            type = IdempotentTypeEnum.SPEL,
            scene = IdempotentSceneEnum.RESTAPI,
            message = "正在移除乘车人，请稍后再试..."
    )
    @Override
    public void removePassenger(PassengerRemoveReqDTO requestParam) {
        String username = UserContext.getUsername();
        PassengerDO passengerDO = selectPassenger(username, requestParam.getId());
        if (Objects.isNull(passengerDO)) {
            throw new ClientException("乘车人数据不存在");
        }
        try {
            LambdaUpdateWrapper<PassengerDO> deleteWrapper = Wrappers.lambdaUpdate(PassengerDO.class)
                    .eq(PassengerDO::getUsername, username)
                    .eq(PassengerDO::getId, requestParam.getId());
            // 逻辑删除，修改数据库表记录 del_flag
            int deleted = passengerMapper.delete(deleteWrapper);
            if (!SqlHelper.retBool(deleted)) {
                throw new ServiceException(String.format("[%s] 删除乘车人失败", username));
            }
        } catch (Exception ex) {
            if (ex instanceof ServiceException) {
                log.error("{}，请求参数：{}", ex.getMessage(), com.alibaba.fastjson2.JSON.toJSONString(requestParam));
            } else {
                log.error("[{}] 删除乘车人失败，请求参数：{}", username, com.alibaba.fastjson2.JSON.toJSONString(requestParam), ex);
            }
            throw ex;
        }
        delUserPassengerCache(username);
    }

    private PassengerDO selectPassenger(String username, String passengerId) {
        LambdaQueryWrapper<PassengerDO> queryWrapper = Wrappers.lambdaQuery(PassengerDO.class)
                .eq(PassengerDO::getUsername, username)
                .eq(PassengerDO::getId, passengerId);
        return passengerMapper.selectOne(queryWrapper);
    }

    private void delUserPassengerCache(String username) {
        distributedCache.delete(USER_PASSENGER_LIST_KEY + username);
    }

    private void verifyPassenger(PassengerReqDTO requestParam) {
        int length = requestParam.getRealName().length();
        if (!(length >= 2 && length <= 16)) {
            throw new ClientException("乘车人名称请设置2-16位的长度");
        }
        if (!IdcardUtil.isValidCard(requestParam.getIdCard())) {
            throw new ClientException("乘车人证件号错误");
        }
        if (!PhoneUtil.isMobile(requestParam.getPhone())) {
            throw new ClientException("乘车人手机号错误");
        }
    }
}
