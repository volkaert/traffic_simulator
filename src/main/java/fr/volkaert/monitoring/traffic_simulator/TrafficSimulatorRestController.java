package fr.volkaert.monitoring.traffic_simulator;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping(value = "/traffic-simulator")
@Configuration
public class TrafficSimulatorRestController {

    private static final String DEFAULT_PAUSE = "0";  // in millis

    private static final Logger LOGGER = LoggerFactory.getLogger(TrafficSimulatorRestController.class);

    private boolean simulateTrafficEnabled = false;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${spring.application.name}")
    private String currentServiceName;

    @Value("${spring.application.instance_id}")
    private String currentServiceInstanceId;

    @Value("${serviceA.url}")
    private String serviceA_Url;

    @Value("${serviceB.url}")
    private String serviceB_Url;

    @Value("${serviceC.url}")
    private String serviceC_Url;

    @Value("${serviceD.url}")
    private String serviceD_Url;

    @Value("${serviceE.url}")
    private String serviceE_Url;

    private final List<String> serviceNames = List.of("A", "B", "C", "D", "E");
    private List<String> serviceUrls = null;

    private final List<String> httpVerbs = List.of("GET", "POST", "PUT", "DELETE");

    private final List<String> okHttpStatusCodes = List.of("200", "201");
    private final List<String> errorHttpStatusCodes = List.of("400", "401", "500");

    private String chainForContinuousTraffic = null;
    private String pauseForContinuousTraffic = null;

    @Autowired
    MeterRegistry meterRegistry;

    @PostConstruct
    public void init() {
        serviceUrls = List.of(serviceA_Url, serviceB_Url, serviceC_Url, serviceD_Url, serviceE_Url);
        LOGGER.info("Service {} initialized", currentServiceName);
        LOGGER.info("Service URLs are {}", serviceUrls);
    }

