package com.neoul.ex.domain.user.entity;

import com.neoul.ex.domain.user.entity.enums.Role;
import com.neoul.ex.domain.beach.entity.Beach;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    // 기존 계정의 이름은 미등록 상태를 유지하며 신규 가입은 요청 DTO에서 필수 검증한다.
    @Column(length = 50)
    private String name;

    @Column(nullable = false, length = 255)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "beach_id")
    private Beach beach;

    private User(String email, String password, Role role, Beach beach, String name) {
        this.email = email;
        this.name = name;
        this.password = password;
        this.role = role;
        this.beach = beach;
    }

    public static User createGuard(String email, String encodedPassword, Beach beach) {
        return createGuard(email, encodedPassword, beach, null);
    }

    public static User createGuard(String email, String encodedPassword, Beach beach, String name) {
        return new User(email, encodedPassword, Role.GUARD, beach, name);
    }
}
