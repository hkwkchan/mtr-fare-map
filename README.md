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

The JAR is created at `target/fare-1.0.0.jar`. Run it with:

```bash
java -jar target/fare-1.0.0.jar
```

The same build also creates `target/fare-1.0.0-lambda.zip` for AWS
Lambda. Upload this ZIP as the function code, select a Java 21 runtime, and set
the handler to `com.example.fare.StreamLambdaHandler`. Configure API Gateway to
send Lambda proxy payloads in version 1.0 format. The ZIP contains application
classes at its root and runtime dependencies in `lib/`; keep using the
executable JAR for local runs.

### Deploy the website and HTTP API

The [CloudFormation template](cloudformation.yaml) creates a private S3 bucket
and an HTTPS CloudFront distribution for the website, plus an HTTP API for an
existing Lambda function. CloudFront serves the static files from S3 and sends
`/api/*` requests to API Gateway on the same origin, so the frontend does not
need cross-origin requests. The HTTP API uses a `$default` route and stage, a
Lambda proxy integration with payload format 1.0, and permission for that API
to invoke the function. The template does not create or update the Lambda
function or reuse an existing API.

The API stage has a best-effort throttle of 10 requests per second with a burst
of 30, shared across callers rather than per user. Excess requests can receive
HTTP 429. This is not a spending cap and does not limit CloudFront requests
for static files; adjust `DefaultRouteSettings` for your expected traffic.

First upload the Lambda ZIP and configure the function as described above.
Then deploy or update the stack in the same AWS account and Region as the
Lambda, using AWS CLI credentials allowed to manage S3, CloudFront and API
Gateway resources and the Lambda function's resource-based policy:

```bash
aws cloudformation deploy \
  --stack-name mtr-fare-api \
  --template-file cloudformation.yaml \
  --parameter-overrides LambdaFunctionArn=arn:aws:lambda:REGION:ACCOUNT_ID:function:FUNCTION_NAME \
  --region REGION
```

Replace the ARN and Region with your own values. You can also supply a
version- or alias-qualified function ARN; the API will invoke that qualifier.
CloudFront deployment can take several minutes. Find the bucket name and
website URL in the stack's **Outputs** tab or with:

```bash
aws cloudformation describe-stacks --stack-name mtr-fare-api --region REGION \
  --query "Stacks[0].Outputs" --output table
```

CloudFormation creates the bucket but does not upload the site. Replace
`WEBSITE_BUCKET_NAME` with the `WebsiteBucketName` output and upload both files:

```bash
aws s3 cp src/main/resources/static/index.html s3://WEBSITE_BUCKET_NAME/index.html \
  --content-type 'text/html; charset=utf-8' --region REGION
aws s3 cp src/main/resources/static/styles.css s3://WEBSITE_BUCKET_NAME/styles.css \
  --content-type 'text/css; charset=utf-8' --region REGION
```

Open the `WebsiteUrl` output in your browser. The distribution serves
`index.html` at `/` and forwards its API calls to the existing Lambda through
API Gateway. Both behaviors disable CloudFront caching, so re-upload the files
after editing them. Test the API through the website domain with:

```bash
curl 'https://YOUR_CLOUDFRONT_DOMAIN/api/stations?query=Central'
```

The `ApiUrl` output remains available for direct API access; use `WebsiteUrl`
to serve the frontend from S3 instead of Lambda. The website and API are
publicly accessible unless you configure authorization separately. Empty the
website bucket before deleting the stack, or CloudFormation cannot delete it.

## Test

Run the test suite with Maven:

```bash
mvn test
```