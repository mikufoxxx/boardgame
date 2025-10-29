package cc.techox.boardgame.dto;

public class CreateRoomRequest {
    private String name;
    private String gameCode;
    private Integer maxPlayers;
    private Boolean isPrivate;
    private String password; // 可选

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getGameCode() { return gameCode; }
    public void setGameCode(String gameCode) { this.gameCode = gameCode; }
    public Integer getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(Integer maxPlayers) { this.maxPlayers = maxPlayers; }
    public Boolean getIsPrivate() { return isPrivate; }
    public void setIsPrivate(Boolean isPrivate) { this.isPrivate = isPrivate; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}