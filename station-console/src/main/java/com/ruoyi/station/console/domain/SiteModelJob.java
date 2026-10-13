package com.ruoyi.station.console.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import java.util.Date;

/**
 * 混元生 3D 任务。对应表 {@code u_s_model_job}。
 *
 * <p>状态流转：{@code SUBMITTING}（本站落库、正在调提交接口）→ {@code WAIT} / {@code RUN}（混元在排队 / 生成）→
 * {@code SAVING}（混元已完成，正在下载 glb 转存）→ {@code DONE}；任一步失败 → {@code FAIL}。</p>
 *
 * <p>成功后 {@link #modelId} 指向 {@code u_s_sitemodel} 里那条——生成的模型和手动上传的完全一样。</p>
 */
public class SiteModelJob implements Serializable
{
    private static final long serialVersionUID = 1L;

    public static final String SUBMITTING = "SUBMITTING";
    public static final String WAIT = "WAIT";
    public static final String RUN = "RUN";
    public static final String SAVING = "SAVING";
    public static final String DONE = "DONE";
    public static final String FAIL = "FAIL";

    private String jobId;
    private String siteCode;
    /** 混元的 JobId。只在后端用，不下发给管理台（没用，也省得有人拿去直接调接口） */
    @JsonIgnore
    private String tcJobId;
    private String status;
    private String jobName;
    private String imagesJson;
    private Integer faceCount;
    private Integer enablePbr;
    private String folderId;
    private String modelId;
    private String previewUrl;
    private String errorCode;
    private String errorMsg;
    private Integer polls;
    private Integer saveRetries;
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createtime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatetime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date finishtime;

    /** 还在跑（管理台据此决定要不要继续刷新） */
    public boolean isActive()
    {
        return SUBMITTING.equals(status) || WAIT.equals(status) || RUN.equals(status) || SAVING.equals(status);
    }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public String getTcJobId() { return tcJobId; }
    public void setTcJobId(String tcJobId) { this.tcJobId = tcJobId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getJobName() { return jobName; }
    public void setJobName(String jobName) { this.jobName = jobName; }
    public String getImagesJson() { return imagesJson; }
    public void setImagesJson(String imagesJson) { this.imagesJson = imagesJson; }
    public Integer getFaceCount() { return faceCount; }
    public void setFaceCount(Integer faceCount) { this.faceCount = faceCount; }
    public Integer getEnablePbr() { return enablePbr; }
    public void setEnablePbr(Integer enablePbr) { this.enablePbr = enablePbr; }
    public String getFolderId() { return folderId; }
    public void setFolderId(String folderId) { this.folderId = folderId; }
    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }
    public String getPreviewUrl() { return previewUrl; }
    public void setPreviewUrl(String previewUrl) { this.previewUrl = previewUrl; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
    public Integer getPolls() { return polls; }
    public void setPolls(Integer polls) { this.polls = polls; }
    public Integer getSaveRetries() { return saveRetries; }
    public void setSaveRetries(Integer saveRetries) { this.saveRetries = saveRetries; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }
    public Date getCreatetime() { return createtime; }
    public void setCreatetime(Date createtime) { this.createtime = createtime; }
    public Date getUpdatetime() { return updatetime; }
    public void setUpdatetime(Date updatetime) { this.updatetime = updatetime; }
    public Date getFinishtime() { return finishtime; }
    public void setFinishtime(Date finishtime) { this.finishtime = finishtime; }
}
