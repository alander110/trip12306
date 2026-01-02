package com.alander.trip12306.user.toolkit;

import com.alander.trip12306.user.core.UserInfoDTO;
import com.alibaba.fastjson2.JSON;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.HashMap;

import static com.alander.trip12306.base.constant.UserConstant.*;

@Slf4j
public class JWTUtil {

    private final static long EXPIRATION = 86400L;
    private final static String TOKEN_PREFIX = "Bearer ";
    private final static String ISS = "trip12306";
    private final static String SECRET = "SecretKey27932739128301239128302938102901";

    /**
     * 生成用户 Token
     *
     * @param userInfo 用户信息
     * @return 用户访问 Token
     */
    public String generateAccessToken(UserInfoDTO userInfo) {
        HashMap<Object, Object> customerUserMap = new HashMap<>();
        customerUserMap.put(USER_ID_KEY, userInfo.getUserId());
        customerUserMap.put(USER_NAME_KEY, userInfo.getUsername());
        customerUserMap.put(REAL_NAME_KEY, userInfo.getRealName());
        String jwtToken = Jwts.builder()
                .signWith(SignatureAlgorithm.HS256, SECRET)
                .setIssuedAt(new Date())
                .setIssuer(ISS)
                .setSubject(JSON.toJSONString(customerUserMap))
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION * 1000))
                .compact();
        return TOKEN_PREFIX + jwtToken;
    }

    /**
     * 解析用户 Token
     *
     * @param jwtToken 用户访问 Token
     * @return 用户信息
     */
    public static UserInfoDTO parseJwtToken(String jwtToken) {
        if (StringUtils.hasText(jwtToken)) {
            String actualJwtToken = jwtToken.replace(TOKEN_PREFIX, "");
            try {
                Claims claims = Jwts.parser()
                        .setSigningKey(SECRET)
                        .parseClaimsJws(actualJwtToken)
                        .getBody();
                Date expiration = claims.getExpiration();
                if (expiration.after(new Date())) {
                    String subject = claims.getSubject();
                    return JSON.parseObject(subject, UserInfoDTO.class);
                }
            } catch (ExpiredJwtException ignored) {
            } catch (Exception ex) {
                log.error("JWT Token解析失败, 请检查", ex);
            }
        }
        return null;
    }

}
