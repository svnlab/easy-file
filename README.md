# EasyFile

### 介绍

EasyFile-是为了提供更加便捷的文件服务,可以提供统一的操作入口

目前主要支持导出功能

支持同步导出、异步导出、文件压缩、流式导出、分页导出、导出缓存复用等特性。

优化缓解导出文件时对服务的内存和CPU影响。针对文件服务可做更多的管理。

提供给开发者更加通用、快捷、统一的实现的API方案；

### 解决问题

1、瞬时加载数据过大导致内存飙高不够平滑机器宕机风险很大

2、生成较大文件容易出现HTTP 超时，造成导出失败

3、相同条件的导出结果无法做到复用，需要继续生成导出文件资源浪费

4、导出任务集中出现没有可监控机制

5、开发者不仅需要关心数据查询逻辑同时需要关心文件生成逻辑

### 框架对比

与 Alibaba 的EasyExcel 相比,两者侧重点不同。

Alibaba EasyExcel 是一个Excel文件生成导出、导入 解析工具。

EasyFile 是一个大文件导出的解决方案。用于解决大文件导出时遇到的，文件复用，文件导出超时，内存溢出，瞬时CPU 内存飙高等等问题的一整套解决方案。 同时EasyFile 不仅可以用于Excel
文件的导出,也可以用于csv,pdf,word 等文件导出的管理（暂时需要用户自己集成基础导出下载类BaseDownloadExecutor 实现文件生成逻辑）。

而且,EasyFile和Alibaba EasyExcel 并不冲突，依然可以结合EasyExcel 使用,文件生成逻辑使用Alibaba EasyExcel 做自行拓展使用。

### 软件架构

EasyFile 提供两种模式

local 模式:  需要提供本地的api 存储Mapper. 将数据存储到本地数据库中管理。

remote模式：需要部署easyfile-server 服务，并设置客户端调用远程EasyFile 的域名。

### 代码结构

- easyfile-common: 公共模块服务

- easyfile-storage: 存储服务
- easyfile-storage-api: 存储服务API
- easyfile-storage-remote: 远程调用存储
- easyfile-storage-local: 本地数据源存储

- easyfile-spring-boot-starter: easyfile starter 包
- easyfile-server: easyfile 远程存储服务端

- easyfile-example: 样例工程
- easyfile-example-local: 本地储存样样例工程
- easyfile-example-remote: 远程存储样例工程

### 使用教程

#### 一、引入maven依赖

如果使用本地模式 引入maven

```xml
<dependency>
    <groupId>org.svnee</groupId>
    <artifactId>easyfile-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
<dependency>
   <groupId>org.svnee</groupId>
   <artifactId>easyfile-storage-local</artifactId>
   <version>1.0.0</version>
</dependency>
```

如果使用remote模式引入maven 依赖

```xml
<dependency>
   <groupId>org.svnee</groupId>
    <artifactId>easyfile-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
<dependency>
   <groupId>org.svnee</groupId>
   <artifactId>easyfile-storage-remote</artifactId>
   <version>1.0.0</version>
</dependency>
```

#### 二、Client端需要提供文件上传服务进行实现接口

```java
package org.svnee.easyfile.storage.file;

import java.io.File;
import org.svnee.easyfile.common.bean.Pair;

/**
 * 文件上传服务
 *
 * @author svnee
 */
public interface UploadService {

    /**
     * 文件上传
     * 如果需要重试则需要抛出 org.svnee.easyfile.starter.exception.GenerateFileException
     *
     * @param file 文件
     * @param fileName 自定义生成的文件名
     * @param appId 服务ID
     * @return key: 文件系统 --> value:返回文件URL/KEY标识符
     */
    Pair<String, String> upload(File file, String fileName, String appId);

}
```

将文件上传到自己的文件存储服务

#### 三、额外处理

如果是使用Local模式，需要提供Client配置

```properties
##### easyfile-local-datasource
easyfile.local.datasource.type=com.zaxxer.hikari.HikariDataSource
easyfile.local.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
easyfile.local.datasource.url=jdbc:mysql://localhost:3306/test?characterEncoding=utf-8&zeroDateTimeBehavior=convertToNull&transformedBitIsBoolean=true&serverTimezone=GMT%2B8
easyfile.local.datasource.username=root
easyfile.local.datasource.password=123456
```

需要执行SQL:

