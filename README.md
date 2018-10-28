# Open Census Java Demo 

[OpenCensus](https://www.opencensus.io) is a toolkit for collecting application performance and behavior data. It currently
includes 3 apis: stats, tracing and tags.

This demo application shows how to use OpenCensus Java Stats and Trace API to instrument your Java application and get End-to-End
distributed traces on Zipkins as well as visualize metrics on Prometheus. 

## How to set up demo application

#### Prerequisites: 
1. JDK 8+
2. [Zipkins](https://zipkin.io) (For distributed trace view)
3. [Prometheus](https://prometheus.io) (For viewing metrics)
4. Docker for Linux / Windows

#### Steps to setup the application

1. Clone the repository.
2. Set up Zipkins by following [this](https://opencensus.io/codelabs/zipkin) guided lab.
3. Set up Prometheus by following the instructions on the prometheus [getting started](https://prometheus.io/docs/prometheus/latest/getting_started/) guide. 
2. On the terminal run the command `mvnw install`.
3. On the terminal run the command `mvnw exec:java -Dexec.mainClass=io.opencensus.statsandtraces.quickstart.Repl`
4. Interact with the console application by providing some text input.
5. Navigate to [http://localhost:9411](http://localhost:9411) (Zipkins UI) to view the distributed traces. It helps you understand the control flow
of your application looks like. This helps in understanding which part of the code is causing latency in the system.
6. Navigate to [https://localhost:9090](https://localhost:9090) (Prometheus UI) to view the metrics emitted from Open Census Library. 