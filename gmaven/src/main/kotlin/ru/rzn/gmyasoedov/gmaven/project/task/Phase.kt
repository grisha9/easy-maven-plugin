package ru.rzn.gmyasoedov.gmaven.project.task

enum class Phase(val phaseName:String, val lifecycle: Lifecycle) {
    PRE_CLEAN("pre-clean", Lifecycle.CLEAN),
    CLEAN("clean", Lifecycle.CLEAN),
    POST_CLEAN("post-clean", Lifecycle.CLEAN),

    VALIDATE("validate", Lifecycle.DEFAULT),
    INITIALIZE("initialize", Lifecycle.DEFAULT),
    GENERATE_SOURCES("generate-sources", Lifecycle.DEFAULT),
    PROCESS_SOURCES("process-sources", Lifecycle.DEFAULT),
    GENERATE_RESOURCES("generate-resources", Lifecycle.DEFAULT),
    PROCESS_RESOURCES("process-resources", Lifecycle.DEFAULT),
    COMPILE("compile", Lifecycle.DEFAULT),
    PROCESS_CLASSES("process-classes", Lifecycle.DEFAULT),
    GENERATE_TEST_SOURCES("generate-test-sources", Lifecycle.DEFAULT),
    PROCESS_TEST_SOURCES("process-test-sources", Lifecycle.DEFAULT),
    GENERATE_TEST_RESOURCES("generate-test-resources", Lifecycle.DEFAULT),
    PROCESS_TEST_RESOURCES("process-test-resources", Lifecycle.DEFAULT),
    TEST_COMPILE("test-compile", Lifecycle.DEFAULT),
    PROCESS_TEST_CLASSES("process-test-classes", Lifecycle.DEFAULT),
    TEST("test", Lifecycle.DEFAULT),
    PREPARE_PACKAGE("prepare-package", Lifecycle.DEFAULT),
    PACKAGE("package", Lifecycle.DEFAULT),
    PRE_INTEGRATION_TEST("pre-integration-test", Lifecycle.DEFAULT),
    INTEGRATION_TEST("integration-test", Lifecycle.DEFAULT),
    POST_INTEGRATION_TEST("post-integration-test", Lifecycle.DEFAULT),
    VERIFY("verify", Lifecycle.DEFAULT),
    INSTALL("install", Lifecycle.DEFAULT),
    DEPLOY("deploy", Lifecycle.DEFAULT),

    PRE_SITE("pre-site", Lifecycle.SITE),
    SITE("site", Lifecycle.SITE),
    POST_SITE("post-site", Lifecycle.SITE),
    SITE_DEPLOY("site-deploy", Lifecycle.SITE);

    companion object {
        fun find(taskName: String) = entries.find { it.phaseName == taskName }
    }
}

enum class Phase4(val phaseName: String, val lifecycle: Lifecycle) {
    BEFORE_CLEAN("before:clean", Lifecycle.CLEAN),
    CLEAN("clean", Lifecycle.CLEAN),
    AFTER_CLEAN("after:clean", Lifecycle.CLEAN),

    //before:all, before:initialize, before:validate, validate, after:validate, initialize, after:initialize,
    BEFORE_ALL("before:all", Lifecycle.DEFAULT),
    BEFORE_INITIALIZE("before:initialize", Lifecycle.DEFAULT),
    BEFORE_VALIDATE("before:validate", Lifecycle.DEFAULT),
    VALIDATE("validate", Lifecycle.DEFAULT),
    AFTER_VALIDATE("after:validate", Lifecycle.DEFAULT),
    INITIALIZE("initialize", Lifecycle.DEFAULT),
    AFTER_INITIALIZE("after:initialize", Lifecycle.DEFAULT),

    //before:build, before:sources, sources, after:sources, before:resources, resources, after:resources, before:compile, compile, after:compile,
    BEFORE_BUILD("before:build", Lifecycle.DEFAULT),
    BEFORE_SOURCES("before:sources", Lifecycle.DEFAULT),
    SOURCES("sources", Lifecycle.DEFAULT),
    AFTER_SOURCES("after:sources", Lifecycle.DEFAULT),
    BEFORE_RESOURCES("before:resources", Lifecycle.DEFAULT),
    RESOURCES("resources", Lifecycle.DEFAULT),
    AFTER_RESOURCES("after:resources", Lifecycle.DEFAULT),
    BEFORE_COMPILE("before:compile", Lifecycle.DEFAULT),
    COMPILE("compile", Lifecycle.DEFAULT),
    AFTER_COMPILE("after:compile", Lifecycle.DEFAULT),

