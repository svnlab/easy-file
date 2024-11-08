package com.openquartz.easyfile.storage.local.mapper.impl;

import com.openquartz.easyfile.storage.local.mapper.AsyncDownloadRecordMapper;
import com.openquartz.easyfile.storage.local.mapper.condition.BaseRecordQueryCondition;
import com.openquartz.easyfile.storage.local.mapper.condition.UploadInfoChangeCondition;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import com.openquartz.easyfile.common.bean.Pair;
import com.openquartz.easyfile.common.dictionary.UploadStatusEnum;
import com.openquartz.easyfile.common.util.CollectionUtils;
import com.openquartz.easyfile.common.util.StringUtils;
import com.openquartz.easyfile.storage.local.entity.AsyncDownloadRecord;
import com.openquartz.easyfile.storage.local.prop.EasyFileTableGeneratorSupplier;

/**
 * @author svnee
 **/
@Slf4j
@RequiredArgsConstructor
public class AsyncDownloadRecordMapperImpl implements AsyncDownloadRecordMapper {

    private final JdbcTemplate jdbcTemplate;

    private static final String INSERT_SQL =
            "insert into {0}(download_task_id, app_id,download_code,upload_status, file_url, file_name, file_system, download_operate_by,download_operate_name, remark, notify_enable_status,notify_email, max_server_retry, current_retry,download_num,last_execute_time,"
                    + "invalid_time,execute_param,error_msg,execute_process, version, create_time, update_time, create_by, update_by,locale) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    private static final String FIND_BY_ID_SQL =
            "select id,download_task_id, app_id,download_code,upload_status, file_url, file_name, file_system, download_operate_by,"
                    + "download_operate_name, remark, notify_enable_status,notify_email, max_server_retry, current_retry,download_num,last_execute_time,"
                    + "invalid_time,execute_param,error_msg,execute_process, version, create_time, update_time, create_by, update_by,locale from {0} where id = ?";

    private static final String REFRESH_UPLOAD_STATUS_SQL = "update {0} set upload_status = ?,update_by = ? where id = ? AND upload_status = ?";

    private static final String UPDATE_DOWNLOAD_SQL = "update {0} set download_num = download_num + 1 where id = ? and upload_status = ?";

    private static final String UPDATE_EXECUTE_PROCESS_SQL = "update {0} set execute_process = ? where id = ? and execute_process <= ? ";

    private static final String UPDATE_EXECUTE_PROCESS_NEXT_STATUS_SQL = "update {0} set execute_process = ?,upload_status=? where id = ? and execute_process <= ? ";

    private static final String LIST_SQL =
            "select id,download_task_id, app_id,download_code,upload_status, file_url, file_name, file_system, download_operate_by,"
                    + "download_operate_name, remark, notify_enable_status,notify_email, max_server_retry, current_retry,download_num,last_execute_time,"
                    + "invalid_time,execute_param,error_msg,execute_process, version, create_time, update_time, create_by, update_by,locale from {0} "
                    + "where download_task_id = ? and upload_status=? order by update_time desc limit ?";

    private static final String SELECT_ALL_SQL =
            "select id,download_task_id, app_id,download_code,upload_status, file_url, file_name, file_system, download_operate_by,"
                    + "download_operate_name, remark, notify_enable_status,notify_email, max_server_retry, current_retry,download_num,last_execute_time,"
                    + "invalid_time,execute_param,error_msg,execute_process, version, create_time, update_time, create_by, update_by,locale from {0} ";

    private static final String SELECT_COUNT_SQL = "select count(*) from {0}";

    private static final String SELECT_LOCALE_SQL = "select locale from {0} where id = ?";

