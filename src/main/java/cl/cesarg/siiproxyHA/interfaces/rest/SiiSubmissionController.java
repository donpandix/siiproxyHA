package cl.cesarg.siiproxyHA.interfaces.rest;

import cl.cesarg.siiproxyHA.application.exception.ResourceNotFoundException;
import cl.cesarg.siiproxyHA.infrastructure.persistence.DteRepository;
import cl.cesarg.siiproxyHA.infrastructure.persistence.SiiSubmissionRepository;
import cl.cesarg.siiproxyHA.interfaces.rest.dto.SiiSubmissionDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dte/{dteId}/sii-submissions")
public class SiiSubmissionController {

    private final DteRepository dteRepository;
    private final SiiSubmissionRepository submissionRepository;

    public SiiSubmissionController(
            DteRepository dteRepository,
            SiiSubmissionRepository submissionRepository
    ) {
        this.dteRepository = dteRepository;
        this.submissionRepository = submissionRepository;
    }

    @GetMapping
    public ResponseEntity<List<SiiSubmissionDto>> list(@PathVariable UUID dteId) {
        if (!dteRepository.existsById(dteId)) {
            throw new ResourceNotFoundException("DTE not found: " + dteId);
        }
        return ResponseEntity.ok(
                submissionRepository.findByDteIdOrderByCreatedAtDesc(dteId)
                        .stream()
                        .map(SiiSubmissionDto::from)
                        .toList()
        );
    }

    @GetMapping("/{submissionId}")
    public ResponseEntity<SiiSubmissionDto> get(
            @PathVariable UUID dteId,
            @PathVariable UUID submissionId
    ) {
        return submissionRepository.findById(submissionId)
                .filter(entity -> dteId.equals(entity.getDteId()))
                .map(SiiSubmissionDto::from)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SII submission not found for DTE"
                ));
    }
}
