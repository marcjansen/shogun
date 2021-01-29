package de.terrestris.shogun.lib.config;

import com.bedatadriven.jackson.datatype.jts.JtsModule;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.vladmihalcea.hibernate.type.util.ObjectMapperSupplier;
import de.terrestris.shogun.lib.annotation.JsonSuperType;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class JacksonConfig implements ObjectMapperSupplier {

    private static ObjectMapper mapper;

    @Bean
    public ObjectMapper objectMapper() {
        init();
        return mapper;
    }

    private static int srid;

    @Value("${shogun.srid:4326}")
    public void setSrid(int srid) {
        JacksonConfig.srid = srid;
    }

    private static int coordinatePrecisionScale;

    @Value("${shogun.coordinatePrecisionScale:10}")
    public void setCoordinatePrecisionScale(int coordinatePrecisionScale) {
        JacksonConfig.coordinatePrecisionScale = coordinatePrecisionScale;
    }

    @Bean
    public static JtsModule jtsModule() {
        GeometryFactory geomFactory = new GeometryFactory(new PrecisionModel(JacksonConfig.coordinatePrecisionScale), JacksonConfig.srid);
        return new JtsModule(geomFactory);
    }

    @Override
    public ObjectMapper get() {
        return objectMapper();
    }

    @PostConstruct
    public static void init() {
        if (JacksonConfig.mapper == null) {
            JacksonConfig.mapper = new ObjectMapper().registerModule(jtsModule());

            JacksonConfig.mapper.configure(MapperFeature.DEFAULT_VIEW_INCLUSION, false);
            JacksonConfig.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            JacksonConfig.mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

            for (var entry : findAnnotatedClasses().entrySet()) {
                JacksonConfig.mapper.addMixIn(entry.getKey(), entry.getValue());
            }
        }
    }

    private static Map<Class<?>, Class<?>> findAnnotatedClasses() {
        var reflections = new Reflections(new ConfigurationBuilder()
            .setUrls(ClasspathHelper.forJavaClassPath())
            .setScanners(new SubTypesScanner(),
                new TypeAnnotationsScanner()));

        Map<Class<?>, Class<?>> implementers = new HashMap<>();

        // this finds the type furthest down along the implementation chain
        for (var cl : reflections.getTypesAnnotatedWith(JsonSuperType.class)) {
            var annotation = cl.getAnnotation(JsonSuperType.class);
            var superType = annotation.type();

            if (!annotation.override() && !superType.isInterface()) {
                throw new IllegalStateException("The super type " + superType.getName() + " is not an interface. " +
                    "Set override to true if this is intended.");
            }

            if (!implementers.containsKey(superType)) {
                implementers.put(superType, cl);
            } else {
                var previous = implementers.get(superType);
                if (previous.isAssignableFrom(cl)) {
                    implementers.put(superType, cl);
                } else if (!cl.isAssignableFrom(previous)) {
                    throw new IllegalStateException("Found 2 incompatible types that both want to deserialize to the type "
                        + superType.getName() + ". Any existing type should get extended.");
                }
            }
        }

        return implementers;
    }

}