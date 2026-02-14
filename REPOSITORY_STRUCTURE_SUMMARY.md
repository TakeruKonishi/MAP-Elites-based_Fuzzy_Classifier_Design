# Repository Structure Summary

## 1. Repository-wide structure

- Build system: Maven (`pom.xml`)
- Main source tree: `src/`
  - `src/cilabo/`: core implementation for fuzzy classifier design and GBML pipeline
    - `main/`: experiment entry points
    - `gbml/`: algorithm/problem/operators/objectives/solutions
    - `fuzzy/`: fuzzy rule/classifier/knowledge components
    - `data/`: dataset loading and pattern structures
  - `src/jfml/`: JFML-related classes
  - `src/org/`: jMetal-related classes and examples
  - `src/random/`: random generator utilities
  - `src/xml/`: XML utilities
- Test source tree: `srctest/` (JUnit tests)
- Runtime settings: `consts.properties` (overrides defaults in `src/cilabo/main/Consts.java`)
- Utility run script: `RUNJarFile.ps1` (batch-like repeated jar execution)

## 2. Entry point

- Maven JAR manifest main class is configured as:
  - `cilabo.main.impl.MAPElites.FGBML_MAPElites_Main`
- This class:
  - loads constants from `consts.properties`
  - parses 6 CLI arguments
  - loads train/test datasets
  - constructs and runs MAP-Elites-based Hybrid FGBML

## 3. Build / run

### Build

Typical Maven commands:

- `mvn install`
- `mvn package`

`maven-dependency-plugin` copies runtime dependencies under `target/dependency/` during `package` phase.

### Run

The main class expects 6 arguments:

1. `dataName`
2. `algorithmID`
3. `experimentID`
4. `parallelCores`
5. `trainFile`
6. `testFile`

Example command shape:

```bash
java -jar target/<jar-name>.jar <dataName> <algorithmID> <experimentID> <parallelCores> <trainFile> <testFile>
```

`RUNJarFile.ps1` contains a concrete repeated execution example for cross-validation-style runs.

## 4. Test execution

- Test sources are under `srctest/`.
- However, current `pom.xml` sets Surefire `skipTests=true` by default.

Therefore:

- Default (`mvn test`) will skip tests unless overridden.
- To force running tests, use:

```bash
mvn -DskipTests=false test
```

## 5. Where to modify when improving the method

- Top-level experiment wiring / settings:
  - `src/cilabo/main/impl/MAPElites/FGBML_MAPElites_Main.java`
  - `src/cilabo/main/impl/MAPElites/FGBML_MAPElites_CommandLineArgs.java`
  - `src/cilabo/main/Consts.java` + `consts.properties`
- MAP-Elites search behavior and archive/selection logic:
  - `src/cilabo/main/impl/MAPElites/HybridFGBMLwithMAPElitesV.java`
- Problem definition and objective assignment:
  - `src/cilabo/main/impl/MAPElites/PittsburghFGBML_MAPElites.java`
  - plus objective functions under `src/cilabo/gbml/objectivefunction/`
- Variation operators (crossover/mutation/learning flow):
  - `src/cilabo/gbml/component/variation/`
  - `src/cilabo/gbml/operator/crossover/`
  - `src/cilabo/gbml/operator/mutation/`
- Fuzzy knowledge/rule/classifier representation:
  - `src/cilabo/fuzzy/`

A practical strategy is to start from `FGBML_MAPElites_Main` (wiring) and then drill down into `HybridFGBMLwithMAPElitesV` (search dynamics), followed by `gbml/objectivefunction` and operators (optimization behavior).
