package com.alander.trip12306.userservice.service.impl;

import com.alander.trip12306.cache.DistributedCache;
import com.alander.trip12306.common.toolkit.BeanUtil;
import com.alander.trip12306.convention.exception.ClientException;
import com.alander.trip12306.convention.exception.ServiceException;
import com.alander.trip12306.designpattern.chain.AbstractChainContext;
import com.alander.trip12306.user.core.UserContext;
import com.alander.trip12306.user.core.UserInfoDTO;
import com.alander.trip12306.user.toolkit.JWTUtil;
import com.alander.trip12306.userservice.common.enums.UserChainMarkEnum;
import com.alander.trip12306.userservice.dao.entity.*;
import com.alander.trip12306.userservice.dao.mapper.*;
import com.alander.trip12306.userservice.dto.req.UserDeletionReqDTO;
import com.alander.trip12306.userservice.dto.req.UserLoginReqDTO;
import com.alander.trip12306.userservice.dto.req.UserRegisterReqDTO;
import com.alander.trip12306.userservice.dto.resp.UserLoginRespDTO;
import com.alander.trip12306.userservice.dto.resp.UserQueryRespDTO;
import com.alander.trip12306.userservice.dto.resp.UserRegisterRespDTO;
import com.alander.trip12306.userservice.service.UserLoginService;
import com.alander.trip12306.userservice.service.UserService;
import com.alander.trip12306.userservice.toolkit.PasswordUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static com.alander.trip12306.userservice.common.constant.RedisKeyConstant.*;
import static com.alander.trip12306.userservice.common.enums.UserRegisterErrorCodeEnum.*;
import static com.alander.trip12306.userservice.toolkit.UserReuseUtil.hashShardingIdx;

