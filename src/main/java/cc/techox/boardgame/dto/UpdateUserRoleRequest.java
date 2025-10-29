package cc.techox.boardgame.dto;

public class UpdateUserRoleRequest {
    private String role; // user/admin

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}