    @Override
    public int insertSelective(AsyncDownloadRecord downloadRecord) {

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {

            String sql = MessageFormat.format(INSERT_SQL, EasyFileTableGeneratorSupplier.genAsyncDownloadRecordTable());
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, downloadRecord.getDownloadTaskId());
            ps.setString(2, downloadRecord.getAppId());
            ps.setString(3, downloadRecord.getDownloadCode());
            ps.setString(4, downloadRecord.getUploadStatus().getCode());
            ps.setString(5, downloadRecord.getFileUrl());
            ps.setString(6, downloadRecord.getFileName());
            ps.setString(7, downloadRecord.getFileSystem());
            ps.setString(8, downloadRecord.getDownloadOperateBy());
            ps.setString(9, downloadRecord.getDownloadOperateName());
            ps.setString(10, downloadRecord.getRemark());
            ps.setInt(11, downloadRecord.getNotifyEnableStatus());
            ps.setString(12, downloadRecord.getNotifyEmail());
            ps.setInt(13, downloadRecord.getMaxServerRetry());
            ps.setInt(14, downloadRecord.getCurrentRetry());
            ps.setInt(15, downloadRecord.getDownloadNum());
            ps.setTimestamp(16, new Timestamp(downloadRecord.getLastExecuteTime().getTime()));
            ps.setTimestamp(17, new Timestamp(downloadRecord.getInvalidTime().getTime()));
            ps.setString(18, downloadRecord.getExecuteParam());
            ps.setString(19, downloadRecord.getErrorMsg());
            ps.setInt(20, downloadRecord.getExecuteProcess());
            ps.setInt(21, downloadRecord.getVersion());
            ps.setTimestamp(22, new Timestamp(downloadRecord.getCreateTime().getTime()));
            ps.setTimestamp(23, new Timestamp(downloadRecord.getUpdateTime().getTime()));
            ps.setString(24, downloadRecord.getCreateBy());
            ps.setString(25, downloadRecord.getUpdateBy());
            ps.setString(26, downloadRecord.getLocale());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (Objects.nonNull(key)) {
            downloadRecord.setId(key.longValue());
            return 1;
        }
        return 0;
    }

    @Override
    public AsyncDownloadRecord findById(Long id) {
        String sql = MessageFormat
                .format(FIND_BY_ID_SQL, EasyFileTableGeneratorSupplier.genAsyncDownloadRecordTable());
        List<AsyncDownloadRecord> downloadRecordList = jdbcTemplate
                .query(sql, new Object[]{id}, new AsyncDownloadRecordRowMapper());
        if (CollectionUtils.isNotEmpty(downloadRecordList)) {
            return downloadRecordList.get(0);
        }
        return null;
    }

    @Override
    public int changeUploadInfo(UploadInfoChangeCondition condition) {
        List<Object> params = new ArrayList<>();
        final StringBuilder sql = new StringBuilder(
                "update " + EasyFileTableGeneratorSupplier.genAsyncDownloadRecordTable()
                        + " set version=version+1,upload_status = ? ");
        params.add(condition.getUploadStatus().getCode());
        if (StringUtils.isNotBlank(condition.getFileUrl())) {
            sql.append(",file_url = ?");
            params.add(condition.getFileUrl());
        }
        if (StringUtils.isNotBlank(condition.getFileName())) {
            sql.append(",file_name = ?");
            params.add(condition.getFileName());
        }
        if (StringUtils.isNotBlank(condition.getFileSystem())) {
            sql.append(",file_system = ?");
            params.add(condition.getFileSystem());
        }
        if (StringUtils.isNotBlank(condition.getErrorMsg())) {
            sql.append(",error_msg = ?");
            params.add(condition.getErrorMsg());
        }
        if (Objects.nonNull(condition.getLastExecuteTime())) {
            sql.append(",last_execute_time = ?");
            params.add(condition.getLastExecuteTime());
        }
        if (Objects.nonNull(condition.getInvalidTime())) {
            sql.append(",invalid_time = ?");
            params.add(condition.getInvalidTime());
        }
        sql.append(" where id = ?");
        params.add(condition.getId());
        return jdbcTemplate.update(sql.toString(), params.toArray());
    }

