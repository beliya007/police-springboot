package com.fragment.policeforce.exam.api.dto;

/**
 * 体检预约分页查询。
 */
public class ExamPageQueryDTO {

    private Long pageNum;

    private Long pageSize;

    /**
     * 体检批次 ID
     */
    private Long batchId;

    /**
     * 体检时段 ID
     */
    private Long slotId;

    /**
     * 警员 ID
     */
    private Long officerId;

    /**
     * 状态：BOOKED / CANCELLED / CHECKED_IN / COMPLETED
     */
    private String status;

    public Long getPageNum() {
        return pageNum;
    }

    public void setPageNum(Long pageNum) {
        this.pageNum = pageNum;
    }

    public Long getPageSize() {
        return pageSize;
    }

    public void setPageSize(Long pageSize) {
        this.pageSize = pageSize;
    }

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public Long getSlotId() {
        return slotId;
    }

    public void setSlotId(Long slotId) {
        this.slotId = slotId;
    }

    public Long getOfficerId() {
        return officerId;
    }

    public void setOfficerId(Long officerId) {
        this.officerId = officerId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
