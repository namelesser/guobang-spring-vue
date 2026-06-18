package com.guobang.transport.rate;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("freight_rates")
public class FreightRate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String origin;
    private String destination;
    private String sender;
    private BigDecimal pricePerTon;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String note;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }
    public BigDecimal getPricePerTon() { return pricePerTon; }
    public void setPricePerTon(BigDecimal pricePerTon) { this.pricePerTon = pricePerTon; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
