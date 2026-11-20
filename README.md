# mtr-fare-map
MTR fare map

## Requirements

- Java 21
- Apache Maven 3.8.7 or later

Check that Java and Maven are available:

```bash
java -version
mvn -version
```

## Run the application

```bash
mvn spring-boot:run -Pdev
```

The `dev` Maven profile enables OpenAPI and Swagger UI at
`http://localhost:8080/swagger-ui/index.html`, where you can inspect and execute
the documented endpoints interactively. Run without `-Pdev` to start the
application without Swagger UI.

## Build

Package the application as an executable JAR:

```bash
mvn clean package
```

The default build does not include Swagger UI. Do not enable `-Pdev` when
building the production JAR.

The JAR is created at `target/fare-0.0.1-SNAPSHOT.jar`. Run it with:

```bash
java -jar target/fare-0.0.1-SNAPSHOT.jar
```

## Test

Run the test suite with Maven:

```bash
mvn test
```