package cc.techox.boardgame.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 游戏数据管理器
 * 从文件加载静态游戏数据并缓存到内存中，避免数据库查询
 */
@Component
public class GameDataManager {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    
    // 内存缓存
    private final Map<String, Map<String, Object>> gameConfigs = new ConcurrentHashMap<>();
    private final Map<String, List<Map<String, Object>>> gameCards = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> gameTexts = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void loadGameData() {
        try {
            loadUnoData();
            // 可以继续加载其他游戏数据
            // loadChessData();
            // loadGoData();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load game data", e);
        }
    }
    
    /**
     * 加载UNO游戏数据
     */
    private void loadUnoData() throws IOException {
        // 加载UNO卡牌数据
        loadCardsFromFile("uno", "gamedata/uno/cards.json");
        
        // 加载UNO规则配置（优先使用YAML格式）
        loadConfigFromFile("uno", "gamedata/uno/config.yml");
        
        // 加载UNO文本
        loadTextsFromFile("uno", "i18n/uno/zh_CN.json");
    }
    
    /**
     * 从文件加载卡牌数据
     */
    private void loadCardsFromFile(String gameCode, String filePath) throws IOException {
        try (InputStream inputStream = new ClassPathResource(filePath).getInputStream()) {
            JsonNode root = objectMapper.readTree(inputStream);
            List<Map<String, Object>> cards = new ArrayList<>();
            
            if (root.has("deck") && root.get("deck").isArray()) {
                for (JsonNode cardNode : root.get("deck")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> card = objectMapper.convertValue(cardNode, Map.class);
                    cards.add(card);
                }
            }
            
            gameCards.put(gameCode, cards);
            System.out.println("Loaded " + cards.size() + " cards for " + gameCode);
        }
    }
    
    /**
     * 从文件加载游戏配置
     */
    private void loadConfigFromFile(String gameCode, String filePath) throws IOException {
        try (InputStream inputStream = new ClassPathResource(filePath).getInputStream()) {
            JsonNode root;
            
            // 根据文件扩展名选择合适的解析器
            if (filePath.endsWith(".yml") || filePath.endsWith(".yaml")) {
                root = yamlMapper.readTree(inputStream);
            } else {
                root = objectMapper.readTree(inputStream);
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> config = objectMapper.convertValue(root, Map.class);
            gameConfigs.put(gameCode, config);
            System.out.println("Loaded config for " + gameCode + ": " + config.keySet());
        }
    }
    
    /**
     * 从文件加载游戏文本
     */
    private void loadTextsFromFile(String gameCode, String filePath) throws IOException {
        try (InputStream inputStream = new ClassPathResource(filePath).getInputStream()) {
            JsonNode root = objectMapper.readTree(inputStream);
            Map<String, String> texts = new HashMap<>();
            
            // 递归提取所有文本
            extractTexts(root, "", texts);
            
            gameTexts.put(gameCode, texts);
            System.out.println("Loaded " + texts.size() + " texts for " + gameCode);
        }
    }
    
    /**
     * 递归提取JSON中的文本
     */
    private void extractTexts(JsonNode node, String prefix, Map<String, String> texts) {
        if (node.isObject()) {
            Iterator<String> fieldNames = node.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                JsonNode fieldValue = node.get(fieldName);
                String key = prefix.isEmpty() ? fieldName : prefix + "." + fieldName;
                extractTexts(fieldValue, key, texts);
            }
        } else if (node.isTextual()) {
            texts.put(prefix, node.asText());
        }
    }
    
    // ==================== 公共API ====================
    
    /**
     * 获取游戏卡牌数据
     */
    public List<Map<String, Object>> getGameCards(String gameCode) {
        return gameCards.getOrDefault(gameCode.toLowerCase(), Collections.emptyList());
    }
    
    /**
     * 获取游戏配置
     */
    public Map<String, Object> getGameConfig(String gameCode) {
        return gameConfigs.getOrDefault(gameCode.toLowerCase(), Collections.emptyMap());
    }
    
    /**
     * 获取配置值
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfigValue(String gameCode, String key, T defaultValue) {
        Map<String, Object> config = getGameConfig(gameCode);
        return (T) config.getOrDefault(key, defaultValue);
    }
    
    /**
     * 获取游戏文本
     */
    public String getGameText(String gameCode, String key, String defaultText) {
        Map<String, String> texts = gameTexts.getOrDefault(gameCode.toLowerCase(), Collections.emptyMap());
        return texts.getOrDefault(key, defaultText);
    }
    
    /**
     * 创建新的卡牌副本（用于游戏）
     */
    public List<Map<String, Object>> createCardDeck(String gameCode) {
        List<Map<String, Object>> originalCards = getGameCards(gameCode);
        List<Map<String, Object>> deck = new ArrayList<>();
        
        // 深拷贝卡牌数据
        for (Map<String, Object> card : originalCards) {
            deck.add(new HashMap<>(card));
        }
        
        // 洗牌
        Collections.shuffle(deck);
        return deck;
    }
    
    /**
     * 获取支持的游戏列表
     */
    public Set<String> getSupportedGames() {
        return gameCards.keySet();
    }
    
    /**
     * 重新加载游戏数据（用于热更新）
     */
    public void reloadGameData() {
        gameCards.clear();
        gameConfigs.clear();
        gameTexts.clear();
        loadGameData();
    }
    
    /**
     * 获取内存使用统计
     */
    public Map<String, Object> getMemoryStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // 统计各游戏的数据量
        Map<String, Object> cardStats = new HashMap<>();
        gameCards.forEach((game, cards) -> cardStats.put(game, cards.size()));
        stats.put("cardsByGame", cardStats);
        
        Map<String, Object> configStats = new HashMap<>();
        gameConfigs.forEach((game, config) -> configStats.put(game, config.size()));
        stats.put("configsByGame", configStats);
        
        Map<String, Object> textStats = new HashMap<>();
        gameTexts.forEach((game, texts) -> textStats.put(game, texts.size()));
        stats.put("textsByGame", textStats);
        
        stats.put("totalGames", gameCards.size());
        stats.put("totalCards", gameCards.values().stream().mapToInt(List::size).sum());
        stats.put("totalTexts", gameTexts.values().stream().mapToInt(Map::size).sum());
        
        return stats;
    }
}