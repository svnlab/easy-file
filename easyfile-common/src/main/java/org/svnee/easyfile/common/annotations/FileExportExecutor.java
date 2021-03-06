package org.svnee.easyfile.common.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.svnee.easyfile.common.bean.BaseDownloaderRequestContext;
import org.svnee.easyfile.common.util.StringUtils;

/**
 * 文件导出执行器
 *
 * @author svnee
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface FileExportExecutor {

    /**
     * 执行器code
     */
    String value();

    /**
     * 执行器中文解释
     * 默认是{@link #value()}
     */
    String desc() default StringUtils.EMPTY;

    /**
     * 是否开启通知
     */
    boolean enableNotify() default false;

    /**
     * 最大服务端重试次数
     * 小于等于0时不在执行重试。
     */
    int maxServerRetry() default 0;

    /**
     * cache-key
     *
     * @see BaseDownloaderRequestContext#getOtherMap() 中的key的对应的value值
     * 如果有值则可以使用点使用指向最终的数据字段。例如：#a.b.c
     * 支持SpringEL表达式
     */
    String[] cacheKey() default {};
}