    // Example: curl -X GET http://localhost:8081/traffic-simulator/call?chain=(A;GET;100;200),(B;POST;300;201),(C;DELETE;150;204)&pause=100&status=200
    @GetMapping(value="/call", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> callUsingGET(@RequestParam(required = false) String chain,    /* ex: (A;GET;100;200),(B;POST;300;201),(C;DELETE;150;204) */
                                               @RequestParam(required = false) String pause,    /* in millis */
                                               @RequestParam(required = false) String status) {
        return call(chain, pause, status, "GET");
    }

    // Example: curl -X POST http://localhost:8081/traffic-simulator/call?chain=(A;GET;100;200),(B;POST;300;201),(C;DELETE;150;204)&pause=100&status=200
    @PostMapping(value="/call", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> callUsingPOST(@RequestParam(required = false) String chain,   /* ex: (A;GET;100;200),(B;POST;300;201),(C;DELETE;150;204) */
                                                @RequestParam(required = false) String pause,   /* in millis */
                                                @RequestParam(required = false) String status) {
        return call(chain, pause, status, "POST");
    }

    // Example: curl -X PUT http://localhost:8081/traffic-simulator/call?chain=(A;GET;100;200),(B;POST;300;201),(C;DELETE;150;204)&pause=100&status=200
    @PutMapping(value="/call", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> callUsingPUT( @RequestParam(required = false) String chain,   /* ex: (A;GET;100;200),(B;POST;300;201),(C;DELETE;150;204) */
                                                 @RequestParam(required = false) String pause,  /* in millis */
                                                 @RequestParam(required = false) String status) {
        return call(chain, pause, status, "PUT");
    }

    // Example: curl -X DELETE http://localhost:8081/traffic-simulator/call?chain=(A;GET;100;200),(B;POST;300;201),(C;DELETE;150;204)&pause=100&status=200
    @DeleteMapping(value="/call", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> callUsingDELETE(@RequestParam(required = false) String chain, /* ex: (A;GET;100;200),(B;POST;300;201),(C;DELETE;150;204) */
                                                  @RequestParam(required = false) String pause, /* in millis */
                                                  @RequestParam(required = false) String status) {
        return call(chain, pause, status, "DELETE");
    }

    private ResponseEntity<String> call( String chain,  /* ex: (A;GET;100;200),(B;POST;300;201),(C;DELETE;150;204) */
                                         String pause,  /* in millis */
                                         String status,
                                         String httpVerbUsed) {
        LOGGER.info("{} /call called with chain={} and pause={} and status={}", httpVerbUsed, chain, pause, status);
        LOGGER.info("logfmt: msg=\"{}\" service_name={} service_instance_id={} http_verb={} chain={} pause={} status={}",
                "service called", currentServiceName, currentServiceInstanceId, httpVerbUsed, chain, pause, status);

        Instant beginOfProcessingTime = Instant.now();

        // Generate a metric to count the number of calls received by this service
        Counter callsCounter = meterRegistry.counter("myapp_calls_count_total", "http_verb", httpVerbUsed);
        callsCounter.increment();

        // Handle default values for pause and status parameters
        if (StringUtils.isEmpty(pause)) pause = DEFAULT_PAUSE;
        if (StringUtils.isEmpty(status)) status = "200";

        // Handle random values for pause and status parameters
        if ("*".equals(chain))
            chain = serviceNames.get(getRandomNumberBetween(0, serviceNames.size()));
        if ("*".equals(pause))
            pause = Integer.toString(getRandomNumberBetween(0, 2000));
        if ("*".equals(status))
            status = getRandomHttpStatusCode();

        LOGGER.info("{} /call: default or random values replaced by chain={} and pause={} and status={}", httpVerbUsed, chain, pause, status);

        // chain is null or "" if the current service is the last in the chain
        if (! StringUtils.isEmpty(chain)) {
            // Split the chain of services to call in items
            String[] chainItems = chain.split(",");

            // Get the fist item the chain of services to call
            String firstChainItem = chainItems[0];

            // Remove leading '(' and trailing ')'
            if (firstChainItem.startsWith("("))
                firstChainItem = firstChainItem.substring(1);
            if (firstChainItem.endsWith(")"))
                firstChainItem = firstChainItem.substring(0, firstChainItem.length() - 1);

            // Get parameters for the next service to call
            String[] firstChainItemArgs = firstChainItem.split(";");

            // Get the name of the next service to call
            String serviceNameToCall = firstChainItemArgs[0].toUpperCase();
            if (StringUtils.isEmpty(serviceNameToCall))
                serviceNameToCall = "A";
            else if ("*".equals(serviceNameToCall))
                serviceNameToCall = serviceNames.get(getRandomNumberBetween(0, serviceNames.size()));

            // Get the URL of the next service to call
            int indexOfServiceNameToCall = serviceNames.indexOf(serviceNameToCall);
            String serviceUrlToCall = serviceUrls.get(indexOfServiceNameToCall);

            // Get the HTTP verb to use to call the next service
            String httpVerbToUseForTheCallee = firstChainItemArgs.length >= 2 ? firstChainItemArgs[1].toUpperCase() : "GET";
            if (StringUtils.isEmpty(httpVerbToUseForTheCallee))
                httpVerbToUseForTheCallee = "GET";
            else if ("*".equals(httpVerbToUseForTheCallee))
                httpVerbToUseForTheCallee = httpVerbs.get(getRandomNumberBetween(0, httpVerbs.size()));

            // Get the pause parameter to use to call the next service
            String pauseToUseForTheCallee = firstChainItemArgs.length >= 3 ? firstChainItemArgs[2] : DEFAULT_PAUSE;
            if (StringUtils.isEmpty(pauseToUseForTheCallee))
                pauseToUseForTheCallee = DEFAULT_PAUSE;
            else if ("*".equals(pauseToUseForTheCallee))
                pauseToUseForTheCallee = Integer.toString(getRandomNumberBetween(0, 2000));

            // Get the status parameter to use to call the next service
            String statusToUseForTheCallee = firstChainItemArgs.length >= 4 ? firstChainItemArgs[3] : "200";
            if (StringUtils.isEmpty(statusToUseForTheCallee))
                statusToUseForTheCallee = "200";
            else if ("*".equals(statusToUseForTheCallee))
                statusToUseForTheCallee = getRandomHttpStatusCode();

            // The chain parameter to use to call the next service is the input chain without its fist element
            List<String> chainToUseForTheCalleeAsList = new ArrayList<>();
            for (int i = 1; i < chainItems.length; i++) {   // start with 1 instead of O to remove the first item !
                chainToUseForTheCalleeAsList.add(chainItems[i]);
            }
            String chainToUseForTheCallee = StringUtils.collectionToCommaDelimitedString(chainToUseForTheCalleeAsList);

            String queryToUseForTheCallee = String.format("%s/traffic-simulator/call?chain=%s&pause=%s&status=%s",
                    serviceUrlToCall, chainToUseForTheCallee, pauseToUseForTheCallee, statusToUseForTheCallee);
            LOGGER.info("Calling {}", queryToUseForTheCallee);

            try {
                ResponseEntity<String> responseEntity = restTemplate.exchange(
                        queryToUseForTheCallee, HttpMethod.valueOf(httpVerbToUseForTheCallee), null, String.class);
                LOGGER.info("Call to {} returned successfully with the status code {}", queryToUseForTheCallee, responseEntity.getStatusCode());
            } catch (RestClientException e) {
                LOGGER.error("Call to {} returned the error {}", queryToUseForTheCallee, e.getMessage());
            }
        } // if (! StringUtils.isEmpty(chain))

        // Make a pause if required
        if (! (pause.isEmpty() || "0".equals(pause))) {
            LOGGER.debug("Pause {} millis", pause);
            pause(Long.parseLong(pause));

            // Generate a metric to record the duration of the pause (useful to simulate latency and trigger alerts on high latency)
            Timer pauseTimer = meterRegistry.timer("myapp_pause_timer", "http_verb", httpVerbUsed);
            pauseTimer.record(Duration.ofMillis(Long.parseLong(pause)));
        }

        // Return a response with the HTTP status provided as input parameter
        HttpStatus httpStatus = HttpStatus.valueOf(Integer.parseInt(status));
        String helloMessage = String.format("%s Hello from %s; returning status code %s", Instant.now(), getHostName(), httpStatus);
        if (httpStatus.isError()) {
            LOGGER.error(helloMessage);
            // Generate a metric to count the number of error HTTP status
            Counter errorHttpStatusCount = meterRegistry.counter("myapp_error_http_status_count_total", "http_status", status);
            errorHttpStatusCount.increment();
        }
        else {
            LOGGER.info(helloMessage);
            // Generate a metric to count the number of success HTTP status
            Counter successHttpStatusCount = meterRegistry.counter("myapp_success_http_status_count_total", "http_status", status);
            successHttpStatusCount.increment();
        }

        Instant endOfProcessingTime = Instant.now();
        Timer processingTimeTimer = meterRegistry.timer("myapp_processing_time_timer", "http_verb", httpVerbUsed);
        processingTimeTimer.record(Duration.between(beginOfProcessingTime, endOfProcessingTime));

        return new ResponseEntity(helloMessage + "\n", httpStatus);
    }

    @PostMapping(value="/{action}")
    public void manageContinuousTraffic(@PathVariable String action,
                                        @RequestParam(required = false) String chain,       /* ex: (A;GET;100;200),(B;POST;300;201),(C;DELETE;150;204) */
                                        @RequestParam(required = false) String pause) {     /* in millis */
        if ("start".equalsIgnoreCase(action) || "restart".equalsIgnoreCase(action) || "resume".equalsIgnoreCase(action)) {
            simulateTrafficEnabled = true;
            chainForContinuousTraffic = chain;
            pauseForContinuousTraffic = pause;
        }
        else if ("stop".equalsIgnoreCase(action) || "suspend".equalsIgnoreCase(action) || "pause".equalsIgnoreCase(action))
            simulateTrafficEnabled = false;
    }

    @Scheduled(fixedDelay = 10)
    public void generateContinuousTraffic() {
        if (simulateTrafficEnabled) {

            String chainToUseForTheCallee = null;
            if (! StringUtils.isEmpty(chainForContinuousTraffic)) {
                chainToUseForTheCallee = chainForContinuousTraffic;
            }
            else {
                List<String> chainItems = new ArrayList();
                int chainSize = getRandomNumberBetween(1, 6);
                for (int i = 0; i < chainSize; i++) {
                    String serviceNameToCall = serviceNames.get(getRandomNumberBetween(0, serviceNames.size()));
                    String httpVerbToUseForTheCallee = httpVerbs.get(getRandomNumberBetween(0, httpVerbs.size()));
                    String pauseToUseForTheCallee = Integer.toString(getRandomNumberBetween(0, 2000));
                    String statusToUseForTheCallee = getRandomHttpStatusCode();
                    String chainItem = String.format("(%s;%s;%s;%s)",
                            serviceNameToCall, httpVerbToUseForTheCallee, pauseToUseForTheCallee, statusToUseForTheCallee);
                    chainItems.add(chainItem);
                }
                chainToUseForTheCallee = StringUtils.collectionToCommaDelimitedString(chainItems);
            }

            String pauseToUseForTheCallee = null;
            if (! StringUtils.isEmpty(pauseForContinuousTraffic))
                pauseToUseForTheCallee = pauseForContinuousTraffic;
            else
                pauseToUseForTheCallee = "1000";

            call(chainToUseForTheCallee, pauseToUseForTheCallee, null, "POST");

        } // if (simulateTrafficEnabled)
    }

    private int getRandomNumberBetween(int min, int max) {
        return (int) ((Math.random() * (max - min)) + min);
    }

    private String getRandomHttpStatusCode() {
        boolean shouldReturnOk = getRandomNumberBetween(0, 10) <= 7;
        String httpStatusCode = shouldReturnOk ?
                okHttpStatusCodes.get(getRandomNumberBetween(0, okHttpStatusCodes.size())) :
                errorHttpStatusCodes.get(getRandomNumberBetween(0, errorHttpStatusCodes.size()));
        return httpStatusCode;
    }

    private void pause(long pauseInMillis) {
        try {
            Thread.sleep(pauseInMillis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return "unknown host";
        }
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }
}
