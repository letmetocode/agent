package com.getoffer.test;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.core.importer.ImportOption;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 架构依赖边界规则。
 */
@AnalyzeClasses(packages = "com.getoffer", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureDependencyRuleTest {

    @ArchTest
    static final ArchRule domain_should_not_depend_on_infrastructure =
            noClasses().that()
                    .resideInAPackage("com.getoffer.domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("com.getoffer.infrastructure..");

    @ArchTest
    static final ArchRule trigger_should_not_depend_on_infrastructure =
            noClasses().that()
                    .resideInAPackage("com.getoffer.trigger..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("com.getoffer.infrastructure..");

    @ArchTest
    static final ArchRule infrastructure_should_not_depend_on_trigger =
            noClasses().that()
                    .resideInAPackage("com.getoffer.infrastructure..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("com.getoffer.trigger..");

    @ArchTest
    static final ArchRule api_should_not_depend_on_trigger_or_infrastructure =
            noClasses().that()
                    .resideInAPackage("com.getoffer.api..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.getoffer.trigger..", "com.getoffer.infrastructure..");
}
