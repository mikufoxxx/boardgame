package cc.techox.boardgame.service;

import cc.techox.boardgame.game.uno.UnoEngine;
import cc.techox.boardgame.game.uno.UnoState;
import cc.techox.boardgame.model.Game;
import cc.techox.boardgame.model.Match;
import cc.techox.boardgame.model.Room;
import cc.techox.boardgame.model.RoomPlayer;
import cc.techox.boardgame.model.User;
import cc.techox.boardgame.repo.GameRepository;
import cc.techox.boardgame.repo.MatchRepository;
import cc.techox.boardgame.repo.RoomPlayerRepository;
import cc.techox.boardgame.repo.RoomRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class UnoService {
    private final RoomRepository roomRepo;
    private final RoomPlayerRepository roomPlayerRepo;
    private final GameRepository gameRepo;
    private final MatchRepository matchRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    public UnoService(RoomRepository roomRepo, RoomPlayerRepository roomPlayerRepo, GameRepository gameRepo, MatchRepository matchRepo) {
        this.roomRepo = roomRepo;
        this.roomPlayerRepo = roomPlayerRepo;
        this.gameRepo = gameRepo;
        this.matchRepo = matchRepo;
    }

    @Transactional
    public Match startInRoom(long roomId, User starter) {
        Room room = roomRepo.findById(roomId).orElseThrow(() -> new IllegalArgumentException("房间不存在"));
        if (room.getOwner().getId() != starter.getId()) throw new IllegalArgumentException("仅房主可开局");
        Game game = room.getGame();
        if (!"UNO".equalsIgnoreCase(game.getCode())) throw new IllegalArgumentException("房间游戏不是 UNO");
        List<RoomPlayer> players = roomPlayerRepo.findByRoom(room);
        if (players.size() < Math.max(2, game.getMinPlayers())) throw new IllegalArgumentException("玩家不足");
        List<Long> ids = players.stream().map(rp -> rp.getUser().getId()).toList();
        UnoState s = UnoEngine.newGame(ids);
        Match m = new Match();
        m.setRoom(room);
        m.setGame(game);
        m.setStatus(Match.Status.playing);
        m.setStartedAt(LocalDateTime.now());
        m.setStateJson(writeJson(s));
        return matchRepo.save(m);
    }

    @Transactional
    public Map<String,Object> view(long matchId, long viewerId) {
        Match m = matchRepo.findById(matchId).orElseThrow(() -> new IllegalArgumentException("对局不存在"));
        UnoState s = readState(m.getStateJson());
        return UnoEngine.publicView(s, viewerId);
    }

    @Transactional
    public Map<String,Object> play(long matchId, User user, String card, String chooseColor) {
        Match m = matchRepo.findById(matchId).orElseThrow(() -> new IllegalArgumentException("对局不存在"));
        UnoState s = readState(m.getStateJson());
        UnoEngine.play(s, user.getId(), card, chooseColor);
        if (s.finished) {
            m.setStatus(Match.Status.finished);
            m.setWinner(null); // 简化：不回写 winner 用户对象
            m.setEndedAt(LocalDateTime.now());
        }
        m.setStateJson(writeJson(s));
        matchRepo.save(m);
        return UnoEngine.publicView(s, user.getId());
    }

    @Transactional
    public Map<String,Object> drawAndPass(long matchId, User user) {
        Match m = matchRepo.findById(matchId).orElseThrow(() -> new IllegalArgumentException("对局不存在"));
        UnoState s = readState(m.getStateJson());
        UnoEngine.drawAndPass(s, user.getId());
        m.setStateJson(writeJson(s));
        matchRepo.save(m);
        return UnoEngine.publicView(s, user.getId());
    }

    private String writeJson(UnoState s) {
        try { return mapper.writeValueAsString(s); } catch (JsonProcessingException e) { throw new RuntimeException(e); }
    }
    private UnoState readState(String json) {
        try { return mapper.readValue(json, UnoState.class); } catch (Exception e) { throw new RuntimeException(e); }
    }
}