package de.terrestris.shogun.lib.service.security.permission;

import de.terrestris.shogun.lib.enumeration.PermissionCollectionType;
import de.terrestris.shogun.lib.model.BaseEntity;
import de.terrestris.shogun.lib.model.User;
import de.terrestris.shogun.lib.model.security.permission.PermissionCollection;
import de.terrestris.shogun.lib.model.security.permission.UserClassPermission;
import de.terrestris.shogun.lib.repository.security.permission.PermissionCollectionRepository;
import de.terrestris.shogun.lib.repository.security.permission.UserClassPermissionRepository;
import de.terrestris.shogun.lib.security.SecurityContextUtil;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserClassPermissionService extends BasePermissionService<UserClassPermissionRepository, UserClassPermission> {

    @Autowired
    protected SecurityContextUtil securityContextUtil;

    @Autowired
    protected PermissionCollectionRepository permissionCollectionRepository;

    /**
     * Returns all {@link UserClassPermission} for the given query arguments.
     *
     * @param user The user to find the permissions for.
     * @return The permissions.
     */
    public List<UserClassPermission> findFor(User user) {

        LOG.trace("Getting all user class permissions for user with Keycloak ID {}",
            user.getKeycloakId());

        return repository.findAllByUser(user);
    }

    /**
     * Get all {@link UserClassPermission} for the given entity.
     *
     * @param entity entity to get user permissions for
     * @return
     */
    public List<UserClassPermission> findFor(BaseEntity entity) {
        String className = entity.getClass().getCanonicalName();

        LOG.trace("Getting all user class permissions for entity class {}", className);

        return repository.findByClassName(className);
    }

    /**
     * Return {@link Optional} containing {@link UserClassPermission}
     * @param clazz The class that should be checked
     * @param user The user to check for
     * @return {@link Optional} containing {@link UserClassPermission}
     */
    public Optional<UserClassPermission> findFor(Class<? extends BaseEntity> clazz, User user) {
        String className = clazz.getCanonicalName();

        LOG.trace("Getting all user class permissions for user with Keycloak ID {} and " +
            "entity class {}", user.getKeycloakId(), className);

        return repository.findByUserIdAndClassName(user.getId(), className);
    }

    /**
     * Returns the {@link UserClassPermission} for the given query arguments. Hereby
     * the class of the given entity will be considered.
     *
     * @param entity The entity to find the permission for.
     * @param user The user to find the permission for.
     * @return The (optional) permission.
     */
    public Optional<UserClassPermission> findFor(BaseEntity entity, User user) {
        LOG.trace("Getting all user class permissions for user with Keycloak ID {} and " +
            "entity class {}", user.getKeycloakId(), entity.getClass().getCanonicalName());

        return repository.findByUserIdAndClassName(user.getId(), entity.getClass().getCanonicalName());
    }

    /**
     * Returns the {@link PermissionCollection} for the given query arguments. Hereby
     * the class of the given entity and and all groups of the given user will be considered.
     *
     * @param entity The entity to find the collection for.
     * @param user The user to find the collection for.
     * @return The collection (may be empty).
     */
    public PermissionCollection findPermissionCollectionFor(BaseEntity entity, User user) {
        Class<? extends BaseEntity> clazz = entity.getClass();
        Optional<UserClassPermission> userClassPermission = this.findFor(clazz, user);

        return getPermissionCollection(userClassPermission);
    }

    /**
     * Sets the given {@link PermissionCollectionType} for the given class and the currently
     * logged in user.
     *
     * @param clazz The class to set the permission for.
     * @param permissionCollectionType The permission to set.
     */
    public void setPermission(Class<? extends BaseEntity> clazz, PermissionCollectionType permissionCollectionType) {
        Optional<User> activeUser = securityContextUtil.getUserBySession();

        if (activeUser.isEmpty()) {
            throw new RuntimeException("Could not detect the logged in user.");
        }

        setPermission(clazz, activeUser.get(), permissionCollectionType);
    }

    /**
     * Sets the given {@link PermissionCollectionType} for the given class and user.
     *
     * @param clazz The class to find set the permission for.
     * @param user The user to find set the permission for.
     * @param permissionCollectionType The permission to set.
     */
    public void setPermission(Class<? extends BaseEntity> clazz, User user, PermissionCollectionType permissionCollectionType) {
        Optional<PermissionCollection> permissionCollection = permissionCollectionRepository
            .findByName(permissionCollectionType);

        if (permissionCollection.isEmpty()) {
            throw new RuntimeException("Could not find requested permission collection");
        }

        clearExistingPermission(user, permissionCollection.get(), clazz);

        UserClassPermission userClassPermission = new UserClassPermission();
        userClassPermission.setUser(user);
        userClassPermission.setClassName(clazz.getCanonicalName());
        userClassPermission.setPermissions(permissionCollection.get());

        repository.save(userClassPermission);
    }

    /**
     * Clears the given {@link PermissionCollection} for the given target combination.
     *
     * @param user The user to clear the permission for.
     * @param permissionCollection The permission collection to clear.
     * @param clazz The class to clear the permission for.
     */
    private void clearExistingPermission(User user, PermissionCollection permissionCollection, Class<? extends BaseEntity> clazz) {
        Optional<UserClassPermission> existingPermission = findFor(clazz, user);

        // Check if there is already an existing permission set on the entity
        if (existingPermission.isPresent()) {
            LOG.debug("Permission is already set for clazz {} and user with " +
                "Keycloak ID {}: {}", clazz.getCanonicalName(), user, permissionCollection);

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
    private PermissionCollection getPermissionCollection(Optional<UserClassPermission> classPermission) {
        if (classPermission.isPresent()) {
            return classPermission.get().getPermissions();
        }

        return new PermissionCollection();
    }

    public void deleteAllFor(BaseEntity persistedEntity) {
        List<UserClassPermission> userClassPermissions = this.findFor(persistedEntity);

        repository.deleteAll(userClassPermissions);

        LOG.info("Successfully deleted all user class permissions for entity with ID {}",
            persistedEntity.getId());
    }

    public void deleteFor(BaseEntity persistedEntity, User user) {
        Optional<UserClassPermission> userClassPermission = this.findFor(persistedEntity.getClass(), user);

        if (userClassPermission.isPresent()) {
            repository.delete(userClassPermission.get());

            LOG.info("Successfully deleted the user class permission for entity with ID {} and user {}.",
                persistedEntity.getId(), user.getId());
        } else {
            LOG.warn("Could not delete the user class permission. The requested permission does not exist.");
        }
    }
}
