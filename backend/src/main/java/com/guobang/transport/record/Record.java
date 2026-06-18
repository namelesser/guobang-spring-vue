package com.guobang.transport.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("records")
public class Record {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String source;
    private String fileName;
    private String imageId;
    private LocalDate recordDate;
    private String orderNo;
    private String sender;
    private String receiver;
    private String company;
    private String plateNo;
    private BigDecimal netWeight;
    private BigDecimal freightRate;
    private BigDecimal detourSurcharge;
    private BigDecimal totalCost;
    private Integer reviewed;
    private LocalDateTime reviewedAt;
    private String reviewNote;
    private String note;
    private String ocrStatus;
    private String ocrText;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getImageId() { return imageId; }
    public void setImageId(String imageId) { this.imageId = imageId; }
    public LocalDate getRecordDate() { return recordDate; }
    public void setRecordDate(LocalDate recordDate) { this.recordDate = recordDate; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }
    public String getReceiver() { return receiver; }
    public void setReceiver(String receiver) { this.receiver = receiver; }
    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
    public String getPlateNo() { return plateNo; }
    public void setPlateNo(String plateNo) { this.plateNo = plateNo; }
    public BigDecimal getNetWeight() { return netWeight; }
    public void setNetWeight(BigDecimal netWeight) { this.netWeight = netWeight; }
    public BigDecimal getFreightRate() { return freightRate; }
    public void setFreightRate(BigDecimal freightRate) { this.freightRate = freightRate; }
    public BigDecimal getDetourSurcharge() { return detourSurcharge; }
    public void setDetourSurcharge(BigDecimal detourSurcharge) { this.detourSurcharge = detourSurcharge; }
    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }
    public Integer getReviewed() { return reviewed; }
    public void setReviewed(Integer reviewed) { this.reviewed = reviewed; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getOcrStatus() { return ocrStatus; }
    public void setOcrStatus(String ocrStatus) { this.ocrStatus = ocrStatus; }
    public String getOcrText() { return ocrText; }
    public void setOcrText(String ocrText) { this.ocrText = ocrText; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
