package de.terrestris.shogun.lib.model.security.permission;

import de.terrestris.shogun.lib.model.BaseEntity;
import lombok.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;
import javax.persistence.OneToOne;

@MappedSuperclass
@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = true)
public abstract class InstancePermission extends BaseEntity {

    @Column
    private Long entityId;

    @OneToOne(optional = false)
    @Fetch(FetchMode.JOIN)
    private PermissionCollection permissions;

}
