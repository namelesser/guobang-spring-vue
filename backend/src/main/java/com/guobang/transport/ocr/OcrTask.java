package com.guobang.transport.ocr;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ocr_tasks")
public class OcrTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long recordId;
    private Long imageId;
    private String fileName;
    private String mimeType;
    private Integer priority;
    private String status;
    private Integer retryCount;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    public Long getId() { return id; } // 返回任务自增主键
    public void setId(Long id) { this.id = id; } // 设置任务主键
    public Long getRecordId() { return recordId; } // 获取关联的运输记录ID
    public void setRecordId(Long recordId) { this.recordId = recordId; } // 设置关联记录ID
    public Long getImageId() { return imageId; } // 获取关联的图片ID
    public void setImageId(Long imageId) { this.imageId = imageId; } // 设置关联图片ID
    public String getFileName() { return fileName; } // 获取原始文件名
    public void setFileName(String fileName) { this.fileName = fileName; } // 设置文件名
    public String getMimeType() { return mimeType; } // 获取图片MIME类型
    public void setMimeType(String mimeType) { this.mimeType = mimeType; } // 设置MIME类型
    public Integer getPriority() { return priority; } // 获取任务优先级
    public void setPriority(Integer priority) { this.priority = priority; } // 设置任务优先级
    public String getStatus() { return status; } // 获取任务状态(pending/processing/done/error)
    public void setStatus(String status) { this.status = status; } // 设置任务状态
    public Integer getRetryCount() { return retryCount; } // 获取已重试次数
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; } // 设置重试次数
    public String getErrorMessage() { return errorMessage; } // 获取错误信息
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; } // 设置错误信息
    public LocalDateTime getCreatedAt() { return createdAt; } // 获取任务创建时间
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; } // 设置创建时间
    public LocalDateTime getStartedAt() { return startedAt; } // 获取任务开始处理时间
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; } // 设置开始处理时间
    public LocalDateTime getFinishedAt() { return finishedAt; } // 获取任务完成时间
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; } // 设置完成时间
}
