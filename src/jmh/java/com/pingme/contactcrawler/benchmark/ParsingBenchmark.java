package com.pingme.contactcrawler.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class ParsingBenchmark {

    private List<String> input;

    @Setup(Level.Trial)
    public void setup() {
        input = new ArrayList<>(50_000);

        for (int i = 0; i < 50_000; i++) {
            if (i % 2 == 0) {
                input.add("name=John Doe; phone=+123456789; email=john" + i + "@mail.com");
            } else {
                input.add("name=John Doe; email=john" + i + "@mail.com"); // без phone=
            }
        }
    }

    // for-loop
    @Benchmark
    public void parseWithFor(Blackhole bh) {
        int ok = 0;
        for (String s : input) {
            if (s.contains("email=") && s.contains("phone=")) ok++;
        }
        bh.consume(ok);
    }

    // stream
    @Benchmark
    public void parseWithStream(Blackhole bh) {
        long ok = input.stream()
                .filter(s -> s.contains("email=") && s.contains("phone="))
                .count();
        bh.consume(ok);
    }

    // parallelStream
    @Benchmark
    public void parseWithParallelStream(Blackhole bh) {
        long ok = input.parallelStream()
                .filter(s -> s.contains("email=") && s.contains("phone="))
                .count();
        bh.consume(ok);
    }
}
