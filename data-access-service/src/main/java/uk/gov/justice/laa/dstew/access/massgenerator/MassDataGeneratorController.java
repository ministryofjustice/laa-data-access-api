package uk.gov.justice.laa.dstew.access.massgenerator;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.massgenerator.model.MassDataGenerationRequest;
import uk.gov.justice.laa.dstew.access.massgenerator.model.MassDataGenerationResponse;

/**
 * Controller for mass data generation endpoints.
 * This is intended for testing and development purposes only.
 */
@RestController
@RequestMapping("/api/v0/admin")
@RequiredArgsConstructor
@Slf4j
@ExcludeFromGeneratedCodeCoverage
public class MassDataGeneratorController {

    private final MassDataGeneratorService massDataGeneratorService;

    /**
     * Generate mass test data with default settings using a query parameter.
     *
     * @param count number of application records to generate (default: 10)
     * @return response with generation statistics
     */
    @GetMapping("/generate-mass-data")
    public ResponseEntity<MassDataGenerationResponse> generateMassDataSimple(
            @RequestParam(defaultValue = "10") int count) {

        log.info("Received simple mass data generation request: count={}", count);

        try {
            MassDataGenerationRequest request = new MassDataGenerationRequest();
            request.setCount(count);
            request.setBatchSize(100);
            request.setDecisionRate(0.6);
            request.setLinkRate(0.3);

            MassDataGenerationResponse response = massDataGeneratorService.generateMassData(request);
            log.info("Mass data generation completed successfully: {} records generated", response.getRecordsGenerated());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error during mass data generation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Generate mass test data including applications, proceedings, decisions, and certificates.
     *
     * @param request the generation request with configuration
     * @return response with generation statistics
     */
    @PostMapping("/generate-mass-data")
    public ResponseEntity<MassDataGenerationResponse> generateMassData(
            @Valid @RequestBody MassDataGenerationRequest request) {

        log.info("Received mass data generation request: count={}, batchSize={}, decisionRate={}, linkRate={}",
                request.getCount(), request.getBatchSize(), request.getDecisionRate(), request.getLinkRate());

        try {
            MassDataGenerationResponse response = massDataGeneratorService.generateMassData(request);
            log.info("Mass data generation completed successfully: {} records generated", response.getRecordsGenerated());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error during mass data generation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

