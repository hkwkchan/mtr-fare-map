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

## Station and route API

The station data is loaded from `src/main/resources/data/mtr_lines_and_stations.csv`.
Routes minimize line changes first, then adjacent station stops among routes with
the same number of changes. This can produce a longer route to avoid a transfer;
travel time and walking time are not considered. Adjacent stations are connected
by their sequence in a line direction, and a transfer is possible where the same
station ID is shared by lines.

Set the API base URL:

```bash
export SERVER_URL=http://localhost:8080
```

Get a route with the fewest line changes by station ID:

```text
GET ${SERVER_URL}/api/route?start=1&dest=3
```

The response includes the ordered station list, stop count, and line legs. A
transfer station appears at the end of one leg and the start of the next. Unknown
station IDs and pairs with no connected route return `404`.

Get all unique stations for a line, along with its direction-specific station
sequences (useful for lines with branches):

```text
GET ${SERVER_URL}/api/lines/AEL
```

Line codes are case-insensitive. An unknown line returns `404`.
