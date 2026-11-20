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

<<<<<<< HEAD
=======
Open `http://localhost:8080/` for the station search, route, and adult Octopus
fare UI.

>>>>>>> ac5c34e (Basic MTR route and fare web UI given start/end stations)
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
<<<<<<< HEAD
Routes minimize line changes first, then adjacent station stops among routes with
the same number of changes. This can produce a longer route to avoid a transfer;
travel time and walking time are not considered. Adjacent stations are connected
by their sequence in a line direction, and a transfer is possible where the same
station ID is shared by lines.
=======
Routes score each adjacent station stop as one point, each line transfer as
three points, and each walking connection as three points; ties prefer fewer
transfers, then fewer walking connections, then fewer stops. Walking time is not
considered. Adjacent stations are connected by their sequence in a line
direction, and a transfer is possible where the same station ID is shared by
lines. Central and Hong Kong stations also have a one-stop walking connection.

Search stations by a partial English or Chinese name, or station code. Use the
selected station's `stationId` with the route endpoint:

```text
GET ${SERVER_URL}/api/stations?query=central
```

The response contains station IDs, codes, and English and Chinese names. Search
is case-insensitive for English names and station codes; a missing or blank
query returns `400`. Keep station IDs as the identifiers for route and fare
requests rather than sending user-entered names directly.
>>>>>>> ac5c34e (Basic MTR route and fare web UI given start/end stations)

Set the API base URL:

```bash
export SERVER_URL=http://localhost:8080
```

<<<<<<< HEAD
Get a route with the fewest line changes by station ID:
=======
Get a lowest-cost route by station ID:
>>>>>>> ac5c34e (Basic MTR route and fare web UI given start/end stations)

```text
GET ${SERVER_URL}/api/route?start=1&dest=3
```

<<<<<<< HEAD
The response includes the ordered station list, stop count, and line legs. A
transfer station appears at the end of one leg and the start of the next. Unknown
station IDs and pairs with no connected route return `404`.
=======
The response includes the ordered station list, stop count, line legs, and fare
categories in HK cents when fare data is available. The adult Octopus fare is
available at `fare.octAdultCents`; if there is no matching fare row, `fare` is
`null`. A transfer station appears at the end of one leg and the start of the
next. Unknown station IDs and pairs with no connected route return `404`.
>>>>>>> ac5c34e (Basic MTR route and fare web UI given start/end stations)

Get all unique stations for a line, along with its direction-specific station
sequences (useful for lines with branches):

```text
GET ${SERVER_URL}/api/lines/AEL
```

Line codes are case-insensitive. An unknown line returns `404`.
<<<<<<< HEAD
=======

## Fare data

Fares are loaded at startup from `src/main/resources/data/mtr_lines_fares.csv`
into a compact in-memory table. The route response includes all nine fare
categories for an ordered pair when available. Fare fields end in `Cents` and
use integer HK cents (for example, `1060` means HK$10.60). Zero is a valid fare.

Fare IDs come directly from the fare CSV and are not inferred from station
names or route connectivity. The datasets differ: for example, route station ID
`44` has no fare row, while fare station ID `70` is absent from the route data.
There is no automatic mapping between them, so a valid route can have a `null`
fare.
>>>>>>> ac5c34e (Basic MTR route and fare web UI given start/end stations)
