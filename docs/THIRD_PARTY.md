# Third-party components

The bundled Windows libraries are OpenJFX JavaFX base, graphics, controls and media, version
21.0.12. JavaFX is distributed under GPL version 2 with the Classpath Exception, with
additional notices for included third-party components. The application contains no
Gluon Glisten UI dependency.

- Project and source: https://github.com/openjdk/jfx
- Exact matching sources: the `21.0.12` `-sources.jar` artifacts under
  https://repo.maven.apache.org/maven2/org/openjfx/ for `javafx-base`, `javafx-graphics`
  `javafx-controls` and `javafx-media`.
- OpenJFX licensing documents are included in `licenses/`.
- Maven and GluonFX are build tools, not bundled runtimes. Their versions are explicit in
  the build instructions and `pom.xml`. GluonFX: https://github.com/gluonhq/gluonfx-maven-plugin

The ZIP includes Windows JavaFX libraries only. Mobile builds use the native libraries
selected by the GluonFX toolchain; consult their accompanying licenses when distributing.

The four WAV sound effects were synthesized for this project. They contain no
recordings or samples from an external sound library.