```sql
CREATE TABLE ef_async_download_task
(
    id                BIGINT (20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'id',
    task_code         VARCHAR(50) NOT NULL DEFAULT '' COMMENT '任务编码',
    task_desc         VARCHAR(50) NOT NULL DEFAULT '' COMMENT '任务描述',
    app_id            VARCHAR(50) NOT NULL DEFAULT '' COMMENT '归属系统 APP ID',
    unified_app_id    VARCHAR(50) NOT NULL DEFAULT '' COMMENT '统一APP ID',
    enable_status     TINYINT (3) NOT NULL DEFAULT 0 COMMENT '启用状态',
    limiting_strategy VARCHAR(50) NOT NULL DEFAULT '' COMMENT '限流策略',
    version           INT (10) NOT NULL DEFAULT 0 COMMENT '版本号',
    create_time       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by         VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人',
    update_by         VARCHAR(50) NOT NULL DEFAULT '' COMMENT '更新人',
    is_deleted        BIGINT (20) NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (id),
    UNIQUE KEY `uniq_app_id_task_code` (`task_code`,`app_id`) USING BTREE
)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '异步下载任务';

CREATE TABLE ef_async_download_record
(
    id                    BIGINT (20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'id',
    download_task_id      BIGINT (20) NOT NULL DEFAULT 0 COMMENT '下载任务ID',
    app_id                VARCHAR(50)  NOT NULL DEFAULT '' COMMENT 'app ID',
    download_code         VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '下载code',
    upload_status         VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '上传状态',
    file_url              VARCHAR(512) NOT NULL DEFAULT '' COMMENT '文件路径',
    file_system           VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '文件所在系统',
    download_operate_by   VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '下载操作人',
    download_operate_name VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '下载操作人',
    remark                VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '备注',
    notify_enable_status  TINYINT (3) NOT NULL DEFAULT 0 COMMENT '通知启用状态',
    notify_email          VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '通知有效',
    max_server_retry      INT (3) NOT NULL DEFAULT 0 COMMENT '最大服务重试',
    current_retry         INT (3) NOT NULL DEFAULT 0 COMMENT '当前重试次数',
    execute_param         TEXT NULL COMMENT '重试执行参数',
    error_msg             VARCHAR(256) NOT NULL DEFAULT '' COMMENT '异常信息',
    last_execute_time     DATETIME NULL COMMENT '最新执行时间',
    invalid_time          DATETIME NULL COMMENT '链接失效时间',
    download_num          INT (3) NOT NULL DEFAULT 0 COMMENT '下载次数',
    version               INT (10) NOT NULL DEFAULT 0 COMMENT '版本号',
    create_time           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by             VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '创建人',
    update_by             VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '更新人',
    PRIMARY KEY (id),
    KEY `idx_download_operate_by` (`download_operate_by`) USING BTREE,
    KEY `idx_operator_record` (`download_operate_by`,`app_id`,`create_time`),
    KEY `idx_upload_invalid` (`upload_status`,`invalid_time`,`id`)
)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '异步下载记录';
```

如果是使用remote服务，需要部署easyfile-server 服务,Client提供配置

```properties
#### easyfile-storage-remote
easyfile.remote.username=example
easyfile.remote.password=example
easyfile.remote.server-addr=127.0.0.1:8080
easyfile.remote.namespace=remote-example
```

#### 四、异步文件处理器

[异步文件处理器配置](doc/AsyncFileHandler.md)

#### 五、实现下载器

实现接口：`org.svnee.easyfile.starter.executor.BaseDownloadExecutor`

并注入到Spring ApplicationContext中，并使用注解 `org.svnee.easyfile.common.annotations.FileExportExecutor`

如果需要支持同步导出，需要设置文件的HttpResponse 请求头，需要实现接口 `org.svnee.easyfile.starter.executor.BaseWrapperSyncResponseHeader`

例如：

```java
import org.springframework.stereotype.Component;
import org.svnee.easyfile.common.annotations.FileExportExecutor;
import org.svnee.easyfile.common.bean.DownloaderRequestContext;
import org.svnee.easyfile.starter.executor.BaseDownloadExecutor;
import org.svnee.easyfile.starter.executor.BaseWrapperSyncResponseHeader;

@Component
@FileExportExecutor("ExampleExcelExecutor")
public class ExampleExcelExecutor implements BaseDownloadExecutor,BaseWrapperSyncResponseHeader {

    @Override
    public boolean enableAsync(DownloaderRequestContext context) {
        // 判断是否开启异步
        return true;
    }

    @Override
    public void export(DownloaderRequestContext context) {
        // 生成文件下载逻辑
    }
}
```

#### 拓展

类继承关系图
![AbstractStreamDownloadExcelExecutor](doc/image/AbstractStreamDownloadExcelExecutor.png)

##### 下载器

1、分页下载支持

`org.svnee.easyfile.starter.executor.PageShardingDownloadExecutor`

提供更加方便的分页支持

`org.svnee.easyfile.starter.executor.impl.AbstractPageDownloadExcelExecutor`

需要配合使用（`org.svnee.easyfile.common.annotations.ExcelProperty`）

多Sheet组下载支持
`org.svnee.easyfile.starter.executor.impl.AbstractMultiSheetPageDownloadExcelExecutor`

2、流式下载支持

`org.svnee.easyfile.starter.executor.StreamDownloadExecutor`

提供更加方便的流式支持

`org.svnee.easyfile.starter.executor.impl.AbstractStreamDownloadExcelExecutor`

