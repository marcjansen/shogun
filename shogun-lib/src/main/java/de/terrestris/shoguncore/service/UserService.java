package de.terrestris.shoguncore.service;

import de.terrestris.shoguncore.dto.PasswordChange;
import de.terrestris.shoguncore.dto.RegisterUserDto;
import de.terrestris.shoguncore.enumeration.PermissionCollectionType;
import de.terrestris.shoguncore.event.OnRegistrationConfirmedEvent;
import de.terrestris.shoguncore.exception.EmailExistsException;
import de.terrestris.shoguncore.model.Group;
import de.terrestris.shoguncore.model.User;
import de.terrestris.shoguncore.model.security.Identity;
import de.terrestris.shoguncore.model.security.permission.UserClassPermission;
import de.terrestris.shoguncore.model.security.permission.UserInstancePermission;
import de.terrestris.shoguncore.model.token.UserVerificationToken;
import de.terrestris.shoguncore.repository.UserRepository;
import de.terrestris.shoguncore.repository.security.IdentityRepository;
import de.terrestris.shoguncore.repository.security.permission.UserClassPermissionRepository;
import de.terrestris.shoguncore.repository.security.permission.UserInstancePermissionRepository;
import de.terrestris.shoguncore.repository.token.UserVerificationTokenRepository;
import de.terrestris.shoguncore.service.security.IdentityService;
import de.terrestris.shoguncore.service.security.permission.UserInstancePermissionService;
import de.terrestris.shoguncore.specification.UserSpecification;
import de.terrestris.shoguncore.specification.token.UserVerificationTokenSpecification;
import java.util.Calendar;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService extends BaseService<UserRepository, User> {

    @Autowired
    private UserVerificationTokenRepository userVerificationTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private IdentityService identityService;

    @Autowired
    private IdentityRepository identityRepository;

    @Autowired
    private UserInstancePermissionService userInstancePermissionService;

    @Autowired
    private UserInstancePermissionRepository userInstancePermissionRepository;

    @Autowired
    private UserClassPermissionRepository userClassPermissionRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    protected MessageSource messageSource;

    private static final String TOKEN_INVALID = "invalidToken";
    private static final String TOKEN_EXPIRED = "expired";
    private static final String TOKEN_VALID = "valid";

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public User register(RegisterUserDto registerUserData) throws IllegalArgumentException, EmailExistsException {
        if (registerUserData == null) {
            throw new IllegalArgumentException("Invalid user data");
        }

        if (emailExists(registerUserData.getEmail())) {
            LOG.info("Registration aborted because email {} already exists", registerUserData.getEmail());
            throw new EmailExistsException("There already is an account with this email address");
        }

        LOG.info("Registering a new user.");

        try {
            User user = new User();
            user.setUsername(registerUserData.getEmail());
            user.setEmail(registerUserData.getEmail());
            user.setEnabled(false);
            user.setPassword(passwordEncoder.encode(registerUserData.getPassword()));

            User persistedUser = repository.save(user);

            LOG.info("Successfully registered a new user.");

            return persistedUser;
        } catch (Exception e) {
            LOG.error("Error while registering a new user: {}", e.getMessage());
            LOG.trace("Full stack trace: ", e);

            throw new RuntimeException();
        }
    }

    /**
     * Deletes a {@link de.terrestris.shoguncore.model.User} and all corresponding entities:
     *   * All {@link de.terrestris.shoguncore.model.token.UserVerificationToken}
     *   * All {@link de.terrestris.shoguncore.model.security.Identity}
     *   * All {@link de.terrestris.shoguncore.model.security.permission.UserClassPermission}
     *   * All {@link de.terrestris.shoguncore.model.security.permission.UserInstancePermission}
     *   * Membership of all {@link de.terrestris.shoguncore.model.Group}
     *
     * Note: This method is without any pre-authorization check by intention since
     *       it may be called from a context without a principal (e.g. during cleanup
     *       after new user has been registered).
     *
     * @param user The user to delete.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void deleteUser(User user) {

        LOG.info("Deleting user with ID {}", user.getId());

        if (user.getUsername().equalsIgnoreCase("admin")) {
            throw new RuntimeException("The admin user can't be deleted!");
        }

        try {
            LOG.trace("Deleting all pending user verification tokens");

            // Get all pending user verification tokens.
            List<UserVerificationToken> userVerificationTokens =
                userVerificationTokenRepository.findAllByUser(user);

            LOG.trace("Found {} pending user verification tokens to delete",
                userVerificationTokens.size());

            // Delete the pending user verification tokens.
            for (UserVerificationToken userVerificationToken : userVerificationTokens) {
                userVerificationTokenRepository.delete(userVerificationToken);

                LOG.trace("Successfully deleted user verification token with ID {}",
                    userVerificationToken.getId());
            }

            LOG.trace("Deleting all user instance permissions");

            // Get all user instance permissions.
            List<UserInstancePermission> userInstancePermissions = userInstancePermissionRepository
                .findAllByEntityId(user.getId());

            LOG.trace("Found {} user instance permissions to delete",
                userInstancePermissions.size());

            // Delete the user instance permissions.
            for (UserInstancePermission userInstancePermission : userInstancePermissions) {
                userInstancePermissionRepository.delete(userInstancePermission);

                LOG.trace("Successfully deleted user instance permission with ID {}",
                    userInstancePermission.getId());
            }

            LOG.trace("Deleting all user class permissions");

            // Get all user class permissions.
            List<UserClassPermission> userClassPermissions = userClassPermissionRepository
                .findAllByUser(user);

            LOG.trace("Found {} user class permissions to delete", userClassPermissions.size());

            // Delete the user class permissions.
            for (UserClassPermission userClassPermission : userClassPermissions) {
                userClassPermissionRepository.delete(userClassPermission);

                LOG.trace("Successfully deleted user class permission with ID {}",
                    userClassPermission.getId());
            }

            LOG.trace("Removing the user from all its groups");

            // Get all groups the user is member in.
            List<Group> groups = identityService.findAllGroupsFrom(user);

            LOG.trace("Found {} groups to remove the user from", groups.size());

            // Remove the user from all groups.
            for (Group group : groups) {
                identityService.removeUserFromGroup(user, group);

                LOG.trace("Successfully removed user from group with ID {}",
                    group);
            }

            LOG.trace("Deleting all identities");

            // Get all identities of the user.
            List<Identity> identities = identityService.findAllIdentitiesBy(user);

            LOG.trace("Found {} identities to delete", identities.size());

            // Delete the identities.
            for (Identity identity : identities) {
                identityRepository.delete(identity);

                LOG.trace("Successfully deleted identity with ID {}",
                    identity.getId());
            }

            LOG.trace("Deleting the user itself");

            repository.delete(user);

            LOG.trace("Successfully deleted the user");
        } catch (Exception e) {
            LOG.error("Error while deleting user with ID {}: {}. Any " +
                "changes made will be rolled back.", user.getId(), e.getMessage());
            LOG.trace("Full stack trace:", e);

            // Throw a runtime exception to trigger rollback on the DB.
            throw new RuntimeException();
        }
    }

    /**
     * Checks if an username exists.
     * @param username The username to be checked.
     * @return Whether the username already exists in the database or not.
     */
    private boolean usernameExists(final String username) {
        return this.repository.findOne(UserSpecification.findByUserName(username)).isPresent();
    }

    /**
     * Checks if an email exists.
     * @param email The email to be checked.
     * @return Whether the username already exists in the database or not.
     */
    private boolean emailExists(final String email) {
        return this.repository.findOne(UserSpecification.findByMail(email)).isPresent();
    }

    /**
     * Creates an initial verification token for a new user.
     * @param user The user
     * @param token The token
     */
    public void createVerificationTokenForUser(final User user, final String token) {
        final UserVerificationToken myToken = new UserVerificationToken(token, user);
        userVerificationTokenRepository.save(myToken);
    }

    /**
     * Validates the expiration date of a verification token
     * @param token The token string.
     * @return Returns `TOKEN_INVALID` if the token can't be found, returns `TOKEN_EXPIRED` if the token has expired and
     * `TOKEN_VALID` otherwise.
     */
    public String validateVerificationToken(String token) {
        final UserVerificationToken verificationToken =
            userVerificationTokenRepository.findOne(UserVerificationTokenSpecification.findByToken(token)).orElseThrow();

        if (verificationToken == null) {
            return TOKEN_INVALID;
        }

        final User user = verificationToken.getUser();
        final Calendar cal = Calendar.getInstance();
        if ((verificationToken.getExpiryDate()
            .getTime()
            - cal.getTime()
            .getTime()) <= 0) {
            userVerificationTokenRepository.delete(verificationToken);
            return TOKEN_EXPIRED;
        }

        enableUser(user);

        userVerificationTokenRepository.delete(verificationToken);

        OnRegistrationConfirmedEvent event = new OnRegistrationConfirmedEvent(user);
        eventPublisher.publishEvent(event);

        return TOKEN_VALID;
    }

    /**
     *
     * @param user
     */
    private void enableUser(User user) {
        user.setEnabled(true);

        identityService.initUserIdenity(user);

        userInstancePermissionService.setPermission(user, user, PermissionCollectionType.ADMIN);

        repository.save(user);
    }

    /**
     * Changes user Password
     * @param user the user that is requesting a password change
     * @param passwordChange object containing the old and new password
     * @return
     */
    @PreAuthorize("hasRole('ROLE_ADMIN') or hasPermission(#user, 'UPDATE')")
    public void changeUserPassword(User user, PasswordChange passwordChange) throws SecurityException {
        String currentPassword = user.getPassword();
        String givenOldPassword = passwordChange.getOldPassword();
        String newPassword = passwordChange.getNewPassword();
        String encodedNewPassword = passwordEncoder.encode(newPassword);

        if (!passwordEncoder.matches(givenOldPassword, currentPassword)) {
            throw new SecurityException("Your current password does not match with the given old one. Aborting password change.");
        }

        user.setPassword(encodedNewPassword);

        repository.save(user);
    }
}
