Easy Maven. IntelliJ Plugin [page](https://plugins.jetbrains.com/plugin/22370-easy-maven)
==================

#### (Old name: GMaven)

Easy Maven is lightweight Maven plugin for IDEA that gets the project model through maven task execution.  
Easy Maven is a complete replacement for the bundled Maven plugin.
It provides the most accurate resolution of project dependencies.
Easy Maven plugin runs on top of pure Maven, without any hidden logic.
The philosophy of Easy Maven plugin: "everything is a Maven task".
Any interaction with Maven: import project, test execution, dependency analysis, debugging - is simply running the
appropriate Maven plugin task with the required arguments.
The result is original build tool behavior and a simple implementation.

### Maven Plugin for IntelliJ IDEA [GitHub-Wiki](https://github.com/grisha9/gmaven-plugin/wiki)

The plugin adds support for the Maven for Java language projects:

- Original Maven behavior for importing project model into IDEA
- Run Task & Debug test via Maven
- Dependency analyzer
- Maven 4 support
- Groovy support
- Kotlin JVM support

![Screenshot](.github/readme-logo1.png)  
![Screenshot](.github/run-test.gif)

#### Articles about Easy Maven

- [dev.to](https://dev.to/grisha9/my-intellij-idea-plugin-for-maven-support-gmaven-cn9);
- [habr1.com](https://habr.com/ru/articles/753828/) (Russian);
- [habr2.com](https://habr.com/ru/articles/882778/) (Russian);
- [habr3.com](https://habr.com/ru/articles/969386/) (Russian);

### Prerequisites

1. IntelliJ IDEA 2021.3+
2. Maven 3.3.1+
3. Access to [Maven Central](https://mvnrepository.com/artifact/io.github.grisha9/maven-model-reader-plugin)
   for [model-reader-plugin](https://github.com/grisha9/maven-model-reader)

### Issues

If you found a bug, please report it on https://github.com/grisha9/easy-maven-plugin/issues

Wiki about issues: https://github.com/grisha9/easy-maven-plugin/wiki/Issues



