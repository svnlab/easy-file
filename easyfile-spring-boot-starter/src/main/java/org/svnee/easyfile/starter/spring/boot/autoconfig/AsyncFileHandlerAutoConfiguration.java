package org.svnee.easyfile.starter.spring.boot.autoconfig;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.svnee.easyfile.starter.executor.BaseAsyncFileHandler;
import org.svnee.easyfile.starter.executor.BaseDefaultDownloadRejectExecutionHandler;
import org.svnee.easyfile.starter.executor.impl.DefaultAsyncFileHandler;
import org.svnee.easyfile.starter.executor.impl.ScheduleTriggerAsyncFileHandler;
import org.svnee.easyfile.storage.download.DownloadStorageService;
import org.svnee.easyfile.storage.download.DownloadTriggerService;
import org.svnee.easyfile.storage.file.UploadService;

/**
 * @author svnee
 **/
@Slf4j
@Configuration
@EnableConfigurationProperties({DefaultAsyncHandlerThreadPoolProperties.class, ScheduleAsyncHandlerProperties.class})
@Import(MqAsyncFileHandlerAutoConfiguration.class)
public class AsyncFileHandlerAutoConfiguration {

    @Bean
    @ConditionalOnBean(DownloadTriggerService.class)
    @ConditionalOnMissingBean(BaseAsyncFileHandler.class)
    @ConditionalOnProperty(prefix = ScheduleAsyncHandlerProperties.PREFIX, name = "enable", havingValue = "true")
    public BaseAsyncFileHandler scheduleTriggerAsyncFileHandler(EasyFileDownloadProperties easyFileDownloadProperties,
        UploadService uploadService,
        DownloadStorageService downloadStorageService,
        DownloadTriggerService downloadTriggerService,
        BaseDefaultDownloadRejectExecutionHandler baseDefaultDownloadRejectExecutionHandler,
        ScheduleAsyncHandlerProperties scheduleAsyncHandlerProperties) {
        return new ScheduleTriggerAsyncFileHandler(easyFileDownloadProperties, uploadService, downloadStorageService,
            downloadTriggerService,
            scheduleAsyncHandlerProperties,
            baseDefaultDownloadRejectExecutionHandler);
    }

    @Bean
    @ConditionalOnMissingBean(BaseAsyncFileHandler.class)
    public BaseAsyncFileHandler defaultAsyncFileHandler(EasyFileDownloadProperties easyFileDownloadProperties,
        UploadService uploadService,
        DownloadStorageService downloadStorageService,
        BaseDefaultDownloadRejectExecutionHandler baseDefaultDownloadRejectExecutionHandler,
        DefaultAsyncHandlerThreadPoolProperties defaultAsyncHandlerThreadPoolProperties) {
        return new DefaultAsyncFileHandler(easyFileDownloadProperties, uploadService, downloadStorageService,
            baseDefaultDownloadRejectExecutionHandler,
            defaultAsyncHandlerThreadPoolProperties);
    }

}
