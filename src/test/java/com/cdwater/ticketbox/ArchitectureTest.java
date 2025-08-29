package com.cdwater.ticketbox;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.cdwater.ticketbox",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final List<String> FEATURES = List.of(
            "auth", "event", "performance", "seat", "order",
            "payment", "ticket", "waitingroom", "storage");

    @ArchTest
    static final ArchRule onlyWebAndConfigurationDependOnWeb = noClasses()
            .that().resideOutsideOfPackage("com.cdwater.ticketbox.*.web")
            .and().areNotAnnotatedWith(Configuration.class)
            .should().dependOnClassesThat().resideInAPackage("com.cdwater.ticketbox.*.web")
            .because("web holds the HTTP adapters; only Spring configuration may assemble it");

    @ArchTest
    static final ArchRule commonDoesNotDependOnFeatures = noClasses()
            .that().resideInAPackage("com.cdwater.ticketbox.common..")
            .should().dependOnClassesThat().resideInAnyPackage(featurePackages())
            .because("common is shared below every feature");

    @ArchTest
    static final ArchRule crossFeatureDependenciesTargetDomain = classes()
            .that().resideInAnyPackage(featurePackages())
            .should(new ArchCondition<>("depend on other features only through their domain package") {
                @Override
                public void check(JavaClass source, ConditionEvents events) {
                    String sourceFeature = featureOf(source.getPackageName());
                    for (Dependency dependency : source.getDirectDependenciesFromSelf()) {
                        JavaClass target = dependency.getTargetClass();
                        String targetFeature = featureOf(target.getPackageName());
                        if (targetFeature == null || targetFeature.equals(sourceFeature)) {
                            continue;
                        }
                        if (!target.getPackageName().startsWith(
                                "com.cdwater.ticketbox." + targetFeature + ".domain")) {
                            events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                        }
                    }
                }
            })
            .because("features may call each other, but only through a domain facade");

    private static String[] featurePackages() {
        return FEATURES.stream()
                .map(feature -> "com.cdwater.ticketbox." + feature + "..")
                .toArray(String[]::new);
    }

    private static String featureOf(String packageName) {
        String prefix = "com.cdwater.ticketbox.";
        if (!packageName.startsWith(prefix)) {
            return null;
        }
        String rest = packageName.substring(prefix.length());
        int dot = rest.indexOf('.');
        String feature = dot < 0 ? rest : rest.substring(0, dot);
        return FEATURES.contains(feature) ? feature : null;
    }
}
