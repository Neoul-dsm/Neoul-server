package com.neoul.ex.domain.user.entity;

import com.neoul.ex.domain.beach.entity.Beach;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "star_beaches", uniqueConstraints =
        @UniqueConstraint(name = "uk_star_beach_user_beach", columnNames = {"user_id", "beach_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StarBeach {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Beach beach;

    static StarBeach create(User user, Beach beach) {
        StarBeach registration = new StarBeach();
        registration.user = user;
        registration.beach = beach;
        return registration;
    }

    void detachUser() {
        this.user = null;
    }
}
