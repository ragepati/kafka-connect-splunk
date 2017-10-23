package com.splunk.hecclient.examples;

import com.splunk.hecclient.*;
import org.apache.commons.cli.*;
import org.apache.http.impl.client.CloseableHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CountDownLatch;

/**
 * Created by kchen on 10/17/17.
 */

public class HecPerf {
    private static Logger log = LoggerFactory.getLogger(HecPerf.class);
    private static String uriArg = "uris";
    private static String tokenArg = "token";
    private static String concurrencyArg = "concurrency";
    private static String pollerThreadArg = "ack-poller-threads";
    private static String pollIntervalArg = "ack-poll-interval";
    private static String eventTimoutArg = "event-batch-timeout";
    private static String iterationArg = "iterations";
    private static String connectionArg = "http-connection-per-indexer";
    private static String clientPoolArg = "client-pool-size";
    private static String verificationArg = "disable-cert-verification";
    private static String keepAliveArg = "keep-alive";

    public static void main(String[] args) throws Exception {
        HecPerfConfig config;
        try {
            config = createConfigFrom(args);
        } catch (HecClientException ex) {
            return;
        }

        // shared http client pool
        List<CloseableHttpClient> httpClients = new ArrayList<>();
        for (int i = 0; i < config.getClientPoolSize(); i++) {
            CloseableHttpClient httpClient = HecClient.createHttpClient(config.getHecClientConfig());
            httpClients.add(httpClient);
        }
        CloseableHttpClient pollerHttpClient = HecClient.createHttpClient(config.getHecClientConfig());
        Poller poller = HecWithAck.createPoller(config.getHecClientConfig(), pollerHttpClient, new PrintIt());

        int iterationsPerThread = config.getIterations() / config.getConcurrency();
        List<Thread> threads = new ArrayList<>();

        long start = System.currentTimeMillis();
        CountDownLatch countdown = new CountDownLatch(config.getConcurrency());
        for (int i = 0; i < config.getConcurrency(); i++) {
            final int id = i;
            Runnable r = () -> {
                perf(config.getHecClientConfig(), httpClients.get(id % httpClients.size()), poller, iterationsPerThread);
                countdown.countDown();
            };
            Thread thr = new Thread(r, "perf-thread-" + id);
            thr.start();
            threads.add(thr);
        }

        countdown.await();
        log.info("Took {} milliseconds to send {} events", System.currentTimeMillis() - start, config.getIterations() * 100);
        for (Thread th: threads) {
            th.join();
        }

        for (CloseableHttpClient client: httpClients) {
            client.close();
        }

        log.info("done");
    }

    private static void perf(HecClientConfig config, CloseableHttpClient httpClient, Poller poller, int iteration) {
        log.info("handle {} iterations", iteration);
        Hec hec = new HecWithAck(config, httpClient, poller);
        for (int i = 0; i < iteration; i++) {
            EventBatch batch = createEventBatch();
            hec.send(batch);
        }
        hec.close();
    }

    private static EventBatch createEventBatch() {
        String data = "127.0.0.1 - - [29/Apr/2017:17:52:57.885 -0700] \"GET /zh-CN/static/@1FFB5B3691CDDD837FB53E2D652D5DD69058B047CE286DD4DB8D00D952746A3E/build/css/bootstrap-enterprise.css HTTP/1.1\" 200 132601 \"http://        localhost:8000/zh-CN/account/login?return_to=%2Fzh-CN%2F\" \"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/57.0.2987.133 Safari/537.36\" - 59053569e210669c110 8ms";
        EventBatch batch = new JsonEventBatch();
        for (int j = 0; j < 100; j++) {
            Event evt = new JsonEvent(data, null);
            batch.add(evt);
        }
        return batch;
    }

