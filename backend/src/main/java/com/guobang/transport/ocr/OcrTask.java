package com.guobang.transport.ocr;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
@TableName("ocr_tasks")
public class OcrTask {
    // 设置任务主键
    // 返回任务自增主键
    @TableId(type = IdType.AUTO)
    private Long id;
    // 设置关联记录ID
    // 获取关联的运输记录ID
    private Long recordId;
    // 设置关联图片ID
    // 获取关联的图片ID
    private Long imageId;
    // 设置文件名
    // 获取原始文件名
    private String fileName;
    // 设置MIME类型
    // 获取图片MIME类型
    private String mimeType;
    // 设置任务优先级
    // 获取任务优先级
    private Integer priority;
    // 设置任务状态
    // 获取任务状态(pending/processing/done/error)
    private String status;
    // 设置重试次数
    // 获取已重试次数
    private Integer retryCount;
    // 设置错误信息
    // 获取错误信息
    private String errorMessage;
    // 设置创建时间
    // 获取任务创建时间
    private LocalDateTime createdAt;
    // 设置开始处理时间
    // 获取任务开始处理时间
    private LocalDateTime startedAt;
    // 设置完成时间
    // 获取任务完成时间
    private LocalDateTime finishedAt;

}
