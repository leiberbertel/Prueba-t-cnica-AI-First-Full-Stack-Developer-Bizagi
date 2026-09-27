package dev.leiber.polla.matches.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "teams")
class Team {

    @Id
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "flag_code", nullable = false)
    private String flagCode;

    @Column(name = "group_code", nullable = false)
    private String groupCode;

    protected Team() {
    }

    String getCode() {
        return code;
    }

    String getName() {
        return name;
    }

    String getFlagCode() {
        return flagCode;
    }
}
