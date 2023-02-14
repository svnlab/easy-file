package org.svnee.easyfile.example.downloader;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Resource;
import org.springframework.stereotype.Component;
import org.svnee.easyfile.common.annotations.FileExportExecutor;
import org.svnee.easyfile.common.bean.BaseDownloaderRequestContext;
import org.svnee.easyfile.common.bean.Page;
import org.svnee.easyfile.common.bean.PageTotal;
import org.svnee.easyfile.common.bean.PageTotalContext;
import org.svnee.easyfile.common.bean.Pair;
import org.svnee.easyfile.common.util.CollectionUtils;
import org.svnee.easyfile.example.excel.WaterMarkExcelIntensifier;
import org.svnee.easyfile.example.mapper.StudentMapper;
import org.svnee.easyfile.example.model.Student;
import org.svnee.easyfile.starter.executor.excel.ExcelIntensifier;
import org.svnee.easyfile.starter.executor.impl.AbstractPageDownloadExcelExecutor;

/**
 * @author svnee
 **/
@Component
@FileExportExecutor(value = "StudentPageDownloadDemoExecutor", desc = "Student分页下载")
public class StudentPageDownloadDemoExecutor extends AbstractPageDownloadExcelExecutor<Student> {

    @Resource
    private StudentMapper studentMapper;

    @Override
    public boolean enableAsync(BaseDownloaderRequestContext context) {
        return true;
    }

    @Override
    public String sheetPrefix() {
        return "学生信息";
    }

    @Override
    public PageTotal count(Map<String, Object> othersMap) {
        if (PageTotalContext.currentPageToTal(sheetPrefix()) != null) {
            return PageTotalContext.currentPageToTal(sheetPrefix());
        }
        int count = studentMapper.count();
        PageTotalContext.cache(sheetPrefix(), PageTotal.of(count, 100));
        return PageTotalContext.currentPageToTal(sheetPrefix());
    }

    @Override
    public Pair<Long, List<Student>> shardingData(BaseDownloaderRequestContext context, Page page, Long cursorId) {
        List<Student> studentList = studentMapper.findByMinIdLimit(cursorId, page.getPageSize());
        if (CollectionUtils.isEmpty(studentList)) {
            return Pair.of(cursorId, studentList);
        }
        cursorId = studentList.get(studentList.size() - 1).getId();
        return Pair.of(cursorId, studentList);
    }

    /**
     * 水印打印
     */
    @Override
    public List<ExcelIntensifier> enhanceExcel() {
        return Collections.singletonList(new WaterMarkExcelIntensifier());
    }
}
