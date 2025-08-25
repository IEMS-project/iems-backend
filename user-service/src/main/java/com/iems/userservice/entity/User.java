package com.iems.userservice.entity;

import com.iems.userservice.entity.enums.Gender;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

import java.util.Date;
import java.util.UUID;

@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(unique = true, nullable = false, length = 150)
    private String email;

    @Column(length = 200)
    private String address;

    @Column(length = 20, unique = true)
    private String phone;

    @Temporal(TemporalType.DATE)
    private Date dob;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Column(name = "personal_id", unique = true, nullable = false, length = 20)
    private String personalID;

    @Column(length = 255)
    private String image;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", updatable = false)
    private Date createdAt = new Date();

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_at")
    private Date updatedAt = new Date();

}
