package cc.techox.boardgame.dto;

public class CreateInviteCodesRequest {
    private int count; // 生成数量
    private String batchNo; // 批次号，可选
    private Integer expiresDays; // 过期天数，可选

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public Integer getExpiresDays() { return expiresDays; }
    public void setExpiresDays(Integer expiresDays) { this.expiresDays = expiresDays; }
}