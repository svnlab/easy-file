package org.svnee.easyfile.starter.spring.boot.autoconfig;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.MQProducer;
import org.apache.rocketmq.common.UtilAll;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.svnee.easyfile.common.constants.Constants;
import org.svnee.easyfile.common.util.IpUtil;
import org.svnee.easyfile.starter.trigger.handler.MqTriggerAsyncFileHandler;
import org.svnee.easyfile.core.executor.trigger.MQTriggerHandler;
import org.svnee.easyfile.core.executor.trigger.MQTriggerProducer;
import org.svnee.easyfile.starter.trigger.RocketMQTriggerConsumer;
import org.svnee.easyfile.starter.trigger.RocketMQTriggerProducer;
import org.svnee.easyfile.starter.spring.boot.autoconfig.properties.EasyFileDownloadProperties;
import org.svnee.easyfile.starter.spring.boot.autoconfig.properties.RocketMqAsyncHandlerProperties;
import org.svnee.easyfile.storage.download.DownloadStorageService;
import org.svnee.easyfile.storage.download.DownloadTriggerService;
import org.svnee.easyfile.storage.file.UploadService;

/**
 * @author svnee
 **/
@Slf4j
@Configuration
@ConditionalOnBean(DownloadTriggerService.class)
@ConditionalOnClass(MQProducer.class)
@EnableConfigurationProperties({RocketMqAsyncHandlerProperties.class})
@ConditionalOnProperty(prefix = EasyFileDownloadProperties.PREFIX, name = "async-trigger-type", havingValue = "rocketmq")
@AutoConfigureAfter(EasyFileCreatorAutoConfiguration.class)
public class RocketMqAsyncFileHandlerAutoConfiguration {

    @Bean
    @Primary
    public MqTriggerAsyncFileHandler mqTriggerAsyncFileHandler(EasyFileDownloadProperties easyFileDownloadProperties,
        UploadService uploadService,
        DownloadStorageService downloadStorageService,
        DownloadTriggerService downloadTriggerService,
        MQTriggerProducer mqTriggerProducer,
        RocketMqAsyncHandlerProperties mqAsyncHandlerProperties) {
        return new MqTriggerAsyncFileHandler(easyFileDownloadProperties, uploadService, downloadStorageService,
            downloadTriggerService,
            mqAsyncHandlerProperties,
            mqTriggerProducer);
    }

    @Bean
    @ConditionalOnMissingBean(MQTriggerHandler.class)
    public MQTriggerHandler mqTriggerHandler(MqTriggerAsyncFileHandler mqTriggerAsyncFileHandler) {
        return mqTriggerAsyncFileHandler;
    }

    @Bean
    @ConditionalOnMissingBean(value = MQTriggerProducer.class, name = "mqTriggerProducer")
    public MQTriggerProducer mqTriggerProducer(RocketMqAsyncHandlerProperties mqAsyncHandlerProperties,
        MQProducer easyFileTriggerMQProducer) {
        return new RocketMQTriggerProducer(mqAsyncHandlerProperties, easyFileTriggerMQProducer);
    }

    @Bean
    @ConditionalOnMissingBean(RocketMQTriggerConsumer.class)
    public RocketMQTriggerConsumer rocketMQTriggerConsumer(RocketMqAsyncHandlerProperties mqAsyncHandlerProperties,
        EasyFileDownloadProperties downloadProperties,
        MQTriggerHandler mqTriggerHandler) {
        return new RocketMQTriggerConsumer(mqAsyncHandlerProperties, downloadProperties, mqTriggerHandler);
    }

    @Bean
    @ConditionalOnMissingBean(value = MQProducer.class, name = "easyFileTriggerMQProducer")
    public MQProducer easyFileTriggerMQProducer(RocketMqAsyncHandlerProperties mqAsyncHandlerProperties,
        EasyFileDownloadProperties easyFileDownloadProperties) {
        DefaultMQProducer producer = new DefaultMQProducer(mqAsyncHandlerProperties.getProduceGroup());
        producer.setNamesrvAddr(mqAsyncHandlerProperties.getHost());
        producer.setVipChannelEnabled(false);
        producer.setRetryTimesWhenSendAsyncFailed(mqAsyncHandlerProperties.getProduceTryTimes());
        producer.setSendLatencyFaultEnable(mqAsyncHandlerProperties.isProduceLatencyFaultEnable());
        String ipAddress = IpUtil.getIp();
        String[] split = ipAddress.split("\\.");
        producer.setInstanceName(
            Constants.EASY_FILE_SENDER + "@" + easyFileDownloadProperties.getAppId() + "@" + split[split.length - 1]
                + "@" + UtilAll.getPid());
        producer.setClientIP(ipAddress);
        try {
            producer.start();
        } catch (MQClientException ex) {
            log.error("EasyFileTriggerMQProducer producer start error", ex);
            ExceptionUtils.rethrow(ex);
        }
        return producer;
    }

}
