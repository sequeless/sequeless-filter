package org.sequeless.filter.sql;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Mechanically enforced hexagonal boundaries for {@code filter-sql-adapter}, including its
 * cross-module relationship with {@code filter-core}.
 */
@AnalyzeClasses(
        packages = "org.sequeless.filter",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class BoundaryRulesTest {

    @ArchTest
    static final ArchRule api_and_spi_types_are_public = classes()
            .that()
            .resideInAnyPackage("org.sequeless.filter.sql.api..", "org.sequeless.filter.sql.spi..")
            .and()
            .areTopLevelClasses()
            .and()
            .haveSimpleNameNotEndingWith("package-info")
            .should()
            .bePublic()
            .as("the published contract (sql.api + sql.spi) must be exported as public types");

    @ArchTest
    static final ArchRule sql_adapter_does_not_reach_filter_core_internals = noClasses()
            .that()
            .resideInAnyPackage("org.sequeless.filter.sql..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.sequeless.filter.internal..", "org.sequeless.filter.spi..")
            .as("filter-sql-adapter's main scope may only depend on filter-core's api package");

    @ArchTest
    static final ArchRule filter_core_does_not_depend_on_sql_adapter = noClasses()
            .that()
            .resideInAnyPackage("org.sequeless.filter..")
            .and()
            .resideOutsideOfPackage("org.sequeless.filter.sql..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.sequeless.filter.sql..")
            .as("filter-core must not depend on filter-sql-adapter (documents the intended "
                    + "dependency direction; Maven's reactor build order already rejects this cycle)");
}