/**
 * 用户登录接口实现层
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserLoginServiceImpl implements UserLoginService {

    private final UserMailMapper userMailMapper;
    private final UserPhoneMapper userPhoneMapper;
    private final UserMapper userMapper;
    private final DistributedCache distributedCache;
    private final RedissonClient redissonClient;
    private final AbstractChainContext<UserRegisterReqDTO> abstractChainContext;
    private final RBloomFilter<String> userRegisterCachePenetrationBloomFilter;
    private final UserReuseMapper userReuseMapper;
    private final UserService userService;
    private final UserDeletionMapper userDeletionMapper;

    @Override
    public UserLoginRespDTO login(UserLoginReqDTO requestParam) {
        String usernameOrMailOrPhone = requestParam.getUsernameOrMailOrPhone();
        boolean mailFlag = false;
        for (char c : usernameOrMailOrPhone.toCharArray()) {
            if (c == '@') {
                mailFlag = true;
                break;
            }
        }
        String username;
        if (mailFlag) {
            // 通过邮箱查询用户名
            LambdaQueryWrapper<UserMailDO> queryWrapper = Wrappers.lambdaQuery(UserMailDO.class)
                    .eq(UserMailDO::getMail, usernameOrMailOrPhone);
            username = Optional.ofNullable(userMailMapper.selectOne(queryWrapper))
                    .map(UserMailDO::getUsername)
                    .orElseThrow(() -> new ClientException("用户名/手机号/邮箱不存在"));
        } else {
            // 通过手机号查询用户名
            LambdaQueryWrapper<UserPhoneDO> queryWrapper = Wrappers.lambdaQuery(UserPhoneDO.class)
                    .eq(UserPhoneDO::getPhone, usernameOrMailOrPhone);
            username = Optional.ofNullable(userPhoneMapper.selectOne(queryWrapper))
                    .map(UserPhoneDO::getUsername)
                    .orElse(null);
        }
        // 邮箱和手机号都获取不到，则使用用户名
        username = Optional.ofNullable(username).orElse(usernameOrMailOrPhone);
        // 根据用户名和密码查询用户信息
        LambdaQueryWrapper<UserDO> queryWrapper = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, username)
                .select(UserDO::getId, UserDO::getUsername, UserDO::getRealName);
        UserDO userDO = userMapper.selectOne(queryWrapper);
        if (userDO != null) {
            if (!PasswordUtil.matches(requestParam.getPassword(), userDO.getPassword())) {
                throw new ClientException("输入密码错误");
            }
            // 构建用户信息并生成访问令牌
            UserInfoDTO userInfo = UserInfoDTO.builder()
                    .userId(userDO.getId().toString())
                    .username(userDO.getUsername())
                    .realName(userDO.getRealName())
                    .build();
            String accessToken = JWTUtil.generateAccessToken(userInfo);
            UserLoginRespDTO userLogin = UserLoginRespDTO.builder()
                    .userId(userInfo.getUserId())
                    .username(userInfo.getUsername())
                    .realName(userInfo.getRealName())
                    .accessToken(accessToken)
                    .build();
            // 将登录信息存入分布式缓存
            distributedCache.put(accessToken, JSON.toJSONString(userLogin));
            return userLogin;
        }
        throw new ServiceException("账号不存在或密码错误");
    }

    @Override
    public UserLoginRespDTO checkLogin(String accessToken) {
        return distributedCache.get(accessToken, UserLoginRespDTO.class);
    }

    @Override
    public void logout(String accessToken) {
        if (StringUtils.isNotBlank(accessToken)) {
            distributedCache.delete(accessToken);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public UserRegisterRespDTO register(UserRegisterReqDTO requestParam) {
        abstractChainContext.handler(UserChainMarkEnum.USER_REGISTER_FILTER.name(), requestParam);
        RLock lock = redissonClient.getLock(LOCK_USER_REGISTER_KEY + requestParam.getUsername());
        boolean tryLock = lock.tryLock();
        if (!tryLock) {
            throw new ServiceException(HAS_USERNAME_NOTNULL);
        }
        try {
            try {
                requestParam.setPassword(PasswordUtil.encodePassword(requestParam.getPassword()));
                int insert = userMapper.insert(BeanUtil.convert(requestParam, UserDO.class));
                if (insert < 1) {
                    throw new ServiceException(USER_REGISTER_FAIL);
                }
            } catch (DuplicateKeyException dke) {
                log.error("用户名[{}]重复注册", requestParam.getUsername());
                throw new ServiceException(HAS_USERNAME_NOTNULL);
            }
            UserPhoneDO userPhoneDO = UserPhoneDO.builder()
                    .username(requestParam.getUsername())
                    .phone(requestParam.getPhone())
                    .build();
            try {
                int insert = userPhoneMapper.insert(userPhoneDO);
            } catch (DuplicateKeyException dke) {
                log.error("用户[{}]注册手机号[{}]重复", requestParam.getUsername(), requestParam.getPhone());
                throw new ServiceException(PHONE_REGISTERED);
            }


            if (StringUtils.isNotBlank(requestParam.getMail())) {
                UserMailDO userMailDO = UserMailDO.builder()
                        .username(requestParam.getUsername())
                        .mail(requestParam.getMail())
                        .build();
                try {
                    int insert = userMailMapper.insert(userMailDO);
                } catch (DuplicateKeyException dke) {
                    log.error("用户[{}]注册邮箱[{}]重复", requestParam.getUsername(), requestParam.getMail());
                    throw new ServiceException(MAIL_REGISTERED);
                }
                String username = requestParam.getUsername();
                userReuseMapper.delete(Wrappers.update(new UserReuseDO(username)));
                StringRedisTemplate instance = (StringRedisTemplate) distributedCache.getInstance();
                instance.opsForSet().remove(USER_REGISTER_REUSE_SHARDING_KEY + hashShardingIdx(username), username);
                userRegisterCachePenetrationBloomFilter.add(username);
            }
        } finally {
            lock.unlock();
        }
        return BeanUtil.convert(requestParam, UserRegisterRespDTO.class);
    }

    @Override
    public Boolean hasUsername(String username) {
        boolean hasUsername = userRegisterCachePenetrationBloomFilter.contains(username);
        if (hasUsername) {
            StringRedisTemplate instance = (StringRedisTemplate) distributedCache.getInstance();
            return instance.opsForSet().isMember(USER_REGISTER_REUSE_SHARDING_KEY + hashShardingIdx(username), username);
        }
        return true;
    }


    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deletion(UserDeletionReqDTO requestParam) {
        String username = UserContext.getUsername();
        if (username.equals(requestParam.getUsername())) {
            throw new ClientException("注销账号与登录账号不一致");
        }
        RLock lock = redissonClient.getLock(LOCK_USER_DELETION_LEY + username);
        lock.lock();
        try {
            // 查询当前用户信息
            UserQueryRespDTO userQueryRespDTO = userService.queryUserByUsername(username);

            // 插入用户注销记录到注销表
            UserDeletionDO userDeletionDO = UserDeletionDO.builder()
                    .idCard(userQueryRespDTO.getIdCard())
                    .idType(userQueryRespDTO.getIdType())
                    .build();
            userDeletionMapper.insert(userDeletionDO);

            // 更新用户表中的删除时间，标记用户为已删除状态
            UserDO userDO = new UserDO();
            userDO.setDeletionTime(System.currentTimeMillis());
            userDO.setUsername(username);
            userMapper.deletionUser(userDO);

            // 更新用户手机号表中的删除时间
            UserPhoneDO userPhoneDO = UserPhoneDO.builder()
                    .deletionTime(System.currentTimeMillis())
                    .phone(userQueryRespDTO.getPhone())
                    .build();
            userPhoneMapper.deletionUser(userPhoneDO);

            // 如果用户邮箱不为空，则更新用户邮箱表中的删除时间
            if (StringUtils.isNotBlank(userQueryRespDTO.getMail())) {
                UserMailDO userMailDO = UserMailDO.builder()
                        .mail(userQueryRespDTO.getMail())
                        .deletionTime(System.currentTimeMillis())
                        .build();
                userMailMapper.deletionUser(userMailDO);
            }

            // 删除缓存中的用户token
            distributedCache.delete(UserContext.getToken());

            // 记录可重用用户名到重用表
            userReuseMapper.insert(new UserReuseDO(username));
            StringRedisTemplate instance = (StringRedisTemplate) distributedCache.getInstance();
            instance.opsForSet().add(USER_REGISTER_REUSE_SHARDING_KEY + hashShardingIdx(username), username);

            // 统计同一身份证类型的注销次数并更新缓存
            LambdaQueryWrapper<UserDeletionDO> queryWrapper = Wrappers.lambdaQuery(UserDeletionDO.class)
                    .eq(UserDeletionDO::getIdType, userQueryRespDTO.getIdType())
                    .eq(UserDeletionDO::getIdCard, userQueryRespDTO.getIdCard());
            Long userDeletionCount = userDeletionMapper.selectCount(queryWrapper);
            distributedCache.putForever(String.format(USER_DELETION_COUNT_KEY, userQueryRespDTO.getIdType(), userQueryRespDTO.getIdCard()), userDeletionCount);
        } finally {
            lock.unlock();
        }
    }

}
