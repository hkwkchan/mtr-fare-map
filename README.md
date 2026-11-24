# mtr-fare-map
MTR fare map

After you find a route from start and destination stations, the website displays the route with all passing-by stations and next available train arrival time. A live demo hosting in AWS is at https://d1o9hg7ev4qh0q.cloudfront.net.

The real-time estimate is provided by the
[MTR real-time train information API](https://data.gov.hk/en-data/dataset/mtr-data2-nexttrain-data/resource/103472ed-01be-4dda-92c6-30530dee022f), which is updated every 10 seconds.
Route and fare data are sourced from the MTR open data CSVs:
[MTR Lines (except Light Rail) & Stations](https://data.gov.hk/en-data/dataset/mtr-data-routes-fares-barrier-free-facilities/resource/8daba4fe-b879-4a51-8962-27b4cffdc61c)
and
[MTR Lines (except Airport Express & Light Rail) Fares](https://data.gov.hk/en-data/dataset/mtr-data-routes-fares-barrier-free-facilities/resource/be9afb46-0c72-4e1c-8b15-07059d5541a7).

## Code structure

- [Root `pom.xml`](pom.xml) defines the parent Maven build for both modules.
- [`fare/`](fare/) contains the web API, website, and AWS Lambda handler.
  Its [`com.example.fare` package](fare/src/main/java/com/example/fare/)
  also holds the shared station search, routing, and fare logic
  (`DataService` and `FareTable`). Station and fare data and website files are
  in [`fare/src/main/resources/`](fare/src/main/resources/).
- [`mcp/`](mcp/) depends on `fare` and adds the MCP tool definitions and
  configuration in its [`com.example.fare` package](mcp/src/main/java/com/example/fare/).
  There is no separate common module; MCP reuses the shared code in `fare`.
- Each module has its own tests under `src/test/java`. The Lambda assembly
  descriptor is in [`fare/src/assembly/`](fare/src/assembly/).

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
mvn -pl fare spring-boot:run -Pdev
```

The `dev` Maven profile enables OpenAPI and Swagger UI at
`http://localhost:8080/swagger-ui/index.html`, where you can inspect and execute
the documented endpoints interactively. Run without `-Pdev` to start the
application without Swagger UI.

## MCP server

The separate MCP module exposes station search and route lookup as MCP tools
over Streamable HTTP. Build both modules, then enable the `mcp` Spring profile
when starting the standalone server:

```bash
mvn clean package
java -jar mcp/target/fare-mcp-1.0.0.jar --spring.profiles.active=mcp
```

The MCP endpoint is `http://localhost:8080/mcp`, which can be added to VS Code's `.vscode/mcp.json` (or configure an
equivalent Streamable HTTP connection in another MCP client for an AI tool):

```json
{
  "servers": {
    "mtr-fare-map": {
      "type": "http",
      "url": "http://localhost:8080/mcp"
    }
  }
}
```

Start the application and connect the client, then ask its AI assistant
something like "Tuen Mun to Kwun Tong" or "TM to KwunTong". The assistant can use:

- `search_stations(query)` to find station names or codes and their IDs.
- `get_route(start, dest)` to find a route using those IDs.

The assistant should ask for missing or ambiguous stations. Searches use
case-insensitive substring matching, not fuzzy matching. Routes minimize
line changes before stops; fares are in HK cents and can be unavailable.
Route stations are provided in each leg; transfer stations appear at the end of
one leg and the start of the next. A route from a station to itself has no legs.

HTTP requests under `/api` are logged with their method, path, status and
duration. Query parameters are not logged.

## Test

Run the test suite with Maven:

```bash
mvn test
```

To run only the Lambda integration test:

```bash
mvn -pl fare -Dtest=LambdaIntegrationTests test
```

## Build

Build both modules and package the application:

```bash
mvn clean package
```

The standalone fare JAR is created at `fare/target/fare-1.0.0-exec.jar`. Run it with:

```bash
java -jar fare/target/fare-1.0.0-exec.jar
```

The same build also creates `fare/target/fare-1.0.0-lambda.zip` for AWS
Lambda. The CloudFormation stack deploys it with the Java 21 runtime and
`com.example.fare.StreamLambdaHandler` handler. The ZIP contains application
classes at its root and runtime dependencies in `lib/`, without MCP code or
dependencies; keep using the executable JAR for local runs.

### Deploy the website and HTTP API

The [CloudFormation template](cloudformation.yaml) creates a private S3 bucket
and an HTTPS CloudFront distribution for the website, an HTTP API, and the
Java 21 Lambda function. CloudFront serves the static files from S3 and sends
`/api/*` requests to API Gateway on the same origin, so the frontend does not
need cross-origin requests. The HTTP API uses a `$default` route and stage, a
Lambda proxy integration with payload format 1.0, and permission for that API
to invoke the function. A CloudFormation custom resource copies the staged
`index.html` and `styles.css` into the website bucket.

The API stage has a best-effort throttle of 10 requests per second with a burst
of 30, shared across callers rather than per user. Excess requests can receive
HTTP 429. Adjust `DefaultRouteSettings` for your expected traffic.

Build the project with `mvn clean package`. Before deploying, upload the Lambda
ZIP and website files to an existing S3 deployment bucket in the stack's
Region. Set these values for your deployment:

```bash
export REGION=YOUR_AWS_REGION
export DEPLOYMENT_BUCKET=YOUR_EXISTING_DEPLOYMENT_BUCKET
export LAMBDA_VERSION=1.0.0
export ASSETS_VERSION=1
export LAMBDA_CODE_KEY="lambda/${LAMBDA_VERSION}/fare-${LAMBDA_VERSION}-lambda.zip"
```

Upload the artifacts to the deployment bucket:

```bash
aws s3 cp "fare/target/fare-${LAMBDA_VERSION}-lambda.zip" \
  "s3://${DEPLOYMENT_BUCKET}/${LAMBDA_CODE_KEY}" --region "${REGION}"
aws s3 cp fare/src/main/resources/static/index.html \
  "s3://${DEPLOYMENT_BUCKET}/site/index.html" --region "${REGION}"
aws s3 cp fare/src/main/resources/static/styles.css \
  "s3://${DEPLOYMENT_BUCKET}/site/styles.css" --region "${REGION}"
```

Then deploy or update the stack using AWS CLI credentials allowed to manage
CloudFormation, IAM roles, Lambda, S3, CloudFront and API Gateway:

```bash
aws cloudformation deploy \
  --stack-name mtr-fare-api \
  --template-file cloudformation.yaml \
  --capabilities CAPABILITY_IAM \
  --parameter-overrides \
    DeploymentBucket="${DEPLOYMENT_BUCKET}" \
    LambdaCodeKey="${LAMBDA_CODE_KEY}" \
    IndexHtmlKey=site/index.html \
    StylesCssKey=site/styles.css \
    AssetsVersion="${ASSETS_VERSION}" \
  --region "${REGION}"
```

Use a new `LAMBDA_VERSION` for each new Lambda ZIP so CloudFormation updates
the function. Change `ASSETS_VERSION` whenever you upload updated website
files so CloudFormation reruns the asset copy. CloudFront deployment can take
several minutes.

Find the bucket name, Lambda ARN and website URL in the
stack's **Outputs** tab or with the AWS CLI below:

```bash
aws cloudformation describe-stacks --stack-name mtr-fare-api --region "${REGION}" \
  --query "Stacks[0].Outputs" --output table
```

Open the `WebsiteUrl` output in your browser. You can also find the
website domain in the AWS Console under **CloudFront** > **Distributions**
