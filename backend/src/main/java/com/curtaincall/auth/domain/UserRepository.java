package com.curtaincall.auth.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    /**
     * 사용자 행을 트랜잭션이 끝날 때까지 잠그고 id를 돌려준다. 같은 사용자의 선점 요청을 한 줄로 세울 때 쓴다(R2, spec 2.4).
     *
     * <p>FOR NO KEY UPDATE는 같은 행의 FOR NO KEY UPDATE끼리 기다리게 하고, 다른 테이블이 이 사용자를 외래 키로 참조하며 거는
     * FOR KEY SHARE(예: 선점·예매 행 저장)와는 부딪히지 않는다. 행이 없으면 아무것도 잠그지 않고 빈 값을 돌려준다.
     */
    @Query(value = "SELECT id FROM users WHERE id = :id FOR NO KEY UPDATE", nativeQuery = true)
    Optional<Long> lockById(long id);
}
