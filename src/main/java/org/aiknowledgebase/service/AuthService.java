package org.aiknowledgebase.service;

import org.aiknowledgebase.dto.CurrentUserResponse;
import org.aiknowledgebase.dto.LoginRequest;
import org.aiknowledgebase.dto.LoginResponse;
import org.aiknowledgebase.dto.RegisterRequest;
import org.aiknowledgebase.entity.UserAccount;
import org.aiknowledgebase.entity.UserToken;
import org.aiknowledgebase.repository.UserAccountRepository;
import org.aiknowledgebase.repository.UserTokenRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService {

    private static final int TOKEN_EXPIRE_DAYS = 7;

    private final UserAccountRepository userAccountRepository;
    private final UserTokenRepository userTokenRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserAccountRepository userAccountRepository,
                       UserTokenRepository userTokenRepository) {
        this.userAccountRepository = userAccountRepository;
        this.userTokenRepository = userTokenRepository;
    }

    @Transactional
    public CurrentUserResponse register(RegisterRequest request) {
        String username = normalize(request.getUsername());
        String password = request.getPassword();
        String nickname = normalize(request.getNickname());

        if (username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }

        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("密码长度不能少于 6 位");
        }

        if (userAccountRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在");
        }

        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setNickname(nickname.isBlank() ? username : nickname);

        UserAccount savedUser = userAccountRepository.save(user);

        return new CurrentUserResponse(
                savedUser.getId(),
                savedUser.getUsername(),
                savedUser.getNickname()
        );
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String username = normalize(request.getUsername());
        String password = request.getPassword();

        UserAccount user = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        userTokenRepository.deleteByUserId(user.getId());

        String token = UUID.randomUUID().toString().replace("-", "");

        UserToken userToken = new UserToken();
        userToken.setUserId(user.getId());
        userToken.setToken(token);
        userToken.setExpireTime(LocalDateTime.now().plusDays(TOKEN_EXPIRE_DAYS));

        userTokenRepository.save(userToken);

        return new LoginResponse(
                token,
                user.getId(),
                user.getUsername(),
                user.getNickname()
        );
    }

    public CurrentUserResponse getCurrentUser(String authorizationHeader) {
        UserAccount user = resolveUser(authorizationHeader);

        return new CurrentUserResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname()
        );
    }

    @Transactional
    public void logout(String authorizationHeader) {
        String token = extractToken(authorizationHeader);

        if (!token.isBlank()) {
            userTokenRepository.deleteByToken(token);
        }
    }

    public UserAccount resolveUser(String authorizationHeader) {
        String token = extractToken(authorizationHeader);

        if (token.isBlank()) {
            throw new IllegalArgumentException("未登录或缺少 Authorization 请求头");
        }

        UserToken userToken = userTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("登录状态无效，请重新登录"));

        if (userToken.getExpireTime().isBefore(LocalDateTime.now())) {
            userTokenRepository.deleteByToken(token);
            throw new IllegalArgumentException("登录已过期，请重新登录");
        }

        return userAccountRepository.findById(userToken.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    }

    private String extractToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return "";
        }

        String prefix = "Bearer ";
        if (authorizationHeader.startsWith(prefix)) {
            return authorizationHeader.substring(prefix.length()).trim();
        }

        return authorizationHeader.trim();
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim();
    }
}