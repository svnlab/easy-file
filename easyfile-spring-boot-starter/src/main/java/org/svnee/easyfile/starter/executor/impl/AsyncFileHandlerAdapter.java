package org.svnee.easyfile.starter.executor.impl;

import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Comparator;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.svnee.easyfile.common.annotations.FileExportExecutor;
import org.svnee.easyfile.common.bean.BaseDownloaderRequestContext;
import org.svnee.easyfile.common.bean.DownloaderRequestContext;
import org.svnee.easyfile.common.bean.PageTotalContext;
import org.svnee.easyfile.common.bean.Pair;
import org.svnee.easyfile.common.constants.Constants;
import org.svnee.easyfile.common.dictionary.FileSuffixEnum;
import org.svnee.easyfile.common.dictionary.UploadStatusEnum;
import org.svnee.easyfile.common.exception.EasyFileException;
import org.svnee.easyfile.common.request.LoadingExportCacheRequest;
import org.svnee.easyfile.common.request.UploadCallbackRequest;
import org.svnee.easyfile.common.response.ExportResult;
import org.svnee.easyfile.common.util.CollectionUtils;
import org.svnee.easyfile.common.util.CompressUtils;
import org.svnee.easyfile.common.util.DateFormatUtils;
import org.svnee.easyfile.common.util.FileUtils;
import org.svnee.easyfile.common.util.SpringContextUtil;
import org.svnee.easyfile.common.util.StringUtils;
import org.svnee.easyfile.common.exception.Asserts;
import org.svnee.easyfile.starter.exception.DownloadErrorCode;
import org.svnee.easyfile.starter.exception.GenerateFileErrorCode;
import org.svnee.easyfile.starter.exception.GenerateFileException;
import org.svnee.easyfile.starter.executor.BaseAsyncFileHandler;
import org.svnee.easyfile.starter.executor.BaseDownloadExecutor;
import org.svnee.easyfile.starter.executor.bean.GenerateFileResult;
import org.svnee.easyfile.starter.executor.bean.HandleFileResult;
import org.svnee.easyfile.starter.intercept.DownloadExecutorInterceptor;
import org.svnee.easyfile.starter.intercept.ExecutorInterceptorSupport;
import org.svnee.easyfile.starter.intercept.InterceptorContext;
import org.svnee.easyfile.starter.spring.boot.autoconfig.EasyFileDownloadProperties;
import org.svnee.easyfile.storage.download.DownloadStorageService;
import org.svnee.easyfile.storage.file.UploadService;

/**
 * ????????????????????????
 * ?????????????????????????????????
 *
 * @author svnee
 */
public abstract class AsyncFileHandlerAdapter implements BaseAsyncFileHandler {

    private static final Logger logger = LoggerFactory.getLogger(AsyncFileHandlerAdapter.class);
    private final EasyFileDownloadProperties downloadProperties;
    private final UploadService uploadService;
    private final DownloadStorageService downloadStorageService;

    public AsyncFileHandlerAdapter(EasyFileDownloadProperties downloadProperties, UploadService uploadService,
        DownloadStorageService storageService) {
        this.downloadProperties = downloadProperties;
        this.downloadStorageService = storageService;
        this.uploadService = uploadService;
    }

    @Override
    public boolean handle(BaseDownloadExecutor executor, BaseDownloaderRequestContext baseRequest, Long registerId) {
        ExportResult exportResult = this.handleResult(executor, baseRequest, registerId);
        return UploadStatusEnum.SUCCESS.equals(exportResult.getUploadStatus());
    }

