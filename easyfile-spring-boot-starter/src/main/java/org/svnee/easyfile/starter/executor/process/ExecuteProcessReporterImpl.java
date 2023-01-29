package org.svnee.easyfile.starter.executor.process;

import lombok.extern.slf4j.Slf4j;
import org.svnee.easyfile.common.constants.Constants;
import org.svnee.easyfile.storage.download.DownloadStorageService;

/**
 * 执行进度上报器
 *
 * @author svnee
 **/
@Slf4j
public class ExecuteProcessReporterImpl implements ExecuteProcessReporter {

    private final Long registerId;
    private final DownloadStorageService downloadStorageService;

    public ExecuteProcessReporterImpl(Long registerId,
        DownloadStorageService downloadStorageService) {
        this.registerId = registerId;
        this.downloadStorageService = downloadStorageService;
    }

    @Override
    public void start() {
        try {
            downloadStorageService.resetExecuteProcess(registerId);
        } catch (Exception ex) {
            log.error("[ExecuteProcessReporterImpl#start] start-error!,registerId:{}", registerId, ex);
        }
    }

    @Override
    public void report(Integer executeProcess) {
        try {
            downloadStorageService.refreshExecuteProgress(registerId, executeProcess);
        } catch (Exception ex) {
            log.error("[ExecuteProcessReporterImpl#report] report-error!,registerId:{},executeProcess:{}", registerId,
                executeProcess, ex);
        }
    }

    @Override
    public void complete() {
        try {
            downloadStorageService.refreshExecuteProgress(registerId, Constants.FULL_PROCESS);
        } catch (Exception ex) {
            log.error("[ExecuteProcessReporterImpl#complete] complete-error!,registerId:{}", registerId, ex);
        }
    }
}
