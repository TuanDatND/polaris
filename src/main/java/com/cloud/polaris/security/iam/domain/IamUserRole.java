package com.cloud.polaris.security.iam.domain;

import com.cloud.polaris.security.iam.domain.id.UserRoleId;
import com.cloud.polaris.security.user.domain.AppUser;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "iam_user_roles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IamUserRole {

    @EmbeddedId
    private UserRoleId id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @MapsId("roleId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private IamRole role;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    Instant createdAt;
}
