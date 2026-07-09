package org.aiknowledgebase.repository;

import org.aiknowledgebase.entity.UserToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserTokenRepository extends JpaRepository<UserToken, Long> {

    Optional<UserToken> findByToken(String token);

    void deleteByToken(String token);

    void deleteByUserId(Long userId);
}