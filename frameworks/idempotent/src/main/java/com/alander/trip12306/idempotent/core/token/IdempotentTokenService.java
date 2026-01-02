package com.alander.trip12306.idempotent.core.token;

import com.alander.trip12306.idempotent.core.IdempotentExecuteHandler;

/**
 * Token 实现幂等接口
*/
public interface IdempotentTokenService extends IdempotentExecuteHandler {

    /**
     * 创建幂等验证Token
     */
    String createToken();
}
