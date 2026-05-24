package com.example.photomanagement.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

/**
 * Enforces the package-by-feature + simple 3-layer architecture.
 *
 * <p>If you find yourself wanting to disable a rule, prefer adding a new feature package or moving
 * code instead. The rules exist so contributors (and Claude Code) do not have to memorise the
 * conventions.
 *
 * <p>{@code allowEmptyShould(true)} is set on rules that target classes which may not yet exist
 * (e.g., no {@code *Service} until M2). Once those classes exist the rule starts checking them.
 */
@AnalyzeClasses(
    packages = "com.example.photomanagement",
    importOptions = {ImportOption.DoNotIncludeTests.class})
class ArchitectureTest {

  @ArchTest
  static final ArchRule controllersDoNotDependOnMappers =
      noClasses()
          .that()
          .resideInAPackage("..controller..")
          .or()
          .haveSimpleNameEndingWith("Controller")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..mapper..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule mappersDoNotDependOnControllersOrServices =
      noClasses()
          .that()
          .resideInAPackage("..mapper..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..controller..", "..service..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule restControllersAreNamedController =
      classes()
          .that()
          .areAnnotatedWith(RestController.class)
          .should()
          .haveSimpleNameEndingWith("Controller")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule servicesAreNamedService =
      classes()
          .that()
          .areAnnotatedWith(Service.class)
          .should()
          .haveSimpleNameEndingWith("Service")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule myBatisMappersAreNamedMapper =
      classes()
          .that()
          .areAnnotatedWith(Mapper.class)
          .should()
          .haveSimpleNameEndingWith("Mapper")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule noFeatureCycles =
      SlicesRuleDefinition.slices()
          .matching("com.example.photomanagement.(*)..")
          .should()
          .beFreeOfCycles();

  @ArchTest
  static final ArchRule commonDoesNotDependOnFeatures =
      noClasses()
          .that()
          .resideInAPackage("com.example.photomanagement.common..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.example.photomanagement.photo..",
              "com.example.photomanagement.album..",
              "com.example.photomanagement.auth..",
              "com.example.photomanagement.user..",
              "com.example.photomanagement.health..")
          .allowEmptyShould(true);
}
