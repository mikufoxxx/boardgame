package cc.techox.boardgame.dto;

public class UpdateProfileRequest {
    private String displayName;
    private String currentPassword;
    private String newPassword;

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}