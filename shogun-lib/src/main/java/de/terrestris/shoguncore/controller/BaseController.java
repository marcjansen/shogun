package de.terrestris.shoguncore.controller;

import de.terrestris.shoguncore.enumeration.PermissionCollectionType;
import de.terrestris.shoguncore.model.BaseEntity;
import de.terrestris.shoguncore.service.BaseService;
import de.terrestris.shoguncore.service.security.permission.UserInstancePermissionService;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.GenericTypeResolver;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;

// TODO Specify and type extension of BaseService
public abstract class BaseController<T extends BaseService<?, S>, S extends BaseEntity> {

    protected final Logger LOG = LogManager.getLogger(getClass());

    @Autowired
    protected T service;

    @Autowired
    protected MessageSource messageSource;

    @Autowired
    protected UserInstancePermissionService userInstancePermissionService;

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<S> findAll() {
        try {
            return service.findAll();
        } catch (AccessDeniedException ade) {
            LOG.info("Access to entity of type {} is denied", getGenericClassName());

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    messageSource.getMessage(
                            "BaseController.NOT_FOUND",
                            null,
                            LocaleContextHolder.getLocale()
                    ),
                    ade
            );
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception e) {
            LOG.error("Error while requesting all entities of type {}: \n {}",
                    getGenericClassName(), e.getMessage());
            LOG.trace("Full stack trace: ", e);

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    messageSource.getMessage(
                            "BaseController.INTERNAL_SERVER_ERROR",
                            null,
                            LocaleContextHolder.getLocale()
                    ),
                    e
            );
        }
    }

    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public S findOne(@PathVariable("id") Long entityId) {
        try {
            Optional<S> entity = service.findOne(entityId);

            if (entity.isPresent()) {
                return entity.get();
            } else {
                LOG.error("Could not find entity of type {} with ID {}",
                        getGenericClassName(), entityId);

                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        messageSource.getMessage(
                                "BaseController.NOT_FOUND",
                                null,
                                LocaleContextHolder.getLocale()
                        )
                );
            }
        } catch (AccessDeniedException ade) {
            LOG.info("Access to entity of type {} with ID {} is denied",
                    getGenericClassName(), entityId);

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    messageSource.getMessage(
                            "BaseController.NOT_FOUND",
                            null,
                            LocaleContextHolder.getLocale()
                    ),
                    ade
            );
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception e) {
            LOG.error("Error while requesting entity of type {} with ID {}: \n {}",
                    getGenericClassName(), entityId, e.getMessage());
            LOG.trace("Full stack trace: ", e);

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    messageSource.getMessage(
                            "BaseController.INTERNAL_SERVER_ERROR",
                            null,
                            LocaleContextHolder.getLocale()
                    ),
                    e
            );
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public S add(@RequestBody S entity) {
        try {
            S persistedEntity = service.create(entity);

            userInstancePermissionService.setPermission(persistedEntity, PermissionCollectionType.ADMIN);

            return persistedEntity;
        } catch (AccessDeniedException ade) {
            LOG.info("Creating entity of type {} is denied", getGenericClassName());

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    messageSource.getMessage(
                            "BaseController.NOT_FOUND",
                            null,
                            LocaleContextHolder.getLocale()
                    ),
                    ade
            );
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception e) {
            LOG.error("Error while creating entity {}: \n {}", entity, e.getMessage());
            LOG.trace("Full stack trace: ", e);

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    messageSource.getMessage(
                            "BaseController.INTERNAL_SERVER_ERROR",
                            null,
                            LocaleContextHolder.getLocale()
                    ),
                    e
            );
        }
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public S update(@RequestBody S entity, @PathVariable("id") Long entityId) {
        try {
            if (!entityId.equals(entity.getId())) {
                LOG.error("IDs of update candidate (ID: {}) and update data ({}) don't match.",
                    entityId, entity);

                throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            }

            Optional<S> persistedEntity = service.findOne(entityId);

            if (persistedEntity.isPresent()) {
                return service.update(entityId, entity);
            } else {
                LOG.error("Could not find entity of type {} with ID {}",
                        getGenericClassName(), entityId);

                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        messageSource.getMessage(
                                "BaseController.NOT_FOUND",
                                null,
                                LocaleContextHolder.getLocale()
                        )
                );
            }
        } catch (AccessDeniedException ade) {
            LOG.info("Updating entity of type {} with ID {} is denied",
                    getGenericClassName(), entityId);

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    messageSource.getMessage(
                            "BaseController.NOT_FOUND",
                            null,
                            LocaleContextHolder.getLocale()
                    ),
                    ade
            );
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception e) {
            LOG.error("Error while updating entity of type {} with ID {}: \n {}",
                    getGenericClassName(), entityId, e.getMessage());
            LOG.trace("Full stack trace: ", e);

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    messageSource.getMessage(
                            "BaseController.INTERNAL_SERVER_ERROR",
                            null,
                            LocaleContextHolder.getLocale()
                    ),
                    e
            );
        }
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") Long entityId) {
        try {
            Optional<S> entity = service.findOne(entityId);

            if (entity.isPresent()) {
                service.delete(entity.get());
            } else {
                LOG.error("Could not find entity of type {} with ID {}",
                        getGenericClassName(), entityId);

                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        messageSource.getMessage(
                                "BaseController.NOT_FOUND",
                                null,
                                LocaleContextHolder.getLocale()
                        )
                );
            }
        } catch (AccessDeniedException ade) {
            LOG.info("Deleting entity of type {} with ID {} is denied",
                    getGenericClassName(), entityId);

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    messageSource.getMessage(
                            "BaseController.NOT_FOUND",
                            null,
                            LocaleContextHolder.getLocale()
                    ),
                    ade
            );
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception e) {
            LOG.error("Error while deleting entity of type {} with ID {}: \n {}",
                    getGenericClassName(), entityId, e.getMessage());
            LOG.trace("Full stack trace: ", e);

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    messageSource.getMessage(
                            "BaseController.INTERNAL_SERVER_ERROR",
                            null,
                            LocaleContextHolder.getLocale()
                    ),
                    e
            );
        }
    }

    private String getGenericClassName() {
        Class<?>[] resolvedTypeArguments = GenericTypeResolver.resolveTypeArguments(getClass(),
                BaseController.class);

        if (resolvedTypeArguments != null && resolvedTypeArguments.length == 2) {
            return resolvedTypeArguments[1].getSimpleName();
        } else {
            return null;
        }
    }
}
