package org.svnee.easyfile.storage.mapper;

import java.util.List;
import org.svnee.easyfile.common.dictionary.UploadStatusEnum;
import org.svnee.easyfile.storage.entity.AsyncDownloadRecord;
import org.svnee.easyfile.storage.mapper.condition.UploadInfoChangeCondition;

/**
 * 异步下载记录Mapper
 *
 * @author svnee
 */
public interface AsyncDownloadRecordMapper {

    /**
     * 插入数据
     *
     * @param asyncDownloadRecord 插入记录
     * @return 插入条数
     */
    int insertSelective(AsyncDownloadRecord asyncDownloadRecord);

    /**
     * 根据ID 查询
     *
     * @param id id
     * @return record
     */
    AsyncDownloadRecord findById(Long id);

    /**
     * 变更上传信息
     *
     * @param condition 变更信息条件
     * @return 变更行数
     */
    int changeUploadInfo(UploadInfoChangeCondition condition);

    /**
     * 刷新上传状态
     *
     * @param id ID
     * @param oriUploadStatus oriUploadStatus
     * @param tagUploadStatus tagUploadStatus
     * @param updateBy 更新人
     * @return affect num
     */
    int refreshUploadStatus(Long id, UploadStatusEnum oriUploadStatus, UploadStatusEnum tagUploadStatus,
        String updateBy);

    /**
     * 下载
     *
     * @param id ID
     * @param uploadStatus 上传状态
     * @return affect row
     */
    int download(Long id, UploadStatusEnum uploadStatus);

    /**
     * 根据下载任务查询异步下载记录
     *
     * @param downloadTaskId 下载任务ID
     * @param uploadStatus 上传状态
     * @param offset 偏移量
     * @return 异步导出下载记录
     */
    List<AsyncDownloadRecord> listByTaskIdAndStatus(Long downloadTaskId, UploadStatusEnum uploadStatus, Integer offset);
}
