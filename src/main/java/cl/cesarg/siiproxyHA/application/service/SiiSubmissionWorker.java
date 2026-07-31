package cl.cesarg.siiproxyHA.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(
        prefix = "sii",
        name = "worker-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class SiiSubmissionWorker {

    private static final Logger log = LoggerFactory.getLogger(SiiSubmissionWorker.class);
    private final SiiSubmissionClaimService claims;
    private final SiiSubmissionProcessor processor;

    public SiiSubmissionWorker(
            SiiSubmissionClaimService claims,
            SiiSubmissionProcessor processor
    ) {
        this.claims = claims;
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${sii.worker-delay:2s}")
    public void processNext() {
        try {
            claims.claimNext().ifPresent(processor::process);
        } catch (Exception exception) {
            log.error(
                    "Unexpected SII background worker failure type={}",
                    exception.getClass().getSimpleName(),
                    exception
            );
        }
    }
}