    @Override
    public ExportResult handleResult(BaseDownloadExecutor executor, BaseDownloaderRequestContext baseRequest,
        Long registerId) {
        // ?????????????????????FileExportExecutor
        Class<?> clazz = SpringContextUtil.getRealClass(executor);
        FileExportExecutor exportExecutor = clazz.getDeclaredAnnotation(FileExportExecutor.class);
        // ??????????????????-??????????????????????????????
        if (executor.enableExportCache(baseRequest)) {
            LoadingExportCacheRequest cacheRequest =
                buildLoadingExportCacheRequest(baseRequest, exportExecutor, registerId);
            ExportResult exportResult = downloadStorageService.loadingCacheExportResult(cacheRequest);
            if (Objects.nonNull(exportResult) && UploadStatusEnum.SUCCESS.equals(exportResult.getUploadStatus())) {
                return exportResult;
            }
        }

        // ??????????????????????????????
        if (!downloadStorageService.enableRunning(registerId)) {
            logger.error("[AsyncFileHandlerAdapter#handleResult] this registerId has running!skip,registerId:{}",
                registerId);
            return buildDefaultRejectExportResult(registerId);
        }

        Pair<String, String> fileUrl;
        boolean handleBreakFlag;
        GenerateFileResult genFileResult = null;
        try {
            HandleFileResult handleFileResult = handleFileWithRetry(executor, exportExecutor, baseRequest, registerId);
            genFileResult = handleFileResult.getGenFileResult();
            handleBreakFlag = handleFileResult.getGenFileResult().isHandleBreakFlag();
            fileUrl = handleFileResult.getFileUrlPair();
        } finally {
            // ???????????????????????????????????????
            if (Objects.nonNull(genFileResult)) {
                genFileResult.destroy(logger, downloadProperties.isCleanFileAfterUpload());
            }
        }

        UploadCallbackRequest request = new UploadCallbackRequest();
        request.setRegisterId(registerId);
        request.setSystem(Objects.nonNull(fileUrl) ? fileUrl.getKey() : Constants.NONE_FILE_SYSTEM);
        if (!handleBreakFlag) {
            request.setUploadStatus(UploadStatusEnum.SUCCESS);
            request.setFileUrl(Objects.nonNull(fileUrl) ? fileUrl.getValue() : StringUtils.EMPTY);
        } else {
            request.setUploadStatus(UploadStatusEnum.FAIL);
            Optional.ofNullable(genFileResult).ifPresent(k -> request.setErrorMsg(lessErrorMsg(k.getErrorMsg())));
        }
        downloadStorageService.uploadCallback(request);
        // ???????????????????????????????????????
        ExportResult exportResult = buildExportResult(request);
        executor.asyncCompleteCallback(exportResult, baseRequest);
        return exportResult;
    }

    private void afterHandle(BaseDownloadExecutor executor, BaseDownloaderRequestContext baseRequest,
        ExportResult result, InterceptorContext interceptorContext) {
        ExecutorInterceptorSupport.getInterceptors().stream()
            .sorted(((o1, o2) -> o2.order() - o1.order()))
            .forEach(interceptor -> interceptor.afterExecute(executor, baseRequest, result, interceptorContext));
    }

    private void beforeHandle(BaseDownloadExecutor executor, BaseDownloaderRequestContext baseRequest,
        Long registerId, InterceptorContext interceptorContext) {
        ExecutorInterceptorSupport.getInterceptors().stream()
            .sorted((Comparator.comparingInt(DownloadExecutorInterceptor::order)))
            .forEach(interceptor -> interceptor.beforeExecute(executor, baseRequest, registerId, interceptorContext));
    }

    /**
     * ??????????????????
     *
     * @param executor ?????????
     * @param baseRequest ??????
     * @param registerId ??????ID
     */
    public void doExecute(BaseDownloadExecutor executor, BaseDownloaderRequestContext baseRequest, Long registerId) {
        logger.info("[AsyncFileHandlerAdapter#execute]start,execute!registerId:{}", registerId);
        ExportResult result = null;
        InterceptorContext interceptorContext = InterceptorContext.newInstance();
        try {
            // ????????????????????????
            beforeHandle(executor, baseRequest, registerId, interceptorContext);
            // ????????????
            result = handleResult(executor, baseRequest, registerId);
        } catch (Exception ex) {
            logger.error("[AsyncFileHandlerAdapter#execute]end,execute error!registerId:{}", registerId, ex);
            throw ex;
        } finally {
            // ????????????????????????
            afterHandle(executor, baseRequest, result, interceptorContext);
            PageTotalContext.clear();
        }
        logger.info("[AsyncFileHandlerAdapter#execute]end,execute!registerId:{}", registerId);
    }

    private LoadingExportCacheRequest buildLoadingExportCacheRequest(BaseDownloaderRequestContext baseRequest,
        FileExportExecutor exportExecutor,
        Long registerId) {
        LoadingExportCacheRequest loadingExportCacheRequest = new LoadingExportCacheRequest();
        loadingExportCacheRequest.setRegisterId(registerId);
        loadingExportCacheRequest.setCacheKeyList(CollectionUtils.newArrayList(exportExecutor.cacheKey()));
        loadingExportCacheRequest.setExportParamMap(baseRequest.getOtherMap());
        return loadingExportCacheRequest;
    }

