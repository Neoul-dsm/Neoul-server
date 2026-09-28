package com.neoul.ex.domain.beach.entity;

import com.neoul.ex.domain.beach.entity.value.BeachEnvironment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Embedded;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "beach")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Beach {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Embedded
    private BeachEnvironment environment;

    public static Beach create(String name) {
        Beach beach = new Beach();
        beach.name = name;
        return beach;
    }

    public void updateEnvironment(BeachEnvironment environment) {
        this.environment = environment;
    }
}
