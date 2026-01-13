package com.alander.trip12306.ticketservice.mq.consumer;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import com.alander.trip12306.ticketservice.common.constant.TicketRocketMQConstant;
import com.alander.trip12306.ticketservice.common.enums.CanalExecuteStrategyMarkEnum;
import com.alander.trip12306.ticketservice.mq.event.CanalBinlogEvent;
import com.alander.trip12306.designpattern.strategy.AbstractStrategyChoose;
import com.alander.trip12306.idempotent.annotation.Idempotent;
import com.alander.trip12306.idempotent.enums.IdempotentSceneEnum;
import com.alander.trip12306.idempotent.enums.IdempotentTypeEnum;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 列车车票余量缓存更新消费端
*/
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = TicketRocketMQConstant.CANAL_COMMON_SYNC_TOPIC_KEY,
        consumerGroup = TicketRocketMQConstant.CANAL_COMMON_SYNC_CG_KEY
)
public class CanalCommonSyncBinlogConsumer implements RocketMQListener<CanalBinlogEvent> {

    private final AbstractStrategyChoose abstractStrategyChoose;

    @Value("${ticket.availability.cache-update.type:}")
    private String ticketAvailabilityCacheUpdateType;

    @Idempotent(
            uniqueKeyPrefix = "trip12306-ticket:binlog_sync:",
            key = "#message.getId()+'_'+#message.hashCode()",
            type = IdempotentTypeEnum.SPEL,
            scene = IdempotentSceneEnum.MQ,
            keyTimeout = 7200L
    )
    @Override
    public void onMessage(CanalBinlogEvent message) {
        // 余票 Binlog 更新延迟问题如何解决？详情查看：https://nageoffer.com/12306/question
        if (message.getIsDdl()
                || CollUtil.isEmpty(message.getOld())
                || !Objects.equals("UPDATE", message.getType())
                || !StrUtil.equals(ticketAvailabilityCacheUpdateType, "binlog")) {
            return;
        }
        abstractStrategyChoose.chooseAndExecute(
                message.getTable(),
                message,
                CanalExecuteStrategyMarkEnum.isPatternMatch(message.getTable())
        );
    }
}
