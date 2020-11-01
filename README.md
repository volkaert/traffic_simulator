# Traffic Simulator 

## Quick start

Start 3 services named A, B and C (respectively on ports 8091, 8092 and 9093):
```
java -Dspring.application.name=A -Dserver.port=8091 -jar traffic-simulator-1.0-SNAPSHOT.jar
java -Dspring.application.name=B -Dserver.port=8092 -jar traffic-simulator-1.0-SNAPSHOT.jar
java -Dspring.application.name=C -Dserver.port=8093 -jar traffic-simulator-1.0-SNAPSHOT.jar
```

To generate traffic between services, make a request of the form:
```
curl -X POST "http://<SERVICE_HOST>:<SERVICE_PORT></traffic-simulator/call?chain=<CHAIN>&pause=<PAUSE_IN_MILLIS_BEFORE_RETURNING>&status=<HTTP_STATUS_CODE_TO_RETURN>
```
where 
- `<SERVICE_SERVER>` is the host of the fist service of the chain of calls
- `<SERVICE_PORT>` is the port of the fist service of the chain of calls
- `<CHAIN>` is the chain of calls (call B which will call C which will call D...)
- `<PAUSE_IN_MILLIS_BEFORE_RETURNING>`. Duration of the pause to make (in millis) after calling the chain of services and 
before returning the HTTP status code. Useful to simulate high latency. Optional. Default is 0 ms.
- `<HTTP_STATUS_CODE_TO_RETURN>`. HTTP status code to return after calling the chain of services. Default is 200 (OK).

>See section "Generate traffic" below for details about the form of the `<CHAIN>` parameter (can contain optional parameters
>like the HTTP verb to use at each hop of the chain, duration of the pause at each hop of the chain, status code to return
> at each hop of the chain...).

>Random values can be generated for the HTTP verb, the duration of the pause, the HHTP status code to return... 
>See examples below.

Examples:
```
curl -X POST "http://localhost:8091/traffic-simulator/call?chain=B,C&pause=100&status=201"
curl -X POST "http://localhost:8091/traffic-simulator/call?chain=B,C"
```
>In the examples above, the first service called in the chain of calls is A because the request is sent  to `locahost:8091`.


Generate *single* traffic between services A, B, and C with *default* values (GET operation, pause=0ms, status code=200):
```
curl -X POST "http://localhost:8091/traffic-simulator/call?chain=B,C"
```

Generate *single* traffic between services A, B, and C with *explicit* HTTP verbs, pauses and status codes:
```
curl -X POST "http://localhost:8091/traffic-simulator/call?chain=(B;POST;300;201),(C;DELETE;150;204)&pause=100&status=200"
```

Generate *single* traffic between services A, B, and C with *random* HTTP verbs, pauses and status codes:
```
curl -X POST "http://localhost:8091/traffic-simulator/call?chain=(B;*;*;*),(C;*;*;*)&pause=*&status=*"
```

Generate *continuous* traffic between services A, B, and C with *default* values (GET operation, pause=0ms, status code=200):
```
curl -X POST "http://localhost:8091/traffic-simulator/start?chain=B,C"
```

Generate *continuous* traffic between services A, B, and C with *random* HTTP verbs, pauses and status codes:
```
curl -X POST "http://localhost:8091/traffic-simulator/start?chain=(B;*;*;*),(C;*;*;*)"
```

Stop *continuous* traffic generated from service A:
```
curl -X POST "http://localhost:8091/traffic-simulator/stop"
```

## Prerequisite

### Java 11

Java 11 must be installed.
Install the JDK version if you want to compile the services.
Install the JRE version if you just want to execute the services.

The AdoptOpenJDK binaries and instructions can be found here: https://adoptopenjdk.net/installation.html#

### Docker for optional components (Prometheus, Grafana, Jaeger...)

Instructions to install Docker can be found here: https://docs.docker.com/get-docker/


## Optional components

Optional components require Docker.

### Prometheus to collect and store metrics

In the `bin\prometheus.yml` file, change the address of the target services with your own IP(s) and ports 
(replace my 192.168.1.74 IP).

Then start Prometheus:
```
cd bin
./start-prometheus.sh
```

### Grafana to visualize metrics

Start Grafana:
```
cd bin
./start-grafana.sh
```

Then browse to `http://<YOUR_GRAFANA_SERVER>:3000` (example: `http:/localhost:3000`) 

