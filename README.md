# ai-platform-core

## Common Maven Commands

| Command | Purpose |
|---------|---------|
| `./mvnw clean` | Remove target/ build output |
| `./mvnw compile` | Compile source code |
| `./mvnw test` | Run all tests |
| `./mvnw package` | Build JAR (library JAR) |
| `./mvnw install` | Install to local Maven repo (~/.m2) |
| `./mvnw dependency:tree` | Show dependency tree |
| `./mvnw help:effective-pom` | Show resolved POM |

- Use `-DskipTests` to skip tests (e.g., `./mvnw package -DskipTests`)
- Use `-B` for batch mode in CI (e.g., `./mvnw test -B`)
- Use `-s settings.xml` to specify custom Maven settings
