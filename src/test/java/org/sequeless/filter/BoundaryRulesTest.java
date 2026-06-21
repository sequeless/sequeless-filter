package org.sequeless.filter;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Mechanically enforced hexagonal boundaries for {@code sequeless-filter}.
 */
@AnalyzeClasses(
        packages = "org.sequeless.filter",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class BoundaryRulesTest {

    @ArchTest
    static final ArchRule filter_is_free_of_spring = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..")
            .as("sequeless-filter must stay transport-agnostic — no Spring dependency");

    @ArchTest
    static final ArchRule internal_is_not_referenced_from_outside_filter = noClasses()
            .that()
            .resideOutsideOfPackage("org.sequeless.filter..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("org.sequeless.filter.internal..")
            .as("..filter.internal.. is not part of the published contract")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule api_and_spi_types_are_public = classes()
            .that()
            .resideInAnyPackage("org.sequeless.filter.api..", "org.sequeless.filter.spi..")
            .and()
            .areTopLevelClasses()
            .and()
            .haveSimpleNameNotEndingWith("package-info")
            .should()
            .bePublic()
            .as("the published contract (api + spi) must be exported as public types");
}