    /**
     * ??????????????????
     *
     * @param executor executor ?????????
     * @param baseRequest baseRequest ??????????????????
     * @param registerId ??????ID
     * @param exportExecutor ???????????????
     * @param tempLocalFilePath ??????????????????????????????
     * @return ??????????????????
     */
    private GenerateFileResult generateFile(BaseDownloadExecutor executor,
        FileExportExecutor exportExecutor,
        BaseDownloaderRequestContext baseRequest,
        Long registerId,
        String tempLocalFilePath) {

        // ????????????????????????
        String fileName = generateEnFileName(baseRequest.getFileSuffix(), exportExecutor, tempLocalFilePath);
        File file = new File(fileName);
        File parentFile = file.getParentFile();

        // ????????????
        StringJoiner errorMsgJoiner = new StringJoiner("|");
        // ??????????????????
        boolean handleBreakFlag = false;
        boolean compress;

        // ?????????????????????
        if (!parentFile.exists()) {
            try {
                boolean mkdirs = parentFile.mkdirs();
                Asserts.isTrue(mkdirs, GenerateFileErrorCode.CREATE_LOCAL_TEMP_FILE_ERROR);
            } catch (Exception ex) {
                logger.error(
                    "[AbstractAsyncFileHandlerAdapter#handle] create dictionary error,registerId:{},downloadCode:{}",
                    registerId, exportExecutor.value(), ex);
                handleBreakFlag = true;
                errorMsgJoiner.add(ex.getMessage());
            }
        }
        if (!file.exists()) {
            try {
                boolean fileHasName = file.createNewFile();
                Asserts.isTrue(fileHasName, GenerateFileErrorCode.FILE_NAME_DUPLICATE_ERROR,
                    GenerateFileException.class);
            } catch (Exception ex) {
                logger.error(
                    "[AbstractAsyncFileHandlerAdapter#handle] handle,create new file error,registerId:{},downloadCode:{},path:{}",
                    registerId, exportExecutor.value(), downloadProperties.getLocalFileTempPath(), ex);
                errorMsgJoiner.add("?????????????????????,????????????:" + downloadProperties.getLocalFileTempPath());
                handleBreakFlag = true;
            }
        }
        if (!handleBreakFlag) {
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
                DownloaderRequestContext requestContext = buildRequestDownloaderRequest(out, baseRequest);
                executor.export(requestContext);
            } catch (Exception exception) {
                logger.error("[AbstractAsyncFileHandlerAdapter#handle] execute file error,downloadCode:{}",
                    exportExecutor.value(),
                    exception);
                errorMsgJoiner.add("????????????????????????:" + exception.getMessage());
                handleBreakFlag = true;
            }
        }
        // ??????????????????
        Pair<Boolean, File> compressResult = compress(file, handleBreakFlag);
        compress = compressResult.getKey();
        return GenerateFileResult
            .build(errorMsgJoiner, file, compressResult.getValue(), handleBreakFlag, compress);
    }

    /**
     * ????????????
     *
     * @param file ??????
     * @param handleBreakFlag ??????????????????
     * @return key:????????????????????????/value:????????????
     */
    private Pair<Boolean, File> compress(final File file, boolean handleBreakFlag) {
        if (handleBreakFlag || !downloadProperties.isEnableCompressFile()) {
            return Pair.of(false, null);
        }
        if (file.exists() && file.isFile() && !isZipCompress(file)) {
            if (downloadProperties.getMinEnableCompressMbSize() <= 0
                || FileUtils.sizeOfMB(file) >= downloadProperties.getMinEnableCompressMbSize()) {
                // ??????????????????
                // ???????????????????????????
                try {
                    String path = file.getAbsolutePath();
                    CompressUtils.zip(path, path + FileSuffixEnum.ZIP.getFullFileSuffix());
                    return Pair.of(Boolean.TRUE, new File(path + FileSuffixEnum.ZIP.getFullFileSuffix()));
                } catch (Exception ex) {
                    logger
                        .warn("[AsyncFileHandleAdapter#compress] compress file fail! path:{}", file.getAbsolutePath());
                }
            }
        }
        return Pair.of(false, null);
    }

    /**
     * ???????????????????????????
     *
     * @param file ??????
     * @return ???????????????zip????????????
     */
    private boolean isZipCompress(File file) {
        if (file.getName().contains(Constants.FILE_SUFFIX_SEPARATOR)) {
            return false;
        }
        String[] fileNameSplit = file.getName().split("\\.");
        return fileNameSplit.length > 1 && FileSuffixEnum.ZIP.getCode().equals(fileNameSplit[fileNameSplit.length - 1]);
    }

    /**
     * ????????????&????????????
     *
     * @param executor ?????????
     * @param exportExecutor ???????????????
     * @param baseRequest ??????
     * @param registerId ??????ID
     * @return ??????????????????
     */
    private HandleFileResult handleFileWithRetry(BaseDownloadExecutor executor,
        FileExportExecutor exportExecutor,
        BaseDownloaderRequestContext baseRequest,
        Long registerId) {

        // ??????IO ????????????3???
        Retryer<HandleFileResult> retryer = RetryerBuilder.<HandleFileResult>newBuilder()
            .retryIfExceptionOfType(GenerateFileException.class)
            .withWaitStrategy(WaitStrategies.incrementingWait(5, TimeUnit.SECONDS, 5, TimeUnit.SECONDS))
            .withStopStrategy(StopStrategies.stopAfterAttempt(3))
            .build();
        try {
            return retryer.call(() -> {
                // ????????????
                GenerateFileResult genFileResult = generateFile(executor, exportExecutor, baseRequest, registerId,
                    downloadProperties.getLocalFileTempPath());
                Pair<String, String> fileUrl = null;
                Throwable error = null;
                if (!genFileResult.isHandleBreakFlag()) {
                    try {
                        String cnFileName = genCnFileName(baseRequest.getFileSuffix(), exportExecutor,
                            genFileResult.isCompress());
                        fileUrl = uploadService
                            .upload(genFileResult.getUploadFile(), cnFileName, downloadProperties.getAppId());
                    } catch (GenerateFileException ex) {
                        throw ex;
                    } catch (Throwable t) {
                        genFileResult.setHandleBreakFlag(true);
                        genFileResult.getErrorMsg().add("[????????????]" + t.getMessage());
                        error = t;
                    }
                }
                return new HandleFileResult(genFileResult, fileUrl, error);
            });
        } catch (Exception e) {
            logger.error(
                "[AsyncFileHandlerAdapter#handleFileWithRetry] retry execute handle file,error!registerId:{},executor:{}",
                registerId, exportExecutor.value(), e);
            throw new EasyFileException(DownloadErrorCode.HANDLE_DOWNLOAD_FILE_ERROR);
        }
    }


    /**
     * ????????????errorMsg
     *
     * @param errorMsgJoiner joiner
     * @return errorMsg
     */
    private String lessErrorMsg(StringJoiner errorMsgJoiner) {
        if (Objects.isNull(errorMsgJoiner)) {
            return StringUtils.EMPTY;
        }
        String errorMsg = errorMsgJoiner.toString();
        if (StringUtils.isNotBlank(errorMsg) && errorMsg.length() > Constants.MAX_UPLOAD_ERROR_MSG_LENGTH) {
            errorMsg = errorMsg.substring(0, Constants.MAX_UPLOAD_ERROR_MSG_LENGTH);
        }
        return errorMsg;
    }

    private ExportResult buildExportResult(UploadCallbackRequest request) {
        ExportResult exportResult = new ExportResult();
        exportResult.setRegisterId(request.getRegisterId());
        exportResult.setUploadStatus(request.getUploadStatus());
        exportResult.setFileSystem(request.getSystem());
        exportResult.setFileUrl(request.getFileUrl());
        exportResult.setErrorMsg(request.getErrorMsg());
        return exportResult;
    }

    private ExportResult buildDefaultRejectExportResult(Long registerId) {
        ExportResult exportResult = new ExportResult();
        exportResult.setRegisterId(registerId);
        exportResult.setUploadStatus(UploadStatusEnum.FAIL);
        exportResult.setFileSystem(Constants.NONE_FILE_SYSTEM);
        exportResult.setFileUrl(StringUtils.EMPTY);
        return exportResult;
    }

    private DownloaderRequestContext buildRequestDownloaderRequest(OutputStream out,
        BaseDownloaderRequestContext baseRequest) {
        DownloaderRequestContext downloaderRequest = new DownloaderRequestContext();
        downloaderRequest.setOut(out);
        downloaderRequest.setNotifier(baseRequest.getNotifier());
        downloaderRequest.setFileSuffix(baseRequest.getFileSuffix());
        downloaderRequest.setExportRemark(baseRequest.getExportRemark());
        downloaderRequest.setOtherMap(baseRequest.getOtherMap());
        return downloaderRequest;
    }

    private String genCnFileName(String fileSuffix, FileExportExecutor executor, boolean compress) {
        String fileName = StringUtils.isNotBlank(executor.desc()) ? executor.desc() : executor.value();
        String executeTime = DateFormatUtils.format(new Date(), "yyMMddHHmmssSSS");
        String suffix = fileSuffix.startsWith(Constants.FILE_SUFFIX_SEPARATOR) ? fileSuffix
            : Constants.FILE_SUFFIX_SEPARATOR + fileSuffix;
        // ?????????????????????????????????,??????????????????????????????.zip ???????????????
        return fileName + "_" + executeTime + suffix + (compress ? FileSuffixEnum.ZIP.getFullFileSuffix()
            : StringUtils.EMPTY);
    }

    private String generateEnFileName(String fileSuffix, FileExportExecutor executor, String path) {
        String newPath = path.endsWith(File.separator) ? path : path + File.separator;
        String fileName = executor.value();
        String executeTime = DateFormatUtils.format(new Date(), "yyMMddHHmmssSSS");
        String suffix = fileSuffix.startsWith(Constants.FILE_SUFFIX_SEPARATOR) ? fileSuffix
            : Constants.FILE_SUFFIX_SEPARATOR + fileSuffix;
        return newPath + fileName + "_" + executeTime + suffix;
    }
}