    //before:ready, ready, after:ready, before:test-sources, test-sources, after:test-sources, before:test-resources, test-resources, after:test-resources
    BEFORE_READY("before:ready", Lifecycle.DEFAULT),
    READY("ready", Lifecycle.DEFAULT),
    AFTER_READY("after:ready", Lifecycle.DEFAULT),
    BEFORE_TEST_SOURCES("before:test-sources", Lifecycle.DEFAULT),
    TEST_SOURCES("test-sources", Lifecycle.DEFAULT),
    AFTER_TEST_SOURCES("after:test-sources", Lifecycle.DEFAULT),
    BEFORE_TEST_RESOURCES("before:test-resources", Lifecycle.DEFAULT),
    TEST_RESOURCES("test-resources", Lifecycle.DEFAULT),
    AFTER_TEST_RESOURCES("after:test-resources", Lifecycle.DEFAULT),

    //before:test-compile, test-compile, after:test-compile, before:test, test, after:test,
    BEFORE_TEST_COMPILE("before:test-compile", Lifecycle.DEFAULT),
    TEST_COMPILE("test-compile", Lifecycle.DEFAULT),
    AFTER_TEST_COMPILE("after:test-compile", Lifecycle.DEFAULT),
    BEFORE_TEST("before:test", Lifecycle.DEFAULT),
    TEST("test", Lifecycle.DEFAULT),
    AFTER_TEST("after:test", Lifecycle.DEFAULT),

    //before:unit-test, unit-test, after:unit-test, before:package, package, after:package, build, after:build,
    BEFORE_UNIT_TEST("before:unit-test", Lifecycle.DEFAULT),
    UNIT_TEST("unit-test", Lifecycle.DEFAULT),
    AFTER_UNIT_TEST("after:unit-test", Lifecycle.DEFAULT),
    BEFORE_PACKAGE("before:package", Lifecycle.DEFAULT),
    PACKAGE("package", Lifecycle.DEFAULT),
    AFTER_PACKAGE("after:package", Lifecycle.DEFAULT),
    BUILD("build", Lifecycle.DEFAULT),
    AFTER_BUILD("after:build", Lifecycle.DEFAULT),

    //before:verify,before:integration-test, integration-test, after:integration-test, verify, after:verify, before:install, install, after:install,
    BEFORE_VERIFY("before:verify", Lifecycle.DEFAULT),
    BEFORE_INTEGRATION_TEST("before:integration-test", Lifecycle.DEFAULT),
    INTEGRATION_TEST("integration-test", Lifecycle.DEFAULT),
    AFTER_INTEGRATION_TEST("after:integration-test", Lifecycle.DEFAULT),
    VERIFY("verify", Lifecycle.DEFAULT),
    AFTER_VERIFY("after:verify", Lifecycle.DEFAULT),
    BEFORE_INSTALL("before:install", Lifecycle.DEFAULT),
    INSTALL("install", Lifecycle.DEFAULT),
    AFTER_INSTALL("after:install", Lifecycle.DEFAULT),

    //before:deploy, deploy, after:deploy, all, after:all,
    BEFORE_DEPLOY("before:deploy", Lifecycle.DEFAULT),
    DEPLOY("deploy", Lifecycle.DEFAULT),
    AFTER_DEPLOY("after:deploy", Lifecycle.DEFAULT),
    ALL("all", Lifecycle.DEFAULT),
    AFTER_ALL("after:all", Lifecycle.DEFAULT),

    PRE_SITE("before:site", Lifecycle.SITE),
    SITE("site", Lifecycle.SITE),
    POST_SITE("after:site", Lifecycle.SITE),
    BEFORE_SITE_DEPLOY("before:site-deploy", Lifecycle.SITE),
    SITE_DEPLOY("site-deploy", Lifecycle.SITE),
    AFTER_SITE_DEPLOY("after:site-deploy", Lifecycle.SITE);

    companion object {
        fun find(taskName: String) = Phase4.entries.find { it.phaseName == taskName }
    }
}