Default login is `admin` and default password is `admin`.

Then, in Grafana, create a Prometheus DataSource (for example with the Prometheus URL `http://localhost:9090`).


### Jaeger to store and visualize distributed traces

Start Jaeger:
```
cd bin
./start-jaeger.sh
```

Then browse to `http://<YOUR_JAEGER_SERVER>:16686` (example: `http:/localhost:16686`) 


## Compile

```
./mvnw clean package
```

## Config to check or update before execution

Update the `config/application.properties` file with the locations of the various services.

## Execution

### Execution with mvnw (embedded Maven)

>Pay attention to the consistency of the ports used on the command line with those written in the `application.properties` file.

>The allowed service names are exclusively A, B, C, D, and E. Do NOT use other names !

For Service A: ```./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.application.name=A --server.port=8091"```

For Service B: ```./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.application.name=B --server.port=8092"```

For Service C: ```./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.application.name=C --server.port=8093"```

For Service D: ```./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.application.name=D --server.port=8094"```

For Service E: ```./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.application.name=E --server.port=8095"```

### Execution with java -jar

>Pay attention to the consistency of the ports used on the command line with those written in the `application.properties` file.

>The allowed service names are exclusively A, B, C, D, and E. Do NOT use other names !

For Service A: ```java -Dspring.application.name=A -Dserver.port=8091 -jar target/traffic-simulator-1.0-SNAPSHOT.jar```

For Service B: ```java -Dspring.application.name=B -Dserver.port=8092 -jar target/traffic-simulator-1.0-SNAPSHOT.jar```

For Service C: ```java -Dspring.application.name=C -Dserver.port=8093 -jar target/traffic-simulator-1.0-SNAPSHOT.jar```

For Service D: ```java -Dspring.application.name=D -Dserver.port=8094 -jar target/traffic-simulator-1.0-SNAPSHOT.jar```

For Service E: ```java -Dspring.application.name=E -Dserver.port=8095 -jar target/traffic-simulator-1.0-SNAPSHOT.jar```

### Execution with java -jar and env variables (useful for Kubernetes env)

>Pay attention to the consistency of the ports used on the command line with those written in the `application.properties` file.

>The allowed service names are exclusively A, B, C, D, and E. Do NOT use other names !

For Service A:
```
export SPRING_APPLICATION_NAME=A
export SERVER_PORT=8091
java -jar target/traffic-simulator-1.0-SNAPSHOT.jar
```

For Service B:
```
export SPRING_APPLICATION_NAME=B
export SERVER_PORT=8092
java -jar target/traffic-simulator-1.0-SNAPSHOT.jar
```

For Service C:
```
export SPRING_APPLICATION_NAME=C
export SERVER_PORT=8092
java -jar target/traffic-simulator-1.0-SNAPSHOT.jar
```

For Service D:
```
export SPRING_APPLICATION_NAME=D
export SERVER_PORT=8093
java -jar target/traffic-simulator-1.0-SNAPSHOT.jar
```

For Service E:
```
export SPRING_APPLICATION_NAME=E
export SERVER_PORT=8094
java -jar target/traffic-simulator-1.0-SNAPSHOT.jar
```

## Generate traffic

A traffic chain is of the form: `(<ServiceName1>;<HttpVerb1>;<PauseInMillis1>;<StatusCode1>),(<ServiceName2>;<HttpVerb2>;<PauseInMillis2>;<StatusCode2>),(<ServiceName3>;<HttpVerb3>;<PauseInMillis3>;<StatusCode3>),...`
where 
- `ServiceName` is the name of the service to call (either A or B or B or C or D)
- `HttpVerb` is the HTTP verb to use to call the service (either GET or POST or PUT or DELETE). Default value is GET.
- `PauseInMillis` is the pause made by the called service before returning. Default value is 0.
- `SatusCode` is the HTTP status code to be returned by the called service. Default is 200.

Example: `chain=(B;GET;100;200),(C;POST;300;201),(D;DELETE;150;204)`

You can omit some parameters if you want to use default values:
 - `chain=(B;GET;;),(C;POST;;),(D;DELETE;;)`
 - `chain=(B,GET),(C;POST),(D;DELETE)`

You can generate random parameters with the special character `*`:
 - `chain=(B;*;*;*),(C;*;*;*),(D;*;*;*)`
 - `chain=(*;*;*;*),(*;*;*;*),(*;*;*;*)`

