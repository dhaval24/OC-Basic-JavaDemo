package io.opencensus.statsandtraces.quickstart;

import io.opencensus.common.Scope;
import io.opencensus.exporter.stats.prometheus.PrometheusStatsCollector;
import io.opencensus.exporter.trace.zipkin.ZipkinTraceExporter;
import io.opencensus.stats.Aggregation;
import io.opencensus.stats.BucketBoundaries;
import io.opencensus.stats.Measure;
import io.opencensus.stats.Stats;
import io.opencensus.stats.StatsRecorder;
import io.opencensus.stats.View;
import io.opencensus.stats.ViewManager;
import io.opencensus.tags.TagContext;
import io.opencensus.tags.TagKey;
import io.opencensus.tags.TagValue;
import io.opencensus.tags.Tagger;
import io.opencensus.tags.Tags;
import io.opencensus.trace.AttributeValue;
import io.opencensus.trace.Span;
import io.opencensus.trace.Status;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.config.TraceConfig;
import io.opencensus.trace.samplers.Samplers;
import io.prometheus.client.exporter.HTTPServer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Repl {

    // The latency in milliseconds
    private static final Measure.MeasureDouble M_LATENCY_MS = Measure.MeasureDouble.create("repl/latency", "The latency in milliseconds per REPL loop", "ms");

    // Counts the number of lines read in from standard input.
    private static final Measure.MeasureLong M_LINES_IN = Measure.MeasureLong.create("repl/lines_in", "The number of lines read in", "1");

    // Counts the number of non EOF(end-of-file) errors.
    private static final Measure.MeasureLong M_ERRORS = Measure.MeasureLong.create("repl/errors", "The number of errors encountered", "1");

    // Counts/groups the lengths of lines read in.
    private static final Measure.MeasureLong M_LINE_LENGTHS = Measure.MeasureLong.create("repl/line_lengths", "The distribution of line lengths", "By");

    // The tag "method"
    private static final TagKey KEY_METHOD = TagKey.create("method");

    private static final Tagger tagger = Tags.getTagger();
    private static final StatsRecorder statsRecorder = Stats.getStatsRecorder();

    private static final Tracer tracer = Tracing.getTracer();

    private static final ViewManager viewManager = Stats.getViewManager();

    static {
        try {
            Files.deleteIfExists(Paths.get("stats.txt"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String ...args) {

        try {
            registerAllViews();
            setupOpenCensusAndZipkinExporter();
            setupOpenCensusAndPrometheusExporter();

            // Default metric writing interval in file (To understand metric structure)
            int metricsPushInterval = 30;

            // Get metric write interval from command line
            if (args.length > 0 && args[0] != null) {
                metricsPushInterval = Integer.parseInt(args[0]);
            }
            Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    printStats();
                }
            },0, metricsPushInterval, TimeUnit.SECONDS);

        } catch (Exception e) {
            System.err.println("Failed to create and register OpenCensus Zipkin Trace exporter and OC Prometheus Exporter "+ e);
            return;
        }
        
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            try {
                readEvaluateProcessLine(stdin);
            } catch (IOException e) {
                recordTaggedStat(KEY_METHOD, "repl", M_ERRORS, new Long(1));
                System.err.println("Exception "+ e);
            }
        }
    }

    /**
     * Records stats of type long without specific TagKey
     * @param ml
     * @param n
     */
    private static void recordStat(Measure.MeasureLong ml, Long n) {
        statsRecorder.newMeasureMap().put(ml, n).record();
    }

    /**
     * Records stats of long type with specified TagKey
     * @param key
     * @param value
     * @param ml
     * @param n
     */
    private static void recordTaggedStat(TagKey key, String value, Measure.MeasureLong ml, Long n) {
        TagContext tctx = tagger.emptyBuilder().put(key, TagValue.create(value)).build();
        try (Scope ss = tagger.withTagContext(tctx)) {
            statsRecorder.newMeasureMap().put(ml, n).record();
        }
    }

    /**
     * Records stats of Double type with specified TagKey
     * @param key
     * @param value
     * @param md
     * @param d
     */
    private static void recordTaggedStat(TagKey key, String value, Measure.MeasureDouble md, Double d) {
        TagContext tctx = tagger.emptyBuilder().put(key, TagValue.create(value)).build();
        try (Scope ss = tagger.withTagContext(tctx)) {
            statsRecorder.newMeasureMap().put(md, d).record();
        }
    }

    /**
     * This method sets up Prometheus exporter to view OC Stats on Prometheus.
     * Prometheus reads metrics from http://localhost:8889
     * @throws IOException
     */
    private static void setupOpenCensusAndPrometheusExporter() throws IOException {

        // Create and register the Prometheus exporter
        PrometheusStatsCollector.createAndRegister();

        // Run the server as a daemon on address "localhost:8889"
        HTTPServer server = new HTTPServer("localhost", 8889, true);
    }

    /**
     * This method sets up Zipkin exporter to report traces to Zipkin ux.
     * By default Zipkins run on http://localhost:9411
     * @throws IOException
     */
    private static void setupOpenCensusAndZipkinExporter() throws IOException {
        TraceConfig traceConfig = Tracing.getTraceConfig();

        // For demo purpose, we will always sample.
        traceConfig.updateActiveTraceParams(
                traceConfig.getActiveTraceParams().toBuilder().setSampler(Samplers.alwaysSample()).build());

        ZipkinTraceExporter.createAndRegister("http://localhost:9411/api/v2/spans", "ocjavaquickstart");
    }

    private static String processLine(String line) {

        long startTimeNs = System.nanoTime();

        try (Scope ss = tracer.spanBuilder("processLine").startScopedSpan()) {
            //System.out.println("processLine - " + tracer.getCurrentSpan().getContext());
            return line.toUpperCase();
        }
        finally {
            long totalTimeNs = System.nanoTime() - startTimeNs;
            double timespentMs = (new Double(totalTimeNs))/1e6;
            recordTaggedStat(KEY_METHOD, "processLine", M_LATENCY_MS, timespentMs);
        }
    }

    private static String readLine(BufferedReader in) {

        Scope ss = tracer.spanBuilder("readLine").startScopedSpan();

        String line = "";

        try {
            line = in.readLine();
            //System.out.println("readLine - " + tracer.getCurrentSpan().getContext());
        } catch (Exception e) {
            Span span = tracer.getCurrentSpan();
            span.setStatus(Status.INTERNAL.withDescription(e.toString()));

        } finally {
            ss.close();
            return line;
        }
    }

    private static void readEvaluateProcessLine(BufferedReader in) throws IOException {

        try (Scope ss = tracer.spanBuilder("readEvaluateProcessLine").startScopedSpan()) {
            System.out.print("> ");
            System.out.flush();
            String line = readLine(in);

            // Annotate the span to indicate we are invoking processLine next.

            Map<String, AttributeValue> attributes = new HashMap<>();

            // Zipkins drop additional tags
            attributes.put("len", AttributeValue.longAttributeValue(line.length()));
            attributes.put("use", AttributeValue.stringAttributeValue("repl"));
            Span span = tracer.getCurrentSpan();
            span.addAnnotation("Invoking processLine", attributes);

            String processed = processLine(line);
            System.out.println("< " + processed + "\n");
            if (line != null && line.length() > 0) {
                recordStat(M_LINES_IN, 1L);
                recordStat(M_LINE_LENGTHS, new Long(line.length()));
            }
            //System.out.println("repl - "+ tracer.getCurrentSpan().getContext());
        }
    }

    /**
     * This method is used to create and register all the views for the defined metrics. It also defines the type of aggregation
     * for the metrics.
     */
    private static void registerAllViews() {
        // Defining the distribution aggregations
        Aggregation latencyDistribution = Aggregation.Distribution.create(BucketBoundaries.create(
                Arrays.asList(
                        // [>=0ms, >=25ms, >=50ms, >=75ms, >=100ms, >=200ms, >=400ms, >=600ms, >=800ms, >=1s, >=2s, >=4s, >=6s]
                        0.0, 25.0, 50.0, 75.0, 100.0, 200.0, 400.0, 600.0, 800.0, 1000.0, 2000.0, 4000.0, 6000.0)
        ));

        Aggregation lengthsDistribution = Aggregation.Distribution.create(BucketBoundaries.create(
                Arrays.asList(
                        // [>=0B, >=5B, >=10B, >=20B, >=40B, >=60B, >=80B, >=100B, >=200B, >=400B, >=600B, >=800B, >=1000B]
                        0.0, 5.0, 10.0, 20.0, 40.0, 60.0, 80.0, 100.0, 200.0, 400.0, 600.0, 800.0, 1000.0)
        ));

        // Define the count aggregation
        Aggregation countAggregation = Aggregation.Count.create();

        // So tagKeys
        List<TagKey> noKeys = new ArrayList<>();

        // Define the views
        View[] views = new View[]{
                View.create(View.Name.create("ocjavametrics/latency"), "The distribution of latencies", M_LATENCY_MS, latencyDistribution, Collections.singletonList(KEY_METHOD)),
                View.create(View.Name.create("ocjavametrics/lines_in"), "The number of lines read in from standard input", M_LINES_IN, countAggregation, noKeys),
                View.create(View.Name.create("ocjavametrics/errors"), "The number of errors encountered", M_ERRORS, countAggregation, Collections.singletonList(KEY_METHOD)),
                View.create(View.Name.create("ocjavametrics/line_length"), "The distribution of line lengths", M_LINE_LENGTHS, lengthsDistribution, noKeys)
        };

        // Then finally register the views
        for (View view : views) {
            viewManager.registerView(view);
        }
    }

    /**
     * This method is used to print stats to file: /stats.txt to understand the structure of OC Metrics
     */
    private synchronized static void printStats() {

        Set<View> views = viewManager.getAllExportedViews();

        Path path = Paths.get("stats.txt");
        try (final BufferedWriter writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
             final PrintWriter out = new PrintWriter(writer)) {

            for (View view : views) {
                out.println(view.getName() + "--");
                out.println(viewManager.getView(view.getName()));
                out.println("----------");
            }
            out.println("###################");
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}