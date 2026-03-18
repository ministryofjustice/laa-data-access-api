package uk.gov.justice.laa.dstew.access.controller;

import io.swagger.v3.oas.annotations.Hidden;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
//import uk.gov.justice.laa.dstew.access.api.SdsApi;
//import uk.gov.justice.laa.dstew.access.model.SdsGetFileResponse;
//import uk.gov.justice.laa.dstew.access.model.SdsHealthResponse;
//import uk.gov.justice.laa.dstew.access.model.SdsSaveFileResponse;
//import uk.gov.justice.laa.dstew.access.service.SdsService;
//import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodArguments;
//import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodResponse;

/**
 * Controller class for handling requests related to the SDS service.
 */
@RequiredArgsConstructor
@RestController
@ExcludeFromGeneratedCodeCoverage
//@Hidden
public class SdsController {
//
//  private final SdsService sdsService;
//
//  @LogMethodArguments
//  @LogMethodResponse
//  @Override
//  public ResponseEntity<SdsGetFileResponse> getFile(String id) {
//    return ResponseEntity.ok(sdsService.getFile(id));
//  }
//
//  @LogMethodArguments
//  @LogMethodResponse
//  @Override
//  public ResponseEntity<SdsSaveFileResponse> saveFile(MultipartFile file) {
//    SdsSaveFileResponse response = sdsService.saveFile(file);
//    return ResponseEntity.status(HttpStatus.CREATED).body(response);
//  }
//
//  @LogMethodArguments
//  @LogMethodResponse
//  @Override
//  public ResponseEntity<SdsSaveFileResponse> saveOrUpdateFile(MultipartFile file) {
//    SdsSaveFileResponse response = sdsService.saveOrUpdateFile(file);
//    return ResponseEntity.ok(response);
//  }
//
//  @LogMethodArguments
//  @LogMethodResponse
//  @Override
//  public ResponseEntity<Void> deleteFiles(List<String> fileKeys) {
//    sdsService.deleteFiles(fileKeys);
//    return ResponseEntity.ok().build();
//  }
//
//  @LogMethodArguments
//  @LogMethodResponse
//  @Override
//  public ResponseEntity<SdsHealthResponse> getSdsHealth() {
//    return ResponseEntity.ok(sdsService.checkHealth());
//  }

}