    private static HecPerfConfig createConfigFrom(String[] args) throws Exception {
        Option uriOption = Option.builder()
                .argName(uriArg)
                .longOpt(uriArg)
                .required(true)
                .hasArg(true)
                .desc("hec uris, separated by comma. For example: https://127.0.0.1:8088")
                .build();
        Option tokenOption = Option.builder()
                .argName(tokenArg)
                .longOpt(tokenArg)
                .required(true)
                .hasArg(true)
                .desc("hec token")
                .build();
        Option concurrency = Option.builder()
                .argName(concurrencyArg)
                .longOpt(concurrencyArg)
                .type(PatternOptionBuilder.NUMBER_VALUE)
                .hasArg(true)
                .desc("Number of concurrent HEC posters")
                .build();
        Option ackPollerThreads = Option.builder()
                .argName(pollerThreadArg)
                .longOpt(pollerThreadArg)
                .type(PatternOptionBuilder.NUMBER_VALUE)
                .hasArg(true)
                .desc("number of ACK poller threads")
                .build();
        Option ackPollInterval = Option.builder()
                .argName(pollIntervalArg)
                .longOpt(pollIntervalArg)
                .type(PatternOptionBuilder.NUMBER_VALUE)
                .hasArg(true)
                .desc("ACK poll interval in seconds")
                .build();
        Option eventBatchTimeout = Option.builder()
                .argName(eventTimoutArg)
                .longOpt(eventTimoutArg)
                .type(PatternOptionBuilder.NUMBER_VALUE)
                .hasArg(true)
                .desc("Event batch timeout in seconds")
                .build();
        Option totalIterations = Option.builder()
                .argName(iterationArg)
                .longOpt(iterationArg)
                .type(PatternOptionBuilder.NUMBER_VALUE)
                .hasArg(true)
                .desc("Number of iterations to execute")
                .build();
        Option clientPool = Option.builder()
                .argName(clientPoolArg)
                .longOpt(clientPoolArg)
                .type(PatternOptionBuilder.NUMBER_VALUE)
                .hasArg(true)
                .desc("Number of clients to share among http post")
                .build();
        Option connectionPerIndexer = Option.builder()
                .argName(connectionArg)
                .longOpt(connectionArg)
                .type(PatternOptionBuilder.NUMBER_VALUE)
                .hasArg(true)
                .desc("Max HTTP connection per indexer")
                .build();
        Option disableCertVerification = Option.builder()
                .argName(verificationArg)
                .longOpt(verificationArg)
                .desc("Disable SSL cert verification")
                .build();
        Option keepAlive = Option.builder()
                .argName(keepAliveArg)
                .longOpt(keepAliveArg)
                .desc("HTTP keepalive")
                .build();
        Option helpOption = Option.builder()
                .argName("h")
                .longOpt("help")
                .desc("help usage")
                .build();

        Options options = new Options();
        options.addOption(uriOption);
        options.addOption(tokenOption);

        // optional options
        options.addOption(concurrency);
        options.addOption(totalIterations);
        options.addOption(ackPollerThreads);
        options.addOption(ackPollInterval);
        options.addOption(eventBatchTimeout);
        options.addOption(connectionPerIndexer);
        options.addOption(clientPool);
        options.addOption(disableCertVerification);
        options.addOption(keepAlive);
        options.addOption(helpOption);

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd;
        try {
            cmd = parser.parse(options, args);
        } catch (Exception ex) {
            new HelpFormatter().printHelp("java -Xmx6G -Xms2G -cp hecclient.jar com.splunk.hecclient.examples.HecPerf [options]", options);
            throw new HecClientException("usage error");
        }

        String uris = cmd.getOptionValue(uriArg);
        String token = cmd.getOptionValue(tokenArg);

        HecClientConfig config = new HecClientConfig(Arrays.asList(uris.split(",")), token);

        if (cmd.hasOption(pollerThreadArg)) {
            config.setAckPollerThreads((int) (long) cmd.getParsedOptionValue(pollerThreadArg));
        } else {
            config.setAckPollerThreads(4);
        }

        if (cmd.hasOption(pollIntervalArg)) {
            config.setAckPollInterval((int) (long) cmd.getParsedOptionValue(pollIntervalArg));
        } else {
            config.setAckPollInterval(10);
        }

        if (cmd.hasOption(eventTimoutArg)) {
            config.setEventBatchTimeout((int) (long) cmd.getParsedOptionValue(eventTimoutArg));
        } else {
            config.setEventBatchTimeout(60);
        }

        if (cmd.hasOption(connectionArg)) {
            config.setMaxHttpConnectionPerIndexer((int) (long) cmd.getParsedOptionValue(connectionArg));
        } else {
            config.setMaxHttpConnectionPerIndexer(4);
        }

        int iterations = 1000000;
        if (cmd.hasOption(iterationArg)) {
            iterations = (int) (long) cmd.getParsedOptionValue(iterationArg);
        }

        int concurrent = 1;
        if (cmd.hasOption(concurrencyArg)) {
            concurrent = (int) (long) cmd.getParsedOptionValue(concurrencyArg);
        }

        int clientPoolSize = 2;
        if (cmd.hasOption(clientPoolArg)) {
            clientPoolSize = (int) (long) cmd.getParsedOptionValue(clientPoolArg);
        }

        if (cmd.hasOption(verificationArg)) {
            config.setDisableSSLCertVerification(true);
        } else {
            config.setDisableSSLCertVerification(false);
        }

        if (cmd.hasOption(keepAliveArg)) {
            config.setHttpKeepAlive(true);
        } else {
            config.setHttpKeepAlive(false);
        }

        return new HecPerfConfig(config, concurrent, clientPoolSize, iterations);
    }
}