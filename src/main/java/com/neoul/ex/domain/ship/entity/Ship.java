package com.neoul.ex.domain.ship.entity;

import com.neoul.ex.domain.beach.entity.Beach;
import com.neoul.ex.domain.ship.entity.value.ShipLocation;
import com.neoul.ex.domain.ship.entity.value.ShipStatus;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "boats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ship {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "beach_id", nullable = false)
    private Beach beach;

    @Embedded
    private ShipStatus status;

    @Embedded
    private ShipLocation location;

    public static Ship create(String code, String name, Beach beach) {
        Ship ship = new Ship();
        ship.code = code;
        ship.name = name;
        ship.beach = beach;
        return ship;
    }

    public void updateStatus(ShipStatus status) {
        this.status = status;
    }

    public void updateLocation(ShipLocation location) {
        this.location = location;
    }
}
