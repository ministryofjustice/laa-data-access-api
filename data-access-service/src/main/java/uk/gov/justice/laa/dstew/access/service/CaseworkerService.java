package uk.gov.justice.laa.dstew.access.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.mapper.CaseworkerMapper;
import uk.gov.justice.laa.dstew.access.model.Caseworker;
import uk.gov.justice.laa.dstew.access.repository.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.security.AllowApiAdmin;

/**
 * Service for providing caseworkers.
 */
@Service
@RequiredArgsConstructor
public class CaseworkerService {

  private final CaseworkerRepository caseworkerRepository;
  private final CaseworkerMapper caseworkerMapper;

  /**
  * Provides a list of all caseworkers.
  */
  @AllowApiAdmin
  public List<Caseworker> getAllCaseworkers() {
    return caseworkerRepository.findAll()
                                .stream()
                                .map(caseworkerMapper::toCaseworker)
                                .toList();
  }
}
