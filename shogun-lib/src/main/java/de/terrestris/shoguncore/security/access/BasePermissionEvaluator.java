package de.terrestris.shoguncore.security.access;

import de.terrestris.shoguncore.enumeration.PermissionType;
import de.terrestris.shoguncore.model.BaseEntity;
import de.terrestris.shoguncore.model.User;
import de.terrestris.shoguncore.repository.UserRepository;
import de.terrestris.shoguncore.security.access.entity.BaseEntityPermissionEvaluator;
import de.terrestris.shoguncore.security.access.entity.DefaultPermissionEvaluator;
import de.terrestris.shoguncore.specification.UserSpecification;
import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class BasePermissionEvaluator implements PermissionEvaluator {

    protected final Logger LOG = LogManager.getLogger(getClass());

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    private List<BaseEntityPermissionEvaluator> permissionEvaluators;

    @Autowired
    private DefaultPermissionEvaluator defaultPermissionEvaluator;

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permissionObject) {
        LOG.trace("About to evaluate permission for user '{}' targetDomainObject '{}' " +
                "and permissionObject '{}'", authentication, targetDomainObject, permissionObject);

        if (authentication == null) {
            LOG.trace("Restricting access since no authentication is available.");
            return false;
        }

        if (targetDomainObject == null || (targetDomainObject instanceof Optional &&
            ((Optional) targetDomainObject).isEmpty())) {
            LOG.trace("Restricting access since no target domain object is available.");
            return false;
        }

        if (!(permissionObject instanceof String)) {
            LOG.trace("Restricting access since no permission object is available.");
            return false;
        }

        User user = this.getUserFromAuthentication(authentication);

        if (user == null) {
            LOG.trace("Restricting access since no user is available.");

            return false;
        }

        final BaseEntity persistentObject;
        if (targetDomainObject instanceof Optional) {
            persistentObject = ((Optional<BaseEntity>) targetDomainObject).get();
        } else {
            persistentObject = (BaseEntity) targetDomainObject;
        }

        final PermissionType permission = PermissionType.valueOf((String) permissionObject);

        LOG.trace("Getting the appropriate permission evaluator implementation for class '{}'",
            targetDomainObject.getClass().getSimpleName());

        BaseEntityPermissionEvaluator entityPermissionEvaluator =
                this.getPermissionEvaluatorForClass(persistentObject);

        LOG.trace("Checking permissions with permission evaluator '{}'",
            entityPermissionEvaluator.getClass().getSimpleName());

        return entityPermissionEvaluator.hasPermission(user, persistentObject, permission);
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission) {
        // TODO Backport from main
        return false;
    }

    /**
     * Returns the {@BaseEntityPermissionEvaluator} for the given {@BaseEntity}.
     *
     * @param persistentObject
     * @return
     */
    protected BaseEntityPermissionEvaluator getPermissionEvaluatorForClass(BaseEntity persistentObject) {
        BaseEntityPermissionEvaluator entityPermissionEvaluator = permissionEvaluators.stream()
                .filter(permissionEvaluator -> persistentObject.getClass().equals(
                        permissionEvaluator.getEntityClassName()))
                .findAny()
                .orElse(defaultPermissionEvaluator);

        return entityPermissionEvaluator;
    }

    /**
     * Returns the current user object from the database.
     *
     * @param authentication
     * @return
     */
    protected User getUserFromAuthentication(Authentication authentication) {
        final Object principal = authentication.getPrincipal();

        String userMail;

        if (principal instanceof String) {
            userMail = (String) principal;
        } else if (principal instanceof org.springframework.security.core.userdetails.User) {
            userMail = ((org.springframework.security.core.userdetails.User) principal).getUsername();
        } else {
            LOG.error("Could not detect user from authentication, evaluation of permissions will fail.");
            return null;
        }

        return userRepository.findOne(UserSpecification.findByMail(userMail)).orElse(null);
    }
}
