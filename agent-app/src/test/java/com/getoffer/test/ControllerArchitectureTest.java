package com.getoffer.test;

import com.getoffer.trigger.http.ChatRoutingV3Controller;
import com.getoffer.trigger.http.ChatV3Controller;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

public class ControllerArchitectureTest {

    private static final List<Class<?>> CONTROLLERS = List.of(
            ChatV3Controller.class,
            ChatRoutingV3Controller.class
    );

    @Test
    public void controllersShouldNotDependOnDomainRepositoryPorts() {
        for (Class<?> controller : CONTROLLERS) {
            for (Field field : controller.getDeclaredFields()) {
                String typeName = field.getType().getName();
                Assertions.assertFalse(
                        typeName.contains(".domain") && typeName.contains(".adapter.repository."),
                        () -> "Controller should not inject repository port directly: " + controller.getSimpleName() + " -> " + typeName
                );
            }
        }
    }
}