    @Override
    public int refreshUploadStatus(Long id, UploadStatusEnum oriUploadStatus, UploadStatusEnum tagUploadStatus,
                                   String updateBy) {
        String sql = MessageFormat.format(REFRESH_UPLOAD_STATUS_SQL,
                EasyFileTableGeneratorSupplier.genAsyncDownloadRecordTable());
        return jdbcTemplate
                .update(sql, tagUploadStatus.getCode(), updateBy, id, oriUploadStatus.getCode());
    }

    @Override
    public int download(Long id, UploadStatusEnum uploadStatus) {
        String sql = MessageFormat
                .format(UPDATE_DOWNLOAD_SQL, EasyFileTableGeneratorSupplier.genAsyncDownloadRecordTable());
        return jdbcTemplate.update(sql, id, uploadStatus.getCode());
    }

    @Override
    public List<AsyncDownloadRecord> listByTaskIdAndStatus(Long downloadTaskId, UploadStatusEnum uploadStatus,
                                                           Integer offset) {
        String sql = MessageFormat.format(LIST_SQL, EasyFileTableGeneratorSupplier.genAsyncDownloadRecordTable());
        return jdbcTemplate
                .query(sql, new Object[]{downloadTaskId, uploadStatus.getCode(), offset},
                        new AsyncDownloadRecordRowMapper());
    }

    private static class AsyncDownloadRecordRowMapper implements RowMapper<AsyncDownloadRecord> {

