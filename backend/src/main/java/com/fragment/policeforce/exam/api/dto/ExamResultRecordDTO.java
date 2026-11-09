package com.fragment.policeforce.exam.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 录入体检结论请求。
 */
public class ExamResultRecordDTO {

    /**
     * 结论：PASS / FAIL / INJURED
     */
    @NotBlank(message = "体检结论不能为空")
    private String conclusion;

    /**
     * 结论备注
     */
    @Size(max = 512, message = "结论备注不能超过 512 个字符")
    private String conclusionNote;

    public String getConclusion() {
        return conclusion;
    }

    public void setConclusion(String conclusion) {
        this.conclusion = conclusion;
    }

    public String getConclusionNote() {
        return conclusionNote;
    }

    public void setConclusionNote(String conclusionNote) {
        this.conclusionNote = conclusionNote;
    }
}