Random values can be:
- Random `ServiceName` parameter is either A or B or C or D or E
- Random `HttpVerb` parameter is either GET or POST or PUT or DELETE
- Random `PauseInMillis` parameter is between 0 and 2000 millis
- Random `StatusCode` parameter is either 200 or 201 or 400 or 401 or 500

The `(` and `)` delimeters are optional. The following chains are equivalent: 
 - `chain=(B),(C),(D)`
 - `chain=B,C,D`

 
### Generate *single* traffic
```
curl -X POST "http://localhost:8091/traffic-simulator/call?chain=B"
curl -X POST "http://localhost:8091/traffic-simulator/call?chain=B,C,D,E"
curl -X GET "http://localhost:8091/traffic-simulator/call?chain=(B;GET;100;200),(C;POST;300;201)&pause=100&status=200"
curl -X GET "http://localhost:8091/traffic-simulator/call?chain=(B;GET;100;200),(C;POST;300;201),(D;DELETE;150;204)&pause=100&status=200"
curl -X POST "http://localhost:8091/traffic-simulator/call?chain=(*;*;*;*),(*;*;*;*),(*;*;*;*)&pause=*&status=*"
```

### Generate *continuous* traffic
```
curl -X POST "http://localhost:8091/traffic-simulator/start?chain=B,C,D,E"
curl -X POST "http://localhost:8091/traffic-simulator/start?chain=(B;*;*;*),(C;*;*;*)"
curl -X POST "http://localhost:8091/traffic-simulator/stop"
```


## Logs management

Logs are displayed in the console (it is good practice for Kubernetes env).

Logs usually use the INFO level.

There are logs with ERROR level when the status parameter in the query string of the HTTP request is an error HTTP status 
code (all the 4xx and 5xx HTTP status codes) or when a remote call throws an error.

Access logs to the embedded Tomcat are displayed in the console (see the `config/application.properties` file).

>If you don't want to send the Tomcat access logs to the console but to a file instead, a configuration example is  
>provided in the `config/application.properties` file.


## Metrics management

To see the metrics, browse to `http://<YOUR_GRAFANA_SERVER>:3000` (example: `http:/localhost:3000`).

Prometheus metrics are exposed on the `/actuator/prometheus` endpoint of the services.

If you want to change this endpoint, you must also change the `metrics_path` property in the `bin/prometheus.yml` file.

Services produce lots of standard Spring metrics. 
 
All the metrics produced by the services contain the tags `myapp_name` and `myapp_instance_id` 
(respectively filled with the value `spring.application.name` provided on the command line and the value 
`spring.application.instance_id` generated using a formula in the `config/application.properties` file).

The services produce custom metrics that start with the prefix `myapp_` (example: `myapp_calls_count_total`).

To visualize the metrics in Grafana, click on the Explore button in the left bar, then click on the dropdown list 
named Metrics (in the upper left), then select the "myapp" entry in the list to see the custom metrics (or select the
"system" entry in the list to see the system metrics or the "tomcat" entry to see Tomcat metrics...).

The custom metrics produced by the services are:
- `myapp_calls_count_total` which counts the number of calls received by the service (the metric contain the HTTP verb used to call the service)
- `myapp_error_http_status_count_total` which counts the number of error HTTP status returned by the service (the metric contain the HTTP status as tag)
- `myapp_success_http_status_count_total` which counts the number of success HTTP status returned by the service (the metric contain the HTTP status as tag)
- `myapp_pause_timer` which records the duration of the pause made by the service before returning (the metric contain the HTTP verb used to call the service)
- `myapp_processing_time_timer` which records the processing time taken by the service to call the chain and make the pause (the metric contain the HTTP verb used to call the service).

## Traces management

To see the traces, browse to `http://<YOUR_JAEGER_SERVER>:16686` (example: `http:/localhost:16686`).
Default login is `admin` and default password is `admin`.

Traces are managed by Spring Cloud Sleuth. Traces are exported using the Zipkin format on the port 9411.

Jaeger is configured to accept traces with the Zipkin format on the port 9411.

Traces are sent to Jaeger but are also displayed in the console, right after the log level (example: 
`INFO [C,a730b1ae23fe940a,41484569aa86851f,true]` where `a730b1ae23fe940a` is the TraceID, `41484569aa86851f` is the SpanID
and `true` is to say that the metrics have been exported to some collector).
