package de.terrestris.shogun.lib.service.security.permission;

import de.terrestris.shogun.lib.enumeration.PermissionCollectionType;
import de.terrestris.shogun.lib.model.BaseEntity;
import de.terrestris.shogun.lib.model.Group;
import de.terrestris.shogun.lib.model.User;
import de.terrestris.shogun.lib.model.security.permission.GroupClassPermission;
import de.terrestris.shogun.lib.model.security.permission.PermissionCollection;
import de.terrestris.shogun.lib.repository.security.permission.GroupClassPermissionRepository;
import de.terrestris.shogun.lib.repository.security.permission.PermissionCollectionRepository;
import de.terrestris.shogun.lib.security.SecurityContextUtil;
import de.terrestris.shogun.lib.util.KeycloakUtil;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GroupClassPermissionService extends BasePermissionService<GroupClassPermissionRepository, GroupClassPermission> {

    @Autowired
    protected SecurityContextUtil securityContextUtil;

    @Autowired
    protected KeycloakUtil keycloakUtil;

    @Autowired
    protected PermissionCollectionRepository permissionCollectionRepository;

    /**
     * Returns all {@link GroupClassPermission} for the given query arguments.
     *
     * @param group The group to find the permissions for.
     * @return The permissions.
     */
    public List<GroupClassPermission> findFor(BaseEntity entity) {

        LOG.trace("Getting all group class permissions for entity with ID {}", entity.getId());

        return repository.findByClassName(entity.getClass().getCanonicalName());
    }

    /**
     * Returns all {@link GroupClassPermission} for the given query arguments.
     *
     * @param group The group to find the permissions for.
     * @return The permissions.
     */
    public List<GroupClassPermission> findFor(Group group) {

        LOG.trace("Getting all group class permissions for group with Keycloak ID {}",
            group.getKeycloakId());

        return repository.findAllByGroup(group);
    }

    /**
     * Find group class permission for class of entity and given group
     *
     * @param entity The entity to find the permission for.
     * @param group The group to find the permission for.
     * @return The (optional) permission.
     */
    public Optional<GroupClassPermission> findFor(BaseEntity entity, Group group) {
        String className = entity.getClass().getCanonicalName();

        LOG.trace("Getting all group class permissions for group with Keycloak ID {} and " +
            "entity class {}", group.getKeycloakId(), className);

        return repository.findByGroupIdAndClassName(group.getId(), className);
    }

    /**
     * Returns the {@link GroupClassPermission} for the given query arguments.
     *
     * @param clazz The class to find the permission for.
     * @param group The group to find the permission for.
     * @return The (optional) permission.
     */
    public Optional<GroupClassPermission> findFor(Class<? extends BaseEntity> clazz, Group group) {
        String className = clazz.getCanonicalName();

        LOG.trace("Getting all group class permissions for group with Keycloak ID {} and " +
            "entity class {}", group.getKeycloakId(), className);

        return repository.findByGroupIdAndClassName(group.getId(), className);
    }

    /**
     * Returns the {@link GroupClassPermission} for the given query arguments. Hereby
     * all groups of the given user will be considered.
     *
     * @param clazz The class to find the permission for.
     * @param user The user to find the permission for.
     * @return The (optional) permission.
     */
    public Optional<GroupClassPermission> findFor(Class<? extends BaseEntity> clazz, User user) {
        String className = clazz.getCanonicalName();

        LOG.trace("Getting all group class permissions for user with Keycloak ID {} and " +
            "entity class {}", user.getKeycloakId(), className);

        // Get all groups of the user from Keycloak
        List<Group> groups = securityContextUtil.getGroupsForUser(user);
        Optional<GroupClassPermission> gcp = Optional.empty();
        for (Group g : groups) {
            Optional<GroupClassPermission> permissionsForGroup = repository
                .findByGroupIdAndClassName(g.getId(), className);

            if (permissionsForGroup.isPresent()) {
                gcp = permissionsForGroup;
                break;
            }
        }

        return gcp;
    }

    /**
     * Returns the {@link GroupClassPermission} for the given query arguments. Hereby
     * it will be considered if the user is currently a member of the given group.
     *
     * @param entity The entity to find the permission for.
     * @param group The group to find the permission for.
     * @param user The user to find the permission for.
     * @return The (optional) permission.
     */
    public Optional<GroupClassPermission> findFor(BaseEntity entity, Group group, User user) {

        LOG.trace("Getting all group class permissions for user with Keycloak ID {} and " +
            "entity with ID {} in the context of group with Keycloak ID {}", user.getKeycloakId(),
            entity.getId(), group.getKeycloakId());

        boolean isUserMemberInGroup = keycloakUtil.isUserInGroup(user, group);

        if (!isUserMemberInGroup) {
            LOG.trace("The user is not a member of the given group, no permissions available.");

            return Optional.empty();
        }

        return repository.findByGroupIdAndClassName(group.getId(), entity.getClass().getCanonicalName());
    }

    /**
     * Returns the {@link PermissionCollection} for the given query arguments.
     *
     * @param entity The entity to find the collection for.
     * @param group The group to find the collection for.
     * @return The collection (may be empty).
     */
    public PermissionCollection findPermissionCollectionFor(BaseEntity entity, Group group) {
        Optional<GroupClassPermission> groupClassPermission = this.findFor(entity, group);

        return getPermissionCollection(groupClassPermission);
    }

    /**
     * Returns the {@link GroupClassPermission} for the given query arguments. Hereby
     * the class of the given entity and all groups of the given user will be considered.
     *
     * @param entity The entity to find the permission for.
     * @param user The user to find the permission for.
     * @return The (optional) permission.
     */
    public PermissionCollection findPermissionCollectionFor(BaseEntity entity, User user) {
        Class<? extends BaseEntity> clazz = entity.getClass();
        Optional<GroupClassPermission> groupClassPermission = this.findFor(clazz, user);

        return getPermissionCollection(groupClassPermission);
    }

    /**
     * Returns the {@link PermissionCollection} for the given query arguments. Hereby
     * it will be considered if the user is currently a member of the given group.
     *
     * @param entity The entity to find the collection for.
     * @param group The group to find the collection for.
     * @param user The user to find the collection for.
     * @return The collection (may be empty).
     */
    public PermissionCollection findPermissionCollectionFor(BaseEntity entity, Group group, User user) {
        Optional<GroupClassPermission> groupClassPermission = this.findFor(entity, group, user);

        return getPermissionCollection(groupClassPermission);
    }

    /**
     * Sets the {@link PermissionCollection} for the given target combination.
     *
     * @param clazz The class to set the permission for.
     * @param group The group to set the permission for.
     * @param permissionCollectionType The permission collection type (e.g. READ, READ_WRITE) to set.
     */
    public void setPermission(Class<? extends BaseEntity> clazz, Group group, PermissionCollectionType permissionCollectionType) {
        Optional<PermissionCollection> permissionCollection = permissionCollectionRepository
            .findByName(permissionCollectionType);

        if (permissionCollection.isEmpty()) {
            throw new RuntimeException("Could not find requested permission collection");
        }

        clearExistingPermission(group, permissionCollection.get(), clazz);

        GroupClassPermission groupClassPermission = new GroupClassPermission();
        groupClassPermission.setGroup(group);
        groupClassPermission.setClassName(clazz.getCanonicalName());
        groupClassPermission.setPermissions(permissionCollection.get());

        repository.save(groupClassPermission);
    }

    /**
     * Clears the given {@link PermissionCollection} for the given target combination.
     *
     * @param group The group to clear the permission for.
     * @param permissionCollection The permission collection to clear.
     * @param clazz The clazz to clear the permission for.
     */
    private void clearExistingPermission(Group group, PermissionCollection permissionCollection, Class<? extends BaseEntity> clazz) {
        Optional<GroupClassPermission> existingPermission = findFor(clazz, group);

        // Check if there is already an existing permission set on the entity
        if (existingPermission.isPresent()) {
            LOG.debug("Permission is already set for class {} and group with " +
                "Keycloak ID {}: {}", clazz, group.getKeycloakId(), permissionCollection);

            // Remove the existing one
            repository.delete(existingPermission.get());

            LOG.debug("Removed the permission");
        }
    }

    /**
     * Helper function to get the {@link PermissionCollection} from a given
     * class permission. If no collection is available, it returns an empty
     * list.
     *
     * @param classPermission The classPermission to get the permissions from.
     * @return The collection (may be empty).
     */
    private PermissionCollection getPermissionCollection(Optional<GroupClassPermission> classPermission) {
        if (classPermission.isPresent()) {
            return classPermission.get().getPermissions();
        }

        return new PermissionCollection();
    }

    public void deleteAllFor(BaseEntity persistedEntity) {
        List<GroupClassPermission> groupClassPermissions = this.findFor(persistedEntity);

        repository.deleteAll(groupClassPermissions);

        LOG.info("Successfully deleted all group class permissions for entity with ID {}",
            persistedEntity.getId());
    }

    public void deleteFor(BaseEntity persistedEntity, Group group) {
        Optional<GroupClassPermission> groupClassPermission = this.findFor(persistedEntity, group);

        if (groupClassPermission.isPresent()) {
            repository.delete(groupClassPermission.get());

            LOG.info("Successfully deleted the group class permission for entity with ID {} and group {}.",
                persistedEntity.getId(), group.getId());
        } else {
            LOG.warn("Could not delete the group class permission. The requested permission does not exist.");
        }
    }
}