多Sheet组下载支持
`org.svnee.easyfile.starter.executor.impl.AbstractMultiSheetStreamDownloadExcelExecutor`

需要配合使用(`org.svnee.easyfile.common.annotations.ExcelProperty`)

##### 限流执行器

如需限流需要实现ExportLimitingExecutor

```java
package org.svnee.easyfile.storage.expand;

import org.svnee.easyfile.common.request.ExportLimitingRequest;

/**
 * 限流服务
 *
 * @author svnee
 */
public interface ExportLimitingExecutor {

    /**
     * 策略
     *
     * @return 策略code码
     */
    String strategy();

    /**
     * 限流
     *
     * @param request request
     */
    void limit(ExportLimitingRequest request);
}
```

##### 缓存开启

导出结果缓存,主要是为了能够复用大文件的导出,减少不必要的相同数据的进行多次重复导出。以此尽可能的复用已经成功导出的结果。

导出的数据主要分成三种：\
1、静态数据(已经是过去数据,不在发生变化,相同条件多次导出结果一致) \
2、动态数据(正在发生的数据,数据一直在改变,相同条件多次导出结果不一致) \
3、静态数据+动态数据(部分数据已经不在改变、部分数据依旧在改变)

导出结果缓存 主要适用在第一种情况的场景

1、需要实现时,重写开启缓存方法

```java
/**
 * 开启导出缓存
 *
 * @param context context
 * @return 是否开启缓存
 */
default boolean enableExportCache(BaseDownloaderRequestContext context){
    return false;
    }
```

2、提供需要判定缓存的key的结果-用于比较是否一致.如果cache-key 为空时,则缓存为匹配所有

```java
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
    String desc() default "";

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
     * 如果有值则可以使用点使用指向最终的数据字段。例如：a.b.c
     * 支持SpringEL表达式
     */
    String[] cacheKey() default {};
}

// 例如
@FileExportExecutor(value = "studentStreamDownloadDemo", desc = "Student导出", cacheKey = {"#request.age"})
```

##### 子列单元格导出支持

目前针对 1:* 的映射导出 只支持及到两级,暂时不支持三级及以上(即：1：* ：* ) \
excel的导出支持1:* 的数据单元行列的导出。例如：\
![MergeCellSheet](doc/image/img.png)
但是针对\
1、1：* 的导出数据时,不建议导出过多数据,由于需要merge 单元格的原因,导致导出生成excel时很慢,建议数量小于 2K行

2、针对特别大的数据时,建议使用1:1的单元格导出 \
![MergeCellSheet](doc/image/one2one.png)

##### 多Sheet分组导出支持

需要按照多个Sheet进行分组查询导出 导出数据形如,
![MultiSheetExport](doc/image/MultiSheetExport.png)

EasyFile 提供两个执行器

- 流式-多Sheet组导出
  `org.svnee.easyfile.starter.executor.impl.AbstractMultiSheetStreamDownloadExcelExecutor`

- 分页-多Sheet组导出
  `org.svnee.easyfile.starter.executor.impl.AbstractMultiSheetPageDownloadExcelExecutor`

##### 优化建议

针对大文件导出功能目前easyfile 提供两种处理方式 分页导出/流式导出。\
1、但是针对大文件导出时建议将单sheet的最大行数设置的比较大,甚至设置成07版本excel的单sheet最大行数,避免频繁创建单Sheet导致内存无法回收OOM
配置为: `easyfile.download.excel-max-sheet-rows` \
2、针对大文件导出时可适量的根据内存本身的大小做设定excel缓存在内存中的行数。不建议设置特别大 配置为: `easyfile.download.excel-row-access-window-size` \
设置过大,会对内存有一定的压力。过小则会频繁的刷新数据到磁盘中,CPU容器上升。\
3、针对分页/流式导出 使用时设置一次查询行数,需要合理设置 \
分页导出时,需要注意分页的分页大小的设置 \
流式导出时,需要注意增强数据缓存的长度即方法`org.svnee.easyfile.starter.executor.impl.AbstractStreamDownloadExcelExecutor.enhanceLength`

##### 内存性能验证

使用本地存储模式 启动参数`-Xms512M -Xmx512M -Xmn512M -Xss1M -XX:MetaspaceSize=256M -XX:MaxMetaspaceSize=256M` \
导出数据行数100w,生成文件大小30079kb excel(2007版本) \
设置分页/流式buf 一次长度设置100 \
使用配置：

```properties
easyfile.download.excel-max-sheet-rows=10000000
easyfile.download.excel-row-access-window-size=100
```

使用分页导出CPU/内存情况 \
![分页导出内存消耗情况](doc/image/PageExport.png) \
使用流式导出CPU/内存情况 \
![流式导出内存消耗情况](doc/image/StreamExport.png)

#### easyfile-server 部署

1、执行存储DB SQL \
2、部署服务