        @Override
        public AsyncDownloadRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            AsyncDownloadRecord downloadRecord = new AsyncDownloadRecord();
            downloadRecord.setId(rs.getLong("id"));
            downloadRecord.setDownloadTaskId(rs.getLong("download_task_id"));
            downloadRecord.setAppId(rs.getString("app_id"));
            downloadRecord.setDownloadCode(rs.getString("download_code"));
            downloadRecord.setUploadStatus(UploadStatusEnum.fromCode(rs.getString("upload_status")));
            downloadRecord.setFileUrl(rs.getString("file_url"));
            downloadRecord.setFileName(rs.getString("file_name"));
            downloadRecord.setFileSystem(rs.getString("file_system"));
            downloadRecord.setDownloadOperateBy(rs.getString("download_operate_by"));
            downloadRecord.setDownloadOperateName(rs.getString("download_operate_name"));
            downloadRecord.setRemark(rs.getString("remark"));
            downloadRecord.setNotifyEnableStatus(rs.getInt("notify_enable_status"));
            downloadRecord.setNotifyEmail(rs.getString("notify_email"));
            downloadRecord.setMaxServerRetry(rs.getInt("max_server_retry"));
            downloadRecord.setCurrentRetry(rs.getInt("current_retry"));
            downloadRecord.setExecuteParam(rs.getString("execute_param"));
            downloadRecord.setErrorMsg(rs.getString("error_msg"));
            downloadRecord.setLastExecuteTime(rs.getTimestamp("last_execute_time"));
            downloadRecord.setInvalidTime(rs.getTimestamp("invalid_time"));
            downloadRecord.setDownloadNum(rs.getInt("download_num"));
            downloadRecord.setExecuteProcess(rs.getInt("execute_process"));
            downloadRecord.setVersion(rs.getInt("version"));
            downloadRecord.setCreateTime(rs.getTimestamp("create_time"));
            downloadRecord.setUpdateTime(rs.getTimestamp("update_time"));
            downloadRecord.setCreateBy(rs.getString("create_by"));
            downloadRecord.setUpdateBy(rs.getString("update_by"));
            downloadRecord.setLocale(rs.getString("locale"));
            return downloadRecord;
        }
    }

    @Override
    public int refreshExecuteProcess(Long registerId, Integer executeProcess, UploadStatusEnum nextStatus) {
        String sql = MessageFormat
                .format(UPDATE_EXECUTE_PROCESS_NEXT_STATUS_SQL,
                        EasyFileTableGeneratorSupplier.genAsyncDownloadRecordTable());
        return jdbcTemplate.update(sql, executeProcess, nextStatus.getCode(), registerId, executeProcess);
    }

    @Override
    public int resetExecuteProcess(Long registerId) {
        String sql = MessageFormat
                .format(UPDATE_EXECUTE_PROCESS_SQL, EasyFileTableGeneratorSupplier.genAsyncDownloadRecordTable());
        return jdbcTemplate.update(sql, 0, registerId, 100);
    }

    @Override
    public List<AsyncDownloadRecord> selectByCondition(BaseRecordQueryCondition condition) {
        Pair<String, Map<String, Object>> pair = queryByCondition(condition, SELECT_ALL_SQL);
        return new NamedParameterJdbcTemplate(jdbcTemplate)
                .query(pair.getKey(), pair.getValue(), new AsyncDownloadRecordRowMapper());
    }

    private Pair<String, Map<String, Object>> queryByCondition(BaseRecordQueryCondition condition, String sqlTemplate) {
        String sql = MessageFormat
                .format(sqlTemplate, EasyFileTableGeneratorSupplier.genAsyncDownloadRecordTable());

        StringJoiner conditionWhere = new StringJoiner(" and ");
        Map<String, Object> paramMap = new HashMap<>(10);
        if (CollectionUtils.isNotEmpty(condition.getLimitedAppIdList())) {
            conditionWhere.add("app_id in (:appId)");
            paramMap.put("appId", condition.getLimitedAppIdList());
        }
        if (StringUtils.isNotBlank(condition.getDownloadCode())) {
            conditionWhere.add("download_code= :downloadCode");
            paramMap.put("downloadCode", condition.getDownloadCode());
        }
        if (StringUtils.isNotBlank(condition.getDownloadOperateBy())) {
            conditionWhere.add("download_operate_by= :downloadOperateBy");
            paramMap.put("downloadOperateBy", condition.getDownloadOperateBy());
        }
        if (Objects.nonNull(condition.getStartCreateTime())) {
            conditionWhere.add("create_time>= :startCreateTime");
            paramMap.put("startCreateTime", condition.getStartCreateTime());
        }
        if (Objects.nonNull(condition.getEndCreateTime())) {
            conditionWhere.add("create_time< :endCreateTime");
            paramMap.put("endCreateTime", condition.getEndCreateTime());
        }
        if (Objects.nonNull(condition.getUploadStatus())) {
            conditionWhere.add("upload_status= :uploadStatus");
            paramMap.put("uploadStatus", condition.getUploadStatus().getCode());
        }
        if (Objects.nonNull(condition.getMaxInvalidTime())) {
            conditionWhere.add("invalid_time <= :maxInvalidTime");
            paramMap.put("maxInvalidTime", condition.getMaxInvalidTime());
        }
        if (conditionWhere.length() > 0) {
            sql = sql + " where " + conditionWhere;
        }
        sql = sql + " order by create_time desc";
        if (Objects.nonNull(condition.getStartOffset())) {
            sql = sql + " limit :startIndex,:offset";
            paramMap.put("startIndex", condition.getStartOffset().getKey());
            paramMap.put("offset", condition.getStartOffset().getValue());
        }
        return Pair.of(sql, paramMap);
    }

    @Override
    public int countByCondition(BaseRecordQueryCondition condition) {
        Pair<String, Map<String, Object>> pair = queryByCondition(condition, SELECT_COUNT_SQL);
        Integer total = new NamedParameterJdbcTemplate(jdbcTemplate)
                .queryForObject(pair.getKey(), pair.getValue(), Integer.class);
        return Objects.nonNull(total) ? total : 0;
    }

    @Override
    public String getLocale(Long registerId) {
        String sql = MessageFormat
                .format(SELECT_LOCALE_SQL, EasyFileTableGeneratorSupplier.genAsyncDownloadRecordTable());
        return jdbcTemplate.queryForObject(sql, String.class, registerId);
    }
}
