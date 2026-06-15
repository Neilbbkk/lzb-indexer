package com.lzb.indexer.aspect;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Aspect
@Component
public class ScannerMetricsAspect {

    private static final Logger log = LoggerFactory.getLogger(ScannerMetricsAspect.class);

    private final MeterRegistry registry;
    private final Timer scanDuration;

    public ScannerMetricsAspect(MeterRegistry registry) {
        this.registry = registry;
        this.scanDuration = Timer.builder("scanner.scan.duration")
            .description("Duration of BlockScanner.scan() execution")
            .register(registry);
    }

    @PostConstruct
    public void init() {
        log.info("ScannerMetricsAspect initialized, timer={}", scanDuration.getId().getName());
    }

    @Around("execution(public void com.lzb.indexer.scanner.BlockScanner.scan())")
    public Object timeScan(ProceedingJoinPoint pjp) throws Throwable {
        log.info(">>> AOP intercepted scan()!");
        Timer.Sample sample = Timer.start(registry);
        try {
            return pjp.proceed();
        } finally {
            sample.stop(scanDuration);
            log.info("<<< AOP scan() finished, count={}", scanDuration.count());
        }
    }
}