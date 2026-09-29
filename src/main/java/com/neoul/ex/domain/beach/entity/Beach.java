package com.neoul.ex.domain.beach.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "beaches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Beach {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "decimal(10,7)")
    private Double latitude;

    @Column(columnDefinition = "decimal(10,7)")
    private Double longitude;

    public static Beach create(String name) {
        Beach beach = new Beach();
        beach.name = name;
        return beach;
    }

    public void updateLocation(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Invalid beach coordinates");
        }
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
